package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.model.IntegrationInventory.Component;
import jakarta.inject.Inject;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/** {@code po-xray components -s <system> [-n <pattern>] [--json]}: communication components of a PO system. */
@CommandDefinition(name = "components",
        description = "List communication components (business systems / components) of a PO system", generateHelp = true)
public class ComponentsCommand implements Command<CommandInvocation> {

    @Option(shortName = 's', name = "system", required = true,
            description = "PO system configured in ~/.po-xray/systems/<system>.properties")
    String system;

    @Option(shortName = 'n', name = "name",
            description = "Name filter, case-insensitive: plain text = contains, * and ? = wildcards")
    String name;

    @Option(name = "json", hasValue = false, description = "Print JSON instead of one name per line")
    boolean json;

    @Inject
    PoService po;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            List<Component> components = po.query(system, null, false).components(name);
            if (json) {
                invocation.println(Json.write(components).stripTrailing());
            } else {
                components.forEach(c -> invocation.println(c.party() == null ? c.name() : c.party() + " | " + c.name()));
            }
            System.err.println(components.size() + " components" + (name == null ? "" : " matching '" + name + "'"));
            return CommandResult.SUCCESS;
        });
    }
}
