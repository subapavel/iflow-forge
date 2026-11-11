package io.poxray.mcp;

import io.poxray.json.Json;
import io.poxray.monitoring.BusinessAttribute;
import io.poxray.monitoring.KeyResolver;
import io.poxray.monitoring.KeyResolver.Resolution;
import io.poxray.monitoring.LogEntry;
import io.poxray.monitoring.LogResult;
import io.poxray.monitoring.Lookup;
import io.poxray.monitoring.Message;
import io.poxray.monitoring.MessageArchive;
import io.poxray.monitoring.MessageFilter;
import io.poxray.monitoring.MessageInterface;
import io.poxray.monitoring.MessagePayload;
import io.poxray.monitoring.MonitoringClient;
import io.poxray.monitoring.MonitoringSystems;
import io.poxray.monitoring.PayloadPart;
import io.poxray.monitoring.SaveTarget;
import io.poxray.monitoring.SearchResult;
import io.poxray.monitoring.ValueResult;
import io.poxray.po.PoException;
import io.poxray.po.PoSystem;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import tools.jackson.databind.SerializationFeature;

/**
 * MCP tools over the read-only message monitoring of an online PO system (AdapterMessageMonitoring, spec §3, §7):
 * search, read by ID or key, audit log, payload, user-defined search and lookups. Results are compact JSON of at most
 * {@link PoXrayTools#MAX_RESULT} characters, cut with {@code truncated} / {@code dropped} / {@code hint} when larger,
 * never written to a file; only {@code saveDir} writes (three tools).
 */
public class MessageTools {

    static final int MAX_RESULT = PoXrayTools.MAX_RESULT;
    /** A raw SOAP answer above this is left out of a result even with includeRaw (it would crowd out the model). */
    static final int MAX_RAW = MAX_RESULT / 2;
    /** Room kept for the hint and rounding when the list part of a result is fitted. */
    private static final int SLACK = 1_000;

    private static final String SYSTEM = "PO system in ~/.po-xray/systems/<name>.properties; empty = the "
            + "environment variable POXRAY_SYSTEM, then system= in ~/.po-xray/monitoring.properties";
    private static final String RAW = "Also return the SOAP answer as received (large; left out above "
            + MAX_RAW + " characters)";
    private static final String READ_ONLY = " Read-only against the PO system.";
    private static final String SAVES = " Read-only against the PO system; writes only when saveDir is given "
            + "(response.xml, model.json, payload.bin under <saveDir>/<message>/; inside a git work tree only below "
            + "local/ or testdata/).";
    private static final String ID_OR_KEY = "Message ID or message key (a value with a backslash is a key); an ID "
            + "with several keys (sender and receiver side) returns the candidates instead";
    private static final String KEY = "Message key to read, e.g. one of the candidates returned for an ambiguous ID";
    private static final String DIRECTION = "OUTBOUND (sender side) or INBOUND (receiver side) to narrow a message ID";
    private static final String SAVE_DIR = "Directory to save the message to (only on request: customer data; "
            + "inside a git work tree only below local/ or testdata/)";
    private static final String LOOKUP_FIRST = " Call message_lookup (interfaces, services, parties) first for valid "
            + "filter values.";
    private static final List<String> KINDS =
            List.of("interfaces", "parties", "services", "connections", "iflows", "error-codes", "status-details");

    @Inject
    MonitoringEnvironment environment;

    // ------------------------------------------------------------------------------------------ results

    /** A search answer cut to the result budget. */
    record SearchView(String system, List<Message> messages, Integer number, boolean moreAvailable,
            boolean truncated, int dropped, String hint, Map<String, String> otherFields, String rawResponse) {
    }

    /** One message of an ambiguous ID; {@code key} is null and {@code keyless} true when it has no key. */
    record Candidate(String key, boolean keyless, String direction, String receiver, String status) {
    }

    /** The answer for an ID with several candidates: nothing was read for it. */
    record AmbiguousId(String idOrKey, List<Candidate> candidates, String hint) {
    }

    /** The answer for an ID without a usable key. */
    record NotFoundId(String idOrKey, String reason) {
    }

