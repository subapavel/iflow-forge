package io.poxray.cli;

import static io.poxray.migration.ExportMetadataFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.poxray.json.Json;
import io.poxray.model.ExportManifest;
import io.poxray.model.ExportManifest.FileDigest;
import io.poxray.model.Issue;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/** MIG-36: {@code po-xray export --provenance-input} through the real command (JVM mode). */
@QuarkusMainTest
class ExportProvenanceCommandTest {

    @Test
    void legacyExportIsUnchanged(QuarkusMainLauncher launcher, @TempDir Path dir) throws Exception {
        Scene s = new Scene(dir);
        Map<String, String> inputs = snapshot(dir);

        LaunchResult result = launcher.launch("export", s.sca.toString(), "-o", s.out.toString(), "-l", "en");

        assertThat(result.exitCode()).isZero();
        assertThat(snapshot(s.out).keySet()).containsExactly("ACME.jira.txt", "ACME.json", "ACME.md");
        assertThat(written(result)).containsExactly(abs(s.out.resolve("ACME.json")), abs(s.out.resolve("ACME.md")),
                abs(s.out.resolve("ACME.jira.txt")));
        assertThat(result.getErrorOutput()).contains("ACME.sca: 2 DCs, 0 processes, 1 EJB modules")
                .doesNotContain("provenance", "EXPORT_");
        JsonNode inventory = Json.mapper().readTree(Files.readString(s.out.resolve("ACME.json")));
        assertThat(inventory.path("source").path("sha256").stringValue()).isEqualTo(sha256(s.sca));
        assertThat(inventory.has("outputCreatedAt")).isFalse();
        assertThat(Files.readString(s.out.resolve("ACME.md"))).contains("# SAP PO content: ACME\\_CALC");
        assertThat(Files.readString(s.out.resolve("ACME.jira.txt"))).startsWith("h1. SAP PO content: ACME\\_CALC");
        assertThat(inputsOf(dir, "out")).isEqualTo(inputs);

        Path realOut = Files.createDirectories(dir.resolve("real-out"));
        Path linkOut = junction(dir.resolve("link-out"), realOut);
        try {
            LaunchResult viaJunction = launcher.launch("export", s.sca.toString(), "-o", linkOut.toString(), "-l", "en");
            assertThat(viaJunction.exitCode()).as("legacy mode adds no new destination checks").isZero();
            assertThat(snapshot(realOut).keySet()).containsExactly("ACME.jira.txt", "ACME.json", "ACME.md");
            assertThat(written(viaJunction)).hasSize(3);
        } finally {
            deleteLink(linkOut);
        }
    }

    @Test
    void optInAddsCompleteSidecarWithActualPaths(QuarkusMainLauncher launcher, @TempDir Path dir) throws Exception {
        Scene s = new Scene(dir);
        byte[] descriptorBytes = Files.readAllBytes(s.descriptor);
        Map<String, String> inputs = snapshot(dir);
        Path json = s.out.resolve("ACME.json");
        Path md = s.out.resolve("ACME.md");
        Path jira = s.out.resolve("ACME.jira.txt");
        Path side = s.out.resolve("ACME.provenance.json");

        Instant start = Instant.now();
        LaunchResult result = launcher.launch("export", s.sca.toString(), "-o", s.out.toString(), "-l", "en",
                "--provenance-input", s.descriptor.toString());
        Instant end = Instant.now();

        assertThat(result.exitCode()).as(result.getErrorOutput()).isZero();
        assertThat(snapshot(s.out).keySet()).containsExactly("ACME.jira.txt", "ACME.json", "ACME.md",
                "ACME.provenance.json");
        assertThat(written(result)).containsExactly(abs(json), abs(md), abs(jira), abs(side));
        assertThat(result.getErrorOutput()).doesNotContain("EXPORT_");
        ExportManifest m = Json.mapper().readValue(Files.readString(side), ExportManifest.class);
        assertThat(m.formatVersion()).isEqualTo(1);
        assertThat(m.outputs()).containsExactlyElementsOf(outputDigests(List.of(json, md, jira)));
        assertThat(m.descriptor()).isEqualTo(new FileDigest("descriptor", "descriptor.json", abs(s.descriptor),
                sha256(descriptorBytes), "read-time-snapshot"));
        assertThat(m.suppliedArtifacts()).containsExactly(measured("build", BUILD_LABEL, s.build),
                measured("skill", SKILL_LABEL, s.skill));
        assertThat(m.versions()).containsExactly(deployedOld(), releaseNew());
        assertThat(m.issues()).isEmpty();
        assertThat(m.source().sha256()).isEqualTo(sha256(s.sca));
        JsonNode sidecar = Json.mapper().readTree(Files.readString(side));
        JsonNode inventory = Json.mapper().readTree(Files.readString(json));
        assertThat(sidecar.path("generator")).isEqualTo(inventory.path("generator"));
        assertThat(sidecar.path("source")).isEqualTo(inventory.path("source"));
        assertThat(Instant.parse(m.outputCreatedAt())).isBetween(start.truncatedTo(ChronoUnit.SECONDS), end);
        assertThat(inputsOf(dir, "out")).isEqualTo(inputs);
    }

