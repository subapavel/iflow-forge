package io.poxray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Synthetic TPZ export for tests: {@code Metadata.properties} plus one {@code .tpt} with the given xiObj XMLs. */
public final class SyntheticTpz {

    private SyntheticTpz() {
    }

    public static Path write(Path file, String metadata, List<String> xiObjs) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("Metadata.properties"));
                zip.write(metadata.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
                zip.putNextEntry(new ZipEntry("content.tpt"));
                zip.write(tpt(xiObjs));
                zip.closeEntry();
            }
            Files.write(file, out.toByteArray());
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A {@code .tpt} container with one {@code modelElement} segment per object (layout as in real exports). */
    static byte[] tpt(List<String> xiObjs) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {0, 0, 0, 0, (byte) 0xdc, 0x35, (byte) 0x8a, (byte) 0x91}); // magic
        StringBuilder segments = new StringBuilder();
        for (String xiObj : xiObjs) {
            int offset = out.size();
            byte[] obj = xiObj.getBytes(StandardCharsets.UTF_8);
            out.write(ByteBuffer.allocate(8).putLong(obj.length).array());
            out.write(obj);
            segments.append("<segment type=\"modelElement\" offset=\"").append(offset).append("\" length=\"")
                    .append(out.size() - offset).append("\" />");
        }
        int catalogOffset = out.size();
        byte[] catalog = ("<?xml version=\"1.0\" encoding=\"utf-8\"?><catalog><segments><objectList>" + segments
                + "</objectList></segments></catalog>").getBytes(StandardCharsets.UTF_8);
        out.write(ByteBuffer.allocate(8).putLong(catalog.length).array());
        out.write(catalog);
        out.write(ByteBuffer.allocate(8).putLong(catalogOffset).array());
        return out.toByteArray();
    }
}
