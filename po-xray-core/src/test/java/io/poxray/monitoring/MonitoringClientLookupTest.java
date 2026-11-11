package io.poxray.monitoring;

import static io.poxray.monitoring.FakeMonitoringPo.JL;
import static io.poxray.monitoring.FakeMonitoringPo.OP;
import static io.poxray.monitoring.FakeMonitoringPo.WS;
import static io.poxray.monitoring.FakeMonitoringPo.child;
import static io.poxray.monitoring.FakeMonitoringPo.childNames;
import static io.poxray.monitoring.FakeMonitoringPo.children;
import static io.poxray.monitoring.FakeMonitoringPo.envelope;
import static io.poxray.monitoring.FakeMonitoringPo.message;
import static io.poxray.monitoring.FakeMonitoringPo.operation;
import static io.poxray.monitoring.FakeMonitoringPo.searchResponse;
import static io.poxray.monitoring.FakeMonitoringPo.valueResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** User-defined search and the lookup operations of MonitoringClient against the local fake (synthetic names). */
class MonitoringClientLookupTest {

    private static final String KEY = "aaaaaaaa-1111-2222-3333-000000000001\\OUTBOUND\\0\\EO\\0\\";
    private static final String XSI = "http://www.w3.org/2001/XMLSchema-instance";

    private FakeMonitoringPo po;
    private MonitoringClient client;

    @BeforeEach
    void start() {
        po = new FakeMonitoringPo();
        client = new MonitoringClient(po.system);
    }

    @AfterEach
    void stop() {
        po.close();
    }

    /** The operation element of the only request sent so far. */
    private Element sent(String operation) {
        assertThat(po.requests()).hasSize(1);
        assertThat(po.lastRequest().pathAndQuery()).isEqualTo(FakeMonitoringPo.ENDPOINT);
        return operation(po.lastRequest(), operation);
    }

    private static String text(Element parent, String localName) {
        return child(parent, OP, localName).getTextContent();
    }

    private static void assertStrings(Element e, String... values) {
        assertThat(childNames(e)).as(e.getLocalName())
                .containsExactlyElementsOf(Collections.nCopies(values.length, JL + " String"));
        assertThat(children(e)).as(e.getLocalName()).extracting(Element::getTextContent).containsExactly(values);
    }

    private static void assertLocale(Element locale, String language, String country, String variant) {
        assertThat(childNames(locale)).containsExactly(WS + " language", WS + " country", WS + " variant");
        assertThat(children(locale)).extracting(Element::getTextContent).containsExactly(language, country, variant);
    }

    private static final String INTERFACES = "<d:MessageInterface><d:name>SI_A_Out</d:name>"
            + "<d:namespace>urn:test:a</d:namespace><d:senderParty>PartyA</d:senderParty>"
            + "<d:senderComponent>SenderA</d:senderComponent><d:receiverParty>PartyB</d:receiverParty>"
            + "<d:receiverComponent>ReceiverB</d:receiverComponent></d:MessageInterface>"
            + "<d:MessageInterface><d:name>SI_B_In</d:name><d:namespace>urn:test:b</d:namespace></d:MessageInterface>";

    // ------------------------------------------------------------------------------------------ user-defined search

    @Test
    void udsSearchSendsAttributesAndOperator() {
        String served = searchResponse("getUserDefinedSearchMessages", 1, false,
                message("msg-0001", KEY, "OUTBOUND", "success"));
        po.respondSoap(served);
        MessageFilter filter = MessageFilter.builder().senderName("SenderA").build();

        SearchResult r = client.getUserDefinedSearchMessages(filter, 50,
                List.of(new BusinessAttribute("OrderNo", "A-1"), new BusinessAttribute("Plant", "P-2")), "AND");

        Element op = sent("getUserDefinedSearchMessages");
        assertThat(childNames(op)).containsExactly(
                OP + " filter", OP + " maxMessages", OP + " attributes", OP + " operator");
        Element f = child(op, OP, "filter");
        assertThat(children(f)).allSatisfy(e -> assertThat(e.getNamespaceURI()).isEqualTo(WS));
        assertThat(child(f, WS, "senderName").getTextContent()).isEqualTo("SenderA");
        assertThat(child(f, WS, "archive").getTextContent()).isEqualTo("false"); // the required defaults, as MON-02
        assertThat(text(op, "maxMessages")).isEqualTo("50");
        Element attributes = child(op, OP, "attributes");
        assertThat(childNames(attributes)).containsExactly(WS + " BusinessAttribute", WS + " BusinessAttribute");
        List<Element> items = children(attributes);
        assertThat(childNames(items.get(0))).containsExactly(WS + " name", WS + " value");
        assertThat(children(items.get(0))).extracting(Element::getTextContent).containsExactly("OrderNo", "A-1");
        assertThat(children(items.get(1))).extracting(Element::getTextContent).containsExactly("Plant", "P-2");
        assertThat(text(op, "operator")).isEqualTo("AND");

        assertThat(r.system()).isEqualTo("QA1");
        assertThat(r.rawResponse()).isEqualTo(envelope(served));
        assertThat(r.messages()).extracting(Message::messageKey).containsExactly(KEY);
    }

