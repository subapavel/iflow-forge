package io.poxray.monitoring;

/**
 * The description of an error label (F-17 Appendix C {@code StatusDetail}: errorLabelID, description, text; namespace
 * WS); {@code errorLabelId} is kept as the text the service sent.
 */
public record StatusDetail(String errorLabelId, String description, String text) {
}
