package io.poxray.migration;

import static io.poxray.migration.BehaviorAnalysisTest.analyze;
import static io.poxray.migration.BehaviorAnalysisTest.inbound;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.SyntheticBehavior;
import io.poxray.SyntheticBehavior.Pair;
import io.poxray.json.Json;
import io.poxray.model.BehaviorReport;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorInputsTest {
    @TempDir Path dir;

    @Test
    void sortedDirectJsonSkipsMalformedNullNonScaAndUnsupportedWithoutMutation() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir.resolve("fixture"));
        Path root = Files.createDirectory(dir.resolve("inputs"));
        Json.write(pair.sca(), root.resolve("z.json"));
        Json.write(pair.sca(), root.resolve("a.JSON"));
        Files.writeString(root.resolve("b-null.json"), "null");
        Files.writeString(root.resolve("c-malformed.json"), "{not-json");
        Files.writeString(root.resolve("d-non-sca.json"), "{\"schemaVersion\":\"0.1\"}");
        Json.write(schema(pair.sca(), "future"), root.resolve("e-future.json"));
        Json.write(pair.sca(), root.resolve("ignored.txt"));
        Json.write(pair.sca(), Files.createDirectory(root.resolve("nested")).resolve("child.json"));
        Map<Path, byte[]> before = new LinkedHashMap<>();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) before.put(file, Files.readAllBytes(file));
        }
        BehaviorReport report = BehaviorAnalysis.read(root, pair.icosJson());
        assertThat(report.data().paths().inputs()).extracting(input -> input.label())
                .containsExactly(root.resolve("a.JSON").toString(), root.resolve("z.json").toString());
        assertThat(report.processes()).hasSize(2);
        List<Issue> skipped = report.issues().stream().filter(i -> "BEHAVIOR_INPUT_UNAVAILABLE".equals(i.code())).toList();
        assertThat(skipped).hasSize(4);
        assertThat(skipped).extracting(Issue::source).containsExactly(root.resolve("b-null.json").toString(),
                root.resolve("c-malformed.json").toString(), root.resolve("d-non-sca.json").toString(), root.resolve("e-future.json").toString());
        for (var entry : before.entrySet()) assertThat(Files.readAllBytes(entry.getKey())).containsExactly(entry.getValue());
    }

    @Test
    void missingRootThrowsAndZeroUsableInputsRemainExplicit() throws IOException {
        assertThatThrownBy(() -> BehaviorAnalysis.read(dir.resolve("missing"), null)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> BehaviorAnalysis.read(null, null));
        BehaviorReport empty = BehaviorAnalysis.read(Files.createDirectory(dir.resolve("empty")), null);
        assertThat(empty.processes()).isEmpty();
        assertThat(empty.issues()).extracting(Issue::code).contains("BEHAVIOR_NO_USABLE_EXPORT");
        Path malformed = Files.writeString(dir.resolve("malformed.json"), "[broken");
        BehaviorReport rejected = BehaviorAnalysis.read(malformed, null);
        assertThat(rejected.processes()).isEmpty();
        assertThat(rejected.issues()).extracting(Issue::code).contains("BEHAVIOR_INPUT_UNAVAILABLE", "BEHAVIOR_NO_USABLE_EXPORT");
    }

    @Test
    void optionalIcoReadFailuresReturnUnavailableReports() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir.resolve("fixture"));
        Path nullDocument = Files.writeString(dir.resolve("null.json"), "null");
        Path malformed = Files.writeString(dir.resolve("malformed.json"), "{");
        Path directory = Files.createDirectory(dir.resolve("not-a-file"));
        for (Path unavailable : List.of(dir.resolve("missing.json"), nullDocument, malformed, directory)) {
            BehaviorReport report = BehaviorAnalysis.read(pair.exportJson(), unavailable);
            assertThat(report.integration().input()).isEqualTo(unavailable.toString());
            assertThat(report.integration().availability()).isEqualTo("unavailable");
            assertThat(report.issues()).extracting(Issue::code).contains("BEHAVIOR_ICO_UNAVAILABLE");
            assertThat(report.processes()).hasSize(1);
            assertThat(inbound(report).matches()).isEmpty();
            assertThat(inbound(report).reasons()).isNotEmpty();
        }
    }

    @Test
    void optionalIcoStatesAndSelectionRemainIndependent() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        IntegrationInventory raw = pair.icos();
        BehaviorReport absent = BehaviorAnalysis.of(List.of(pair.sca()), List.of("s"), null, "original label");
        assertThat(absent.integration().availability()).isEqualTo("absent");
        assertThat(absent.integration().selection()).isEqualTo("unknown");
        assertThat(absent.integration().input()).isEqualTo("original label");
        IntegrationInventory missing = configuration(raw, "0.1", null, new IntegrationInventory.Query("BPM", "both"), List.of());
        BehaviorReport missingReport = analyze(pair.sca(), missing);
        assertThat(missingReport.integration().availability()).isEqualTo("missing-icos");
        assertThat(missingReport.integration().selection()).isEqualTo("query-limited");
        assertThat(missingReport.integration().channels()).hasSize(raw.channels().size());
        assertThat(missingReport.integration().source()).isEqualTo(raw.source());
        assertThat(inbound(missingReport).matches()).isEmpty();
        IntegrationInventory unsupported = configuration(raw, "future", raw.icos(), null, List.of());
        BehaviorReport unsupportedReport = analyze(pair.sca(), unsupported);
        assertThat(unsupportedReport.integration().availability()).isEqualTo("unsupported-schema");
        assertThat(unsupportedReport.integration().icos()).hasSize(2);
        assertThat(unsupportedReport.integration().channelUses()).isEmpty();
        assertThat(inbound(unsupportedReport).matches()).isEmpty();
        BehaviorReport empty = analyze(pair.sca(), configuration(raw, "0.1", List.of(), null, List.of()));
        assertThat(empty.integration().availability()).isEqualTo("available");
        assertThat(empty.integration().selection()).isEqualTo("unknown");
        assertThat(empty.integration().icos()).isEmpty();
        BehaviorReport legacy = analyze(pair.sca(), configuration(raw, null, raw.icos(), new IntegrationInventory.Query(" ", ""), List.of()));
        assertThat(legacy.integration().availability()).isEqualTo("available");
        assertThat(legacy.integration().selection()).isEqualTo("unknown");
        assertThat(legacy.integration().reasons()).contains("legacy-schema");
        List<Issue> issues = Arrays.asList(null, new Issue(Issue.WARNING, "SYNTHETIC_SOURCE", "retained", null, null));
        BehaviorReport sourceIssues = analyze(pair.sca(), configuration(raw, "0.1", raw.icos(), null, issues));
        assertThat(sourceIssues.integration().selection()).isEqualTo("source-issues");
        assertThat(sourceIssues.integration().issues()).containsExactlyElementsOf(issues);
        BehaviorReport limited = analyze(pair.sca(), configuration(raw, "0.1", raw.icos(), new IntegrationInventory.Query(null, "both"), issues));
        assertThat(limited.integration().selection()).isEqualTo("query-limited");
    }

    static ScaInventory schema(ScaInventory source, String schema) {
        return new ScaInventory(schema, source.generator(), source.source(), source.developmentComponents(), source.bpm(), source.ejb(), source.issues(), source.unmapped());
    }
    static IntegrationInventory configuration(IntegrationInventory source, String schema, List<IntegrationInventory.Ico> icos,
            IntegrationInventory.Query query, List<Issue> issues) {
        return new IntegrationInventory(schema, source.generator(), source.source(), query, source.components(), icos, source.channels(),
                source.classicConfiguration(), source.esr(), issues);
    }
}
