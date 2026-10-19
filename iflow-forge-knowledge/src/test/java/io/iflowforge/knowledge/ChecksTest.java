package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import io.poxray.cpi.SyntheticArtifact;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

/** {@link Checks} and {@link KnowledgeBase} on a synthetic knowledge base and synthetic sources in a temp folder. */
class ChecksTest {

    private static final String ID1 = "com.sap.integration.cloud.pipeline.v2.test.one";
    private static final String ID2 = "com.sap.integration.cloud.pipeline.v2.test.two";

    private static final List<String> FLOW_HEADINGS = List.of("Purpose", "Adapters", "Steps",
            "Headers and properties", "Partner directory", "Scripts and resources", "Externalized parameters",
            "Error handling", "Extension points", "Version notes");
    private static final List<String> SCRIPT_HEADINGS = List.of("Purpose", "Inputs", "Outputs", "Gotchas",
            "Used by");

    @TempDir
    Path dir;

    // --- fixtures -------------------------------------------------------------------------------------------------

    private static Path write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** A minimal pipeline folder: the four loaded pages, naming.md without placeholders, empty page folders. */
    private static Path kb(Path dir) throws IOException {
        write(dir.resolve("SKILL.md"), "# Skill\n\nSynthetic skill page.\n");
        write(dir.resolve("overview.md"), "# Overview\n\nSynthetic overview page.\n");
        write(dir.resolve("contracts.md"), "# Contracts\n\nSynthetic contracts page.\n");
        write(dir.resolve("scenario-contribution.md"), "# Scenario contribution\n\nSynthetic page.\n");
        write(dir.resolve("naming.md"), "# Naming\n\nNo placeholders declared.\n");
        Files.createDirectories(dir.resolve("iflows"));
        Files.createDirectories(dir.resolve("templates"));
        Files.createDirectories(dir.resolve("scripts"));
        return dir;
    }

    /**
     * A label folder with two packages: {@code generic-pipelines/<ID1>} (script/A.groovy, mapping/B.xsl) and
     * {@code templates/<ID2>} (script/C.groovy, and mapping/B.xsl again: one stem from two artifacts), plus a plain
     * file at label level, which is not a package.
     */
    private static Path sources(Path dir) throws IOException {
        Path label = dir.resolve("2026-06-trial");
        SyntheticArtifact.write(label.resolve("generic-pipelines").resolve(ID1), ID1, "script/A.groovy",
                "mapping/B.xsl");
        SyntheticArtifact.write(label.resolve("templates").resolve(ID2), ID2, "script/C.groovy", "mapping/B.xsl");
        write(label.resolve("README.txt"), "Synthetic label folder.\n");
        return label;
    }

    /** A page with an H1 title and the given H2 headings in order, each with a line of text. */
    private static String page(List<String> headings) {
        StringBuilder sb = new StringBuilder("# Synthetic page\n\n");
        for (String h : headings) {
            sb.append("## ").append(h).append("\n\nText.\n\n");
        }
        return sb.toString();
    }

    /** Exactly {@code n} whitespace-separated words. */
    private static String words(int n) {
        return String.join(" ", Collections.nCopies(n, "word")) + "\n";
    }

    // --- C1 coverage ----------------------------------------------------------------------------------------------

    @Test
    void coverageReportsMissingPages() throws IOException {
        Path kb = kb(dir.resolve("pipeline"));
        Path sources = sources(dir.resolve("sources"));
        write(kb.resolve("iflows/" + ID1 + ".md"), page(FLOW_HEADINGS));
        write(kb.resolve("scripts/A.md"), page(SCRIPT_HEADINGS));

        assertThat(Checks.coverage(kb, sources)).containsExactly(
                "missing page: scripts/B.md",
                "missing page: scripts/C.md",
                "missing page: templates/" + ID2 + ".md");
    }

    @Test
    void coveragePassesWhenEveryPageExists() throws IOException {
        Path kb = kb(dir.resolve("pipeline"));
        Path sources = sources(dir.resolve("sources"));
        write(kb.resolve("iflows/" + ID1 + ".md"), page(FLOW_HEADINGS));
        write(kb.resolve("templates/" + ID2 + ".md"), page(FLOW_HEADINGS));
        write(kb.resolve("scripts/A.md"), page(SCRIPT_HEADINGS));
        write(kb.resolve("scripts/B.md"), page(SCRIPT_HEADINGS));
        write(kb.resolve("scripts/C.md"), page(SCRIPT_HEADINGS));

        assertThat(Checks.coverage(kb, sources)).isEmpty();
    }

    // --- C2 headings ----------------------------------------------------------------------------------------------

