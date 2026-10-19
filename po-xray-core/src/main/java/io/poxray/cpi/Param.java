package io.poxray.cpi;

/**
 * An externalized parameter declared in {@code parameters.propdef}. A configured value (from
 * {@code parameters.prop}) is never stored, only the fact that one exists.
 *
 * @param name         the parameter name
 * @param defaultValue text of the declaration's {@code <value>} child; empty when there is none
 * @param configured   true when {@code parameters.prop} has a key with this name
 */
public record Param(String name, String defaultValue, boolean configured) {
}
