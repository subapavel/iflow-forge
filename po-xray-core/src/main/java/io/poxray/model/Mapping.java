package io.poxray.model;

import java.util.List;

/** Data mapping flattened to assignments {@code target <- expr}; defaults are omitted (null). */
public record Mapping(String id, String name, List<Assignment> assignments) {

    /**
     * @param target      full target path incl. root element
     * @param mode        MappingPart {@code category}, default {@code set}
     * @param transformer default {@code getValue}
     * @param expander    default {@code currentNode}
     * @param enabled     default true
     */
    public record Assignment(
            String target, String mode, String transformer, String expander, Boolean enabled, Expression expr) {
    }
}