    @Test
    void invalidDescriptorFailsBeforeAnyOutput(QuarkusMainLauncher launcher, @TempDir Path dir) throws Exception {
        Scene s = new Scene(dir);
        String valid = descriptor(List.of(artifact("build", BUILD_LABEL, abs(s.build)), artifact("skill", SKILL_LABEL,
                abs(s.skill))), List.of(json(deployedOld())));
        Path unknownField = Files.writeString(s.in.resolve("unknown-field.json"),
                valid.replaceFirst("\\{", "{ \"extra\" : true,"));
        Path format2 = Files.writeString(s.in.resolve("format-2.json"),
                valid.replace("\"formatVersion\" : 1", "\"formatVersion\" : 2"));
        Path uncArtifact = Files.writeString(s.in.resolve("unc-artifact.json"), descriptor(
                List.of(artifact("build", BUILD_LABEL, "\\\\server.invalid\\share\\po-xray.jar")), List.of()));
        Path artifactAtDestination = Files.writeString(s.in.resolve("artifact-at-destination.json"), descriptor(
                List.of(artifact("build", BUILD_LABEL, abs(dir.resolve("out-artifact/ACME.md")))), List.of()));
        Path outDescriptor = Files.createDirectories(dir.resolve("out-descriptor"));
        Path descriptorAtDestination = Files.writeString(outDescriptor.resolve("ACME.provenance.json"), valid);
        Path realOut = Files.createDirectories(dir.resolve("real-out"));
        Path linkOut = junction(dir.resolve("link-out"), realOut);
        try {
            Map<String, List<String>> cases = new LinkedHashMap<>();
            cases.put("unknown field", args(s.sca, dir.resolve("out-unknown"), unknownField));
            cases.put("unsupported formatVersion", args(s.sca, dir.resolve("out-format"), format2));
            cases.put("missing descriptor", args(s.sca, dir.resolve("out-missing"), s.in.resolve("missing.json")));
            cases.put("UNC artifact path", args(s.sca, dir.resolve("out-unc"), uncArtifact));
            cases.put("artifact is a destination", args(s.sca, dir.resolve("out-artifact"), artifactAtDestination));
            cases.put("descriptor is the sidecar destination", args(s.sca, outDescriptor, descriptorAtDestination));
            cases.put("missing source", args(s.in.resolve("missing.sca"), dir.resolve("out-source"), s.descriptor));
            cases.put("output directory is a junction", args(s.sca, linkOut, s.descriptor));
            for (var c : cases.entrySet()) {
                Map<String, String> before = snapshot(dir);

                LaunchResult result = launcher.launch(c.getValue().toArray(String[]::new));

                assertThat(result.exitCode()).as(c.getKey()).isEqualTo(1);
                assertThat(result.getErrorOutput().lines()).as(c.getKey()).anyMatch(l -> l.startsWith("Error:"));
                assertThat(written(result)).as(c.getKey()).isEmpty();
                assertThat(result.getErrorOutput()).as(c.getKey() + ": SCA not read").doesNotContain("2 DCs");
                assertThat(snapshot(dir)).as(c.getKey() + ": no output, inputs unchanged").isEqualTo(before);
            }
            assertThat(Files.readString(descriptorAtDestination)).isEqualTo(valid);
            assertThat(snapshot(realOut)).isEmpty();
        } finally {
            deleteLink(linkOut);
        }
    }

