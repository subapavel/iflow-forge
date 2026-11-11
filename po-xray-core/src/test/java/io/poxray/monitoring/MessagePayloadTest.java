package io.poxray.monitoring;

import static io.poxray.monitoring.PayloadPartTest.ORDER;
import static io.poxray.monitoring.PayloadPartTest.XI_HEADER;
import static io.poxray.monitoring.PayloadPartTest.text;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link MessagePayload#defaultPart()}: the first part that is not an XI header (F-18), not a position. */
class MessagePayloadTest {

    private static final String ATTACHMENT = "<attachment><no>B-2</no></attachment>";

    private static MessagePayload payload(PayloadPart... parts) {
        return new MessagePayload("QA1", "key-0001", "current", "bytes".getBytes(StandardCharsets.UTF_8),
                List.of(parts), parts.length == 0 ? "not multipart" : null, "<raw/>");
    }

    @Test
    void onePartIsTheDefault() {
        assertThat(payload(text(0, ORDER)).defaultPart()).isZero();
    }

    @Test
    void headerThenMainGivesTheMain() {
        assertThat(payload(text(0, XI_HEADER), text(1, ORDER)).defaultPart()).isEqualTo(1);
    }

    @Test
    void mainThenAttachmentGivesTheMain() {
        assertThat(payload(text(0, ORDER), text(1, ATTACHMENT)).defaultPart()).isZero();
    }

    @Test
    void headerMainAndAttachmentGivesTheMain() {
        assertThat(payload(text(0, XI_HEADER), text(1, ORDER), text(2, ATTACHMENT)).defaultPart()).isEqualTo(1);
    }

    @Test
    void onlyAHeaderGivesTheHeader() {
        assertThat(payload(text(0, XI_HEADER)).defaultPart()).isZero();
        assertThat(payload(text(0, XI_HEADER), text(1, XI_HEADER)).defaultPart()).as("two headers").isZero();
    }

    @Test
    void firstNonHeaderAfterSeveralHeaders() {
        assertThat(payload(text(0, XI_HEADER), text(1, XI_HEADER), text(2, ORDER)).defaultPart()).isEqualTo(2);
    }

    @Test
    void binaryFirstPartIsNotSkipped() {
        byte[] bytes = {(byte) 0x89, 'P', 'N', 'G'};
        PayloadPart binary = new PayloadPart(0, "image-1@test", "image/png", bytes.length, null,
                Base64.getEncoder().encodeToString(bytes));

        assertThat(payload(binary, text(1, ORDER)).defaultPart()).isZero();
    }

    /** The JSON keys of a record serialised by {@link io.poxray.json.Json}. */
    private static java.util.Set<String> jsonKeys(Object value) {
        return new java.util.TreeSet<>(io.poxray.json.Json.mapper().readTree(io.poxray.json.Json.write(value))
                .propertyNames());
    }

    @Test
    void jsonShapeHasNoDerivedProperties() {
        PayloadPart header = text(0, XI_HEADER);
        byte[] bytes = {1, 2, 3};
        PayloadPart binary = new PayloadPart(1, "image-1@test", "image/png", bytes.length, null,
                Base64.getEncoder().encodeToString(bytes));
        MessagePayload payload = new MessagePayload("QA1", "key-0001", "current", bytes, List.of(header, binary),
                "a note", "<raw/>");
        assertThat(header.isXiHeader()).isTrue();
        assertThat(payload.defaultPart()).isEqualTo(1);

        assertThat(jsonKeys(header)).containsExactly("contentId", "contentType", "index", "size", "text");
        assertThat(jsonKeys(binary)).containsExactly("base64", "contentId", "contentType", "index", "size");
        assertThat(jsonKeys(payload)).containsExactly("bytes", "messageKey", "note", "parts", "rawResponse",
                "source", "system");
        assertThat(io.poxray.json.Json.write(payload)).doesNotContain("xiHeader").doesNotContain("XiHeader")
                .doesNotContain("defaultPart");
    }

    @Test
    void noPartsGivesMinusOne() {
        assertThat(payload().defaultPart()).isEqualTo(-1);
        assertThat(new MessagePayload("QA1", "key-0001", "current", null, null, "no payload", "<raw/>").defaultPart())
                .as("nil payload").isEqualTo(-1);
    }
}
