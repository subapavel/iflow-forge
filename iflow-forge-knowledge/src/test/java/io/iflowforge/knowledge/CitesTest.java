package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The cite-resolution check on the real repository: every {@code [page: <path>#<heading>]} and
 * {@code [pattern: <id>]} cite in a markdown file under {@code skills/} and {@code patterns/} resolves to an existing
 * file and heading (form descriptions with {@code <…>} or {@code …} are skipped by the check).
 */
class CitesTest {

    @Test
    void everyCiteInSkillsAndPatternsResolvesToAnExistingFileAndHeading() throws IOException {
        KnowledgeBase.skipIfRequested("cites");
        List<String> messages = Checks.cites(KnowledgeBase.repoRoot(), KnowledgeBase.skills(),
                KnowledgeBase.patterns());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
