package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * C7 on the real knowledge base: every header and exchange-property name the sources set or read (content-modifier
 * tables, Groovy calls) is backticked in {@code contracts.md}; the failure text is the inventory of missing names.
 */
class ContractNamesTest {

    @Test
    void everyContractNameIsDocumented() throws IOException {
        KnowledgeBase.skipIfRequested("contractNames");
        assumeTrue(Files.isDirectory(KnowledgeBase.sources()), "no sources at " + KnowledgeBase.sources());
        List<String> messages = Checks.contractNames(KnowledgeBase.pipeline(), KnowledgeBase.sources());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
