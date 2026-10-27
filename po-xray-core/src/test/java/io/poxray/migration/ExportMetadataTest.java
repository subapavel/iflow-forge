package io.poxray.migration;

import static io.poxray.migration.ExportMetadataFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

import io.poxray.json.Json;
import io.poxray.model.ExportManifest;
import io.poxray.model.ExportManifest.FileDigest;
import io.poxray.model.ExportProvenanceInput;
import io.poxray.model.ExportProvenanceInput.ArtifactInput;
import io.poxray.model.ExportProvenanceInput.VersionObservation;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/** MIG-36: the optional export provenance sidecar (descriptor read, preflight, manifest creation). */
class ExportMetadataTest {

    private static final String UNAVAILABLE = "EXPORT_ARTIFACT_UNAVAILABLE";
    private static final String ROLE_MISSING = "EXPORT_ROLE_MISSING";

    @TempDir
    Path dir;

    @Test
    void manifestRetainsInventorySourceAndReadAtAndSamplesCreationTimeSeparately() throws Exception {
        Scene s = new Scene(dir);
        ScaInventory read = inventory(s.sca);
        ScaInventory inv = withReadAt(read, "2026-01-02T03:04:05Z");
        ExportMetadata.Loaded loaded = ExportMetadata.read(s.descriptor);
        List<Path> outputs = s.writeOutputs(inv);
        FileTime old = FileTime.from(Instant.parse("2001-01-01T00:00:00Z"));
        for (Path p : List.of(outputs.get(0), outputs.get(1), outputs.get(2), s.descriptor, s.build, s.skill, s.sca)) {
            Files.setLastModifiedTime(p, old);
        }
        Map<String, String> before = snapshot(dir);

        Instant start = Instant.now();
        ExportManifest m = ExportMetadata.create(inv, loaded, outputs);
        Instant end = Instant.now();

        assertThat(m.formatVersion()).isEqualTo(1);
        assertThat(m.generator()).isEqualTo(new ScaInventory.Generator(read.generator().name(),
                read.generator().version(), "2026-01-02T03:04:05Z"));
        assertThat(m.source()).isEqualTo(read.source());
        assertThat(m.source().file()).isEqualTo("ACME.sca");
        assertThat(m.source().sha256()).isEqualTo(sha256(s.sca));
        assertThat(m.outputCreatedAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        assertThat(Instant.parse(m.outputCreatedAt())).isBetween(start.truncatedTo(ChronoUnit.SECONDS), end);
        assertThat(m.outputCreatedAt()).isNotEqualTo(m.generator().readAt());
        assertThat(snapshot(dir)).as("create writes no sidecar and touches no file").isEqualTo(before);
        assertThat(s.out.resolve("ACME.provenance.json")).doesNotExist();
    }

    @Test
    void outputsAndArtifactsHaveMeasuredDigestsWhileDescriptorIsReadTimeSnapshot() throws Exception {
        Scene s = new Scene(dir);
        ScaInventory inv = inventory(s.sca);
        byte[] descriptorAtRead = Files.readAllBytes(s.descriptor);
        ExportMetadata.Loaded loaded = ExportMetadata.read(s.descriptor);
        assertThat(loaded.descriptorPath()).isEqualTo(abs(s.descriptor));
        assertThat(loaded.descriptorSha256()).isEqualTo(sha256(descriptorAtRead));

        Files.writeString(s.descriptor, descriptor(List.of(artifact("build", "edited later", "elsewhere.jar")), List.of()));
        Files.write(s.build, bytes("synthetic build v2"));
        List<Path> outputs = s.writeOutputs(inv);
        Map<String, String> before = snapshot(dir);

        ExportManifest m = ExportMetadata.create(inv, loaded, outputs);

        FileDigest descriptorDigest = new FileDigest("descriptor", "descriptor.json", abs(s.descriptor),
                sha256(descriptorAtRead), "read-time-snapshot");
        assertThat(m.descriptor()).isEqualTo(descriptorDigest);
        assertThat(m.descriptor().sha256()).isNotEqualTo(sha256(s.descriptor));
        assertThat(m.outputs()).containsExactlyElementsOf(outputDigests(outputs));
        assertThat(m.outputs()).extracting(FileDigest::label).containsExactly("ACME.json", "ACME.md", "ACME.jira.txt");
        FileDigest build2 = new FileDigest("build", BUILD_LABEL, abs(dir.resolve("tools/po-xray.jar")),
                sha256(bytes("synthetic build v2")), "measured");
        FileDigest skill1 = new FileDigest("skill", SKILL_LABEL, abs(s.skill), sha256(bytes("synthetic skill v1")),
                "measured");
        assertThat(m.suppliedArtifacts()).containsExactly(build2, skill1);
        assertThat(m.versions()).containsExactly(deployedOld(), releaseNew());
        assertThat(m.issues()).isEmpty();
        assertThat(snapshot(dir)).isEqualTo(before);

        Files.write(s.skill, bytes("synthetic skill v2"));
        ExportManifest m2 = ExportMetadata.create(inv, loaded, outputs);

        assertThat(m2.suppliedArtifacts()).containsExactly(build2,
                new FileDigest("skill", SKILL_LABEL, abs(s.skill), sha256(bytes("synthetic skill v2")), "measured"));
        assertThat(m2.descriptor()).isEqualTo(descriptorDigest);
        assertThat(m2.outputs()).isEqualTo(m.outputs());
        assertThat(m2.source()).isEqualTo(m.source()).isEqualTo(inv.source());
        assertThat(m2.generator()).isEqualTo(m.generator()).isEqualTo(inv.generator());
        assertThat(m2.versions()).isEqualTo(m.versions());
        assertThat(m2.issues()).isEqualTo(m.issues());

        Path directoryOutput = Files.createDirectories(s.out.resolve("not-a-file"));
        assertThatThrownBy(() -> ExportMetadata.create(inv, loaded, List.of(outputs.get(0), s.out.resolve("missing.md"))))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ExportMetadata.create(inv, loaded, List.of(directoryOutput)))
                .isInstanceOf(IOException.class);
        assertThat(Files.readAllBytes(s.build)).isEqualTo(bytes("synthetic build v2"));
    }

