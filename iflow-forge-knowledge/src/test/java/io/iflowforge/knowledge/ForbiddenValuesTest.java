package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * C6 on the real knowledge base: no value of the local forbidden-values file ({@code -Dkb.forbidden=<file>}) occurs
 * anywhere under the pipeline folder; skipped when the property is not set or the file does not exist.
 */
class ForbiddenValuesTest {

    @Test
    void noForbiddenValueInTheKnowledgeBase() throws IOException {
        KnowledgeBase.skipIfRequested("forbidden");
        Optional<Path> forbiddenFile = KnowledgeBase.forbiddenFile();
        assumeTrue(forbiddenFile.isPresent(), "kb.forbidden not set");
        assumeTrue(Files.isRegularFile(forbiddenFile.get()), "kb.forbidden names no file");
        List<String> messages = Checks.forbidden(KnowledgeBase.pipeline(), forbiddenFile.get());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
