package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.SyntheticBpmSca;
import io.poxray.json.Json;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Existing full SCA commands must carry the additive BPM source evidence. */
@QuarkusMainTest
class BpmSourceEvidenceCommandTest {
    @Test
    void fullScaJsonContainsRetainedMappingAndMissingEndpointEvidence(QuarkusMainLauncher launcher, @TempDir Path dir) {
        Path sca = SyntheticBpmSca.write(dir.resolve("SYNTH_BPM.sca"), SyntheticBpmSca.mappedProcess());
        LaunchResult result = launcher.launch("json", sca.toString());

        assertThat(result.exitCode()).isZero();
        String stdout = result.getOutput();
        var inventory = Json.mapper().readTree(stdout.substring(stdout.indexOf('{'), stdout.lastIndexOf('}') + 1));
        assertThat(inventory.path("schemaVersion").asString()).isEqualTo("0.1");
        var source = inventory.path("bpm").get(0).path("sourceEvidence");
        assertThat(source.path("formatVersion").asInt()).isEqualTo(1);
        assertThat(source.path("selectionVersion").asString()).isEqualTo("bpm-source-v1");
        assertThat(source.path("elements").toString()).contains("initial-expr", "root-qname", "mapped|value");
        assertThat(source.path("links").toString()).contains("missing-target", "missing-part", "missing",
                "Galaxy.Mapping.MappingPartHasChildren");
        assertThat(inventory.path("issues").toString()).contains("BPM_SOURCE_REFERENCE_UNRESOLVED");
    }

    @Test
    void fullScaExportWritesEvidenceAndInitialAndStructuralMappings(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws IOException {
        Path sca = SyntheticBpmSca.write(dir.resolve("SYNTH_BPM.sca"), SyntheticBpmSca.mappedProcess());
        byte[] original = Files.readAllBytes(sca);
        Path output = dir.resolve("export");
        LaunchResult result = launcher.launch("export", sca.toString(), "-o", output.toString(), "-l", "en");

        assertThat(result.exitCode()).isZero();
        var json = Json.mapper().readTree(Files.readString(output.resolve("SYNTH_BPM.json")));
        assertThat(json.path("bpm").get(0).path("sourceEvidence").path("selectionVersion").asString())
                .isEqualTo("bpm-source-v1");
        String markdown = Files.readString(output.resolve("SYNTH_BPM.md"));
        assertThat(markdown).contains("bpm-source-v1", "seed\\|value", "mapped\\|value", "target/leaf", "missing-target",
                "sequential", "false", SyntheticBpmSca.PROCESS);
        assertThat(markdown.toLowerCase(java.util.Locale.ROOT)).contains("structural", "disabled");
        assertThat(output.resolve("SYNTH_BPM.jira.txt")).exists();
        assertThat(Files.readAllBytes(sca)).containsExactly(original);
    }
}
