package io.poxray.cli;

import io.poxray.monitoring.MessageFilter;
import io.poxray.monitoring.MessageInterface;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

/** The search filter options of {@code messages search} and {@code messages uds-search}. */
abstract class MessagesFilterSubcommand extends MessagesSubcommand {

    @Option(name = "interface", description = "Interface name")
    String interfaceName;

    @Option(name = "interface-ns", description = "Interface namespace")
    String interfaceNamespace;

    @Option(name = "sender", description = "Sender component (business system or component)")
    String sender;

    @Option(name = "receiver", description = "Receiver component")
    String receiver;

    @OptionList(name = "status", description = "Message status, e.g. systemError, holding, success (several: a,b)")
    List<String> statuses;

    @Option(name = "faulty", hasValue = false, description = "Only faulty messages")
    boolean faulty;

    @Option(name = "from", defaultValue = "-24h",
            description = "Start of the time window: 2026-11-10T08:00 (local), ISO with offset, -2h, -30m, -1d")
    String from;

    @Option(name = "to", description = "End of the time window (same forms; default: now)")
    String to;

    @Option(shortName = 'l', name = "limit", defaultValue = "200", description = "Maximum number of messages")
    int limit;

    @Option(name = "archive", hasValue = false, description = "Search the archive")
    boolean archive;

    /** The filter of the options, times read in the local zone. */
    MessageFilter filter() {
        Clock clock = Clock.systemDefaultZone();
        OffsetDateTime fromTime = TimeArg.parse(from, clock);
        OffsetDateTime toTime = to == null ? OffsetDateTime.now(clock) : TimeArg.parse(to, clock);
        MessageFilter.Builder b = MessageFilter.builder().archive(archive).onlyFaultyMessages(faulty)
                .fromTime(fromTime).toTime(toTime).senderName(sender).receiverName(receiver);
        if (interfaceName != null || interfaceNamespace != null) {
            b.interfaceRef(new MessageInterface(interfaceName, interfaceNamespace, null, null, null, null));
        }
        if (statuses != null && !statuses.isEmpty()) {
            b.statuses(statuses.stream().map(String::strip).filter(s -> !s.isEmpty()).toList());
        }
        return b.build();
    }
}