    @Test
    void versionObservationsStayVerbatimOrderedAndSeparated() throws Exception {
        Scene s = new Scene(dir);
        scaWithRelease(s.sca, "7.50-SCA-RELEASE");
        ScaInventory inv = inventory(s.sca);
        assertThat(inv.source().softwareComponent().release()).isEqualTo("7.50-SCA-RELEASE");
        VersionObservation bundleNew = new VersionObservation("bundle-version", "ACME integration package",
                "bundle-new", "export", "META-INF/MANIFEST.MF Bundle-Version", HEX_B, "2026-10-20",
                "downloaded package", "Bundle-Version of the download");
        VersionObservation legacy = new VersionObservation("legacy-installed", "ACME_CALC", "7.50 SP12 legacy",
                "owner-confirmed", "owner statement, synthetic", null, null, "legacy PO system",
                "Owner statement, not read from the system");
        VersionObservation deployedOwner = new VersionObservation("deployed", "ACME integration package",
                "deployed-old", "owner-confirmed", "owner statement, synthetic", null, "2026-10-21",
                "synthetic tenant", "Owner statement");
        VersionObservation label = new VersionObservation("export-label", "ACME export", "label-1", "documentation",
                "synthetic export notes", null, "2026-10-19", "export", "Label only");
        VersionObservation target = new VersionObservation("target", "ACME integration package", "target-version",
                "derived", "synthetic plan", null, null, "migration plan", "Derived by the caller");
        VersionObservation designTime = new VersionObservation("design-time", "ACME integration package",
                "design-version", "documentation", "synthetic design notes", null, "2026-10-18T08:00:00Z",
                "design time", "Documentation only");
        List<VersionObservation> expected = List.of(deployedOld(), releaseNew(), bundleNew, legacy, deployedOwner,
                deployedOld(), label, target, designTime);
        List<String> versionsJson = new ArrayList<>();
        for (VersionObservation v : expected) {
            versionsJson.add(json(v));
        }
        Files.writeString(s.descriptor, descriptor(List.of(artifact("build", BUILD_LABEL, "../tools/po-xray.jar"),
                artifact("skill", SKILL_LABEL, abs(s.skill))), versionsJson));
        Map<String, String> before = snapshot(dir);

        ExportMetadata.Loaded loaded = ExportMetadata.read(s.descriptor);
        ExportManifest m = ExportMetadata.create(inv, loaded, s.writeOutputs(inv));

        assertThat(loaded.input().formatVersion()).isEqualTo(1);
        assertThat(loaded.input().versions()).containsExactlyElementsOf(expected);
        assertThat(m.versions()).containsExactlyElementsOf(expected);
        assertThat(m.versions()).filteredOn(v -> v.role().equals("deployed")).extracting(VersionObservation::value)
                .containsExactly("deployed-old", "deployed-old", "deployed-old");
        assertThat(m.versions()).extracting(VersionObservation::sourceKind).containsExactly("tenant-observed",
                "export", "export", "owner-confirmed", "owner-confirmed", "tenant-observed", "documentation",
                "derived", "documentation");
        assertThat(m.versions()).extracting(VersionObservation::role).containsExactly("deployed", "package-release",
                "bundle-version", "legacy-installed", "deployed", "deployed", "export-label", "target", "design-time");
        assertThat(m.source()).isEqualTo(inv.source());
        assertThat(m.source().softwareComponent().release()).isEqualTo("7.50-SCA-RELEASE");
        assertThat(Json.write(m.versions())).doesNotContain("7.50-SCA-RELEASE", m.outputCreatedAt(),
                inv.generator().readAt());
        assertThat(snapshot(dir).entrySet()).containsAll(before.entrySet());
    }

