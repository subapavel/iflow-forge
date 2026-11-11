package io.poxray.cli;

import static io.poxray.monitoring.FakeMonitoringPo.OP;
import static io.poxray.monitoring.FakeMonitoringPo.WS;
import static io.poxray.monitoring.FakeMonitoringPo.child;
import static io.poxray.monitoring.FakeMonitoringPo.operation;
import static io.poxray.monitoring.FakeMonitoringPo.operationName;
import static io.poxray.monitoring.FakeMonitoringPo.searchResponse;
import static io.poxray.monitoring.FakeMonitoringPo.valueResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import io.poxray.migration.ExportMetadataFixtures;
import io.poxray.monitoring.FakeMonitoringPo;
import io.poxray.monitoring.MessageFilter;
import io.poxray.monitoring.MonitoringClient;
import io.poxray.monitoring.SearchResult;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

/**
 * {@code po-xray messages ...} against the local fake AdapterMessageMonitoring service. Each test points
 * {@code user.home} at a temporary directory holding {@code .po-xray/systems/QA1.properties} for the fake (synthetic
 * names only) and restores it afterwards; the real home is never read.
 */
@QuarkusMainTest
class MessagesCommandTest {

    private static final String KEY = "aaaaaaaa-1111-2222-3333-000000000001\\OUTBOUND\\0\\EO\\0\\";
    private static final String KEY2 = "aaaaaaaa-1111-2222-3333-000000000001\\INBOUND\\0\\EO\\0\\";
    private static final String START = "2026-11-10T08:15:30.123+01:00";
    private static final DateTimeFormatter TABLE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

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

    @BeforeEach
    @AfterEach
    void resetEnvironment() {
        EmptyMonitoringEnvironment.reset();
    }

    // ------------------------------------------------------------------------------------------ fixtures

    /** One AdapterFrameworkData with the table's fields (prefixes w, d of {@link FakeMonitoringPo#searchResponse}). */
    private static String message(String id, String key, String direction, String status, String receiver) {
        return "<w:AdapterFrameworkData><w:direction>" + direction + "</w:direction>"
                + "<w:messageID>" + id + "</w:messageID>"
                + (key == null ? "" : "<w:messageKey>" + key + "</w:messageKey>")
                + "<w:receiverInterface><d:name>SI_B_In</d:name><d:namespace>urn:test:b</d:namespace>"
                + "</w:receiverInterface><w:receiverName>" + receiver + "</w:receiverName>"
                + "<w:senderInterface><d:name>SI_A_Out</d:name><d:namespace>urn:test:a</d:namespace>"
                + "</w:senderInterface><w:senderName>SenderA</w:senderName>"
                + "<w:startTime>" + START + "</w:startTime><w:status>" + status + "</w:status>"
                + "</w:AdapterFrameworkData>";
    }

    private static final String ONE_MESSAGE = searchResponse("getMessageList", 1, false,
            message("msg-0001", KEY, "OUTBOUND", "systemError", "ReceiverB"));

    /** The requests sent so far for one operation. */
    private List<FakeMonitoringPo.Request> sent(String operation) {
        return po.requests().stream().filter(r -> operationName(r.body()).equals(operation)).toList();
    }

    private static String all(LaunchResult r) {
        return r.getOutput() + "\n" + r.getErrorOutput();
    }

    private static String line(String output, String containing) {
        return output.lines().filter(l -> l.contains(containing)).findFirst()
                .orElseThrow(() -> new AssertionError("no line with " + containing + " in:\n" + output));
    }

    private static final String HEADER_PART = "<SOAP:Envelope xmlns:SOAP=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<SOAP:Header/><SOAP:Body/></SOAP:Envelope>";
    private static final String PAYLOAD = "<order><no>A-1</no></order>";
    private static final String BOUNDARY = "SAP_test-boundary-0001_END";

