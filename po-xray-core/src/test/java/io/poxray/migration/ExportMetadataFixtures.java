package io.poxray.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.poxray.PoXray;
import io.poxray.SyntheticSca;
import io.poxray.json.Json;
import io.poxray.model.ExportManifest;
import io.poxray.model.ExportProvenanceInput;
import io.poxray.model.ScaInventory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Synthetic fixtures of the export provenance sidecar (MIG-36): an actual synthetic SCA, a descriptor, build and
 * skill bytes in a temporary directory, digests of real bytes, a NOFOLLOW snapshot of a directory tree and a local
 * junction (Windows, without privilege) or symbolic link (elsewhere). Public for the CLI tests (core test-jar).
 */
public final class ExportMetadataFixtures {

    public static final String BUILD_LABEL = "po-xray build (caller supplied)";
    public static final String SKILL_LABEL = "sap-po-migration skill (caller supplied)";
    public static final String HEX_A = "a".repeat(64);
    public static final String HEX_B = "0123456789abcdef".repeat(4);

    private ExportMetadataFixtures() {
    }

    /**
     * {@code <dir>/in/ACME.sca} (actual synthetic SCA), {@code <dir>/tools/po-xray.jar}, {@code
     * <dir>/skills/sap-po/SKILL.md}, {@code <dir>/in/descriptor.json} naming both (build relative to the descriptor,
     * skill absolute) with two version observations; {@code <dir>/out} is not created.
     */
    public static final class Scene {
        public final Path dir;
        public final Path in;
        public final Path sca;
        public final Path build;
        public final Path skill;
        public final Path descriptor;
        public final Path out;
        public final List<Path> destinations;

        public Scene(Path dir) throws IOException {
            this.dir = dir;
            in = Files.createDirectories(dir.resolve("in"));
            sca = SyntheticSca.write(in.resolve("ACME.sca"));
            build = Files.createDirectories(dir.resolve("tools")).resolve("po-xray.jar");
            Files.write(build, bytes("synthetic build v1"));
            skill = Files.createDirectories(dir.resolve("skills").resolve("sap-po")).resolve("SKILL.md");
            Files.write(skill, bytes("synthetic skill v1"));
            descriptor = in.resolve("descriptor.json");
            Files.writeString(descriptor, descriptor(
                    List.of(artifact("build", BUILD_LABEL, "../tools/po-xray.jar"),
                            artifact("skill", SKILL_LABEL, abs(skill))),
                    List.of(json(deployedOld()), json(releaseNew()))));
            out = dir.resolve("out");
            destinations = destinations(out);
        }

        /** Writes the three legacy outputs (inventory JSON, Markdown, Jira) into {@code out}; returns them in that order. */
        public List<Path> writeOutputs(ScaInventory inventory) throws IOException {
            return writeOutputs(out, inventory);
        }

        public static List<Path> writeOutputs(Path out, ScaInventory inventory) throws IOException {
            Files.createDirectories(out);
            Path json = Files.writeString(out.resolve("ACME.json"), Json.write(inventory));
            Path md = Files.writeString(out.resolve("ACME.md"), "# synthetic markdown output\n");
            Path jira = Files.writeString(out.resolve("ACME.jira.txt"), "h1. synthetic jira output\n");
            return List.of(json, md, jira);
        }
    }

    /** The four planned destinations of base name ACME in {@code out}: inventory, Markdown, Jira, sidecar. */
    public static List<Path> destinations(Path out) {
        return List.of(out.resolve("ACME.json"), out.resolve("ACME.md"), out.resolve("ACME.jira.txt"),
                out.resolve("ACME.provenance.json"));
    }

    public static ScaInventory inventory(Path sca) {
        return PoXray.read(sca);
    }

    /** The actual inventory with only {@code generator.readAt} replaced (to tell it apart from the creation time). */
    public static ScaInventory withReadAt(ScaInventory inv, String readAt) {
        return new ScaInventory(inv.schemaVersion(),
                new ScaInventory.Generator(inv.generator().name(), inv.generator().version(), readAt), inv.source(),
                inv.developmentComponents(), inv.bpm(), inv.ejb(), inv.issues(), inv.unmapped());
    }

