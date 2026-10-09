package io.poxray.xi;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.TestData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TpzArchiveTest {

    @Test
    void readsSyntheticContainer(@TempDir Path dir) throws IOException {
        Path tpz = dir.resolve("x.tpz");
        Files.write(tpz, zip("Metadata.properties", "swcv=aaaabbbb\nsystemtype=IBD 7.1\n".getBytes(StandardCharsets.UTF_8),
                "x.tpt", tpt(XiObjectParserTest.XIOBJ)));

        TpzArchive a = TpzArchive.open(tpz);

        assertThat(a.metadata()).containsEntry("systemtype", "IBD 7.1");
        assertThat(a.objects()).singleElement().satisfies(o -> assertThat(o.id().key(0)).isEqualTo("OrderCreate"));
    }

    @Test
    void readsDirectoryExport() {
        TpzArchive a = TpzArchive.open(TestData.require("XI7_1_directory-objs.tpz"));

        assertThat(a.metadata()).containsEntry("systemtype", "IBC 7.1");
        assertThat(a.objects()).singleElement().satisfies(ico -> {
            assertThat(ico.typeId()).isEqualTo("AllInOne");
            assertThat(ico.id().isDirectory()).isTrue();
            assertThat(ico.id().key(1)).isEqualTo("CD1CLNT300"); // sender component
            assertThat(ico.linksStartingWith("MAP")).singleElement()
                    .satisfies(l -> assertThat(l.target().typeId()).isEqualTo("MAPPING"));
            assertThat(ico.link("receiver")).hasValueSatisfying(l -> assertThat(l.target().key(1)).isEqualTo("V3S_D"));
            assertThat(ico.content().getLocalName()).isEqualTo("AllInOne");
        });
    }

    @Test
    void readsEsrExportAndResolvesLinksWithinIt() {
        TpzArchive a = TpzArchive.open(TestData.require("XI7_1_PPAS_AEM_1.0_of_ppas.cz.tpz"));
        InMemoryObjectSource source = new InMemoryObjectSource().addAll(a.objects());

        assertThat(a.objects()).hasSize(20);
        assertThat(source.ofType("MAPPING")).hasSize(2).allSatisfy(om -> {
            XiLink trafo = om.link("REQUEST_TRAFO").orElseThrow();
            assertThat(source.resolve(trafo.target())).hasValueSatisfying(
                    mm -> assertThat(mm.typeId()).isEqualTo("XI_TRAFO"));
        });
        XiObject mt = source.ofType("ifmmessage").getFirst();
        assertThat(source.resolve(mt.link("TYPE").orElseThrow().target()))
                .hasValueSatisfying(dt -> assertThat(dt.typeId()).isEqualTo("ifmtypedef"));
    }

    @Test
    void readsIflowExport() {
        TpzArchive a = TpzArchive.open(TestData.require("SAPCRM_ApplicationStatus.tpz"));

        assertThat(a.objects()).extracting(XiObject::typeId)
                .containsExactlyInAnyOrder("Channel", "Channel", "Service", "Service", "IFLOW", "DirectoryView");
    }

    // ------------------------------------------------------------------------------------------ fixture

    /** Minimal .tpt: one modelElement segment and a catalog, offsets as in real exports. */
    static byte[] tpt(String xiObj) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {0, 0, 0, 0, (byte) 0xdc, 0x35, (byte) 0x8a, (byte) 0x91}); // magic
        int segmentOffset = out.size();
        byte[] obj = xiObj.getBytes(StandardCharsets.UTF_8);
        out.write(ByteBuffer.allocate(8).putLong(obj.length).array());
        out.write(obj);
        int segmentLength = out.size() - segmentOffset;
        int catalogOffset = out.size();
        String catalog = "<?xml version=\"1.0\" encoding=\"utf-8\"?><catalog><segments><objectList>"
                + "<segment type=\"modelElement\" offset=\"" + segmentOffset + "\" length=\"" + segmentLength + "\" />"
                + "</objectList></segments></catalog>";
        byte[] cat = catalog.getBytes(StandardCharsets.UTF_8);
        out.write(ByteBuffer.allocate(8).putLong(cat.length).array());
        out.write(cat);
        out.write(ByteBuffer.allocate(8).putLong(catalogOffset).array());
        return out.toByteArray();
    }

    private static byte[] zip(Object... nameContent) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (int i = 0; i < nameContent.length; i += 2) {
                z.putNextEntry(new ZipEntry((String) nameContent[i]));
                z.write((byte[]) nameContent[i + 1]);
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
