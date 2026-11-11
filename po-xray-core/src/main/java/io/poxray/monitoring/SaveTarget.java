package io.poxray.monitoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Where saved messages (customer data) may go (spec §7): anywhere outside a git work tree; inside one only below
 * {@code <root>/local/} or {@code <root>/testdata/}, the two directories the repository ignores. The work tree root is
 * the nearest directory, from the target up, with a {@code .git} entry (a directory, or a file in a linked work tree).
 */
public final class SaveTarget {

    private SaveTarget() {
    }

    /**
     * The target as an absolute, normalised path when saving there is allowed; otherwise a {@link MonitoringException}
     * naming it. {@code ..} segments are resolved before the check, and so are symbolic links of the part of the path
     * that exists, so neither leads out of {@code local/} or {@code testdata/}.
     */
    public static Path check(Path dir) {
        Path target = dir.toAbsolutePath().normalize();
        if (!allowed(target) || !allowed(real(target))) {
            throw new MonitoringException("refusing to save customer data inside the repository outside local/ or"
                    + " testdata/: " + target);
        }
        return target;
    }

    private static boolean allowed(Path target) {
        for (Path p = target; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                Path rel = p.relativize(target);
                if (rel.toString().isEmpty()) {
                    return false;
                }
                String first = rel.getName(0).toString();
                return first.equals("local") || first.equals("testdata");
            }
        }
        return true;
    }

    /** The target with the symbolic links of its deepest existing ancestor resolved. */
    private static Path real(Path target) {
        Path existing = target;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return target;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(target)).normalize();
        } catch (IOException e) {
            throw new MonitoringException("Cannot check the save target " + target + ": " + e.getMessage(), e);
        }
    }
}
