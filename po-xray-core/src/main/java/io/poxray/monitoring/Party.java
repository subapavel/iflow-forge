package io.poxray.monitoring;

import org.w3c.dom.Element;

/** A {@code MessageParty} of the monitoring service (F-17 Appendix C: agency, name, schema; namespace DATA). */
public record Party(String agency, String name, String schema) {

    /** Reads a {@code MessageParty} element; a child that is absent or nil is null. */
    static Party read(Element e) {
        return new Party(Soap.childText(e, Soap.DATA, "agency"), Soap.childText(e, Soap.DATA, "name"),
                Soap.childText(e, Soap.DATA, "schema"));
    }

    /** Appends the children of a {@code MessageParty}, unset ones omitted. */
    void writeTo(StringBuilder out) {
        Soap.element(out, "d", "agency", agency);
        Soap.element(out, "d", "name", name);
        Soap.element(out, "d", "schema", schema);
    }
}
