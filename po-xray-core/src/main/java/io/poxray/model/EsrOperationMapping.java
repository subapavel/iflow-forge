package io.poxray.model;

/**
 * An operation mapping of the ESR (PI / PO) called from NW BPM: the composite's {@code implementation.piom} component
 * exposes it as a service ({@code sapextensions omguid / svguid}), a service reference of the process calls it.
 * {@code oid} / {@code swcGuid} identify the object in the ESR exactly (read it with the PO system, e.g. MCP
 * read_object); {@code name} / {@code namespace} as in the ESR.
 *
 * @param input  request message (type) of the mapping
 * @param output response message (type) of the mapping
 */
public record EsrOperationMapping(String name, String namespace, String oid, String swcGuid, QNameRef input,
        QNameRef output) {
}
