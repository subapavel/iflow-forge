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
        String source) {

    /** Parsed from {@code lookupName}: {@code <application>/LOCAL|REMOTE/<bean>/<interface>}. */
    public record EjbLink(String application, String bean, @JsonProperty("interface") String iface) {
    }

    public record Parameter(String name, String type) {
    }
}
