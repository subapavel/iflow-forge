package io.poxray.cli;

import io.poxray.integration.ObjectCatalog;
import io.poxray.json.Json;
import io.poxray.model.IntegrationInventory.ObjectSummary;
import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/** {@code po-xray search -s <system> -t <types> [-n <name>] [--namespace <ns>] [--json]}: any ESR / Directory object. */
@CommandDefinition(name = "search", description = "Search ESR / Integration Directory objects of any type by name",
        generateHelp = true)
public class SearchCommand implements Command<CommandInvocation> {

    @Option(shortName = 's', name = "system", required = true,
            description = "PO system configured in ~/.po-xray/systems/<system>.properties")
    String system;

    @Option(shortName = 't', name = "type", required = true,
            description = "Comma-separated types: aliases (messageMapping, operationMapping, serviceInterface, dataType, "
                    + "messageType, functionLibrary, importedArchive, channel, ico, receiverDetermination, "
                    + "interfaceDetermination, senderAgreement, receiverAgreement, valueMapping, iflow, ...) or type IDs")
    String type;

    @Option(shortName = 'n', name = "name",
            description = "Name filter, case-insensitive: plain text = contains, * and ? = wildcards")
    String name;

    @Option(name = "namespace", description = "Namespace filter (same syntax)")
    String namespace;

    @Option(shortName = 'l', name = "limit", defaultValue = "200", description = "Maximum number of objects listed")
    int limit;

    @Option(name = "json", hasValue = false, description = "Print JSON instead of one object per line")
    boolean json;

    @Inject
    PoService po;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            List<String> types = Arrays.stream(type.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList();
            ObjectCatalog.SearchResult result = po.catalog(system).search(types, name, namespace, limit);
            if (json) {
                invocation.println(Json.write(result).stripTrailing());
            } else {
                result.objects().forEach(o -> invocation.println(line(o)));
            }
            System.err.println(result.total() + " objects" + (result.total() > result.objects().size()
                    ? ", first " + result.objects().size() + " listed (--limit)" : ""));
            return CommandResult.SUCCESS;
        });
    }

    /** {@code XI_TRAFO  Name | urn:ns  (SWC 1.0)  changed 2025-05-16 by user}. */
    static String line(ObjectSummary o) {
        String key = String.join(" | ", o.key().values().stream().filter(v -> !v.isEmpty()).toList());
        return o.type() + "  " + key + (o.swc() == null ? "" : "  (" + o.swc() + ")")
                + (o.modifiedAt() == null ? "" : "  changed " + o.modifiedAt()
                        + (o.modifiedBy() == null ? "" : " by " + o.modifiedBy()));
    }
}
