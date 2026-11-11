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
import static org.assertj.core.api.Assertions.tuple;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** By-id lookups, audit log and payload reads of MonitoringClient against the local fake service (synthetic names). */
class MonitoringClientDetailTest {

    /** xs:dateTime lexical form with seconds and an explicit offset. */
    private static final String DATE_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})";
    /** Synthetic message keys in the backslash-separated key form. */
    private static final String KEY = "aaaaaaaa-1111-2222-3333-000000000001\\OUTBOUND\\0\\EO\\0\\";
    private static final String KEY2 = "aaaaaaaa-1111-2222-3333-000000000001\\INBOUND\\0\\EO\\0\\";
    private static final OffsetDateTime OLDER_THAN = OffsetDateTime.parse("2026-11-10T12:00:00+01:00");

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

    // ------------------------------------------------------------------------------------------ search by id / key

    @Test
    void getMessagesByIdsSendsIdsReferencesCorrelationsAndArchive() {
        String served = searchResponse("getMessagesByIDs", 1, false, message("msg-0001", KEY, "OUTBOUND", "success"));
        po.respondSoap(served);

        SearchResult r = client.getMessagesByIds(List.of("msg-0001", "msg-0002"), List.of("ref-0001"), List.of(),
                true);

        Element op = sent("getMessagesByIDs");
        assertThat(childNames(op)).containsExactly(
                OP + " messageIds", OP + " referenceIds", OP + " correlationIds", OP + " archive");
        assertStrings(child(op, OP, "messageIds"), "msg-0001", "msg-0002");
        assertStrings(child(op, OP, "referenceIds"), "ref-0001");
        assertThat(children(child(op, OP, "correlationIds"))).isEmpty();
        assertThat(text(op, "archive")).isEqualTo("true");

        assertThat(r.system()).isEqualTo("QA1");
        assertThat(r.rawResponse()).isEqualTo(envelope(served));
        assertThat(r.number()).isEqualTo(1);
        assertThat(r.messages()).extracting(Message::messageId, Message::messageKey, Message::direction)
                .containsExactly(tuple("msg-0001", KEY, "OUTBOUND"));
    }

    @Test
    void getMessagesByKeysSendsKeysAsFilterAndMaxMessages() {
        po.respondSoap(searchResponse("getMessagesByKeys", 2, false,
                message("msg-0001", KEY, "OUTBOUND", "success"), message("msg-0001", KEY2, "INBOUND", "holding")));

        SearchResult r = client.getMessagesByKeys(List.of(KEY, KEY2), 25);

        Element op = sent("getMessagesByKeys");
        assertThat(childNames(op)).containsExactly(OP + " filter", OP + " maxMessages");
        assertStrings(child(op, OP, "filter"), KEY, KEY2);
        assertThat(text(op, "maxMessages")).isEqualTo("25");
        assertThat(r.messages()).extracting(Message::messageKey).containsExactly(KEY, KEY2);
        assertThat(r.messages()).extracting(Message::status).containsExactly("success", "holding");
    }

    @Test
    void getMessagesWithSuccessorsSendsIdsAndArchive() {
        po.respondSoap(searchResponse("getMessagesWithSuccessors", 2, true,
                message("msg-0001", KEY, "OUTBOUND", "success"), message("msg-0003", KEY2, "OUTBOUND", "success")));

        SearchResult r = client.getMessagesWithSuccessors(List.of("msg-0001"), false);

        Element op = sent("getMessagesWithSuccessors");
        assertThat(childNames(op)).containsExactly(OP + " messageIds", OP + " archive");
        assertStrings(child(op, OP, "messageIds"), "msg-0001");
        assertThat(text(op, "archive")).isEqualTo("false");
        assertThat(r.messages()).extracting(Message::messageId).containsExactly("msg-0001", "msg-0003");
        assertThat(r.moreAvailable()).isTrue();
    }

    @Test
    void getPredecessorMessageIdSendsIdAndDirectionAndReturnsTheValue() {
        String served = valueResponse("getPredecessorMessageId", "msg-0000");
        po.respondSoap(served);

        ValueResult r = client.getPredecessorMessageId("msg-0001", "INBOUND");

        Element op = sent("getPredecessorMessageId");
        assertThat(childNames(op)).containsExactly(OP + " messageId", OP + " direction");
        assertThat(text(op, "messageId")).isEqualTo("msg-0001");
        assertThat(text(op, "direction")).isEqualTo("INBOUND");
        assertThat(r).isEqualTo(new ValueResult("QA1", "msg-0000", envelope(served)));
    }

    @Test
    void nilPredecessorIsNull() {
        po.respondSoap("<m:getPredecessorMessageIdResponse xmlns:m=\"" + OP + "\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<m:Response xsi:nil=\"true\"/></m:getPredecessorMessageIdResponse>");

        ValueResult r = client.getPredecessorMessageId("msg-0001", "OUTBOUND");

        assertThat(r.system()).isEqualTo("QA1");
        assertThat(r.value()).isNull();
    }

    // ------------------------------------------------------------------------------------------ audit log

    private static final String LOG_ENTRIES = """
            <d:AuditLogEntryData>
              <d:timeStamp>2026-11-10T08:15:30.100+01:00</d:timeStamp>
              <d:textKey>MP_ENTER</d:textKey>
              <d:params><j:String>ReceiverB</j:String><j:String>SI_B_In</j:String></d:params>
              <d:status>success</d:status>
              <d:localizedText>Message entered the adapter</d:localizedText>
            </d:AuditLogEntryData>
            <d:AuditLogEntryData>
              <d:timeStamp>2026-11-10T08:15:31.200+01:00</d:timeStamp>
              <d:textKey>DELIVERY_FAILED</d:textKey>
              <d:status>error</d:status>
              <d:localizedText>Delivery failed: Grüße</d:localizedText>
            </d:AuditLogEntryData>
            <d:AuditLogEntryData>
              <d:timeStamp>2026-11-10T08:15:29.000+01:00</d:timeStamp>
              <d:textKey>MP_RETRY</d:textKey>
              <d:params><j:String>3</j:String></d:params>
              <d:status>warning</d:status>
              <d:localizedText>Retry 3</d:localizedText>
            </d:AuditLogEntryData>
            """;

    @Test
    void getLogEntriesSendsKeyArchiveMaxResultsLocaleAndOlderThan() {
        po.respondSoap(valueResponse("getLogEntries", ""));

        client.getLogEntries(KEY, true, 100, Locale.of("de", "CH", "POSIX"), OLDER_THAN);

        Element op = sent("getLogEntries");
        assertThat(childNames(op)).containsExactly(OP + " messageKey", OP + " archive", OP + " maxResults",
                OP + " locale", OP + " olderThan");
        assertThat(text(op, "messageKey")).isEqualTo(KEY);
        assertThat(text(op, "archive")).isEqualTo("true");
        assertThat(text(op, "maxResults")).isEqualTo("100");
        Element locale = child(op, OP, "locale");
        assertThat(childNames(locale)).containsExactly(WS + " language", WS + " country", WS + " variant");
        assertThat(children(locale)).extracting(Element::getTextContent).containsExactly("de", "CH", "POSIX");
        String olderThan = text(op, "olderThan");
        assertThat(olderThan).matches(DATE_TIME).endsWith("+01:00");
        assertThat(OffsetDateTime.parse(olderThan)).isEqualTo(OLDER_THAN);
    }

    @Test
    void getLogEntriesWithoutLocaleOmitsTheLocale() {
        po.respondSoap(valueResponse("getLogEntries", ""));

        client.getLogEntries(KEY, false, 10, null, OLDER_THAN);

        Element op = sent("getLogEntries");
        assertThat(childNames(op)).containsExactly(OP + " messageKey", OP + " archive", OP + " maxResults",
                OP + " olderThan");
        assertThat(text(op, "archive")).isEqualTo("false");
        assertThat(text(op, "maxResults")).isEqualTo("10");
    }

    @Test
    void logEntriesParsedInOrder() {
        String served = valueResponse("getLogEntries", LOG_ENTRIES);
        po.respondSoap(served);

        LogResult r = client.getLogEntries(KEY, false, 100, null, OLDER_THAN);

        assertThat(r.system()).isEqualTo("QA1");
        assertThat(r.messageKey()).isEqualTo(KEY);
        assertThat(r.rawResponse()).isEqualTo(envelope(served));
        // document order, not sorted by time stamp
        assertThat(r.entries()).containsExactly(
                new LogEntry(OffsetDateTime.parse("2026-11-10T08:15:30.100+01:00"), "success", "MP_ENTER",
                        List.of("ReceiverB", "SI_B_In"), "Message entered the adapter", Map.of()),
                new LogEntry(OffsetDateTime.parse("2026-11-10T08:15:31.200+01:00"), "error", "DELIVERY_FAILED",
                        List.of(), "Delivery failed: Grüße", Map.of()),
                new LogEntry(OffsetDateTime.parse("2026-11-10T08:15:29.000+01:00"), "warning", "MP_RETRY",
                        List.of("3"), "Retry 3", Map.of()));
        assertThat(r.entries().get(1).params()).isNotNull().isEmpty();
    }

    @Test
    void emptyLogIsEmptyNotNull() {
        po.respondSoap(valueResponse("getLogEntries", ""));

        LogResult r = client.getLogEntries(KEY, false, 100, null, OLDER_THAN);

        assertThat(r.entries()).isNotNull().isEmpty();
    }

    @Test
    void unparseableLogValuesAreKept() {
        po.respondSoap(valueResponse("getLogEntries", "<d:AuditLogEntryData>"
                + "<d:timeStamp>soon</d:timeStamp><d:textKey>MP_ENTER</d:textKey><d:status>success</d:status>"
                + "<d:localizedText>Entered</d:localizedText><d:futureField>y</d:futureField>"
                + "</d:AuditLogEntryData>"));

        LogEntry e = client.getLogEntries(KEY, false, 100, null, OLDER_THAN).entries().getFirst();

        assertThat(e.timeStamp()).isNull();
        assertThat(e.textKey()).isEqualTo("MP_ENTER");
        assertThat(e.status()).isEqualTo("success");
        assertThat(e.localizedText()).isEqualTo("Entered");
        assertThat(e.otherFields()).containsExactly(entry("timeStamp", "soon"), entry("futureField", "y"));
    }

    // ------------------------------------------------------------------------------------------ payload

    private static final byte[] PLAIN = "<order><note>Grüße</note></order>".getBytes(StandardCharsets.UTF_8);

    private static byte[] allBytes(int times) {
        byte[] b = new byte[256 * times];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) i;
        }
        return b;
    }

    @Test
    void getMessageBytesSendsKeyAndArchive() {
        po.respondSoap(valueResponse("getMessageBytesJavaLangStringBoolean",
                Base64.getEncoder().encodeToString(PLAIN)));

        MessagePayload p = client.getMessageBytes(KEY, true);

        Element op = sent("getMessageBytesJavaLangStringBoolean");
        assertThat(childNames(op)).containsExactly(OP + " messageKey", OP + " archive");
        assertThat(text(op, "messageKey")).isEqualTo(KEY);
        assertThat(text(op, "archive")).isEqualTo("true");
        assertThat(p.source()).isEqualTo("current");
    }

    @Test
    void payloadBase64Decoded() {
        String served = valueResponse("getMessageBytesJavaLangStringBoolean",
                Base64.getEncoder().encodeToString(PLAIN));
        po.respondSoap(served);

        MessagePayload p = client.getMessageBytes(KEY, false);

        assertThat(p.system()).isEqualTo("QA1");
        assertThat(p.messageKey()).isEqualTo(KEY);
        assertThat(p.source()).isEqualTo("current");
        assertThat(p.bytes()).isEqualTo(PLAIN);
        assertThat(p.parts()).isNotNull().isEmpty();
        assertThat(p.note()).isEqualTo("not multipart");
        assertThat(p.rawResponse()).isEqualTo(envelope(served));
    }

    @Test
    void wrappedBase64IsDecoded() {
        byte[] bytes = allBytes(3);
        String wrapped = Base64.getMimeEncoder().encodeToString(bytes);
        assertThat(wrapped).contains("\r\n");
        po.respondSoap(valueResponse("getMessageBytesJavaLangStringBoolean", wrapped));

        assertThat(client.getMessageBytes(KEY, false).bytes()).isEqualTo(bytes);
    }

    @Test
    void getMessageBytesOfAVersionSendsKeyVersionAndArchive() {
        po.respondSoap(valueResponse("getMessageBytesJavaLangStringIntBoolean",
                Base64.getEncoder().encodeToString(PLAIN)));

        MessagePayload p = client.getMessageBytes(KEY, 3, true);

        Element op = sent("getMessageBytesJavaLangStringIntBoolean");
        assertThat(childNames(op)).containsExactly(OP + " messageKey", OP + " version", OP + " archive");
        assertThat(text(op, "messageKey")).isEqualTo(KEY);
        assertThat(text(op, "version")).isEqualTo("3");
        assertThat(text(op, "archive")).isEqualTo("true");
        assertThat(p.source()).isEqualTo("version:3");
        assertThat(p.bytes()).isEqualTo(PLAIN);
    }

    @Test
    void getLoggedMessageBytesSendsKeyVersionAndArchive() {
        po.respondSoap(valueResponse("getLoggedMessageBytes", Base64.getEncoder().encodeToString(PLAIN)));

        MessagePayload p = client.getLoggedMessageBytes(KEY, "BI", false);

        Element op = sent("getLoggedMessageBytes");
        assertThat(childNames(op)).containsExactly(OP + " messageKey", OP + " version", OP + " archive");
        assertThat(text(op, "messageKey")).isEqualTo(KEY);
        assertThat(text(op, "version")).isEqualTo("BI");
        assertThat(text(op, "archive")).isEqualTo("false");
        assertThat(p.source()).isEqualTo("logged:BI");
        assertThat(p.messageKey()).isEqualTo(KEY);
        assertThat(p.bytes()).isEqualTo(PLAIN);
    }

    @Test
    void multipartPayloadIsSplitIntoParts() {
        byte[] xi = MimeTest.xiMessage();
        po.respondSoap(valueResponse("getMessageBytesJavaLangStringBoolean", Base64.getEncoder().encodeToString(xi)));

        MessagePayload p = client.getMessageBytes(KEY, false);

        assertThat(p.bytes()).isEqualTo(xi);
        assertThat(p.parts()).hasSize(2);
        assertThat(p.parts()).extracting(PayloadPart::contentType).containsExactly(
                "text/xml; charset=utf-8", "application/xml");
        assertThat(p.parts().getFirst().text()).isEqualTo(MimeTest.HEADER_PART);
    }

    // ------------------------------------------------------------------------------------------ review round 2

    @Test
    void nullOlderThanIsSentAsNil() {
        po.respondSoap(valueResponse("getLogEntries", ""));

        client.getLogEntries(KEY, false, 10, null, null);

        Element op = sent("getLogEntries");
        assertThat(childNames(op)).containsExactly(OP + " messageKey", OP + " archive", OP + " maxResults",
                OP + " olderThan");
        Element olderThan = child(op, OP, "olderThan");
        assertThat(olderThan.getAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "nil")).isEqualTo("true");
        assertThat(olderThan.getTextContent()).isEmpty();
        assertThat(children(olderThan)).isEmpty();
    }

    @Test
    void invalidBase64PayloadIsAMonitoringException() {
        for (String invalid : List.of("Zm9v*not-base64*", "A===")) {
            po.respondSoap(valueResponse("getMessageBytesJavaLangStringBoolean", invalid));

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.getMessageBytes(KEY, false))
                    .as("Response %s", invalid)
                    .isInstanceOf(MonitoringException.class);
        }
    }

    @Test
    void nilPayloadHasNoBytes() {
        po.respondSoap("<m:getMessageBytesJavaLangStringBooleanResponse xmlns:m=\"" + OP + "\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
                + "<m:Response xsi:nil=\"true\"/></m:getMessageBytesJavaLangStringBooleanResponse>");

        MessagePayload p = client.getMessageBytes(KEY, false);

        assertThat(p.bytes()).isNull();
        assertThat(p.note()).isEqualTo("no payload");
        assertThat(p.parts()).isNotNull().isEmpty();
        assertThat(p.messageKey()).isEqualTo(KEY);
        assertThat(p.source()).isEqualTo("current");
    }

    // ------------------------------------------------------------------------------------------ MON-04 D3

    @Test
    void logItemsThatAreNotEntriesAreKept() {
        po.respondSoap(valueResponse("getLogEntries", "<d:AuditLogEntryData>"
                + "<d:timeStamp>2026-11-10T08:15:30.100+01:00</d:timeStamp><d:textKey>MP_ENTER</d:textKey>"
                + "<d:status>success</d:status><d:localizedText>Entered</d:localizedText></d:AuditLogEntryData>"
                + "<d:FutureNote>first</d:FutureNote><d:FutureNote>second</d:FutureNote>"));

        LogResult r = client.getLogEntries(KEY, false, 100, null, OLDER_THAN);

        assertThat(r.entries()).extracting(LogEntry::textKey).containsExactly("MP_ENTER");
        assertThat(r.otherFields()).containsExactly(
                entry("Response/FutureNote", "first"), entry("Response/FutureNote#2", "second"));
    }

    @Test
    void logResultWithOnlyEntriesHasNoOtherFields() {
        po.respondSoap(valueResponse("getLogEntries", LOG_ENTRIES));

        LogResult r = client.getLogEntries(KEY, false, 100, null, OLDER_THAN);

        assertThat(r.otherFields()).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------------------------------ MON-04 review round 2

    @Test
    void nilLogResponseIsEmptyWithTheNilRecorded() {
        po.respondSoap(MonitoringClientLookupTest.nilResponse("getLogEntries"));

        LogResult r = client.getLogEntries(KEY, false, 100, null, OLDER_THAN);

        assertThat(r.entries()).isNotNull().isEmpty();
        assertThat(r.otherFields()).containsExactly(entry("Response", "(nil)"));
    }

    @Test
    void nilLogEntryIsKept() {
        po.respondSoap(valueResponse("getLogEntries", "<d:AuditLogEntryData xsi:nil=\"true\"/>"
                + "<d:AuditLogEntryData><d:textKey>MP_ENTER</d:textKey></d:AuditLogEntryData>"));

        LogResult r = client.getLogEntries(KEY, false, 100, null, OLDER_THAN);

        assertThat(r.entries()).extracting(LogEntry::textKey).containsExactly("MP_ENTER");
        assertThat(r.otherFields()).containsExactly(entry("Response/AuditLogEntryData", "(nil)"));
    }
}
