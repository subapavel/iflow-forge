package io.poxray.xi;

import io.poxray.sca.ZipArchive;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ESR / Integration Directory transport file (TPZ): a zip with {@code Metadata.properties} and a binary
 * {@code .tpt} container (plus optional {@code .dlt} delta for another release, ignored: same objects).
 * <p>
 * {@code .tpt} layout (docs/SOURCES-ESR-DIRECTORY.md §5): the last 8 bytes are the big-endian offset of the catalog
 * segment; the catalog XML lists segments ({@code type}, {@code offset}, {@code length}); each segment starts with an
 * 8-byte big-endian length, some have an additional 8-byte CRC before the XML. Objects are the
 * {@code modelElement} segments ({@code p1:xiObj}).
 */
public final class TpzArchive {

    private static final Pattern SEGMENT =
            Pattern.compile("<segment\\s+type=\"([^\"]+)\"\\s+offset=\"(\\d+)\"\\s+length=\"(\\d+)\"");

    private final String name;
    private final Map<String, String> metadata;
    private final List<XiObject> objects;

    private TpzArchive(String name, Map<String, String> metadata, List<XiObject> objects) {
        this.name = name;
        this.metadata = metadata;
        this.objects = objects;
    }

    public static TpzArchive open(Path file) {
        ZipArchive zip = ZipArchive.open(file);
        Map<String, String> metadata = new LinkedHashMap<>();
        zip.get("Metadata.properties").ifPresent(b -> metadata.putAll(properties(b)));
        List<XiObject> objects = new ArrayList<>();
        for (var e : zip.entries().entrySet()) {
            if (e.getKey().endsWith(".tpt")) {
                objects.addAll(readTpt(e.getValue(), file.getFileName() + "!/" + e.getKey()));
            }
        }
        return new TpzArchive(file.getFileName().toString(), metadata, List.copyOf(objects));
    }

    public String name() {
        return name;
    }

    /** {@code Metadata.properties}: swcv, sourcesystem, systemtype (IBD = ESR, IBC = Directory), counts, ... */
    public Map<String, String> metadata() {
        return metadata;
    }

    public List<XiObject> objects() {
        return objects;
    }

    /** Payload of large containers is written in blocks of this size, separated by 8-byte markers. */
    static final int BLOCK = 1048576;

    /**
     * Objects of the {@code modelElement} segments of a {@code .tpt} container. Containers over 1 MiB are written
     * in 1 MiB payload blocks separated by 8-byte markers (catalog offsets count payload bytes, the trailing
     * catalog pointer counts file bytes): tried first as-is, then with the markers removed; each segment must
     * carry its own length as {@code catalog length - 8}, which tells the two layouts apart.
     */
    static List<XiObject> readTpt(byte[] tpt, String name) {
        if (tpt.length < 16) {
            throw new IllegalArgumentException("Not a TPT container (too short): " + name);
        }
        long pointer = ByteBuffer.wrap(tpt, tpt.length - 8, 8).getLong();
        List<XiObject> plain = read(tpt, pointer, name);
        if (plain != null) {
            return plain;
        }
        List<XiObject> deblocked = read(deblock(tpt), payloadOffset(pointer), name);
        if (deblocked == null) {
            throw new IllegalArgumentException("Invalid TPT segment structure: " + name);
        }
        return deblocked;
    }

    /** The objects, or null when the catalog or a segment does not fit the data (the other layout applies). */
    private static List<XiObject> read(byte[] data, long catalogOffset, String name) {
        if (catalogOffset < 0 || catalogOffset >= data.length - 8) {
            return null;
        }
        String catalog = xmlOf(data, (int) catalogOffset, data.length - 8 - (int) catalogOffset);
        record Segment(int offset, int length) {
        }
        List<Segment> segments = new ArrayList<>();
        Matcher m = SEGMENT.matcher(catalog);
        while (m.find()) {
            if (!"modelElement".equals(m.group(1))) {
                continue;
            }
            Segment s = new Segment(Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            if (s.offset() < 0 || s.length() < 8 || s.offset() + s.length() > data.length
                    || ByteBuffer.wrap(data, s.offset(), 8).getLong() != s.length() - 8) {
                return null;
            }
            segments.add(s);
        }
        List<XiObject> out = new ArrayList<>();
        for (Segment s : segments) {
            out.add(XiObjectParser.parse(xmlOf(data, s.offset(), s.length()), name + "@" + s.offset()));
        }
        return out;
    }

    /** The payload without the 8-byte markers (at file offset BLOCK, then after every further BLOCK bytes). */
    private static byte[] deblock(byte[] tpt) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(tpt.length);
        out.write(tpt, 0, Math.min(BLOCK, tpt.length));
        int pos = BLOCK;
        while (pos + 8 < tpt.length) {
            out.write(tpt, pos + 8, Math.min(BLOCK, tpt.length - pos - 8));
            pos += BLOCK + 8;
        }
        return out.toByteArray();
    }

    /** File offset → payload offset: 8 bytes less per marker before it. */
    private static long payloadOffset(long fileOffset) {
        long marker = BLOCK;
        long shift = 0;
        while (marker < fileOffset) {
            shift += 8;
            marker += BLOCK + 8;
        }
        return fileOffset - shift;
    }

    /** XML payload of a segment: skips the 8-byte length (and an optional CRC) up to the first {@code <}. */
    private static String xmlOf(byte[] data, int offset, int length) {
        int start = offset + 8;
        int end = offset + length;
        while (start < end && data[start] != '<') {
            start++;
        }
        return new String(data, start, end - start, StandardCharsets.UTF_8).strip();
    }

    private static Map<String, String> properties(byte[] content) {
        Properties p = new Properties();
        try {
            p.load(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, String> out = new LinkedHashMap<>();
        p.stringPropertyNames().stream().sorted().forEach(k -> out.put(k, p.getProperty(k)));
        return out;
    }
}
