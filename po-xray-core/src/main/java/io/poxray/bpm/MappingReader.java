package io.poxray.bpm;

import io.poxray.model.Expression;
import io.poxray.model.Mapping;
import io.poxray.moin.MoinElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts a Galaxy {@code Mapping.Mapping} into a flat list of assignments. Root parts are linked via
 * {@code MappingContainsMappingParts}, nested parts via {@code MappingPartHasChildren}; the target path is the
 * chain of part QNames.
 */
final class MappingReader {

    private final BpmContext ctx;
    private final ExpressionReader expressions;

    MappingReader(BpmContext ctx, ExpressionReader expressions) {
        this.ctx = ctx;
        this.expressions = expressions;
    }

    /** The mapping linked to {@code ownerId} via {@code association} (f=mapping), or null if none/empty. */
    Mapping readLinked(String association, String ownerId, String process, MoinElement owner) {
        return ctx.index.first(association, ownerId).map(id -> read(id, process, owner)).orElse(null);
    }

    Mapping read(String mappingId, String process, MoinElement owner) {
        MoinElement m = ctx.index.get(mappingId).orElse(null);
        if (m == null) {
            ctx.issues.warn("UNRESOLVED_REF", "Mapping " + mappingId + " not found", ctx.at(process, owner, null), null);
            return null;
        }
        List<Mapping.Assignment> out = new ArrayList<>();
        for (String root : ctx.index.firsts(Galaxy.MAPPING_PARTS, mappingId)) {
            walk(root, null, out, process, 0);
        }
        if (out.isEmpty()) {
            return null;
        }
        checkSuspicious(out, process, owner, m);
        return new Mapping(m.id(), m.name(), List.copyOf(out));
    }

    private void walk(String partId, String parentPath, List<Mapping.Assignment> out, String process, int depth) {
        MoinElement part = ctx.index.get(partId).orElse(null);
        if (part == null || depth > ExpressionReader.MAX_DEPTH) {
            return;
        }
        String name = ctx.qname(Galaxy.PART_QNAME, partId);
        String path = parentPath == null ? String.valueOf(name) : parentPath + "/" + name;
        Optional<String> exprId = ctx.index.first(Galaxy.PART_EXPRESSION, partId);
        Boolean enabled = "false".equals(part.attr("enabled")) ? Boolean.FALSE : null;
        Expression expr = exprId.map(x -> expressions.read(x, process)).orElse(null);
        if (expr != null && (expr.text() == null || expr.text().isBlank())) {
            expr = null; // structural part: no source expression
        }
        out.add(new Mapping.Assignment(path, part.attr("category"), part.attr("expander"), part.attr("transformer"),
                enabled, expr));
        for (String child : ctx.index.firsts(Galaxy.PART_CHILDREN, partId)) {
            walk(child, path, out, process, depth + 1);
        }
    }

    /**
     * Heuristic: two sibling targets assigned from the same non-trivial source path (typical copy-paste error,
     * e.g. two different fields both mapped from {@code .../function}).
     */
    private void checkSuspicious(List<Mapping.Assignment> assignments, String process, MoinElement owner, MoinElement m) {
        Map<String, Mapping.Assignment> seen = new HashMap<>();
        for (Mapping.Assignment a : assignments) {
            if (a.expr() == null || a.expr().text() == null || Boolean.FALSE.equals(a.enabled())) {
                continue;
            }
            String text = a.expr().text().replaceAll("\\s+", "");
            if (!text.contains("/") || text.startsWith("\"")) {
                continue;
            }
            String key = parent(a.target()) + "|" + text;
            Mapping.Assignment other = seen.putIfAbsent(key, a);
            if (other != null && !lastSegment(other.target()).equals(lastSegment(a.target()))) {
                ctx.issues.warn("SUSPICIOUS_MAPPING",
                        "'" + lastSegment(other.target()) + "' and '" + lastSegment(a.target())
                                + "' are mapped from the same source expression: " + a.expr().text().strip(),
                        ctx.at(process, owner, a.target()), ctx.source(m.partition()));
            }
        }
    }

    private static String parent(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i);
    }

    private static String lastSegment(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
