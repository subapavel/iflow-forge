package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.bpm.SyntheticNwdsPlugins;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code po-xray bpm-functions <nwds-plugins-dir> <output.json>} writes the built-in function catalog. */
@QuarkusMainTest
class BpmFunctionsCommandTest {

    @Test
    void generatesCatalog(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path plugins = SyntheticNwdsPlugins.write(dir.resolve("plugins"));
        Path out = dir.resolve("bpm-builtin-functions.json");

        LaunchResult result = launcher.launch("bpm-functions", plugins.toString(), out.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("3 functions, 3 triggers, 2 XSD types, 1 SAP types -> " + out);
        assertThat(out).exists();
        assertThat(Files.readString(out, StandardCharsets.UTF_8)).startsWith("{\n \"source\": {");
    }

    @Test
    void missingJarFailsWithMessage(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path empty = Files.createDirectories(dir.resolve("plugins"));
        Path out = dir.resolve("bpm-builtin-functions.json");

        LaunchResult result = launcher.launch("bpm-functions", empty.toString(), out.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.getErrorOutput()).contains("servicecomp");
        assertThat(out).doesNotExist();
    }
}