    @Test
    void unavailableArtifactSucceedsWithIssuesOnlyInSidecar(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws Exception {
        Scene s = new Scene(dir);
        Path missing = dir.resolve("tools/missing.jar");
        Files.writeString(s.descriptor, descriptor(List.of(artifact("build", "missing build", "../tools/missing.jar")),
                List.of(json(deployedOld()))));
        Map<String, String> inputs = snapshot(dir);

        LaunchResult legacy = launcher.launch("export", s.sca.toString(), "-o", dir.resolve("out-legacy").toString(),
                "-l", "en");
        LaunchResult result = launcher.launch("export", s.sca.toString(), "-o", s.out.toString(), "-l", "en",
                "--provenance-input", s.descriptor.toString());

        assertThat(result.exitCode()).as(result.getErrorOutput()).isZero();
        assertThat(snapshot(s.out).keySet()).containsExactly("ACME.jira.txt", "ACME.json", "ACME.md",
                "ACME.provenance.json");
        assertThat(written(result)).hasSize(4);
        ExportManifest m = Json.mapper().readValue(Files.readString(s.out.resolve("ACME.provenance.json")),
                ExportManifest.class);
        assertThat(m.suppliedArtifacts()).containsExactly(unavailable("build", "missing build", missing));
        assertThat(m.issues()).extracting(Issue::severity, Issue::code, Issue::location, Issue::source).containsExactly(
                tuple("warning", "EXPORT_ARTIFACT_UNAVAILABLE", null, abs(missing)),
                tuple("warning", "EXPORT_ROLE_MISSING", null, null));
        assertThat(m.issues().get(0).message()).contains("build", "missing build");
        assertThat(m.issues().get(1).message()).contains("skill");
        assertThat(result.getErrorOutput()).doesNotContain("EXPORT_ARTIFACT_UNAVAILABLE", "EXPORT_ROLE_MISSING",
                "missing build");
        for (String legacyFile : List.of("ACME.json", "ACME.md", "ACME.jira.txt")) {
            assertThat(Files.readString(s.out.resolve(legacyFile))).as(legacyFile).doesNotContain("EXPORT_",
                    "missing build");
        }
        assertThat(summary(result)).isEqualTo(summary(legacy)).isNotEmpty();
        assertThat(inputsOf(dir, "out")).isEqualTo(inputs);
    }

    @Test
    void sidecarWriteFailureKeepsPartialOutputsAndExitsOne(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws Exception {
        Scene s = new Scene(dir);
        Path side = Files.createDirectories(s.out).resolve("ACME.provenance.json");
        Files.writeString(side, "old sidecar");
        Map<String, String> inputs = inputsOf(dir, "out");
        Files.setAttribute(side, "dos:readonly", true);
        try {
            LaunchResult result = launcher.launch("export", s.sca.toString(), "-o", s.out.toString(), "-l", "en",
                    "--provenance-input", s.descriptor.toString());

            assertThat(result.exitCode()).isEqualTo(1);
            List<String> lines = result.getErrorOutput().lines().toList();
            assertThat(written(result)).containsExactly(abs(s.out.resolve("ACME.json")), abs(s.out.resolve("ACME.md")),
                    abs(s.out.resolve("ACME.jira.txt")));
            int lastWritten = -1;
            int error = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("Written ")) {
                    lastWritten = i;
                }
                if (error < 0 && lines.get(i).startsWith("Error:")) {
                    error = i;
                }
            }
            assertThat(error).as(result.getErrorOutput()).isGreaterThan(lastWritten);
            assertThat(lastWritten).isGreaterThanOrEqualTo(0);
            assertThat(snapshot(s.out).keySet()).containsExactly("ACME.jira.txt", "ACME.json", "ACME.md",
                    "ACME.provenance.json");
            assertThat(Files.readString(side)).isEqualTo("old sidecar");
            assertThat(Json.mapper().readTree(Files.readString(s.out.resolve("ACME.json"))).path("source")
                    .path("sha256").stringValue()).isEqualTo(sha256(s.sca));
            assertThat(Files.readString(s.out.resolve("ACME.md"))).contains("# SAP PO content: ACME\\_CALC");
            assertThat(Files.readString(s.out.resolve("ACME.jira.txt"))).startsWith("h1. SAP PO content: ACME\\_CALC");
            assertThat(inputsOf(dir, "out")).isEqualTo(inputs);
        } finally {
            Files.setAttribute(side, "dos:readonly", false);
        }
    }

