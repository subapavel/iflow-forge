package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/** Real command launches over private-looking but entirely synthetic temporary files. */
@QuarkusMainTest
class DossierCheckCommandTest {

    @Test
    void validJsonContainsScopedApprovalDerivedStateAndHistoryWarningWithoutWritingInputs(
            QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path ledger = fixture(dir);
        Map<Path, byte[]> before = inputBytes(dir);

        LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--json");

        assertThat(result.exitCode()).isZero();
        JsonNode report = json(result);
        assertThat(report.path("valid").booleanValue()).isTrue();
        assertThat(report.path("formatVersion").intValue()).isEqualTo(1);
        assertThat(report.path("migrationId").stringValue()).isEqualTo("M-1");
        assertThat(report.path("ledgerSha256").stringValue()).isEqualTo(sha256(before.get(ledger)));
        assertThat(report.path("historyStatus").stringValue()).isEqualTo("not-checked");
        assertThat(report.path("decisions").size()).isEqualTo(1);
        JsonNode decision = report.path("decisions").get(0);
        assertThat(decision.path("id").stringValue()).isEqualTo("D1");
        assertThat(decision.path("status").stringValue()).isEqualTo("approved");
        assertThat(decision.path("activeScopes").size()).isEqualTo(1);
        assertThat(decision.path("activeScopes").get(0).path("sectionId").stringValue()).isEqualTo("placement");
        assertThat(decision.path("activeScopes").get(0).path("revision").stringValue()).isEqualTo("r1");
        assertThat(decision.path("approvalEventIds").get(0).stringValue()).isEqualTo("A1");
        assertThat(report.path("derivedDocuments").get(0).path("status").stringValue()).isEqualTo("current");
        assertThat(report.path("nextAction").stringValue()).isEqualTo("Review the remaining rules");
        assertThat(report.path("diagnostics").valueStream().map(node -> node.path("code").stringValue()).toList())
                .contains("HISTORY_NOT_CHECKED");
        assertUnchanged(dir, before);
    }

