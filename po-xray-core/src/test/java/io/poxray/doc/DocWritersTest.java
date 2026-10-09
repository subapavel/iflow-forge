package io.poxray.doc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DocWritersTest {

    private static final List<Doc.Block> DOC = List.of(
            new Doc.Heading(1, "Proces digisign"),
            new Doc.Paragraph(List.of(new Doc.Text("Text with * and _ and [x]"))),
            new Doc.Table(List.of("Cíl", "Zdroj"), List.of(
                    List.of(Doc.code("a/b"), Doc.code("if(x|y) then {a} else b")),
                    List.of(Doc.text(""), Doc.line(new Doc.Strong("Yes"), new Doc.Text(" multi\nline"))))),
            new Doc.Bullets(List.of(Doc.text("item"))),
            new Doc.CodeBlock("mermaid", "flowchart LR\n  n0 --> n1"));

    @Test
    void markdownEscapesAndKeepsMermaid() {
        String md = new MarkdownWriter().write(DOC);

        assertThat(md).startsWith("# Proces digisign\n");
        assertThat(md).contains("Text with \\* and \\_ and \\[x\\]");
        assertThat(md).contains("| Cíl | Zdroj |\n| --- | --- |");
        assertThat(md).contains("| `a/b` | `if(x\\|y) then {a} else b` |"); // pipe escaped inside table code
        assertThat(md).contains("**Yes** multi line"); // no line breaks in cells
        assertThat(md).contains("```mermaid\nflowchart LR");
        assertThat(md).contains("- item");
    }

    @Test
    void jiraEscapesSkipsMermaidAndFillsEmptyCells() {
        String jira = new JiraWriter().write(DOC);

        assertThat(jira).startsWith("h1. Proces digisign\n");
        assertThat(jira).contains("Text with \\* and \\_ and \\[x\\]");
        assertThat(jira).contains("||Cíl||Zdroj||");
        assertThat(jira).contains("|{{a/b}}|{{if(x\\|y) then \\{a\\} else b}}|");
        assertThat(jira).contains("| |*Yes* multi line|"); // empty cell kept as a space
        assertThat(jira).doesNotContain("mermaid").doesNotContain("flowchart");
        assertThat(jira).contains("* item");
    }

    @Test
    void tableRowsAlwaysHaveAllCells() {
        Doc.TableBuilder t = new Doc.TableBuilder("a", "b");
        t.row(Doc.text("1"), Doc.text("2"));
        assertThat(t.build().rows()).singleElement().satisfies(r -> assertThat(r).hasSize(2));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> t.row(Doc.text("only one")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
