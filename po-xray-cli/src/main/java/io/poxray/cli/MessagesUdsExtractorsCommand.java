package io.poxray.cli;

import io.poxray.monitoring.AttributeMetadata;
import io.poxray.monitoring.Lookup;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/** {@code po-xray messages uds-extractors --interface <name> --interface-ns <ns>}: the search attributes defined. */
@CommandDefinition(name = "uds-extractors",
        description = "User-defined search attributes defined for an interface (getUserDefinedSearchExtractors)",
        generateHelp = true)
public class MessagesUdsExtractorsCommand extends MessagesSubcommand {

    @Option(name = "interface", required = true, description = "Interface name")
    String interfaceName;

    @Option(name = "interface-ns", required = true, description = "Interface namespace")
    String interfaceNamespace;

    @Override
    void run(CommandInvocation invocation) {
        Lookup<AttributeMetadata> r = service.client(system).getUserDefinedSearchExtractors(interfaceName,
                interfaceNamespace);
        MessagesLookupCommand.print(invocation, r, json,
                a -> MessagesService.or(a.name()) + (a.description() == null ? "" : "  " + a.description()));
    }
}
