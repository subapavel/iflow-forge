package io.poxray.po;

import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectParser;
import io.poxray.xi.XiObjectSource;
import io.poxray.xi.XiRef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Objects read online ({@code read/ext}) and kept as raw {@code xiObj} XML in a local snapshot directory, so that
 * repeated queries (e.g. by an agent) do not hit the PO system and work offline. Layout:
 * {@code <dir>/<rep|dir>/<typeId>/<hash of SWCV + key>.xml}. {@code refresh} re-reads cached objects.
 */
public final class CachingObjectSource implements XiObjectSource {

    private final PoClient client;
    private final Path directory;
    private final boolean refresh;

    public CachingObjectSource(PoClient client, Path directory, boolean refresh) {
        this.client = client;
        this.directory = directory;
        this.refresh = refresh;
    }

    /** Default snapshot location: {@code ~/.po-xray/cache/<system>}. */
    public static Path defaultDirectory(PoSystem system) {
        return Path.of(System.getProperty("user.home"), ".po-xray", "cache", system.name());
    }

    @Override
    public Optional<XiObject> resolve(XiRef ref) {
        if (ref == null || ref.typeId() == null || (!ref.isDirectory() && ref.swcGuid() == null)) {
            return Optional.empty();
        }
        Path file = fileOf(ref);
        try {
            if (!refresh && Files.isRegularFile(file)) {
                return Optional.of(XiObjectParser.parse(Files.readString(file, StandardCharsets.UTF_8), file.toString()));
            }
            String raw = client.readRaw(ref);
            Files.createDirectories(file.getParent());
            Files.writeString(file, raw, StandardCharsets.UTF_8);
            return Optional.of(XiObjectParser.parse(raw, ref.label()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot access snapshot " + file, e);
        } catch (PoException e) {
            if (e.getMessage() != null && e.getMessage().contains("authentication failed")) {
                throw e;
            }
            return Optional.empty(); // deleted or not readable: reported by the caller as unresolved
        }
    }

    Path fileOf(XiRef ref) {
        Area area = Area.of(ref);
        String scope = area == Area.DIRECTORY ? "" : ref.swcGuid();
        return directory.resolve(area.path()).resolve(ref.typeId()).resolve(hash(scope + "|" + ref.keyString()) + ".xml");
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
