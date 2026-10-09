package io.poxray.model;

import java.util.List;

/**
 * One function invocation in an expression (pre-order, i.e. in text order).
 *
 * @param id        MOF ID of the function interface (always present, also when unknown)
 * @param category  built-in type category (operand type, e.g. finite-integer); null for DC functions
 * @param source    builtin | dc-function | unknown
 * @param arguments parameter mapping parts, ordered by the formal parameters
 */
public record FunctionCall(String id, String name, String category, String source, List<Argument> arguments) {

    /**
     * A parameter mapping part. {@code text} is rendered from the tree; the flags are always present
     * ({@code expander = moveCorresponding}: fields with equal names are copied automatically).
     */
    public record Argument(
            String parameter, String text, String category, String expander, String transformer, Boolean enabled) {
    }
}