    @Test
    void headingsOrderMatters() throws IOException {
        Path kb = kb(dir.resolve("pipeline"));
        List<String> swapped = List.of("Purpose", "Adapters", "Headers and properties", "Steps",
                "Partner directory", "Scripts and resources", "Externalized parameters", "Error handling",
                "Extension points", "Version notes");
        write(kb.resolve("iflows/x.md"), page(swapped));
        write(kb.resolve("templates/y.md"), page(FLOW_HEADINGS));
        write(kb.resolve("scripts/s.md"), page(SCRIPT_HEADINGS.subList(0, 4)));
        write(kb.resolve("iflows/z.md"), "# Z\n\n## Purpose\n\n_Stub, filled in Task 4._\n");

        List<String> messages = Checks.headings(kb);

        assertThat(messages).hasSize(2);
        assertThat(messages).filteredOn(m -> m.contains("x.md")).singleElement(STRING)
                .contains("expected \"Steps\" at position 3, found \"Headers and properties\"");
        assertThat(messages).filteredOn(m -> m.contains("s.md")).singleElement(STRING)
                .contains("missing \"Used by\"");
        assertThat(messages).noneMatch(m -> m.contains("y.md"));
        assertThat(messages).noneMatch(m -> m.contains("z.md"));
    }

    @Test
    void headingsAcceptTheExactContract() throws IOException {
        Path kb = kb(dir.resolve("pipeline"));
        StringBuilder flow = new StringBuilder("# Flow page\n\n");
        for (String h : FLOW_HEADINGS) {
            flow.append("## ").append(h).append("\n\nText.\n\n### Detail\n\nMore text.\n\n# Not an H2\n\n");
        }
        StringBuilder script = new StringBuilder("# Script page\n\n");
        for (String h : SCRIPT_HEADINGS) {
            script.append("## ").append(h).append("\n\n### Detail\n\nText.\n\n");
        }
        write(kb.resolve("iflows/a.md"), flow.toString());
        write(kb.resolve("templates/b.md"), flow.toString());
        write(kb.resolve("scripts/c.md"), script.toString());

        assertThat(Checks.headings(kb)).isEmpty();
    }

    // --- C3 placeholders ------------------------------------------------------------------------------------------

    @Test
    void placeholdersReportsMissingAndExtra() throws IOException {
        Path kb = kb(dir.resolve("pipeline"));
        write(kb.resolve("overview.md"), "# Overview\n\nThe sender reads {{tenant.jms.queue.inbound}} and the "
                + "receiver calls {{tenant.endpoint.out}}.\n");
        write(kb.resolve("naming.md"), "# Naming\n\n- `{{tenant.jms.queue.inbound}}`: inbound queue\n"
                + "- `{{tenant.unused}}`: declared, never used\n");

        assertThat(Checks.placeholders(kb)).containsExactlyInAnyOrder(
                "undeclared placeholder: {{tenant.endpoint.out}} (in overview.md)",
                "unused placeholder: {{tenant.unused}}");
    }

    @Test
    void placeholdersPassWhenSetsMatch() throws IOException {
        Path kb = kb(dir.resolve("pipeline"));
        write(kb.resolve("overview.md"), "# Overview\n\nThe sender reads {{tenant.jms.queue.inbound}}.\n");
        write(kb.resolve("iflows/p.md"), "# P\n\nIt calls {{tenant.endpoint.out}} and reads "
                + "{{tenant.jms.queue.inbound}} too.\n");
        write(kb.resolve("naming.md"), "# Naming\n\n- `{{tenant.jms.queue.inbound}}`: inbound queue\n"
                + "- `{{tenant.endpoint.out}}`: receiver endpoint\n");

        assertThat(Checks.placeholders(kb)).isEmpty();
    }

    // --- C4 names -------------------------------------------------------------------------------------------------

    @Test
    void namesReportsUnknownScript() throws IOException {
        Path kb = kb(dir.resolve("pipeline"));
        Path sources = sources(dir.resolve("sources"));
        write(kb.resolve("contracts.md"), "# Contracts\n\n"
                + "The flow `" + ID1 + "` runs `A.groovy` (stored as `script/A.groovy`) and `B.xsl`.\n"
                + "The page also names `Unknown.groovy` and `com.sap.integration.cloud.pipeline.v2.nope`.\n"
                + "Plain.groovy is not backticked and is not a name.\n");

        assertThat(Checks.names(kb, sources)).containsExactlyInAnyOrder(
                "unknown name: Unknown.groovy (in contracts.md)",
                "unknown name: com.sap.integration.cloud.pipeline.v2.nope (in contracts.md)");
    }

    // --- C5 budget ------------------------------------------------------------------------------------------------

