package io.poxray.monitoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mime.parts on synthetic XI 3.0 style multipart messages (leading headers, then MIME parts). */
class MimeTest {

    static final String BOUNDARY = "SAP_test-boundary-0001_END";
    static final String HEADER_PART = "<SOAP:Envelope xmlns:SOAP=\"http://schemas.xmlsoap.org/soap/envelope/\">"
            + "<SOAP:Header><t:Main xmlns:t=\"urn:test:header\"><t:MessageId>msg-0001</t:MessageId></t:Main>"
            + "</SOAP:Header><SOAP:Body/></SOAP:Envelope>";
    static final String PAYLOAD = "<order><no>A-1</no><item>Widget</item></order>";
    private static final Charset LATIN2 = Charset.forName("ISO-8859-2");

    /** One MIME part: its header lines (without the blank line) and its body bytes. */
    record Part(List<String> headers, byte[] body) {
    }

    /**
     * A message: the leading headers ({@code Content-Type: multipart/related; boundary=...; type=...; start=...}), a
     * blank line, then each part as {@code --boundary CRLF headers CRLF CRLF body CRLF}, closed by
     * {@code --boundary--}. All line ends CRLF.
     */
    static byte[] multipart(Part... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ascii(out, "Content-Type: multipart/related; boundary=" + BOUNDARY
                + "; type=\"text/xml\"; start=\"<soap-1@test>\"\r\n");
        ascii(out, "MIME-Version: 1.0\r\n");
        ascii(out, "\r\n");
        for (Part p : parts) {
            ascii(out, "--" + BOUNDARY + "\r\n");
            for (String h : p.headers()) {
                ascii(out, h + "\r\n");
            }
            ascii(out, "\r\n");
            out.writeBytes(p.body());
            ascii(out, "\r\n");
        }
        ascii(out, "--" + BOUNDARY + "--\r\n");
        return out.toByteArray();
    }

    private static void ascii(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
    }

    /** The SOAP header part with mixed-case header names, as the first part of every test message. */
    static Part headerPart() {
        byte[] body = HEADER_PART.getBytes(StandardCharsets.UTF_8);
        return new Part(List.of("Content-ID: <soap-1@test>", "Content-Type: text/xml; charset=utf-8",
                "Content-Length: " + body.length), body);
    }

    /** A payload part with lower-case header names and no space after the colon, as XI writes them. */
    static Part payloadPart(String contentId, String contentType, byte[] body) {
        return new Part(List.of("content-id:<" + contentId + ">", "content-type:" + contentType,
                "content-length:" + body.length), body);
    }

    /** The XI message of the splits test: the SOAP header part and one XML payload part. */
    static byte[] xiMessage() {
        return multipart(headerPart(),
                payloadPart("payload-1@test", "application/xml", PAYLOAD.getBytes(StandardCharsets.UTF_8)));
    }

    /** The content of a part, from whichever of text/base64 is set. */
    private static byte[] content(PayloadPart p, Charset charset) {
        return p.text() != null ? p.text().getBytes(charset) : Base64.getDecoder().decode(p.base64());
    }

