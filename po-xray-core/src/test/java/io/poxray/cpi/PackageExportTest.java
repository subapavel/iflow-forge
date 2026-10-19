package io.poxray.cpi;

import static io.poxray.cpi.IFlowFixture.END;
import static io.poxray.cpi.IFlowFixture.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link PackageExport} and edge cases of {@link PackageUnpacker} on synthetic package exports. */
class PackageExportTest {

    private static Path export(Path dir) throws IOException {
        Path a = IFlowFixture.iflow("a.one").name("A One").version("1.0.0")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .write(dir.resolve("src/a.one"));
        Path b = IFlowFixture.iflow("b.scripts").name("B Scripts").version("2.0.0").scriptCollection()
                .resource("script/b.groovy", "return message")
                .write(dir.resolve("src/b.scripts"));
        return IFlowFixture.packageExport(dir.resolve("export"), List.of(a, b), "Change log\r\n");
    }

    @Test
    void readsTheEntriesAlikeFromFolderAndZip(@TempDir Path dir) throws Exception {
        Path export = export(dir);
        Path exportZip = IFlowFixture.zip(export, dir.resolve("export.zip"));

        PackageExport folder = PackageExport.read(export);

        assertThat(folder.entries()).extracting(PackageExport.Entry::resourceType)
                .containsExactly("Url", "ContentPackage", "IFlow", "ScriptCollection", "ChangeLog");
        PackageExport.Entry iflow = folder.entries().get(2);
        assertThat(iflow.id()).isEqualTo(IFlowFixture.resourceId(0));
        assertThat(iflow.displayName()).isEqualTo("A One");
        assertThat(iflow.semanticVersion()).isEqualTo("1.0.0");
        assertThat(iflow.content()).isEqualTo(Path.of(IFlowFixture.resourceId(0) + "_content"));
        assertThat(export.resolve(iflow.content())).isRegularFile();
        assertThat(PackageExport.read(exportZip)).isEqualTo(folder);
    }

    @Test
    void decodesMimeBase64WithLineBreaks(@TempDir Path dir) throws Exception {
        Path export = export(dir);
        Path cnt = export.resolve("resources.cnt");
        byte[] json = Base64.getDecoder().decode(Files.readString(cnt, StandardCharsets.US_ASCII));
        Files.writeString(cnt, Base64.getMimeEncoder().encodeToString(json) + "\r\n", StandardCharsets.US_ASCII);
        assertThat(Files.readString(cnt).strip()).as("fixture breaks the Base64 into lines").contains("\r\n");

        assertThat(IFlowReader.readAll(export)).extracting(IFlow::id).containsExactly("a.one", "b.scripts");
    }

    @Test
    void aFolderWithoutResourcesCntIsNoPackageExport(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("readme.txt"), "not an export");
        Path out = dir.resolve("out");

        assertThatThrownBy(() -> PackageExport.read(dir)).isInstanceOf(IOException.class)
                .hasMessageContaining("resources.cnt");
        assertThatThrownBy(() -> PackageUnpacker.unpack(dir, out, List.of())).isInstanceOf(IOException.class);
    }

    @Test
    void unpackRejectsAnEntryOutsideTheArtifactFolder(@TempDir Path dir) throws Exception {
        Path export = export(dir);
        Path content = export.resolve(IFlowFixture.resourceId(0) + "_content");
        try (OutputStream os = Files.newOutputStream(content); ZipOutputStream zip = new ZipOutputStream(os)) {
            zip.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
            zip.write(("Manifest-Version: 1.0\r\nBundle-SymbolicName: a.one;singleton:=true\r\n"
                    + "Bundle-Name: A One\r\nBundle-Version: 1.0.0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../../evil.txt"));
            zip.write("x".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path out = dir.resolve("deep/out");

        assertThatThrownBy(() -> PackageUnpacker.unpack(export, out, List.of())).isInstanceOf(IOException.class)
                .hasMessageContaining("../../evil.txt");
        try (Stream<Path> walk = Files.walk(dir)) {
            assertThat(walk.map(p -> p.getFileName().toString())).doesNotContain("evil.txt");
        }
    }

    @Test
    void stripRefoldsALongSymbolicNameAt72BytesAndKeepsOtherLines(@TempDir Path dir) throws Exception {
        String id = "com.example.integration.pipeline.generic.step04.receiver.determination.long";
        Path src = IFlowFixture.iflow(id + ".CZTEST").name("Long").version("1.0.0")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .write(dir.resolve("src/long"));
        Path export = IFlowFixture.packageExport(dir.resolve("export"), List.of(src), "Change log\r\n");
        Path out = dir.resolve("out");

        PackageUnpacker.unpack(export, out, List.of(".CZTEST"));

        Path manifest = out.resolve(id).resolve("META-INF/MANIFEST.MF");
        String text = Files.readString(manifest, StandardCharsets.UTF_8);
        assertThat(text).doesNotContain("CZTEST").contains("\r\nBundle-Name: Long\r\nBundle-Version: 1.0.0\r\n");
        assertThat(text.split("\r\n")).allSatisfy(line -> assertThat(line.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(72));
        assertThat(text).contains("Bundle-SymbolicName: " + (id + ";singleton:=true").substring(0, 51) + "\r\n ");
        assertThat(IFlowFixture.manifestHeaders(manifest)).containsEntry("Bundle-SymbolicName", id + ";singleton:=true");
    }
}
