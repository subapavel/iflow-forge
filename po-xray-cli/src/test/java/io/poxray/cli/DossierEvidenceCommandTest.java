package io.poxray.cli;

import static org.junit.jupiter.api.Assertions.*;

import io.poxray.json.Json;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

@QuarkusMainTest
class DossierEvidenceCommandTest {
    @Test void readinessJsonAndMarkdownExposeActualEvidenceWithoutChangingFiles(QuarkusMainLauncher launcher, @TempDir Path dir) throws Exception {
        Path ledger = fixture(dir); var before = hashes(dir);
        LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--readiness", "--json");
        assertEquals(0, result.exitCode(), result.getErrorOutput());
        JsonNode report = json(result);
        assertTrue(report.path("dossier").path("valid").booleanValue());
        assertEquals("supported", report.path("evidence").path("uses").get(0).path("state").stringValue());
        assertEquals("owner-confirmed", report.path("evidence").path("packets").get(0).path("packet").path("sourceKind").stringValue());
        assertEquals("deployed-old", report.path("evidence").path("packets").get(0).path("packet").path("versions").path("deployedVersion").stringValue());
        LaunchResult markdown = launcher.launch("dossier-check", ledger.toString(), "--readiness");
        assertEquals(0, markdown.exitCode());
        for (String text : List.of("Synthetic response claim", "Independent reviewer", "No runtime guarantee", "supported", "deployed-old")) assertTrue(markdown.getOutput().contains(text), text);
        LaunchResult plain = launcher.launch("dossier-check", ledger.toString(), "--json");
        assertEquals(0, plain.exitCode()); assertTrue(json(plain).path("valid").booleanValue()); assertFalse(json(plain).has("evidence"));
        assertEquals(before, hashes(dir));
    }

    @Test void milestoneExitTracksRequiredEvidenceLossAndRestoreOnlyForAffectedScope(QuarkusMainLauncher launcher, @TempDir Path dir) throws Exception {
        Path ledger = fixture(dir); Path packet = dir.resolve("packet.json"); byte[] bytes = Files.readAllBytes(packet);
        assertEquals(0, launcher.launch("dossier-check", ledger.toString(), "--milestone", "A", "--json").exitCode());
        Files.delete(packet); var before = hashes(dir);
        LaunchResult affected = launcher.launch("dossier-check", ledger.toString(), "--milestone", "A", "--json");
        assertEquals(1, affected.exitCode()); assertTrue(json(affected).path("dossier").path("valid").booleanValue());
        assertEquals("unavailable", json(affected).path("evidence").path("uses").get(0).path("state").stringValue());
        assertFalse(json(affected).path("milestones").get(0).path("ready").booleanValue());
        assertEquals(0, launcher.launch("dossier-check", ledger.toString(), "--milestone", "B", "--json").exitCode());
        assertEquals(0, launcher.launch("dossier-check", ledger.toString(), "--readiness", "--json").exitCode());
        assertEquals(before, hashes(dir));
        Files.write(packet, bytes);
        assertEquals(0, launcher.launch("dossier-check", ledger.toString(), "--milestone", "A", "--json").exitCode());
    }

    private static Path fixture(Path dir) throws Exception {
        Path snapshot = Files.writeString(dir.resolve("snapshot.txt"), "Synthetic design");
        String digest = hash(Files.readAllBytes(snapshot));
        var dep = obj("decisionId", "D", "sectionId", "a", "revision", "r1", "sha256", digest);
        var scope = obj("sectionId", "a", "revision", "r1", "path", "snapshot.txt", "sha256", digest);
        Path packet = Files.writeString(dir.resolve("packet.json"), Json.write(obj("formatVersion", 1, "id", "P", "author", "Synthetic author", "sourceKind", "owner-confirmed",
                "sourceLocator", "https://invalid.example/attribution", "sourceSha256", null, "observedAt", null,
                "versions", obj("exportLabel", null, "packageRelease", "release-new", "bundleVersion", "bundle-new", "targetVersion", null, "designTimeVersion", null, "deployedVersion", "deployed-old"),
                "limitations", "No runtime guarantee", "kbDifference", "Synthetic difference", "claims", List.of(obj("id", "claim", "text", "Synthetic response claim", "scope", List.of(dep), "limitations", "Single response")))));
        Path review = Files.writeString(dir.resolve("review.json"), Json.write(obj("formatVersion", 1, "packetId", "P", "packetSha256", hash(Files.readAllBytes(packet)),
                "reviewer", "Independent reviewer", "reviewedAt", "2026-10-21", "claims", List.of(obj("claimId", "claim", "scope", List.of(dep), "verdict", "PASS", "limitations", "Attributed only")))));
        List<Object> events = new ArrayList<>();
        events.add(event("D", "propose", "text", "Synthetic proposal", "scope", List.of(scope)));
        events.add(event("SB", "section-declare", "sectionId", "b", "text", "Independent section"));
        for (String id : List.of("A", "B")) events.add(event("MD" + id, "milestone-declare", "milestoneId", id, "kind", "design", "sections", List.of(id.toLowerCase()),
                "scope", id.equals("A") ? List.of(dep) : List.of(), "connections", List.of(), "connectionRationale", "No connections", "requires", List.of()));
        events.add(event("C", "question-coverage", "sections", List.of("a", "b"), "questions", List.of(), "owner", "owner", "evidenceSource", "inventory:C"));
        events.add(event("E", "evidence-register", "packetPath", "packet.json", "packetSha256", hash(Files.readAllBytes(packet)), "reviewPath", "review.json", "reviewSha256", hash(Files.readAllBytes(review))));
        events.add(event("U", "evidence-use", "scope", dep, "evidence", "E", "claimId", "claim", "text", "Synthetic response claim", "replaces", null));
        return Files.writeString(dir.resolve("dossier.json"), Json.write(obj("formatVersion", 3, "migrationId", "M-1", "events", events)));
    }
    private static Map<String, Object> event(String id, String type, Object... fields) {
        var result = obj("id", id, "type", type, "at", "unknown", "source", "reply:" + id); result.putAll(obj(fields)); return result;
    }
    private static Map<String, Object> obj(Object... fields) { Map<String, Object> result = new LinkedHashMap<>(); for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]); return result; }
    private static String hash(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
    private static Map<Path, String> hashes(Path dir) throws Exception {
        Map<Path, String> result = new LinkedHashMap<>(); try (var paths = Files.walk(dir)) { for (Path path : paths.filter(Files::isRegularFile).toList()) result.put(path, hash(Files.readAllBytes(path))); } return result;
    }
    private static JsonNode json(LaunchResult result) {
        String text = result.getOutput(); int begin = text.indexOf('{'), end = text.lastIndexOf('}');
        assertTrue(begin >= 0 && end > begin, text + result.getErrorOutput()); return Json.mapper().readTree(text.substring(begin, end + 1));
    }
}
