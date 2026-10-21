package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The tenant-kind check on the real repository (KB-14, D-044): no markdown page under the pipeline folder or under
 * {@code skills/} names the tenant kind a package export came from ({@code trial tenant}, {@code customer tenant},
 * {@code tenant export}, the standalone word {@code trial}); skipped when the pipeline folder is absent.
 */
class TenantKindTest {

    @Test
    void noPipelineOrSkillPageNamesTheTenantKind() throws IOException {
        KnowledgeBase.skipIfRequested("tenantKind");
        assumeTrue(Files.isDirectory(KnowledgeBase.pipeline()), "no pipeline folder");
        List<String> messages = Checks.tenantKind(KnowledgeBase.repoRoot(), KnowledgeBase.pipeline(),
                KnowledgeBase.skills());
        assertThat(messages).as(String.join("\n", messages)).isEmpty();
    }
}
