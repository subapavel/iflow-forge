package io.poxray.cli;

import java.util.List;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;

/** {@code po-xray messages successors <id>...}: the messages and their successors (getMessagesWithSuccessors). */
@CommandDefinition(name = "successors", description = "Read messages with their successors (getMessagesWithSuccessors)",
        generateHelp = true)
public class MessagesSuccessorsCommand extends MessagesSubcommand {

    @Arguments(description = "Message IDs", paramLabel = "id", required = true)
    List<String> ids;

    @Option(name = "archive", hasValue = false, description = "Search the archive")
    boolean archive;

    @Override
    void run(CommandInvocation invocation) {
        service.print(invocation, service.client(system).getMessagesWithSuccessors(ids, archive), json);
    }
}
