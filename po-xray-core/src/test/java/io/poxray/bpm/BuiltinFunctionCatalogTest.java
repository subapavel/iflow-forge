package io.poxray.bpm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BuiltinFunctionCatalogTest {

    private final BuiltinFunctionCatalog catalog = BuiltinFunctionCatalog.get();

    @Test
    void containsTheIdsDerivedEarlierFromBpmn2Exports() {
        // docs/HANDOFF.md §2.5 (+ numeric-less-than from the DigiSign editor text "DO_TagsIndex < DO_TagsCount")
        Map<String, String> expected = Map.of(
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
        expected.forEach((id, name) -> assertThat(catalog.function(id)).as(id).hasValueSatisfying(
                f -> assertThat(f.name()).isEqualTo(name)));
    }

    @Test
    void entriesCarrySymbolCategoryParametersAndTypes() {
        BuiltinFunctionCatalog.Entry lessThan = catalog.function("481F3B2E935578A21AC311DDCA830018717335EB").orElseThrow();
        assertThat(lessThan.symbol()).isEqualTo("<");
        assertThat(lessThan.category()).isEqualTo("finite-integer");
        assertThat(lessThan.parameters()).extracting(BuiltinFunctionCatalog.Parameter::name)
                .containsExactly("left", "right");
        assertThat(lessThan.documentation()).isNotBlank();

        assertThat(catalog.typeName("4743E046A4700C52980411DCADE30011252E548A")).isEqualTo("xsd:string");
        assertThat(catalog.typeName("E02C978BF5A4C4FAE11511DFC9DB0019993BA185")).isEqualTo("sap:ProcessInstanceAttributes");
        assertThat(catalog.typeName("0000")).isNull();

        // principal functions from DC predefs/default; result is an SAP type, parameters XSD types
        BuiltinFunctionCatalog.Entry principals = catalog.function("481F3B33967D93A01AC311DDCFD20018717335EB").orElseThrow();
        assertThat(principals.name()).isEqualTo("getPrincipals");
        assertThat(principals.category()).isEqualTo("principal");
        assertThat(catalog.qualify(principals.result())).isEqualTo("sap:Principal");
        assertThat(catalog.qualify("string")).isEqualTo("xsd:string"); // XSD preferred over SOAP-enc duplicate
        assertThat(catalog.function("0000")).isEmpty();
    }
}
