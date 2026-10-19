package io.poxray.cpi;

import static io.poxray.cpi.IFlowFixture.END;
import static io.poxray.cpi.IFlowFixture.IFLW_DIR;
import static io.poxray.cpi.IFlowFixture.START;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link PackageUnpacker} on synthetic package exports built by {@link IFlowFixture#packageExport}. */
class PackageUnpackerTest {

    private static Path artifact(String id, Path dir) throws IOException {
        return IFlowFixture.iflow(id).name("Flow " + id).version("1.0.3")
                .messageFlow("In", "JMS", "Sender")
                .step("A", "A", "Script", "script", "a.groovy").flow(START, "A").flow("A", END)
                .param("p1", "d1")
                .resource("script/a.groovy", "return message")
                .write(dir.resolve(id));
    }

    private static List<Path> files(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).map(root::relativize).sorted().toList();
        }
    }

    private static List<String> topLevel(Path root) throws IOException {
        try (Stream<Path> list = Files.list(root)) {
            return list.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private static Path onlyIflw(Path artifactDir) throws IOException {
        try (Stream<Path> walk = Files.walk(artifactDir)) {
            List<Path> iflw = walk.filter(p -> p.getFileName().toString().endsWith(".iflw")).toList();
            assertThat(iflw).hasSize(1);
            return iflw.getFirst();
        }
    }

    @Test
    void unpacksUnderSymbolicNameAndSkipsParametersProp(@TempDir Path dir) throws Exception {
        Path a = IFlowFixture.iflow("a.one").name("A One").version("1.0.0")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .param("p1", "d1").configured("p1", "secret")
                .file("Configured_Custom_Tags.txt", "tag=value\n")
                .write(dir.resolve("src/a.one"));
        Path export = IFlowFixture.packageExport(dir.resolve("export"), List.of(a), "Change log\r\n");
        Path out = dir.resolve("out");

        PackageUnpacker.unpack(export, out, List.of());

        assertThat(out.resolve("a.one/META-INF/MANIFEST.MF")).isRegularFile();
        assertThat(out.resolve("a.one").resolve(IFLW_DIR).resolve("a.one.iflw")).isRegularFile();
        assertThat(out.resolve("a.one/src/main/resources/parameters.propdef")).isRegularFile();
        assertThat(files(out)).extracting(p -> p.getFileName().toString())
                .doesNotContain("parameters.prop", "Configured_Custom_Tags.txt", "contentmetadata.md", "resources.cnt");
        assertThat(topLevel(out)).as("no file or folder for Url, ContentPackage, contentmetadata.md")
                .containsExactly("CHANGELOG.txt", "a.one");
    }

    @Test
    void stripsTokensFromSymbolicNameManifestAndProject(@TempDir Path dir) throws Exception {
        Path src = artifact("x.y.z.CZTEST", dir.resolve("src"));
        byte[] iflw = Files.readAllBytes(onlyIflw(src));
        assertThat(Files.readString(src.resolve(".project"))).contains("<name>x.y.z.CZTEST</name>");
        Path export = IFlowFixture.packageExport(dir.resolve("export"), List.of(src), "Change log\r\n");
        Path out = dir.resolve("out");

        PackageUnpacker.unpack(export, out, List.of(".CZTEST"));

        assertThat(out.resolve("x.y.z")).isDirectory();
        assertThat(out.resolve("x.y.z.CZTEST")).doesNotExist();
        Path manifest = out.resolve("x.y.z/META-INF/MANIFEST.MF");
        assertThat(Files.readString(manifest)).doesNotContain("CZTEST");
        assertThat(IFlowFixture.manifestHeaders(manifest)).containsEntry("Bundle-SymbolicName", "x.y.z;singleton:=true");
        assertThat(Files.readString(out.resolve("x.y.z/.project"))).contains("<name>x.y.z</name>")
                .doesNotContain("CZTEST");
        assertThat(Files.readAllBytes(onlyIflw(out.resolve("x.y.z")))).isEqualTo(iflw);
        assertThat(IFlowReader.read(out.resolve("x.y.z")).id()).isEqualTo("x.y.z");
    }

    @Test
    void writesChangelogWithLf(@TempDir Path dir) throws Exception {
        Path a = artifact("a.one", dir.resolve("src"));
        Path export = IFlowFixture.packageExport(dir.resolve("export"), List.of(a),
                "Change log for \"P\"\r\nDate format: dd/mm/yyyy\r\n");
        Path out = dir.resolve("out");

        PackageUnpacker.unpack(export, out, List.of());

        String changelog = Files.readString(out.resolve("CHANGELOG.txt"), StandardCharsets.UTF_8);
        assertThat(changelog).isEqualTo("Change log for \"P\"\nDate format: dd/mm/yyyy\n").doesNotContain("\r");
    }

    @Test
    void unpacksAZippedExportTheSameAsTheFolder(@TempDir Path dir) throws Exception {
        Path a = artifact("a.one", dir.resolve("src"));
        Path b = IFlowFixture.iflow("b.scripts").name("B Scripts").version("1.0.0").scriptCollection()
                .resource("script/b.groovy", "return message")
                .write(dir.resolve("src/b.scripts"));
        Path export = IFlowFixture.packageExport(dir.resolve("export"), List.of(a, b), "Change log\r\n");
        Path exportZip = IFlowFixture.zip(export, dir.resolve("export.zip"));
        Path fromFolder = dir.resolve("out-folder");
        Path fromZip = dir.resolve("out-zip");

        PackageUnpacker.unpack(export, fromFolder, List.of());
        PackageUnpacker.unpack(exportZip, fromZip, List.of());

        List<Path> folderFiles = files(fromFolder);
        assertThat(folderFiles).contains(Path.of("CHANGELOG.txt"), Path.of("a.one/META-INF/MANIFEST.MF"),
                Path.of("b.scripts/src/main/resources/script/b.groovy"));
        assertThat(files(fromZip)).isEqualTo(folderFiles);
        Map<Path, String> folderBytes = new LinkedHashMap<>();
        Map<Path, String> zipBytes = new LinkedHashMap<>();
        for (Path p : folderFiles) {
            folderBytes.put(p, new String(Files.readAllBytes(fromFolder.resolve(p)), StandardCharsets.ISO_8859_1));
            zipBytes.put(p, new String(Files.readAllBytes(fromZip.resolve(p)), StandardCharsets.ISO_8859_1));
        }
        assertThat(zipBytes).isEqualTo(folderBytes);
    }
}
