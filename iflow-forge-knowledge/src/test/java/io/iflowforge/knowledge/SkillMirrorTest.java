package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.STRING;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link Checks#skillMirror}: every pointer file repeats the canonical skill's frontmatter line for line and names
 * the canonical path; the canonical body carries no agent-specific syntax; a missing canonical or pointer fails.
 * Synthetic repositories in a temp folder, plus the real repository's two skills.
 */
class SkillMirrorTest {

    private static final String CANONICAL = "skills/demo/SKILL.md";
    private static final String CLAUDE_POINTER = ".claude/skills/demo/SKILL.md";
    private static final String AGENTS_POINTER = ".agents/skills/demo/SKILL.md";

    private static final String FRONTMATTER = """
            ---
            name: demo
            description: Synthetic skill for the mirror check. Load when testing the mirror.
            ---
            """;

    /** Lines 5..9 of the canonical file; line 5 is blank, line 6 the title. */
    private static final String CANONICAL_BODY = """

            # demo

            Read knowledge/pipeline/SKILL.md at phase 2, and/or see the index; the `Read` step is a word here.
            Mail to someone@example.org stays prose, and a path like /tmp/x mid-line is fine.
            """;

    private static final String POINTER_BODY = """

            The content lives in `skills/demo/`. Read `skills/demo/SKILL.md` first.
            """;

    @TempDir
    Path repo;

    // --- fixtures -------------------------------------------------------------------------------------------------

