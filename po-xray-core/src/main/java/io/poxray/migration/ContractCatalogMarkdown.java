package io.poxray.migration;

import io.poxray.model.ContractCatalogReport;

/** Human-readable evidence, with structural candidates explicitly separated from design claims. */
final class ContractCatalogMarkdown {
    private ContractCatalogMarkdown() { }

    static String render(ContractCatalogReport report) {
        var out = new StringBuilder("# Contract catalog\n\n");
        if (report == null) return out.append("Unknown: missing report.\n").toString();
        out.append("Source observations mean parsed export evidence. Supplied claims are attributed proposals, not verified approvals.\n\n")
                .append("Structural data and import matches are candidates, not runtime availability or complete deployable schemas. ")
                .append("Unknown, unresolved and unsupported fields are retained with diagnostics.\n\n");
        table(out, "Artifacts", "ID", "Kind", "ESR identity", "Owner", "Basis", "Evidence");
        for (var a : report.artifacts()) row(out, a.id(), a.kind(), a.esr(), a.owner(), a.basis(), a.evidence());
        table(out, "Contracts", "ID", "Artifact", "Disposition", "ESR identity", "XML QName", "Evidence");
        for (var c : report.contracts()) row(out, c.id(), c.artifact(), c.disposition(), c.esr(), c.xmlName(), c.evidence());
        table(out, "Port uses and attributed port claims", "Contract", "Role", "Participant", "Operation", "Step", "Evidence");
        for (var c : report.contracts()) for (var p : c.ports()) {
            if (p == null) row(out, c.id(), "unknown port", null, null, null, null);
            else row(out, c.id(), role(p.role(), ContractCatalogClaims.PORTS), p.participant(), p.operation(), p.step(), p.evidence());
        }
        table(out, "Attributed values (inputs, defaults and API responses)", "Contract", "Path", "Role", "Producer", "Consumers", "Default text", "Evidence");
        for (var c : report.contracts()) for (var v : c.values()) {
            if (v == null) row(out, c.id(), "unknown value", null, null, null, null, null);
            else row(out, c.id(), v.path(), role(v.role(), ContractCatalogClaims.VALUES), v.producer(), v.consumers(), v.defaultValue(), v.evidence());
        }
        table(out, "Dependencies (identity or lexical path candidates)", "Artifact", "Kind", "ESR reference", "Location", "Namespace", "Candidates", "State", "Evidence");
        for (var d : report.dependencies()) row(out, d.artifact(), d.kind(), d.esr(), d.location(), d.namespace(), d.candidates(), d.state(), d.evidence());
        table(out, "Diagnostics", "Severity", "Code", "Locator", "Source", "Reason and original claim");
        for (var i : report.issues()) row(out, i.severity(), i.code(), i.location(), i.source(), i.message());
        out.append("\n## Structural data candidates\n\n");
        var data = report.data();
        if (data == null) return out.append("Unknown: no BPM data evidence supplied.\n").toString();
        out.append("Initial mappings and later default/API producers remain separate from request requirements.\n\n");
        for (var p : data.processes()) {
            out.append("\n### Process ").append(cell(p.key())).append("\n\n");
            table(out, "Process coverage", "DC", "ID", "Name", "Source", "Coverage", "Reasons");
            row(out, p.dc(), p.id(), p.name(), p.source(), p.coverage(), p.reasons());
            table(out, "Objects", "Ordinal", "Source declaration", "Memberships", "Scope path", "Types", "Type candidates", "Scope state", "Reasons");
            for (var o : p.objects()) row(out, o.ordinal(), o.source(), o.memberships(), o.scopePath(), o.types(), o.typeCandidates(), o.scopeState(), o.reasons());
            table(out, "Initial and step mappings", "Ordinal", "Parent", "Parent declaration", "Mapping identity", "Ownership", "Root owner", "Role", "Read domain", "Write domain", "Evidence", "Reasons");
            for (var m : p.mappings()) row(out, m.ordinal(), m.parent(), m.parentDeclaration(), m.mapping(), m.ownership(),
                    m.rootOwner(), m.role(), m.readDomain(), m.writeDomain(), m.evidence(), m.reasons());
            table(out, "Declarations and default producer evidence", "Ordinal", "Mapping", "Part identity", "Target path", "Target binding", "Value kind", "Candidacy", "Evidence", "Reasons");
            for (var d : p.declarations()) row(out, d.ordinal(), d.mapping(), d.part(), d.targetPath(), d.target(), d.valueKind(), d.candidacy(), d.evidence(), d.reasons());
            table(out, "Reads", "Ordinal", "Declaration", "Domain", "Binding", "Origin candidates", "Evidence", "Reasons");
            for (var r : p.reads()) row(out, r.ordinal(), r.declaration(), r.domain(), r.binding(), r.candidates(), r.evidence(), r.reasons());
            table(out, "Steps: before and after origin candidates", "Step", "Location", "Connectivity", "Before candidates", "After candidates", "Reasons");
            for (var s : p.steps()) row(out, s.step(), s.location(), s.connectivity(), s.before(), s.after(), s.reasons());
        }
        return out.toString();
    }

    private static String role(String role, java.util.Set<String> supported) {
        return role != null && supported.contains(role) ? role : "unsupported/unknown: " + role;
    }
    private static void table(StringBuilder out, String title, String... headings) {
        out.append("\n#### ").append(title).append("\n\n");
        row(out, (Object[]) headings);
        for (int i = 0; i < headings.length; i++) out.append("| --- ");
        out.append("|\n");
    }
    private static void row(StringBuilder out, Object... values) {
        for (Object value : values) out.append("| ").append(cell(value)).append(' ');
        out.append("|\n");
    }
    private static String cell(Object value) {
        return value == null ? "unknown" : String.valueOf(value).replace("\\", "\\\\").replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;").replace("|", "\\|").replace("\r\n", "<br>")
                .replace("\n", "<br>").replace("\r", "<br>");
    }
}
