package io.poxray.cli;

import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;

/** {@code po-xray messages uds-filters}: the interfaces with a user-defined search filter. */
@CommandDefinition(name = "uds-filters",
        description = "Interfaces with a user-defined search filter (getUserDefinedSearchFilters)", generateHelp = true)
public class MessagesUdsFiltersCommand extends MessagesSubcommand {

    @Override
    void run(CommandInvocation invocation) {
        MessagesLookupCommand.printInterfaces(invocation, service.client(system).getUserDefinedSearchFilters(), json);
    }
}
