package io.poxray.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.SyntheticBehavior;
import io.poxray.json.Json;
import io.poxray.migration.BehaviorAnalysis;
import io.poxray.model.BehaviorReport;
import io.poxray.model.Issue;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorToolTest {
    @TempDir Path dir;

    @Test
    void publicToolHasNameDescriptionAndOptionalIcoArgument() throws NoSuchMethodException {
        var method = PoXrayTools.class.getMethod("bpm_behavior", String.class, String.class);
        assertThat(method.getReturnType()).isEqualTo(String.class);
        Tool tool = method.getAnnotation(Tool.class);
        assertThat(tool).isNotNull();
        assertThat(tool.name()).isEqualTo("bpm_behavior");
        assertThat(tool.description()).isNotBlank();
        ToolArg optional = method.getParameters()[1].getAnnotation(ToolArg.class);
        assertThat(optional).isNotNull();
        assertThat(optional.defaultValue()).isEmpty();
    }

    @Test
    void actualSmallToolResultContainsParsedBehaviorInline() throws IOException {
        var pair = new SyntheticBehavior().write(dir);
        var before = SyntheticBehavior.hashes(pair);
        String output = new PoXrayTools().bpm_behavior(pair.exportJson().toString(), pair.icosJson().toString());
        var json = Json.mapper().readTree(output);
        assertThat(json.path("file").isMissingNode()).isTrue();
        assertThat(output.length()).isLessThanOrEqualTo(80_000);
        BehaviorReport report = Json.mapper().readValue(output, BehaviorReport.class);
        assertThat(report.data().paths().processes().getFirst().name()).isEqualTo("Synthetic behavior");
        assertThat(report.integration().icos().getFirst().configuration().noReceiverBehaviour()).isEqualTo("error");
        assertThat(report.processes().getFirst().interfaces().getFirst().source().name()).isEqualTo(SyntheticBehavior.IN);
        assertThat(report.faults().processes().getFirst().declarations()).isNotEmpty();
        assertThat(Json.write(report)).isEqualTo(Json.write(BehaviorAnalysis.read(pair.exportJson(), pair.icosJson())));
        assertThat(SyntheticBehavior.hashes(pair)).isEqualTo(before);
    }

    @Test
    void actualLargeToolResultSpillsFullJsonWithoutTruncation() throws IOException {
        String payload = "BEGIN-SYNTHETIC-" + "x".repeat(90_000) + "-END-SYNTHETIC";
        var pair = new SyntheticBehavior().write(dir, payload);
        var before = SyntheticBehavior.hashes(pair);
        String output = new PoXrayTools().bpm_behavior(pair.exportJson().toString(), pair.icosJson().toString());
        var envelope = Json.mapper().readTree(output);
        assertThat(output.length()).isLessThan(2_000);
        assertThat(envelope.path("message").asString()).contains("characters", "file");
        Path file = Path.of(envelope.path("file").asString());
        assertThat(file.getFileName().toString()).startsWith("bpm_behavior-").endsWith(".json");
        try {
            String contents = Files.readString(file);
            assertThat(contents.length()).isGreaterThan(80_000);
            BehaviorReport report = Json.mapper().readValue(contents, BehaviorReport.class);
            assertThat(report.integration().channels().getFirst().configuration().attributes()).containsEntry("syntheticValue", payload);
            assertThat(Json.write(report)).isEqualTo(Json.write(BehaviorAnalysis.read(pair.exportJson(), pair.icosJson())));
            assertThat(report.processes()).hasSize(1);
            assertThat(SyntheticBehavior.hashes(pair)).isEqualTo(before);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void missingRootBecomesToolErrorAndOptionalBadIcoStaysReport() throws IOException {
        PoXrayTools tool = new PoXrayTools();
        assertThatThrownBy(() -> tool.bpm_behavior(dir.resolve("missing-export").toString(), ""))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("missing-export");
        var pair = new SyntheticBehavior().write(dir);
        Path bad = Files.writeString(dir.resolve("bad.json"), "null");
        BehaviorReport report = Json.mapper().readValue(tool.bpm_behavior(pair.exportJson().toString(), bad.toString()), BehaviorReport.class);
        assertThat(report.integration().availability()).isEqualTo("unavailable");
        assertThat(report.issues()).extracting(Issue::code).contains("BEHAVIOR_ICO_UNAVAILABLE");
        BehaviorReport absent = Json.mapper().readValue(tool.bpm_behavior(pair.exportJson().toString(), ""), BehaviorReport.class);
        assertThat(absent.integration().availability()).isEqualTo("absent");
    }
}
