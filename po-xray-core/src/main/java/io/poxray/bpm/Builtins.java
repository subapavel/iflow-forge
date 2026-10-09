package io.poxray.bpm;

import java.util.Map;

/**
 * Static lookup tables for SAP built-in objects referenced from Galaxy models but not contained in the DC.
 * Every entry is backed by evidence (see comments); unknown IDs are reported, never guessed.
 */
final class Builtins {

    private Builtins() {
    }

    /**
     * Built-in function interface IDs; source: BPMN 2.0 export of DigiSign (docs/HANDOFF.md §2.5), plus
     * numeric-less-than from DigiSign expression text {@code DO_TagsIndex < DO_TagsCount}. Further names are
     * learned from {@code stringExpression} at runtime ({@link FunctionNames}).
     */
    static final Map<String, String> FUNCTIONS = Map.of(
            "481F3B2A912070301AC311DDB6990018717335EB", "and",
            "481F3B2B916CBB701AC311DD9D840018717335EB", "or",
            "481F3B2A9148DFC11AC311DD9AF50018717335EB", "boolean-equal",
            "481F3B2E933B39E31AC311DDAFAD0018717335EB", "numeric-equal",
            "481F3B2E93496AB01AC311DDAC420018717335EB", "numeric-greater-than",
            "481F3B2E935578A21AC311DDCA830018717335EB", "numeric-less-than",
            "481F3B3295D1EA511AC311DD8C180018717335EB", "string-equal",
            "481F3B3295F355001AC311DD9D850018717335EB", "string-length",
            "49182A9D15FC90A0AF2411DDB43A0019991C7C01", "get",
            "492D235E1CAE0460BBA411DDA2350019991C7C01", "isSet");

    /** Built-in functions the editor writes as infix operators (not as {@code name(}); same evidence as above. */
    static final Map<String, String> INFIX = Map.of(
            "481F3B2E935578A21AC311DDCA830018717335EB", "<");

    /**
     * Built-in XSD / SAP type IDs; source: element names in DigiSign {@code rep/wsdl/*.types} matched with the
     * original {@code src/wsdl/*.wsdl} declarations (orgId → string, signable → boolean, size → integer,
     * contentSize → long, content → base64Binary, complex type restriction base → anyType).
     */
    static final Map<String, String> TYPES = Map.of(
            "4743E046A4700C52980411DCADE30011252E548A", "xsd:string",
            "4743E048A5B22940980411DC83220011252E548A", "xsd:boolean",
            "4743E048A5DE6962980411DC98C90011252E548A", "xsd:integer",
            "4743E048A5E60A85980411DCA1E50011252E548A", "xsd:long",
            "4743E048A5BFE4E8980411DCA1E70011252E548A", "xsd:base64Binary",
            "4743E048A5AC0EC1980411DCB60A0011252E548A", "xsd:anyType",
            "E007956A29DF6A4AF91911E28D894437E68365F0", "sap:TechnicalErrorParameters",
            "E02C978BF5A4C4FAE11511DFC9DB0019993BA185", "sap:ProcessInstanceAttributes",
            "481F3B278F75A6601AC311DDBFB00018717335EB", "sap:Principals");

    /** XSD type of a literal rendered with quotes. */
    static final String STRING_TYPE = "xsd:string";

    /** Built-in triggers; source: DigiSign boundary events named "TechnicalError". */
    static final Map<String, String> TRIGGERS = Map.of(
            "E0EE4DB2B3D39C3FEAEE11E2BF54001999A12632", "TECHNICAL_ERROR");
}
