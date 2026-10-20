package io.poxray.cpi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Public test bridge to the package-private {@link IFlowFixture} of the po-xray-core test-jar, so the knowledge-base
 * checks can build synthetic sources: one unpacked iFlow artifact with a single step and the given resources.
 */
public final class SyntheticArtifact {

    private SyntheticArtifact() {
    }

    /**
     * Writes the artifact {@code id} into the folder {@code dir}; each of {@code resources} is a path relative to
     * {@code src/main/resources/} (for example {@code script/A.groovy}) written with synthetic content.
     */
    public static Path write(Path dir, String id, String... resources) throws IOException {
        IFlowFixture fixture = IFlowFixture.iflow(id).name("Synthetic " + id).version("1.0.0")
                .step("S1", "Run script", "Script").flow(IFlowFixture.START, "S1").flow("S1", IFlowFixture.END);
        for (String resource : resources) {
            fixture.resource(resource, "synthetic content of " + resource + "\n");
        }
        return fixture.write(dir);
    }

    /**
     * Writes the artifact {@code id} into the folder {@code dir} with one step {@code stepName} of
     * {@code activityType} whose further {@code ifl:property} key/value pairs are {@code stepProperties} (raw values,
     * entity-encoded into the {@code .iflw} as the real artifacts have them, for example a {@code headerTable} row
     * XML), and the given {@code resources}: path relative to {@code src/main/resources/} to exact content.
     */
    public static Path withStep(Path dir, String id, String stepName, String activityType,
            Map<String, String> stepProperties, Map<String, String> resources) throws IOException {
        String[] kv = stepProperties.entrySet().stream()
                .flatMap(e -> Stream.of(e.getKey(), e.getValue())).toArray(String[]::new);
        IFlowFixture fixture = IFlowFixture.iflow(id).name("Synthetic " + id).version("1.0.0")
                .step("S1", stepName, activityType, kv).flow(IFlowFixture.START, "S1").flow("S1", IFlowFixture.END);
        resources.forEach(fixture::resource);
        return fixture.write(dir);
    }
}