    private void write(String relative, String content) throws IOException {
        Path file = repo.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /** A correct canonical and its two pointers. */
    private void correctPair() throws IOException {
        write(CANONICAL, FRONTMATTER + CANONICAL_BODY);
        write(CLAUDE_POINTER, FRONTMATTER + POINTER_BODY);
        write(AGENTS_POINTER, FRONTMATTER + POINTER_BODY);
    }

    private List<String> check() throws IOException {
        return Checks.skillMirror(repo, Map.of(CANONICAL, List.of(CLAUDE_POINTER, AGENTS_POINTER)));
    }

    /** The canonical with {@code line} appended as file line 10 (after the nine lines of the correct canonical). */
    private void canonicalWithLine(String line) throws IOException {
        write(CANONICAL, FRONTMATTER + CANONICAL_BODY + line + "\n");
    }

    // --- A.1 frontmatter ------------------------------------------------------------------------------------------

    @Test
    void correctPairsProduceNoMessages() throws IOException {
        correctPair();
        String other = "skills/other/SKILL.md";
        String otherFrontmatter = "---\nname: other\ndescription: A second synthetic skill.\n---\n";
        write(other, otherFrontmatter + "\n# other\n\nPlain prose only.\n");
        write(".claude/skills/other/SKILL.md", otherFrontmatter + "\nSee `" + other + "`.\n");
        Map<String, List<String>> skills = new LinkedHashMap<>();
        skills.put(CANONICAL, List.of(CLAUDE_POINTER, AGENTS_POINTER));
        skills.put(other, List.of(".claude/skills/other/SKILL.md"));

        assertThat(Checks.skillMirror(repo, skills)).isEmpty();
    }

    @Test
    void pointerFrontmatterThatDiffersFailsNamingFileAndLine() throws IOException {
        correctPair();
        write(AGENTS_POINTER, FRONTMATTER.replace("Load when testing the mirror.", "Load sometimes.") + POINTER_BODY);

        assertThat(check()).singleElement(STRING).startsWith(AGENTS_POINTER + ":3");
    }

    @Test
    void pointerFrontmatterWithAnExtraLineFailsAtThatLine() throws IOException {
        correctPair();
        write(CLAUDE_POINTER, FRONTMATTER.replace("mirror.\n---", "mirror.\nlicense: x\n---")
                + POINTER_BODY);

        assertThat(check()).singleElement(STRING).startsWith(CLAUDE_POINTER + ":4");
    }

    @Test
    void pointerWithoutFrontmatterFails() throws IOException {
        correctPair();
        write(CLAUDE_POINTER, "The content lives in `skills/demo/`. Read `skills/demo/SKILL.md` first.\n");

        assertThat(check()).isNotEmpty().allSatisfy(m -> assertThat(m).startsWith(CLAUDE_POINTER));
    }

    // --- A.2 pointer names the canonical path ---------------------------------------------------------------------

    @Test
    void pointerBodyWithoutTheCanonicalPathFails() throws IOException {
        correctPair();
        write(CLAUDE_POINTER, FRONTMATTER + "\nThe content lives in the demo skill folder.\n");

        assertThat(check()).singleElement(STRING).startsWith(CLAUDE_POINTER);
    }

    // --- A.3 no agent-specific syntax in the canonical body -------------------------------------------------------

    @Test
    void canonicalBodyWithAnAtFileIncludeFailsNamingFileAndLine() throws IOException {
        correctPair();
        canonicalWithLine("Load @knowledge/pipeline/SKILL.md first.");

        assertThat(check()).singleElement(STRING)
                .startsWith(CANONICAL + ":10")
                .doesNotContain("@knowledge");
    }

    @Test
    void canonicalBodyWithASlashCommandLineFailsNamingFileAndLine() throws IOException {
        correctPair();
        canonicalWithLine("/sap-is-pipeline");

        assertThat(check()).singleElement(STRING)
                .startsWith(CANONICAL + ":10")
                .doesNotContain("/sap-is-pipeline");
    }

    @Test
    void canonicalBodyWithABacktickedToolInvocationFailsForEveryNamedTool() throws IOException {
        for (String tool : List.of("Read", "Edit", "Write", "Bash", "Grep", "Glob", "Agent", "Task", "WebFetch",
                "WebSearch")) {
            correctPair();
            canonicalWithLine("Then run `" + tool + "(knowledge/pipeline/SKILL.md)` once.");

            assertThat(check()).as(tool).singleElement(STRING)
                    .startsWith(CANONICAL + ":10")
                    .doesNotContain(tool + "(");
        }
    }

    // --- A.4 missing files ----------------------------------------------------------------------------------------

    @Test
    void pointerDirectoryWithoutSkillFileFails() throws IOException {
        correctPair();
        Files.delete(repo.resolve(AGENTS_POINTER));
        write(".agents/skills/demo/README.md", FRONTMATTER + POINTER_BODY);

        assertThat(check()).singleElement(STRING).startsWith(AGENTS_POINTER);
    }

    @Test
    void canonicalWithAMissingPointerFails() throws IOException {
        write(CANONICAL, FRONTMATTER + CANONICAL_BODY);
        write(CLAUDE_POINTER, FRONTMATTER + POINTER_BODY);

        assertThat(check()).singleElement(STRING).startsWith(AGENTS_POINTER);
    }

    @Test
    void missingCanonicalFails() throws IOException {
        correctPair();
        Files.delete(repo.resolve(CANONICAL));

        assertThat(check()).isNotEmpty().anyMatch(m -> m.startsWith(CANONICAL));
    }

    // --- real repository ------------------------------------------------------------------------------------------

    @Test
    void repositorySkillsMirror() throws IOException {
        KnowledgeBase.skipIfRequested("mirror");
        Map<String, List<String>> skills = new LinkedHashMap<>();
        skills.put("knowledge/pipeline/SKILL.md",
                List.of(".claude/skills/sap-is-pipeline/SKILL.md", ".agents/skills/sap-is-pipeline/SKILL.md"));
        skills.put("skills/sap-po-migration/SKILL.md",
                List.of(".claude/skills/sap-po-migration/SKILL.md", ".agents/skills/sap-po-migration/SKILL.md"));

        List<String> messages = Checks.skillMirror(KnowledgeBase.repoRoot(), skills);
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
