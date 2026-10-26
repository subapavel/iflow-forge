package io.poxray.model;

import java.util.List;

/** One Process Composer DC: its processes and the DC-level catalog they reference. */
public record BpmComponent(String dc, List<Process> processes, Catalog catalog, BpmSourceEvidence sourceEvidence) {

    /** Inventories created without parsed-source evidence retain an unavailable (null) extension. */
    public BpmComponent(String dc, List<Process> processes, Catalog catalog) {
        this(dc, processes, catalog, null);
    }

    public record Catalog(
            List<Interface> interfaces,
            List<ServiceReference> serviceReferences,
            List<Function> functions,
            List<BuiltinFunction> builtinFunctions,
            List<EventDefinition> eventDefinitions,
            List<EventDefinition> builtinEventDefinitions,
            List<DataType> dataTypes) {
    }
}
