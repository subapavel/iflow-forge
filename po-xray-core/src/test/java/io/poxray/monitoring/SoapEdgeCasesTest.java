package io.poxray.monitoring;

import static io.poxray.monitoring.FakeMonitoringPo.OP;
import static io.poxray.monitoring.FakeMonitoringPo.SOAP_ENV;
import static io.poxray.monitoring.FakeMonitoringPo.WS;
import static io.poxray.monitoring.FakeMonitoringPo.envelope;
import static io.poxray.monitoring.FakeMonitoringPo.searchResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SOAP transport edge cases of MonitoringClient against the local fake service (synthetic names only). */
class SoapEdgeCasesTest {

    private static final String KEY = "aaaaaaaa-1111-2222-3333-000000000001\\OUTBOUND\\0\\EO\\0\\";
    private static final String TEXT = "Dobrý den, čtvrtek";

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

    /** A getMessageList answer with one message carrying one business attribute. */
    private static String oneMessageWithAttribute(String name, String value) {
        return searchResponse("getMessageList", 1, false, "<w:AdapterFrameworkData>"
                + "<w:messageID>msg-0001</w:messageID><w:messageKey>key-0001</w:messageKey>"
                + "<w:businessAttributes><w:BusinessAttribute><w:name>" + name + "</w:name><w:value>" + value
                + "</w:value></w:BusinessAttribute></w:businessAttributes></w:AdapterFrameworkData>");
    }

    private SearchResult search() {
        return client.getMessageList(MessageFilter.builder().build(), 10);
    }

    @Test
    void soap200ContainingLogonFieldNamesIsNotAnAuthFailure() {
        String body = oneMessageWithAttribute("LoginForm", "fields j_username and j_password")
                .replace("<w:messageID>", "<w:futureField hint=\"j_username/j_password\">x</w:futureField>"
                        + "<w:messageID>");
        po.respondSoap(body);

        SearchResult r = search();

        assertThat(r.messages()).hasSize(1);
        Message m = r.messages().getFirst();
        assertThat(m.messageId()).isEqualTo("msg-0001");
        assertThat(m.businessAttributes())
                .containsExactly(new BusinessAttribute("LoginForm", "fields j_username and j_password"));
        assertThat(m.otherFields()).containsEntry("futureField", "x");
    }

    @Test
    void missingResponseElementIsUnexpected() {
        po.respondSoap("<m:getMessageListResponse xmlns:m=\"" + OP + "\"/>");

        assertThatThrownBy(this::search)
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("unexpected response")
                .hasMessageMatching("(?s).*(?<![A-Za-z])Response(?![A-Za-z]).*");
    }

    @Test
    void non200SoapWithoutFaultIsReported() {
        po.respond(500, "text/xml; charset=utf-8", envelope(oneMessageWithAttribute("OrderNo", "A-1")));

        assertThatThrownBy(this::search)
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void invalidKeyFaultNamesTheKey() {
        po.respond(500, "text/xml; charset=utf-8", FakeMonitoringPo.invalidKeyFault("getLogEntries",
                "Message key is not valid", 7, KEY, "argK1", "argK2"));

        assertThatThrownBy(() -> client.getLogEntries(KEY, false, 10, null,
                OffsetDateTime.parse("2026-11-10T12:00:00+01:00")))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("InvalidKeyException")
                .hasMessageContaining("Message key is not valid")
                .hasMessageContaining(KEY)
                .hasMessageMatching("(?s).*(?<!\\d)7(?!\\d).*")
                .hasMessageContaining("argK1")
                .hasMessageContaining("argK2");
    }

    @Test
    void xmlDeclarationEncodingIsHonouredWithoutCharset() {
        Charset latin2 = Charset.forName("ISO-8859-2");
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-2\"?>\n<S:Envelope xmlns:S=\"" + SOAP_ENV
                + "\"><S:Body>" + oneMessageWithAttribute("Greeting", TEXT) + "</S:Body></S:Envelope>";
        byte[] body = xml.getBytes(latin2);
        assertThat(new String(body, StandardCharsets.UTF_8)).doesNotContain("č"); // not readable as UTF-8
        po.respond(200, Map.of("Content-Type", "text/xml"), body);

        SearchResult r = search();

        assertThat(r.messages().getFirst().businessAttributes())
                .containsExactly(new BusinessAttribute("Greeting", TEXT));
        assertThat(r.rawResponse()).isEqualTo(xml).contains("č");
    }

    @Test
    void bodyWithoutCharsetOrEncodingDeclarationIsUtf8() {
        String xml = "<S:Envelope xmlns:S=\"" + SOAP_ENV + "\"><S:Body>" + oneMessageWithAttribute("Greeting", TEXT)
                + "</S:Body></S:Envelope>";
        po.respond(200, Map.of("Content-Type", "text/xml"), xml.getBytes(StandardCharsets.UTF_8));

        SearchResult r = search();

        assertThat(r.messages().getFirst().businessAttributes())
                .containsExactly(new BusinessAttribute("Greeting", TEXT));
        assertThat(r.rawResponse()).isEqualTo(xml);
    }

    @Test
    void controlCharacterInFilterIsRejected() {
        po.respondSoap(searchResponse("getMessageList", 0, false));
        MessageFilter filter = MessageFilter.builder().senderName("Sender\u0001A").build();

        assertThatThrownBy(() -> client.getMessageList(filter, 10))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("senderName");
        assertThat(po.requests()).isEmpty(); // never sent
    }

    @Test
    void filterTextIsSentEscaped() {
        po.respondSoap(searchResponse("getMessageList", 0, false));

        client.getMessageList(MessageFilter.builder().senderName("A&B <C> \"D\"").build(), 10);

        org.w3c.dom.Element filter = FakeMonitoringPo.child(
                FakeMonitoringPo.operation(po.lastRequest(), "getMessageList"), OP, "filter");
        assertThat(FakeMonitoringPo.child(filter, WS, "senderName").getTextContent()).isEqualTo("A&B <C> \"D\"");
    }

    @Test
    void unpairedSurrogateInFilterIsRejected() {
        po.respondSoap(searchResponse("getMessageList", 0, false));
        MessageFilter filter = MessageFilter.builder().senderName("Sender\uD800A").build();

        assertThatThrownBy(() -> client.getMessageList(filter, 10))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("senderName");
        assertThat(po.requests()).isEmpty(); // never sent
    }
}
