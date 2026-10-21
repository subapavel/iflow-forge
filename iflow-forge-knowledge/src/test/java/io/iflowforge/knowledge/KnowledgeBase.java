package io.iflowforge.knowledge;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The real knowledge base as the test run sees it, from system properties (set by surefire, see the module pom):
 * {@code kb.root} (default {@code ../knowledge}), {@code kb.sources} (default {@code 2026-06}), {@code kb.skip}
 * (comma-separated check names) and {@code kb.forbidden} (a local file). A blank property counts as unset.
 */
final class KnowledgeBase {

    static final String DEFAULT_ROOT = "../knowledge";
    static final String DEFAULT_SOURCES = "2026-06";

    private KnowledgeBase() {
    }

    /** {@code <kb.root>/pipeline}. */
    static Path pipeline() {
        return root().resolve("pipeline");
    }

    /** {@code <kb.root>/sources/<kb.sources>}: one label folder holding package folders. */
    static Path sources() {
        return root().resolve("sources").resolve(property("kb.sources").orElse(DEFAULT_SOURCES));
    }

    /** {@code <repo>/skills}: the canonical agent-neutral skills; a missing folder holds no files. */
    static Path skills() {
        return repoRoot().resolve("skills");
    }

    /** {@code <repo>/patterns}: the scenario-pattern pages; a missing folder holds no files. */
    static Path patterns() {
        return repoRoot().resolve("patterns");
    }

    /** {@code <repo>/requests}: the filed requests; a missing folder holds no files. */
    static Path requests() {
        return repoRoot().resolve("requests");
    }

    /** Aborts the calling test (JUnit assumption) when {@code kb.skip} names {@code check}. */
    static void skipIfRequested(String check) {
        List<String> skipped = property("kb.skip")
                .map(v -> Arrays.stream(v.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList())
                .orElse(List.of());
        assumeFalse(skipped.contains(check), () -> "check " + check + " skipped by kb.skip");
    }

    /**
     * The forbidden-values file named by {@code kb.forbidden}; empty when the property is unset or blank. A relative
     * value is resolved against the repository root, the parent of {@code kb.root} (surefire runs in the module
     * folder); an absolute value is used as given.
     */
    static Optional<Path> forbiddenFile() {
        return property("kb.forbidden").map(Path::of).map(p -> p.isAbsolute() ? p : repoRoot().resolve(p));
    }

    /** The repository root: the parent of {@code kb.root}, absolute and normalized. */
    static Path repoRoot() {
        Path parent = root().toAbsolutePath().normalize().getParent();
        return parent == null ? root().toAbsolutePath() : parent;
    }

    /** The {@code *.md} files under {@code dir} (recursive), sorted; none when {@code dir} is not a folder. */
    static List<Path> markdown(Path dir) throws IOException {
        return files(dir).stream().filter(p -> p.getFileName().toString().endsWith(".md")).toList();
    }

    /** All regular files under {@code dir} (recursive), sorted; none when {@code dir} is not a folder. */
    static List<Path> files(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile).sorted().toList();
        }
    }

    /** {@code file} relative to {@code kb}, with {@code /} as separator. */
    static String relative(Path kb, Path file) {
        return kb.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    private static Path root() {
        return Path.of(property("kb.root").orElse(DEFAULT_ROOT));
    }

    private static Optional<String> property(String key) {
        String value = System.getProperty(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }
}