    // ------------------------------------------------------------------------------------------ lookups

    @Test
    void getUserDefinedSearchFiltersSendsNoParametersAndReadsInterfaces() {
        String served = valueResponse("getUserDefinedSearchFilters", INTERFACES);
        po.respondSoap(served);

        Lookup<MessageInterface> r = client.getUserDefinedSearchFilters();

        assertThat(childNames(sent("getUserDefinedSearchFilters"))).isEmpty();
        assertThat(r.system()).isEqualTo("QA1");
        assertThat(r.rawResponse()).isEqualTo(envelope(served));
        assertThat(r.values()).containsExactly(
                new MessageInterface("SI_A_Out", "urn:test:a", "PartyA", "SenderA", "PartyB", "ReceiverB"),
                new MessageInterface("SI_B_In", "urn:test:b", null, null, null, null));
        assertThat(r.otherFields()).isEmpty();
    }

    @Test
    void getUserDefinedSearchExtractorsSendsNameAndNamespace() {
        po.respondSoap(valueResponse("getUserDefinedSearchExtractors",
                "<w:AttributeMetadata><w:name>OrderNo</w:name><w:description>Order number</w:description>"
                        + "</w:AttributeMetadata><w:AttributeMetadata><w:name>Plant</w:name></w:AttributeMetadata>"));

        Lookup<AttributeMetadata> r = client.getUserDefinedSearchExtractors("SI_A_Out", "urn:test:a");

        Element op = sent("getUserDefinedSearchExtractors");
        assertThat(childNames(op)).containsExactly(OP + " name", OP + " namespace");
        assertThat(text(op, "name")).isEqualTo("SI_A_Out");
        assertThat(text(op, "namespace")).isEqualTo("urn:test:a");
        assertThat(r.values()).containsExactly(
                new AttributeMetadata("OrderNo", "Order number"), new AttributeMetadata("Plant", null));
    }

    @Test
    void getUserDefinedSearchAttributesSendsKeyAndArchive() {
        po.respondSoap(valueResponse("getUserDefinedSearchAttributes",
                "<w:BusinessAttribute><w:name>OrderNo</w:name><w:value>A-1</w:value></w:BusinessAttribute>"
                        + "<w:BusinessAttribute><w:name>Greeting</w:name><w:value>Grüße</w:value></w:BusinessAttribute>"));

        Lookup<BusinessAttribute> r = client.getUserDefinedSearchAttributes(KEY, true);

        Element op = sent("getUserDefinedSearchAttributes");
        assertThat(childNames(op)).containsExactly(OP + " messageKey", OP + " archive");
        assertThat(text(op, "messageKey")).isEqualTo(KEY);
        assertThat(text(op, "archive")).isEqualTo("true");
        assertThat(r.values()).containsExactly(
                new BusinessAttribute("OrderNo", "A-1"), new BusinessAttribute("Greeting", "Grüße"));
    }

    @Test
    void getInterfacesSendsNoParametersAndReadsInterfaces() {
        po.respondSoap(valueResponse("getInterfaces", INTERFACES));

        Lookup<MessageInterface> r = client.getInterfaces();

        assertThat(childNames(sent("getInterfaces"))).isEmpty();
        assertThat(r.values()).extracting(MessageInterface::name).containsExactly("SI_A_Out", "SI_B_In");
        assertThat(r.values().getFirst().receiverComponent()).isEqualTo("ReceiverB");
    }

