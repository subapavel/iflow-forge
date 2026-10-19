package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C1 on the real knowledge base: every artifact of the sources has its iFlow, template and script pages. */
class CoverageTest {

    @Test
    void everyArtifactHasItsPages() throws IOException {
        KnowledgeBase.skipIfRequested("coverage");
        assumeTrue(Files.isDirectory(KnowledgeBase.sources()), "no sources at " + KnowledgeBase.sources());
        List<String> messages = Checks.coverage(KnowledgeBase.pipeline(), KnowledgeBase.sources());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
