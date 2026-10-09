package io.poxray.bpm;

import java.util.Map;

/**
 * SAP objects referenced from Galaxy models that are neither in the DC nor in the built-in function catalog
 * ({@link BuiltinFunctionCatalog}, which also holds the XSD and SAP types). Every entry is backed by evidence;
 * unknown IDs are reported, never guessed.
 */
final class Builtins {

    private Builtins() {
    }

    /**
     * Standard SAP triggers; source: NWDS {@code com.sap.tc.ap.bpemcontent_*.jar} → DC
     * {@code sap.com/tc/bpem/content/predefs/default} → {@code src/triggers/technicalerror.eventdef}
     * (DigiSign boundary events named "TechnicalError").
     */
    static final Map<String, String> TRIGGERS = Map.of(
            "E0EE4DB2B3D39C3FEAEE11E2BF54001999A12632", "TechnicalError");
}
