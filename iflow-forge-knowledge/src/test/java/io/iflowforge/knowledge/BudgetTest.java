package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C5 on the real knowledge base: the loaded set and every page stay within the word budget. */
class BudgetTest {

    @Test
    void pagesAreWithinBudget() throws IOException {
        KnowledgeBase.skipIfRequested("budget");
        List<String> messages = Checks.budget(KnowledgeBase.pipeline());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
