package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.monitoring.LogEntry;
import io.poxray.monitoring.LogResult;
import io.poxray.monitoring.MonitoringClient;
import java.time.Clock;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/** {@code po-xray messages log <id-or-key> [--key <key>] [--direction ...]}: the audit log (getLogEntries). */
@CommandDefinition(name = "log", description = "Audit log of a message (getLogEntries)", generateHelp = true)
public class MessagesLogCommand extends MessagesKeySubcommand {

    @Option(shortName = 'l', name = "limit", defaultValue = "200", description = "Maximum number of log entries")
    int limit;

    @Option(name = "older-than", description = "Only entries older than this time (2026-11-10T08:00, ISO, -2h)")
    String olderThan;

    @Override
    void run(CommandInvocation invocation) {
        MessagesService.checkSave(save);
        MonitoringClient client = service.client(system);
        String messageKey = key(invocation, client);
        LogResult r = client.getLogEntries(messageKey, archive, limit, null,
                olderThan == null ? null : TimeArg.parse(olderThan, Clock.systemDefaultZone()));
        MessagesService.save(save, saveName(messageKey), r, r.rawResponse(), null);
        if (json) {
            invocation.println(Json.write(r).stripTrailing());
        } else {
            r.entries().forEach(e -> invocation.println(line(e)));
            System.err.println(r.entries().size() + " log entries of " + messageKey);
        }
        MessagesService.otherFields(r.otherFields().size());
    }

    /** {@code <local time>  <status>  <text>}: the localized text, else the text key with its parameters. */
    static String line(LogEntry e) {
        String text = e.localizedText() != null && !e.localizedText().isBlank() ? e.localizedText()
                : MessagesService.or(e.textKey()) + (e.params().isEmpty() ? "" : " " + e.params());
        return String.join("  ", MessagesService.time(e.timeStamp()), MessagesService.or(e.status()), text);
    }
}
