package io.poxray.monitoring;

/**
 * One MIME part of a message payload: {@code index} from 0 in the order of the message, {@code contentId} without
 * the surrounding {@code <>}, {@code contentType} as declared (null if the part declares none), {@code size} the
 * number of content bytes, and exactly one of {@code text} (the content decoded in the declared charset) and
 * {@code base64} (the content bytes) set.
 */
public record PayloadPart(int index, String contentId, String contentType, long size, String text, String base64) {

    public PayloadPart {
        if ((text == null) == (base64 == null)) {
            throw new IllegalArgumentException("exactly one of text and base64 must be set (part " + index + ")");
        }
    }
}
