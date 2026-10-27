package io.poxray.migration;

import static io.poxray.migration.DossierReadinessTest.*;
import static io.poxray.migration.DossierValidatorTest.*;

import io.poxray.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Actual local bytes and explicit event histories; no validator mocks or private inputs. */
final class DossierEvidenceFixtures {
    static final String TEXT = "Synthetic API response was observed";
    final Fixture ledger;
    final Part a;
    final Part b;
    DossierEvidenceFixtures(Path dir) throws IOException { this(dir, true); }
    DossierEvidenceFixtures(Path dir, boolean approved) throws IOException {
        Files.createDirectories(dir);
        ledger = new Fixture(dir);
        a = ledger.part("a"); b = ledger.part("b");
        if (approved) { ledger.approve(a); ledger.approve(b); }
        ledger.add(milestone("DA", "design", List.of("a"), List.of(a), List.of(), List.of()),
                milestone("DB", "design", List.of("b"), List.of(b), List.of(), List.of()),
                coverage("C", List.of("a", "b")));
    }
    Path path() throws IOException { return write(ledger.dir.resolve("dossier-v3.json"), v2(ledger.events).replace("\"formatVersion\":2", "\"formatVersion\":3")); }
    DossierAssessment assess() throws IOException { return DossierValidator.assess(path()); }
    DossierAssessment legacyPrerequisites() throws IOException {
        List<String> originalEvents = ledger.events.stream().filter(event -> !Json.mapper().readTree(event).path("type").stringValue().startsWith("evidence-")).toList();
        return DossierValidator.assess(write(ledger.dir.resolve("prerequisites-v2.json"), v2(originalEvents)));
    }
    JsonNode json() throws IOException { return tree(assess()); }
    static JsonNode tree(Object value) { return Json.mapper().readTree(Json.write(value)); }
    static JsonNode dep(Part part) { return Json.mapper().readTree(part.dependency()); }
    static Map<String, Object> object(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put((String) pairs[i], pairs[i + 1]);
        return map;
    }
    Map<String, Object> packet(String id, Part scope) {
        return object("formatVersion", 1, "id", id, "author", "Synthetic author", "sourceKind", "owner-confirmed",
                "sourceLocator", "https://invalid.example/attribution-only", "sourceSha256", null,
                "observedAt", "2026-10-20", "versions", object("exportLabel", "export-label",
                    "packageRelease", "release-new", "bundleVersion", "bundle-new", "targetVersion", "target-version",
                    "designTimeVersion", "design-version", "deployedVersion", "deployed-old"),
                "limitations", "No cache or idempotence claim", "kbDifference", "Synthetic discrepancy remains attributed",
                "claims", List.of(object("id", "api", "text", TEXT, "scope", List.of(dep(scope)), "limitations", "One response only")));
    }
    Map<String, Object> review(String packetId, String hash, Part scope) {
        return object("formatVersion", 1, "packetId", packetId, "packetSha256", hash, "reviewer", "Independent reviewer",
                "reviewedAt", "2026-10-21", "claims", List.of(object("claimId", "api", "scope", List.of(dep(scope)),
                        "verdict", "PASS", "limitations", "Attribution check only")));
    }
    Fileset register(String id, Part scope) throws IOException { return register(id, packet(id, scope), null, scope); }
    Fileset register(String id, Object packet, Object review, Part scope) throws IOException {
        Path p = write(ledger.dir.resolve(id + "-packet.json"), packet instanceof String text ? text : Json.write(packet));
        String digest = sha256(Files.readAllBytes(p));
        Object actualReview = review == null ? review(id, digest, scope) : review;
        Path r = write(ledger.dir.resolve(id + "-review.json"), actualReview instanceof String text ? text : Json.write(actualReview));
        ledger.add(e(id, "evidence-register", "packetPath", p.getFileName().toString(), "packetSha256", digest,
                "reviewPath", r.getFileName().toString(), "reviewSha256", sha256(Files.readAllBytes(r))));
        return new Fileset(p, r);
    }
    void use(String id, String registration, Part scope, String replaces) {
        ledger.add(e(id, "evidence-use", "scope", dep(scope), "evidence", registration, "claimId", "api", "text", TEXT, "replaces", replaces));
    }
    static JsonNode use(DossierAssessment report, String id) {
        return tree(report).path("evidence").path("uses").valueStream().filter(n -> id.equals(n.path("eventId").stringValue())).findFirst().orElseThrow();
    }
    static JsonNode packet(DossierAssessment report, String id) {
        return tree(report).path("evidence").path("packets").valueStream().filter(n -> id.equals(n.path("eventId").stringValue())).findFirst().orElseThrow();
    }
    static Map<Path, String> hashes(Path dir) throws IOException {
        Map<Path, String> result = new LinkedHashMap<>();
        try (var paths = Files.walk(dir)) {
            for (Path p : paths.filter(Files::isRegularFile).sorted().toList()) result.put(p, sha256(Files.readAllBytes(p)));
        }
        return result;
    }
    record Fileset(Path packet, Path review) { }
}
