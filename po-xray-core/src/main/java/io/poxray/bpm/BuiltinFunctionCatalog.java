package io.poxray.bpm;

import io.poxray.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;

/**
 * NW BPM built-in function and standard trigger catalog (resource {@value #RESOURCE}, generated from NWDS by
 * {@code tools/gen_bpm_builtin_functions.py}, see {@code docs/BPM-BUILTIN-FUNCTIONS.md}).
 * Always keyed by MOF ID: the same name exists in several type categories with different IDs.
 * Derived from SAP software: private repositories only.
 */
final class BuiltinFunctionCatalog {

    static final String RESOURCE = "bpm-builtin-functions.json";

    record Parameter(String name, String type) {
    }

    record Entry(String id, String namespace, String category, String name, String symbol, String documentation,
            List<Parameter> parameters, String result, String javaClass) {
    }

    record TriggerParameter(String name, String namespace, String type) {
    }

    /** Standard SAP event definition (src/triggers of DC predefs/default); {@code kind}: error | message | timer. */
    record Trigger(String id, String kind, String name, String documentation, boolean isCritical,
            boolean isTechnical, TriggerParameter parameter, String file, String sourceDc) {
    }

    /** Provenance ({@code source}, {@code additionalSources}) is not interpreted. */
    private record Data(JsonNode source, List<Entry> functions, List<Trigger> triggers, Map<String, String> xsdTypes,
            Map<String, String> sapTypes) {
    }

    private static final BuiltinFunctionCatalog INSTANCE = load();

    private final Map<String, Entry> functions = new HashMap<>();
    private final Map<String, Trigger> triggers = new HashMap<>();
    private final Map<String, String> xsdTypes;
    private final Map<String, String> sapTypes;
    private final Set<String> xsdTypeNames;
    private final Set<String> sapTypeNames;

    private BuiltinFunctionCatalog(Data data) {
        for (Entry e : data.functions()) {
            functions.put(e.id(), e);
        }
        if (data.triggers() != null) {
            for (Trigger t : data.triggers()) {
                triggers.put(t.id(), t);
            }
        }
        this.xsdTypes = Map.copyOf(data.xsdTypes());
        this.sapTypes = data.sapTypes() == null ? Map.of() : Map.copyOf(data.sapTypes());
        this.xsdTypeNames = Set.copyOf(xsdTypes.values());
        this.sapTypeNames = Set.copyOf(sapTypes.values());
    }

    static BuiltinFunctionCatalog get() {
        return INSTANCE;
    }

    Optional<Entry> function(String id) {
        return Optional.ofNullable(id == null ? null : functions.get(id));
    }

    /** Standard SAP trigger (event definition not contained in the customer DC). */
    Optional<Trigger> trigger(String id) {
        return Optional.ofNullable(id == null ? null : triggers.get(id));
    }

    /**
     * Type name for a type MOF ID: XSD built-in types ({@code xsd:string}), then SAP-defined types of the BPM
     * content ({@code sap:ProcessInstanceAttributes}); null if unknown.
     */
    String typeName(String id) {
        if (id == null) {
            return null;
        }
        String xsd = xsdTypes.get(id);
        if (xsd != null) {
            return "xsd:" + xsd;
        }
        String sap = sapTypes.get(id);
        return sap == null ? null : "sap:" + sap;
    }

    /**
     * Qualifies a plain type name as used for catalog parameters/results: {@code xsd:} if it is an XSD type
     * (preferred, some names exist in both), {@code sap:} if it is an SAP type, else unchanged.
     */
    String qualify(String typeName) {
        if (typeName == null) {
            return null;
        }
        if (xsdTypeNames.contains(typeName)) {
            return "xsd:" + typeName;
        }
        return sapTypeNames.contains(typeName) ? "sap:" + typeName : typeName;
    }

    private static BuiltinFunctionCatalog load() {
        try (InputStream in = BuiltinFunctionCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource io/poxray/bpm/" + RESOURCE
                        + " (generate it with tools/gen_bpm_builtin_functions.py)");
            }
            Data data = Json.mapper().rebuild()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build()
                    .readValue(in, Data.class);
            return new BuiltinFunctionCatalog(data);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }
}
