package io.poxray.monitoring;

import static io.poxray.monitoring.FakeMonitoringPo.DATA;
import static io.poxray.monitoring.FakeMonitoringPo.JL;
import static io.poxray.monitoring.FakeMonitoringPo.OP;
import static io.poxray.monitoring.FakeMonitoringPo.SOAP_ENV;
import static io.poxray.monitoring.FakeMonitoringPo.WS;
import static io.poxray.monitoring.FakeMonitoringPo.child;
import static io.poxray.monitoring.FakeMonitoringPo.childNames;
import static io.poxray.monitoring.FakeMonitoringPo.children;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

import io.poxray.po.PoException;
import io.poxray.po.PoSystem;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** MonitoringClient.getMessageList against a local fake AdapterMessageMonitoring service (synthetic names only). */
class MonitoringClientSearchTest {

    /** xs:dateTime lexical form with seconds and an explicit offset. */
    private static final String DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})";

    /** Two messages; prefixes deliberately unusual, DATA partly as default namespace, pretty-printed. */
    private static final String TWO_MESSAGES = """
            <m:getMessageListResponse xmlns:m="urn:AdapterMessageMonitoringVi"
                xmlns:w="urn:com.sap.aii.mdt.server.adapterframework.ws"
                xmlns:d="urn:com.sap.aii.mdt.api.data" xmlns:j="urn:java/lang">
              <m:Response>
                <w:date>2026-11-10T09:00:00.000+01:00</w:date>
                <w:list>
                  <w:AdapterFrameworkData>
                    <w:applicationComponent>SenderA</w:applicationComponent>
                    <w:businessMessage><d:value>true</d:value></w:businessMessage>
                    <w:cancelable><d:value>false</d:value></w:cancelable>
                    <w:connectionName>ConnA</w:connectionName>
                    <w:direction>OUTBOUND</w:direction>
                    <w:editable><d:value>false</d:value></w:editable>
                    <w:endTime>2026-11-10T08:15:31.623+01:00</w:endTime>
                    <w:errorCategory>CategoryA</w:errorCategory>
                    <w:errorCode>CODE_A</w:errorCode>
                    <w:interface xmlns="urn:com.sap.aii.mdt.api.data">
                      <name>SI_A</name>
                      <namespace>urn:test:a</namespace>
                      <senderParty>PartyA</senderParty>
                      <senderComponent>SenderA</senderComponent>
                      <receiverParty>PartyB</receiverParty>
                      <receiverComponent>ReceiverB</receiverComponent>
                    </w:interface>
                    <w:isPersistent>true</w:isPersistent>
                    <w:messageID>msg-0001</w:messageID>
                    <w:messageKey>key-0001</w:messageKey>
                    <w:messageType>Send</w:messageType>
                    <w:nodeId>0</w:nodeId>
                    <w:protocol>XI</w:protocol>
                    <w:qualityOfService>EO</w:qualityOfService>
                    <w:receiverInterface><d:name>SI_B_In</d:name><d:namespace>urn:test:b</d:namespace></w:receiverInterface>
                    <w:receiverName>ReceiverB</w:receiverName>
                    <w:receiverParty><d:agency>AgencyB</d:agency><d:name>PartyB</d:name><d:schema>SchemaB</d:schema></w:receiverParty>
                    <w:restartable><d:value>true</d:value></w:restartable>
                    <w:retries>3</w:retries>
                    <w:senderInterface><d:name>SI_A_Out</d:name><d:namespace>urn:test:a</d:namespace></w:senderInterface>
                    <w:senderName>SenderA</w:senderName>
                    <w:senderParty><d:agency>AgencyA</d:agency><d:name>PartyA</d:name><d:schema>SchemaA</d:schema></w:senderParty>
                    <w:startTime>2026-11-10T08:15:30.123+01:00</w:startTime>
                    <w:status>systemError</w:status>
                    <w:version>0</w:version>
                    <w:wasEdited><d:value>false</d:value></w:wasEdited>
                    <w:businessAttributes>
                      <w:BusinessAttribute><w:name>OrderNo</w:name><w:value>A-1</w:value></w:BusinessAttribute>
                      <w:BusinessAttribute><w:name>Greeting</w:name><w:value>Grüße</w:value></w:BusinessAttribute>
                    </w:businessAttributes>
                    <w:payloadPermissionWarning><d:value>false</d:value></w:payloadPermissionWarning>
                    <w:errorLabel>1001</w:errorLabel>
                    <w:parentID>parent-0001</w:parentID>
                    <w:duration><d:duration>1500</d:duration></w:duration>
                    <w:size>2048</w:size>
                    <w:rootID>root-0001</w:rootID>
                    <w:sequenceID>seq-0001</w:sequenceID>
                    <w:logLocations><j:String>BI</j:String><j:String>AM</j:String></w:logLocations>
                    <w:futureField>x</w:futureField>
                  </w:AdapterFrameworkData>
                  <w:AdapterFrameworkData>
                    <w:direction>INBOUND</w:direction>
                    <w:messageID>msg-0002</w:messageID>
                    <w:messageKey>key-0002</w:messageKey>
                    <w:senderName>SenderA</w:senderName>
                    <w:status>success</w:status>
                    <w:errorLabel>0</w:errorLabel>
                    <w:futureBlock><w:part>p1</w:part></w:futureBlock>
                  </w:AdapterFrameworkData>
                </w:list>
                <w:number>2</w:number>
                <w:warning>false</w:warning>
                <w:displayPermissionWarning>false</w:displayPermissionWarning>
              </m:Response>
            </m:getMessageListResponse>
            """;

    private FakeMonitoringPo po;

    @BeforeEach
    void start() {
        po = new FakeMonitoringPo();
    }

    @AfterEach
    void stop() {
        po.close();
    }

    private static String result(String list, boolean warning, boolean displayPermissionWarning, int number) {
        return "<m:getMessageListResponse xmlns:m=\"" + OP + "\" xmlns:w=\"" + WS + "\"><m:Response>" + list
                + "<w:number>" + number + "</w:number><w:warning>" + warning + "</w:warning>"
                + "<w:displayPermissionWarning>" + displayPermissionWarning + "</w:displayPermissionWarning>"
                + "</m:Response></m:getMessageListResponse>";
    }

    private static final String EMPTY_RESULT = result("<w:list/>", false, false, 0);

    /** The {@code OP:filter} element of the last recorded getMessageList request. */
    private Element sentFilter() {
        return child(sentOperation(), OP, "filter");
    }

    private Element sentOperation() {
        Document doc = po.lastRequest().document();
        Element envelope = doc.getDocumentElement();
        assertThat(FakeMonitoringPo.qname(envelope)).isEqualTo(SOAP_ENV + " Envelope");
        Element body = child(envelope, SOAP_ENV, "Body");
        assertThat(childNames(body)).containsExactly(OP + " getMessageList");
        return children(body).getFirst();
    }

    // ------------------------------------------------------------------------------------------ request

    @Test
    void sendsGetMessageListEnvelope() {
        po.respondSoap(EMPTY_RESULT);
        MonitoringClient client = new MonitoringClient(po.system);
        OffsetDateTime from = OffsetDateTime.parse("2026-11-10T00:00:00+01:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-11-10T12:30:00+01:00");
        MessageFilter filter = MessageFilter.builder()
                .status("systemError")
                .senderName("SenderA")
                .toTime(to)
                .senderInterface(new MessageInterface("SI_A_Out", "urn:test:a", null, null, null, null))
                .messageIDs(List.of("msg-0001", "msg-0002"))
                .onlyFaultyMessages(true)
                .fromTime(from)
                .build();

        client.getMessageList(filter, 200);

        assertThat(client.system()).isSameAs(po.system);
        FakeMonitoringPo.Request req = po.lastRequest();
        assertThat(po.requests()).hasSize(1);
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.pathAndQuery()).isEqualTo("/AdapterMessageMonitoring/basic?style=document");
        assertThat(req.header("Content-Type")).isEqualTo("text/xml; charset=UTF-8");
        assertThat(req.header("SOAPAction")).isEqualTo("\"\"");
        assertThat(req.header("Authorization")).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("alice:s3cret".getBytes(StandardCharsets.UTF_8)));

        Element op = sentOperation();
        assertThat(childNames(op)).containsExactly(OP + " filter", OP + " maxMessages");
        assertThat(child(op, OP, "maxMessages").getTextContent()).isEqualTo("200");

        Element f = sentFilter();
        // pinned AdapterFilter order; the eight required fields always present, unset optional fields absent
        assertThat(childNames(f)).containsExactly(
                WS + " archive", WS + " dateType", WS + " fromTime", WS + " messageIDs", WS + " nodeId",
                WS + " onlyFaultyMessages", WS + " retries", WS + " retryInterval", WS + " senderInterface",
                WS + " senderName", WS + " status", WS + " timesFailed", WS + " toTime", WS + " wasEdited");
        assertThat(FakeMonitoringPo.texts(f, WS)).contains(
                entry("archive", "false"), entry("dateType", "0"), entry("nodeId", "0"),
                entry("onlyFaultyMessages", "true"), entry("retries", "0"), entry("retryInterval", "0"),
                entry("timesFailed", "0"), entry("wasEdited", "false"),
                entry("senderName", "SenderA"), entry("status", "systemError"));

        String fromText = child(f, WS, "fromTime").getTextContent();
        String toText = child(f, WS, "toTime").getTextContent();
        assertThat(fromText).matches(DATE_TIME).endsWith("+01:00");
        assertThat(toText).matches(DATE_TIME).endsWith("+01:00");
        assertThat(OffsetDateTime.parse(fromText)).isEqualTo(from);
        assertThat(OffsetDateTime.parse(toText)).isEqualTo(to);

        Element ids = child(f, WS, "messageIDs");
        assertThat(childNames(ids)).containsExactly(JL + " String", JL + " String");
        assertThat(children(ids)).extracting(Element::getTextContent).containsExactly("msg-0001", "msg-0002");

        Element si = child(f, WS, "senderInterface");
        assertThat(children(si)).allSatisfy(e -> assertThat(e.getNamespaceURI()).isEqualTo(DATA));
        assertThat(child(si, DATA, "name").getTextContent()).isEqualTo("SI_A_Out");
        assertThat(child(si, DATA, "namespace").getTextContent()).isEqualTo("urn:test:a");
    }

    @Test
    void emptyFilterSendsOnlyTheEightRequiredFieldsWithDefaults() {
        po.respondSoap(EMPTY_RESULT);

        new MonitoringClient(po.system).getMessageList(MessageFilter.builder().build(), 50);

        Element f = sentFilter();
        assertThat(childNames(f)).containsExactly(
                WS + " archive", WS + " dateType", WS + " nodeId", WS + " onlyFaultyMessages", WS + " retries",
                WS + " retryInterval", WS + " timesFailed", WS + " wasEdited");
        assertThat(FakeMonitoringPo.texts(f, WS)).containsExactly(
                entry("archive", "false"), entry("dateType", "0"), entry("nodeId", "0"),
                entry("onlyFaultyMessages", "false"), entry("retries", "0"), entry("retryInterval", "0"),
                entry("timesFailed", "0"), entry("wasEdited", "false"));
        assertThat(child(sentOperation(), OP, "maxMessages").getTextContent()).isEqualTo("50");
    }

    @Test
    void archiveSetTrueIsSentAsTrue() {
        po.respondSoap(EMPTY_RESULT);

        new MonitoringClient(po.system).getMessageList(MessageFilter.builder().archive(true).build(), 10);

        assertThat(child(sentFilter(), WS, "archive").getTextContent()).isEqualTo("true");
    }

    @Test
    void everyFilterFieldIsSentInOrderWithItsValue() {
        po.respondSoap(EMPTY_RESULT);
        OffsetDateTime from = OffsetDateTime.parse("2026-11-01T00:00:00+01:00");
        OffsetDateTime persist = OffsetDateTime.parse("2026-11-02T01:02:03+01:00");
        OffsetDateTime schedule = OffsetDateTime.parse("2026-11-03T04:05:06.789+02:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-11-04T23:59:00+01:00");
        OffsetDateTime valid = OffsetDateTime.parse("2026-11-05T07:08:09Z");
        MessageFilter filter = MessageFilter.builder()
                .onlyLogLocationsWithPayload(false)
                .returnLogLocations(true)
                .rootID("root-1")
                .scenarioIDs(List.of("scn-1", "scn-2"))
                .wasEdited(true)
                .validUntil(valid)
                .transport("transport-1")
                .toTime(to)
                .timesFailed(5)
                .statuses(List.of("systemError", "holding"))
                .status("toBeDelivered")
                .softwareComponent("swc-1")
                .serviceDefinition("svcdef-1")
                .serializationContext("serctx-1")
                .sequenceID("seq-1")
                .senderParty(new Party("AgencyS", "PartyS", "SchemaS"))
                .senderName("SenderA")
                .senderInterface(new MessageInterface("SI_S", "urn:test:s", "SP-s", "SC-s", "RP-s", "RC-s"))
                .scheduleTime(schedule)
                .retryInterval(4000L)
                .retries(3)
                .referenceIDs(List.of("ref-1", "ref-2", "ref-3"))
                .receiverParty(new Party("AgencyR", "PartyR", "SchemaR"))
                .receiverName("ReceiverB")
                .receiverInterface(new MessageInterface("SI_R", "urn:test:r", "SP-r", "SC-r", "RP-r", "RC-r"))
                .qualityOfService("EOIO")
                .protocol("protocol-1")
                .persistUntil(persist)
                .onlyFaultyMessages(true)
                .nodeId(2)
                .messageType("msgtype-1")
                .messageIDs(List.of("msg-1"))
                .messageFormat("format-1")
                .interfaceRef(new MessageInterface("SI_I", "urn:test:i", "SP-i", "SC-i", "RP-i", "RC-i"))
                .fromTime(from)
                .errorCode("errcode-1")
                .errorCategory("errcat-1")
                .direction("OUTBOUND")
                .dateType(1)
                .correlationID("corr-1")
                .connectionName("conn-1")
                .archive(true)
                .applicationComponent("appcomp-1")
                .build();

        new MonitoringClient(po.system).getMessageList(filter, 7);

        Element f = sentFilter();
        List<String> order = List.of("applicationComponent", "archive", "connectionName", "correlationID",
                "dateType", "direction", "errorCategory", "errorCode", "fromTime", "interface", "messageFormat",
                "messageIDs", "messageType", "nodeId", "onlyFaultyMessages", "persistUntil", "protocol",
                "qualityOfService", "receiverInterface", "receiverName", "receiverParty", "referenceIDs", "retries",
                "retryInterval", "scheduleTime", "senderInterface", "senderName", "senderParty", "sequenceID",
                "serializationContext", "serviceDefinition", "softwareComponent", "status", "statuses",
                "timesFailed", "toTime", "transport", "validUntil", "wasEdited", "scenarioIDs", "rootID",
                "returnLogLocations", "onlyLogLocationsWithPayload");
        assertThat(order).hasSize(43);
        assertThat(childNames(f)).containsExactlyElementsOf(order.stream().map(n -> WS + " " + n).toList());

        Map<String, String> simple = new java.util.LinkedHashMap<>();
        simple.put("applicationComponent", "appcomp-1");
        simple.put("archive", "true");
        simple.put("connectionName", "conn-1");
        simple.put("correlationID", "corr-1");
        simple.put("dateType", "1");
        simple.put("direction", "OUTBOUND");
        simple.put("errorCategory", "errcat-1");
        simple.put("errorCode", "errcode-1");
        simple.put("messageFormat", "format-1");
        simple.put("messageType", "msgtype-1");
        simple.put("nodeId", "2");
        simple.put("onlyFaultyMessages", "true");
        simple.put("protocol", "protocol-1");
        simple.put("qualityOfService", "EOIO");
        simple.put("receiverName", "ReceiverB");
        simple.put("retries", "3");
        simple.put("retryInterval", "4000");
        simple.put("senderName", "SenderA");
        simple.put("sequenceID", "seq-1");
        simple.put("serializationContext", "serctx-1");
        simple.put("serviceDefinition", "svcdef-1");
        simple.put("softwareComponent", "swc-1");
        simple.put("status", "toBeDelivered");
        simple.put("timesFailed", "5");
        simple.put("transport", "transport-1");
        simple.put("wasEdited", "true");
        simple.put("rootID", "root-1");
        simple.put("returnLogLocations", "true");
        simple.put("onlyLogLocationsWithPayload", "false");
        simple.forEach((name, text) -> assertThat(child(f, WS, name).getTextContent())
                .as("WS:%s", name).isEqualTo(text));

        Map<String, OffsetDateTime> times = Map.of("fromTime", from, "persistUntil", persist,
                "scheduleTime", schedule, "toTime", to, "validUntil", valid);
        times.forEach((name, time) -> {
            String text = child(f, WS, name).getTextContent();
            assertThat(text).as("WS:%s", name).matches(DATE_TIME);
            assertThat(OffsetDateTime.parse(text)).as("WS:%s", name).isEqualTo(time);
        });

        assertMessageInterface(child(f, WS, "interface"), "SI_I", "urn:test:i", "SP-i", "SC-i", "RP-i", "RC-i");
        assertMessageInterface(child(f, WS, "receiverInterface"),
                "SI_R", "urn:test:r", "SP-r", "SC-r", "RP-r", "RC-r");
        assertMessageInterface(child(f, WS, "senderInterface"), "SI_S", "urn:test:s", "SP-s", "SC-s", "RP-s", "RC-s");
        assertParty(child(f, WS, "receiverParty"), "AgencyR", "PartyR", "SchemaR");
        assertParty(child(f, WS, "senderParty"), "AgencyS", "PartyS", "SchemaS");

        assertStrings(child(f, WS, "messageIDs"), "msg-1");
        assertStrings(child(f, WS, "referenceIDs"), "ref-1", "ref-2", "ref-3");
        assertStrings(child(f, WS, "statuses"), "systemError", "holding");
        assertStrings(child(f, WS, "scenarioIDs"), "scn-1", "scn-2");
        assertThat(child(sentOperation(), OP, "maxMessages").getTextContent()).isEqualTo("7");
    }

    private static void assertMessageInterface(Element e, String... values) {
        assertThat(childNames(e)).as(e.getLocalName()).containsExactly(DATA + " name", DATA + " namespace",
                DATA + " senderParty", DATA + " senderComponent", DATA + " receiverParty", DATA + " receiverComponent");
        assertThat(children(e)).as(e.getLocalName()).extracting(Element::getTextContent).containsExactly(values);
    }

    private static void assertParty(Element e, String... values) {
        assertThat(childNames(e)).as(e.getLocalName())
                .containsExactly(DATA + " agency", DATA + " name", DATA + " schema");
        assertThat(children(e)).as(e.getLocalName()).extracting(Element::getTextContent).containsExactly(values);
    }

    private static void assertStrings(Element e, String... values) {
        assertThat(childNames(e)).as(e.getLocalName())
                .containsExactlyElementsOf(java.util.Collections.nCopies(values.length, JL + " String"));
        assertThat(children(e)).as(e.getLocalName()).extracting(Element::getTextContent).containsExactly(values);
    }

    // ------------------------------------------------------------------------------------------ response

    @Test
    void parsesMessagesAndKeepsRaw() {
        po.respondSoap(TWO_MESSAGES);
        String served = FakeMonitoringPo.envelope(TWO_MESSAGES);

        SearchResult r = new MonitoringClient(po.system).getMessageList(MessageFilter.builder().build(), 200);

        assertThat(r.system()).isEqualTo("QA1");
        assertThat(r.rawResponse()).isEqualTo(served);
        assertThat(r.number()).isEqualTo(2);
        assertThat(r.moreAvailable()).isFalse();
        assertThat(r.displayPermissionWarning()).isFalse();
        assertThat(r.date()).isEqualTo(OffsetDateTime.parse("2026-11-10T09:00:00.000+01:00"));
        assertThat(r.messages()).hasSize(2);

        Message m = r.messages().getFirst();
        assertThat(m.messageId()).isEqualTo("msg-0001");
        assertThat(m.messageKey()).isEqualTo("key-0001");
        assertThat(m.status()).isEqualTo("systemError");
        assertThat(m.errorCode()).isEqualTo("CODE_A");
        assertThat(m.errorCategory()).isEqualTo("CategoryA");
        assertThat(m.errorLabel()).isEqualTo(1001);
        assertThat(m.startTime()).isEqualTo(OffsetDateTime.parse("2026-11-10T08:15:30.123+01:00"));
        assertThat(m.endTime()).isEqualTo(OffsetDateTime.parse("2026-11-10T08:15:31.623+01:00"));
        assertThat(m.senderParty()).isEqualTo(new Party("AgencyA", "PartyA", "SchemaA"));
        assertThat(m.senderName()).isEqualTo("SenderA");
        assertThat(m.senderInterface())
                .isEqualTo(new MessageInterface("SI_A_Out", "urn:test:a", null, null, null, null));
        assertThat(m.receiverParty()).isEqualTo(new Party("AgencyB", "PartyB", "SchemaB"));
        assertThat(m.receiverName()).isEqualTo("ReceiverB");
        assertThat(m.receiverInterface())
                .isEqualTo(new MessageInterface("SI_B_In", "urn:test:b", null, null, null, null));
        assertThat(m.interfaceRef()).isEqualTo(
                new MessageInterface("SI_A", "urn:test:a", "PartyA", "SenderA", "PartyB", "ReceiverB"));
        assertThat(m.direction()).isEqualTo("OUTBOUND");
        assertThat(m.qualityOfService()).isEqualTo("EO");
        assertThat(m.connectionName()).isEqualTo("ConnA");
        assertThat(m.sequenceId()).isEqualTo("seq-0001");
        assertThat(m.rootId()).isEqualTo("root-0001");
        assertThat(m.parentId()).isEqualTo("parent-0001");
        assertThat(m.size()).isEqualTo(2048L);
        assertThat(m.businessAttributes()).containsExactly(
                new BusinessAttribute("OrderNo", "A-1"), new BusinessAttribute("Greeting", "Grüße"));
        assertThat(m.logLocations()).containsExactly("BI", "AM");
        assertThat(m.flags()).containsOnly(
                entry("businessMessage", true), entry("cancelable", false), entry("editable", false),
                entry("restartable", true), entry("wasEdited", false), entry("payloadPermissionWarning", false));
        assertThat(m.durationMillis()).isEqualTo(1500L);
        // every other simple child, by element name, in document order; nothing dropped, nothing mapped twice
        assertThat(m.otherFields()).containsExactly(
                entry("applicationComponent", "SenderA"), entry("isPersistent", "true"),
                entry("messageType", "Send"), entry("nodeId", "0"), entry("protocol", "XI"),
                entry("retries", "3"), entry("version", "0"), entry("futureField", "x"));

        Message second = r.messages().get(1);
        assertThat(second.messageId()).isEqualTo("msg-0002");
        assertThat(second.messageKey()).isEqualTo("key-0002");
        assertThat(second.status()).isEqualTo("success");
        assertThat(second.direction()).isEqualTo("INBOUND");
        assertThat(second.errorLabel()).isZero();
        assertThat(second.errorCode()).isNull();
        assertThat(second.senderParty()).isNull();
        assertThat(second.interfaceRef()).isNull();
        assertThat(second.startTime()).isNull();
        assertThat(second.size()).isNull();
        assertThat(second.durationMillis()).isNull();
        assertThat(second.businessAttributes()).isEmpty();
        assertThat(second.logLocations()).isEmpty();
        assertThat(second.flags()).isEmpty();
        // an unknown complex child is kept as its XML string
        assertThat(second.otherFields()).containsOnlyKeys("futureBlock");
        assertThat(second.otherFields().get("futureBlock")).contains("futureBlock", "part", ">p1<");
    }

    @Test
    void warningMeansMoreAvailable() {
        po.respondSoap(result("<w:list/>", true, true, 0));

        SearchResult r = new MonitoringClient(po.system).getMessageList(MessageFilter.builder().build(), 1);

        assertThat(r.moreAvailable()).isTrue();
        assertThat(r.displayPermissionWarning()).isTrue();
    }

    @Test
    void emptyListIsEmptyNotNull() {
        MonitoringClient client = new MonitoringClient(po.system);

        po.respondSoap(EMPTY_RESULT);
        SearchResult emptyList = client.getMessageList(MessageFilter.builder().build(), 200);
        po.respondSoap(result("", false, false, 0));
        SearchResult noList = client.getMessageList(MessageFilter.builder().build(), 200);

        assertThat(emptyList.messages()).isNotNull().isEmpty();
        assertThat(emptyList.number()).isZero();
        assertThat(emptyList.moreAvailable()).isFalse();
        assertThat(noList.messages()).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------------------------------ errors

    @Test
    void http401AndRedirectAndLogonPageAreAuthenticationFailures() {
        MonitoringClient client = new MonitoringClient(po.system);
        String logonPage = "<html><title>Logon</title><form action=\"j_security_check\">"
                + "<input name=\"j_username\"/><input name=\"j_password\" type=\"password\"/></form></html>";

        po.respond(401, Map.of("WWW-Authenticate", "Basic realm=\"test\"", "Content-Type", "text/html"),
                "<html>Unauthorized</html>".getBytes(StandardCharsets.UTF_8));
        assertAuthenticationFailure(client);

        po.respond(302, Map.of("Location", po.baseUrl() + "/logon/start"), new byte[0]);
        assertAuthenticationFailure(client);
        assertThat(po.requests()).hasSize(2); // the redirect is not followed

        po.respond(200, "text/html; charset=utf-8", logonPage);
        assertAuthenticationFailure(client);
        assertThat(po.requests()).hasSize(3);
    }

    private static void assertAuthenticationFailure(MonitoringClient client) {
        assertThatThrownBy(() -> client.getMessageList(MessageFilter.builder().build(), 10))
                .isInstanceOf(MonitoringException.class)
                .isInstanceOf(PoException.class)
                .hasMessageContaining("authentication failed")
                .hasMessageNotContaining(FakeMonitoringPo.PASSWORD);
    }

    @Test
    void http403NamesTheMonitoringRole() {
        po.respond(403, "text/html", "<html>Forbidden</html>");

        assertThatThrownBy(() -> new MonitoringClient(po.system).getMessageList(MessageFilter.builder().build(), 10))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("SAP_XI_API_DISPLAY_J2EE")
                .hasMessageNotContaining(FakeMonitoringPo.PASSWORD);
    }

    @Test
    void soapFaultCarriesCodeAndText() {
        po.respond(500, "text/xml; charset=utf-8", FakeMonitoringPo.operationFailedFault("getMessageList",
                "Search failed for the test filter", 42, "argA", "argB"));

        assertThatThrownBy(() -> new MonitoringClient(po.system).getMessageList(MessageFilter.builder().build(), 10))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("Search failed for the test filter")
                .hasMessageMatching("(?s).*(?<!\\d)42(?!\\d).*")
                .hasMessageContaining("argA")
                .hasMessageContaining("argB");
    }

    @Test
    void unexpectedOkBodyIsReportedWithItsFirst200Characters() {
        MonitoringClient client = new MonitoringClient(po.system);
        String head = "Gateway notice: " + "n".repeat(184);
        String plain = head + "TAIL_MARKER" + "t".repeat(100);
        assertThat(head).hasSize(200);

        po.respond(200, "text/plain; charset=utf-8", plain);
        assertThatThrownBy(() -> client.getMessageList(MessageFilter.builder().build(), 10))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("unexpected response")
                .hasMessageContaining(head)
                .hasMessageNotContaining("TAIL_MARKER");

        po.respond(200, "text/xml; charset=utf-8", "<status>ok</status>");
        assertThatThrownBy(() -> client.getMessageList(MessageFilter.builder().build(), 10))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("unexpected response")
                .hasMessageContaining("<status>ok</status>");
    }

    @Test
    void doctypeAndExternalEntitiesAreRejected(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, "LEAKED_SECRET");
        String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<!DOCTYPE S:Envelope [\n"
                + "  <!ENTITY file SYSTEM \"" + secret.toUri() + "\">\n"
                + "  <!ENTITY remote SYSTEM \"" + po.baseUrl() + "/entity\">\n"
                + "]>\n"
                + "<S:Envelope xmlns:S=\"" + SOAP_ENV + "\"><S:Body>"
                + result("<w:list><w:AdapterFrameworkData><w:messageID>&file;</w:messageID>"
                        + "<w:messageKey>&remote;</w:messageKey></w:AdapterFrameworkData></w:list>", false, false, 1)
                + "</S:Body></S:Envelope>";
        po.respond(200, "text/xml; charset=utf-8", body);

        assertThatThrownBy(() -> new MonitoringClient(po.system).getMessageList(MessageFilter.builder().build(), 10))
                .isInstanceOf(MonitoringException.class)
                .hasMessageNotContaining("LEAKED_SECRET");
        assertThat(po.requests()).hasSize(1); // the remote entity was never fetched
    }

    @Test
    void unreachableSystemIsAMonitoringException() {
        PoSystem gone = po.system;
        po.close();

        assertThatThrownBy(() -> new MonitoringClient(gone).getMessageList(MessageFilter.builder().build(), 10))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("cannot connect");
    }

    @Test
    void tpzSystemIsRejected(@TempDir Path dir) {
        assertThatThrownBy(() -> new MonitoringClient(PoSystem.tpz("exports", dir)))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("System 'exports' is a directory of TPZ exports")
                .hasMessageContaining("monitoring needs an online system");
    }
}
