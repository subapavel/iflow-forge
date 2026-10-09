package io.poxray.doc;

/** Output formats of the generated documentation. */
public enum DocFormat {

    MARKDOWN(new MarkdownWriter(), ".md"),
    JIRA(new JiraWriter(), ".jira.txt");

    private final DocWriter writer;
    private final String extension;

    DocFormat(DocWriter writer, String extension) {
        this.writer = writer;
        this.extension = extension;
    }

    public DocWriter writer() {
        return writer;
    }

    /** Suggested file extension. */
    public String extension() {
        return extension;
    }
}
