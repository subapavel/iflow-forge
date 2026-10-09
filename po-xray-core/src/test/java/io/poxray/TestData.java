package io.poxray;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Access to the confidential client test data in {@code testdata/} (gitignored, never committed).
 * Tests calling {@link #require(String)} are skipped (not failed) when the file is not available, e.g. on CI.
 * Location: system property {@code poxray.testdata} (set by surefire, overridable with {@code -Dpoxray.testdata=...});
 * only when it is not set (e.g. run from an IDE) {@code ../testdata} or {@code testdata} are tried.
 */
public final class TestData {

    private TestData() {
    }

    public static Path require(String fileName) {
        Path file = dir() == null ? null : dir().resolve(fileName);
        assumeTrue(file != null && Files.isRegularFile(file), "test data not available: testdata/" + fileName);
        return file;
    }

    static Path dir() {
        String configured = System.getProperty("poxray.testdata");
        Stream<String> candidates = configured != null && !configured.isBlank()
                ? Stream.of(configured)
                : Stream.of("../testdata", "testdata");
        return candidates
                .map(Path::of)
                .filter(Files::isDirectory)
                .findFirst()
                .orElse(null);
    }
}