    @Test
    void getPartiesSendsNoParametersAndReadsParties() {
        po.respondSoap(valueResponse("getParties",
                "<d:MessageParty><d:agency>AgencyA</d:agency><d:name>PartyA</d:name><d:schema>SchemaA</d:schema>"
                        + "</d:MessageParty><d:MessageParty><d:name>PartyB</d:name></d:MessageParty>"));

        Lookup<Party> r = client.getParties();

        assertThat(childNames(sent("getParties"))).isEmpty();
        assertThat(r.values()).containsExactly(new Party("AgencyA", "PartyA", "SchemaA"), new Party(null, "PartyB", null));
    }

    @Test
    void getServicesSendsNoParametersAndReadsStrings() {
        po.respondSoap(valueResponse("getServices", "<j:String>SenderA</j:String><j:String>ReceiverB</j:String>"));

        Lookup<String> r = client.getServices();

        assertThat(childNames(sent("getServices"))).isEmpty();
        assertThat(r.values()).containsExactly("SenderA", "ReceiverB");
    }

    @Test
    void getConnectionsSendsNoParametersAndReadsStrings() {
        po.respondSoap(valueResponse("getConnections", "<j:String>ConnA</j:String><j:String>ConnB</j:String>"));

        Lookup<String> r = client.getConnections();

        assertThat(childNames(sent("getConnections"))).isEmpty();
        assertThat(r.values()).containsExactly("ConnA", "ConnB");
    }

    @Test
    void getIntegrationFlowsSendsLanguage() {
        po.respondSoap(valueResponse("getIntegrationFlows",
                "<w:IntegrationFlow><w:name>Flow A</w:name><w:description>First flow</w:description>"
                        + "<w:id>flow-0001</w:id></w:IntegrationFlow>"));

        Lookup<IntegrationFlow> r = client.getIntegrationFlows("en");

        Element op = sent("getIntegrationFlows");
        assertThat(childNames(op)).containsExactly(OP + " language");
        assertThat(text(op, "language")).isEqualTo("en");
        assertThat(r.values()).containsExactly(new IntegrationFlow("flow-0001", "Flow A", "First flow"));
    }

    @Test
    void getErrorCodesSendsTheErrorLabelId() {
        po.respondSoap(valueResponse("getErrorCodes", "<j:String>CODE_A</j:String><j:String>CODE_B</j:String>"));

        Lookup<String> r = client.getErrorCodes(1001);

        Element op = sent("getErrorCodes");
        assertThat(childNames(op)).containsExactly(OP + " errorLabelID");
        assertThat(text(op, "errorLabelID")).isEqualTo("1001");
        assertThat(r.values()).containsExactly("CODE_A", "CODE_B");
    }

    private static final String STATUS_DETAILS = "<w:StatusDetail><w:errorLabelID>1001</w:errorLabelID>"
            + "<w:description>Mapping error</w:description><w:text>The mapping failed</w:text></w:StatusDetail>"
            + "<w:StatusDetail><w:errorLabelID>1002</w:errorLabelID><w:description>Übertragung</w:description>"
            + "</w:StatusDetail>";

    @Test
    void getStatusDetailsSendsErrorCodesAndLocale() {
        po.respondSoap(valueResponse("getStatusDetails", STATUS_DETAILS));

        Lookup<StatusDetail> r = client.getStatusDetails(List.of("CODE_A", "CODE_B"), Locale.of("de", "DE"));

        Element op = sent("getStatusDetails");
        assertThat(childNames(op)).containsExactly(OP + " errorCodes", OP + " locale");
        assertStrings(child(op, OP, "errorCodes"), "CODE_A", "CODE_B");
        assertLocale(child(op, OP, "locale"), "de", "DE", "");
        assertThat(r.values()).containsExactly(
                new StatusDetail("1001", "Mapping error", "The mapping failed"),
                new StatusDetail("1002", "Übertragung", null));
    }

    @Test
    void getAllAvailableStatusDetailsSendsTheLocale() {
        po.respondSoap(valueResponse("getAllAvailableStatusDetails", STATUS_DETAILS));

        Lookup<StatusDetail> r = client.getAllAvailableStatusDetails(Locale.of("en", "US", "POSIX"));

        Element op = sent("getAllAvailableStatusDetails");
        assertThat(childNames(op)).containsExactly(OP + " locale");
        assertLocale(child(op, OP, "locale"), "en", "US", "POSIX");
        assertThat(r.values()).extracting(StatusDetail::errorLabelId).containsExactly("1001", "1002");
    }

