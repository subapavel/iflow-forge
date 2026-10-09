package io.poxray.cli;

import io.poxray.doc.DocFormat;
import io.poxray.doc.Documentation;
import io.poxray.integration.IntegrationQuery;
import io.poxray.json.Json;
import io.poxray.model.IntegrationInventory;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/**
 * {@code po-xray ico -s <system> -c <component> [-d in|out|both] [-f json|markdown|jira|all] [-l cs|en] [-o dir]}:
 * ICOs to / from a component with channels and ESR objects (interfaces, mappings, message and data types).
 */
@CommandDefinition(name = "ico",
        description = "ICOs to/from a communication component, with channels and ESR objects", generateHelp = true)
public class IcoCommand implements Command<CommandInvocation> {

    @Option(shortName = 's', name = "system", required = true,
            description = "PO system configured in ~/.po-xray/systems/<system>.properties")
    String system;

    @Option(shortName = 'c', name = "component", required = true,
            description = "Component name (case-insensitive) or wildcard pattern, e.g. *DIGISIGN*")
    String component;

    @Option(shortName = 'd', name = "direction", description = "in = component is receiver, out = sender",
            defaultValue = "both", allowedValues = {"in", "out", "both"})
    String direction;

    @Option(shortName = 'f', name = "format", description = "Output format", defaultValue = "all",
            allowedValues = {"json", "markdown", "jira", "all"})
    String format;

    @Option(shortName = 'l', name = "lang", description = "Document language", defaultValue = "cs",
            allowedValues = {"cs", "en"})
    String lang;

    @Option(shortName = 'o', name = "output", description = "Output directory", defaultValue = ".")
    String output;

    @Option(name = "refresh", hasValue = false, description = "Re-read objects instead of using the local snapshot")
    boolean refresh;

    @Option(name = "cache", description = "Snapshot directory (default ~/.po-xray/cache/<system>)")
    String cache;

    @Inject
    PoService po;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            IntegrationInventory inv = po.query(system, cache, refresh)
                    .icos(component, IntegrationQuery.Direction.valueOf(direction.toUpperCase(Locale.ROOT)));
            System.err.printf("%s: %d ICOs, %d channels, %d service interfaces, %d operation mappings, "
                            + "%d message mappings, %d data types; %d issues%n", component, inv.icos().size(),
                    inv.channels().size(), inv.esr().serviceInterfaces().size(), inv.esr().operationMappings().size(),
                    inv.esr().messageMappings().size(), inv.esr().dataTypes().size(), inv.issues().size());
            if (inv.icos().isEmpty()) {
                System.err.println("No ICO found for component '" + component
                        + "'. Find the exact name with: po-xray components -s " + system + " -n <part of the name>");
            }
            Path dir = Path.of(output);
            String base = PoService.baseName(component);
            List<Path> written = new ArrayList<>();
            if (format.equals("json") || format.equals("all")) {
                written.add(service.write(dir.resolve(base + ".json"), Json.write(inv)));
            }
            Locale locale = Locale.forLanguageTag(lang);
            for (DocFormat f : DocFormat.values()) {
                boolean wanted = format.equals("all") || format.equals(f == DocFormat.MARKDOWN ? "markdown" : "jira");
                if (wanted) {
                    written.add(service.write(dir.resolve(base + f.extension()), Documentation.render(inv, f, locale)));
                }
            }
            written.forEach(p -> System.err.println("Written " + p));
            return CommandResult.SUCCESS;
        });
    }
}
