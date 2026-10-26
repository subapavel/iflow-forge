package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.SyntheticBehavior;
import io.poxray.json.Json;
import io.poxray.model.BehaviorReport;
import io.poxray.model.Issue;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@QuarkusMainTest
class BehaviorCommandTest {
    @Test
    void commandIsRegisteredAndDocumentsOfflineArguments(QuarkusMainLauncher launcher) {
        LaunchResult root = launcher.launch("--help");
        assertThat(root.exitCode()).isZero();
        assertThat(root.getOutput()).contains("behavior");
        LaunchResult help = launcher.launch("behavior", "--help");
        assertThat(help.exitCode()).isZero();
        assertThat(help.getOutput()).contains("--icos", "--json");
    }

    @Test
    void jsonContainsActualParsedSourceAndConfigurationWithoutInputChanges(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        var pair = new SyntheticBehavior().write(dir);
        var before = SyntheticBehavior.hashes(pair);
        LaunchResult result = launcher.launch("behavior", pair.exportJson().toString(), "--icos", pair.icosJson().toString(), "--json");
        assertThat(result.exitCode()).isZero();
        BehaviorReport report = read(result);
        assertThat(report.analysisVersion()).isEqualTo("bpm-behavior-v1");
        assertThat(report.data().paths().processes()).singleElement().satisfies(p -> assertThat(p.name()).isEqualTo("Synthetic behavior"));
        assertThat(report.data().processes().getFirst().declarations()).isNotEmpty();
        assertThat(report.faults().processes().getFirst().declarations()).anySatisfy(d -> assertThat(d.role()).isEqualTo("boundary-trigger"));
        assertThat(report.integration().icos().getFirst().configuration().noReceiverBehaviour()).isEqualTo("error");
        assertThat(report.integration().channels().getFirst().configuration().attributes()).containsEntry("syntheticValue", "configured-value");
        assertThat(report.processes().getFirst().interfaces().getFirst().source().name()).isEqualTo(SyntheticBehavior.IN);
        assertThat(report.processes().getFirst().calls()).allSatisfy(c -> {
            assertThat(c.acknowledgment()).isEqualTo("unknown");
            assertThat(c.transaction()).isEqualTo("unknown");
        });
        assertThat(SyntheticBehavior.hashes(pair)).isEqualTo(before);
    }

    @Test
    void defaultMarkdownContainsBehaviorAndEscapesSourceText(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        var pair = new SyntheticBehavior().write(dir);
        LaunchResult result = launcher.launch("behavior", pair.exportJson().toString(), "--icos", pair.icosJson().toString());
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("Inbound\\|Contract", "Fault\\|Declared", "pre-process", "unknown");
        assertThat(result.getOutput().toLowerCase(java.util.Locale.ROOT)).contains("configured", "coverage", "transaction", "acknowledg");
    }

    @Test
    void missingExportRootFailsAndNamesPath(QuarkusMainLauncher launcher, @TempDir Path dir) {
        LaunchResult result = launcher.launch("behavior", dir.resolve("missing-export").toString());
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.getErrorOutput()).contains("missing-export");
    }

    @Test
    void optionalBadIcoIsDiagnosticSuccess(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        var pair = new SyntheticBehavior().write(dir);
        Path malformed = Files.writeString(dir.resolve("bad-ico.json"), "{");
        LaunchResult result = launcher.launch("behavior", pair.exportJson().toString(), "--icos", malformed.toString(), "--json");
        assertThat(result.exitCode()).isZero();
        BehaviorReport report = read(result);
        assertThat(report.integration().availability()).isEqualTo("unavailable");
        assertThat(report.issues()).extracting(Issue::code).contains("BEHAVIOR_ICO_UNAVAILABLE");
        assertThat(report.processes()).hasSize(1);
    }

    @Test
    void emptyDirectoryProducesExplicitWarning(QuarkusMainLauncher launcher, @TempDir Path dir) {
        LaunchResult result = launcher.launch("behavior", dir.toString(), "--json");
        assertThat(result.exitCode()).isZero();
        BehaviorReport report = read(result);
        assertThat(report.processes()).isEmpty();
        assertThat(report.issues()).extracting(Issue::code).contains("BEHAVIOR_NO_USABLE_EXPORT");
    }

    private static BehaviorReport read(LaunchResult result) {
        String output = result.getOutput();
        return Json.mapper().readValue(output.substring(output.indexOf('{'), output.lastIndexOf('}') + 1), BehaviorReport.class);
    }
}
