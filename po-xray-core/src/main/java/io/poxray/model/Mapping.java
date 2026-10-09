package io.poxray.model;

import java.util.List;

/** Data mapping: every MappingPart flattened to {@code target <- expr} with its flags. */
public record Mapping(String id, String name, List<Assignment> assignments) {

    /**
     * @param target      full target path incl. root element
     * @param category    set | merge | append (always present)
     * @param expander    currentNode | moveCorresponding (always present; moveCorresponding = fields with equal
     *                    names are copied automatically)
     * @param transformer getValue | discardValue (always present)
     * @param enabled     only present when false (part disabled in the editor)
     * @param expr        absent for structural parts without an expression
     */
    public record Assignment(
            String target, String category, String expander, String transformer, Boolean enabled, Expression expr) {
    }
}
