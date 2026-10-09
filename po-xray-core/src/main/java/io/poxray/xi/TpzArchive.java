package io.poxray.xi;

import io.poxray.sca.ZipArchive;
import java.io.ByteArrayInputStream;
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

    /** Objects of the {@code modelElement} segments of a {@code .tpt} container. */
    static List<XiObject> readTpt(byte[] tpt, String name) {
        if (tpt.length < 16) {
            throw new IllegalArgumentException("Not a TPT container (too short): " + name);
        }
        long catalogOffset = ByteBuffer.wrap(tpt, tpt.length - 8, 8).getLong();
        if (catalogOffset < 0 || catalogOffset >= tpt.length - 8) {
            throw new IllegalArgumentException("Invalid TPT catalog offset " + catalogOffset + ": " + name);
        }
        String catalog = xmlOf(tpt, (int) catalogOffset, tpt.length - 8 - (int) catalogOffset);
        List<XiObject> out = new ArrayList<>();
        Matcher m = SEGMENT.matcher(catalog);
        while (m.find()) {
            if (!"modelElement".equals(m.group(1))) {
                continue;
            }
            int offset = Integer.parseInt(m.group(2));
            int length = Integer.parseInt(m.group(3));
            if (offset < 0 || length < 8 || offset + length > tpt.length) {
                throw new IllegalArgumentException("Segment outside the TPT container at " + offset + ": " + name);
            }
            out.add(XiObjectParser.parse(xmlOf(tpt, offset, length), name + "@" + offset));
        }
        return out;
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
