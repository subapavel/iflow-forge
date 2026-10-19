package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C4 on the real knowledge base: every backticked iFlow id and script name exists in the sources. */
class NamesTest {

    @Test
    void everyNameExistsInTheSources() throws IOException {
        KnowledgeBase.skipIfRequested("names");
        assumeTrue(Files.isDirectory(KnowledgeBase.sources()), "no sources at " + KnowledgeBase.sources());
        List<String> messages = Checks.names(KnowledgeBase.pipeline(), KnowledgeBase.sources());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
