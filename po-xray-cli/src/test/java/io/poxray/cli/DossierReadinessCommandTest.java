package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/** Executes the real command over synthetic dossiers and compares every input byte. */
@QuarkusMainTest
class DossierReadinessCommandTest {
    @Test
    void readinessWithoutSelectorReportsValidButBlockedDossierWithExitZero(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path ledger = fixture(dir);
        Map<Path, byte[]> before = bytes(dir);
        LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--readiness", "--json");
        assertThat(result.exitCode()).isZero();
        JsonNode report = json(result);
        assertThat(report.path("status").stringValue()).isEqualTo("assessed");
        assertThat(report.path("dossier").path("valid").booleanValue()).isTrue();
        assertThat(report.path("questions").get(0).path("status").stringValue()).isEqualTo("deferred");
        assertThat(milestone(report, "A").path("ready").booleanValue()).isFalse();
        assertThat(milestone(report, "B").path("ready").booleanValue()).isTrue();
        assertThat(report.path("coverages").size()).isEqualTo(2);
        unchanged(dir, before);
    }

    @Test
    void milestoneSelectorImpliesReadinessAndDistinguishesReadyBlockedAndUnknown(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path ledger = fixture(dir);
        Map<Path, byte[]> before = bytes(dir);
        for (String selection : List.of("B", "A", "missing")) {
            LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--milestone", selection, "--json");
            assertThat(result.exitCode()).as(selection).isEqualTo(selection.equals("B") ? 0 : 1);
            JsonNode report = json(result);
            assertThat(report.path("dossier").path("valid").booleanValue()).isTrue();
            assertThat(report.path("milestones").size()).isEqualTo(2);
            assertThat(result.getErrorOutput()).contains(selection);
            noStackTrace(result);
            unchanged(dir, before);
        }
    }

    @Test
    void explicitBaselineUpgradeAndDefaultP1OutputBothRemainReadOnly(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path ledger = fixture(dir);
        Path baseline = Files.writeString(dir.resolve("baseline.json"), "{\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":[]}");
        Map<Path, byte[]> before = bytes(dir);
        LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--readiness", "--baseline", baseline.toString(), "--json");
        assertThat(result.exitCode()).isZero();
        JsonNode assessment = json(result);
        assertThat(assessment.path("dossier").path("historyStatus").stringValue()).isEqualTo("verified");
        assertThat(assessment.path("dossier").path("diagnostics").valueStream().map(n -> n.path("code").stringValue()).toList()).contains("HISTORY_FORMAT_UPGRADED");
        LaunchResult legacyShape = launcher.launch("dossier-check", ledger.toString(), "--json");
        assertThat(legacyShape.exitCode()).isZero();
        JsonNode report = json(legacyShape);
        assertThat(report.path("valid").booleanValue()).isTrue();
        assertThat(report.propertyNames()).containsExactly("formatVersion", "migrationId", "ledgerSha256", "valid", "historyStatus", "decisions", "risks", "derivedDocuments", "nextAction", "diagnostics");
        assertThat(report.has("milestones")).isFalse();
        unchanged(dir, before);
    }

    @Test
    void legacySelectionNeverGrantsReadinessAndMalformedV2NeverPrintsPositiveReadiness(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path legacy = Files.writeString(dir.resolve("legacy.json"), "{\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":[]}");
        Map<Path, byte[]> before = bytes(dir);
        LaunchResult legacyResult = launcher.launch("dossier-check", legacy.toString(), "--milestone", "A", "--json");
        assertThat(legacyResult.exitCode()).isEqualTo(1);
        assertThat(json(legacyResult).path("status").stringValue()).isEqualTo("not-assessed");
        assertThat(json(legacyResult).path("milestones").size()).isZero();
        assertThat(legacyResult.getErrorOutput()).isNotBlank();
        noStackTrace(legacyResult);
        unchanged(dir, before);
        Path malformed = Files.writeString(dir.resolve("malformed.json"), "{\"formatVersion\":2,\"formatVersion\":2,\"migrationId\":\"M-1\",\"events\":[]}");
        before = bytes(dir);
        LaunchResult malformedResult = launcher.launch("dossier-check", malformed.toString(), "--readiness", "--json");
        assertThat(malformedResult.exitCode()).isEqualTo(1);
        assertThat(malformedResult.getErrorOutput()).isNotBlank();
        assertThat(malformedResult.getOutput()).doesNotContainPattern("\"ready\"\\s*:\\s*true");
        noStackTrace(malformedResult);
        unchanged(dir, before);
    }