    @Test
    void markdownContainsActualCurrentDesignAndWarnings(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws IOException {
        Path ledger = fixture(dir);
        Map<Path, byte[]> before = inputBytes(dir);

        LaunchResult result = launcher.launch("dossier-check", ledger.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("M-1", "D1", "approved", "placement", "r1", "plan.md", "current",
                "Review the remaining rules", "not-checked", "HISTORY_NOT_CHECKED", sha256(before.get(ledger)));
        assertThat(result.getOutput().toLowerCase(Locale.ROOT)).contains("question", "milestone", "not");
        assertUnchanged(dir, before);
    }

    @Test
    void explicitBaselineVerifiesPrefixAndPreservesEmptyDiagnosticArray(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws IOException {
        Path ledger = fixture(dir);
        Path baseline = dir.resolve("archive/baseline.json");
        Map<Path, byte[]> before = inputBytes(dir);

        LaunchResult result = launcher.launch("dossier-check", "--baseline", baseline.toString(), "--json", ledger.toString());

        assertThat(result.exitCode()).isZero();
        JsonNode report = json(result);
        assertThat(report.path("valid").booleanValue()).isTrue();
        assertThat(report.path("historyStatus").stringValue()).isEqualTo("verified");
        assertThat(report.path("diagnostics").isArray()).isTrue();
        assertThat(report.path("diagnostics").size()).isZero();
        assertThat(report.path("risks").isArray()).isTrue();
        assertThat(report.path("risks").size()).isZero();
        assertUnchanged(dir, before);
    }

    @Test
    void changedBaselineHistoryReturnsExitOneAndJsonDiagnostic(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws IOException {
        Path ledger = fixture(dir);
        Files.writeString(ledger, Files.readString(ledger).replace("Review the remaining rules", "Changed action"));
        Map<Path, byte[]> before = inputBytes(dir);

        LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--baseline",
                dir.resolve("archive/baseline.json").toString(), "--json");

        assertThat(result.exitCode()).isEqualTo(1);
        JsonNode report = json(result);
        assertThat(report.path("valid").booleanValue()).isFalse();
        assertThat(report.path("historyStatus").stringValue()).isEqualTo("changed");
        assertThat(report.path("diagnostics").valueStream().map(node -> node.path("code").stringValue()).toList())
                .contains("HISTORY_CHANGED");
        assertUnchanged(dir, before);
    }

    @Test
    void invalidDossierReturnsExitOneAndNonAuthoritativeMarkdown(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws IOException {
        Path ledger = fixture(dir);
        Files.writeString(dir.resolve("placement-r1.txt"), "changed synthetic content");
        Map<Path, byte[]> before = inputBytes(dir);

        LaunchResult result = launcher.launch("dossier-check", ledger.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.getOutput()).contains("SNAPSHOT_CHANGED", "M-1");
        assertThat(result.getOutput().toLowerCase(Locale.ROOT)).contains("invalid").containsPattern("not[^\\n]*authoritative");
        assertNoStackTrace(result);
        assertUnchanged(dir, before);
    }

    @Test
    void parseErrorsIncludingDuplicateKeysFailWithoutStackTrace(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws IOException {
        for (String invalid : List.of("{ not json", "{\"formatVersion\":1,\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":[]}")) {
            Path ledger = Files.writeString(dir.resolve("broken.json"), invalid);
            Map<Path, byte[]> before = inputBytes(dir);

            LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--json");

            assertThat(result.exitCode()).isEqualTo(1);
            assertThat(result.getErrorOutput()).isNotBlank();
            assertThat(result.getOutput()).doesNotContain("\"valid\" : true", "\"valid\":true");
            assertNoStackTrace(result);
            assertUnchanged(dir, before);
        }
    }

    @Test
    void missingFileFailsWithUsefulMessage(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Map<Path, byte[]> before = inputBytes(dir);

        LaunchResult result = launcher.launch("dossier-check", dir.resolve("missing.json").toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.getErrorOutput()).contains("missing.json");
        assertNoStackTrace(result);
        assertUnchanged(dir, before);
    }

    @Test
    void emptySyntheticLedgerIsValidButDoesNotClaimVerifiedHistory(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws IOException {
        Path ledger = Files.writeString(dir.resolve("dossier.json"), "{\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":[]}");
        Map<Path, byte[]> before = inputBytes(dir);

        LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--json");

        assertThat(result.exitCode()).isZero();
        JsonNode report = json(result);
        assertThat(report.path("valid").booleanValue()).isTrue();
        assertThat(report.path("historyStatus").stringValue()).isEqualTo("not-checked");
        for (String name : List.of("decisions", "risks", "derivedDocuments")) {
            assertThat(report.path(name).isArray()).as(name).isTrue();
            assertThat(report.path(name).size()).as(name).isZero();
        }
        assertUnchanged(dir, before);
    }

    @Test
    void helpDescribesTheCommandAndMissingArgumentIsUsageFailure(QuarkusMainLauncher launcher) {
        LaunchResult help = launcher.launch("dossier-check", "--help");
        assertThat(help.exitCode()).isZero();
        assertThat(help.getOutput()).contains("dossier-check", "--baseline", "--json");

        LaunchResult missing = launcher.launch("dossier-check");
        assertThat(missing.exitCode()).isNotZero();
        assertThat((missing.getOutput() + missing.getErrorOutput()).toLowerCase(Locale.ROOT))
                .containsPattern("argument|required|usage");
        assertNoStackTrace(missing);
    }

    private static JsonNode json(LaunchResult result) {
        String output = result.getOutput();
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        assertThat(start).as("JSON report begins on stdout").isGreaterThanOrEqualTo(0);
        assertThat(end).as("JSON report ends on stdout").isGreaterThan(start);
        return Json.mapper().readTree(output.substring(start, end + 1));
    }

    private static void assertNoStackTrace(LaunchResult result) {
        assertThat(result.getOutput() + result.getErrorOutput())
                .doesNotContain("Exception in thread", "Caused by:", "java.io.IOException:", "tools.jackson.")
                .doesNotContainPattern("(?m)^\\s+at [\\w.$]+\\(");
    }

    private static Path fixture(Path dir) throws IOException {
        byte[] snapshot = "approved placement\n".getBytes(StandardCharsets.UTF_8);
        Files.write(dir.resolve("placement-r1.txt"), snapshot);
        Files.writeString(dir.resolve("plan.md"), "Synthetic derived prose\n");
        String digest = sha256(snapshot);
        String scope = "{\"sectionId\":\"placement\",\"revision\":\"r1\",\"path\":\"placement-r1.txt\",\"sha256\":\"" + digest + "\"}";
        String input = """
                {"formatVersion":1,"migrationId":"M-1","events":[
                  {"id":"D1","type":"propose","at":"unknown","source":"reply:D1","text":"Placement design","scope":[%s]},
                  {"id":"A1","type":"approve","at":"2026-10-26","source":"reply:A1","decision":"D1","scope":[%s]},
                  {"id":"F1","type":"derived-document","at":"unknown","source":"reply:F1","documentId":"plan","path":"plan.md","required":true,
                   "consumes":[{"decisionId":"D1","sectionId":"placement","revision":"r1","sha256":"%s"}]},
                  {"id":"N1","type":"next-action","at":"unknown","source":"reply:N1","text":"Review the remaining rules"}
                ]}
                """.formatted(scope, scope, digest);
        Path ledger = Files.writeString(dir.resolve("dossier.json"), input);
        Files.createDirectories(dir.resolve("archive"));
        Files.writeString(dir.resolve("archive/baseline.json"), input);
        return ledger;
    }

    private static Map<Path, byte[]> inputBytes(Path dir) throws IOException {
        Map<Path, byte[]> bytes = new LinkedHashMap<>();
        try (var files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                bytes.put(file, Files.readAllBytes(file));
            }
        }
        return bytes;
    }

    private static void assertUnchanged(Path dir, Map<Path, byte[]> before) throws IOException {
        Map<Path, byte[]> after = inputBytes(dir);
        assertThat(after.keySet()).containsExactlyElementsOf(before.keySet());
        before.forEach((file, bytes) -> assertThat(after.get(file)).as(file.toString()).isEqualTo(bytes));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
