package io.poxray.migration;

import io.poxray.PoXray;
import io.poxray.json.Json;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;

/** Shared parts of the migration helper reports: generator, JSON input, Markdown cells and issue sections. */
final class Reports {

    static final String SCHEMA_VERSION = "0.1";

    private Reports() {
    }

    static ScaInventory.Generator generator() {
        return new ScaInventory.Generator(PoXray.NAME, PoXray.VERSION,
                Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
    }

    /** Reads {@code file} as {@code type}; a missing file or a parse error is an {@link IOException} naming it. */
    static <T> T readJson(Path file, Class<T> type) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        T value;
        try {
            value = Json.mapper().readValue(bytes, type);
        } catch (JacksonException e) {
            throw new IOException("Cannot parse " + file + " as " + type.getSimpleName() + ": "
                    + firstLine(e.getMessage()), e);
        }
        if (value == null) {
            throw new IOException("Cannot parse " + file + " as " + type.getSimpleName() + ": empty document");
        }
        return value;
    }

    static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int nl = text.indexOf('\n');
        return nl < 0 ? text : text.substring(0, nl);
    }

    static <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    static <K, V> Map<K, V> map(Map<K, V> values) {
        return values == null ? Map.of() : values;
    }

    /** A Markdown table cell: {@code |} escaped, line breaks flattened, null as empty. */
    static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    static String code(String text) {
        return text == null || text.isEmpty() ? "" : "`" + text + "`";
    }

    /** {@code ## Issues} with one bullet per issue; nothing when there are none. */
    static void issues(StringBuilder md, List<Issue> issues) {
        if (issues == null || issues.isEmpty()) {
            return;
        }
        md.append("\n## Issues\n\n");
        for (Issue i : issues) {
            md.append("- ").append(i.severity()).append(' ').append(i.code()).append(": ").append(i.message())
                    .append('\n');
        }
    }
}
