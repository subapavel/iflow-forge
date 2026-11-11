package io.poxray.cli;

import io.poxray.monitoring.MonitoringClient;
import io.poxray.monitoring.SearchResult;
import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;

/**
 * {@code po-xray messages get <id>... | <key>...}: the messages with these IDs (getMessagesByIDs) or keys
 * (getMessagesByKeys; a value with a backslash is a key).
 */
@CommandDefinition(name = "get", description = "Read messages by message ID (getMessagesByIDs) or key (getMessagesByKeys)",
        generateHelp = true)
public class MessagesGetCommand extends MessagesSubcommand {

    @Arguments(description = "Message IDs, or message keys (values with a backslash)", paramLabel = "id-or-key",
            required = true)
    List<String> ids;

    @Option(shortName = 'l', name = "limit", defaultValue = "200", description = "Maximum number of messages (keys)")
    int limit;

    @Option(name = "archive", hasValue = false, description = "Search the archive (IDs)")
    boolean archive;

    @Override
    void run(CommandInvocation invocation) {
        long keys = ids.stream().filter(v -> v.indexOf('\\') >= 0).count();
        if (keys > 0 && keys < ids.size()) {
            throw new IllegalArgumentException("Give either message IDs or message keys, not both");
        }
        MonitoringClient client = service.client(system);
        SearchResult result = keys > 0 ? client.getMessagesByKeys(ids, limit)
                : client.getMessagesByIds(ids, List.of(), List.of(), archive);
        service.print(invocation, result, json);
    }
}
