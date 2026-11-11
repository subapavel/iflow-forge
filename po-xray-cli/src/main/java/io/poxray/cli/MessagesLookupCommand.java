package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.monitoring.Lookup;
import io.poxray.monitoring.MessageInterface;
import io.poxray.monitoring.MonitoringClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

/**
 * {@code po-xray messages lookup <kind>}: the values the monitoring knows, one per line; kind {@code interfaces},
 * {@code parties}, {@code services}, {@code connections}, {@code iflows}, {@code error-codes} ({@code --label}),
 * {@code status-details} ({@code --codes}, else all).
 */
@CommandDefinition(name = "lookup", description = "Values known to the monitoring: interfaces | parties | services | "
        + "connections | iflows | error-codes | status-details", generateHelp = true)
public class MessagesLookupCommand extends MessagesSubcommand {

    static final List<String> KINDS =
            List.of("interfaces", "parties", "services", "connections", "iflows", "error-codes", "status-details");

    @Argument(description = "interfaces | parties | services | connections | iflows | error-codes | status-details",
            paramLabel = "kind", required = true)
    String kind;

    @Option(name = "label", description = "Error label ID (error-codes)")
    Integer label;

    @OptionList(name = "codes", description = "Error codes (status-details; default: all), several: A,B")
    List<String> codes;

    @Option(name = "lang", defaultValue = "en", description = "Language of descriptions (iflows, status-details)")
    String lang;

    @Override
    void run(CommandInvocation invocation) {
        String k = kind.strip().toLowerCase(Locale.ROOT);
        if (!KINDS.contains(k)) {
            throw new IllegalArgumentException("Unknown lookup '" + kind + "': use one of " + String.join(", ", KINDS));
        }
        if (k.equals("error-codes") && label == null) {
            throw new IllegalArgumentException("lookup error-codes needs --label <error label ID>");
        }
        MonitoringClient client = service.client(system);
        Locale locale = Locale.forLanguageTag(lang.strip());
        switch (k) {
            case "interfaces" -> printInterfaces(invocation, client.getInterfaces(), json);
            case "parties" -> print(invocation, client.getParties(), json, p -> MessagesService.or(p.name())
                    + (p.agency() == null && p.schema() == null ? ""
                            : "  (" + MessagesService.or(p.agency()) + ", " + MessagesService.or(p.schema()) + ")"));
            case "services" -> print(invocation, client.getServices(), json, s -> s);
            case "connections" -> print(invocation, client.getConnections(), json, s -> s);
            case "iflows" -> print(invocation, client.getIntegrationFlows(lang.strip()), json,
                    f -> String.join("  ", MessagesService.or(f.id()), MessagesService.or(f.name()),
                            f.description() == null ? "" : f.description()).stripTrailing());
            case "error-codes" -> print(invocation, client.getErrorCodes(label), json, s -> s);
            default -> print(invocation, codes == null || codes.isEmpty() ? client.getAllAvailableStatusDetails(locale)
                    : client.getStatusDetails(codes, locale), json,
                    d -> String.join("  ", MessagesService.or(d.errorLabelId()), MessagesService.or(d.description()),
                            d.text() == null ? "" : d.text()).stripTrailing());
        }
    }

    /** {@code name | namespace} and, when set, the sender and receiver party and component. */
    static void printInterfaces(CommandInvocation invocation, Lookup<MessageInterface> r, boolean json) {
        print(invocation, r, json, i -> {
            List<String> extra = new ArrayList<>();
            for (String s : new String[] {i.senderParty(), i.senderComponent(), i.receiverParty(),
                i.receiverComponent()}) {
                extra.add(MessagesService.or(s));
            }
            return MessagesService.or(i.name()) + " | " + MessagesService.or(i.namespace())
                    + (extra.stream().allMatch("-"::equals) ? "" : "  " + String.join(" ", extra));
        });
    }

    /** {@code --json} the model, else one line per value and the count on stderr. */
    static <T> void print(CommandInvocation invocation, Lookup<T> r, boolean json, Function<T, String> line) {
        if (json) {
            invocation.println(Json.write(r).stripTrailing());
        } else {
            r.values().forEach(v -> invocation.println(line.apply(v)));
            System.err.println(r.values().size() + " value(s)");
        }
        MessagesService.otherFields(r.otherFields().size());
    }
}
