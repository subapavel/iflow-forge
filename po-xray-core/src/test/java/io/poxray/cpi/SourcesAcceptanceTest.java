package io.poxray.cpi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Acceptance on the scrubbed SAP pipeline packages under {@code knowledge/sources/2026-06-trial}; skipped when the
 * directory is absent.
 */
class SourcesAcceptanceTest {

    private static final String SCRIPT_COLLECTION = "com.sap.integration.cloud.pipeline.v2.script.collection";
    private static final String STEP04 = "com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination";

    private static List<IFlow> trial() throws IOException {
        String property = System.getProperty("iflowforge.sources");
        Path sources = property != null ? Path.of(property) : Path.of("..", "knowledge", "sources");
        Path trial = sources.resolve("2026-06-trial");
        assumeTrue(Files.isDirectory(trial), "knowledge/sources/2026-06-trial absent");
        List<Path> packages;
        try (Stream<Path> list = Files.list(trial)) {
            packages = list.filter(Files::isDirectory).sorted().toList();
        }
        List<IFlow> all = new ArrayList<>();
        for (Path p : packages) {
            all.addAll(IFlowReader.readAll(p));
        }
        return all;
    }

    @Test
    void readsEveryArtifactOfTheTrialLabel() throws Exception {
        List<IFlow> all = trial();

        assertThat(all).hasSize(29);
        assertThat(all).allSatisfy(f -> {
            assertThat(f.id()).isNotBlank();
            assertThat(f.name()).as(f.id()).isNotBlank();
            assertThat(f.version()).as(f.id()).isNotBlank();
        });
        assertThat(all).filteredOn(f -> !f.id().equals(SCRIPT_COLLECTION))
                .hasSize(28)
                .allSatisfy(f -> assertThat(f.steps()).as(f.id()).isNotEmpty());
        assertThat(all).filteredOn(f -> f.id().equals(SCRIPT_COLLECTION)).singleElement().satisfies(f -> {
            assertThat(f.steps()).isEmpty();
            assertThat(f.resources()).anyMatch(r -> r.endsWith(".groovy"));
        });
    }

    @Test
    void knownIflowHasJmsParticipantAndScriptStep() throws Exception {
        IFlow f = trial().stream().filter(i -> i.id().equals(STEP04)).findFirst().orElseThrow();

        assertThat(f.participants()).anyMatch(p -> p.componentType().equals("JMS"));
        assertThat(f.participants()).anyMatch(p -> p.endpoint().equals("InterfaceDeterminationQueue"));
        assertThat(f.participants()).filteredOn(p -> p.componentType().equals("JMS"))
                .anyMatch(p -> p.properties().keySet().stream().anyMatch(k -> k.startsWith("QueueName")));
        assertThat(f.steps()).anyMatch(s -> s.activityType().equals("Script"));
        List<Step> steps = f.steps();
        boolean callFollowedByDepthOne = false;
        for (int i = 0; i + 1 < steps.size(); i++) {
            if (steps.get(i).activityType().equals("ProcessCallElement") && steps.get(i + 1).depth() == 1) {
                callFollowedByDepthOne = true;
            }
        }
        assertThat(callFollowedByDepthOne).as("a ProcessCallElement step followed by a step at depth 1").isTrue();
        assertThat(steps).filteredOn(s -> s.name().equals("Exception Subprocess 1")).isNotEmpty()
                .allMatch(Step::reached);
    }
}