    @Test
    void splitsHeaderAndPayloadParts() {
        List<PayloadPart> parts = Mime.parts(xiMessage());

        assertThat(parts).hasSize(2);
        PayloadPart header = parts.get(0);
        PayloadPart payload = parts.get(1);

        assertThat(header.index()).isIn(0, 1);
        assertThat(payload.index()).isEqualTo(header.index() + 1);

        assertThat(header.contentId()).isIn("<soap-1@test>", "soap-1@test");
        assertThat(header.contentType()).isEqualTo("text/xml; charset=utf-8");
        assertThat(header.size()).isEqualTo(HEADER_PART.getBytes(StandardCharsets.UTF_8).length);
        assertThat(header.text()).isEqualTo(HEADER_PART);
        assertThat(header.base64()).isNull();

        assertThat(payload.contentId()).isIn("<payload-1@test>", "payload-1@test");
        assertThat(payload.contentType()).isEqualTo("application/xml");
        assertThat(payload.size()).isEqualTo(PAYLOAD.getBytes(StandardCharsets.UTF_8).length);
        assertThat(payload.text() == null).as("exactly one of text/base64").isNotEqualTo(payload.base64() == null);
        assertThat(content(payload, StandardCharsets.UTF_8)).isEqualTo(PAYLOAD.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void declaredCharsetIsHonoured() {
        String text = "Dobrý den, čtvrtek";
        byte[] latin2 = text.getBytes(LATIN2);
        assertThat(latin2).hasSize(text.length()); // one byte per character in ISO-8859-2

        List<PayloadPart> parts = Mime.parts(multipart(headerPart(),
                payloadPart("payload-1@test", "text/plain; charset=ISO-8859-2", latin2)));

        assertThat(parts).hasSize(2);
        PayloadPart p = parts.get(1);
        assertThat(p.contentType()).isEqualTo("text/plain; charset=ISO-8859-2");
        assertThat(p.text()).isEqualTo(text).contains("č");
        assertThat(p.base64()).isNull();
        assertThat(p.size()).isEqualTo(latin2.length);
    }

    @Test
    void binaryPartIsBase64() {
        byte[] all = new byte[256];
        for (int i = 0; i < all.length; i++) {
            all[i] = (byte) i;
        }

        List<PayloadPart> parts = Mime.parts(multipart(headerPart(),
                payloadPart("attachment-1@test", "application/octet-stream", all)));

        assertThat(parts).hasSize(2);
        PayloadPart p = parts.get(1);
        assertThat(p.contentId()).isIn("<attachment-1@test>", "attachment-1@test");
        assertThat(p.contentType()).isEqualTo("application/octet-stream");
        assertThat(p.size()).isEqualTo(256L);
        assertThat(p.text()).isNull();
        assertThat(p.base64()).isNotNull();
        assertThat(Base64.getDecoder().decode(p.base64())).isEqualTo(all);
    }

    @Test
    void nonMultipartGivesNoParts() {
        assertThat(Mime.parts(PAYLOAD.getBytes(StandardCharsets.UTF_8))).isNotNull().isEmpty();
        assertThat(Mime.parts("Content-Type: text/xml\r\n\r\n<order/>".getBytes(StandardCharsets.US_ASCII)))
                .isNotNull().isEmpty();
        assertThat(Mime.parts(new byte[0])).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------------------------------ review round 2

    @Test
    void textPartWithUnknownCharsetIsBase64() {
        byte[] body = "plain words".getBytes(StandardCharsets.US_ASCII);

        List<PayloadPart> parts = Mime.parts(multipart(headerPart(),
                payloadPart("payload-1@test", "text/plain; charset=X-UNKNOWN-CHARSET-42", body)));

        assertThat(parts).hasSize(2);
        PayloadPart p = parts.get(1);
        assertThat(p.text()).isNull();
        assertThat(p.base64()).isNotNull();
        assertThat(Base64.getDecoder().decode(p.base64())).isEqualTo(body);
    }

    @Test
    void textPartThatDoesNotDecodeInItsCharsetIsBase64() {
        byte[] body = {'a', (byte) 0xC3, (byte) 0x28, 'b'}; // invalid UTF-8 sequence

        List<PayloadPart> parts = Mime.parts(multipart(headerPart(),
                payloadPart("payload-1@test", "text/plain; charset=utf-8", body)));

        PayloadPart p = parts.get(1);
        assertThat(p.text()).isNull();
        assertThat(Base64.getDecoder().decode(p.base64())).isEqualTo(body);
    }

    @Test
    void indexStartsAtZeroAndContentIdHasNoBrackets() {
        List<PayloadPart> parts = Mime.parts(xiMessage());

        assertThat(parts).extracting(PayloadPart::index).containsExactly(0, 1);
        assertThat(parts).extracting(PayloadPart::contentId).containsExactly("soap-1@test", "payload-1@test");
    }

    // ------------------------------------------------------------------------------------------ review round 3

    @Test
    void xmlAndJsonPartsAreText() {
        String xml = "<order><no>A-1</no></order>";
        String json = "{\"order\":\"A-1\",\"text\":\"Grüße\"}";
        String soap = "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\"><e:Body/></e:Envelope>";
        String problem = "{\"title\":\"Not found\",\"status\":404}";

        List<PayloadPart> parts = Mime.parts(multipart(headerPart(),
                payloadPart("p1@test", "application/xml", xml.getBytes(StandardCharsets.UTF_8)),
                payloadPart("p2@test", "application/json", json.getBytes(StandardCharsets.UTF_8)),
                payloadPart("p3@test", "application/soap+xml", soap.getBytes(StandardCharsets.UTF_8)),
                payloadPart("p4@test", "application/problem+json", problem.getBytes(StandardCharsets.UTF_8))));

        assertThat(parts).hasSize(5);
        assertThat(parts.subList(1, 5)).extracting(PayloadPart::text).containsExactly(xml, json, soap, problem);
        assertThat(parts.subList(1, 5)).extracting(PayloadPart::base64).containsOnlyNulls();
    }

    @Test
    void nonTextMediaTypeIsBase64() {
        byte[] pdf = "%PDF-1.4 synthetic".getBytes(StandardCharsets.US_ASCII);

        List<PayloadPart> parts = Mime.parts(multipart(headerPart(), payloadPart("doc-1@test", "application/pdf", pdf)));

        PayloadPart p = parts.get(1);
        assertThat(p.text()).isNull();
        assertThat(p.base64()).isNotNull();
        assertThat(Base64.getDecoder().decode(p.base64())).isEqualTo(pdf);
    }

    private static byte[] latin1(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void lfOnlyLineEndsAreSplit() {
        byte[] msg = latin1("Content-Type: multipart/related; boundary=" + BOUNDARY + "\n\n"
                + "--" + BOUNDARY + "\ncontent-id:<p1@test>\ncontent-type:text/plain\n\nfirst\n"
                + "--" + BOUNDARY + "\ncontent-id:<p2@test>\ncontent-type:text/plain\n\nsecond\n"
                + "--" + BOUNDARY + "--\n");

        List<PayloadPart> parts = Mime.parts(msg);

        assertThat(parts).extracting(PayloadPart::contentId).containsExactly("p1@test", "p2@test");
        assertThat(parts).extracting(PayloadPart::text).containsExactly("first", "second");
        assertThat(parts).extracting(PayloadPart::size).containsExactly(5L, 6L);
    }

    @Test
    void foldedContentTypeHeaderIsUnfolded() {
        byte[] msg = latin1("Content-Type: multipart/related;\r\n\ttype=\"text/xml\";\r\n boundary=" + BOUNDARY
                + "\r\n\r\n--" + BOUNDARY + "\r\ncontent-type: text/plain;\r\n charset=ISO-8859-2\r\n\r\n");
        byte[] body = "čtvrtek".getBytes(LATIN2);
        byte[] tail = latin1("\r\n--" + BOUNDARY + "--\r\n");
        byte[] all = new byte[msg.length + body.length + tail.length];
        System.arraycopy(msg, 0, all, 0, msg.length);
        System.arraycopy(body, 0, all, msg.length, body.length);
        System.arraycopy(tail, 0, all, msg.length + body.length, tail.length);

        List<PayloadPart> parts = Mime.parts(all);

        assertThat(parts).hasSize(1);
        assertThat(parts.getFirst().text()).isEqualTo("čtvrtek");
        assertThat(parts.getFirst().contentType()).contains("text/plain").contains("charset=ISO-8859-2");
    }

    @Test
    void quotedBoundaryIsHonoured() {
        String boundary = "SAP_quoted=boundary;1";
        byte[] msg = latin1("Content-Type: multipart/related; boundary=\"" + boundary + "\"; type=\"text/xml\"\r\n\r\n"
                + "--" + boundary + "\r\ncontent-type:text/plain\r\n\r\nonly\r\n--" + boundary + "--\r\n");

        List<PayloadPart> parts = Mime.parts(msg);

        assertThat(parts).extracting(PayloadPart::text).containsExactly("only");
    }

    @Test
    void preambleIsIgnored() {
        byte[] msg = latin1("Content-Type: multipart/related; boundary=" + BOUNDARY + "\r\n\r\n"
                + "This is a preamble, not a part.\r\n"
                + "--" + BOUNDARY + "\r\ncontent-type:text/plain\r\n\r\nbody\r\n--" + BOUNDARY + "--\r\n");

        List<PayloadPart> parts = Mime.parts(msg);

        assertThat(parts).extracting(PayloadPart::text).containsExactly("body");
    }

    @Test
    void missingCloseDelimiterKeepsLastPart() {
        byte[] msg = latin1("Content-Type: multipart/related; boundary=" + BOUNDARY + "\r\n\r\n"
                + "--" + BOUNDARY + "\r\ncontent-type:text/plain\r\n\r\nfirst\r\n"
                + "--" + BOUNDARY + "\r\ncontent-type:text/plain\r\n\r\nlast part, cut off");

        List<PayloadPart> parts = Mime.parts(msg);

        assertThat(parts).extracting(PayloadPart::text).containsExactly("first", "last part, cut off");
    }
}
