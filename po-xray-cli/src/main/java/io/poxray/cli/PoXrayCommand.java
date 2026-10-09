package io.poxray.cli;

import io.poxray.PoXray;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;

/** Entry point: {@code po-xray <json|doc|export> <sca> [options]}. */
@CommandDefinition(name = "po-xray",
        description = "Reads SAP PO content (NWDI SCA: NW BPM, EJB; ESR / Integration Directory) for migration to "
                + "SAP Integration Suite",
        version = PoXray.VERSION,
        generateHelp = true,
        groupCommands = {JsonCommand.class, DocCommand.class, ExportCommand.class, ComponentsCommand.class,
            IcoCommand.class, SearchCommand.class, ShowCommand.class, WhereUsedCommand.class})
public class PoXrayCommand implements Command<CommandInvocation> {

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        System.err.println("Usage: po-xray <json|doc|export> <file.sca> [options]  |  po-xray components -s <system>"
                + "  |  po-xray ico -s <system> -c <component>   (po-xray --help, po-xray <command> --help)");
        return CommandResult.USAGE_ERROR;
    }
}
