package io.poxray.xi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.w3c.dom.Element;

/**
 * Content blobs of ESR objects ({@code <blob type="zip" zipped="true">!zip!<base64></blob>}): base64 of a zip with
 * one entry {@code value}. For {@code type="zip"} the entry is itself a zip whose first entry is the payload
 * (message mapping {@code metadata}, function library {@code metaData.xml}); {@code xml} blobs hold the payload
 * directly. Verified on xipod (PO 7.50).
 */
public final class XiBlob {

    /** Upper bound for one decompressed entry; mapping metadata is typically well below 1 MB. */
    private static final int MAX_SIZE = 64 * 1024 * 1024;

    private XiBlob() {
    }

    /** The payload of a {@code blob} element, unwrapping nested zips; null if the element is empty. */
    public static byte[] payload(Element blob) {
        String text = blob == null ? null : blob.getTextContent().strip();
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.startsWith("!")) {
            int end = text.indexOf('!', 1);
            text = end < 0 ? text : text.substring(end + 1);
        }
        byte[] data = firstEntry(Base64.getMimeDecoder().decode(text));
        while (isZip(data)) {
            data = firstEntry(data);
        }
        return data;
    }

    private static boolean isZip(byte[] data) {
        return data.length > 3 && data[0] == 'P' && data[1] == 'K' && data[2] == 3 && data[3] == 4;
    }

    private static byte[] firstEntry(byte[] zip) {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e = in.getNextEntry();
            if (e == null) {
                throw new IllegalArgumentException("Empty blob archive");
            }
            return read(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot unpack blob: " + e.getMessage(), e);
        }
    }

    private static byte[] read(InputStream in) throws IOException {
        byte[] data = in.readNBytes(MAX_SIZE + 1);
        if (data.length > MAX_SIZE) {
            throw new IOException("blob entry larger than " + MAX_SIZE + " bytes");
        }
        return data;
    }
}