    /** An XI-style multipart message: the SOAP header part, then each given payload part (text/plain or XML). */
    private static byte[] multipart(String... payloadParts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StringBuilder b = new StringBuilder("Content-Type: multipart/related; boundary=" + BOUNDARY
                + "; type=\"text/xml\"\r\n\r\n");
        b.append("--").append(BOUNDARY).append("\r\nContent-ID: <soap-1@test>\r\nContent-Type: text/xml; charset=utf-8")
                .append("\r\n\r\n").append(HEADER_PART).append("\r\n");
        for (int i = 0; i < payloadParts.length; i++) {
            b.append("--").append(BOUNDARY).append("\r\ncontent-id:<payload-").append(i + 1)
                    .append("@test>\r\ncontent-type:application/xml\r\n\r\n").append(payloadParts[i]).append("\r\n");
        }
        b.append("--").append(BOUNDARY).append("--\r\n");
        out.writeBytes(b.toString().getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private void servePayload(byte[] bytes) {
        po.respondPerOperation(op -> valueResponse(op, Base64.getEncoder().encodeToString(bytes)));
    }

    // ------------------------------------------------------------------------------------------ search

    @Test
    void searchPrintsTableWithLocalTime(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1", "--from", "2026-11-10T00:00",
                "--to", "2026-11-10T12:00");

        assertThat(r.exitCode()).as(all(r)).isZero();
        String expectedTime = TABLE_TIME.format(OffsetDateTime.parse(START).atZoneSameInstant(ZoneId.systemDefault()));
        String row = line(r.getOutput(), "msg-0001");
        assertThat(row).contains(expectedTime, "systemError", "SenderA", "SI_A_Out", "ReceiverB", "SI_B_In", KEY);
        assertThat(row.indexOf("SenderA")).isLessThan(row.indexOf("ReceiverB"));
        assertThat(all(r)).contains("1 message(s)");
        assertThat(r.getErrorOutput()).doesNotContain("more messages exist");

        Element op = operation(po.lastRequest(), "getMessageList");
        assertThat(child(op, OP, "maxMessages").getTextContent()).isEqualTo("200");
        Element filter = child(op, OP, "filter");
        ZoneId zone = ZoneId.systemDefault();
        assertThat(OffsetDateTime.parse(child(filter, WS, "fromTime").getTextContent()).toInstant())
                .isEqualTo(LocalDateTime.parse("2026-11-10T00:00").atZone(zone).toInstant());
        assertThat(OffsetDateTime.parse(child(filter, WS, "toTime").getTextContent()).toInstant())
                .isEqualTo(LocalDateTime.parse("2026-11-10T12:00").atZone(zone).toInstant());
        assertThat(child(filter, WS, "onlyFaultyMessages").getTextContent()).isEqualTo("false");
    }

    @Test
    void searchDefaultsToTheLast24Hours(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);
        OffsetDateTime before = OffsetDateTime.now();

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1");

        OffsetDateTime after = OffsetDateTime.now();
        assertThat(r.exitCode()).as(all(r)).isZero();
        Element filter = child(operation(po.lastRequest(), "getMessageList"), OP, "filter");
        OffsetDateTime from = OffsetDateTime.parse(child(filter, WS, "fromTime").getTextContent());
        OffsetDateTime to = OffsetDateTime.parse(child(filter, WS, "toTime").getTextContent());
        assertThat(from.toInstant()).isBetween(before.minusHours(24).minusSeconds(1).toInstant(),
                after.minusHours(24).plusSeconds(1).toInstant());
        assertThat(to.toInstant()).isBetween(before.minusSeconds(1).toInstant(), after.plusSeconds(1).toInstant());
    }

    @Test
    void searchOptionsAreSentInTheFilter(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1", "--faulty", "--status", "systemError",
                "--sender", "SenderA", "--receiver", "ReceiverB", "--interface", "SI_A_Out",
                "--interface-ns", "urn:test:a", "-l", "5");

