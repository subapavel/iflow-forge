package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.monitoring.ValueResult;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/** {@code po-xray messages predecessor <id> [--direction OUTBOUND|INBOUND]}: getPredecessorMessageId. */
@CommandDefinition(name = "predecessor", description = "Message ID of the predecessor of a message (getPredecessorMessageId)",
        generateHelp = true)
public class MessagesPredecessorCommand extends MessagesSubcommand {

    @Argument(description = "Message ID", paramLabel = "id", required = true)
    String id;

    @Option(name = "direction", description = "OUTBOUND or INBOUND")
    String direction;

    @Override
    void run(CommandInvocation invocation) {
        ValueResult r = service.client(system).getPredecessorMessageId(id, direction);
        if (json) {
            invocation.println(Json.write(r).stripTrailing());
        } else if (r.value() == null || r.value().isBlank()) {
            System.err.println("No predecessor of " + id);
        } else {
            invocation.println(r.value());
        }
    }
}
