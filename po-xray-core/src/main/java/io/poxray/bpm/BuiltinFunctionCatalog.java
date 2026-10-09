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
 * NW BPM built-in function catalog (resource {@value #RESOURCE}, generated from NWDS by
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

    /** Provenance ({@code source}, {@code additionalSources}) is not interpreted. */
    private record Data(JsonNode source, List<Entry> functions, Map<String, String> xsdTypes,
            Map<String, String> sapTypes) {
    }

    /** W3C XML Schema built-in datatypes: rendered with the {@code xsd:} prefix, other catalog types as is. */
    private static final Set<String> XSD_DATATYPES = Set.of("string", "boolean", "decimal", "float", "double",
            "duration", "dateTime", "time", "date", "gYearMonth", "gYear", "gMonthDay", "gDay", "gMonth", "hexBinary",
            "base64Binary", "anyURI", "QName", "NOTATION", "normalizedString", "token", "language", "NMTOKEN",
            "NMTOKENS", "Name", "NCName", "ID", "IDREF", "IDREFS", "ENTITY", "ENTITIES", "integer",
            "nonPositiveInteger", "negativeInteger", "long", "int", "short", "byte", "nonNegativeInteger",
            "unsignedLong", "unsignedInt", "unsignedShort", "unsignedByte", "positiveInteger", "anyType",
            "anySimpleType");

    private static final BuiltinFunctionCatalog INSTANCE = load();

    private final Map<String, Entry> functions = new HashMap<>();
    private final Map<String, String> xsdTypes;
    private final Map<String, String> sapTypes;

    private BuiltinFunctionCatalog(Data data) {
        for (Entry e : data.functions()) {
            functions.put(e.id(), e);
        }
        this.xsdTypes = Map.copyOf(data.xsdTypes());
        this.sapTypes = data.sapTypes() == null ? Map.of() : Map.copyOf(data.sapTypes());
    }

    static BuiltinFunctionCatalog get() {
        return INSTANCE;
    }

    Optional<Entry> function(String id) {
        return Optional.ofNullable(id == null ? null : functions.get(id));
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

    /** {@code xsd:} prefix for W3C datatypes (catalog parameter/result types are plain names). */
    static String qualify(String typeName) {
        return typeName != null && XSD_DATATYPES.contains(typeName) ? "xsd:" + typeName : typeName;
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
