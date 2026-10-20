package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * C5 on the real repository: the pipeline loaded set and every pipeline page stay within the word budget, and so does
 * every page under {@code skills/}, {@code patterns/} and {@code requests/} (no loaded set there).
 */
class BudgetTest {

    @Test
    void pagesAreWithinBudget() throws IOException {
        KnowledgeBase.skipIfRequested("budget");
        List<String> messages = Checks.budget(KnowledgeBase.pipeline());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }

    @Test
    void skillPatternAndRequestPagesAreWithinThePageBudget() throws IOException {
        KnowledgeBase.skipIfRequested("budget");
        List<String> messages = Checks.budgetPages(KnowledgeBase.skills(), KnowledgeBase.patterns(),
                KnowledgeBase.requests());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
