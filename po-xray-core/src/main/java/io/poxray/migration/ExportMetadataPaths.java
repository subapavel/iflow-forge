package io.poxray.migration;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Local path checks of the export provenance sidecar: lexical locality (default file system, no UNC or device
 * namespace) and a component walk that reads every existing component without following links (as
 * {@code DossierEvidence.read}) and stops at the first missing one. Never reads file bytes, never follows a link.
 */
final class ExportMetadataPaths {

    /** What the walk found at a path. */
    enum State {
        /** Every component exists, none is a link, the target is a regular file. */
        REGULAR,
        /** Every component exists, none is a link, the target is not a regular file (directory, root). */
        NOT_REGULAR,
        /** No entry at the first missing component, no link before it. */
        MISSING,
        /** A parent component is a regular file, so the path cannot exist. */
        BLOCKED,
        /** A component is a symbolic link or another reparse entry (junction, dangling link). */
        LINKED,
        /** A component could not be inspected (I/O error, access denied). */
        FAILED
    }

    record Walk(Path path, State state, String cause) {
    }

    private ExportMetadataPaths() {
    }

    /** {@code path} absolute and normalized; non-default file systems and UNC/device roots are an IOException. */
    static Path local(Path path, String what) throws IOException {
        if (!path.getFileSystem().equals(FileSystems.getDefault())) {
            throw new IOException(what + " must be on the default file system: " + path);
        }
        Path abs;
        try {
            abs = path.toAbsolutePath().normalize();
        } catch (IOError | SecurityException e) {
            throw new IOException(what + " cannot be made absolute: " + path, e);
        }
        Path root = abs.getRoot();
        if (root == null || root.toString().replace('\\', '/').startsWith("//")) {
            throw new IOException(what + " must be a local path, not a UNC or device namespace path: " + abs);
        }
        return abs;
    }

    /** {@code value} resolved against the directory of {@code descriptor} (absolute values stay as they are). */
    static Path resolve(Path descriptor, String value) throws IOException {
        try {
            return local(descriptor.getParent().resolve(Path.of(value)), "Supplied artifact path");
        } catch (InvalidPathException e) {
            throw new IOException("Supplied artifact path is malformed", e);
        }
    }

    /** The NOFOLLOW component walk of the absolute path {@code abs}. */
    static Walk walk(Path abs) {
        Path part = abs.getRoot();
        int count = abs.getNameCount();
        for (int i = 0; i < count; i++) {
            part = part.resolve(abs.getName(i));
            BasicFileAttributes attrs;
            try {
                attrs = Files.readAttributes(part, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            } catch (NoSuchFileException e) {
                return new Walk(abs, State.MISSING, "does not exist");
            } catch (IOException | SecurityException e) {
                return new Walk(abs, State.FAILED, "cannot be inspected (" + e.getClass().getSimpleName() + ")");
            }
            if (attrs.isSymbolicLink() || attrs.isOther()) {
                return new Walk(abs, State.LINKED, "is reached through a symbolic link or reparse entry (" + part + ")");
            }
            if (i < count - 1 && !attrs.isDirectory()) {
                return new Walk(abs, State.BLOCKED, "has a parent component that is not a directory (" + part + ")");
            }
            if (i == count - 1) {
                return attrs.isRegularFile() ? new Walk(abs, State.REGULAR, null)
                        : new Walk(abs, State.NOT_REGULAR, "is not a regular file");
            }
        }
        return new Walk(abs, State.NOT_REGULAR, "is a file system root, not a regular file");
    }

    /** Fails unless {@code abs} is a link-free regular file. */
    static void requireRegular(Path abs, String what) throws IOException {
        Walk walk = walk(abs);
        if (walk.state() != State.REGULAR) {
            throw new IOException(what + " " + abs + " " + walk.cause());
        }
    }
}
