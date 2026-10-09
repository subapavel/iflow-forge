package io.poxray.model;

import java.util.List;

/** XSD element declaration used as a data object type, flattened to field paths. */
public record DataType(String id, String name, String namespace, String type, List<Field> fields) {

    /** {@code max}: number or {@code unbounded}; min/max omitted when 1. */
    public record Field(String path, String type, Integer min, String max) {
    }
}
