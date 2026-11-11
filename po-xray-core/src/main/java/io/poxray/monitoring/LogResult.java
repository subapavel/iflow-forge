package io.poxray.monitoring;

import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

/**
 * The audit log of one message ({@code getLogEntries}, {@code DATA:ArrayOfAuditLogEntryData}) from the system named
 * {@code system}: the requested message key, the entries in the order the service sent them (never null, not sorted)
 * and the SOAP answer as received.
 */
public record LogResult(String system, String messageKey, List<LogEntry> entries, String rawResponse) {

    public LogResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** Reads the {@code OP:Response} element; nil items are skipped (the raw answer keeps them). */
    static LogResult read(String system, String messageKey, MonitoringClient.Reply reply) {
        List<LogEntry> entries = new ArrayList<>();
        for (Element e : Soap.children(reply.response())) {
            if (Soap.is(e, Soap.DATA, "AuditLogEntryData") && !Soap.nil(e)) {
                entries.add(LogEntry.read(e));
            }
        }
        return new LogResult(system, messageKey, entries, reply.raw());
    }
}
