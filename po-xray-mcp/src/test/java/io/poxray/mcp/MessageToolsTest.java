package io.poxray.mcp;

import static io.poxray.monitoring.FakeMonitoringPo.OP;
import static io.poxray.monitoring.FakeMonitoringPo.child;
import static io.poxray.monitoring.FakeMonitoringPo.operation;
import static io.poxray.monitoring.FakeMonitoringPo.operationName;
import static io.poxray.monitoring.FakeMonitoringPo.searchResponse;
import static io.poxray.monitoring.FakeMonitoringPo.valueResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.json.Json;
import io.poxray.migration.ExportMetadataFixtures;
import io.poxray.monitoring.FakeMonitoringPo;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import tools.jackson.databind.JsonNode;

/**
 * The {@code message_*} MCP tools against the local fake AdapterMessageMonitoring service. Tools are called the way
 * the MCP server calls them: by tool name and argument name (the {@code @ToolArg} name, else the Java parameter
 * name), unset arguments taking their {@code defaultValue}. {@code user.home} points at a temporary directory with
 * {@code .po-xray/systems/QA1.properties} for the fake; the environment is a {@link TestMonitoringEnvironment}.
 */
class MessageToolsTest {

    private static final String KEY = "aaaaaaaa-1111-2222-3333-000000000001\\OUTBOUND\\0\\EO\\0\\";
    private static final List<String> TOOLS = List.of("message_search", "message_uds_search", "message_get",
            "message_successors", "message_predecessor", "message_log", "message_payload", "message_uds_attributes",
            "message_uds_filters", "message_uds_extractors", "message_lookup");
    private static final Set<String> SAVING = Set.of("message_log", "message_payload", "message_uds_attributes");
    private static final Path SPILL = Path.of(System.getProperty("java.io.tmpdir"), "po-xray");

    @TempDir
    Path home;
    private String savedHome;
    private FakeMonitoringPo po;

    @BeforeEach
    void start() throws IOException {
        savedHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        po = new FakeMonitoringPo();
        Path systems = Files.createDirectories(home.resolve(".po-xray").resolve("systems"));
        Files.writeString(systems.resolve("QA1.properties"), "url=" + po.baseUrl() + "/\nuser="
                + FakeMonitoringPo.USER + "\npassword=" + FakeMonitoringPo.PASSWORD + "\n", StandardCharsets.UTF_8);
    }

    @AfterEach
    void stop() {
        po.close();
        System.setProperty("user.home", savedHome);
    }

    // ------------------------------------------------------------------------------------------ calling tools

