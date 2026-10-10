package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Custom function used in expressions ({@code src/functions/**}/*.function). */
public record Function(
        String id,
        String name,
        String namespace,
        String kind,
        String lookupName,
        EjbLink ejb,
        List<Parameter> parameters,
        Parameter result,
        String source,
        List<Usage> usedBy) {

    /**
     * One call of the function: process, element (step, flow, role or data object) with its kind, the part
     * ({@code inputMapping}, {@code outputMapping}, {@code condition}, {@code assignment}, {@code initialValue}),
     * the mapping target (null for conditions) and the whole expression.
     */
    public record Usage(String process, Ref element, String elementKind, String part, String target,
            String expression) {
    }

    /** Parsed from {@code lookupName}: {@code <application>/LOCAL|REMOTE/<bean>/<interface>}. */
    public record EjbLink(String application, String bean, @JsonProperty("interface") String iface) {
    }

    public record Parameter(String name, String type) {
    }
}
