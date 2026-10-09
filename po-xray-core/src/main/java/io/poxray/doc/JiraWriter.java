package io.poxray.doc;

import java.util.List;
import java.util.Set;

/**
 * Jira / Confluence wiki markup. Mermaid diagrams are skipped (not supported without a plugin); the flow table
 * carries the same information.
 */
public final class JiraWriter implements DocWriter {

    private static final Set<String> CODE_LANGUAGES = Set.of("java", "xml", "json", "sql", "javascript");
    private static final String SPECIAL = "\\*_+-^~?{}[]|!#";

    @Override
    public String write(List<Doc.Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Doc.Block b : blocks) {
            switch (b) {
                case Doc.Heading h -> sb.append('h').append(Math.min(h.level(), 6)).append(". ")
                        .append(escape(oneLine(h.text()))).append("\n\n");
                case Doc.Paragraph p -> sb.append(inline(p.content(), false)).append("\n\n");
                case Doc.Bullets l -> {
                    for (List<Doc.Inline> item : l.items()) {
                        sb.append("* ").append(inline(item, false)).append('\n');
                    }
                    sb.append('\n');
                }
                case Doc.Table t -> table(t, sb);
                case Doc.CodeBlock c -> {
                    if (!"mermaid".equals(c.language())) {
                        String open = c.language() != null && CODE_LANGUAGES.contains(c.language())
                                ? "{code:" + c.language() + "}" : "{noformat}";
                        String close = open.startsWith("{code") ? "{code}" : "{noformat}";
                        sb.append(open).append('\n').append(c.text().stripTrailing()).append('\n').append(close)
                                .append("\n\n");
                    }
                }
            }
        }
        return sb.toString().stripTrailing() + "\n";
    }

    private static void table(Doc.Table t, StringBuilder sb) {
        sb.append("||");
        for (String h : t.headers()) {
            sb.append(escape(oneLine(h))).append("||");
        }
        sb.append('\n');
        for (List<List<Doc.Inline>> row : t.rows()) {
            sb.append('|');
            for (List<Doc.Inline> cell : row) {
                String s = inline(cell, true).strip();
                sb.append(s.isBlank() ? " " : s).append('|'); // empty cells would collapse
            }
            sb.append('\n');
        }
        sb.append('\n');
    }

    static String inline(List<Doc.Inline> parts, boolean inTable) {
        StringBuilder sb = new StringBuilder();
        for (Doc.Inline p : parts) {
            String text = switch (p) {
                case Doc.Text t -> t.text();
                case Doc.Strong s -> s.text();
                case Doc.Code c -> c.text();
            };
            // lines are joined per part without trimming: spaces between parts are significant
            String escaped = escape(inTable ? text.replaceAll("\\s*\\R\\s*", " ") : lineBreaks(text));
            switch (p) {
                case Doc.Text t -> sb.append(escaped);
                case Doc.Strong s -> sb.append('*').append(escaped).append('*');
                case Doc.Code c -> sb.append("{{").append(escaped).append("}}");
            }
        }
        return sb.toString();
    }

    /** Backslash-escapes wiki markup characters. */
    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (SPECIAL.indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String oneLine(String s) {
        return s.replaceAll("\\s*\\R\\s*", " ").strip();
    }

    /** Line breaks inside inline text (multi-line expressions) are joined with spaces. */
    private static String lineBreaks(String s) {
        return s.replaceAll("\\R", " ");
    }
}
