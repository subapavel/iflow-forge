package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import io.poxray.model.AttributesReport;
import io.poxray.model.AttributesReport.Attribute;
import io.poxray.model.AttributesReport.ModuleRef;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@code po-xray attributes <ico.json>... [--json]} on the committed synthetic fixture of the migration skill. */
@QuarkusMainTest
class AttributesCommandTest {

    private static final Path ICOS = Path.of("..", "skills", "sap-po-migration", "fixtures", "icos-SND_SYS.json");

    private static String value(AttributesReport report, int scenario, String name) {
        return report.scenarios().get(scenario).attributes().stream().filter(a -> a.name().equals(name))
                .map(Attribute::value).findFirst().orElseThrow();
    }

    private static String json(String stdout) {
        return stdout.substring(stdout.indexOf('{'), stdout.lastIndexOf('}') + 1);
    }

    @Test
    void printsMarkdownOfTheThreeFixtureScenarios(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("attributes", ICOS.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("# Scenario attributes", "integrated-sync",
                "## IF_OrderQuery_to_RCV_SYS", "## IF_OrderIDoc_to_RCV_SYS_RCV2_SYS", "## IF_OrderBatch_to_RCV_SYS",
                "| attribute | value | source |", "### Questions", "### Modules",
                "receiver CC_RCV2_FILE #2 MyCustomModule", "sender CC_SND_SOAP #1 localejbs/MyPreModule");
    }

    @Test
    void printsJsonWithOneScenarioPerIco(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("attributes", "--json", ICOS.toString());

        assertThat(result.exitCode()).isZero();
        AttributesReport report = Json.mapper().readValue(json(result.getOutput()), AttributesReport.class);
        assertThat(report.inputs()).containsExactly(ICOS.toString());
        assertThat(report.scenarios()).hasSize(3);
        assertThat(report.scenarios()).allSatisfy(s -> assertThat(s.attributes()).hasSize(11));

        // (a) sync BE, XI to XI, one 1:1 mapping, no modules
        assertThat(value(report, 0, "qos")).isEqualTo("BE");
        assertThat(value(report, 0, "runtime")).isEqualTo("integrated-sync");
        assertThat(value(report, 0, "sender_type")).isEqualTo("XI");
        assertThat(value(report, 0, "receiver_type")).isEqualTo("XI");
        assertThat(value(report, 0, "receiver_count")).isEqualTo("1");
        assertThat(value(report, 0, "receiver_determination")).isEqualTo("static");
        assertThat(value(report, 0, "mapping")).isEqualTo("one");
        assertThat(value(report, 0, "custom_exit")).isEqualTo("none");
        assertThat(value(report, 0, "inbound_conversion")).isEqualTo("none");
        // (b) async EO, IDoc sender, conditional second receiver, XI and File receivers, module after CallSapAdapter
        assertThat(value(report, 1, "qos")).isEqualTo("EO");
        assertThat(value(report, 1, "runtime")).isEqualTo("?");
        assertThat(value(report, 1, "sender_type")).isEqualTo("IDOC");
        assertThat(value(report, 1, "receiver_type")).isEqualTo("?");
        assertThat(value(report, 1, "receiver_count")).isEqualTo("n");
        assertThat(value(report, 1, "receiver_determination")).isEqualTo("xpath");
        assertThat(value(report, 1, "custom_exit")).isEqualTo("post");
        assertThat(report.scenarios().get(1).modules())
                .containsExactly(new ModuleRef("receiver", "CC_RCV2_FILE", 2, "MyCustomModule"));
        // (c) async EO, SOAP sender with a module before CallSapAdapter, dynamic routing, 1:n of one interface
        assertThat(value(report, 2, "sender_type")).isEqualTo("OTHER");
        assertThat(value(report, 2, "receiver_count")).isEqualTo("n");
        assertThat(value(report, 2, "receiver_determination")).isEqualTo("reuse-xrd");
        assertThat(value(report, 2, "mapping")).isEqualTo("one-to-many-one-type");
        assertThat(value(report, 2, "custom_exit")).isEqualTo("pre");
        assertThat(value(report, 2, "inbound_conversion")).isEqualTo("?");
        assertThat(report.scenarios().get(2).modules())
                .containsExactly(new ModuleRef("sender", "CC_SND_SOAP", 1, "localejbs/MyPreModule"));
    }

    @Test
    void aMissingFileFailsAndNamesIt(QuarkusMainLauncher launcher, @TempDir Path dir) {
        Path missing = dir.resolve("absent-icos.json");

        LaunchResult result = launcher.launch("attributes", missing.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.getErrorOutput()).contains("absent-icos.json");
    }

    @Test
    void anUnparsableFileFailsAndNamesIt(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path broken = Files.writeString(dir.resolve("broken-icos.json"), "{ not json", StandardCharsets.UTF_8);

        LaunchResult result = launcher.launch("attributes", ICOS.toString(), broken.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.getErrorOutput()).contains("broken-icos.json");
    }

    @Test
    void withoutFilesItIsAUsageError(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("attributes");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.getOutput()).doesNotContain("# Scenario attributes");
    }
}
