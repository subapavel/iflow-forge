package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.SyntheticSca;
import io.poxray.TestData;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests of the CLI (JVM mode; the same tests run against the native executable as ITs). */
@QuarkusMainTest
class CliTest {

    @Test
    @Launch(value = {"--help"})
    void helpListsCommands(LaunchResult result) {
        assertThat(result.getOutput()).contains("json", "doc", "export");
    }

    @Test
    void jsonToStdout(QuarkusMainLauncher launcher, @TempDir Path dir) {
        Path sca = SyntheticSca.write(dir.resolve("ACME.sca"));

        LaunchResult result = launcher.launch("json", sca.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("\"schemaVersion\" : \"0.1\"", "acme.com/calc~ear");
        assertThat(result.getErrorOutput()).contains("ACME.sca: 2 DCs, 0 processes, 1 EJB modules");
    }

    @Test
    void exportWritesJsonAndBothDocuments(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path sca = SyntheticSca.write(dir.resolve("ACME.sca"));
        Path out = dir.resolve("out");

        LaunchResult result = launcher.launch("export", sca.toString(), "-o", out.toString(), "-l", "en");

        assertThat(result.exitCode()).isZero();
        assertThat(out.resolve("ACME.json")).exists();
        assertThat(Files.readString(out.resolve("ACME.md"))).contains("# SAP PO content: ACME\\_CALC");
        assertThat(Files.readString(out.resolve("ACME.jira.txt"))).startsWith("h1. SAP PO content: ACME\\_CALC");
    }

    @Test
    void docWritesSelectedFormat(QuarkusMainLauncher launcher, @TempDir Path dir) {
        Path sca = SyntheticSca.write(dir.resolve("ACME.sca"));

        LaunchResult result = launcher.launch("doc", sca.toString(), "--format", "jira", "--output", dir.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(dir.resolve("ACME.jira.txt")).exists();
        assertThat(dir.resolve("ACME.md")).doesNotExist();
    }

    @Test
    void missingFileFailsWithMessage(QuarkusMainLauncher launcher, @TempDir Path dir) {
        LaunchResult result = launcher.launch("json", dir.resolve("nope.sca").toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.getErrorOutput()).contains("SCA file not found");
    }

    @Test
    void exportsDigiSign(QuarkusMainLauncher launcher, @TempDir Path dir) {
        Path sca = TestData.require("EONBPMDIGI00_0.sca");

        LaunchResult result = launcher.launch("export", sca.toString(), "-o", dir.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput()).contains("2 processes, 2 EJB modules");
        assertThat(dir.resolve("EONBPMDIGI00_0.md")).exists();
    }
}