    @Test
    void readinessMarkdownExplainsQuestionExceptionAndSeparateWorkAndApprovalStates(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path ledger = fixture(dir);
        Map<Path, byte[]> before = bytes(dir);
        LaunchResult result = launcher.launch("dossier-check", ledger.toString(), "--readiness");
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("Q", "deferred", "QUESTION_OPEN", "A", "B", "Review evidence", "not-checked");
        assertThat(result.getOutput().toLowerCase(Locale.ROOT)).contains("question", "exception", "work", "complete", "approv");
        noStackTrace(result);
        unchanged(dir, before);
        LaunchResult help = launcher.launch("dossier-check", "--help");
        assertThat(help.exitCode()).isZero();
        assertThat(help.getOutput()).contains("--readiness", "--milestone", "--baseline", "--json");
    }

    private static Path fixture(Path dir) throws IOException {
        return Files.writeString(dir.resolve("dossier.json"), """
                {"formatVersion":2,"migrationId":"M-1","events":[
                  {"id":"SA","type":"section-declare","at":"unknown","source":"analysis:SA","sectionId":"a","text":"Part A"},
                  {"id":"SB","type":"section-declare","at":"unknown","source":"analysis:SB","sectionId":"b","text":"Part B"},
                  {"id":"MA","type":"milestone-declare","at":"unknown","source":"analysis:MA","milestoneId":"A","kind":"design","sections":["a"],"scope":[],"connections":[],"connectionRationale":"No connections apply","requires":[]},
                  {"id":"MB","type":"milestone-declare","at":"unknown","source":"analysis:MB","milestoneId":"B","kind":"design","sections":["b"],"scope":[],"connections":[],"connectionRationale":"No connections apply","requires":[]},
                  {"id":"Q","type":"question-open","at":"unknown","source":"analysis:Q","text":"Question for A","topic":"conversion","owner":"owner","expectedEvidence":"Lab result","sections":["a"],"classification":"scoped","classificationSource":"analysis:Q","classificationReason":"Needed for A design","dependencies":[{"sectionId":"a","neededByMilestoneId":"A"}]},
                  {"id":"DEFER","type":"question-defer","at":"unknown","source":"reply:DEFER","question":"Q","reason":"Review evidence","reviewAt":"review-session"},
                  {"id":"C","type":"question-coverage","at":"unknown","source":"analysis:C","sections":["a","b"],"questions":["Q"],"owner":"owner","evidenceSource":"inventory:C"}
                ]}
                """);
    }

    private static JsonNode milestone(JsonNode report, String id) { return report.path("milestones").valueStream().filter(n -> n.path("id").stringValue().equals(id)).findFirst().orElseThrow(); }
    private static JsonNode json(LaunchResult result) {
        String output = result.getOutput();
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return Json.mapper().readTree(output.substring(start, end + 1));
    }
    private static Map<Path, byte[]> bytes(Path dir) throws IOException {
        Map<Path, byte[]> result = new LinkedHashMap<>();
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) result.put(path, Files.readAllBytes(path));
        }
        return result;
    }
    private static void unchanged(Path dir, Map<Path, byte[]> before) throws IOException {
        Map<Path, byte[]> after = bytes(dir);
        assertThat(after.keySet()).containsExactlyElementsOf(before.keySet());
        before.forEach((path, content) -> assertThat(after.get(path)).as(path.toString()).isEqualTo(content));
    }
    private static void noStackTrace(LaunchResult result) {
        assertThat(result.getOutput() + result.getErrorOutput()).doesNotContain("Exception in thread", "Caused by:", "java.io.IOException:")
                .doesNotContainPattern("(?m)^\\s+at [\\w.$]+\\(");
    }
}
