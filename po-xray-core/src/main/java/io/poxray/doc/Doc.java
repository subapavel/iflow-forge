package io.poxray.doc;

import java.util.ArrayList;
import java.util.List;

/**
 * Format-neutral document model: what to say ({@link DocumentBuilder}), not how to write it
 * ({@link MarkdownWriter}, {@link JiraWriter}). Deliberately minimal.
 */
public final class Doc {

    private Doc() {
    }

    /** Block-level element. */
    public sealed interface Block permits Heading, Paragraph, Table, Bullets, CodeBlock {
    }

    /** Inline element of a paragraph, list item or table cell. */
    public sealed interface Inline permits Text, Code, Strong {
    }

    public record Heading(int level, String text) implements Block {
    }

    public record Paragraph(List<Inline> content) implements Block {
    }

    /** Table; every row has as many cells as there are headers. */
    public record Table(List<String> headers, List<List<List<Inline>>> rows) implements Block {
    }

    public record Bullets(List<List<Inline>> items) implements Block {
    }

    /** Preformatted block; {@code language} e.g. {@code mermaid} (writers may skip unsupported languages). */
    public record CodeBlock(String language, String text) implements Block {
    }

    public record Text(String text) implements Inline {
    }

    /** Monospace (identifiers, expressions, paths). */
    public record Code(String text) implements Inline {
    }

    public record Strong(String text) implements Inline {
    }

    // ------------------------------------------------------------------------------------------ helpers

    public static List<Inline> line(Inline... parts) {
        List<Inline> out = new ArrayList<>();
        for (Inline p : parts) {
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public static List<Inline> text(String s) {
        return s == null || s.isEmpty() ? List.of() : List.of(new Text(s));
    }

    public static List<Inline> code(String s) {
        return s == null || s.isEmpty() ? List.of() : List.of(new Code(s));
    }

    /** Collects table rows. */
    public static final class TableBuilder {
        private final List<String> headers;
        private final List<List<List<Inline>>> rows = new ArrayList<>();

        public TableBuilder(String... headers) {
            this.headers = List.of(headers);
        }

        @SafeVarargs
        public final TableBuilder row(List<Inline>... cells) {
            if (cells.length != headers.size()) {
                throw new IllegalArgumentException("Expected " + headers.size() + " cells, got " + cells.length);
            }
            List<List<Inline>> row = new ArrayList<>(cells.length);
            for (List<Inline> cell : cells) {
                row.add(cell);
            }
            rows.add(List.copyOf(row));
            return this;
        }

        public boolean isEmpty() {
            return rows.isEmpty();
        }

        public Table build() {
            return new Table(headers, List.copyOf(rows));
        }
    }
}