    @Test
    void strictDescriptorFailuresAreIOExceptions() throws Exception {
        Path in = Files.createDirectories(dir.resolve("in"));
        String obs = "{\"role\":\"deployed\",\"subject\":\"S\",\"value\":\"V\",\"sourceKind\":\"export\",\"locator\":\"L\","
                + "\"sourceSha256\":null,\"observedAt\":null,\"scope\":\"X\",\"limitations\":\"Y\"}";
        String base = "{\"formatVersion\":1,\"artifacts\":[{\"role\":\"build\",\"label\":\"B\",\"path\":\"build.jar\"}],"
                + "\"versions\":[" + obs + "]}";
        Path valid = Files.writeString(in.resolve("valid.json"), base);
        ExportMetadata.Loaded loaded = ExportMetadata.read(valid);
        assertThat(loaded.input().artifacts()).containsExactly(new ArtifactInput("build", "B", "build.jar"));
        assertThat(loaded.input().versions()).containsExactly(
                new VersionObservation("deployed", "S", "V", "export", "L", null, null, "X", "Y"));
        for (String accepted : List.of(
                base.replace("\"sourceSha256\":null", "\"sourceSha256\":\"" + HEX_B + "\""),
                base.replace("\"observedAt\":null", "\"observedAt\":\"2026-10-20\""),
                base.replace("\"observedAt\":null", "\"observedAt\":\"2026-10-20T10:00:00+02:00\""),
                base.replace("\"locator\":\"L\"", "\"locator\":\"file:///definitely-missing/source.json\""),
                base.replace("\"path\":\"build.jar\"", "\"path\":" + q(abs(in.resolve("absolute.jar")))),
                "{\"formatVersion\":1,\"artifacts\":[],\"versions\":[]}")) {
            Path ok = Files.writeString(in.resolve("accepted.json"), accepted);
            assertThat(ExportMetadata.read(ok).input().formatVersion()).as(accepted).isEqualTo(1);
        }

        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("duplicate top-level key", base.replace("\"formatVersion\":1,", "\"formatVersion\":1,\"formatVersion\":1,"));
        cases.put("duplicate nested key", base.replace("\"label\":\"B\"", "\"label\":\"B\",\"label\":\"C\""));
        cases.put("trailing object", base + " {}");
        cases.put("trailing token", base + " x");
        cases.put("unknown top-level field", base.replace("{\"formatVersion\":1,", "{\"formatVersion\":1,\"extra\":true,"));
        cases.put("unknown artifact field", base.replace("\"path\":\"build.jar\"", "\"path\":\"build.jar\",\"sha256\":\"" + HEX_A + "\""));
        cases.put("unknown observation field", base.replace("\"limitations\":\"Y\"", "\"limitations\":\"Y\",\"confidence\":\"high\""));
        cases.put("missing formatVersion", base.replace("\"formatVersion\":1,", ""));
        cases.put("missing artifacts", base.replace("\"artifacts\":[{\"role\":\"build\",\"label\":\"B\",\"path\":\"build.jar\"}],", ""));
        cases.put("missing versions", base.replace(",\"versions\":[" + obs + "]", ""));
        cases.put("missing artifact role", base.replace("\"role\":\"build\",", ""));
        cases.put("missing artifact label", base.replace("\"label\":\"B\",", ""));
        cases.put("missing artifact path", base.replace(",\"path\":\"build.jar\"", ""));
        cases.put("missing observation subject", base.replace("\"subject\":\"S\",", ""));
        cases.put("missing observation limitations", base.replace(",\"limitations\":\"Y\"", ""));
        cases.put("unsupported formatVersion 2", base.replace("\"formatVersion\":1", "\"formatVersion\":2"));
        cases.put("unsupported formatVersion 0", base.replace("\"formatVersion\":1", "\"formatVersion\":0"));
        cases.put("formatVersion as string", base.replace("\"formatVersion\":1", "\"formatVersion\":\"1\""));
        cases.put("formatVersion null", base.replace("\"formatVersion\":1", "\"formatVersion\":null"));
        cases.put("null artifact entry", base.replace("\"artifacts\":[", "\"artifacts\":[null,"));
        cases.put("null version entry", base.replace("\"versions\":[", "\"versions\":[null,"));
        cases.put("null artifacts array", base.replace("[{\"role\":\"build\",\"label\":\"B\",\"path\":\"build.jar\"}]", "null"));
        cases.put("null versions array", base.replace("[" + obs + "]", "null"));
        cases.put("artifacts not an array", base.replace("[{\"role\":\"build\",\"label\":\"B\",\"path\":\"build.jar\"}]", "{}"));
        cases.put("null artifact label", base.replace("\"label\":\"B\"", "\"label\":null"));
        cases.put("null observation subject", base.replace("\"subject\":\"S\"", "\"subject\":null"));
        cases.put("blank artifact label", base.replace("\"label\":\"B\"", "\"label\":\"  \""));
        cases.put("blank artifact path", base.replace("\"path\":\"build.jar\"", "\"path\":\"\""));
        cases.put("blank subject", base.replace("\"subject\":\"S\"", "\"subject\":\" \""));
        cases.put("blank value", base.replace("\"value\":\"V\"", "\"value\":\"\""));
        cases.put("blank locator", base.replace("\"locator\":\"L\"", "\"locator\":\"\""));
        cases.put("blank scope", base.replace("\"scope\":\"X\"", "\"scope\":\" \""));
        cases.put("blank limitations", base.replace("\"limitations\":\"Y\"", "\"limitations\":\"\""));
        cases.put("artifact role runtime", base.replace("\"role\":\"build\"", "\"role\":\"runtime\""));
        cases.put("artifact role wrong case", base.replace("\"role\":\"build\"", "\"role\":\"Build\""));
        cases.put("observation role release", base.replace("\"role\":\"deployed\"", "\"role\":\"release\""));
        cases.put("observation role MIG-35 field name", base.replace("\"role\":\"deployed\"", "\"role\":\"deployedVersion\""));
        cases.put("sourceKind inferred", base.replace("\"sourceKind\":\"export\"", "\"sourceKind\":\"inferred\""));
        cases.put("short sourceSha256", base.replace("\"sourceSha256\":null", "\"sourceSha256\":\"abc\""));
        cases.put("non-hex sourceSha256", base.replace("\"sourceSha256\":null", "\"sourceSha256\":\"" + "g".repeat(64) + "\""));
        cases.put("observedAt unknown", base.replace("\"observedAt\":null", "\"observedAt\":\"unknown\""));
        cases.put("observedAt invalid date", base.replace("\"observedAt\":null", "\"observedAt\":\"2026-13-45\""));
        cases.put("observedAt without offset", base.replace("\"observedAt\":null", "\"observedAt\":\"2026-10-20T10:00:00\""));
        cases.put("file URI path", base.replace("\"path\":\"build.jar\"", "\"path\":\"file:///C:/tools/build.jar\""));
        cases.put("https URI path", base.replace("\"path\":\"build.jar\"", "\"path\":\"https://example.invalid/build.jar\""));
        cases.put("UNC backslash path", base.replace("\"path\":\"build.jar\"", "\"path\":" + q("\\\\server.invalid\\share\\build.jar")));
        cases.put("UNC forward-slash path", base.replace("\"path\":\"build.jar\"", "\"path\":\"//server.invalid/share/build.jar\""));
        cases.put("device path dot", base.replace("\"path\":\"build.jar\"", "\"path\":" + q("\\\\.\\C:\\tools\\build.jar")));
        cases.put("device path question mark", base.replace("\"path\":\"build.jar\"", "\"path\":" + q("\\\\?\\C:\\tools\\build.jar")));
        cases.put("malformed path syntax", base.replace("\"path\":\"build.jar\"", "\"path\":\"bad\\u0000name.jar\""));
        cases.put("root array", "[]");
        cases.put("root null", "null");
        cases.put("empty document", "");
        cases.put("truncated document", "{\"formatVersion\":1,");
        Map<String, String> messages = new LinkedHashMap<>();
        for (var c : cases.entrySet()) {
            Path file = Files.writeString(in.resolve("case.json"), c.getValue());
            Throwable thrown = catchThrowable(() -> ExportMetadata.read(file));
            assertThat(thrown).as(c.getKey()).isInstanceOf(IOException.class);
            messages.put(c.getKey(), thrown.getMessage());
        }
        assertThat(messages.get("unknown top-level field")).contains("extra");
        assertThat(messages.get("unknown observation field")).contains("confidence");
        assertThat(messages.get("unsupported formatVersion 2")).contains("formatVersion");

        assertThatThrownBy(() -> ExportMetadata.read(in.resolve("missing.json"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> ExportMetadata.read(in)).isInstanceOf(IOException.class);
        Path link = junction(dir.resolve("linked-in"), in);
        try {
            assertThatThrownBy(() -> ExportMetadata.read(link.resolve("valid.json")))
                    .as("descriptor reached through a junction/link component").isInstanceOf(IOException.class);
            assertThat(Files.readString(valid)).isEqualTo(base);
        } finally {
            deleteLink(link);
        }
    }

    @Test
    void preflightRejectsOutputCollisionsButAllowsInputAliases() throws Exception {
        Scene s = new Scene(dir);
        Path alias = Files.createLink(dir.resolve("tools/po-xray-alias.jar"), s.build);
        Files.writeString(s.descriptor, descriptor(List.of(
                artifact("build", "build", "../tools/po-xray.jar"),
                artifact("build", "build again", "../tools/po-xray.jar"),
                artifact("build", "hard-link alias", "../tools/po-xray-alias.jar"),
                artifact("skill", "source as skill", "ACME.sca"),
                artifact("skill", "descriptor as skill", "descriptor.json"),
                artifact("skill", SKILL_LABEL, abs(s.skill))), List.of()));
        ExportMetadata.Loaded loaded = ExportMetadata.read(s.descriptor);
        Map<String, String> before = snapshot(dir);

        ExportMetadata.preflight(s.sca, loaded, s.destinations);
        assertThat(s.out).as("preflight creates nothing").doesNotExist();
        assertThat(snapshot(dir)).isEqualTo(before);

        ScaInventory inv = inventory(s.sca);
        ExportManifest m = ExportMetadata.create(inv, loaded, s.writeOutputs(inv));
        assertThat(m.suppliedArtifacts()).containsExactly(
                measured("build", "build", s.build), measured("build", "build again", s.build),
                measured("build", "hard-link alias", alias), measured("skill", "source as skill", s.sca),
                measured("skill", "descriptor as skill", s.descriptor), measured("skill", SKILL_LABEL, s.skill));
        assertThat(m.suppliedArtifacts().get(2).sha256()).isEqualTo(sha256(s.build));
        assertThat(m.suppliedArtifacts().get(3).sha256()).isEqualTo(inv.source().sha256());
        assertThat(m.suppliedArtifacts().get(4).sha256()).isEqualTo(loaded.descriptorSha256());
        assertThat(m.issues()).isEmpty();

        Path out = s.out;
        Path json = out.resolve("ACME.json");
        Path md = out.resolve("ACME.md");
        Path jira = out.resolve("ACME.jira.txt");
        Path side = out.resolve("ACME.provenance.json");
        Path cwd = Path.of("").toAbsolutePath();
        Map<String, ThrowingCall> cases = new LinkedHashMap<>();
        cases.put("output == output", () -> ExportMetadata.preflight(s.sca, loaded, List.of(json, md, md, side)));
        cases.put("output == output after normalization",
                () -> ExportMetadata.preflight(s.sca, loaded, List.of(json, out.resolve("x/../ACME.json"), jira, side)));
        cases.put("output == source", () -> ExportMetadata.preflight(s.sca, loaded, List.of(s.sca, md, jira, side)));
        cases.put("output == source not normalized",
                () -> ExportMetadata.preflight(s.sca, loaded, List.of(json, md, s.in.resolve("./sub/../ACME.sca"), side)));
        cases.put("output == descriptor", () -> ExportMetadata.preflight(s.sca, loaded, List.of(json, md, jira, s.descriptor)));
        cases.put("output == artifact", () -> ExportMetadata.preflight(s.sca, loaded, List.of(json, s.build, jira, side)));
        cases.put("output == hard-link artifact path", () -> ExportMetadata.preflight(s.sca, loaded, List.of(json, md, alias, side)));
        cases.put("output == non-normalized Loaded.descriptorPath",
                () -> ExportMetadata.preflight(s.sca, new ExportMetadata.Loaded(s.in.resolve("sub/../descriptor.json").toString(),
                        loaded.descriptorSha256(), loaded.input()), List.of(json, md, jira, s.descriptor)));
        String relativeDescriptor = cwd.relativize(s.descriptor).toString();
        assertThat(Path.of(relativeDescriptor).isAbsolute()).isFalse();
        cases.put("output == relative Loaded.descriptorPath",
                () -> ExportMetadata.preflight(s.sca, new ExportMetadata.Loaded(relativeDescriptor,
                        loaded.descriptorSha256(), loaded.input()), List.of(json, md, jira, s.descriptor)));
        for (var c : cases.entrySet()) {
            assertPreflightFails(c.getKey(), c.getValue());
        }

        Map<String, List<Path>> hardLinked = new LinkedHashMap<>();
        Path outA = Files.createDirectories(dir.resolve("out-a"));
        Files.createLink(outA.resolve("ACME.md"), s.build);
        hardLinked.put("existing output hard-linked to a supplied artifact", destinations(outA));
        Path outB = Files.createDirectories(dir.resolve("out-b"));
        Files.createLink(outB.resolve("ACME.json"), s.sca);
        hardLinked.put("existing output hard-linked to the source", destinations(outB));
        Path outC = Files.createDirectories(dir.resolve("out-c"));
        Files.createLink(outC.resolve("ACME.provenance.json"), s.descriptor);
        hardLinked.put("existing output hard-linked to the descriptor", destinations(outC));
        Path outD = Files.createDirectories(dir.resolve("out-d"));
        Files.writeString(outD.resolve("ACME.md"), "old markdown");
        Files.createLink(outD.resolve("ACME.jira.txt"), outD.resolve("ACME.md"));
        hardLinked.put("two existing outputs hard-linked to each other", destinations(outD));
        for (var c : hardLinked.entrySet()) {
            assertPreflightFails(c.getKey(), () -> ExportMetadata.preflight(s.sca, loaded, c.getValue()));
        }
        assertThat(Files.readString(outD.resolve("ACME.md"))).isEqualTo("old markdown");
        assertThat(Files.readAllBytes(s.build)).isEqualTo(bytes("synthetic build v1"));

        Path outOk = Files.createDirectories(dir.resolve("out-ok"));
        for (Path p : destinations(outOk)) {
            Files.writeString(p, "old " + p.getFileName());
        }
        Map<String, String> beforeOk = snapshot(dir);
        ExportMetadata.preflight(s.sca, loaded, destinations(outOk));
        assertThat(snapshot(dir)).as("existing unrelated regular destinations pass and stay unchanged").isEqualTo(beforeOk);
    }

    @Test
    void preflightRejectsLinkedSourceAndUnsafeDestinations() throws Exception {
        Scene s = new Scene(dir);
        ExportMetadata.Loaded loaded = ExportMetadata.read(s.descriptor);
        Path real = Files.createDirectories(dir.resolve("real"));
        Files.copy(s.sca, real.resolve("ACME.sca"));
        Path link = junction(dir.resolve("link"), real);
        try {
            Path nonRegular = Files.createDirectories(dir.resolve("out-dir").resolve("ACME.md"));
            Path outJunction = Files.createDirectories(dir.resolve("out-junction"));
            Path destinationLink = junction(outJunction.resolve("ACME.jira.txt"), real);
            try {
                ExportMetadata.preflight(real.resolve("ACME.sca"), loaded, s.destinations);
                Map<String, ThrowingCall> cases = new LinkedHashMap<>();
                cases.put("source through a junction component",
                        () -> ExportMetadata.preflight(link.resolve("ACME.sca"), loaded, s.destinations));
                cases.put("source is a directory", () -> ExportMetadata.preflight(s.in, loaded, s.destinations));
                cases.put("source is missing", () -> ExportMetadata.preflight(s.in.resolve("missing.sca"), loaded, s.destinations));
                cases.put("output directory is a junction", () -> ExportMetadata.preflight(s.sca, loaded, destinations(link)));
                cases.put("output directory below a junction",
                        () -> ExportMetadata.preflight(s.sca, loaded, destinations(link.resolve("out"))));
                cases.put("existing destination is a directory",
                        () -> ExportMetadata.preflight(s.sca, loaded, destinations(nonRegular.getParent())));
                cases.put("existing destination is a junction",
                        () -> ExportMetadata.preflight(s.sca, loaded, destinations(outJunction)));
                cases.put("UNC output directory",
                        () -> ExportMetadata.preflight(s.sca, loaded, destinations(Path.of("\\\\server.invalid\\share\\out"))));
                cases.put("UNC forward-slash output directory",
                        () -> ExportMetadata.preflight(s.sca, loaded, destinations(Path.of("//server.invalid/share/out"))));
                for (var c : cases.entrySet()) {
                    assertPreflightFails(c.getKey(), c.getValue());
                }
                assertThat(real.resolve("out")).doesNotExist();
                assertThat(snapshot(real)).containsOnlyKeys("ACME.sca");
                assertThat(sha256(real.resolve("ACME.sca"))).isEqualTo(sha256(s.sca));
            } finally {
                deleteLink(destinationLink);
            }
        } finally {
            deleteLink(link);
        }
    }

    @Test
    void aliasUnresolvedArtifactRequiresAllDestinationsAbsent() throws Exception {
        Scene s = new Scene(dir);
        ScaInventory inv = inventory(s.sca);
        Path dangling = danglingJunction(dir.resolve("dangling"), dir.resolve("gone"));
        Path liveReal = Files.createDirectories(dir.resolve("live-real"));
        Files.write(liveReal.resolve("po-xray.jar"), bytes("synthetic build behind a junction"));
        Path live = junction(dir.resolve("live"), liveReal);
        try {
            Map<String, String> artifacts = new LinkedHashMap<>();
            artifacts.put("dangling junction component", abs(dangling.resolve("po-xray.jar")));
            artifacts.put("dangling junction itself", abs(dangling));
            artifacts.put("live junction component", abs(live.resolve("po-xray.jar")));
            int n = 0;
            for (var a : artifacts.entrySet()) {
                n++;
                Path descriptor = Files.writeString(s.in.resolve("alias-" + n + ".json"), descriptor(
                        List.of(artifact("build", a.getKey(), a.getValue()), artifact("skill", SKILL_LABEL, abs(s.skill))),
                        List.of()));
                ExportMetadata.Loaded loaded = ExportMetadata.read(descriptor);

                Path absent = dir.resolve("absent-" + n);
                ExportMetadata.preflight(s.sca, loaded, destinations(absent));
                Path empty = Files.createDirectories(dir.resolve("empty-" + n));
                ExportMetadata.preflight(s.sca, loaded, destinations(empty));
                Path occupied = Files.createDirectories(dir.resolve("occupied-" + n));
                Files.writeString(occupied.resolve("ACME.md"), "unrelated old markdown");
                assertPreflightFails(a.getKey() + " with one existing destination",
                        () -> ExportMetadata.preflight(s.sca, loaded, destinations(occupied)));
                assertThat(Files.readString(occupied.resolve("ACME.md"))).isEqualTo("unrelated old markdown");
                assertThat(snapshot(occupied)).containsOnlyKeys("ACME.md");

                ExportManifest m = ExportMetadata.create(inv, loaded, Scene.writeOutputs(absent, inv));
                assertThat(m.suppliedArtifacts()).as(a.getKey()).containsExactly(
                        new FileDigest("build", a.getKey(), a.getValue(), null, "unavailable"),
                        measured("skill", SKILL_LABEL, s.skill));
                assertThat(m.issues()).as(a.getKey()).extracting(Issue::severity, Issue::code, Issue::location, Issue::source)
                        .containsExactly(tuple("warning", UNAVAILABLE, null, a.getValue()));
            }
            assertThat(Files.readAllBytes(liveReal.resolve("po-xray.jar"))).isEqualTo(bytes("synthetic build behind a junction"));

            Path plainMissing = Files.writeString(s.in.resolve("plain-missing.json"), descriptor(
                    List.of(artifact("build", "plain missing build", "../tools/missing.jar"),
                            artifact("skill", SKILL_LABEL, abs(s.skill))), List.of()));
            ExportMetadata.Loaded loaded = ExportMetadata.read(plainMissing);
            Path occupied = Files.createDirectories(dir.resolve("occupied-plain"));
            Files.writeString(occupied.resolve("ACME.md"), "unrelated old markdown");
            ExportMetadata.preflight(s.sca, loaded, destinations(occupied));
            assertThat(Files.readString(occupied.resolve("ACME.md"))).isEqualTo("unrelated old markdown");
        } finally {
            deleteLink(live);
            deleteLink(dangling);
        }
    }

    @Test
    void unavailableArtifactsAndMissingRolesBecomeOrderedWarnings() throws Exception {
        Scene s = new Scene(dir);
        ScaInventory inv = inventory(s.sca);
        List<Path> outputs = s.writeOutputs(inv);
        Path missingSkill = dir.resolve("skills/missing.md");
        Path tools = dir.resolve("tools");
        Path liveReal = Files.createDirectories(dir.resolve("live-real"));
        Files.write(liveReal.resolve("po-xray.jar"), bytes("synthetic build behind a junction"));
        Path live = junction(dir.resolve("live"), liveReal);
        try {
            ExportManifest a = create(s, inv, outputs, "a", List.of(artifact("skill", "missing skill", "../skills/missing.md"),
                    artifact("build", "build directory", "../tools"), artifact("skill", "present skill", abs(s.skill))));
            assertThat(a.suppliedArtifacts()).containsExactly(unavailable("skill", "missing skill", missingSkill),
                    unavailable("build", "build directory", tools), measured("skill", "present skill", s.skill));
            assertThat(a.issues()).extracting(Issue::severity, Issue::code, Issue::location, Issue::source).containsExactly(
                    tuple("warning", UNAVAILABLE, null, abs(missingSkill)), tuple("warning", UNAVAILABLE, null, abs(tools)));
            assertThat(a.issues().get(0).message()).contains("skill", "missing skill");
            assertThat(a.issues().get(1).message()).contains("build", "build directory");

            ExportManifest b = create(s, inv, outputs, "b", List.of(artifact("skill", "missing skill", "../skills/missing.md")));
            assertThat(b.suppliedArtifacts()).containsExactly(unavailable("skill", "missing skill", missingSkill));
            assertThat(b.issues()).extracting(Issue::severity, Issue::code, Issue::location, Issue::source).containsExactly(
                    tuple("warning", UNAVAILABLE, null, abs(missingSkill)), tuple("warning", ROLE_MISSING, null, null));
            assertThat(b.issues().get(1).message()).contains("build");

            ExportManifest c = create(s, inv, outputs, "c", List.of());
            assertThat(c.suppliedArtifacts()).isEmpty();
            assertThat(c.issues()).extracting(Issue::severity, Issue::code, Issue::location, Issue::source).containsExactly(
                    tuple("warning", ROLE_MISSING, null, null), tuple("warning", ROLE_MISSING, null, null));
            assertThat(c.issues().get(0).message()).contains("build");
            assertThat(c.issues().get(1).message()).contains("skill");

            ExportManifest d = create(s, inv, outputs, "d", List.of(artifact("build", "present build", "../tools/po-xray.jar")));
            assertThat(d.suppliedArtifacts()).containsExactly(measured("build", "present build", s.build));
            assertThat(d.issues()).extracting(Issue::severity, Issue::code, Issue::location, Issue::source).containsExactly(
                    tuple("warning", ROLE_MISSING, null, null));
            assertThat(d.issues().get(0).message()).contains("skill");

            Path linked = live.resolve("po-xray.jar");
            ExportManifest e = create(s, inv, outputs, "e", List.of(artifact("build", "linked build", abs(linked)),
                    artifact("skill", "present skill", abs(s.skill))));
            assertThat(e.suppliedArtifacts()).containsExactly(unavailable("build", "linked build", linked),
                    measured("skill", "present skill", s.skill));
            assertThat(e.issues()).extracting(Issue::severity, Issue::code, Issue::location, Issue::source).containsExactly(
                    tuple("warning", UNAVAILABLE, null, abs(linked)));
            assertThat(e.issues().get(0).message()).contains("build", "linked build");
            assertThat(Files.readAllBytes(liveReal.resolve("po-xray.jar"))).isEqualTo(bytes("synthetic build behind a junction"));
            assertThat(inv.issues()).noneMatch(i -> i.code().startsWith("EXPORT_"));
        } finally {
            deleteLink(live);
        }
    }

    @Test
    void snapshotsAreImmutableAndJsonRoundTripsWithExplicitNulls() throws Exception {
        List<ArtifactInput> artifacts = new ArrayList<>(List.of(new ArtifactInput("build", "b", "b.jar")));
        List<VersionObservation> versions = new ArrayList<>(List.of(deployedOld()));
        ExportProvenanceInput input = new ExportProvenanceInput(1, artifacts, versions);
        artifacts.add(new ArtifactInput("skill", "s", "s.md"));
        versions.clear();
        assertThat(input.artifacts()).containsExactly(new ArtifactInput("build", "b", "b.jar"));
        assertThat(input.versions()).containsExactly(deployedOld());
        assertThatThrownBy(() -> input.artifacts().add(new ArtifactInput("skill", "s", "s.md")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> input.versions().clear()).isInstanceOf(UnsupportedOperationException.class);
        ExportProvenanceInput empty = new ExportProvenanceInput(1, null, null);
        assertThat(empty.artifacts()).isEmpty();
        assertThat(empty.versions()).isEmpty();

        Scene s = new Scene(dir);
        ScaInventory inv = inventory(s.sca);
        Files.writeString(s.descriptor, descriptor(List.of(artifact("skill", "missing skill", "../skills/missing.md")),
                List.of(json(deployedOld()), json(releaseNew()))));
        ExportMetadata.Loaded loaded = ExportMetadata.read(s.descriptor);
        assertThatThrownBy(() -> loaded.input().artifacts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> loaded.input().versions().add(releaseNew())).isInstanceOf(UnsupportedOperationException.class);
        List<Path> outputs = new ArrayList<>(s.writeOutputs(inv));
        ExportManifest m = ExportMetadata.create(inv, loaded, outputs);
        outputs.clear();
        assertThat(m.outputs()).hasSize(3);
        assertThatThrownBy(() -> m.outputs().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> m.issues().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> m.suppliedArtifacts().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> m.versions().clear()).isInstanceOf(UnsupportedOperationException.class);

        List<FileDigest> outs = new ArrayList<>(m.outputs());
        List<FileDigest> supplied = new ArrayList<>(m.suppliedArtifacts());
        List<VersionObservation> obs = new ArrayList<>(m.versions());
        List<Issue> issues = new ArrayList<>(m.issues());
        ExportManifest copy = new ExportManifest(1, m.generator(), m.outputCreatedAt(), m.source(), m.descriptor(), outs,
                supplied, obs, issues);
        outs.clear();
        supplied.clear();
        obs.clear();
        issues.clear();
        assertThat(copy).isEqualTo(m);
        ExportManifest bare = new ExportManifest(1, m.generator(), m.outputCreatedAt(), m.source(), m.descriptor(), null,
                null, null, null);
        assertThat(bare.outputs()).isEmpty();
        assertThat(bare.suppliedArtifacts()).isEmpty();
        assertThat(bare.versions()).isEmpty();
        assertThat(bare.issues()).isEmpty();

        String json = Json.write(m);
        JsonNode tree = Json.mapper().readTree(json);
        assertThat(tree.propertyNames()).containsExactly("formatVersion", "generator", "outputCreatedAt", "source",
                "descriptor", "outputs", "suppliedArtifacts", "versions", "issues");
        JsonNode missing = tree.path("suppliedArtifacts").get(0);
        assertThat(missing.propertyNames()).containsExactly("role", "label", "path", "sha256", "state");
        assertThat(missing.path("sha256").isNull()).isTrue();
        assertThat(missing.path("state").stringValue()).isEqualTo("unavailable");
        assertThat(tree.path("versions").get(0).has("sourceSha256")).isTrue();
        assertThat(tree.path("versions").get(0).path("sourceSha256").isNull()).isTrue();
        assertThat(tree.path("versions").get(0).propertyNames()).containsExactly("role", "subject", "value",
                "sourceKind", "locator", "sourceSha256", "observedAt", "scope", "limitations");
        assertThat(tree.path("descriptor").path("state").stringValue()).isEqualTo("read-time-snapshot");
        assertThat(tree.path("issues").size()).isEqualTo(2);
        assertThat(Json.mapper().readValue(json, ExportManifest.class)).isEqualTo(m);

        JsonNode bareTree = Json.mapper().readTree(Json.write(bare));
        for (String list : List.of("outputs", "suppliedArtifacts", "versions", "issues")) {
            assertThat(bareTree.has(list)).as(list).isTrue();
            assertThat(bareTree.get(list).isArray()).as(list).isTrue();
            assertThat(bareTree.get(list).size()).as(list).isZero();
        }
        assertThat(Json.mapper().readValue(Json.write(bare), ExportManifest.class)).isEqualTo(bare);

        String inputJson = Json.write(loaded.input());
        assertThat(Json.mapper().readTree(inputJson).path("versions").get(0).path("observedAt").isNull()).isFalse();
        assertThat(Json.mapper().readTree(inputJson).path("versions").get(0).path("sourceSha256").isNull()).isTrue();
        assertThat(Json.mapper().readValue(inputJson, ExportProvenanceInput.class)).isEqualTo(loaded.input());
        Path rewritten = Files.writeString(s.in.resolve("rewritten.json"), inputJson);
        assertThat(ExportMetadata.read(rewritten).input()).isEqualTo(loaded.input());
    }

    private ExportManifest create(Scene s, ScaInventory inv, List<Path> outputs, String name, List<String> artifacts)
            throws IOException {
        Path descriptor = Files.writeString(s.in.resolve("descriptor-" + name + ".json"), descriptor(artifacts, List.of()));
        return ExportMetadata.create(inv, ExportMetadata.read(descriptor), outputs);
    }

    private void assertPreflightFails(String name, ThrowingCall call) throws IOException {
        Map<String, String> before = snapshot(dir);
        Throwable thrown = catchThrowable(call::run);
        assertThat(thrown).as(name).isInstanceOf(IOException.class);
        assertThat(snapshot(dir)).as(name + ": nothing written or changed").isEqualTo(before);
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
    }
}
