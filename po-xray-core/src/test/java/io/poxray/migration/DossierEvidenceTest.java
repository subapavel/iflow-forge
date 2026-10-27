package io.poxray.migration;

import static io.poxray.migration.DossierEvidenceFixtures.*;
import static io.poxray.migration.DossierReadinessTest.*;
import static io.poxray.migration.DossierValidatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

import io.poxray.json.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

class DossierEvidenceTest {
    @TempDir Path dir;

    @Test void syntheticPrerequisitesAreValidAndReadyWithoutEvidence() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        var baseline = f.ledger.assess();
        assertTrue(baseline.dossier().valid(), Json.write(baseline));
        assertTrue(ms(baseline, "DA").ready());
        assertTrue(ms(baseline, "DB").ready());
    }

    @Test void reviewedPacketSupportsOnlyExactClaimScopeAndKeepsSeparateAttribution() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        var files = f.register("E", f.a); f.use("U", "E", f.a, null);
        Path ledger = f.path(); Map<Path, String> before = hashes(dir);
        var report = DossierValidator.assess(ledger);
        assertTrue(report.dossier().valid(), Json.write(report));
        JsonNode packet = packet(report, "E");
        assertEquals("valid-attribution", packet.path("state").stringValue());
        assertEquals(sha256(Files.readAllBytes(files.packet())), packet.path("packetSha256").stringValue());
        assertEquals("owner-confirmed", packet.path("packet").path("sourceKind").stringValue());
        assertEquals("2026-10-20", packet.path("packet").path("observedAt").stringValue());
        assertTrue(packet.path("packet").path("sourceSha256").isNull());
        assertEquals("deployed-old", packet.path("packet").path("versions").path("deployedVersion").stringValue());
        assertEquals("bundle-new", packet.path("packet").path("versions").path("bundleVersion").stringValue());
        assertEquals("release-new", packet.path("packet").path("versions").path("packageRelease").stringValue());
        assertEquals("Independent reviewer", packet.path("review").path("reviewer").stringValue());
        assertEquals(dep(f.a), use(report, "U").path("scope"));
        assertEquals("supported", use(report, "U").path("state").stringValue());
        assertEquals(TEXT, use(report, "U").path("text").stringValue());
        assertEquals(report, Json.mapper().readValue(Json.write(report), DossierAssessment.class));
        assertEquals(Json.write(report), Json.write(DossierValidator.assess(ledger)));
        assertEquals(report.dossier(), DossierValidator.check(ledger));
        String markdown = DossierValidator.markdown(report);
        for (String value : List.of(TEXT, "owner-confirmed", "deployed-old", "bundle-new", "release-new", "design-version",
                "target-version", "export-label", "Independent reviewer", "No cache or idempotence claim",
                "Synthetic discrepancy remains attributed", "supported", f.a.decision())) assertTrue(markdown.contains(value), value);
        assertEquals(before, hashes(dir), "Assessment and rendering must not alter source bytes");
    }

    @Test void mixedReviewFailureBlocksOnlyItsUsedClaimAndScope() throws Exception {
        for (boolean useFailedClaim : List.of(true, false)) {
            var f = new DossierEvidenceFixtures(dir.resolve(useFailedClaim ? "both-uses" : "only-pass-used"));
            String failedText = "Synthetic B response claim";
            var document = f.packet("E", f.a);
            document.put("claims", List.of(
                    object("id", "api", "text", TEXT, "scope", List.of(dep(f.a)), "limitations", "A response only"),
                    object("id", "response-b", "text", failedText, "scope", List.of(dep(f.b)), "limitations", "B response only")));
            var review = f.review("E", sha256(Json.write(document).getBytes(java.nio.charset.StandardCharsets.UTF_8)), f.a);
            review.put("claims", List.of(
                    object("claimId", "api", "scope", List.of(dep(f.a)), "verdict", "PASS", "limitations", "A attribution checked"),
                    object("claimId", "response-b", "scope", List.of(dep(f.b)), "verdict", "FAIL", "limitations", "B claim not supported")));
            var files = f.register("E", document, review, f.a);
            f.use("UA", "E", f.a, null);
            if (useFailedClaim) f.ledger.add(e("UB", "evidence-use", "scope", dep(f.b), "evidence", "E",
                    "claimId", "response-b", "text", failedText, "replaces", null));
            f.path(); var before = hashes(f.ledger.dir);
            var report = f.assess();
            assertTrue(report.dossier().valid(), Json.write(report));
            JsonNode registered = packet(report, "E");
            assertEquals(sha256(Files.readAllBytes(files.packet())), registered.path("review").path("packetSha256").stringValue());
            assertEquals("FAIL", registered.path("review").path("claims").get(1).path("verdict").stringValue());
            assertEquals("response-b", registered.path("review").path("claims").get(1).path("claimId").stringValue());
            assertEquals("B claim not supported", registered.path("review").path("claims").get(1).path("limitations").stringValue());
            assertAll("mixed review, failed claim used=" + useFailedClaim,
                    () -> assertEquals("valid-attribution", registered.path("state").stringValue()),
                    () -> assertEquals("supported", use(report, "UA").path("state").stringValue()),
                    () -> assertTrue(use(report, "UA").path("reasons").isEmpty()),
                    () -> assertTrue(ms(report, "DA").ready()),
                    () -> assertTrue(ms(report, "DA").canWork()));
            if (useFailedClaim) {
                assertEquals("unavailable", use(report, "UB").path("state").stringValue());
                assertTrue(use(report, "UB").path("reasons").toString().toLowerCase(java.util.Locale.ROOT).matches("(?s).*(fail|pass|verdict).*"));
                assertFalse(ms(report, "DB").ready()); assertFalse(ms(report, "DB").canWork());
                assertTrue(ms(report, "DB").blockers().stream().anyMatch(b -> b.code().startsWith("EVIDENCE_") && "b".equals(b.sectionId())));
                assertTrue(report.dossier().diagnostics().stream().anyMatch(d -> "warning".equals(d.severity()) && "UB".equals(d.eventId())));
            } else {
                assertEquals(1, tree(report).path("evidence").path("uses").size());
                assertTrue(ms(report, "DB").ready()); assertTrue(ms(report, "DB").canWork());
                assertTrue(ms(report, "DB").blockers().isEmpty());
            }
            assertEquals(before, hashes(f.ledger.dir));
        }
    }

    @Test void missingChangedOrUnreviewedDocumentsRemainScopedWarningsWithMetadata() throws Exception {
        for (String failure : List.of("missing-packet", "missing-review", "changed-packet", "changed-review", "self-review", "fail-review", "wrong-packet-id", "wrong-packet-hash")) {
            var f = new DossierEvidenceFixtures(dir.resolve(failure));
            Object packet = f.packet("E", f.a);
            String packetHash = sha256(Json.write(packet).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var review = f.review("E", packetHash, f.a);
            if (failure.equals("self-review")) review.put("reviewer", "  SYNTHETIC AUTHOR  ");
            if (failure.equals("fail-review")) review.put("claims", List.of(object("claimId", "api", "scope", List.of(dep(f.a)), "verdict", "FAIL", "limitations", "Not supported")));
            if (failure.equals("wrong-packet-id")) review.put("packetId", "OTHER");
            if (failure.equals("wrong-packet-hash")) review.put("packetSha256", "f".repeat(64));
            var files = f.register("E", packet, review, f.a); f.use("U", "E", f.a, null);
            switch (failure) {
                case "missing-packet" -> Files.delete(files.packet());
                case "missing-review" -> Files.delete(files.review());
                case "changed-packet" -> Files.writeString(files.packet(), " ", java.nio.file.StandardOpenOption.APPEND);
                case "changed-review" -> Files.writeString(files.review(), " ", java.nio.file.StandardOpenOption.APPEND);
                default -> { }
            }
            f.path(); var before = hashes(f.ledger.dir);
            var report = f.assess(); unavailable(report, "U", failure);
            assertTrue(ms(report, "DB").ready(), failure);
            assertFalse(ms(report, "DA").canWork(), failure);
            assertEquals("unavailable", packet(report, "E").path("state").stringValue(), failure);
            assertFalse(packet(report, "E").path("reasons").isEmpty(), failure);
            String reasons = packet(report, "E").path("reasons").toString().toLowerCase(java.util.Locale.ROOT);
            String explanation = switch (failure) {
                case "missing-packet", "missing-review" -> "missing|read|exist|found|unavailable";
                case "changed-packet", "changed-review", "wrong-packet-hash" -> "hash|digest|sha256";
                case "self-review" -> "author|reviewer|self|independent";
                case "fail-review" -> "fail|pass|verdict";
                default -> "id|packet";
            };
            assertTrue(reasons.matches("(?s).*(" + explanation + ").*"), failure + reasons);
            if (!failure.contains("packet")) assertEquals("owner-confirmed", packet(report, "E").path("packet").path("sourceKind").stringValue(), failure);
            assertEquals(before, hashes(f.ledger.dir), failure);
        }
    }

    @Test void strictExternalJsonAndSemanticShapesCannotSupportAUse() throws Exception {
        for (String failure : List.of("duplicate", "trailing", "unknown", "missing", "format", "bad-kind", "bad-date", "bad-hash", "empty-claim", "duplicate-claim", "unknown-version", "null-version-object")) {
            for (boolean reviewSide : List.of(false, true)) {
                var f = new DossierEvidenceFixtures(dir.resolve(failure + reviewSide));
                var p = f.packet("E", f.a);
                var r = f.review("E", sha256(Json.write(p).getBytes(java.nio.charset.StandardCharsets.UTF_8)), f.a);
                var target = reviewSide ? r : p;
                switch (failure) {
                    case "unknown" -> target.put("extra", true);
                    case "missing" -> target.remove(reviewSide ? "reviewer" : "author");
                    case "format" -> target.put("formatVersion", 2);
                    case "bad-kind" -> target.put(reviewSide ? "reviewer" : "sourceKind", " ");
                    case "bad-date" -> target.put(reviewSide ? "reviewedAt" : "observedAt", "yesterday");
                    case "bad-hash" -> target.put(reviewSide ? "packetSha256" : "sourceSha256", "not-a-digest");
                    case "empty-claim" -> target.put("claims", List.of());
                    case "duplicate-claim" -> { var c = ((List<?>) target.get("claims")).getFirst(); target.put("claims", List.of(c, c)); }
                    case "unknown-version" -> target.put(reviewSide ? "extra" : "versions", object("unexpected", "new"));
                    case "null-version-object" -> target.put(reviewSide ? "claims" : "versions", null);
                    default -> { }
                }
                String malformed = Json.write(target);
                if (failure.equals("duplicate")) malformed = malformed.replaceFirst("\\{", "{\"formatVersion\":1,");
                if (failure.equals("trailing")) malformed += " {}";
                f.register("E", reviewSide ? p : malformed, reviewSide ? malformed : null, f.a);
                f.use("U", "E", f.a, null);
                unavailable(f.assess(), "U", failure + reviewSide);
            }
        }
    }

    @Test void allSourceKindsRemainAttributionAndNeverBecomeRuntimeObservation() throws Exception {
        for (String kind : List.of("export", "documentation", "derived", "owner-confirmed", "tenant-observed")) {
            var f = new DossierEvidenceFixtures(dir.resolve(kind));
            var document = f.packet("E", f.a); document.put("sourceKind", kind); document.put("observedAt", null);
            f.register("E", document, null, f.a); f.use("U", "E", f.a, null);
            var report = f.assess(); assertTrue(report.dossier().valid(), Json.write(report));
            JsonNode projected = packet(report, "E").path("packet");
            assertEquals(kind, projected.path("sourceKind").stringValue());
            assertTrue(projected.path("observedAt").isNull());
            assertEquals(tree(document.get("versions")), projected.path("versions"));
            assertEquals("supported", use(report, "U").path("state").stringValue());
        }
    }

    @Test void exactTextClaimAndFullScopePreventCacheOrRevisionOverreach() throws Exception {
        for (String mismatch : List.of("claim", "text", "section", "revision", "hash", "review-scope", "review-claim")) {
            var f = new DossierEvidenceFixtures(dir.resolve(mismatch));
            var p = f.packet("E", f.a);
            var wrongDigestScope = object("decisionId", f.a.decision(), "sectionId", "a", "revision", "r1", "sha256", "0".repeat(64));
            if (mismatch.equals("hash")) p.put("claims", List.of(object("id", "api", "text", TEXT,
                    "scope", List.of(wrongDigestScope), "limitations", "One response only")));
            var r = f.review("E", sha256(Json.write(p).getBytes(java.nio.charset.StandardCharsets.UTF_8)), f.a);
            if (mismatch.equals("hash")) r.put("claims", List.of(object("claimId", "api", "scope", List.of(wrongDigestScope),
                    "verdict", "PASS", "limitations", "Attribution check only")));
            if (mismatch.startsWith("review")) r.put("claims", List.of(object("claimId", mismatch.equals("review-claim") ? "cache" : "api", "scope", List.of(dep(f.b)), "verdict", "PASS", "limitations", "Only B")));
            var files = f.register("E", p, r, f.a);
            assertEquals(sha256(Files.readAllBytes(files.packet())), Json.mapper().readTree(Files.readString(files.review())).path("packetSha256").stringValue(), mismatch);
            Part scope = mismatch.equals("section") ? f.b : f.a;
            // Valid proposal references distinguish unavailable support from malformed event references.
            if (mismatch.equals("revision")) scope = f.ledger.part("a", "r2");
            f.ledger.add(e("U", "evidence-use", "scope", dep(scope), "evidence", "E", "claimId", mismatch.equals("claim") ? "cache" : "api",
                    "text", mismatch.equals("text") ? "Synthetic cache is idempotent" : TEXT, "replaces", null));
            var prerequisites = f.legacyPrerequisites();
            assertTrue(prerequisites.dossier().valid(), mismatch + Json.write(prerequisites));
            unavailable(f.assess(), "U", mismatch);
        }
    }

    @Test void packetScopeMustExistAtRegistrationPrefixAndExternalDanglingScopeIsNotStructural() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        Part future = f.ledger.part("future"); String proposal = f.ledger.events.removeLast();
        f.register("E", future); f.ledger.add(proposal); f.use("U", "E", future, null);
        var report = f.assess(); unavailable(report, "U", "future snapshot");
        assertTrue(ms(report, "DB").ready());
        assertFalse(packet(report, "E").path("reasons").isEmpty());
    }

    @Test void eventShapesAndDanglingReferencesRemainStructuralErrorsEverywhere() throws Exception {
        for (String failure : List.of("registration", "scope", "unknown-field", "missing-field", "digest", "uri", "unc", "unc-slash", "device", "device-slash", "extended", "extended-slash")) {
            var f = new DossierEvidenceFixtures(dir.resolve(failure)); f.register("E", f.a);
            String use = e("U", "evidence-use", "scope", dep(f.a), "evidence", "E", "claimId", "api", "text", TEXT, "replaces", null);
            switch (failure) {
                case "registration" -> use = use.replace("\"E\"", "\"MISSING\"");
                case "scope" -> use = use.replace(f.a.decision(), "MISSING");
                case "unknown-field" -> use = use.replaceFirst("\\{", "{\"extra\":true,");
                case "missing-field" -> use = e("U", "evidence-use", "scope", dep(f.a), "evidence", "E", "claimId", "api", "text", TEXT);
                default -> {
                    f.ledger.events.removeLast();
                    String path = switch (failure) {
                        case "uri" -> "file:///C:/synthetic.json";
                        case "unc" -> "\\\\synthetic.invalid\\share\\packet.json";
                        case "unc-slash" -> "//synthetic.invalid/share/packet.json";
                        case "device" -> "\\\\.\\pipe\\synthetic";
                        case "device-slash" -> "//./pipe/synthetic";
                        case "extended" -> "\\\\?\\C:\\synthetic.json";
                        case "extended-slash" -> "//?/C:/synthetic.json";
                        default -> "E-packet.json";
                    };
                    f.ledger.add(e("E", "evidence-register", "packetPath", path, "packetSha256", failure.equals("digest") ? "bad" : "a".repeat(64), "reviewPath", "E-review.json", "reviewSha256", "b".repeat(64)));
                }
            }
            f.ledger.add(use); var report = f.assess();
            assertFalse(report.dossier().valid(), failure);
            assertTrue(report.dossier().diagnostics().stream().anyMatch(d -> d.severity().equals("error")), failure);
            for (var milestone : report.milestones()) assertFalse(milestone.ready(), failure);
        }
    }

    @Test void explicitAbsolutePathsWorkAndSourceLocatorDoesNotGetCrawled() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        var p = f.packet("E", f.a); p.put("sourceLocator", "file:///definitely-missing/source.json");
        var files = f.register("E", p, null, f.a);
        f.ledger.events.removeLast();
        f.ledger.add(e("E", "evidence-register", "packetPath", files.packet().toAbsolutePath().toString(), "packetSha256", sha256(Files.readAllBytes(files.packet())),
                "reviewPath", files.review().toAbsolutePath().toString(), "reviewSha256", sha256(Files.readAllBytes(files.review()))));
        f.use("U", "E", f.a, null); var report = f.assess();
        assertTrue(report.dossier().valid(), Json.write(report));
        assertEquals("supported", use(report, "U").path("state").stringValue());
    }

    @Test void localJunctionOrSymbolicLinkIsRejectedBeforeFollowingItsTarget() throws Exception {
        Path real = Files.createDirectory(dir.resolve("real")); Path link = dir.resolve("link");
        if (System.getProperty("os.name").startsWith("Windows")) {
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), real.toString()).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int exit = process.waitFor();
            System.out.println("LOCAL_JUNCTION_SETUP_EXIT=" + exit + " " + output.trim());
            assertEquals(0, exit, "ENVIRONMENT_BLOCKED local junction fixture: " + output);
        } else Files.createSymbolicLink(link, real);
        try {
            var f = new DossierEvidenceFixtures(real); var files = f.register("E", f.a);
            f.ledger.events.removeLast();
            f.ledger.add(e("E", "evidence-register", "packetPath", link.resolve(files.packet().getFileName()).toAbsolutePath().toString(),
                    "packetSha256", sha256(Files.readAllBytes(files.packet())), "reviewPath", files.review().toAbsolutePath().toString(),
                    "reviewSha256", sha256(Files.readAllBytes(files.review()))));
            f.use("U", "E", f.a, null); var report = f.assess();
            assertFalse(Json.write(report).contains("\"state\" : \"supported\""));
            assertTrue(Json.write(report).toLowerCase().matches("(?s).*(link|reparse).*"), Json.write(report));
            assertEquals(sha256(Files.readAllBytes(files.packet())), hashes(real).get(files.packet()));
        } finally { Files.delete(link); }
    }

    @Test void nonDefaultFileSystemCannotSupplyAnEvidencePacket() throws Exception {
        var f = new DossierEvidenceFixtures(dir.resolve("local")); f.register("E", f.a); f.use("U", "E", f.a, null);
        Path archive = dir.resolve("synthetic.zip");
        f.path();
        try (var fs = java.nio.file.FileSystems.newFileSystem(java.net.URI.create("jar:" + archive.toUri()), Map.of("create", "true"))) {
            try (var paths = Files.list(f.ledger.dir)) {
                for (Path file : paths.toList()) Files.copy(file, fs.getPath("/" + file.getFileName()));
            }
            Path ledger = fs.getPath("/dossier-v3.json");
            var report = DossierValidator.assess(ledger);
            assertFalse(tree(report).path("evidence").path("uses").valueStream().anyMatch(n -> "supported".equals(n.path("state").stringValue())));
            assertTrue(Json.write(report).toLowerCase().matches("(?s).*(provider|filesystem|file system).*"), Json.write(report));
        }
    }

    @Test void versionsAndAppendOnlyHistorySupportExplicitUpgradesWithoutImplicitMigration() throws Exception {
        for (int version : List.of(1, 2, 3)) {
            var f = new DossierEvidenceFixtures(dir.resolve("v" + version));
            Path baseline = write(f.ledger.dir.resolve("baseline.json"), "{\"formatVersion\":" + version + ",\"migrationId\":\"M-1\",\"events\":[]}");
            f.register("E", f.a); f.use("U", "E", f.a, null);
            var report = DossierValidator.assess(f.path(), baseline);
            assertTrue(report.dossier().valid(), Json.write(report));
            assertEquals("verified", report.dossier().historyStatus());
            assertTrue(f.assess().dossier().diagnostics().stream().anyMatch(d -> d.code().equals("HISTORY_NOT_CHECKED")));
            Path current = f.path(); Path saved = write(f.ledger.dir.resolve("saved.json"), Files.readString(current));
            Files.writeString(current, Files.readString(current).replace("Synthetic design", "Rewritten design"));
            assertFalse(DossierValidator.check(current, saved).valid());
            Files.writeString(current, Files.readString(saved).replace("\"formatVersion\":3", "\"formatVersion\":2"));
            assertFalse(DossierValidator.check(current, saved).valid());
        }
    }

    @Test void newEvidenceRecordsAreImmutableAndLegacyAssessmentConstructorAndJsonRemainReadable() throws Exception {
        var f = new DossierEvidenceFixtures(dir); var old = f.ledger.assess();
        var legacy = new DossierAssessment(old.dossier(), old.status(), old.questions(), old.assumptions(), old.risks(), old.scopeBases(), old.coverages(), old.milestones());
        assertEquals(0, tree(legacy).path("evidence").path("packets").size());
        assertTrue(tree(legacy).path("evidence").path("packets").isArray());
        String oldJson = Json.write(object("dossier", old.dossier(), "status", old.status(), "questions", old.questions(), "assumptions", old.assumptions(),
                "risks", old.risks(), "scopeBases", old.scopeBases(), "coverages", old.coverages(), "milestones", old.milestones()));
        assertEquals(tree(legacy), tree(Json.mapper().readValue(oldJson, DossierAssessment.class)));
        var v1 = DossierValidator.assess(write(dir.resolve("v1.json"), "{\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":[]}"));
        assertTrue(v1.dossier().valid()); assertEquals("not-assessed", v1.status());
        assertEquals(tree(legacy).path("evidence"), tree(v1).path("evidence"));
        Class<?> reportType = Class.forName("io.poxray.migration.DossierEvidenceReport");
        assertTrue(reportType.isRecord());
        for (Class<?> type : reportType.getDeclaredClasses()) {
            assertTrue(type.isRecord(), type.getName());
            var components = type.getRecordComponents();
            Class<?>[] types = java.util.Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new);
            Object[] args = new Object[components.length]; var lists = new ArrayList<List<Object>>();
            for (int i = 0; i < args.length; i++) {
                if (types[i] == List.class) { var values = new ArrayList<Object>(); lists.add(values); args[i] = values; }
                else if (types[i] == int.class) args[i] = 1;
            }
            Object record = type.getDeclaredConstructor(types).newInstance(args);
            for (var list : lists) list.add("later mutation");
            for (var component : components) if (component.getType() == List.class) {
                List<?> values = (List<?>) component.getAccessor().invoke(record);
                assertTrue(values.isEmpty(), type.getName());
                assertThrows(UnsupportedOperationException.class, values::clear);
            }
            for (int i = 0; i < args.length; i++) if (types[i] == List.class) args[i] = null;
            Object nullLists = type.getDeclaredConstructor(types).newInstance(args);
            for (var component : components) if (component.getType() == List.class) assertEquals(List.of(), component.getAccessor().invoke(nullLists));
            JsonNode json = tree(nullLists);
            for (int i = 0; i < args.length; i++) {
                assertTrue(json.has(components[i].getName()), "Explicit scalar null: " + type.getName() + "." + components[i].getName());
                if (args[i] == null && types[i] != List.class) assertTrue(json.path(components[i].getName()).isNull());
            }
        }
        Object empty = reportType.getDeclaredConstructor(List.class, List.class).newInstance(null, null);
        assertEquals(0, tree(empty).path("uses").size());
        assertTrue(tree(empty).path("uses").isArray());
        var packets = new ArrayList<>(); var uses = new ArrayList<>();
        Object copied = reportType.getDeclaredConstructor(List.class, List.class).newInstance(packets, uses);
        packets.add("mutation"); uses.add("mutation");
        assertEquals(tree(empty), tree(copied));
        for (var component : reportType.getRecordComponents()) assertThrows(UnsupportedOperationException.class, ((List<?>) component.getAccessor().invoke(copied))::clear);
    }

    static void unavailable(DossierAssessment report, String id, String context) {
        assertTrue(report.dossier().valid(), context + Json.write(report));
        assertEquals("unavailable", use(report, id).path("state").stringValue(), context);
        assertFalse(use(report, id).path("reasons").isEmpty(), context);
        assertTrue(report.dossier().diagnostics().stream().anyMatch(d -> d.severity().equals("warning") && !d.code().equals("HISTORY_NOT_CHECKED")), context);
        assertFalse(report.dossier().diagnostics().stream().anyMatch(d -> d.severity().equals("error")), context);
    }
}
