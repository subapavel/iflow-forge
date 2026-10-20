package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import io.poxray.model.BpmInventoryReport;
import io.poxray.model.Issue;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code po-xray bpm-inventory <export-dir> [--json]} on the committed synthetic export of the migration skill. */
@QuarkusMainTest
class BpmInventoryCommandTest {

    private static final Path EXPORT = Path.of("..", "skills", "sap-po-migration", "fixtures", "bpm-export");

    private static String json(String stdout) {
        return stdout.substring(stdout.indexOf('{'), stdout.lastIndexOf('}') + 1);
    }

    @Test
    void printsMarkdownOfTheFixtureExport(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("bpm-inventory", EXPORT.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("# BPM inventory", "## Processes", "### OrderApproval",
                "| automatedActivity | 2 |", "## EJB roles", "## Totals", "## Issues", "NOT_SCA_INVENTORY",
                "notes.json");
    }

    @Test
    void printsJsonWithOneHumanTaskAndTheSkippedFile(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("bpm-inventory", "--json", EXPORT.toString());

        assertThat(result.exitCode()).isZero();
        BpmInventoryReport report = Json.mapper().readValue(json(result.getOutput()), BpmInventoryReport.class);
        assertThat(report.processes()).singleElement().satisfies(p -> {
            assertThat(p.dc()).isEqualTo("synth/bpm");
            assertThat(p.name()).isEqualTo("OrderApproval");
            assertThat(p.stepsTotal()).isEqualTo(7);
            assertThat(p.humanTasks()).isEqualTo(1);
            assertThat(p.unknownMetaclasses()).containsExactly("HumanActivity");
        });
        assertThat(report.totals().humanTasks()).isEqualTo(1);
        assertThat(report.totals().beansByRole()).containsEntry("bpmFunction", 2).containsEntry("adapterModule", 1)
                .containsEntry("other", 1);
        assertThat(report.ejb()).singleElement().satisfies(e -> assertThat(e.dc()).isEqualTo("synth/ejb"));
        assertThat(report.issues()).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("NOT_SCA_INVENTORY");
            assertThat(i.message()).contains("notes.json");
        });
    }

    @Test
    void readsASingleInventoryFile(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("bpm-inventory", "--json", EXPORT.resolve("SYNTH_SCA.json").toString());

        assertThat(result.exitCode()).isZero();
        BpmInventoryReport report = Json.mapper().readValue(json(result.getOutput()), BpmInventoryReport.class);
        assertThat(report.totals().processes()).isEqualTo(1);
        assertThat(report.issues()).extracting(Issue::code).doesNotContain("NOT_SCA_INVENTORY");
    }

    @Test
    void aMissingPathFailsAndNamesIt(QuarkusMainLauncher launcher, @TempDir Path dir) {
        Path missing = dir.resolve("no-such-export");

        LaunchResult result = launcher.launch("bpm-inventory", missing.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.getErrorOutput()).contains("no-such-export");
    }

    @Test
    void aDirectoryWithoutAUsableInventoryFails(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("notes.json"), "{\"note\":\"not an inventory\"}");

        LaunchResult result = launcher.launch("bpm-inventory", dir.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.getErrorOutput()).isNotBlank();
        assertThat(result.getOutput()).doesNotContain("# BPM inventory");
    }
}
