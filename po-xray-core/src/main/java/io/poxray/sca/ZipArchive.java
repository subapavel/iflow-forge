package io.poxray.sca;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/**
 * Fully loaded zip archive (file entries only, directories skipped), entry name to content, in archive order.
 * SCA, DCSA, SDA, EAR and BPEM archives are small enough (MBs) to be held in memory.
 */
public final class ZipArchive {

    private final Map<String, byte[]> entries;

    private ZipArchive(Map<String, byte[]> entries) {
        this.entries = Collections.unmodifiableMap(entries);
    }

    public static ZipArchive open(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return fromZipFile(zip);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read zip " + file, e);
        }
    }

    /** Reads a nested archive. Falls back to a temporary file (central directory) if streaming fails. */
    public static ZipArchive of(byte[] content, String name) {
        try {
            return fromStream(new ByteArrayInputStream(content));
        } catch (ZipException e) {
            return viaTempFile(content, name);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read nested zip " + name, e);
        }
    }

    public Map<String, byte[]> entries() {
        return entries;
    }

    public Optional<byte[]> get(String name) {
        return Optional.ofNullable(entries.get(name));
    }

    public boolean contains(String name) {
        return entries.containsKey(name);
    }

    private static ZipArchive fromStream(InputStream in) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zin = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    out.put(normalize(e.getName()), zin.readAllBytes());
                }
            }
        }
        return new ZipArchive(out);
    }

    private static ZipArchive fromZipFile(ZipFile zip) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (!e.isDirectory()) {
                try (InputStream in = zip.getInputStream(e)) {
                    out.put(normalize(e.getName()), in.readAllBytes());
                }
            }
        }
        return new ZipArchive(out);
    }

    private static ZipArchive viaTempFile(byte[] content, String name) {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("po-xray-", ".zip");
            Files.write(tmp, content);
            return open(tmp);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read nested zip " + name, e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // best effort cleanup
                }
            }
        }
    }

    private static String normalize(String name) {
        return name.replace('\\', '/');
    }
}
