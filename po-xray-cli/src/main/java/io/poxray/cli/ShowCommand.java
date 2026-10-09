package io.poxray.cli;

import io.poxray.IssueLog;
import io.poxray.integration.ObjectCatalog;
import io.poxray.json.Json;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/** {@code po-xray show -s <system> -t <type> -n <name> [--namespace <ns>]}: one object as JSON. */
@CommandDefinition(name = "show", description = "Print one ESR / Directory object of any type as JSON",
        generateHelp = true)
public class ShowCommand implements Command<CommandInvocation> {

    @Option(shortName = 's', name = "system", required = true,
            description = "PO system configured in ~/.po-xray/systems/<system>.properties")
    String system;

    @Option(shortName = 't', name = "type", required = true, description = "Type alias or ID")
    String type;

    @Option(shortName = 'n', name = "name", required = true,
            description = "Exact name (case-insensitive); channels as name or component|name")
    String name;

    @Option(name = "namespace", description = "Namespace (if the name is not unique)")
    String namespace;

    @Inject
    PoService po;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            ObjectCatalog catalog = po.catalog(system);
            List<ObjectCatalog.Hit> hits = catalog.find(type, name, namespace);
            if (hits.isEmpty()) {
                throw new ScaService.CliException("No " + type + " named '" + name + "'"
                        + (namespace == null ? "" : " in " + namespace));
            }
            IssueLog issues = new IssueLog();
            List<Object> objects = new ArrayList<>();
            for (ObjectCatalog.Hit hit : hits) {
                Object o = catalog.read(hit, issues);
                objects.add(o == null ? hit.summary() : o);
            }
            invocation.println(Json.write(objects.size() == 1 ? objects.getFirst() : objects).stripTrailing());
            issues.issues().forEach(i -> System.err.println(i.severity() + " " + i.code() + ": " + i.message()));
            return CommandResult.SUCCESS;
        });
    }
}
