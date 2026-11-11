package io.poxray.cli;

import io.poxray.monitoring.SearchResult;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;

/** {@code po-xray messages search [-s <system>] [filter options] [--json]}: getMessageList, one row per message. */
@CommandDefinition(name = "search", description = "Search messages (getMessageList); default: the last 24 hours",
        generateHelp = true)
public class MessagesSearchCommand extends MessagesFilterSubcommand {

    @Override
    void run(CommandInvocation invocation) {
        SearchResult result = service.client(system).getMessageList(filter(), limit);
        service.print(invocation, result, json);
    }
}