    /** The synthetic SCA whose SAP manifest also carries {@code pr_release}. */
    public static Path scaWithRelease(Path file, String release) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(SyntheticSca.build(SyntheticSca.BEAN)));
                ZipOutputStream out = new ZipOutputStream(bytes)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                byte[] data = in.readAllBytes();
                if (e.getName().equals("META-INF/SAP_MANIFEST.MF")) {
                    data = bytes("Manifest-Version: 1.0\r\nkeyname: ACME_CALC\r\nkeyvendor: acme.com\r\npr_release: "
                            + release + "\r\n\r\n");
                }
                out.putNextEntry(new ZipEntry(e.getName()));
                out.write(data);
                out.closeEntry();
            }
        }
        Files.write(file, bytes.toByteArray());
        return file;
    }

    public static ExportProvenanceInput.VersionObservation deployedOld() {
        return new ExportProvenanceInput.VersionObservation("deployed", "ACME integration package", "deployed-old",
                "tenant-observed", "synthetic tenant monitor note", null, "2026-10-01", "synthetic tenant",
                "Observed once by the caller; not authenticated");
    }

    public static ExportProvenanceInput.VersionObservation releaseNew() {
        return new ExportProvenanceInput.VersionObservation("package-release", "ACME integration package",
                "release-new", "export", "file:///definitely-missing/release.json", HEX_A,
                "2026-10-20T10:00:00+02:00", "downloaded package", "A download, not the deployed state");
    }

    public static String descriptor(List<String> artifacts, List<String> versions) {
        return "{\n  \"formatVersion\" : 1,\n  \"artifacts\" : [ " + String.join(", ", artifacts)
                + " ],\n  \"versions\" : [ " + String.join(", ", versions) + " ]\n}\n";
    }

    public static String artifact(String role, String label, String path) {
        return "{ \"role\" : " + q(role) + ", \"label\" : " + q(label) + ", \"path\" : " + q(path) + " }";
    }

    public static String json(ExportProvenanceInput.VersionObservation v) {
        return "{ \"role\" : " + q(v.role()) + ", \"subject\" : " + q(v.subject()) + ", \"value\" : " + q(v.value())
                + ", \"sourceKind\" : " + q(v.sourceKind()) + ", \"locator\" : " + q(v.locator())
                + ", \"sourceSha256\" : " + q(v.sourceSha256()) + ", \"observedAt\" : " + q(v.observedAt())
                + ", \"scope\" : " + q(v.scope()) + ", \"limitations\" : " + q(v.limitations()) + " }";
    }

    /** A JSON string literal ({@code null} for null). */
    public static String q(String value) {
        return value == null ? "null" : Json.write(value);
    }

    public static ExportManifest.FileDigest measured(String role, String label, Path file) throws IOException {
        return new ExportManifest.FileDigest(role, label, abs(file), sha256(file), "measured");
    }

    public static ExportManifest.FileDigest unavailable(String role, String label, Path file) {
        return new ExportManifest.FileDigest(role, label, abs(file), null, "unavailable");
    }

    public static List<ExportManifest.FileDigest> outputDigests(List<Path> outputs) throws IOException {
        List<ExportManifest.FileDigest> result = new ArrayList<>();
        for (Path p : outputs) {
            result.add(measured("output", p.getFileName().toString(), p));
        }
        return result;
    }

    public static String abs(Path p) {
        return p.toAbsolutePath().normalize().toString();
    }

    public static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static String sha256(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Every entry under {@code root} read without following links: relative path to the SHA256 of a regular file,
     * {@code "dir"} or {@code "link"} (a symbolic link or reparse entry, never entered).
     */
    public static Map<String, String> snapshot(Path root) throws IOException {
        Map<String, String> result = new TreeMap<>();
        walk(root, root, result);
        return result;
    }

    private static void walk(Path root, Path dir, Map<String, String> result) throws IOException {
        List<Path> children;
        try (Stream<Path> list = Files.list(dir)) {
            children = list.toList();
        }
        for (Path p : children) {
            BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            String key = root.relativize(p).toString().replace('\\', '/');
            if (a.isSymbolicLink() || a.isOther()) {
                result.put(key, "link");
            } else if (a.isDirectory()) {
                result.put(key, "dir");
                walk(root, p, result);
            } else {
                result.put(key, sha256(p));
            }
        }
    }

    /**
     * Creates {@code link} pointing at the directory {@code target}: a junction ({@code cmd /c mklink /J}, no
     * privilege) on Windows, a symbolic link elsewhere. A failed junction is reported as ENVIRONMENT_BLOCKED, never
     * passed.
     */
    public static Path junction(Path link, Path target) throws IOException, InterruptedException {
        if (System.getProperty("os.name").startsWith("Windows")) {
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", link.toString(), target.toString())
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = process.waitFor();
            System.out.println("LOCAL_JUNCTION_SETUP_EXIT=" + exit + " " + output.trim());
            assertEquals(0, exit, "ENVIRONMENT_BLOCKED local junction fixture: " + output);
        } else {
            Files.createSymbolicLink(link, target);
        }
        return link;
    }

    /** A junction (or symbolic link) whose target directory no longer exists. */
    public static Path danglingJunction(Path link, Path target) throws IOException, InterruptedException {
        Files.createDirectories(target);
        junction(link, target);
        Files.delete(target);
        return link;
    }

    /** Removes a junction or link entry itself (never its target's content). */
    public static void deleteLink(Path link) throws IOException {
        Files.deleteIfExists(link);
    }
}