    record LogView(String system, String messageKey, List<LogEntry> entries, boolean truncated, int dropped,
            String hint, Map<String, String> otherFields, String saved, String rawResponse) {
    }

    record PartInfo(int index, String contentId, String contentType, long size, boolean binary) {
    }

    /** One page of the selected part (or of the whole payload when it is not multipart). */
    record PayloadView(String system, String messageKey, String source, Integer size, List<PartInfo> parts,
            Integer part, String contentType, String encoding, String content, int offset, int totalChars,
            boolean truncated, Integer nextOffset, String note, String saved, String rawResponse) {
    }

    record ValueView(String system, String value, String rawResponse) {
    }

    record ValuesView(String system, List<?> values, boolean truncated, int dropped, String hint,
            Map<String, String> otherFields, String saved, String rawResponse) {
    }

    // ------------------------------------------------------------------------------------------ search

    @Tool(name = "message_search", description = "Search messages of a PO system (getMessageList) in a time window "
            + "(default: the last 24 hours), newest data as the system returns it; one entry per message with status,"
            + " sender, receiver, interfaces, times, message ID and key." + LOOKUP_FIRST + READ_ONLY,
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String messageSearch(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = "Interface name", required = false) String interfaceName,
            @ToolArg(description = "Interface namespace", required = false) String interfaceNamespace,
            @ToolArg(description = "Sender component", required = false) String sender,
            @ToolArg(description = "Receiver component", required = false) String receiver,
            @ToolArg(description = "Statuses, e.g. systemError, holding, success", required = false)
            List<String> statuses,
            @ToolArg(description = "Only faulty messages", defaultValue = "false") boolean faulty,
            @ToolArg(description = "Start: 2026-11-10T08:00 (server zone), ISO with offset, -2h, -30m, -1d",
                    defaultValue = "-24h") String from,
            @ToolArg(description = "End (same forms; empty = now)", required = false) String to,
            @ToolArg(description = "Maximum number of messages", defaultValue = "50") int limit,
            @ToolArg(description = "Search the archive", defaultValue = "false") boolean archive,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> search(client(system).getMessageList(
                filter(interfaceName, interfaceNamespace, sender, receiver, statuses, faulty, from, to, archive),
                limit), includeRaw));
    }

    @Tool(name = "message_uds_search", description = "Search messages by user-defined search attributes "
            + "(getUserDefinedSearchMessages) with the filters of message_search. Call message_uds_filters first for "
            + "the interfaces that have attributes and message_uds_extractors for their names." + READ_ONLY,
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String messageUdsSearch(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = "Attributes as name=value") List<String> attributes,
            @ToolArg(description = "AND or OR", defaultValue = "AND") String operator,
            @ToolArg(description = "Interface name", required = false) String interfaceName,
            @ToolArg(description = "Interface namespace", required = false) String interfaceNamespace,
            @ToolArg(description = "Sender component", required = false) String sender,
            @ToolArg(description = "Receiver component", required = false) String receiver,
            @ToolArg(description = "Statuses", required = false) List<String> statuses,
            @ToolArg(description = "Only faulty messages", defaultValue = "false") boolean faulty,
            @ToolArg(description = "Start (as message_search)", defaultValue = "-24h") String from,
            @ToolArg(description = "End (empty = now)", required = false) String to,
            @ToolArg(description = "Maximum number of messages", defaultValue = "50") int limit,
            @ToolArg(description = "Search the archive", defaultValue = "false") boolean archive,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> {
            String op = operator == null || operator.isBlank() ? "AND" : operator.strip().toUpperCase(Locale.ROOT);
            if (!op.equals("AND") && !op.equals("OR")) {
                throw new IllegalArgumentException("operator must be AND or OR, not '" + operator + "'");
            }
            List<BusinessAttribute> attrs = new ArrayList<>();
            for (String a : attributes == null ? List.<String>of() : attributes) {
                int eq = a.indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException("attribute needs name=value, not '" + a + "'");
                }
                attrs.add(new BusinessAttribute(a.substring(0, eq).strip(), a.substring(eq + 1)));
            }
            if (attrs.isEmpty()) {
                throw new IllegalArgumentException("Give at least one attribute name=value");
            }
            MessageFilter filter = filter(interfaceName, interfaceNamespace, sender, receiver, statuses, faulty, from,
                    to, archive);
            return search(client(system).getUserDefinedSearchMessages(filter, limit, attrs, op), includeRaw);
        });
    }

    @Tool(name = "message_get", description = "Read messages by message ID (getMessagesByIDs) or by message key "
            + "(getMessagesByKeys; values with a backslash are keys)." + READ_ONLY,
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String messageGet(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = "Message IDs, or message keys (not both)") List<String> ids,
            @ToolArg(description = "Maximum number of messages (keys)", defaultValue = "50") int limit,
            @ToolArg(description = "Search the archive (IDs)", defaultValue = "false") boolean archive,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> {
            List<String> values = nonEmpty(ids, "ids");
            long keys = values.stream().filter(v -> v.indexOf('\\') >= 0).count();
            if (keys > 0 && keys < values.size()) {
                throw new IllegalArgumentException("Give either message IDs or message keys, not both");
            }
            MonitoringClient client = client(system);
            return search(keys > 0 ? client.getMessagesByKeys(values, limit)
                    : client.getMessagesByIds(values, List.of(), List.of(), archive), includeRaw);
        });
    }

    @Tool(name = "message_successors", description = "Read messages with their successors "
            + "(getMessagesWithSuccessors)." + READ_ONLY,
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String messageSuccessors(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = "Message IDs") List<String> ids,
            @ToolArg(description = "Search the archive", defaultValue = "false") boolean archive,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> search(client(system).getMessagesWithSuccessors(nonEmpty(ids, "ids"), archive), includeRaw));
    }

    @Tool(name = "message_predecessor", description = "Message ID of the predecessor of a message "
            + "(getPredecessorMessageId); value is absent when there is none." + READ_ONLY,
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String messagePredecessor(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = "Message ID") String id,
            @ToolArg(description = "OUTBOUND or INBOUND", required = false) String direction,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> {
            ValueResult r = client(system).getPredecessorMessageId(required(id, "id"), blank(direction));
            return json(new ValueView(r.system(), r.value(), raw(r.rawResponse(), includeRaw)));
        });
    }

    // ------------------------------------------------------------------------------------------ one message

    @Tool(name = "message_log", description = "Audit log of one message (getLogEntries), entries in the order of "
            + "the system." + SAVES,
            annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = true))
    String messageLog(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = ID_OR_KEY, required = false) String idOrKey,
            @ToolArg(description = KEY, required = false) String key,
            @ToolArg(description = DIRECTION, required = false) String direction,
            @ToolArg(description = "Read from the archive", defaultValue = "false") boolean archive,
            @ToolArg(description = "Maximum number of log entries", defaultValue = "50") int limit,
            @ToolArg(description = "Only entries older than this time (forms of message_search)", required = false)
            String olderThan,
            @ToolArg(description = SAVE_DIR, required = false) String saveDir,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> {
            checkSave(saveDir);
            MonitoringClient client = client(system);
            Object target = key(client, idOrKey, key, direction, archive);
            if (!(target instanceof String messageKey)) {
                return json(target);
            }
            LogResult r = client.getLogEntries(messageKey, archive, limit, null,
                    blank(olderThan) == null ? null : MessageTimes.parse(olderThan, Clock.systemDefaultZone()));
            String saved = save(saveDir, name(idOrKey, messageKey), r, r.rawResponse(), null);
            String raw = raw(r.rawResponse(), includeRaw);
            LogView empty = new LogView(r.system(), messageKey, List.of(), false, 0, null, r.otherFields(), saved, raw);
            int kept = McpBudget.fitting(r.entries(), budget(empty));
            int dropped = r.entries().size() - kept;
            return json(new LogView(r.system(), messageKey, r.entries().subList(0, kept), dropped > 0, dropped,
                    dropped > 0 ? dropped + " of " + r.entries().size() + " entries dropped to keep the result under "
                            + MAX_RESULT + " characters: lower limit or use olderThan" : null,
                    r.otherFields(), saved, raw));
        });
    }

    @Tool(name = "message_payload", description = "Payload of one message: the selected MIME part (default: the "
            + "first part after the XI header part) as text in its charset or base64, one page of maxChars "
            + "characters from offset; totalChars and truncated tell whether to call again with the next offset; "
            + "version or logged select another version (getMessageBytes..., getLoggedMessageBytes)." + SAVES,
            annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = true))
    String messagePayload(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = ID_OR_KEY, required = false) String idOrKey,
            @ToolArg(description = KEY, required = false) String key,
            @ToolArg(description = DIRECTION, required = false) String direction,
            @ToolArg(description = "Read from the archive", defaultValue = "false") boolean archive,
            @ToolArg(description = "Version of the message", required = false) Integer version,
            @ToolArg(description = "Logged version, e.g. a log location such as BI or AM", required = false)
            String logged,
            @ToolArg(description = "Index of the part, from 0 (empty = the first part after the XI header part)",
                    required = false) Integer part,
            @ToolArg(description = "First character of the page", defaultValue = "0") int offset,
            @ToolArg(description = "Characters per page", defaultValue = "20000") int maxChars,
            @ToolArg(description = SAVE_DIR, required = false) String saveDir,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> {
            if (version != null && blank(logged) != null) {
                throw new IllegalArgumentException("Give version or logged, not both");
            }
            if (offset < 0 || maxChars <= 0) {
                throw new IllegalArgumentException("offset must be 0 or more and maxChars more than 0");
            }
            checkSave(saveDir);
            MonitoringClient client = client(system);
            Object target = key(client, idOrKey, key, direction, archive);
            if (!(target instanceof String messageKey)) {
                return json(target);
            }
            MessagePayload p = version != null ? client.getMessageBytes(messageKey, version, archive)
                    : blank(logged) != null ? client.getLoggedMessageBytes(messageKey, logged.strip(), archive)
                    : client.getMessageBytes(messageKey, archive);
            byte[] bytes = p.bytes();
            String saved = save(saveDir, name(idOrKey, messageKey), p, p.rawResponse(), bytes);
            return payload(p, bytes, part, offset, maxChars, saved, raw(p.rawResponse(), includeRaw));
        });
    }

    @Tool(name = "message_uds_attributes", description = "User-defined search attributes of one message "
            + "(getUserDefinedSearchAttributes)." + SAVES,
            annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = true))
    String messageUdsAttributes(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = ID_OR_KEY, required = false) String idOrKey,
            @ToolArg(description = KEY, required = false) String key,
            @ToolArg(description = DIRECTION, required = false) String direction,
            @ToolArg(description = "Read from the archive", defaultValue = "false") boolean archive,
            @ToolArg(description = SAVE_DIR, required = false) String saveDir,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> {
            checkSave(saveDir);
            MonitoringClient client = client(system);
            Object target = key(client, idOrKey, key, direction, archive);
            if (!(target instanceof String messageKey)) {
                return json(target);
            }
            Lookup<BusinessAttribute> r = client.getUserDefinedSearchAttributes(messageKey, archive);
            String saved = save(saveDir, name(idOrKey, messageKey), r, r.rawResponse(), null);
            return values(r, saved, includeRaw);
        });
    }

    // ------------------------------------------------------------------------------------------ lookups

    @Tool(name = "message_uds_filters", description = "Interfaces that have a user-defined search filter "
            + "(getUserDefinedSearchFilters); use them with message_uds_extractors and message_uds_search." + READ_ONLY,
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String messageUdsFilters(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> values(client(system).getUserDefinedSearchFilters(), null, includeRaw));
    }

    @Tool(name = "message_uds_extractors", description = "User-defined search attributes defined for one interface "
            + "(getUserDefinedSearchExtractors); the names to use in message_uds_search." + READ_ONLY,
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String messageUdsExtractors(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = "Interface name (see message_uds_filters)") String interfaceName,
            @ToolArg(description = "Interface namespace") String interfaceNamespace,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> values(client(system).getUserDefinedSearchExtractors(required(interfaceName, "interfaceName"),
                required(interfaceNamespace, "interfaceNamespace")), null, includeRaw));
    }

    @Tool(name = "message_lookup", description = "Values the message monitoring knows, for valid search filters: "
            + "kind interfaces, parties, services (components), connections (adapter types), iflows, error-codes "
            + "(label = error label ID) or status-details (codes; empty = all)." + READ_ONLY,
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String messageLookup(@ToolArg(description = SYSTEM, required = false) String system,
            @ToolArg(description = "interfaces, parties, services, connections, iflows, error-codes or "
                    + "status-details") String kind,
            @ToolArg(description = "Error label ID (error-codes)", required = false) Integer label,
            @ToolArg(description = "Error codes (status-details; empty = all)", required = false) List<String> codes,
            @ToolArg(description = "Language of descriptions (iflows, status-details)", defaultValue = "en")
            String lang,
            @ToolArg(description = RAW, defaultValue = "false") boolean includeRaw) {
        return call(() -> {
            String k = kind == null ? "" : kind.strip().toLowerCase(Locale.ROOT);
            if (!KINDS.contains(k)) {
                throw new IllegalArgumentException("Unknown lookup kind '" + kind + "': use one of "
                        + String.join(", ", KINDS));
            }
            if (k.equals("error-codes") && label == null) {
                throw new IllegalArgumentException("kind error-codes needs label (the error label ID)");
            }
            String language = blank(lang) == null ? "en" : lang.strip();
            MonitoringClient client = client(system);
            Lookup<?> r = switch (k) {
                case "interfaces" -> client.getInterfaces();
                case "parties" -> client.getParties();
                case "services" -> client.getServices();
                case "connections" -> client.getConnections();
                case "iflows" -> client.getIntegrationFlows(language);
                case "error-codes" -> client.getErrorCodes(label);
                default -> codes == null || codes.isEmpty()
                        ? client.getAllAvailableStatusDetails(Locale.forLanguageTag(language))
                        : client.getStatusDetails(codes, Locale.forLanguageTag(language));
            };
            return values(r, null, includeRaw);
        });
    }

    // ------------------------------------------------------------------------------------------ helpers

    /** The client of the system: {@code system}, else POXRAY_SYSTEM, else ~/.po-xray/monitoring.properties. */
    private MonitoringClient client(String system) {
        Map<String, String> env = environment.env();
        String name = MonitoringSystems.resolve(system, env, Path.of(System.getProperty("user.home"), ".po-xray"));
        return new MonitoringClient(PoSystem.load(name, PoSystem.defaultDirectory(), env));
    }

    private static MessageFilter filter(String interfaceName, String interfaceNamespace, String sender,
            String receiver, List<String> statuses, boolean faulty, String from, String to, boolean archive) {
        Clock clock = Clock.systemDefaultZone();
        OffsetDateTime fromTime = MessageTimes.parse(blank(from) == null ? "-24h" : from, clock);
        OffsetDateTime toTime = blank(to) == null ? OffsetDateTime.now(clock) : MessageTimes.parse(to, clock);
        MessageFilter.Builder b = MessageFilter.builder().archive(archive).onlyFaultyMessages(faulty)
                .fromTime(fromTime).toTime(toTime).senderName(blank(sender)).receiverName(blank(receiver));
        if (blank(interfaceName) != null || blank(interfaceNamespace) != null) {
            b.interfaceRef(new MessageInterface(blank(interfaceName), blank(interfaceNamespace), null, null, null,
                    null));
        }
        List<String> s = statuses == null ? List.of()
                : statuses.stream().filter(Objects::nonNull).map(String::strip).filter(x -> !x.isEmpty()).toList();
        if (!s.isEmpty()) {
            b.statuses(s);
        }
        return b.build();
    }

    /** A search answer with as many messages as fit the result budget. */
    private static String search(SearchResult r, boolean includeRaw) {
        String raw = raw(r.rawResponse(), includeRaw);
        List<String> hints = new ArrayList<>();
        if (includeRaw && raw == null) {
            hints.add(rawOmitted(r.rawResponse()));
        }
        if (r.moreAvailable()) {
            hints.add("more messages exist on the system: narrow the filter or raise limit");
        }
        SearchView empty = new SearchView(r.system(), List.of(), r.number(), r.moreAvailable(), false, 0,
                String.join("; ", hints), r.otherFields(), raw);
        McpBudget.FittedSearch fitted = McpBudget.fit(r, budget(empty));
        if (fitted.hint() != null) {
            hints.addFirst(fitted.hint());
        }
        return json(new SearchView(r.system(), fitted.messages(), r.number(), r.moreAvailable(), fitted.truncated(),
                fitted.dropped(), hints.isEmpty() ? null : String.join("; ", hints), r.otherFields(), raw));
    }

    /** A lookup answer with as many values as fit the result budget. */
    private static String values(Lookup<?> r, String saved, boolean includeRaw) {
        String raw = raw(r.rawResponse(), includeRaw);
        ValuesView empty = new ValuesView(r.system(), List.of(), false, 0, null, r.otherFields(), saved, raw);
        int kept = McpBudget.fitting(r.values(), budget(empty));
        int dropped = r.values().size() - kept;
        String hint = dropped > 0 ? dropped + " of " + r.values().size() + " values dropped to keep the result under "
                + MAX_RESULT + " characters" : includeRaw && raw == null ? rawOmitted(r.rawResponse()) : null;
        return json(new ValuesView(r.system(), r.values().subList(0, kept), dropped > 0, dropped, hint,
                r.otherFields(), saved, raw));
    }

    /** One page of the selected part, shortened further if its JSON would exceed the result cap. */
    private static String payload(MessagePayload p, byte[] bytes, Integer part, int offset, int maxChars,
            String saved, String raw) {
        List<PartInfo> parts = p.parts().stream().map(pp -> new PartInfo(pp.index(), pp.contentId(),
                pp.contentType(), pp.size(), pp.text() == null)).toList();
        String content;
        String encoding;
        String contentType = null;
        Integer selected = null;
        if (p.parts().isEmpty()) {
            String text = bytes == null ? null : utf8(bytes);
            content = bytes == null ? "" : text != null ? text : Base64.getEncoder().encodeToString(bytes);
            encoding = bytes == null || text != null ? "text" : "base64";
        } else {
            int index = part != null ? part : p.parts().size() > 1 ? 1 : 0;
            if (index < 0 || index >= p.parts().size()) {
                throw new IllegalArgumentException("No part " + index + ": the payload has " + p.parts().size()
                        + " part(s), 0.." + (p.parts().size() - 1));
            }
            PayloadPart chosen = p.parts().get(index);
            selected = index;
            contentType = chosen.contentType();
            content = chosen.text() != null ? chosen.text() : chosen.base64();
            encoding = chosen.text() != null ? "text" : "base64";
        }
        int total = content.length();
        if (offset > total) {
            throw new IllegalArgumentException("offset " + offset + " is beyond the end of the content (" + total
                    + " characters)");
        }
        int length = Math.min(maxChars, total - offset);
        while (true) {
            int end = offset + length;
            if (end < total && end > offset && Character.isHighSurrogate(content.charAt(end - 1))) {
                // never split a surrogate pair; a page that would be empty takes the whole pair (maxChars + 1)
                end = end - 1 > offset ? end - 1 : end + 1;
            }
            boolean truncated = end < total;
            String s = compact(new PayloadView(p.system(), p.messageKey(), p.source(),
                    bytes == null ? null : bytes.length, parts, selected, contentType, encoding,
                    content.substring(offset, end), offset, total, truncated, truncated ? end : null, p.note(), saved,
                    raw));
            if (s.length() <= MAX_RESULT) {
                return s;
            }
            if (end - offset <= 1 || end - offset == 2 && Character.isHighSurrogate(content.charAt(offset))) {
                throw new ToolCallException("Payload page too large for an MCP result even without content: "
                        + "use saveDir to keep the payload");
            }
            length = Math.max(1, end - offset - (s.length() - MAX_RESULT) - 100);
        }
    }

    /**
     * The message key to read ({@code key} when given, else {@code idOrKey} resolved by {@link KeyResolver}), or the
     * result to return instead: the candidates of an ambiguous ID, or the reason of an unusable one.
     */
    private static Object key(MonitoringClient client, String idOrKey, String key, String direction, boolean archive) {
        if (blank(key) != null) {
            return key.strip();
        }
        String id = required(idOrKey, "idOrKey (or key)");
        return switch (KeyResolver.resolve(client, id, blank(direction), archive)) {
            case Resolution.Key k -> k.messageKey();
            case Resolution.NotFound n -> new NotFoundId(n.idOrKey(), n.reason());
            case Resolution.Ambiguous a -> new AmbiguousId(id, a.candidates().stream()
                    .map(m -> new Candidate(m.messageKey(), m.messageKey() == null, m.direction(), m.receiverName(),
                            m.status()))
                    .toList(), "Message " + id + " has " + a.candidates().size() + " candidates: call again with key"
                    + " (one of the keys listed) or direction OUTBOUND / INBOUND; nothing was read");
        };
    }

    /** Refuses a saveDir before any request is sent. */
    private static void checkSave(String saveDir) {
        if (blank(saveDir) != null) {
            SaveTarget.check(Path.of(saveDir.strip()));
        }
    }

    /** Saves the message under saveDir when given; the message directory, else null. */
    private static String save(String saveDir, String messageId, Object model, String rawResponse, byte[] payload) {
        return blank(saveDir) == null ? null
                : MessageArchive.save(Path.of(saveDir.strip()), messageId, model, rawResponse, payload).toString();
    }

    private static String name(String idOrKey, String messageKey) {
        return blank(idOrKey) != null ? idOrKey.strip() : messageKey;
    }

    /** The raw answer when asked for and small enough, else null. */
    private static String raw(String rawResponse, boolean includeRaw) {
        return includeRaw && rawResponse != null && rawResponse.length() <= MAX_RAW ? rawResponse : null;
    }

    private static String rawOmitted(String rawResponse) {
        return "rawResponse left out: " + (rawResponse == null ? 0 : rawResponse.length()) + " characters (above "
                + MAX_RAW + ")";
    }

    /** Characters left for the list part of a result whose other parts are {@code empty}. */
    private static int budget(Object empty) {
        return Math.max(0, MAX_RESULT - compact(empty).length() - SLACK);
    }

    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String required(String s, String name) {
        if (blank(s) == null) {
            throw new IllegalArgumentException("Give " + name);
        }
        return s.strip();
    }

    private static List<String> nonEmpty(List<String> values, String name) {
        List<String> v = values == null ? List.of()
                : values.stream().filter(Objects::nonNull).map(String::strip).filter(x -> !x.isEmpty()).toList();
        if (v.isEmpty()) {
            throw new IllegalArgumentException("Give at least one value in " + name);
        }
        return v;
    }

    private static String compact(Object value) {
        return Json.mapper().writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(value);
    }

    /** Compact JSON within the result cap; above it a tool error (never a file: message results are customer data). */
    private static String json(Object value) {
        String s = compact(value);
        if (s.length() > MAX_RESULT) {
            throw new ToolCallException("Result too large (" + s.length() + " characters, the limit is " + MAX_RESULT
                    + "): narrow the request");
        }
        return s;
    }

    /** Errors become one-line tool errors (no stack trace, no credentials). */
    private static String call(Callable<String> body) {
        try {
            return body.call();
        } catch (ToolCallException e) {
            throw e;
        } catch (PoException | IllegalArgumentException | IllegalStateException e) {
            throw new ToolCallException(oneLine(e.getMessage()), e);
        } catch (Exception e) {
            throw new ToolCallException(oneLine(e.getClass().getSimpleName() + ": " + e.getMessage()), e);
        }
    }

    private static String oneLine(String s) {
        return s == null ? "(no message)" : s.replaceAll("\\s*\\R\\s*", " ").strip();
    }
}