    @Test
    void budgetSkipsStubs() throws IOException {
        Path over = kb(dir.resolve("over"));
        write(over.resolve("overview.md"), words(1600));
        write(over.resolve("contracts.md"), "_Stub, filled in Task 5._ " + words(1600));
        write(over.resolve("scripts/big.md"), words(1501));
        List<String> pageMessages = Checks.budget(over);
        assertThat(pageMessages).hasSize(2);
        assertThat(pageMessages).contains("overview.md: 1600 words (limit 1500)");
        assertThat(pageMessages).filteredOn(m -> m.contains("big.md")).singleElement(STRING)
                .contains("1501 words (limit 1500)");
        assertThat(pageMessages).noneMatch(m -> m.contains("contracts.md"));

        Path loaded = kb(dir.resolve("loaded"));
        for (String name : List.of("SKILL.md", "overview.md", "contracts.md", "scenario-contribution.md")) {
            write(loaded.resolve(name), words(1300));
        }
        assertThat(Checks.budget(loaded)).containsExactly("loaded set: 5200 words (limit 5000)");

        Path stubInLoaded = kb(dir.resolve("stub-in-loaded"));
        for (String name : List.of("SKILL.md", "overview.md", "scenario-contribution.md")) {
            write(stubInLoaded.resolve(name), words(1300));
        }
        write(stubInLoaded.resolve("contracts.md"), "_Stub, filled in Task 5._ " + words(1300));
        assertThat(Checks.budget(stubInLoaded)).isEmpty();

        assertThat(Checks.budget(kb(dir.resolve("small")))).isEmpty();
    }

    // --- C6 forbidden values --------------------------------------------------------------------------------------

    @Test
    void forbiddenValueFound() throws IOException {
        Path forbidden = write(dir.resolve("forbidden-values.txt"), "SECRETVALUE\nabc\n\n  \n");

        Path inPage = kb(dir.resolve("in-page"));
        write(inPage.resolve("overview.md"), "# Overview\n\nThe inbound queue is secretvalue today.\n");
        write(inPage.resolve("contracts.md"), "# Contracts\n\nabc abc abc\n");
        assertThat(Checks.forbidden(inPage, forbidden)).singleElement(STRING)
                .contains("overview.md:3")
                .contains("(11 chars)")
                .doesNotContainIgnoringCase("secretvalue");

        Path inOtherFile = kb(dir.resolve("in-other-file"));
        write(inOtherFile.resolve("iflows/raw.xml"), "<a>\n<b>xxSecretValuexx</b>\n</a>\n");
        assertThat(Checks.forbidden(inOtherFile, forbidden)).singleElement(STRING)
                .contains("raw.xml:2")
                .doesNotContainIgnoringCase("secretvalue");

        assertThat(Checks.forbidden(kb(dir.resolve("clean")), forbidden)).isEmpty();
    }

    // --- KnowledgeBase --------------------------------------------------------------------------------------------

    @Test
    void skipIfRequestedAbortsOnlyTheNamedChecks() {
        String previous = System.getProperty("kb.skip");
        try {
            System.setProperty("kb.skip", "coverage,headings");
            assertThatThrownBy(() -> KnowledgeBase.skipIfRequested("coverage"))
                    .isInstanceOf(TestAbortedException.class);
            assertThatThrownBy(() -> KnowledgeBase.skipIfRequested("headings"))
                    .isInstanceOf(TestAbortedException.class);
            assertThatCode(() -> KnowledgeBase.skipIfRequested("names")).doesNotThrowAnyException();

            System.clearProperty("kb.skip");
            assertThatCode(() -> KnowledgeBase.skipIfRequested("coverage")).doesNotThrowAnyException();
        } finally {
            restore("kb.skip", previous);
        }
    }

    @Test
    void forbiddenFileIsPresentOnlyWhenThePropertyIsSet() {
        String previous = System.getProperty("kb.forbidden");
        try {
            Path file = dir.resolve("forbidden-values.txt");
            System.setProperty("kb.forbidden", file.toString());
            assertThat(KnowledgeBase.forbiddenFile()).contains(file);

            System.clearProperty("kb.forbidden");
            assertThat(KnowledgeBase.forbiddenFile()).isEmpty();
        } finally {
            restore("kb.forbidden", previous);
        }
    }

    @Test
    void forbiddenFileResolvesRelativeValuesAgainstTheRepositoryRoot() {
        String previousRoot = System.getProperty("kb.root");
        String previousForbidden = System.getProperty("kb.forbidden");
        try {
            System.setProperty("kb.root", dir.resolve("knowledge").toString());

            System.setProperty("kb.forbidden", "rel/f.txt");
            assertThat(KnowledgeBase.forbiddenFile().map(p -> p.toAbsolutePath().normalize()))
                    .contains(dir.resolve("rel/f.txt").toAbsolutePath().normalize());

            Path absolute = dir.resolve("abs.txt");
            System.setProperty("kb.forbidden", absolute.toString());
            assertThat(KnowledgeBase.forbiddenFile()).contains(absolute);

            System.setProperty("kb.forbidden", "   ");
            assertThat(KnowledgeBase.forbiddenFile()).isEmpty();

            System.clearProperty("kb.forbidden");
            assertThat(KnowledgeBase.forbiddenFile()).isEmpty();
        } finally {
            restore("kb.root", previousRoot);
            restore("kb.forbidden", previousForbidden);
        }
    }

    private static void restore(String key, String previous) {
        if (previous == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previous);
        }
    }
}
