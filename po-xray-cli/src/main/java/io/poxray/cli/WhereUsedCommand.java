package io.poxray.cli;

import io.poxray.integration.ObjectCatalog;
import io.poxray.json.Json;
import io.poxray.model.IntegrationInventory.ObjectSummary;
import jakarta.inject.Inject;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/** {@code po-xray where-used -s <system> -t <type> -n <name> [--namespace <ns>] [--json]}. */
@CommandDefinition(name = "where-used", description = "Objects referencing an ESR / Directory object (e.g. operation "
        + "mappings using a message mapping, ICOs using a channel)", generateHelp = true)
public class WhereUsedCommand implements Command<CommandInvocation> {

    @Option(shortName = 's', name = "system", required = true,
            description = "PO system configured in ~/.po-xray/systems/<system>.properties")
    String system;

    @Option(shortName = 't', name = "type", required = true, description = "Type alias or ID of the used object")
    String type;

    @Option(shortName = 'n', name = "name", required = true,
            description = "Exact name of the used object (case-insensitive); channels as name or component|name")
    String name;

    @Option(name = "namespace", description = "Namespace of the used object (if the name is not unique)")
    String namespace;

    @Option(name = "json", hasValue = false, description = "Print JSON instead of one object per line")
    boolean json;

    @Inject
    PoService po;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            ObjectCatalog catalog = po.catalog(system);
            List<ObjectCatalog.Hit> targets = catalog.find(type, name, namespace);
            if (targets.isEmpty()) {
                throw new ScaService.CliException("No " + type + " named '" + name + "'"
                        + (namespace == null ? "" : " in " + namespace));
            }
            List<ObjectSummary> users = catalog.whereUsed(targets);
            if (json) {
                invocation.println(Json.write(users).stripTrailing());
            } else {
                users.forEach(o -> invocation.println(SearchCommand.line(o)));
            }
            System.err.println(users.size() + " objects use " + targets.size() + " object(s) named '" + name + "'");
            return CommandResult.SUCCESS;
        });
    }
}
