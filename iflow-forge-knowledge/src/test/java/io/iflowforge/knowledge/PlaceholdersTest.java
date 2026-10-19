package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C3 on the real knowledge base: the tenant placeholders used equal the ones declared in naming.md. */
class PlaceholdersTest {

    @Test
    void everyPlaceholderIsDeclared() throws IOException {
        KnowledgeBase.skipIfRequested("placeholders");
        List<String> messages = Checks.placeholders(KnowledgeBase.pipeline());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
