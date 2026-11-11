package io.poxray.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The monitoring client can call the 21 read operations of the service and nothing else (spec §2, §4). */
class ReadOnlySurfaceTest {

    /** The 21 read operations (F-17 Appendix B). */
    private static final List<String> READ_OPERATIONS = List.of(
            "getMessageList", "getUserDefinedSearchMessages", "getMessagesByIDs", "getMessagesByKeys",
            "getMessagesWithSuccessors", "getPredecessorMessageId", "getLogEntries",
            "getMessageBytesJavaLangStringBoolean", "getMessageBytesJavaLangStringIntBoolean", "getLoggedMessageBytes",
            "getUserDefinedSearchFilters", "getUserDefinedSearchExtractors", "getUserDefinedSearchAttributes",
            "getInterfaces", "getParties", "getServices", "getConnections", "getIntegrationFlows", "getErrorCodes",
            "getStatusDetails", "getAllAvailableStatusDetails");
    private static final List<String> WRITE_OPERATIONS = List.of("resendMessages", "cancelMessages", "failEoioMessage");

    /** The public methods of MonitoringClient declared by the class itself (no synthetic or bridge methods). */
    private static List<Method> publicMethods() {
        return Arrays.stream(MonitoringClient.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic() && !m.isBridge())
                .sorted((a, b) -> a.toGenericString().compareTo(b.toGenericString()))
                .toList();
    }

    /** A plain argument of each parameter type the public methods use. */
    private static Object argument(Class<?> type) {
        if (type == String.class) {
            return "x";
        } else if (type == int.class) {
            return 1;
        } else if (type == long.class) {
            return 1L;
        } else if (type == boolean.class) {
            return false;
        } else if (type == List.class) {
            return List.of();
        } else if (type == Locale.class) {
            return Locale.ENGLISH;
        } else if (type == OffsetDateTime.class) {
            return OffsetDateTime.parse("2026-11-10T12:00:00+01:00");
        } else if (type == MessageFilter.class) {
            return MessageFilter.builder().build();
        }
        throw new AssertionError("no test argument for parameter type " + type.getName());
    }

    @Test
    void exactlyTheTwentyOneReadOperations() throws IllegalAccessException {
        assertThat(READ_OPERATIONS).hasSize(21).doesNotHaveDuplicates();
        List<Method> methods = publicMethods().stream().filter(m -> !m.getName().equals("system")).toList();
        Map<String, String> operationByMethod = new TreeMap<>();
        try (FakeMonitoringPo po = new FakeMonitoringPo()) {
            // an empty Response is a valid answer of every read operation
            po.respondPerOperation(op -> "<m:" + op + "Response xmlns:m=\"" + FakeMonitoringPo.OP + "\">"
                    + "<m:Response/></m:" + op + "Response>");
            MonitoringClient client = new MonitoringClient(po.system);
            for (Method m : methods) {
                Object[] args = Arrays.stream(m.getParameterTypes()).map(ReadOnlySurfaceTest::argument).toArray();
                int before = po.requests().size();
                try {
                    m.invoke(client, args);
                } catch (InvocationTargetException e) {
                    throw new AssertionError(m.toGenericString() + " failed: " + e.getCause(), e.getCause());
                }
                assertThat(po.requests()).as("requests sent by %s", m.toGenericString()).hasSize(before + 1);
                operationByMethod.put(m.toGenericString(),
                        FakeMonitoringPo.operationName(po.lastRequest().body()));
            }
        }
        assertThat(methods).as("one public method per read operation, getMessageBytes overloaded").hasSize(21);
        assertThat(operationByMethod.values()).as("operations by method: %s", operationByMethod)
                .containsExactlyInAnyOrderElementsOf(READ_OPERATIONS);
    }

    @Test
    void noPublicMethodBeyondTheReadSet() {
        assertThat(publicMethods()).extracting(Method::getName).containsExactlyInAnyOrder(
                "system", "getMessageList", "getUserDefinedSearchMessages", "getMessagesByIds", "getMessagesByKeys",
                "getMessagesWithSuccessors", "getPredecessorMessageId", "getLogEntries", "getMessageBytes",
                "getMessageBytes", "getLoggedMessageBytes", "getUserDefinedSearchFilters",
                "getUserDefinedSearchExtractors", "getUserDefinedSearchAttributes", "getInterfaces", "getParties",
                "getServices", "getConnections", "getIntegrationFlows", "getErrorCodes", "getStatusDetails",
                "getAllAvailableStatusDetails");
        assertThat(MonitoringClient.class.getConstructors()).hasSize(1);
    }

    /** The reactor root: the nearest directory, from the working directory up, with a pom.xml and po-xray-core/. */
    private static Path reactorRoot() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("pom.xml")) && Files.isDirectory(p.resolve("po-xray-core"))) {
                return p;
            }
        }
        throw new AssertionError("no reactor root above " + Path.of("").toAbsolutePath());
    }

    @Test
    void noWriteOperationInSources() throws IOException {
        Path root = reactorRoot();
        List<Path> mains = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            modules.filter(m -> m.getFileName().toString().startsWith("po-xray-"))
                    .map(m -> m.resolve("src").resolve("main"))
                    .filter(Files::isDirectory)
                    .forEach(mains::add);
        }
        assertThat(mains).extracting(p -> p.getParent().getParent().getFileName().toString())
                .contains("po-xray-core", "po-xray-cli", "po-xray-mcp");

        List<String> scanned = new ArrayList<>();
        List<String> hits = new ArrayList<>();
        for (Path main : mains) {
            try (Stream<Path> files = Files.walk(main)) {
                for (Path f : files.filter(Files::isRegularFile).toList()) {
                    String content = new String(Files.readAllBytes(f), StandardCharsets.ISO_8859_1);
                    scanned.add(root.relativize(f).toString().replace('\\', '/'));
                    for (String w : WRITE_OPERATIONS) {
                        if (content.contains(w)) {
                            hits.add(root.relativize(f) + ": " + w);
                        }
                    }
                }
            }
        }
        assertThat(scanned).contains("po-xray-core/src/main/java/io/poxray/monitoring/MonitoringClient.java",
                "po-xray-core/src/main/java/io/poxray/monitoring/Soap.java");
        assertThat(hits).as("write operation names in src/main").isEmpty();
    }

    @Test
    void callRefusesAnOperationOutsideTheReadSet() {
        // built from parts so that no source file holds a write operation name
        List<String> refused = List.of("cancel" + "Messages", "resend" + "Messages", "failEoio" + "Message",
                "getSomethingElse");
        try (FakeMonitoringPo po = new FakeMonitoringPo()) {
            po.respondPerOperation(op -> "<m:" + op + "Response xmlns:m=\"" + FakeMonitoringPo.OP + "\">"
                    + "<m:Response/></m:" + op + "Response>");
            MonitoringClient client = new MonitoringClient(po.system);
            for (String op : refused) {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.call(op, ""))
                        .as("call(%s)", op)
                        .isInstanceOf(MonitoringException.class)
                        .hasMessageContaining(op);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> Soap.envelope(op, ""))
                        .as("envelope(%s)", op)
                        .isInstanceOf(MonitoringException.class)
                        .hasMessageContaining(op);
            }
            assertThat(po.requests()).as("requests sent").isEmpty();
        }
        for (String op : READ_OPERATIONS) {
            assertThat(FakeMonitoringPo.operationName(Soap.envelope(op, ""))).as(op).isEqualTo(op);
        }
    }
}
