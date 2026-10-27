package io.poxray.migration;

import static io.poxray.migration.DossierReader.fields;
import static io.poxray.migration.DossierReader.string;
import io.poxray.migration.DossierEvidenceReport.*;
import io.poxray.migration.DossierReport.Dependency;
import io.poxray.migration.DossierReport.Diagnostic;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Replay structure is independent of mutable external bytes. Only current applicability uses bytes. */
final class DossierEvidence {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final Path directory;
    private final DossierValidator.Projection projection;
    private final Map<String, Packet> packets = new LinkedHashMap<>();
    private final Map<String, Use> uses = new LinkedHashMap<>();
    private final Map<String, String> successors = new LinkedHashMap<>();

    DossierEvidence(Path directory, DossierValidator.Projection projection) {
        this.directory = directory; this.projection = projection;
    }
    static boolean isEvent(String type) { return type.equals("evidence-register") || type.equals("evidence-use"); }
    static void validateEvent(JsonNode event) {
        boolean registration = string(event, "type").equals("evidence-register");
        Set<String> names = new HashSet<>(Set.of("id", "type", "at", "source"));
        names.addAll(registration ? Set.of("packetPath", "packetSha256", "reviewPath", "reviewSha256")
                : Set.of("scope", "evidence", "claimId", "text", "replaces"));
        fields(event, names); string(event, "id"); string(event, "source"); DossierReader.timestamp(string(event, "at"));
        if (registration) {
            localPath(string(event, "packetPath")); localPath(string(event, "reviewPath"));
            DossierReader.digest(string(event, "packetSha256")); DossierReader.digest(string(event, "reviewSha256"));
        } else {
            dependency(event.get("scope")); string(event, "evidence"); string(event, "claimId"); string(event, "text"); nullable(event, "replaces");
        }
    }
    private static void require(boolean value, String reason) {
        if (!value) throw new DossierReader.InvalidInput("INVALID_EVIDENCE", reason);
    }
    private static void localPath(String value) {
        DossierReader.localPath(value);
        require(!value.replace('\\', '/').startsWith("//"), "Evidence path cannot be UNC or device/network namespace");
        require(Path.of(value).getFileSystem().equals(FileSystems.getDefault()), "Evidence path must use default filesystem");
    }
    void apply(JsonNode event) {
        try {
            if (string(event, "type").equals("evidence-register")) register(event); else use(event);
        } catch (DossierReader.InvalidInput e) {
            projection.diagnostics.add(new Diagnostic("error", e.code, string(event, "id"), e.getMessage()));
        }
    }
    private byte[] read(String name) throws IOException {
        localPath(name);
        Path path = directory.resolve(name).toAbsolutePath();
        require(path.getFileSystem().equals(FileSystems.getDefault()), "Evidence path must use default filesystem");
        Path part = path.getRoot();
        for (Path component : path) {
            part = part.resolve(component);
            BasicFileAttributes attrs = Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            require(!attrs.isSymbolicLink() && !attrs.isOther(), "Evidence path contains a symbolic link or reparse entry");
        }
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "Evidence target must be a regular file");
        return Files.readAllBytes(path);
    }
    private void register(JsonNode event) {
        List<String> reasons = new ArrayList<>();
        PacketDocument packet = null; ReviewDocument review = null;
        String packetHash = string(event, "packetSha256").toLowerCase(Locale.ROOT);
        String reviewHash = string(event, "reviewSha256").toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = read(string(event, "packetPath"));
            if (!DossierReader.sha256(bytes).equals(packetHash)) reasons.add("Packet SHA256 digest mismatch");
            packet = packet(MAPPER.readTree(bytes));
            for (Claim claim : packet.claims()) for (Dependency scope : claim.scope())
                require(projection.proposed(scope) != null, "Packet claim scope does not match a proposal at registration");
        } catch (IOException | RuntimeException e) { reasons.add("Packet unavailable: " + safeReason(e)); }
        try {
            byte[] bytes = read(string(event, "reviewPath"));
            if (!DossierReader.sha256(bytes).equals(reviewHash)) reasons.add("Review SHA256 digest mismatch");
            review = review(MAPPER.readTree(bytes));
            if (packet != null) {
                require(review.packetId().equals(packet.id()), "Review packet ID mismatch");
                require(review.packetSha256().equals(packetHash), "Review packet SHA256 digest mismatch");
                require(!review.reviewer().trim().equalsIgnoreCase(packet.author().trim()), "Reviewer must be independent of author");
                for (ReviewClaim r : review.claims()) {
                    Claim claim = packet.claims().stream().filter(c -> c.id().equals(r.claimId())).findFirst().orElse(null);
                    require(claim != null && claim.scope().containsAll(r.scope()), "Review claim or scope is absent from packet");
                }
            }
        } catch (IOException | RuntimeException e) { reasons.add("Review unavailable: " + safeReason(e)); }
        // A claim's FAIL verdict is claim-specific (checked per use); only an all-FAIL review blocks the packet.
        if (reasons.isEmpty() && review.claims().stream().noneMatch(r -> r.verdict().equals("PASS")))
            reasons.add("No packet claim has a PASS review verdict");
        Packet result = new Packet(string(event, "id"), string(event, "packetPath"), packetHash,
                string(event, "reviewPath"), reviewHash, packet, review, reasons.isEmpty() ? "valid-attribution" : "unavailable", reasons);
        packets.put(result.eventId(), result);
        warn(result.eventId(), reasons);
    }
    private static String safeReason(Exception e) {
        return e instanceof DossierReader.InvalidInput ? e.getMessage() : "cannot read or parse strict JSON document";
    }
    private void use(JsonNode event) {
        Dependency scope = dependency(event.get("scope"));
        require(projection.proposed(scope) != null, "Evidence use scope must match an earlier proposal");
        Packet packet = packets.get(string(event, "evidence"));
        require(packet != null, "Evidence use needs an earlier registration");
        String replaces = nullable(event, "replaces");
        if (replaces != null) {
            Use previous = uses.get(replaces);
            require(previous != null && previous.scope().equals(scope) && !successors.containsKey(replaces),
                    "Evidence replacement must extend an earlier same-scope chain tail; forks are invalid");
        }
        List<String> reasons = new ArrayList<>(packet.reasons());
        String claimId = string(event, "claimId"), text = string(event, "text");
        Claim claim = packet.packet() == null ? null : packet.packet().claims().stream().filter(c -> c.id().equals(claimId)).findFirst().orElse(null);
        ReviewClaim review = packet.review() == null ? null : packet.review().claims().stream().filter(c -> c.claimId().equals(claimId)).findFirst().orElse(null);
        if (claim == null || !claim.scope().contains(scope) || !claim.text().equals(text)) reasons.add("Exact claim text and scope are not supported by packet");
        if (review == null || !review.scope().contains(scope) || !review.verdict().equals("PASS")) reasons.add("Exact claim scope lacks a PASS review");
        String id = string(event, "id");
        uses.put(id, new Use(id, scope, packet.eventId(), claimId, text, replaces, reasons.isEmpty() ? "supported" : "unavailable", reasons));
        if (replaces != null) successors.put(replaces, id);
        warn(id, reasons);
    }
    private void warn(String id, List<String> reasons) {
        for (String reason : reasons) projection.diagnostics.add(new Diagnostic("warning", "EVIDENCE_UNAVAILABLE", id, reason));
    }
    DossierEvidenceReport report() {
        Map<String, String> states = new HashMap<>();
        for (Use root : uses.values()) if (root.replaces() == null) {
            List<Use> chain = new ArrayList<>();
            for (Use node = root; node != null; node = uses.get(successors.get(node.eventId()))) chain.add(node);
            int current = -1;
            for (int i = 0; i < chain.size(); i++) if (chain.get(i).reasons().isEmpty()) current = i;
            for (int i = 0; i < chain.size(); i++) states.put(chain.get(i).eventId(),
                    i == current ? "supported" : i < current ? "superseded" : i == 0 ? "unavailable" : "proposed-unavailable");
        }
        return new DossierEvidenceReport(List.copyOf(packets.values()), uses.values().stream().map(u -> new Use(u.eventId(), u.scope(),
                u.evidenceEventId(), u.claimId(), u.text(), u.replaces(), states.get(u.eventId()), u.reasons())).toList());
    }
    boolean unavailable(Dependency scope) { return report().uses().stream().anyMatch(u -> u.scope().equals(scope) && u.state().equals("unavailable")); }
    boolean hasSection(String section) { return uses.values().stream().anyMatch(u -> u.scope().sectionId().equals(section)); }
    private static String nullable(JsonNode n, String key) { return n.get(key).isNull() ? null : string(n, key); }
    private static String hash(JsonNode n, String key) { String v = string(n, key); DossierReader.digest(v); return v.toLowerCase(Locale.ROOT); }
    private static Dependency dependency(JsonNode n) {
        fields(n, Set.of("decisionId", "sectionId", "revision", "sha256"));
        return new Dependency(string(n, "decisionId"), string(n, "sectionId"), string(n, "revision"), hash(n, "sha256"));
    }
    private static List<Dependency> scopes(JsonNode n) {
        require(n.isArray() && !n.isEmpty(), "Claim scope must be nonempty");
        List<Dependency> result = n.valueStream().map(DossierEvidence::dependency).toList();
        require(new HashSet<>(result).size() == result.size(), "Claim scope must be unique"); return result;
    }
    private static void format(JsonNode n) { require(n.get("formatVersion").isIntegralNumber() && n.get("formatVersion").intValue() == 1,
            "Unsupported evidence formatVersion"); }
    private static PacketDocument packet(JsonNode n) {
        fields(n, Set.of("formatVersion", "id", "author", "sourceKind", "sourceLocator", "sourceSha256", "observedAt", "versions", "limitations", "kbDifference", "claims"));
        format(n); String kind = string(n, "sourceKind");
        require(Set.of("export", "documentation", "derived", "owner-confirmed", "tenant-observed").contains(kind), "Unknown source kind");
        String digest = nullable(n, "sourceSha256"); if (digest != null) { DossierReader.digest(digest); digest = digest.toLowerCase(Locale.ROOT); }
        String observed = nullable(n, "observedAt"); if (observed != null) DossierReader.timestamp(observed);
        JsonNode v = n.get("versions"); fields(v, Set.of("exportLabel", "packageRelease", "bundleVersion", "targetVersion", "designTimeVersion", "deployedVersion"));
        Versions versions = new Versions(nullable(v, "exportLabel"), nullable(v, "packageRelease"), nullable(v, "bundleVersion"), nullable(v, "targetVersion"), nullable(v, "designTimeVersion"), nullable(v, "deployedVersion"));
        JsonNode cs = n.get("claims"); require(cs.isArray() && !cs.isEmpty(), "Claims must be nonempty");
        List<Claim> claims = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (JsonNode c : cs) {
            fields(c, Set.of("id", "text", "scope", "limitations")); String id = string(c, "id"); require(ids.add(id), "Duplicate claim ID");
            claims.add(new Claim(id, string(c, "text"), scopes(c.get("scope")), string(c, "limitations")));
        }
        return new PacketDocument(1, string(n, "id"), string(n, "author"), kind, string(n, "sourceLocator"), digest, observed, versions,
                string(n, "limitations"), string(n, "kbDifference"), claims);
    }
    private static ReviewDocument review(JsonNode n) {
        fields(n, Set.of("formatVersion", "packetId", "packetSha256", "reviewer", "reviewedAt", "claims")); format(n);
        String at = string(n, "reviewedAt"); DossierReader.timestamp(at);
        JsonNode cs = n.get("claims"); require(cs.isArray() && !cs.isEmpty(), "Review claims must be nonempty");
        List<ReviewClaim> claims = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (JsonNode c : cs) {
            fields(c, Set.of("claimId", "scope", "verdict", "limitations")); String id = string(c, "claimId"); require(ids.add(id), "Duplicate review claim ID");
            String verdict = string(c, "verdict"); require(Set.of("PASS", "FAIL").contains(verdict), "Unknown review verdict");
            claims.add(new ReviewClaim(id, scopes(c.get("scope")), verdict, string(c, "limitations")));
        }
        return new ReviewDocument(1, string(n, "packetId"), hash(n, "packetSha256"), string(n, "reviewer"), at, claims);
    }
}
