package io.poxray.model;

import java.util.List;

/**
 * SAP built-in function used by the DC's processes, from the NW BPM built-in function catalog.
 * {@code documentation} is SAP's description of the semantics (relevant for the redesign).
 */
public record BuiltinFunction(
        String id,
        String name,
        String category,
        String symbol,
        List<Function.Parameter> parameters,
        String result,
        String documentation,
        String javaClass) {
}
