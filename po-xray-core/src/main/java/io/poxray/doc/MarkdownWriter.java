package io.poxray.doc;

import java.util.List;

/** GitHub-flavoured Markdown; Mermaid blocks are kept (rendered by GitHub/GitLab). */
public final class MarkdownWriter implements DocWriter {

    @Override
    public String write(List<Doc.Block> blocks) {
        StringBuilder sb = new StringBuilder();
        for (Doc.Block b : blocks) {
            switch (b) {
                case Doc.Heading h -> sb.append("#".repeat(Math.min(h.level(), 6))).append(' ')
                        .append(escape(h.text(), false)).append("\n\n");
                case Doc.Paragraph p -> sb.append(inline(p.content(), false)).append("\n\n");
                case Doc.Bullets l -> {
                    for (List<Doc.Inline> item : l.items()) {
                        sb.append("- ").append(inline(item, false)).append('\n');
                    }
                    sb.append('\n');
                }
                case Doc.Table t -> table(t, sb);
                case Doc.CodeBlock c -> sb.append("```").append(c.language() == null ? "" : c.language()).append('\n')
                        .append(c.text().stripTrailing()).append("\n```\n\n");
            }
        }
        return sb.toString().stripTrailing() + "\n";
    }

    private static void table(Doc.Table t, StringBuilder sb) {
        sb.append('|');
        for (String h : t.headers()) {
            sb.append(' ').append(escape(h, true)).append(" |");
        }
        sb.append("\n|");
        sb.append(" --- |".repeat(t.headers().size()));
        sb.append('\n');
        for (List<List<Doc.Inline>> row : t.rows()) {
            sb.append('|');
            for (List<Doc.Inline> cell : row) {
                sb.append(' ').append(inline(cell, true).strip()).append(" |");
            }
            sb.append('\n');
        }
        sb.append('\n');
    }

    static String inline(List<Doc.Inline> parts, boolean inTable) {
        StringBuilder sb = new StringBuilder();
        for (Doc.Inline p : parts) {
            switch (p) {
                case Doc.Text t -> sb.append(escape(t.text(), inTable));
                case Doc.Strong s -> sb.append("**").append(escape(s.text(), inTable)).append("**");
                case Doc.Code c -> sb.append(code(c.text(), inTable));
            }
        }
        return sb.toString();
    }

    private static String code(String s, boolean inTable) {
        String text = oneLine(s, inTable);
        if (inTable) {
            text = text.replace("|", "\\|");
        }
        String fence = text.contains("`") ? "``" : "`";
        String pad = text.startsWith("`") || text.endsWith("`") ? " " : "";
        return fence + pad + text + pad + fence;
    }

    static String escape(String s, boolean inTable) {
        StringBuilder sb = new StringBuilder();
        for (char c : oneLine(s, inTable).toCharArray()) {
            if ("\\`*_[]<>|#".indexOf(c) >= 0) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Table cells cannot contain line breaks; elsewhere they are kept. */
    private static String oneLine(String s, boolean inTable) {
        return inTable ? s.replaceAll("\\s*\\R\\s*", " ") : s; // the cell is trimmed as a whole
    }
}