    @Test
    void optInUsesOneNormalizedPathSetForChecksWritesAndManifest(QuarkusMainLauncher launcher, @TempDir Path dir)
            throws Exception {
        Scene s = new Scene(dir);
        Map<String, String> inputs = snapshot(dir);
        String rawOut = s.out.toString() + java.io.File.separator + "x" + java.io.File.separator + "..";
        String rawSca = s.in.toString() + java.io.File.separator + "x" + java.io.File.separator + ".."
                + java.io.File.separator + "ACME.sca";
        List<String> expected = List.of(abs(s.out.resolve("ACME.json")), abs(s.out.resolve("ACME.md")),
                abs(s.out.resolve("ACME.jira.txt")), abs(s.out.resolve("ACME.provenance.json")));

        LaunchResult result = launcher.launch("export", rawSca, "-o", rawOut, "-l", "en", "--provenance-input",
                s.descriptor.toString());

        assertThat(result.exitCode()).as(result.getErrorOutput()).isZero();
        List<String> raw = rawWritten(result);
        assertThat(raw).as("raw Written lines are the normalized absolute paths").containsExactlyElementsOf(expected);
        ExportManifest m = Json.mapper().readValue(Files.readString(s.out.resolve("ACME.provenance.json")),
                ExportManifest.class);
        assertThat(m.outputs()).extracting(FileDigest::path).containsExactlyElementsOf(raw.subList(0, 3));
        assertThat(m.outputs()).containsExactlyElementsOf(outputDigests(List.of(s.out.resolve("ACME.json"),
                s.out.resolve("ACME.md"), s.out.resolve("ACME.jira.txt"))));
        assertThat(m.source().sha256()).isEqualTo(sha256(s.sca));
        assertThat(snapshot(s.out).keySet()).containsExactly("ACME.jira.txt", "ACME.json", "ACME.md",
                "ACME.provenance.json");
        assertThat(s.out.resolve("x")).doesNotExist();
        assertThat(s.in.resolve("x")).doesNotExist();
        assertThat(inputsOf(dir, "out")).isEqualTo(inputs);
    }

    /** The paths of the "Written ..." lines exactly as printed. */
    private static List<String> rawWritten(LaunchResult result) {
        return result.getErrorOutput().lines().filter(l -> l.startsWith("Written "))
                .map(l -> l.substring("Written ".length())).toList();
    }

    private static List<String> args(Path sca, Path out, Path descriptor) {
        return List.of("export", sca.toString(), "-o", out.toString(), "-l", "en", "--provenance-input",
                descriptor.toString());
    }

    /** The paths of the "Written ..." lines, absolute and normalized, in printing order. */
    private static List<String> written(LaunchResult result) {
        return result.getErrorOutput().lines().filter(l -> l.startsWith("Written "))
                .map(l -> abs(Path.of(l.substring("Written ".length())))).toList();
    }

    private static List<String> summary(LaunchResult result) {
        return result.getErrorOutput().lines().filter(l -> l.startsWith("ACME.sca: ")).toList();
    }

    /** Snapshot of {@code dir} without the entries below the top-level directory {@code out}. */
    private static Map<String, String> inputsOf(Path dir, String out) throws Exception {
        return snapshot(dir).entrySet().stream()
                .filter(e -> !e.getKey().equals(out) && !e.getKey().startsWith(out + "/")
                        && !e.getKey().startsWith(out + "-"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.TreeMap::new));
    }
}
