package io.poxray.monitoring;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

/**
 * One audit log entry ({@code AuditLogEntryData}, F-17 Appendix C, namespace DATA). Absent or nil children are null;
 * {@code params} (items of {@code params}, a nil item as null) and {@code otherFields} are never null.
 * {@code otherFields} holds by element name in document order every child that is unknown, repeated
 * ({@code name#2}, ...), complex where text is expected (as XML) or a {@code timeStamp} that does not parse as a
 * date-time (as its text); a nil unknown child is {@code "(nil)"}.
 */
public record LogEntry(OffsetDateTime timeStamp, String status, String textKey, List<String> params,
        String localizedText, Map<String, String> otherFields) {

    /** The children of {@code AuditLogEntryData} mapped to a component. */
    private static final Set<String> MAPPED = Set.of("timeStamp", "textKey", "params", "status", "localizedText");

    public LogEntry {
        params = params == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(params));
        otherFields = otherFields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(otherFields));
    }

    /** Reads one {@code DATA:AuditLogEntryData} element. */
    static LogEntry read(Element data) {
        OffsetDateTime timeStamp = null;
        String status = null;
        String textKey = null;
        List<String> params = new ArrayList<>();
        String localizedText = null;
        Map<String, String> other = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (Element e : Soap.children(data)) {
            int occurrence = seen.merge(e.getNamespaceURI() + " " + e.getLocalName(), 1, Integer::sum);
            String name = e.getLocalName();
            if (!Soap.DATA.equals(e.getNamespaceURI()) || occurrence > 1 || !MAPPED.contains(name)) {
                Soap.keep(other, e, occurrence); // foreign, repeated or unknown
                continue;
            }
            if (Soap.nil(e)) {
                continue;
            }
            if (!name.equals("params") && Soap.complex(e)) {
                Soap.keep(other, e, occurrence); // complex where text is expected
                continue;
            }
            switch (name) {
                case "timeStamp" -> {
                    try {
                        timeStamp = OffsetDateTime.parse(e.getTextContent().strip());
                    } catch (DateTimeParseException x) {
                        Soap.keep(other, e, occurrence);
                    }
                }
                case "status" -> status = e.getTextContent();
                case "textKey" -> textKey = e.getTextContent();
                case "localizedText" -> localizedText = e.getTextContent();
                default -> { // params
                    for (Element s : Soap.children(e)) {
                        params.add(Soap.nil(s) ? null : s.getTextContent());
                    }
                }
            }
        }
        return new LogEntry(timeStamp, status, textKey, params, localizedText, other);
    }
}
