package io.poxray.model;

import java.util.List;

/** One Process Composer DC: its processes and the DC-level catalog they reference. */
public record BpmComponent(String dc, List<Process> processes, Catalog catalog) {

    public record Catalog(
            List<Interface> interfaces,
            List<ServiceReference> serviceReferences,
            List<Function> functions,
            List<BuiltinFunction> builtinFunctions,
            List<EventDefinition> eventDefinitions,
            List<DataType> dataTypes) {
    }
}