        assertThat(r.exitCode()).as(all(r)).isZero();
        Element op = operation(po.lastRequest(), "getMessageList");
        assertThat(child(op, OP, "maxMessages").getTextContent()).isEqualTo("5");
        Element filter = child(op, OP, "filter");
        assertThat(child(filter, WS, "onlyFaultyMessages").getTextContent()).isEqualTo("true");
        assertThat(filter.getTextContent()).contains("systemError", "SenderA", "ReceiverB", "SI_A_Out", "urn:test:a");
    }

    @Test
    void searchWithMoreMessagesWarnsOnStderr(QuarkusMainLauncher launcher) {
        po.respondSoap(searchResponse("getMessageList", 1, true,
                message("msg-0001", KEY, "OUTBOUND", "systemError", "ReceiverB")));

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(r.getErrorOutput()).contains("more messages exist", "--limit");
    }

    @Test
    void searchJsonIsTheModel(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1", "--json");

        assertThat(r.exitCode()).as(all(r)).isZero();
        SearchResult expected = new MonitoringClient(po.system).getMessageList(MessageFilter.builder().build(), 1);
        assertThat(Json.mapper().readTree(r.getOutput())).isEqualTo(Json.mapper().readTree(Json.write(expected)));
        assertThat(Json.mapper().readTree(r.getOutput()).get("rawResponse").asString())
                .isEqualTo(FakeMonitoringPo.envelope(ONE_MESSAGE));
    }

    @Test
    void searchWithoutSystemUsesMonitoringProperties(QuarkusMainLauncher launcher) throws IOException {
        Files.writeString(home.resolve(".po-xray").resolve("monitoring.properties"), "system=QA1\n");
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(sent("getMessageList")).hasSize(1);
        assertThat(r.getOutput()).contains("msg-0001");
    }

    @Test
    void searchWithoutAnySystemNamesAllThreeSources(QuarkusMainLauncher launcher) {

        LaunchResult r = launcher.launch("messages", "search");

        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(r.getErrorOutput()).contains("--system", "POXRAY_SYSTEM", "monitoring.properties");
        assertThat(po.requests()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ id resolution

    @Test
    void logWithAmbiguousIdListsCandidatesAndExits2(QuarkusMainLauncher launcher) {
        po.respondSoap(searchResponse("getMessagesByIDs", 2, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB"),
                message("msg-0001", KEY2, "INBOUND", "systemError", "ReceiverC")));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", "msg-0001");

        assertThat(r.exitCode()).as(all(r)).isEqualTo(2);
        String first = line(r.getOutput(), KEY);
        String second = line(r.getOutput(), KEY2);
        assertThat(first).contains("OUTBOUND", "ReceiverB", "success");
        assertThat(second).contains("INBOUND", "ReceiverC", "systemError");
        assertThat(r.getErrorOutput()).contains("--key", "--direction");
        assertThat(sent("getLogEntries")).isEmpty(); // never a silently picked key
    }

    @Test
    void ambiguityShowsAKeylessCandidateAsNoKey(QuarkusMainLauncher launcher) {
        po.respondSoap(searchResponse("getMessagesByIDs", 2, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB"),
                message("msg-0001", null, "INBOUND", "holding", "ReceiverC")));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", "msg-0001");

        assertThat(r.exitCode()).as(all(r)).isEqualTo(2);
        assertThat(line(r.getOutput(), "(no key)")).contains("INBOUND", "ReceiverC", "holding");
        assertThat(line(r.getOutput(), KEY)).contains("OUTBOUND");
        assertThat(sent("getLogEntries")).isEmpty();
    }

    @Test
    void logWithDirectionResolvesToOneKeyAndPrintsEntries(QuarkusMainLauncher launcher) {
        po.respondPerOperation(op -> op.equals("getMessagesByIDs")
                ? searchResponse(op, 2, false, message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB"),
                        message("msg-0001", KEY2, "INBOUND", "systemError", "ReceiverC"))
                : valueResponse(op, "<d:AuditLogEntryData><d:timeStamp>" + START + "</d:timeStamp>"
                        + "<d:textKey>MP_ENTER</d:textKey><d:status>success</d:status>"
                        + "<d:localizedText>Message entered the adapter</d:localizedText></d:AuditLogEntryData>"));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", "msg-0001", "--direction", "INBOUND");

        assertThat(r.exitCode()).as(all(r)).isZero();
        List<FakeMonitoringPo.Request> logs = sent("getLogEntries");
        assertThat(logs).hasSize(1);
        assertThat(child(operation(logs.getFirst(), "getLogEntries"), OP, "messageKey").getTextContent())
                .isEqualTo(KEY2);
        assertThat(r.getOutput()).contains("Message entered the adapter");
    }

    @Test
    void logWithAKeyNeedsNoLookup(QuarkusMainLauncher launcher) {
        po.respondSoap(valueResponse("getLogEntries", ""));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", KEY);

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(sent("getMessagesByIDs")).isEmpty();
        assertThat(child(operation(po.lastRequest(), "getLogEntries"), OP, "messageKey").getTextContent())
                .isEqualTo(KEY);
    }

    @Test
    void unknownIdPrintsTheReasonAndExits1(QuarkusMainLauncher launcher) {
        po.respondSoap(searchResponse("getMessagesByIDs", 0, false));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", "msg-9999");

        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(r.getErrorOutput()).contains("no message with this ID");
        assertThat(sent("getLogEntries")).isEmpty();
    }

    @Test
    void monitoringFailureIsOneLineWithoutStackTrace(QuarkusMainLauncher launcher) {
        po.respond(403, "text/html", "<html>Forbidden</html>");

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1");

        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(r.getErrorOutput()).contains("SAP_XI_API_DISPLAY_J2EE").doesNotContain("\tat ")
                .doesNotContain("at io.poxray.").doesNotContain(FakeMonitoringPo.PASSWORD);
    }

    // ------------------------------------------------------------------------------------------ payload

    @Test
    void payloadPrintsSelectedPart(QuarkusMainLauncher launcher) {
        servePayload(multipart(PAYLOAD));

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY);

        assertThat(r.exitCode()).as(all(r)).isZero();
        // default: the first part that is not the XI header
        assertThat(r.getOutput()).contains(PAYLOAD).doesNotContain("SOAP:Header");
        assertThat(r.getErrorOutput()).contains("soap-1@test", "payload-1@test", "text/xml", "application/xml",
                String.valueOf(PAYLOAD.getBytes(StandardCharsets.UTF_8).length));
        assertThat(sent("getMessageBytesJavaLangStringBoolean")).hasSize(1);
    }

    @Test
    void payloadPartOptionSelectsAnotherPart(QuarkusMainLauncher launcher) {
        servePayload(multipart(PAYLOAD, "<order><no>B-2</no></order>"));

        LaunchResult header = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--part", "0");
        LaunchResult second = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--part", "2");

        assertThat(header.exitCode()).as(all(header)).isZero();
        assertThat(header.getOutput()).contains("SOAP:Header").doesNotContain("A-1");
        assertThat(second.exitCode()).as(all(second)).isZero();
        assertThat(second.getOutput()).contains("<order><no>B-2</no></order>").doesNotContain("A-1");
    }

    @Test
    void payloadWithOnlyTheHeaderPartPrintsIt(QuarkusMainLauncher launcher) {
        servePayload(multipart());

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY);

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(r.getOutput()).contains("SOAP:Header");
    }

    @Test
    void nonMultipartPayloadPrintsTheBytesAsText(QuarkusMainLauncher launcher) {
        servePayload(PAYLOAD.getBytes(StandardCharsets.UTF_8));

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY);

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(r.getOutput()).contains(PAYLOAD);
        assertThat(r.getErrorOutput()).contains("not multipart");
    }

    @Test
    void payloadVersionAndLoggedSelectTheOperation(QuarkusMainLauncher launcher) {
        servePayload(PAYLOAD.getBytes(StandardCharsets.UTF_8));

        LaunchResult version = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--version", "3");
        LaunchResult logged = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--logged", "BI");

        assertThat(version.exitCode()).as(all(version)).isZero();
        assertThat(logged.exitCode()).as(all(logged)).isZero();
        List<FakeMonitoringPo.Request> v = sent("getMessageBytesJavaLangStringIntBoolean");
        assertThat(v).hasSize(1);
        assertThat(child(operation(v.getFirst(), "getMessageBytesJavaLangStringIntBoolean"), OP, "version")
                .getTextContent()).isEqualTo("3");
        List<FakeMonitoringPo.Request> l = sent("getLoggedMessageBytes");
        assertThat(l).hasSize(1);
        assertThat(child(operation(l.getFirst(), "getLoggedMessageBytes"), OP, "version").getTextContent())
                .isEqualTo("BI");
    }

    // ------------------------------------------------------------------------------------------ saving

    /** A fake work tree: {@code <tmp>/repo/.git}, {@code src/} with one file, {@code local/}. */
    private static Path repo(Path tmp) throws IOException {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Files.createDirectories(repo.resolve("local"));
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src").resolve("Existing.java"), "class Existing {}");
        return repo;
    }

    @Test
    void saveIntoSrcIsRefusedAndNothingWritten(QuarkusMainLauncher launcher, @TempDir Path tmp) throws IOException {
        Path repo = repo(tmp);
        Map<String, String> before = ExportMetadataFixtures.snapshot(repo);
        servePayload(multipart(PAYLOAD));

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--save",
                repo.resolve("src").resolve("out").toString());

        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(r.getErrorOutput()).contains("refusing to save customer data");
        assertThat(ExportMetadataFixtures.snapshot(repo)).isEqualTo(before);
    }

    @Test
    void saveIntoLocalWritesTheArchive(QuarkusMainLauncher launcher, @TempDir Path tmp) throws IOException {
        Path out = repo(tmp).resolve("local").resolve("messages");
        byte[] bytes = multipart(PAYLOAD);
        servePayload(bytes);

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--save", out.toString());

        assertThat(r.exitCode()).as(all(r)).isZero();
        List<Path> dirs;
        try (Stream<Path> s = Files.list(out)) {
            dirs = s.toList();
        }
        assertThat(dirs).hasSize(1);
        Path dir = dirs.getFirst();
        assertThat(Files.readString(dir.resolve("response.xml"), StandardCharsets.UTF_8))
                .isEqualTo(FakeMonitoringPo.envelope(valueResponse("getMessageBytesJavaLangStringBoolean",
                        Base64.getEncoder().encodeToString(bytes))));
        assertThat(dir.resolve("model.json")).isRegularFile();
        assertThat(Files.readAllBytes(dir.resolve("payload.bin"))).isEqualTo(bytes);
    }

    /** Top-level entries of the working directory, except the build output. */
    private static List<String> cwdEntries() throws IOException {
        try (Stream<Path> s = Files.list(Path.of("").toAbsolutePath())) {
            return s.map(p -> p.getFileName().toString()).filter(n -> !n.equals("target")).sorted().toList();
        }
    }

    @Test
    void noSaveWritesNothing(QuarkusMainLauncher launcher) throws IOException {
        Map<String, String> homeBefore = ExportMetadataFixtures.snapshot(home);
        List<String> cwdBefore = cwdEntries();
        po.respondPerOperation(op -> switch (op) {
            case "getMessageList" -> ONE_MESSAGE;
            case "getLogEntries" -> valueResponse(op, "");
            default -> valueResponse(op, Base64.getEncoder().encodeToString(multipart(PAYLOAD)));
        });

        LaunchResult search = launcher.launch("messages", "search", "-s", "QA1");
        LaunchResult log = launcher.launch("messages", "log", "-s", "QA1", KEY);
        LaunchResult payload = launcher.launch("messages", "payload", "-s", "QA1", KEY);

        assertThat(List.of(search, log, payload)).allSatisfy(r -> assertThat(r.exitCode()).as(all(r)).isZero());
        assertThat(ExportMetadataFixtures.snapshot(home)).isEqualTo(homeBefore);
        assertThat(cwdEntries()).isEqualTo(cwdBefore);
    }

    // ------------------------------------------------------------------------------------------ lookup and help

    @Test
    void lookupServicesPrintsOneValuePerLine(QuarkusMainLauncher launcher) {
        po.respondSoap(valueResponse("getServices", "<j:String>SenderA</j:String><j:String>ReceiverB</j:String>"));

        LaunchResult r = launcher.launch("messages", "lookup", "services", "-s", "QA1");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(r.getOutput().lines().map(String::strip)).contains("SenderA", "ReceiverB");
        assertThat(sent("getServices")).hasSize(1);
    }

    @Test
    void messagesHelpListsTheElevenSubcommands(QuarkusMainLauncher launcher) {
        LaunchResult r = launcher.launch("messages", "--help");

        assertThat(all(r)).contains("search", "uds-search", "get", "successors", "predecessor", "log", "payload",
                "uds-attributes", "uds-filters", "uds-extractors", "lookup");
        assertThat(po.requests()).isEmpty();
    }

    // ------------------------------------------------------------------------------------------ review round 2

    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";

    /** The texts of the element children of one child of the last request's operation element. */
    private List<String> sentItems(String operation, String parameter) {
        return FakeMonitoringPo.children(child(operation(po.lastRequest(), operation), OP, parameter)).stream()
                .map(Element::getTextContent).toList();
    }

    private static final String EMPTY_UDS = searchResponse("getUserDefinedSearchMessages", 0, false);

    @Test
    void repeatedStatusOptionSendsEveryValueInOrder(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1", "--status", "systemError",
                "--status", "holding");

        assertThat(r.exitCode()).as(all(r)).isZero();
        Element filter = child(operation(po.lastRequest(), "getMessageList"), OP, "filter");
        assertThat(FakeMonitoringPo.children(child(filter, WS, "statuses"))).extracting(Element::getTextContent)
                .containsExactly("systemError", "holding");
    }

    @Test
    void commaSeparatedStatusIsSplit(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1", "--status", "systemError,holding");

        assertThat(r.exitCode()).as(all(r)).isZero();
        Element filter = child(operation(po.lastRequest(), "getMessageList"), OP, "filter");
        assertThat(FakeMonitoringPo.children(child(filter, WS, "statuses"))).extracting(Element::getTextContent)
                .containsExactly("systemError", "holding");
    }

    /** name/value pairs of the attributes of the last getUserDefinedSearchMessages request. */
    private List<String> sentAttributes() {
        Element attributes = child(operation(po.lastRequest(), "getUserDefinedSearchMessages"), OP, "attributes");
        return FakeMonitoringPo.children(attributes).stream()
                .map(a -> child(a, WS, "name").getTextContent() + "=" + child(a, WS, "value").getTextContent())
                .toList();
    }

    @Test
    void repeatedAttrOptionSendsEveryAttributeInOrder(QuarkusMainLauncher launcher) {
        po.respondSoap(EMPTY_UDS);

        LaunchResult r = launcher.launch("messages", "uds-search", "-s", "QA1", "--attr", "OrderNo=A-1",
                "--attr", "Plant=P-2");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(sentAttributes()).containsExactly("OrderNo=A-1", "Plant=P-2");
        assertThat(child(operation(po.lastRequest(), "getUserDefinedSearchMessages"), OP, "operator")
                .getTextContent()).isEqualTo("AND");
    }

    @Test
    void commaSeparatedAttrIsSplit(QuarkusMainLauncher launcher) {
        po.respondSoap(EMPTY_UDS);

        LaunchResult r = launcher.launch("messages", "uds-search", "-s", "QA1", "--attr", "OrderNo=A-1,Plant=P-2",
                "--operator", "or");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(sentAttributes()).containsExactly("OrderNo=A-1", "Plant=P-2");
        assertThat(child(operation(po.lastRequest(), "getUserDefinedSearchMessages"), OP, "operator")
                .getTextContent()).isEqualTo("OR");
    }

    @Test
    void keyOptionWithoutIdSkipsTheLookup(QuarkusMainLauncher launcher) {
        po.respondSoap(valueResponse("getLogEntries", ""));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", "--key", KEY);

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(sent("getMessagesByIDs")).isEmpty();
        assertThat(child(operation(po.lastRequest(), "getLogEntries"), OP, "messageKey").getTextContent())
                .isEqualTo(KEY);
    }

    @Test
    void payloadDefaultIsPartOneAmongSeveral(QuarkusMainLauncher launcher) {
        servePayload(multipart(PAYLOAD, "<order><no>B-2</no></order>"));

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY);

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(r.getOutput()).contains(PAYLOAD).doesNotContain("B-2").doesNotContain("SOAP:Header");
    }

    /** A multipart message without an XI header part: each given part as application/xml. */
    private static byte[] withoutHeader(String... parts) {
        StringBuilder b = new StringBuilder("Content-Type: multipart/related; boundary=" + BOUNDARY + "\r\n\r\n");
        for (int i = 0; i < parts.length; i++) {
            b.append("--").append(BOUNDARY).append("\r\ncontent-id:<part-").append(i + 1)
                    .append("@test>\r\ncontent-type:application/xml\r\n\r\n").append(parts[i]).append("\r\n");
        }
        b.append("--").append(BOUNDARY).append("--\r\n");
        return b.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void payloadDefaultIsTheFirstPartThatIsNotAnXiHeader(QuarkusMainLauncher launcher) {
        String attachment = "<attachment><no>B-2</no></attachment>";
        servePayload(withoutHeader(PAYLOAD, attachment));
        LaunchResult main = launcher.launch("messages", "payload", "-s", "QA1", KEY);
        servePayload(multipart(PAYLOAD));
        LaunchResult afterHeader = launcher.launch("messages", "payload", "-s", "QA1", KEY);

        assertThat(main.exitCode()).as(all(main)).isZero();
        assertThat(main.getOutput()).contains(PAYLOAD).doesNotContain("B-2");
        assertThat(afterHeader.exitCode()).as(all(afterHeader)).isZero();
        assertThat(afterHeader.getOutput()).contains(PAYLOAD).doesNotContain("SOAP:Header");
    }

    @Test
    void payloadPartOutOfRangeExits1(QuarkusMainLauncher launcher) {
        servePayload(multipart(PAYLOAD));

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--part", "5");

        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(r.getOutput()).doesNotContain(PAYLOAD);
    }

    @Test
    void payloadVersionWithLoggedExits1(QuarkusMainLauncher launcher) {
        servePayload(multipart(PAYLOAD));

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--version", "3", "--logged", "BI");

        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(sent("getMessageBytesJavaLangStringIntBoolean")).isEmpty();
        assertThat(sent("getLoggedMessageBytes")).isEmpty();
    }

    @Test
    void nilPayloadPrintsOnlyTheNote(QuarkusMainLauncher launcher) {
        po.respondSoap("<m:getMessageBytesJavaLangStringBooleanResponse xmlns:m=\"" + OP + "\" xmlns:xsi=\"" + XSI
                + "\"><m:Response xsi:nil=\"true\"/></m:getMessageBytesJavaLangStringBooleanResponse>");

        LaunchResult r = launcher.launch("messages", "payload", "-s", "QA1", KEY);

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(r.getOutput()).isBlank();
        assertThat(r.getErrorOutput()).contains("no payload");
    }

    @Test
    void logWithoutOlderThanSendsNilAndLimitIsMaxResults(QuarkusMainLauncher launcher) {
        po.respondSoap(valueResponse("getLogEntries", ""));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", KEY, "-l", "7");

        assertThat(r.exitCode()).as(all(r)).isZero();
        Element op = operation(po.lastRequest(), "getLogEntries");
        assertThat(child(op, OP, "maxResults").getTextContent()).isEqualTo("7");
        assertThat(child(op, OP, "olderThan").getAttributeNS(XSI, "nil")).isEqualTo("true");
    }

    @Test
    void logOlderThanIsSent(QuarkusMainLauncher launcher) {
        po.respondSoap(valueResponse("getLogEntries", ""));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", KEY, "--older-than", "2026-11-10T08:00");

        assertThat(r.exitCode()).as(all(r)).isZero();
        String sentTime = child(operation(po.lastRequest(), "getLogEntries"), OP, "olderThan").getTextContent();
        assertThat(OffsetDateTime.parse(sentTime).toInstant())
                .isEqualTo(LocalDateTime.parse("2026-11-10T08:00").atZone(ZoneId.systemDefault()).toInstant());
    }

    @Test
    void logLinesShowTimeStatusAndText(QuarkusMainLauncher launcher) {
        po.respondSoap(valueResponse("getLogEntries", "<d:AuditLogEntryData><d:timeStamp>" + START + "</d:timeStamp>"
                + "<d:textKey>MP_ENTER</d:textKey><d:status>success</d:status>"
                + "<d:localizedText>Message entered the adapter</d:localizedText></d:AuditLogEntryData>"
                + "<d:AuditLogEntryData><d:textKey>MP_RETRY</d:textKey><d:params><j:String>3</j:String></d:params>"
                + "</d:AuditLogEntryData>"));

        LaunchResult r = launcher.launch("messages", "log", "-s", "QA1", KEY);

        assertThat(r.exitCode()).as(all(r)).isZero();
        String time = TABLE_TIME.format(OffsetDateTime.parse(START).atZoneSameInstant(ZoneId.systemDefault()));
        assertThat(r.getOutput().lines()).contains(time + "  success  Message entered the adapter",
                "-  -  MP_RETRY [3]");
    }

    @Test
    void getWithKeysUsesGetMessagesByKeys(QuarkusMainLauncher launcher) {
        po.respondSoap(searchResponse("getMessagesByKeys", 1, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB")));

        LaunchResult r = launcher.launch("messages", "get", "-s", "QA1", KEY, KEY2);

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(sent("getMessagesByIDs")).isEmpty();
        assertThat(sentItems("getMessagesByKeys", "filter")).containsExactly(KEY, KEY2);
        assertThat(r.getOutput()).contains("msg-0001");
    }

    @Test
    void getWithIdsUsesGetMessagesByIds(QuarkusMainLauncher launcher) {
        po.respondSoap(searchResponse("getMessagesByIDs", 1, false,
                message("msg-0001", KEY, "OUTBOUND", "success", "ReceiverB")));

        LaunchResult r = launcher.launch("messages", "get", "-s", "QA1", "msg-0001", "msg-0002");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(sent("getMessagesByKeys")).isEmpty();
        assertThat(sentItems("getMessagesByIDs", "messageIds")).containsExactly("msg-0001", "msg-0002");
    }

    @Test
    void getMixingIdsAndKeysExits1(QuarkusMainLauncher launcher) {
        po.respondSoap(searchResponse("getMessagesByIDs", 0, false));

        LaunchResult r = launcher.launch("messages", "get", "-s", "QA1", "msg-0001", KEY);

        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(po.requests()).isEmpty();
    }

    @Test
    void emptyPredecessorSaysNoPredecessor(QuarkusMainLauncher launcher) {
        po.respondSoap("<m:getPredecessorMessageIdResponse xmlns:m=\"" + OP + "\" xmlns:xsi=\"" + XSI
                + "\"><m:Response xsi:nil=\"true\"/></m:getPredecessorMessageIdResponse>");

        LaunchResult r = launcher.launch("messages", "predecessor", "-s", "QA1", "msg-0002", "--direction", "INBOUND");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(r.getErrorOutput()).contains("No predecessor");
        assertThat(sent("getPredecessorMessageId")).hasSize(1);
    }

    @Test
    void unknownLookupKindExits1(QuarkusMainLauncher launcher) {
        LaunchResult r = launcher.launch("messages", "lookup", "planets", "-s", "QA1");

        assertThat(r.exitCode()).isEqualTo(1);
        assertThat(r.getErrorOutput()).contains("planets");
        assertThat(po.requests()).isEmpty();
    }

    @Test
    void errorCodesLookupRequiresLabel(QuarkusMainLauncher launcher) {
        po.respondSoap(valueResponse("getErrorCodes", "<j:String>CODE_A</j:String>"));

        LaunchResult without = launcher.launch("messages", "lookup", "error-codes", "-s", "QA1");
        assertThat(without.exitCode()).isEqualTo(1);
        assertThat(without.getErrorOutput()).contains("--label");
        assertThat(po.requests()).isEmpty();

        LaunchResult with = launcher.launch("messages", "lookup", "error-codes", "-s", "QA1", "--label", "1001");
        assertThat(with.exitCode()).as(all(with)).isZero();
        assertThat(child(operation(po.lastRequest(), "getErrorCodes"), OP, "errorLabelID").getTextContent())
                .isEqualTo("1001");
        assertThat(with.getOutput()).contains("CODE_A");
    }

    @Test
    void statusDetailsWithoutCodesUsesAllAvailable(QuarkusMainLauncher launcher) {
        po.respondPerOperation(op -> valueResponse(op, "<w:StatusDetail><w:errorLabelID>1001</w:errorLabelID>"
                + "<w:description>Mapping error</w:description></w:StatusDetail>"));

        LaunchResult every = launcher.launch("messages", "lookup", "status-details", "-s", "QA1");
        LaunchResult some = launcher.launch("messages", "lookup", "status-details", "-s", "QA1", "--codes",
                "CODE_A,CODE_B");

        assertThat(every.exitCode()).as(all(every)).isZero();
        assertThat(some.exitCode()).as(all(some)).isZero();
        assertThat(sent("getAllAvailableStatusDetails")).hasSize(1);
        assertThat(sent("getStatusDetails")).hasSize(1);
        assertThat(sentItems("getStatusDetails", "errorCodes")).containsExactly("CODE_A", "CODE_B");
        assertThat(every.getOutput()).contains("1001", "Mapping error");
    }

    @Test
    void iflowsLanguageDefaultsToEn(QuarkusMainLauncher launcher) {
        po.respondSoap(valueResponse("getIntegrationFlows", ""));

        LaunchResult byDefault = launcher.launch("messages", "lookup", "iflows", "-s", "QA1");
        String defaultLanguage = child(operation(po.lastRequest(), "getIntegrationFlows"), OP, "language")
                .getTextContent();
        LaunchResult german = launcher.launch("messages", "lookup", "iflows", "-s", "QA1", "--lang", "de");

        assertThat(byDefault.exitCode()).as(all(byDefault)).isZero();
        assertThat(german.exitCode()).as(all(german)).isZero();
        assertThat(defaultLanguage).isEqualTo("en");
        assertThat(child(operation(po.lastRequest(), "getIntegrationFlows"), OP, "language").getTextContent())
                .isEqualTo("de");
    }

    @Test
    void unmappedValuesPointToJson(QuarkusMainLauncher launcher) {
        po.respondSoap("<m:getMessageListResponse xmlns:m=\"" + OP + "\" xmlns:w=\"" + WS + "\"><m:Response>"
                + "<w:list/><w:number>abc</w:number><w:warning>false</w:warning>"
                + "<w:displayPermissionWarning>false</w:displayPermissionWarning></m:Response>"
                + "</m:getMessageListResponse>");

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(r.getErrorOutput()).contains("otherFields", "--json");
    }

    @Test
    void missingValuesAreShownAsDash(QuarkusMainLauncher launcher) {
        po.respondSoap(searchResponse("getMessageList", 1, false,
                "<w:AdapterFrameworkData><w:messageID>msg-0003</w:messageID></w:AdapterFrameworkData>"));

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1");

        assertThat(r.exitCode()).as(all(r)).isZero();
        String row = line(r.getOutput(), "msg-0003");
        assertThat(row).startsWith("-  -  -/-").endsWith("msg-0003  -");
    }

    @Test
    void refusedSaveTargetSendsNoRequest(QuarkusMainLauncher launcher, @TempDir Path tmp) throws IOException {
        Path repo = repo(tmp);
        servePayload(multipart(PAYLOAD));

        LaunchResult payload = launcher.launch("messages", "payload", "-s", "QA1", KEY, "--save",
                repo.resolve("src").toString());
        LaunchResult log = launcher.launch("messages", "log", "-s", "QA1", "msg-0001", "--save", repo.toString());

        assertThat(payload.exitCode()).isEqualTo(1);
        assertThat(log.exitCode()).isEqualTo(1);
        assertThat(po.requests()).isEmpty();
    }

    @Test
    void udsSearchWithBadOperatorOrAttrExits1(QuarkusMainLauncher launcher) {
        po.respondSoap(EMPTY_UDS);

        LaunchResult badOperator = launcher.launch("messages", "uds-search", "-s", "QA1", "--attr", "OrderNo=A-1",
                "--operator", "XOR");
        LaunchResult badAttr = launcher.launch("messages", "uds-search", "-s", "QA1", "--attr", "OrderNo");

        assertThat(badOperator.exitCode()).isEqualTo(1);
        assertThat(badOperator.getErrorOutput()).contains("XOR");
        assertThat(badAttr.exitCode()).isEqualTo(1);
        assertThat(badAttr.getErrorOutput()).contains("OrderNo");
        assertThat(po.requests()).isEmpty();
    }

    @Test
    void unreadableTimeExits1NamingTheValue(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult from = launcher.launch("messages", "search", "-s", "QA1", "--from", "yesterday");
        LaunchResult to = launcher.launch("messages", "search", "-s", "QA1", "--to", "soon");
        LaunchResult olderThan = launcher.launch("messages", "log", "-s", "QA1", KEY, "--older-than", "later");

        assertThat(from.exitCode()).isEqualTo(1);
        assertThat(from.getErrorOutput()).contains("yesterday");
        assertThat(to.exitCode()).isEqualTo(1);
        assertThat(to.getErrorOutput()).contains("soon");
        assertThat(olderThan.exitCode()).isEqualTo(1);
        assertThat(olderThan.getErrorOutput()).contains("later");
        assertThat(po.requests()).isEmpty();
    }

    @Test
    void credentialsComeFromTheSystemFileNotTheMachineEnvironment(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(po.lastRequest().header("Authorization")).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString((FakeMonitoringPo.USER + ":" + FakeMonitoringPo.PASSWORD)
                        .getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void systemNameComesFromTheMonitoringEnvironment(QuarkusMainLauncher launcher) {
        // proves the test's override reaches the CLI bean
        EmptyMonitoringEnvironment.override(Map.of("POXRAY_SYSTEM", "QA1"));
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(sent("getMessageList")).hasSize(1);
    }

    @Test
    void systemCredentialsComeFromTheMonitoringEnvironment(QuarkusMainLauncher launcher) {
        EmptyMonitoringEnvironment.override(Map.of("POXRAY_QA1_PASSWORD", "fromBean"));
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1");

        assertThat(r.exitCode()).as(all(r)).isZero();
        assertThat(po.lastRequest().header("Authorization")).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString((FakeMonitoringPo.USER + ":fromBean").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void searchOptionsFillTheirOwnFilterFields(QuarkusMainLauncher launcher) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1", "--faulty", "--status", "systemError",
                "--status", "holding", "--sender", "SenderA", "--receiver", "ReceiverB", "--interface", "SI_A_Out",
                "--interface-ns", "urn:test:a", "-l", "5");

        assertThat(r.exitCode()).as(all(r)).isZero();
        Element op = operation(po.lastRequest(), "getMessageList");
        assertThat(child(op, OP, "maxMessages").getTextContent()).isEqualTo("5");
        Element filter = child(op, OP, "filter");
        assertThat(child(filter, WS, "senderName").getTextContent()).isEqualTo("SenderA");
        assertThat(child(filter, WS, "receiverName").getTextContent()).isEqualTo("ReceiverB");
        assertThat(child(filter, WS, "onlyFaultyMessages").getTextContent()).isEqualTo("true");
        assertThat(FakeMonitoringPo.children(child(filter, WS, "statuses"))).extracting(Element::getTextContent)
                .containsExactly("systemError", "holding");
        Element iface = child(filter, WS, "interface");
        assertThat(child(iface, FakeMonitoringPo.DATA, "name").getTextContent()).isEqualTo("SI_A_Out");
        assertThat(child(iface, FakeMonitoringPo.DATA, "namespace").getTextContent()).isEqualTo("urn:test:a");
    }

    @Test
    void searchDoesNotAcceptSave(QuarkusMainLauncher launcher, @TempDir Path tmp) {
        po.respondSoap(ONE_MESSAGE);

        LaunchResult r = launcher.launch("messages", "search", "-s", "QA1", "--save", tmp.toString());

        assertThat(r.exitCode()).isNotZero();
        assertThat(po.requests()).isEmpty();
    }
}
