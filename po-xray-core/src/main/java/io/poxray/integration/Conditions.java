package io.poxray.integration;

import io.poxray.model.IntegrationInventory.Condition;
import io.poxray.model.IntegrationInventory.ConditionLine;
import io.poxray.sca.XmlDocs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.w3c.dom.Element;

/**
 * Routing conditions of an ICO ({@code AllInOne/Conditions/RDS_CONDSHORT}), referenced from receivers
 * ({@code ReceiverConditionId}) and interface determinations ({@code InterfaceConditionId}).
 * <p>
 * Line = {@code COMPGROUP}, {@code COMPOP}, left / right extractor ({@code TRD_EXTRACTOR}: {@code XP} XPath in
 * {@code VALUE}, {@code CT} context object {@code COBJNAME}/{@code COBJNS}, {@code KO} constant). Lines of a group
 * are combined with AND, groups with OR (SAP data model, AtomicConditionBlock).
 * <p>
 * Receivers are bound to conditions in {@code ReceiverAssignmentList/ReceiverAssignment}
 * ({@code ReceiverConditionId} + {@code ReceiverIds/ReceiverId} → {@code ReceiverConfiguration/ReceiverId});
 * interface determinations carry {@code InterfaceConditionId} directly.
 */
final class Conditions {

    private Conditions() {
    }

    /** All conditions of an ICO content by condition ID. */
    static Map<String, Condition> read(Element allInOne) {
        Map<String, Condition> out = new LinkedHashMap<>();
        Element conditions = allInOne == null ? null : XmlDocs.child(allInOne, "Conditions");
        if (conditions == null) {
            return out;
        }
        for (Element c : XmlDocs.children(conditions, "RDS_CONDSHORT")) {
            List<ConditionLine> lines = new ArrayList<>();
            for (Element line : XmlDocs.children(c, "CONDLINE")) {
                lines.add(new ConditionLine(parseInt(XmlDocs.childText(line, "COMPGROUP")),
                        operand(XmlDocs.child(line, "LEXTRACTOR")), XmlDocs.childText(line, "COMPOP"),
                        operand(XmlDocs.child(line, "REXTRACTOR"))));
            }
            out.put(XmlDocs.childText(c, "CONDITIONID"), new Condition(render(lines), List.copyOf(lines)));
        }
        return out;
    }

    /**
     * Condition IDs per receiver ID from {@code ReceiverAssignmentList}; an assignment without condition is a
     * {@code null} entry (receiver is unconditional).
     */
    static Map<String, List<String>> receiverAssignments(Element allInOne) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Element list = allInOne == null ? null : XmlDocs.child(allInOne, "ReceiverAssignmentList");
        if (list == null) {
            return out;
        }
        for (Element a : XmlDocs.children(list, "ReceiverAssignment")) {
            String conditionId = blankToNull(XmlDocs.childText(a, "ReceiverConditionId"));
            Element ids = XmlDocs.child(a, "ReceiverIds");
            for (Element id : ids == null ? List.<Element>of() : XmlDocs.children(ids, "ReceiverId")) {
                String receiverId = blankToNull(id.getTextContent());
                if (receiverId != null) {
                    out.computeIfAbsent(receiverId, k -> new ArrayList<>()).add(conditionId);
                }
            }
        }
        return out;
    }

    /** Alternative conditions (several assignments of one receiver) combined with OR; groups renumbered. */
    static Condition or(List<Condition> conditions) {
        if (conditions.size() == 1) {
            return conditions.getFirst();
        }
        List<ConditionLine> lines = new ArrayList<>();
        int offset = 0;
        for (Condition c : conditions) {
            int max = 0;
            for (ConditionLine l : c.lines()) {
                lines.add(new ConditionLine(offset + l.group(), l.left(), l.operator(), l.right()));
                max = Math.max(max, l.group());
            }
            offset += max + 1;
        }
        return new Condition(render(lines), List.copyOf(lines));
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** {@code (a AND b) OR (c)}; single groups without parentheses. */
    static String render(List<ConditionLine> lines) {
        Map<Integer, List<String>> groups = new TreeMap<>();
        for (ConditionLine l : lines) {
            groups.computeIfAbsent(l.group(), k -> new ArrayList<>()).add(line(l));
        }
        List<String> parts = new ArrayList<>();
        for (List<String> g : groups.values()) {
            String and = String.join(" AND ", g);
            parts.add(groups.size() > 1 && g.size() > 1 ? "(" + and + ")" : and);
        }
        return String.join(" OR ", parts);
    }

    private static String line(ConditionLine l) {
        String op = l.operator() == null ? "?" : l.operator();
        return switch (op) {
            case "EQ" -> l.left() + " = " + l.right();
            case "NE" -> l.left() + " != " + l.right();
            case "CP" -> l.left() + " like " + l.right();
            case "NP" -> l.left() + " not like " + l.right();
            case "EX" -> "exists " + l.left();
            case "NX" -> "not exists " + l.left();
            default -> l.left() + " " + op + (l.right() == null ? "" : " " + l.right());
        };
    }

    private static String operand(Element extractorHolder) {
        Element trd = extractorHolder == null ? null : XmlDocs.child(extractorHolder, "TRD_EXTRACTOR");
        if (trd == null) {
            return null;
        }
        String type = XmlDocs.childText(trd, "TYPE");
        String value = XmlDocs.childText(trd, "VALUE");
        return switch (String.valueOf(type)) {
            case "KO" -> "\"" + (value == null ? "" : value) + "\"";
            case "XP" -> value;
            case "CT" -> "context:" + XmlDocs.childText(trd, "COBJNAME")
                    + (XmlDocs.childText(trd, "COBJNS") == null ? "" : " (" + XmlDocs.childText(trd, "COBJNS") + ")");
            default -> type + ":" + (value == null ? XmlDocs.childText(trd, "COBJNAME") : value);
        };
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
