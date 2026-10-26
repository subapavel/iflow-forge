package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A versioned subset of parsed MOIN source facts, not a byte-exact snapshot or runtime analysis.
 * Collection snapshots retain encounter order and explicit empty values.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BpmSourceEvidence(
        int formatVersion, String selectionVersion, List<String> indexedPartitions,
        List<String> processPartitions, List<Element> elements, List<Link> links) {

    public static final int FORMAT_VERSION = 1;
    public static final String SELECTION_VERSION = "bpm-source-v1";

    public BpmSourceEvidence {
        indexedPartitions = snapshot(indexedPartitions);
        processPartitions = snapshot(processPartitions);
        elements = snapshot(elements);
        links = snapshot(links);
    }

    /** One-based occurrence in the partition's element or link list, never a source line. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Locator(String archive, String partition, int ordinal) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Element(
            Locator location, String id, String metaclass, Map<String, String> attributes,
            Map<String, String> texts, Map<String, List<String>> values, List<Property> properties) {
        public Element {
            attributes = snapshot(attributes);
            texts = snapshot(texts);
            Map<String, List<String>> copiedValues = new LinkedHashMap<>();
            values.forEach((key, value) -> copiedValues.put(key, value == null ? null : snapshot(value)));
            values = Collections.unmodifiableMap(copiedValues);
            properties = snapshot(properties);
        }
    }

    /** Parsed property entries use records so a missing key remains JSON-serializable evidence. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Property(String key, String value) {
    }

    /** Whether this occurrence's full payload is included in the selected elements. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Candidate(Locator location, String metaclass, boolean included) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Endpoint(String id, String resolution, List<Candidate> candidates) {
        public Endpoint {
            candidates = snapshot(candidates);
        }
    }

    /** First and second retain the normalized MOIN f/s direction. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Link(Locator location, String association, Endpoint first, Endpoint second) {
    }

    private static <T> List<T> snapshot(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static <K, V> Map<K, V> snapshot(Map<K, V> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
