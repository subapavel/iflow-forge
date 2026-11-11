package io.poxray.cli;

import jakarta.inject.Inject;
import org.aesh.command.Command;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/** Options and error handling every {@code po-xray messages} subcommand shares: {@code -s/--system}, {@code --json}. */
abstract class MessagesSubcommand implements Command<CommandInvocation> {

    @Option(shortName = 's', name = "system",
            description = "PO system in ~/.po-xray/systems/<system>.properties (default: POXRAY_SYSTEM, then system= "
                    + "in ~/.po-xray/monitoring.properties)")
    String system;

    @Option(name = "json", hasValue = false, description = "Print the result as JSON (with the SOAP answer)")
    boolean json;

    @Inject
    MessagesService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            run(invocation);
            return CommandResult.SUCCESS;
        });
    }

    /** The command body; a failure is mapped by {@link MessagesService#run}. */
    abstract void run(CommandInvocation invocation);
}
