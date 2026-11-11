package io.poxray.monitoring;

import java.util.List;

/**
 * The payload of one message from the system named {@code system}: the message key, the {@code source}
 * ({@code current}, {@code version:<n>} or {@code logged:<v>}), the bytes the service sent (base64-decoded; null when
 * it answered nil), the MIME parts ({@link Mime#parts}; never null), a {@code note} when there are no parts
 * ({@code "not multipart"}, or {@code "no payload"} for a nil answer) and the SOAP answer as received.
 */
public record MessagePayload(String system, String messageKey, String source, byte[] bytes, List<PayloadPart> parts,
        String note, String rawResponse) {

    public MessagePayload {
        bytes = bytes == null ? null : bytes.clone();
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    /** A copy of the payload bytes, null when there are none. */
    @Override
    public byte[] bytes() {
        return bytes == null ? null : bytes.clone();
    }
}
