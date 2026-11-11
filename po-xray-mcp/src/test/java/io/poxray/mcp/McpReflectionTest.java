package io.poxray.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Native image: every result record of the MCP tools is registered for reflection in {@link McpReflection}. */
class McpReflectionTest {

    /**
     * Records of the package that are never serialised: {@code McpBudget.FittedSearch} is an internal carrier whose
     * fields MessageTools copies into {@code SearchView}.
     */
    private static final Set<String> INTERNAL = Set.of(McpBudget.FittedSearch.class.getName());

    @Test
    void everyResultRecordIsRegistered() throws IOException, URISyntaxException {
        RegisterForReflection reg = McpReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());
        Path classes = Path.of(MessageTools.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Set<String> records;
        try (Stream<Path> files = Files.list(classes.resolve("io/poxray/mcp"))) {
            records = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".class"))
                    .map(n -> "io.poxray.mcp." + n.substring(0, n.length() - ".class".length()))
                    .map(McpReflectionTest::load)
                    .filter(Class::isRecord)
                    .map(Class::getName)
                    .filter(n -> !INTERNAL.contains(n))
                    .collect(Collectors.toSet());
        }

        assertThat(records).contains(MessageTools.SearchView.class.getName(), MessageTools.PayloadView.class.getName(),
                MessageTools.ValuesView.class.getName(), PoXrayTools.ResultFile.class.getName(),
                Views.IcoOverview.class.getName(), Inventories.Components.class.getName());
        assertThat(records).allSatisfy(c -> assertThat(registered).as("registered in McpReflection").contains(c));
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, McpReflectionTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("listed but not loadable: " + name, e);
        }
    }
}
