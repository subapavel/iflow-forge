package io.poxray.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end tests of {@code iflow} and {@code unpack} on a synthetic artifact and package export. */
@QuarkusMainTest
class CpiCommandsTest {

    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String IFL = "http:///com.sap.ifl.model/Ifl.xsd";
    private static final String RESOURCE_ID = "00000000000000000000000000000001";
    private static final String CHANGELOG_ID = "00000000000000000000000000000002";

    /** A synthetic iFlow {@code my.id.T}: JMS sender, one Script step, one parameter with a configured value. */
    private static Map<String, String> artifact() {
        Map<String, String> files = new TreeMap<>();
        files.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nBundle-SymbolicName: my.i\r\n d.T;singleton:=true"
                + "\r\nBundle-Name: My Flow\r\nBundle-Version: 1.0.0\r\n\r\n");
        files.put(".project", "<projectDescription><name>my.id.T</name></projectDescription>\n");
        files.put("src/main/resources/scenarioflows/integrationflow/my.iflw", "<bpmn2:definitions xmlns:bpmn2=\""
                + BPMN + "\" xmlns:ifl=\"" + IFL + "\"><bpmn2:collaboration id=\"C\">"
                + "<bpmn2:participant id=\"P\" ifl:type=\"EndpointSender\" name=\"InQueue\"/>"
                + "<bpmn2:messageFlow id=\"M\" sourceRef=\"P\" targetRef=\"S\">" + props("ComponentType", "JMS",
                        "Name", "In", "direction", "Sender") + "</bpmn2:messageFlow></bpmn2:collaboration>"
                + "<bpmn2:process id=\"Main\"><bpmn2:startEvent id=\"S\"/>"
                + "<bpmn2:callActivity id=\"A\" name=\"A\">" + props("activityType", "Script") + "</bpmn2:callActivity>"
                + "<bpmn2:endEvent id=\"E\"/><bpmn2:sequenceFlow id=\"F1\" sourceRef=\"S\" targetRef=\"A\"/>"
                + "<bpmn2:sequenceFlow id=\"F2\" sourceRef=\"A\" targetRef=\"E\"/></bpmn2:process></bpmn2:definitions>");
        files.put("src/main/resources/parameters.propdef",
                "<parameters><parameter><name>p1</name><type>xsd:string</type></parameter></parameters>");
        files.put("src/main/resources/parameters.prop", "p1=configured-value\n");
        files.put("src/main/resources/script/a.groovy", "return message");
        return files;
    }

    private static String props(String... kv) {
        StringBuilder sb = new StringBuilder("<bpmn2:extensionElements>");
        for (int i = 0; i < kv.length; i += 2) {
            sb.append("<ifl:property><key>").append(kv[i]).append("</key><value>").append(kv[i + 1])
                    .append("</value></ifl:property>");
        }
        return sb.append("</bpmn2:extensionElements>").toString();
    }

    private static Path writeFolder(Map<String, String> files, Path dir) throws IOException {
        for (Map.Entry<String, String> f : files.entrySet()) {
            Path p = dir.resolve(f.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, f.getValue(), StandardCharsets.UTF_8);
        }
        return dir;
    }

    private static void writeZip(Map<String, String> files, Path zipFile) throws IOException {
        try (OutputStream os = Files.newOutputStream(zipFile); ZipOutputStream zip = new ZipOutputStream(os)) {
            for (Map.Entry<String, String> f : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(f.getKey()));
                zip.write(f.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static Path packageExport(Path dir) throws IOException {
        Files.createDirectories(dir);
        writeZip(artifact(), dir.resolve(RESOURCE_ID + "_content"));
        Files.writeString(dir.resolve(CHANGELOG_ID + "_content"), "Change log\r\n");
        String json = "{\"resources\":[{\"id\":\"" + RESOURCE_ID + "\",\"resourceType\":\"IFlow\",\"displayName\":"
                + "\"My Flow\",\"semanticVersion\":\"1.0.0\"},{\"id\":\"" + CHANGELOG_ID + "\",\"resourceType\":"
                + "\"ChangeLog\",\"displayName\":\"Change log\",\"semanticVersion\":\"1.0.0\"}]}";
        Files.writeString(dir.resolve("resources.cnt"),
                Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        return dir;
    }

    @Test
    void iflowPrintsMarkdown(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path artifact = writeFolder(artifact(), dir.resolve("my.id.T"));

        LaunchResult result = launcher.launch("iflow", artifact.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("## my.id.T (My Flow, 1.0.0)",
                "- participant: In JMS Sender -> InQueue", "1. Script \"A\"", "- parameter: p1 = [configured]",
                "- resource: script/a.groovy")
                .doesNotContain("configured-value");
        assertThat(result.getErrorOutput()).contains("Read 1 iFlow(s) from " + artifact);
    }

    @Test
    void iflowPrintsJson(QuarkusMainLauncher launcher, @TempDir Path dir) throws IOException {
        Path artifact = writeFolder(artifact(), dir.resolve("my.id.T"));

        LaunchResult result = launcher.launch("iflow", "--json", artifact.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("\"id\" : \"my.id.T\"", "\"activityType\" : \"Script\"")
                .doesNotContain("configured-value");
    }

    @Test
    void unpackWritesArtifactAndChangelogAndStripsTokens(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws IOException {
        Path export = packageExport(dir.resolve("export"));
        Path out = dir.resolve("out");

        LaunchResult result = launcher.launch("unpack", export.toString(), out.toString(), "--strip", ".T");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput()).contains("Unpacked 1 artifact(s) into " + out);
        assertThat(out.resolve("my.id/META-INF/MANIFEST.MF")).content().doesNotContain(".T;");
        assertThat(out.resolve("my.id/.project")).content().contains("<name>my.id</name>");
        assertThat(out.resolve("my.id/src/main/resources/parameters.prop")).doesNotExist();
        assertThat(out.resolve("CHANGELOG.txt")).content().isEqualTo("Change log\n");
    }

    @Test
    void iflowOnAMissingPathFails(QuarkusMainLauncher launcher, @TempDir Path dir) {
        LaunchResult result = launcher.launch("iflow", dir.resolve("nope").toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.getErrorOutput()).contains("Error: Not found");
    }
}
