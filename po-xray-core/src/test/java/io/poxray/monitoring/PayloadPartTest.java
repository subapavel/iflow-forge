package io.poxray.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/**
 * {@link PayloadPart#isXiHeader()}: a text part whose document element is the SOAP 1.1 {@code Envelope} (F-18). The
 * envelopes are synthetic, in the shape of an XI 3.0 message header.
 */
class PayloadPartTest {

    static final String SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";

    /** A synthetic XI 3.0 header part: SOAP 1.1 envelope with the XI Main header and the payload manifest. */
    static final String XI_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<SOAP:Envelope xmlns:SOAP=\"" + SOAP_ENV + "\"><SOAP:Header>"
            + "<sap:Main xmlns:sap=\"http://sap.com/xi/XI/Message/30\" versionMajor=\"3\" versionMinor=\"1\" "
            + "SOAP:mustUnderstand=\"1\" wsu:Id=\"wsuid-main-0001\" "
            + "xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">"
            + "<sap:MessageClass>ApplicationMessage</sap:MessageClass>"
            + "<sap:ProcessingMode>asynchronous</sap:ProcessingMode>"
            + "<sap:MessageId>aaaaaaaa-1111-2222-3333-000000000001</sap:MessageId>"
            + "<sap:TimeSent>2026-11-10T07:15:30Z</sap:TimeSent>"
            + "<sap:Sender><sap:Service>SenderA</sap:Service>"
            + "<sap:Interface namespace=\"urn:test:a\">SI_A_Out</sap:Interface></sap:Sender>"
            + "<sap:Receiver><sap:Service>ReceiverB</sap:Service></sap:Receiver>"
            + "<sap:Interface namespace=\"urn:test:a\">SI_A_Out</sap:Interface></sap:Main>"
            + "<sap:ReliableMessaging xmlns:sap=\"http://sap.com/xi/XI/Message/30\" SOAP:mustUnderstand=\"1\">"
            + "<sap:QualityOfService>ExactlyOnce</sap:QualityOfService></sap:ReliableMessaging>"
            + "</SOAP:Header><SOAP:Body>"
            + "<sap:Manifest xmlns:sap=\"http://sap.com/xi/XI/Message/30\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">"
            + "<sap:Payload xlink:type=\"simple\" xlink:href=\"cid:payload-1@test\"><sap:Name>MainDocument</sap:Name>"
            + "<sap:Description/><sap:Type>Application</sap:Type></sap:Payload></sap:Manifest>"
            + "</SOAP:Body></SOAP:Envelope>";

    static final String ORDER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ns0:Order xmlns:ns0=\"urn:test:a\">"
            + "<OrderNo>A-1</OrderNo></ns0:Order>";

    static PayloadPart text(int index, String text) {
        return new PayloadPart(index, "part-" + index + "@test", "text/xml; charset=utf-8",
                text.getBytes(StandardCharsets.UTF_8).length, text, null);
    }

    @Test
    void soapEnvelopeIsAnXiHeader() {
        assertThat(text(0, XI_HEADER).isXiHeader()).isTrue();
        assertThat(text(0, "<soapenv:Envelope xmlns:soapenv=\"" + SOAP_ENV + "\"><soapenv:Body/></soapenv:Envelope>")
                .isXiHeader()).as("another prefix").isTrue();
        assertThat(text(0, "<Envelope xmlns=\"" + SOAP_ENV + "\"><Body/></Envelope>").isXiHeader())
                .as("default namespace").isTrue();
    }

    @Test
    void applicationDocumentIsNotAnXiHeader() {
        assertThat(text(0, ORDER).isXiHeader()).isFalse();
        assertThat(text(0, "plain text, not XML").isXiHeader()).isFalse();
        assertThat(text(0, "").isXiHeader()).as("empty").isFalse();
    }

    @Test
    void envelopeInAnotherNamespaceIsNotAnXiHeader() {
        assertThat(text(0, "<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\"><env:Body/>"
                + "</env:Envelope>").isXiHeader()).as("SOAP 1.2").isFalse();
        assertThat(text(0, "<Envelope><Body/></Envelope>").isXiHeader()).as("no namespace").isFalse();
        assertThat(text(0, "<x:Body xmlns:x=\"" + SOAP_ENV + "\"/>").isXiHeader()).as("SOAP Body as root").isFalse();
    }

    @Test
    void malformedXmlIsNotAnXiHeader() {
        assertThat(text(0, "<SOAP:Envelope xmlns:SOAP=\"" + SOAP_ENV + "\"><SOAP:Header>").isXiHeader())
                .as("unclosed").isFalse();
        assertThat(text(0, "<SOAP:Envelope><SOAP:Body/></SOAP:Envelope>").isXiHeader()).as("unbound prefix")
                .isFalse();
        assertThat(text(0, XI_HEADER + "<trailing/>").isXiHeader()).as("two roots").isFalse();
    }

    @Test
    void base64PartIsNotAnXiHeader() {
        byte[] bytes = XI_HEADER.getBytes(StandardCharsets.UTF_8);
        PayloadPart binary = new PayloadPart(0, "soap-1@test", "application/octet-stream", bytes.length, null,
                Base64.getEncoder().encodeToString(bytes));

        assertThat(binary.isXiHeader()).isFalse();
    }

    @Test
    void doctypeIsNotAnXiHeaderAndResolvesNothing() throws IOException {
        List<String> fetched = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            fetched.add(exchange.getRequestURI().toString());
            byte[] body = "<!ENTITY inner \"resolved\">".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            String envelope = "<SOAP:Envelope xmlns:SOAP=\"" + SOAP_ENV + "\"><SOAP:Body>&x;</SOAP:Body>"
                    + "</SOAP:Envelope>";

            assertThat(text(0, "<!DOCTYPE SOAP:Envelope [<!ENTITY x SYSTEM \"" + base + "/entity\">]>" + envelope)
                    .isXiHeader()).as("external entity").isFalse();
            assertThat(text(0, "<!DOCTYPE SOAP:Envelope SYSTEM \"" + base + "/external.dtd\">"
                    + envelope.replace("&x;", "")).isXiHeader()).as("external DTD").isFalse();
            assertThat(text(0, "<!DOCTYPE SOAP:Envelope [<!ENTITY % p SYSTEM \"" + base + "/param\"> %p;]>"
                    + envelope.replace("&x;", "")).isXiHeader()).as("parameter entity").isFalse();
            assertThat(text(0, "<!DOCTYPE SOAP:Envelope [<!ENTITY x \"internal\">]>" + envelope).isXiHeader())
                    .as("internal entity").isFalse();
            assertThat(text(0, "<!DOCTYPE SOAP:Envelope>" + envelope.replace("&x;", "")).isXiHeader())
                    .as("bare DOCTYPE").isFalse();
        } finally {
            server.stop(0);
        }

        assertThat(fetched).as("nothing fetched").isEmpty();
    }
}