    /** The tool method with this {@code @Tool} name. */
    private static Method toolMethod(String tool) {
        List<Method> found = Arrays.stream(MessageTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class) && m.getAnnotation(Tool.class).name().equals(tool))
                .toList();
        assertThat(found).as("@Tool(name = \"%s\")", tool).hasSize(1);
        return found.getFirst();
    }

    /** Argument names of a tool method: {@code @ToolArg(name)} when given, else the Java parameter name. */
    private static List<String> argNames(Method m) {
        List<String> fromClassFile = parameterNames(m);
        List<String> names = new ArrayList<>();
        Parameter[] params = m.getParameters();
        for (int i = 0; i < params.length; i++) {
            ToolArg a = params[i].getAnnotation(ToolArg.class);
            names.add(a != null && !a.name().equals(ToolArg.ELEMENT_NAME) ? a.name() : fromClassFile.get(i));
        }
        return names;
    }

    /** Java parameter names from the class file's local variable table (javac -g, Maven's default). */
    private static List<String> parameterNames(Method m) {
        Class<?> c = m.getDeclaringClass();
        byte[] bytes;
        try (InputStream in = c.getResourceAsStream(c.getSimpleName() + ".class")) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ClassModel model = ClassFile.of().parse(bytes);
        String descriptor = MethodType.methodType(m.getReturnType(), m.getParameterTypes()).toMethodDescriptorString();
        MethodModel mm = model.methods().stream()
                .filter(x -> x.methodName().stringValue().equals(m.getName())
                        && x.methodType().stringValue().equals(descriptor))
                .findFirst().orElseThrow();
        List<LocalVariableInfo> locals = mm.code().orElseThrow()
                .findAttribute(Attributes.localVariableTable())
                .orElseThrow(() -> new AssertionError("no local variable table for " + m))
                .localVariables();
        List<String> names = new ArrayList<>();
        int slot = Modifier.isStatic(m.getModifiers()) ? 0 : 1;
        for (Class<?> p : m.getParameterTypes()) {
            int s = slot;
            names.add(locals.stream().filter(v -> v.slot() == s).findFirst().orElseThrow().name().stringValue());
            slot += p == long.class || p == double.class ? 2 : 1;
        }
        return names;
    }

    private static Object convert(Object value, Class<?> type) {
        if (value == null) {
            if (type == int.class) {
                return 0;
            } else if (type == long.class) {
                return 0L;
            } else if (type == boolean.class) {
                return false;
            }
            return null;
        }
        if (type == String.class) {
            return value instanceof List<?> l ? l.stream().map(String::valueOf).collect(Collectors.joining(","))
                    : String.valueOf(value);
        } else if (type == int.class || type == Integer.class) {
            return value instanceof Number n ? n.intValue() : Integer.valueOf(value.toString().strip());
        } else if (type == long.class || type == Long.class) {
            return value instanceof Number n ? n.longValue() : Long.valueOf(value.toString().strip());
        } else if (type == boolean.class || type == Boolean.class) {
            return value instanceof Boolean b ? b : Boolean.valueOf(value.toString().strip());
        } else if (type == List.class) {
            return value instanceof List<?> l ? l : Arrays.stream(value.toString().split(",")).map(String::strip)
                    .filter(s -> !s.isEmpty()).toList();
        }
        throw new AssertionError("no conversion of " + value + " to " + type);
    }

    /** Calls a tool as the MCP server would; a {@link ToolCallException} (or other runtime exception) propagates. */
    private static String call(MessageTools tools, String tool, Map<String, Object> args) {
        Method m = toolMethod(tool);
        List<String> names = argNames(m);
        assertThat(names).as("arguments of %s", tool).containsAll(args.keySet());
        Parameter[] params = m.getParameters();
        Object[] values = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Object v = args.get(names.get(i));
            ToolArg a = params[i].getAnnotation(ToolArg.class);
            if (v == null && a != null && !a.defaultValue().isEmpty()) {
                v = a.defaultValue();
            }
            values[i] = convert(v, params[i].getType());
        }
        m.setAccessible(true);
        try {
            return String.valueOf(m.invoke(tools, values));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) {
                throw r;
            }
            throw new AssertionError(tool + " threw " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static String call(MessageTools tools, String tool, Object... nameValuePairs) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            args.put((String) nameValuePairs[i], nameValuePairs[i + 1]);
        }
        return call(tools, tool, args);
    }

    /** A MessageTools whose {@code @Inject} fields are filled: environments with {@code env}, others built. */
    private static MessageTools tools(Map<String, String> env) {
        MessageTools t = new MessageTools();
        inject(t, new TestMonitoringEnvironment(env));
        return t;
    }

    private static MessageTools tools() {
        return tools(Map.of());
    }

    private static void inject(Object target, TestMonitoringEnvironment env) {
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.isAnnotationPresent(Inject.class)) {
                    continue;
                }
                f.setAccessible(true);
                try {
                    if (f.getType().isAssignableFrom(TestMonitoringEnvironment.class)) {
                        f.set(target, env);
                    } else {
                        var ctor = f.getType().getDeclaredConstructor();
                        ctor.setAccessible(true);
                        Object bean = ctor.newInstance();
                        inject(bean, env);
                        f.set(target, bean);
                    }
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("cannot inject " + f, e);
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ fixtures

    private static String message(String id, String key, String direction, String status, String receiver,
            String errorCode) {
        return "<w:AdapterFrameworkData><w:direction>" + direction + "</w:direction>"
                + (errorCode == null ? "" : "<w:errorCode>" + errorCode + "</w:errorCode>")
                + "<w:messageID>" + id + "</w:messageID>"
                + (key == null ? "" : "<w:messageKey>" + key + "</w:messageKey>")
                + "<w:receiverName>" + receiver + "</w:receiverName><w:senderName>SenderA</w:senderName>"
                + "<w:startTime>2026-11-10T08:15:30.123+01:00</w:startTime><w:status>" + status + "</w:status>"
                + "</w:AdapterFrameworkData>";
    }

    private static final String ONE_MESSAGE = searchResponse("getMessageList", 1, false,
            message("msg-0001", KEY, "OUTBOUND", "systemError", "ReceiverB", null));

    private List<FakeMonitoringPo.Request> sent(String operation) {
        return po.requests().stream().filter(r -> operationName(r.body()).equals(operation)).toList();
    }

    private static JsonNode json(String result) {
        return Json.mapper().readTree(result);
    }

    private static List<String> spillFiles() throws IOException {
        if (!Files.isDirectory(SPILL)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(SPILL)) {
            return s.map(Path::toString).sorted().toList();
        }
    }

    private static final String BOUNDARY = "SAP_test-boundary-0001_END";

    /** An XI-style multipart message: a SOAP header part, then one text/plain part with this text. */
    private static byte[] multipart(String payloadText) {
        String s = "Content-Type: multipart/related; boundary=" + BOUNDARY + "; type=\"text/xml\"\r\n\r\n"
                + "--" + BOUNDARY + "\r\nContent-ID: <soap-1@test>\r\nContent-Type: text/xml; charset=utf-8\r\n\r\n"
                + "<SOAP:Envelope xmlns:SOAP=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP:Header/>"
                + "</SOAP:Envelope>\r\n"
                + "--" + BOUNDARY + "\r\ncontent-id:<payload-1@test>\r\ncontent-type:text/plain; charset=utf-8\r\n\r\n"
                + payloadText + "\r\n--" + BOUNDARY + "--\r\n";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private void servePayload(byte[] bytes) {
        po.respondPerOperation(op -> valueResponse(op, Base64.getEncoder().encodeToString(bytes)));
    }

    /** The longest string value anywhere in a JSON tree. */
    private static String longestString(JsonNode node) {
        if (node.isString()) {
            return node.asString();
        }
        String best = "";
        for (JsonNode child : node) {
            String s = longestString(child);
            if (s.length() > best.length()) {
                best = s;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------ registration

    @Test
    void elevenToolsRegistered() {
        Set<String> names = Arrays.stream(MessageTools.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(Tool.class)).map(m -> m.getAnnotation(Tool.class).name())
                .collect(Collectors.toSet());

        assertThat(names).containsExactlyInAnyOrderElementsOf(TOOLS);
    }

    @Test
    void readOnlyHintMatchesSaveDir() {
        for (String tool : TOOLS) {
            Method m = toolMethod(tool);
            Tool.Annotations a = m.getAnnotation(Tool.class).annotations();
            boolean hasSaveDir = argNames(m).contains("saveDir");
            assertThat(hasSaveDir).as("%s has saveDir", tool).isEqualTo(SAVING.contains(tool));
            assertThat(a.readOnlyHint()).as("%s readOnlyHint", tool).isEqualTo(!hasSaveDir);
            assertThat(a.openWorldHint()).as("%s openWorldHint", tool).isTrue();
            assertThat(argNames(m)).as("%s arguments", tool).contains("system");
        }
    }

    @Test
    void descriptionsStateReadOnlyAndPointToTheLookups() {
        for (String tool : TOOLS) {
            String d = toolMethod(tool).getAnnotation(Tool.class).description();
            if (SAVING.contains(tool)) {
                assertThat(d).as(tool).contains("saveDir");
            } else {
                assertThat(d).as(tool).containsIgnoringCase("read-only");
            }
        }
        assertThat(toolMethod("message_search").getAnnotation(Tool.class).description()).contains("message_lookup");
        assertThat(toolMethod("message_uds_search").getAnnotation(Tool.class).description())
                .contains("message_uds_filters");
    }

    // ------------------------------------------------------------------------------------------ search

    @Test
    void searchOmitsRawByDefault() {
        po.respondSoap(ONE_MESSAGE);

        String out = call(tools(), "message_search", "system", "QA1");

        JsonNode r = json(out);
        assertThat(r.findValues("rawResponse")).isEmpty();
        assertThat(out).doesNotContain("Envelope");
        assertThat(r.path("messages")).hasSize(1);
        assertThat(r.path("messages").get(0).path("messageKey").asString()).isEqualTo(KEY);
        assertThat(r.path("truncated").asBoolean(true)).isFalse();
    }

    @Test
    void includeRawAddsIt() {
        po.respondSoap(ONE_MESSAGE);

        JsonNode r = json(call(tools(), "message_search", "system", "QA1", "includeRaw", true));

        assertThat(r.path("rawResponse").asString()).isEqualTo(FakeMonitoringPo.envelope(ONE_MESSAGE));
    }

    @Test
    void searchLimitDefaultsTo50() {
        po.respondSoap(ONE_MESSAGE);

        call(tools(), "message_search", "system", "QA1");
        call(tools(), "message_search", "system", "QA1", "limit", 7);

        List<FakeMonitoringPo.Request> searches = sent("getMessageList");
        assertThat(searches).hasSize(2);
        assertThat(child(operation(searches.get(0), "getMessageList"), OP, "maxMessages").getTextContent())
                .isEqualTo("50");
        assertThat(child(operation(searches.get(1), "getMessageList"), OP, "maxMessages").getTextContent())
                .isEqualTo("7");
    }

    @Test
    void oversizedSearchIsCutNotSpilled() throws IOException {
        List<String> messages = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            messages.add(message(String.format("msg-%04d", i), KEY.replace("0001", String.format("%04d", i)),
                    "OUTBOUND", "systemError", "ReceiverB", "E".repeat(1_900)));
        }
        po.respondSoap(searchResponse("getMessageList", 50, false, messages.toArray(String[]::new)));
        List<String> spillBefore = spillFiles();

        String out = call(tools(), "message_search", "system", "QA1");

        assertThat(out.length()).isLessThanOrEqualTo(80_000);
        JsonNode r = json(out);
        assertThat(r.path("truncated").asBoolean(false)).isTrue();
        int kept = r.path("messages").size();
        int dropped = r.path("dropped").asInt(-1);
        assertThat(kept).isPositive();
        assertThat(dropped).isPositive();
        assertThat(kept + dropped).isEqualTo(50);
        assertThat(r.path("messages").get(0).path("messageId").asString()).isEqualTo("msg-0001");
        assertThat(r.path("hint").asString("")).isNotBlank();
        assertThat(spillFiles()).isEqualTo(spillBefore);
    }

    @Test
    void systemAndCredentialsComeFromTheMonitoringEnvironment() {
        po.respondSoap(ONE_MESSAGE);

        String out = call(tools(Map.of("POXRAY_SYSTEM", "QA1", "POXRAY_QA1_PASSWORD", "fromBean")),
                "message_search");

        assertThat(json(out).path("messages")).hasSize(1);
        assertThat(po.lastRequest().header("Authorization")).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString((FakeMonitoringPo.USER + ":fromBean").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void monitoringFailureIsAOneLineToolError() {
        po.respond(403, "text/html", "<html>Forbidden</html>");

        assertThatThrownBy(() -> call(tools(), "message_search", "system", "QA1"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("SAP_XI_API_DISPLAY_J2EE")
                .hasMessageNotContaining("\n")
                .hasMessageNotContaining(FakeMonitoringPo.PASSWORD);
    }

    // ------------------------------------------------------------------------------------------ id resolution

    @Test
    void ambiguousIdReturnsCandidates() {
        po.respondSoap(searchResponse("getMessagesByIDs", 2, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB", null),
                message("msg-0001", null, "INBOUND", "holding", "ReceiverC", null)));

        JsonNode r = json(call(tools(), "message_log", "system", "QA1", "idOrKey", "msg-0001"));

        JsonNode candidates = r.path("candidates");
        assertThat(candidates).hasSize(2);
        JsonNode keyed = candidates.get(0);
        assertThat(keyed.path("key").asString()).isEqualTo(KEY);
        assertThat(keyed.path("keyless").asBoolean(false)).isFalse();
        assertThat(keyed.path("direction").asString()).isEqualTo("OUTBOUND");
        assertThat(keyed.path("receiver").asString()).isEqualTo("ReceiverB");
        assertThat(keyed.path("status").asString()).isEqualTo("success");
        JsonNode keyless = candidates.get(1);
        assertThat(keyless.path("key").isMissingNode() || keyless.path("key").isNull()).as("no key").isTrue();
        assertThat(keyless.path("keyless").asBoolean(false)).isTrue();
        assertThat(keyless.path("direction").asString()).isEqualTo("INBOUND");
        assertThat(keyless.path("receiver").asString()).isEqualTo("ReceiverC");
        assertThat(keyless.path("status").asString()).isEqualTo("holding");
        assertThat(r.path("hint").asString("")).isNotBlank();
        assertThat(sent("getLogEntries")).isEmpty();
    }

    @Test
    void unknownIdReturnsTheReason() {
        po.respondSoap(searchResponse("getMessagesByIDs", 0, false));

        String out = call(tools(), "message_payload", "system", "QA1", "idOrKey", "msg-9999");

        assertThat(out).contains("no message with this ID");
        assertThat(sent("getMessageBytesJavaLangStringBoolean")).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ payload

    @Test
    void payloadPagesByChars() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; text.length() < 45_000; i++) {
            text.append((char) ('a' + i % 26));
        }
        String part = text.toString();
        assertThat(part).hasSize(45_000);
        servePayload(multipart(part));
        MessageTools tools = tools();

        List<JsonNode> pages = new ArrayList<>();
        for (int offset : List.of(0, 20_000, 40_000)) {
            pages.add(json(call(tools, "message_payload", "system", "QA1", "idOrKey", KEY, "offset", offset)));
        }

        assertThat(pages).extracting(p -> p.path("totalChars").asInt(-1)).containsExactly(45_000, 45_000, 45_000);
        assertThat(pages).extracting(p -> p.path("truncated").asBoolean()).containsExactly(true, true, false);
        assertThat(longestString(pages.get(0))).isEqualTo(part.substring(0, 20_000));
        assertThat(longestString(pages.get(1))).isEqualTo(part.substring(20_000, 40_000));
        assertThat(longestString(pages.get(2))).isEqualTo(part.substring(40_000));
        assertThat(pages).allSatisfy(p -> assertThat(p.toString().length()).isLessThanOrEqualTo(80_000));
    }

    @Test
    void payloadMaxCharsAndPartAreHonoured() {
        servePayload(multipart("0123456789"));

        JsonNode header = json(call(tools(), "message_payload", "system", "QA1", "idOrKey", KEY, "part", 0));
        JsonNode slice = json(call(tools(), "message_payload", "system", "QA1", "idOrKey", KEY, "offset", 2,
                "maxChars", 3));

        assertThat(header.toString()).contains("SOAP:Header");
        assertThat(slice.path("totalChars").asInt(-1)).isEqualTo(10);
        assertThat(slice.path("truncated").asBoolean(false)).isTrue();
        assertThat(slice.toString()).contains("\"234\"");
    }

    // ------------------------------------------------------------------------------------------ saving

    private static Path repo(Path tmp) throws IOException {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Files.createDirectories(repo.resolve("local"));
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src").resolve("Existing.java"), "class Existing {}");
        return repo;
    }

    @Test
    void refusedSaveDirSendsNoRequest(@TempDir Path tmp) throws IOException {
        Path repo = repo(tmp);
        Map<String, String> before = ExportMetadataFixtures.snapshot(repo);
        servePayload(multipart("payload"));

        assertThatThrownBy(() -> call(tools(), "message_payload", "system", "QA1", "idOrKey", KEY, "saveDir",
                repo.resolve("src").toString()))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("refusing to save customer data");
        assertThatThrownBy(() -> call(tools(), "message_log", "system", "QA1", "idOrKey", "msg-0001", "saveDir",
                repo.toString()))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("refusing to save customer data");

        assertThat(po.requests()).isEmpty();
        assertThat(ExportMetadataFixtures.snapshot(repo)).isEqualTo(before);
    }

    @Test
    void saveDirInsideLocalWritesTheArchive(@TempDir Path tmp) throws IOException {
        Path out = repo(tmp).resolve("local").resolve("messages");
        byte[] bytes = multipart("payload text");
        servePayload(bytes);

        call(tools(), "message_payload", "system", "QA1", "idOrKey", KEY, "saveDir", out.toString());

        List<Path> dirs;
        try (Stream<Path> s = Files.list(out)) {
            dirs = s.toList();
        }
        assertThat(dirs).hasSize(1);
        assertThat(Files.readAllBytes(dirs.getFirst().resolve("payload.bin"))).isEqualTo(bytes);
        assertThat(dirs.getFirst().resolve("response.xml")).isRegularFile();
        assertThat(dirs.getFirst().resolve("model.json")).isRegularFile();
    }

    /** Top-level entries of the working directory, except the build output. */
    private static List<String> cwdEntries() throws IOException {
        try (Stream<Path> s = Files.list(Path.of("").toAbsolutePath())) {
            return s.map(p -> p.getFileName().toString()).filter(n -> !n.equals("target")).sorted().toList();
        }
    }

    @Test
    void noSaveDirWritesNothing() throws IOException {
        Map<String, String> homeBefore = ExportMetadataFixtures.snapshot(home);
        List<String> cwdBefore = cwdEntries();
        List<String> spillBefore = spillFiles();
        Map<String, String> responses = new HashMap<>();
        responses.put("getLogEntries", valueResponse("getLogEntries", ""));
        po.respondPerOperation(op -> responses.getOrDefault(op,
                valueResponse(op, Base64.getEncoder().encodeToString(multipart("payload")))));
        MessageTools tools = tools();

        call(tools, "message_log", "system", "QA1", "idOrKey", KEY);
        call(tools, "message_payload", "system", "QA1", "idOrKey", KEY);

        assertThat(sent("getLogEntries")).hasSize(1);
        assertThat(sent("getMessageBytesJavaLangStringBoolean")).hasSize(1);
        assertThat(ExportMetadataFixtures.snapshot(home)).isEqualTo(homeBefore);
        assertThat(cwdEntries()).isEqualTo(cwdBefore);
        assertThat(spillFiles()).isEqualTo(spillBefore);
    }

    // ------------------------------------------------------------------------------------------ the other tools

    private static final String KEY2 = "aaaaaaaa-1111-2222-3333-000000000001\\INBOUND\\0\\EO\\0\\";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";

    /** Texts of the element children of one parameter of the last request's operation element. */
    private List<String> sentItems(String operation, String parameter) {
        return FakeMonitoringPo.children(child(operation(po.lastRequest(), operation), OP, parameter)).stream()
                .map(org.w3c.dom.Node::getTextContent).toList();
    }

    @Test
    void udsSearchSendsAttributesAndOperator() {
        po.respondSoap(searchResponse("getUserDefinedSearchMessages", 1, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB", null)));

        JsonNode r = json(call(tools(), "message_uds_search", "system", "QA1",
                "attributes", List.of("OrderNo=A-1", "Plant=P-2"), "operator", "OR"));

        org.w3c.dom.Element op = operation(po.lastRequest(), "getUserDefinedSearchMessages");
        List<String> attributes = FakeMonitoringPo.children(child(op, OP, "attributes")).stream()
                .map(a -> child(a, FakeMonitoringPo.WS, "name").getTextContent() + "="
                        + child(a, FakeMonitoringPo.WS, "value").getTextContent())
                .toList();
        assertThat(attributes).containsExactly("OrderNo=A-1", "Plant=P-2");
        assertThat(child(op, OP, "operator").getTextContent()).isEqualTo("OR");
        assertThat(child(op, OP, "maxMessages").getTextContent()).isEqualTo("50");
        assertThat(r.path("messages")).hasSize(1);
        assertThat(r.path("messages").get(0).path("messageKey").asString()).isEqualTo(KEY);
    }

    @Test
    void getSendsIdsOrKeys() {
        po.respondPerOperation(op -> searchResponse(op, 1, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB", null)));
        MessageTools tools = tools();

        JsonNode byIds = json(call(tools, "message_get", "system", "QA1", "ids", List.of("msg-0001", "msg-0002")));
        assertThat(sentItems("getMessagesByIDs", "messageIds")).containsExactly("msg-0001", "msg-0002");
        JsonNode byKeys = json(call(tools, "message_get", "system", "QA1", "ids", List.of(KEY, KEY2)));
        assertThat(sentItems("getMessagesByKeys", "filter")).containsExactly(KEY, KEY2);

        assertThat(sent("getMessagesByIDs")).hasSize(1);
        assertThat(sent("getMessagesByKeys")).hasSize(1);
        assertThat(byIds.path("messages")).hasSize(1);
        assertThat(byKeys.path("messages")).hasSize(1);
    }

    @Test
    void successorsSendsTheIds() {
        po.respondSoap(searchResponse("getMessagesWithSuccessors", 2, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB", null),
                message("msg-0003", KEY2, "OUTBOUND", "success", "ReceiverC", null)));

        JsonNode r = json(call(tools(), "message_successors", "system", "QA1", "ids", List.of("msg-0001")));

        assertThat(sentItems("getMessagesWithSuccessors", "messageIds")).containsExactly("msg-0001");
        assertThat(r.path("messages")).hasSize(2);
    }

    @Test
    void predecessorReturnsTheValueOrNull() {
        MessageTools tools = tools();
        po.respondSoap(valueResponse("getPredecessorMessageId", "msg-0000"));

        JsonNode found = json(call(tools, "message_predecessor", "system", "QA1", "id", "msg-0001",
                "direction", "INBOUND"));

        org.w3c.dom.Element op = operation(po.lastRequest(), "getPredecessorMessageId");
        assertThat(child(op, OP, "messageId").getTextContent()).isEqualTo("msg-0001");
        assertThat(child(op, OP, "direction").getTextContent()).isEqualTo("INBOUND");
        assertThat(found.path("value").asString()).isEqualTo("msg-0000");

        po.respondSoap("<m:getPredecessorMessageIdResponse xmlns:m=\"" + OP + "\" xmlns:xsi=\"" + XSI
                + "\"><m:Response xsi:nil=\"true\"/></m:getPredecessorMessageIdResponse>");
        JsonNode none = json(call(tools, "message_predecessor", "system", "QA1", "id", "msg-0001",
                "direction", "INBOUND"));
        assertThat(none.path("value").isMissingNode() || none.path("value").isNull()).as("value is null").isTrue();
    }

    @Test
    void udsFiltersReturnsTheInterfaces() {
        po.respondSoap(valueResponse("getUserDefinedSearchFilters",
                "<d:MessageInterface><d:name>SI_A_Out</d:name><d:namespace>urn:test:a</d:namespace>"
                        + "</d:MessageInterface>"));

        JsonNode r = json(call(tools(), "message_uds_filters", "system", "QA1"));

        assertThat(sent("getUserDefinedSearchFilters")).hasSize(1);
        assertThat(r.path("values")).hasSize(1);
        assertThat(r.path("values").get(0).path("name").asString()).isEqualTo("SI_A_Out");
        assertThat(r.path("values").get(0).path("namespace").asString()).isEqualTo("urn:test:a");
    }

    @Test
    void udsExtractorsSendsNameAndNamespace() {
        po.respondSoap(valueResponse("getUserDefinedSearchExtractors",
                "<w:AttributeMetadata><w:name>OrderNo</w:name><w:description>Order number</w:description>"
                        + "</w:AttributeMetadata>"));

        JsonNode r = json(call(tools(), "message_uds_extractors", "system", "QA1", "interfaceName", "SI_A_Out",
                "interfaceNamespace", "urn:test:a"));

        org.w3c.dom.Element op = operation(po.lastRequest(), "getUserDefinedSearchExtractors");
        assertThat(child(op, OP, "name").getTextContent()).isEqualTo("SI_A_Out");
        assertThat(child(op, OP, "namespace").getTextContent()).isEqualTo("urn:test:a");
        assertThat(r.path("values").get(0).path("name").asString()).isEqualTo("OrderNo");
    }

    @Test
    void lookupServicesReturnsTheValues() {
        po.respondSoap(valueResponse("getServices", "<j:String>SenderA</j:String><j:String>ReceiverB</j:String>"));

        JsonNode r = json(call(tools(), "message_lookup", "system", "QA1", "kind", "services"));

        assertThat(sent("getServices")).hasSize(1);
        assertThat(r.path("values")).extracting(JsonNode::asString).containsExactly("SenderA", "ReceiverB");
    }

    @Test
    void lookupOfAnUnknownKindIsAToolErrorWithoutRequest() {
        assertThatThrownBy(() -> call(tools(), "message_lookup", "system", "QA1", "kind", "planets"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("planets");
        assertThat(po.requests()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ r2: raw answer cap

    /** A getServices answer of 1,001 values whose served envelope is exactly {@code rawLength} characters. */
    private static String servicesOfRawLength(int rawLength) {
        StringBuilder items = new StringBuilder();
        for (int i = 1; i <= 1_000; i++) {
            items.append("<j:String>Svc-").append(String.format("%04d", i)).append("</j:String>");
        }
        int pad = rawLength - FakeMonitoringPo.envelope(
                valueResponse("getServices", items + "<j:String>Pad-</j:String>")).length();
        assertThat(pad).as("padding").isNotNegative();
        String body = valueResponse("getServices", items + "<j:String>Pad-" + "x".repeat(pad) + "</j:String>");
        assertThat(FakeMonitoringPo.envelope(body)).hasSize(rawLength);
        return body;
    }

    @Test
    void rawAnswerAbove40000CharactersIsLeftOutEvenWithIncludeRaw() {
        String atLimit = servicesOfRawLength(40_000);
        String aboveLimit = servicesOfRawLength(40_001);
        MessageTools tools = tools();

        po.respondSoap(atLimit);
        JsonNode kept = json(call(tools, "message_lookup", "system", "QA1", "kind", "services", "includeRaw", true));
        po.respondSoap(aboveLimit);
        JsonNode left = json(call(tools, "message_lookup", "system", "QA1", "kind", "services", "includeRaw", true));

        assertThat(kept.path("rawResponse").asString()).isEqualTo(FakeMonitoringPo.envelope(atLimit));
        assertThat(kept.path("values")).hasSize(1_001);
        assertThat(kept.path("hint").isMissingNode() || kept.path("hint").isNull()).as("no hint").isTrue();
        assertThat(left.findValues("rawResponse")).isEmpty();
        assertThat(left.path("values")).hasSize(1_001);
        assertThat(left.path("truncated").asBoolean(true)).isFalse();
        assertThat(left.path("hint").asString("")).contains("rawResponse").contains("40000");
    }

    @Test
    void searchRawAbove40000CharactersIsLeftOutWithAHint() {
        List<String> messages = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            messages.add(message(String.format("msg-%04d", i), KEY.replace("0001", String.format("%04d", i)),
                    "OUTBOUND", "systemError", "ReceiverB", "E".repeat(1_700)));
        }
        String body = searchResponse("getMessageList", 25, false, messages.toArray(String[]::new));
        assertThat(FakeMonitoringPo.envelope(body).length()).isGreaterThan(40_000);
        po.respondSoap(body);

        JsonNode r = json(call(tools(), "message_search", "system", "QA1", "includeRaw", true));

        assertThat(r.findValues("rawResponse")).isEmpty();
        assertThat(r.path("messages")).hasSize(25);
        assertThat(r.path("truncated").asBoolean(true)).isFalse();
        assertThat(r.path("hint").asString("")).contains("rawResponse");
    }

    // ------------------------------------------------------------------------------------------ r2: search filter

    /**
     * The filter of the last getMessageList request flattened to {@code path -> text}: a child with element children
     * gives {@code child/grandchild}, repeated paths are joined with {@code |}, a nil element is {@code (nil)}.
     */
    private Map<String, String> sentFilter() {
        Element filter = child(operation(po.lastRequest(), "getMessageList"), OP, "filter");
        Map<String, String> out = new LinkedHashMap<>();
        for (Element c : FakeMonitoringPo.children(filter)) {
            List<Element> grand = FakeMonitoringPo.children(c);
            if (grand.isEmpty()) {
                out.merge(c.getLocalName(), text(c), (a, b) -> a + "|" + b);
            }
            for (Element g : grand) {
                out.merge(c.getLocalName() + "/" + g.getLocalName(), text(g), (a, b) -> a + "|" + b);
            }
        }
        return out;
    }

    private static String text(Element e) {
        return "true".equals(e.getAttributeNS(XSI, "nil")) ? "(nil)" : e.getTextContent();
    }

    /** Entries of {@code got} that differ from {@code base} ({@code (absent)} for a removed one), times left out. */
    private static Map<String, String> changed(Map<String, String> base, Map<String, String> got) {
        Map<String, String> diff = new LinkedHashMap<>();
        got.forEach((k, v) -> {
            if (!v.equals(base.get(k))) {
                diff.put(k, v);
            }
        });
        base.keySet().stream().filter(k -> !got.containsKey(k)).forEach(k -> diff.put(k, "(absent)"));
        diff.remove("fromTime");
        diff.remove("toTime");
        return diff;
    }

    private static void assertNear(String sentTime, Instant expected) {
        assertThat(sentTime).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*");
        assertThat(Duration.between(expected, OffsetDateTime.parse(sentTime).toInstant()).abs())
                .as("%s near %s", sentTime, expected).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    void searchFiltersFillOnlyTheirOwnFilterField() {
        po.respondSoap(ONE_MESSAGE);
        MessageTools tools = tools();
        call(tools, "message_search", "system", "QA1");
        Instant now = Instant.now();
        Map<String, String> base = sentFilter();
        assertNear(base.get("fromTime"), now.minus(Duration.ofHours(24)));
        assertNear(base.get("toTime"), now);

        Map<String, Object> single = new LinkedHashMap<>();
        single.put("sender", "SenderA");
        single.put("receiver", "ReceiverB");
        single.put("statuses", List.of("systemError", "holding"));
        single.put("faulty", true);
        single.put("archive", true);
        Map<String, Map<String, String>> expected = Map.of(
                "sender", Map.of("senderName", "SenderA"),
                "receiver", Map.of("receiverName", "ReceiverB"),
                "statuses", Map.of("statuses/String", "systemError|holding"),
                "faulty", Map.of("onlyFaultyMessages", "true"),
                "archive", Map.of("archive", "true"));
        for (var e : single.entrySet()) {
            call(tools, "message_search", "system", "QA1", e.getKey(), e.getValue());
            assertThat(changed(base, sentFilter())).as(e.getKey()).isEqualTo(expected.get(e.getKey()));
        }

        call(tools, "message_search", "system", "QA1", "interfaceName", "SI_A_Out");
        Map<String, String> byName = changed(base, sentFilter());
        assertThat(byName).containsEntry("interface/name", "SI_A_Out");
        assertThat(byName).allSatisfy((k, v) -> {
            if (!k.equals("interface/name")) {
                assertThat(k).startsWith("interface/");
                assertThat(v).isEqualTo("(nil)");
            }
        });
        call(tools, "message_search", "system", "QA1", "interfaceNamespace", "urn:test:a");
        Map<String, String> byNamespace = changed(base, sentFilter());
        assertThat(byNamespace).containsEntry("interface/namespace", "urn:test:a");
        assertThat(byNamespace).allSatisfy((k, v) -> {
            if (!k.equals("interface/namespace")) {
                assertThat(k).startsWith("interface/");
                assertThat(v).isEqualTo("(nil)");
            }
        });

        call(tools, "message_search", "system", "QA1", "from", "2026-11-10T08:00:00+01:00");
        Map<String, String> byFrom = sentFilter();
        assertThat(changed(base, byFrom)).as("from").isEmpty();
        assertThat(OffsetDateTime.parse(byFrom.get("fromTime")).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00+01:00").toInstant());
        call(tools, "message_search", "system", "QA1", "to", "2026-11-10T09:30:00+01:00");
        Map<String, String> byTo = sentFilter();
        assertThat(changed(base, byTo)).as("to").isEmpty();
        assertThat(OffsetDateTime.parse(byTo.get("toTime")).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-11-10T09:30:00+01:00").toInstant());
        assertNear(byTo.get("fromTime"), Instant.now().minus(Duration.ofHours(24)));
    }

    @Test
    void moreAvailableAddsAHint() {
        MessageTools tools = tools();
        po.respondSoap(searchResponse("getMessageList", 1, true,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB", null)));
        JsonNode more = json(call(tools, "message_search", "system", "QA1"));
        po.respondSoap(ONE_MESSAGE);
        JsonNode all = json(call(tools, "message_search", "system", "QA1"));

        assertThat(more.path("moreAvailable").asBoolean(false)).isTrue();
        assertThat(more.path("hint").asString("")).isNotBlank();
        assertThat(more.path("truncated").asBoolean(true)).isFalse();
        assertThat(all.path("moreAvailable").asBoolean(true)).isFalse();
        assertThat(all.path("hint").isMissingNode() || all.path("hint").isNull()).as("no hint").isTrue();
    }

    // ------------------------------------------------------------------------------------------ r2: blank strings

    @Test
    void blankSearchStringsCountAsAbsent() {
        po.respondSoap(ONE_MESSAGE);
        MessageTools tools = tools();
        call(tools, "message_search", "system", "QA1");
        Map<String, String> base = sentFilter();

        call(tools, "message_search", "system", "QA1", "interfaceName", " ", "interfaceNamespace", "",
                "sender", "  ", "receiver", "\t", "statuses", List.of(" ", ""), "from", " ", "to", "");
        Instant now = Instant.now();

        Map<String, String> blank = sentFilter();
        assertThat(changed(base, blank)).isEmpty();
        assertThat(blank.keySet()).noneMatch(k -> k.startsWith("interface") || k.startsWith("statuses")
                || k.equals("senderName") || k.equals("receiverName"));
        assertNear(blank.get("fromTime"), now.minus(Duration.ofHours(24)));
        assertNear(blank.get("toTime"), now);
    }

    @Test
    void blankKeyOlderThanAndSaveDirCountAsAbsent() throws IOException {
        Map<String, String> homeBefore = ExportMetadataFixtures.snapshot(home);
        List<String> cwdBefore = cwdEntries();
        po.respondPerOperation(op -> op.equals("getMessagesByIDs") ? searchResponse(op, 1, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB", null)) : valueResponse(op, ""));

        JsonNode r = json(call(tools(), "message_log", "system", "QA1", "idOrKey", "msg-0001", "key", " ",
                "direction", " ", "olderThan", " ", "saveDir", " "));

        assertThat(sent("getMessagesByIDs")).hasSize(1);
        Element op = operation(sent("getLogEntries").getFirst(), "getLogEntries");
        assertThat(child(op, OP, "messageKey").getTextContent()).isEqualTo(KEY);
        assertThat(child(op, OP, "olderThan").getAttributeNS(XSI, "nil")).isEqualTo("true");
        assertThat(r.path("messageKey").asString()).isEqualTo(KEY);
        assertThat(r.findValues("saved")).isEmpty();
        assertThat(ExportMetadataFixtures.snapshot(home)).isEqualTo(homeBefore);
        assertThat(cwdEntries()).isEqualTo(cwdBefore);
    }

    @Test
    void blankLoggedDirectionAndLangCountAsAbsent() {
        po.respondPerOperation(op -> op.startsWith("getMessageBytes") || op.equals("getLoggedMessageBytes")
                ? valueResponse(op, Base64.getEncoder().encodeToString(multipart("payload")))
                : valueResponse(op, ""));
        MessageTools tools = tools();

        call(tools, "message_payload", "system", "QA1", "key", KEY, "version", 2, "logged", " ");
        call(tools, "message_predecessor", "system", "QA1", "id", "msg-0001", "direction", " ");
        call(tools, "message_lookup", "system", "QA1", "kind", "iflows", "lang", " ");

        assertThat(sent("getMessageBytesJavaLangStringIntBoolean")).hasSize(1);
        assertThat(sent("getLoggedMessageBytes")).isEmpty();
        Element predecessor = operation(sent("getPredecessorMessageId").getFirst(), "getPredecessorMessageId");
        assertThat(child(predecessor, OP, "direction").getAttributeNS(XSI, "nil")).isEqualTo("true");
        Element iflows = operation(sent("getIntegrationFlows").getFirst(), "getIntegrationFlows");
        assertThat(child(iflows, OP, "language").getTextContent()).isEqualTo("en");
    }

    // ------------------------------------------------------------------------------------------ r2: refused input

    @Test
    void udsSearchWithABadOperatorOrAttributeIsAToolErrorWithoutRequest() {
        MessageTools tools = tools();

        assertThatThrownBy(() -> call(tools, "message_uds_search", "system", "QA1",
                "attributes", List.of("OrderNo=A-1"), "operator", "XOR"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("XOR");
        assertThatThrownBy(() -> call(tools, "message_uds_search", "system", "QA1",
                "attributes", List.of("OrderNo=A-1", "Plant")))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("Plant");
        assertThatThrownBy(() -> call(tools, "message_uds_search", "system", "QA1",
                "attributes", List.of("=A-1")))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("=A-1");
        assertThatThrownBy(() -> call(tools, "message_uds_search", "system", "QA1", "attributes", List.of()))
                .isInstanceOf(ToolCallException.class);

        assertThat(po.requests()).isEmpty();
    }

    @Test
    void getMixingIdsAndKeysIsAToolErrorWithoutRequest() {
        assertThatThrownBy(() -> call(tools(), "message_get", "system", "QA1", "ids", List.of("msg-0001", KEY)))
                .isInstanceOf(ToolCallException.class)
                .hasMessageNotContaining("\n");

        assertThat(po.requests()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ r2: key and saving

    private void serveOneMessage() {
        po.respondPerOperation(op -> op.startsWith("getMessageBytes")
                ? valueResponse(op, Base64.getEncoder().encodeToString(multipart("payload text")))
                : op.equals("getMessagesByIDs") ? searchResponse(op, 1, false,
                        message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB", null))
                : valueResponse(op, ""));
    }

    private static String messageKeySent(FakeMonitoringPo.Request request, String operation) {
        return child(operation(request, operation), OP, "messageKey").getTextContent();
    }

    @Test
    void keySkipsTheIdLookup() {
        serveOneMessage();
        MessageTools tools = tools();

        JsonNode log = json(call(tools, "message_log", "system", "QA1", "key", KEY));
        JsonNode payload = json(call(tools, "message_payload", "system", "QA1", "idOrKey", "msg-0001", "key", KEY2));
        JsonNode attributes = json(call(tools, "message_uds_attributes", "system", "QA1", "key", KEY));

        assertThat(sent("getMessagesByIDs")).isEmpty();
        assertThat(sent("getMessagesByKeys")).isEmpty();
        assertThat(messageKeySent(sent("getLogEntries").getFirst(), "getLogEntries")).isEqualTo(KEY);
        assertThat(messageKeySent(sent("getMessageBytesJavaLangStringBoolean").getFirst(),
                "getMessageBytesJavaLangStringBoolean")).isEqualTo(KEY2);
        assertThat(messageKeySent(sent("getUserDefinedSearchAttributes").getFirst(),
                "getUserDefinedSearchAttributes")).isEqualTo(KEY);
        assertThat(log.path("messageKey").asString()).isEqualTo(KEY);
        assertThat(payload.path("messageKey").asString()).isEqualTo(KEY2);
        assertThat(attributes.path("system").asString()).isEqualTo("QA1");
    }

    @Test
    void savedNamesTheMessageDirectory(@TempDir Path tmp) throws IOException {
        Path out = repo(tmp).resolve("local").resolve("messages");
        serveOneMessage();
        MessageTools tools = tools();

        JsonNode log = json(call(tools, "message_log", "system", "QA1", "idOrKey", "msg-0001", "saveDir",
                out.toString()));
        JsonNode payload = json(call(tools, "message_payload", "system", "QA1", "idOrKey", "msg-0002", "key", KEY,
                "saveDir", out.toString()));
        JsonNode attributes = json(call(tools, "message_uds_attributes", "system", "QA1", "key", KEY2,
                "saveDir", out.toString()));

        String keyDir = "aaaaaaaa-1111-2222-3333-000000000001_INBOUND_0_EO_0_";
        List<String> dirs;
        try (Stream<Path> s = Files.list(out)) {
            dirs = s.map(p -> p.getFileName().toString()).sorted().toList();
        }
        assertThat(dirs).containsExactly(keyDir, "msg-0001", "msg-0002");
        Path logDir = Path.of(log.path("saved").asString(""));
        Path payloadDir = Path.of(payload.path("saved").asString(""));
        Path attributesDir = Path.of(attributes.path("saved").asString(""));
        assertThat(logDir.getFileName()).hasToString("msg-0001");
        assertThat(payloadDir.getFileName()).hasToString("msg-0002");
        assertThat(attributesDir.getFileName()).hasToString(keyDir);
        assertThat(logDir.resolve("model.json")).isRegularFile();
        assertThat(payloadDir.resolve("payload.bin")).isRegularFile();
        assertThat(attributesDir.resolve("response.xml")).isRegularFile();
        assertThat(sent("getMessagesByIDs")).hasSize(1);
    }

    @Test
    void refusedSaveDirOnUdsAttributesSendsNoRequest(@TempDir Path tmp) throws IOException {
        Path repo = repo(tmp);
        Map<String, String> before = ExportMetadataFixtures.snapshot(repo);
        serveOneMessage();

        assertThatThrownBy(() -> call(tools(), "message_uds_attributes", "system", "QA1", "key", KEY, "saveDir",
                repo.resolve("src").toString()))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("refusing to save customer data");
        assertThatThrownBy(() -> call(tools(), "message_uds_attributes", "system", "QA1", "idOrKey", "msg-0001",
                "saveDir", repo.toString()))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("refusing to save customer data");

        assertThat(po.requests()).isEmpty();
        assertThat(ExportMetadataFixtures.snapshot(repo)).isEqualTo(before);
    }

    @Test
    void ambiguousIdOnUdsAttributesReturnsCandidates() {
        po.respondSoap(searchResponse("getMessagesByIDs", 2, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB", null),
                message("msg-0001", KEY2, "INBOUND", "holding", "ReceiverC", null)));

        JsonNode r = json(call(tools(), "message_uds_attributes", "system", "QA1", "idOrKey", "msg-0001"));

        assertThat(r.path("candidates")).extracting(c -> c.path("key").asString()).containsExactly(KEY, KEY2);
        assertThat(r.path("hint").asString("")).isNotBlank();
        assertThat(sent("getUserDefinedSearchAttributes")).isEmpty();
    }

    @Test
    void savingToolDescriptionsSayTheyWriteOnlyWithSaveDir() {
        for (String tool : SAVING) {
            assertThat(toolMethod(tool).getAnnotation(Tool.class).description()).as(tool)
                    .contains("writes only when saveDir is given");
        }
    }

    @Test
    void badFromIsAToolErrorWithoutRequest() {
        MessageTools tools = tools();

        assertThatThrownBy(() -> call(tools, "message_search", "system", "QA1", "from", "yesterday"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("yesterday");
        assertThatThrownBy(() -> call(tools, "message_uds_search", "system", "QA1", "attributes",
                List.of("OrderNo=A-1"), "to", "tomorrow"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("tomorrow");
        assertThatThrownBy(() -> call(tools, "message_log", "system", "QA1", "key", KEY, "olderThan", "lastweek"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("lastweek");

        assertThat(po.requests()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ r2: log

    @Test
    void logLimitIsMaxResultsAndUnsetOlderThanIsNil() {
        po.respondSoap(valueResponse("getLogEntries", ""));
        MessageTools tools = tools();

        call(tools, "message_log", "system", "QA1", "key", KEY);
        call(tools, "message_log", "system", "QA1", "key", KEY, "limit", 7, "olderThan", "2026-11-10T08:00:00+01:00");

        List<FakeMonitoringPo.Request> logs = sent("getLogEntries");
        assertThat(logs).hasSize(2);
        Element first = operation(logs.get(0), "getLogEntries");
        assertThat(child(first, OP, "maxResults").getTextContent()).isEqualTo("50");
        assertThat(child(first, OP, "olderThan").getAttributeNS(XSI, "nil")).isEqualTo("true");
        Element second = operation(logs.get(1), "getLogEntries");
        assertThat(child(second, OP, "maxResults").getTextContent()).isEqualTo("7");
        assertThat(OffsetDateTime.parse(child(second, OP, "olderThan").getTextContent()).toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-11-10T08:00:00+01:00").toInstant());
    }

    @Test
    void logEntriesAreFittedToTheResultCap() {
        StringBuilder entries = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            entries.append("<d:AuditLogEntryData><d:timeStamp>2026-11-10T08:15:30.100+01:00</d:timeStamp>")
                    .append("<d:textKey>K-").append(String.format("%02d", i)).append("</d:textKey>")
                    .append("<d:status>success</d:status><d:localizedText>").append("T".repeat(1_900))
                    .append("</d:localizedText></d:AuditLogEntryData>");
        }
        po.respondSoap(valueResponse("getLogEntries", entries.toString()));

        String out = call(tools(), "message_log", "system", "QA1", "key", KEY);

        assertThat(out.length()).isLessThanOrEqualTo(80_000);
        JsonNode r = json(out);
        int kept = r.path("entries").size();
        assertThat(kept).isPositive();
        assertThat(r.path("truncated").asBoolean(false)).isTrue();
        assertThat(kept + r.path("dropped").asInt(-1)).isEqualTo(60);
        assertThat(r.path("dropped").asInt(-1)).isPositive();
        assertThat(r.path("entries").get(0).path("textKey").asString()).isEqualTo("K-01");
        assertThat(r.path("hint").asString("")).isNotBlank();
    }

    // ------------------------------------------------------------------------------------------ r2: payload

    /** A multipart message with only one text/plain part. */
    private static byte[] singlePart(String payloadText) {
        String s = "Content-Type: multipart/related; boundary=" + BOUNDARY + "\r\n\r\n"
                + "--" + BOUNDARY + "\r\ncontent-id:<payload-1@test>\r\ncontent-type:text/plain; charset=utf-8\r\n\r\n"
                + payloadText + "\r\n--" + BOUNDARY + "--\r\n";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void defaultPartIsTheFirstAfterTheHeaderElseTheOnlyOne() {
        MessageTools tools = tools();
        servePayload(multipart("second part"));
        JsonNode two = json(call(tools, "message_payload", "system", "QA1", "key", KEY));
        servePayload(singlePart("only part"));
        JsonNode one = json(call(tools, "message_payload", "system", "QA1", "key", KEY));

        assertThat(two.path("part").asInt(-1)).isEqualTo(1);
        assertThat(two.path("content").asString()).isEqualTo("second part");
        assertThat(two.path("encoding").asString()).isEqualTo("text");
        assertThat(two.path("contentType").asString()).contains("text/plain");
        JsonNode parts = two.path("parts");
        assertThat(parts).hasSize(2);
        assertThat(parts).extracting(p -> p.path("index").asInt(-1)).containsExactly(0, 1);
        assertThat(parts.get(0).path("contentId").asString()).contains("soap-1@test");
        assertThat(parts.get(1).path("contentId").asString()).contains("payload-1@test");
        assertThat(parts.get(0).path("contentType").asString()).contains("text/xml");
        assertThat(parts.get(1).path("contentType").asString()).contains("text/plain");
        assertThat(parts).allSatisfy(p -> assertThat(p.path("binary").asBoolean(true)).isFalse());
        assertThat(one.path("part").asInt(-1)).isEqualTo(0);
        assertThat(one.path("content").asString()).isEqualTo("only part");
        assertThat(one.path("parts")).hasSize(1);
    }

    @Test
    void nonMultipartPayloadIsTextOrBase64() {
        MessageTools tools = tools();
        byte[] text = "plain payload, not MIME: Grüße".getBytes(StandardCharsets.UTF_8);
        byte[] binary = {(byte) 0xFF, (byte) 0xFE, 0x41, (byte) 0xC3};
        servePayload(text);
        JsonNode asText = json(call(tools, "message_payload", "system", "QA1", "key", KEY));
        servePayload(binary);
        JsonNode asBase64 = json(call(tools, "message_payload", "system", "QA1", "key", KEY));

        assertThat(asText.path("encoding").asString()).isEqualTo("text");
        assertThat(asText.path("content").asString()).isEqualTo("plain payload, not MIME: Grüße");
        assertThat(asText.path("size").asInt(-1)).isEqualTo(text.length);
        assertThat(asText.path("parts").size()).isZero();
        assertThat(asText.path("part").isMissingNode() || asText.path("part").isNull()).as("no part").isTrue();
        assertThat(asBase64.path("encoding").asString()).isEqualTo("base64");
        assertThat(asBase64.path("content").asString()).isEqualTo(Base64.getEncoder().encodeToString(binary));
        assertThat(asBase64.path("size").asInt(-1)).isEqualTo(binary.length);
    }

    @Test
    void payloadPageGivesNextOffsetUntilTheEnd() {
        servePayload(multipart("0123456789"));
        MessageTools tools = tools();

        JsonNode middle = json(call(tools, "message_payload", "system", "QA1", "key", KEY, "offset", 2,
                "maxChars", 3));
        JsonNode last = json(call(tools, "message_payload", "system", "QA1", "key", KEY, "offset", 5,
                "maxChars", 10));

        assertThat(middle.path("content").asString()).isEqualTo("234");
        assertThat(middle.path("offset").asInt(-1)).isEqualTo(2);
        assertThat(middle.path("nextOffset").asInt(-1)).isEqualTo(5);
        assertThat(middle.path("truncated").asBoolean(false)).isTrue();
        assertThat(last.path("content").asString()).isEqualTo("56789");
        assertThat(last.path("truncated").asBoolean(true)).isFalse();
        assertThat(last.path("nextOffset").isMissingNode() || last.path("nextOffset").isNull())
                .as("no nextOffset on the last page").isTrue();
    }

    @Test
    void surrogatePairIsNeverSplitAtAPageBoundary() {
        String text = "ab😀cd😀e";
        servePayload(multipart(text));
        MessageTools tools = tools();

        List<String> pages = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < 20; i++) {
            JsonNode page = json(call(tools, "message_payload", "system", "QA1", "key", KEY, "offset", offset,
                    "maxChars", 3));
            pages.add(page.path("content").asString());
            if (!page.path("truncated").asBoolean(false)) {
                break;
            }
            int next = page.path("nextOffset").asInt(-1);
            assertThat(next).as("progress after offset %d", offset).isGreaterThan(offset);
            offset = next;
        }

        assertThat(pages.getFirst()).isEqualTo("ab");
        assertThat(String.join("", pages)).isEqualTo(text);
        assertThat(pages).allSatisfy(p -> {
            assertThat(p).isNotEmpty();
            assertThat(Character.isHighSurrogate(p.charAt(p.length() - 1))).as("ends with a high surrogate: %s", p)
                    .isFalse();
            assertThat(Character.isLowSurrogate(p.charAt(0))).as("starts with a low surrogate: %s", p).isFalse();
        });
    }

    @Test
    void onePagerOnASurrogatePairStillAdvances() {
        String text = "ab😀cd😀e";
        servePayload(multipart(text));
        MessageTools tools = tools();

        JsonNode pair = json(call(tools, "message_payload", "system", "QA1", "key", KEY, "offset", 2,
                "maxChars", 1));

        assertThat(pair.path("content").asString()).isEqualTo("😀");
        assertThat(pair.path("nextOffset").asInt(-1)).isEqualTo(4);
        assertThat(pair.path("truncated").asBoolean(false)).isTrue();

        StringBuilder all = new StringBuilder();
        int offset = 0;
        for (int i = 0; i <= text.length(); i++) {
            JsonNode page = json(call(tools, "message_payload", "system", "QA1", "key", KEY, "offset", offset,
                    "maxChars", 1));
            all.append(page.path("content").asString());
            if (!page.path("truncated").asBoolean(false)) {
                break;
            }
            int next = page.path("nextOffset").asInt(-1);
            assertThat(next).as("progress after offset %d", offset).isGreaterThan(offset);
            offset = next;
        }
        assertThat(all.toString()).isEqualTo(text);
    }

    /** Pages through the whole selected part with {@code maxChars}, checking each page against {@code text}. */
    private static String pageThrough(MessageTools tools, String text, int maxChars) {
        StringBuilder all = new StringBuilder();
        int offset = 0;
        for (int i = 0; i <= text.length(); i++) {
            String out = call(tools, "message_payload", "system", "QA1", "key", KEY, "offset", offset,
                    "maxChars", maxChars);
            assertThat(out.length()).as("result length at offset %d", offset).isLessThanOrEqualTo(80_000);
            JsonNode page = json(out);
            String content = page.path("content").asString();
            assertThat(text.substring(offset)).as("page at %d is a prefix of the rest", offset).startsWith(content);
            all.append(content);
            if (!page.path("truncated").asBoolean(false)) {
                break;
            }
            assertThat(content).as("page at %d", offset).isNotEmpty();
            assertThat(page.path("nextOffset").asInt(-1)).as("nextOffset after %d", offset)
                    .isEqualTo(offset + content.length());
            offset += content.length();
        }
        return all.toString();
    }

    @Test
    void payloadPageAboveTheResultCapIsShrunk() {
        MessageTools tools = tools();
        StringBuilder letters = new StringBuilder();
        for (int i = 0; letters.length() < 90_000; i++) {
            letters.append((char) ('a' + i % 26));
        }
        String plain = letters.toString();
        servePayload(multipart(plain));

        String first = call(tools, "message_payload", "system", "QA1", "key", KEY, "maxChars", 100_000);

        assertThat(first.length()).isLessThanOrEqualTo(80_000);
        JsonNode page = json(first);
        String content = page.path("content").asString();
        assertThat(page.path("truncated").asBoolean(false)).isTrue();
        assertThat(content).isNotEmpty().hasSizeLessThan(90_000);
        assertThat(plain).startsWith(content);
        assertThat(page.path("nextOffset").asInt(-1)).isEqualTo(content.length());
        assertThat(page.path("totalChars").asInt(-1)).isEqualTo(90_000);
        assertThat(pageThrough(tools, plain, 100_000)).isEqualTo(plain);

        String quotes = "\"".repeat(45_000);
        servePayload(multipart(quotes));
        assertThat(pageThrough(tools, quotes, 20_000)).isEqualTo(quotes);
        String bigQuotes = call(tools, "message_payload", "system", "QA1", "key", KEY, "maxChars", 45_000);
        assertThat(bigQuotes.length()).isLessThanOrEqualTo(80_000);
        assertThat(json(bigQuotes).path("truncated").asBoolean(false)).isTrue();
        assertThat(pageThrough(tools, quotes, 45_000)).isEqualTo(quotes);
    }

    @Test
    void badPayloadArgumentsAreToolErrorsWithoutRequest() {
        servePayload(multipart("0123456789"));
        MessageTools tools = tools();

        assertThatThrownBy(() -> call(tools, "message_payload", "system", "QA1", "key", KEY, "maxChars", 0))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("maxChars");
        assertThatThrownBy(() -> call(tools, "message_payload", "system", "QA1", "key", KEY, "maxChars", -1))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("maxChars");
        assertThatThrownBy(() -> call(tools, "message_payload", "system", "QA1", "key", KEY, "offset", -1))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("offset");
        assertThatThrownBy(() -> call(tools, "message_payload", "system", "QA1", "key", KEY, "version", 2,
                "logged", "BI"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("version").hasMessageContaining("logged");

        assertThat(po.requests()).isEmpty();
    }

    @Test
    void offsetPastTheEndOrAMissingPartIsAToolError() {
        servePayload(multipart("0123456789"));
        MessageTools tools = tools();

        assertThatThrownBy(() -> call(tools, "message_payload", "system", "QA1", "key", KEY, "offset", 11))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("11");
        assertThatThrownBy(() -> call(tools, "message_payload", "system", "QA1", "key", KEY, "part", 2))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("part").hasMessageContaining("2");
        assertThatThrownBy(() -> call(tools, "message_payload", "system", "QA1", "key", KEY, "part", -1))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("part");
    }

    // ------------------------------------------------------------------------------------------ r2: lookups

    @Test
    void errorCodesWithoutLabelIsAToolErrorWithoutRequest() {
        assertThatThrownBy(() -> call(tools(), "message_lookup", "system", "QA1", "kind", "error-codes"))
                .isInstanceOf(ToolCallException.class).hasMessageContaining("label");
        assertThat(po.requests()).isEmpty();

        po.respondSoap(valueResponse("getErrorCodes", "<j:String>E-1</j:String>"));
        JsonNode r = json(call(tools(), "message_lookup", "system", "QA1", "kind", "error-codes", "label", 3));

        assertThat(child(operation(po.lastRequest(), "getErrorCodes"), OP, "errorLabelID").getTextContent())
                .isEqualTo("3");
        assertThat(r.path("values")).extracting(JsonNode::asString).containsExactly("E-1");
    }

    @Test
    void statusDetailsWithoutCodesAsksForAllAndLangDefaultsToEn() {
        po.respondPerOperation(op -> valueResponse(op, ""));
        MessageTools tools = tools();

        call(tools, "message_lookup", "system", "QA1", "kind", "status-details");
        Element all = operation(po.lastRequest(), "getAllAvailableStatusDetails");
        assertThat(child(child(all, OP, "locale"), FakeMonitoringPo.WS, "language").getTextContent()).isEqualTo("en");

        call(tools, "message_lookup", "system", "QA1", "kind", "status-details", "codes", List.of("E-1", "E-2"),
                "lang", "de");
        Element some = operation(po.lastRequest(), "getStatusDetails");
        assertThat(sentItems("getStatusDetails", "errorCodes")).containsExactly("E-1", "E-2");
        assertThat(child(child(some, OP, "locale"), FakeMonitoringPo.WS, "language").getTextContent()).isEqualTo("de");

        call(tools, "message_lookup", "system", "QA1", "kind", "iflows");
        assertThat(child(operation(po.lastRequest(), "getIntegrationFlows"), OP, "language").getTextContent())
                .isEqualTo("en");
        call(tools, "message_lookup", "system", "QA1", "kind", "iflows", "lang", "de");
        assertThat(child(operation(po.lastRequest(), "getIntegrationFlows"), OP, "language").getTextContent())
                .isEqualTo("de");

        assertThat(sent("getAllAvailableStatusDetails")).hasSize(1);
        assertThat(sent("getStatusDetails")).hasSize(1);
    }

    @Test
    void lookupValuesAreFittedToTheResultCap() {
        StringBuilder values = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            values.append("<j:String>V-").append(String.format("%02d", i)).append("-").append("v".repeat(1_900))
                    .append("</j:String>");
        }
        po.respondSoap(valueResponse("getServices", values.toString()));

        String out = call(tools(), "message_lookup", "system", "QA1", "kind", "services");

        assertThat(out.length()).isLessThanOrEqualTo(80_000);
        JsonNode r = json(out);
        int kept = r.path("values").size();
        assertThat(kept).isPositive();
        assertThat(r.path("truncated").asBoolean(false)).isTrue();
        assertThat(r.path("dropped").asInt(-1)).isPositive();
        assertThat(kept + r.path("dropped").asInt(-1)).isEqualTo(60);
        assertThat(r.path("values").get(0).asString()).startsWith("V-01-");
        assertThat(r.path("hint").asString("")).isNotBlank();
    }

    @Test
    void lookupOtherFieldsArePassedThrough() {
        po.respondSoap(valueResponse("getParties",
                "<d:MessageParty><d:name>PartyA</d:name></d:MessageParty><d:FutureItem>x</d:FutureItem>"));

        JsonNode r = json(call(tools(), "message_lookup", "system", "QA1", "kind", "parties"));

        assertThat(r.path("values")).hasSize(1);
        assertThat(r.path("values").get(0).path("name").asString()).isEqualTo("PartyA");
        assertThat(r.path("otherFields").path("Response/FutureItem").asString()).isEqualTo("x");
    }
}
