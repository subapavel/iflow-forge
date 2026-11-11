package io.poxray.monitoring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

/**
 * The audit log of one message ({@code getLogEntries}, {@code DATA:ArrayOfAuditLogEntryData}) from the system named
 * {@code system}: the requested message key, the entries in the order the service sent them (never null, not sorted),
 * the SOAP answer as received, and {@code otherFields} (never null): every item that is nil ({@code "(nil)"}) or not
 * an {@code AuditLogEntryData}, as {@code Response/<name>}, {@code Response/<name>#2}, ..., and {@code Response} as
 * {@code "(nil)"} when the whole answer is nil.
 */
public record LogResult(String system, String messageKey, List<LogEntry> entries, String rawResponse,
        Map<String, String> otherFields) {

    public LogResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
        otherFields = otherFields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(otherFields));
    }

    /** Reads the {@code OP:Response} element. */
    static LogResult read(String system, String messageKey, MonitoringClient.Reply reply) {
        List<LogEntry> entries = new ArrayList<>();
        Map<String, String> other = new LinkedHashMap<>();
        if (Soap.nil(reply.response())) {
            other.put("Response", Soap.NIL);
        }
        for (Element e : Soap.children(reply.response())) {
            if (Soap.is(e, Soap.DATA, "AuditLogEntryData") && !Soap.nil(e)) {
                entries.add(LogEntry.read(e));
            } else {
                Soap.keepAt(other, "Response/" + e.getLocalName(), e);
            }
        }
        return new LogResult(system, messageKey, entries, reply.raw(), other);
    }
}
