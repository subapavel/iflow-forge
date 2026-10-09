package io.poxray.model;

import java.util.Map;

/** Source element whose metaclass the reader does not interpret (kept so nothing is silently dropped). */
public record UnmappedElement(String dc, String metaclass, String id, String partition, Map<String, String> attributes) {
}