    @Test
    void nullRequiredLocaleIsSentAsNil() {
        po.respondSoap(valueResponse("getAllAvailableStatusDetails", ""));

        client.getAllAvailableStatusDetails(null);

        Element locale = child(sent("getAllAvailableStatusDetails"), OP, "locale");
        assertThat(locale.getAttributeNS(XSI, "nil")).isEqualTo("true");
        assertThat(children(locale)).isEmpty();
    }

    @Test
    void emptyLookupIsEmptyNotNull() {
        po.respondSoap(valueResponse("getServices", ""));

        Lookup<String> r = client.getServices();

        assertThat(r.values()).isNotNull().isEmpty();
        assertThat(r.otherFields()).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------------------------------ honesty

    @Test
    void nilItemIsKeptInOtherFields() {
        po.respondSoap(valueResponse("getServices",
                "<j:String>SenderA</j:String><j:String xsi:nil=\"true\"/><j:String>ReceiverB</j:String>"));

        Lookup<String> r = client.getServices();

        assertThat(r.values()).containsExactly("SenderA", "ReceiverB");
        assertThat(r.otherFields()).hasSize(1);
        assertThat(r.otherFields()).allSatisfy((k, v) -> {
            assertThat(k).contains("String");
            assertThat(v).isEqualTo("(nil)");
        });
    }

    @Test
    void unknownItemIsKeptInOtherFields() {
        po.respondSoap(valueResponse("getParties",
                "<d:MessageParty><d:name>PartyA</d:name></d:MessageParty><d:FutureItem>x</d:FutureItem>"));

        Lookup<Party> r = client.getParties();

        assertThat(r.values()).containsExactly(new Party(null, "PartyA", null));
        assertThat(r.otherFields()).containsExactly(entry("Response/FutureItem", "x"));
    }

    @Test
    void unmappedChildOfAnItemIsKept() {
        po.respondSoap(valueResponse("getIntegrationFlows",
                "<w:IntegrationFlow><w:name>Flow A</w:name><w:id>flow-0001</w:id><w:owner>TeamA</w:owner>"
                        + "</w:IntegrationFlow>"));

        Lookup<IntegrationFlow> r = client.getIntegrationFlows("en");

        assertThat(r.values()).containsExactly(new IntegrationFlow("flow-0001", "Flow A", null));
        assertThat(r.otherFields()).hasSize(1);
        assertThat(r.otherFields()).allSatisfy((k, v) -> {
            assertThat(k).contains("owner");
            assertThat(v).isEqualTo("TeamA");
        });
    }

    @Test
    void itemChildInTheWrongNamespaceIsNotMapped() {
        // items in the wrong namespace (MessageInterface children in WS instead of DATA) are not mapped
        po.respondSoap(valueResponse("getInterfaces",
                "<d:MessageInterface><w:name>SI_Wrong</w:name></d:MessageInterface>" + INTERFACES));

        Lookup<MessageInterface> r = client.getInterfaces();

        assertThat(r.values()).extracting(MessageInterface::name).doesNotContain("SI_Wrong");
        assertThat(String.join(" ", r.otherFields().values()) + " " + String.join(" ", r.otherFields().keySet()))
                .contains("SI_Wrong");
    }

    // ------------------------------------------------------------------------------------------ review round 2

    /** The SOAP body content of an answer whose whole {@code Response} is nil. */
    static String nilResponse(String operation) {
        return "<m:" + operation + "Response xmlns:m=\"" + OP + "\" xmlns:xsi=\"" + XSI + "\">"
                + "<m:Response xsi:nil=\"true\"/></m:" + operation + "Response>";
    }

    @Test
    void nilResponseIsAnEmptyLookupWithTheNilRecorded() {
        po.respondSoap(nilResponse("getServices"));

        Lookup<String> r = client.getServices();

        assertThat(r.values()).isNotNull().isEmpty();
        assertThat(r.otherFields()).containsExactly(entry("Response", "(nil)"));
    }

    @Test
    void repeatedOrComplexItemChildIsKeptUnderItsItem() {
        po.respondSoap(valueResponse("getIntegrationFlows",
                "<w:IntegrationFlow><w:name>Flow A</w:name><w:name>Flow A again</w:name><w:id>flow-0001</w:id>"
                        + "</w:IntegrationFlow>"
                        + "<w:IntegrationFlow><w:name><w:part>Flow B</w:part></w:name><w:id>flow-0002</w:id>"
                        + "</w:IntegrationFlow>"));

        Lookup<IntegrationFlow> r = client.getIntegrationFlows("en");

        assertThat(r.values()).containsExactly(new IntegrationFlow("flow-0001", "Flow A", null),
                new IntegrationFlow("flow-0002", null, null));
        assertThat(r.otherFields()).containsOnlyKeys(
                "Response/IntegrationFlow/name", "Response/IntegrationFlow#2/name");
        assertThat(r.otherFields().get("Response/IntegrationFlow/name")).isEqualTo("Flow A again");
        assertThat(r.otherFields().get("Response/IntegrationFlow#2/name")).contains("part", "Flow B");
    }

    @Test
    void stringItemWithElementContentIsKeptAsXml() {
        po.respondSoap(valueResponse("getConnections",
                "<j:String>ConnA</j:String><j:String><j:inner>ConnX</j:inner></j:String>"));

        Lookup<String> r = client.getConnections();

        assertThat(r.values()).containsExactly("ConnA");
        assertThat(r.otherFields()).containsOnlyKeys("Response/String");
        assertThat(r.otherFields().get("Response/String")).contains("<", "inner", "ConnX");
    }

    @Test
    void itemWhoseOnlyChildIsInTheWrongNamespaceStaysWithNullFields() {
        po.respondSoap(valueResponse("getInterfaces",
                "<d:MessageInterface><w:name>SI_Wrong</w:name></d:MessageInterface>"));

        Lookup<MessageInterface> r = client.getInterfaces();

        assertThat(r.values()).containsExactly(new MessageInterface(null, null, null, null, null, null));
        assertThat(r.otherFields()).containsExactly(entry("Response/MessageInterface/name", "SI_Wrong"));
    }

    @Test
    void udsSearchSendsNullFilterAttributesAndOperatorAsNil() {
        po.respondSoap(searchResponse("getUserDefinedSearchMessages", 0, false));

        client.getUserDefinedSearchMessages(null, 10, null, null);

        Element op = sent("getUserDefinedSearchMessages");
        assertThat(childNames(op)).containsExactly(
                OP + " filter", OP + " maxMessages", OP + " attributes", OP + " operator");
        for (String name : List.of("filter", "attributes", "operator")) {
            Element e = child(op, OP, name);
            assertThat(e.getAttributeNS(XSI, "nil")).as(name).isEqualTo("true");
            assertThat(children(e)).as(name).isEmpty();
        }
    }

    @Test
    void udsSearchSendsANullAttributeAsNilAndLeavesOutNullNameOrValue() {
        po.respondSoap(searchResponse("getUserDefinedSearchMessages", 0, false));
        List<BusinessAttribute> attributes = new java.util.ArrayList<>();
        attributes.add(new BusinessAttribute("OrderNo", null));
        attributes.add(null);
        attributes.add(new BusinessAttribute(null, "P-2"));

        client.getUserDefinedSearchMessages(MessageFilter.builder().build(), 10, attributes, "OR");

        List<Element> items = children(child(sent("getUserDefinedSearchMessages"), OP, "attributes"));
        assertThat(items).extracting(FakeMonitoringPo::qname).containsExactly(
                WS + " BusinessAttribute", WS + " BusinessAttribute", WS + " BusinessAttribute");
        assertThat(childNames(items.get(0))).containsExactly(WS + " name");
        assertThat(items.get(0).getTextContent()).isEqualTo("OrderNo");
        assertThat(items.get(1).getAttributeNS(XSI, "nil")).isEqualTo("true");
        assertThat(children(items.get(1))).isEmpty();
        assertThat(childNames(items.get(2))).containsExactly(WS + " value");
        assertThat(items.get(2).getTextContent()).isEqualTo("P-2");
    }

    @Test
    void nullErrorCodesAreSentAsNil() {
        po.respondSoap(valueResponse("getStatusDetails", ""));

        client.getStatusDetails(null, Locale.of("de", "DE"));

        Element op = sent("getStatusDetails");
        assertThat(childNames(op)).containsExactly(OP + " errorCodes", OP + " locale");
        Element codes = child(op, OP, "errorCodes");
        assertThat(codes.getAttributeNS(XSI, "nil")).isEqualTo("true");
        assertThat(children(codes)).isEmpty();
    }
}
