package io.poxray.cli;

import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;

/** {@code po-xray messages <subcommand>}: read-only message monitoring of an online PO system. */
@CommandDefinition(name = "messages",
        description = "Read-only message monitoring of a PO system (AdapterMessageMonitoring): search, get, "
                + "successors, predecessor, log, payload, uds-search, uds-attributes, uds-filters, uds-extractors, lookup",
        generateHelp = true,
        groupCommands = {MessagesSearchCommand.class, MessagesUdsSearchCommand.class, MessagesGetCommand.class,
            MessagesSuccessorsCommand.class, MessagesPredecessorCommand.class, MessagesLogCommand.class,
            MessagesPayloadCommand.class, MessagesUdsAttributesCommand.class, MessagesUdsFiltersCommand.class,
            MessagesUdsExtractorsCommand.class, MessagesLookupCommand.class})
public class MessagesCommand implements Command<CommandInvocation> {

    /** The subcommands, as listed by {@link #execute}. */
    static final String SUBCOMMANDS = "search | uds-search | get | successors | predecessor | log | payload | "
            + "uds-attributes | uds-filters | uds-extractors | lookup";

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        System.err.println("Usage: po-xray messages <" + SUBCOMMANDS + "> [-s <system>] [options]"
                + "   (po-xray messages <command> --help)");
        return CommandResult.USAGE_ERROR;
    }
}
