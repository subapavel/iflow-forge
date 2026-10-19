package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C2 on the real knowledge base: iFlow, template and script pages have the contract H2 headings in order. */
class HeadingsTest {

    @Test
    void pagesFollowTheContract() throws IOException {
        KnowledgeBase.skipIfRequested("headings");
        List<String> messages = Checks.headings(KnowledgeBase.pipeline());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
