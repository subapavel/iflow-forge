package io.poxray.monitoring;

import io.poxray.po.PoException;
import io.poxray.po.PoHttp;
import io.poxray.po.PoSystem;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * Read-only client of the PO service AdapterMessageMonitoring (SOAP 1.1, document style, endpoint
 * {@code /AdapterMessageMonitoring/basic?style=document}, F-17 Appendix E) on an online {@link PoSystem}: the HTTP
 * setup of {@link PoHttp} (HTTP/1.1, no redirects, trust store, proxy), preemptive Basic authentication, 2 min
 * request timeout. Every failure is a {@link MonitoringException}.
 */
public final class MonitoringClient {

    static final String ENDPOINT = "/AdapterMessageMonitoring/basic?style=document";
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    /** Line breaks and blanks inside a base64Binary value. */
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final PoSystem system;
    private final HttpClient http;
    private final String authorization;
    private final URI endpoint;

    public MonitoringClient(PoSystem system) {
        Objects.requireNonNull(system, "system");
        if (system.url() == null) {
            throw new MonitoringException("System '" + system.name() + "' is a directory of TPZ exports (tpz=...),"
                    + " monitoring needs an online system");
        }
        this.system = system;
        try {
            this.http = PoHttp.client(system);
        } catch (PoException e) {
            throw new MonitoringException(e.getMessage(), e);
        }
        this.authorization = PoHttp.authorization(system);
        this.endpoint = system.url().resolve(ENDPOINT);
    }

    public PoSystem system() {
        return system;
    }

    /** Operation {@code getMessageList}: the messages matching {@code filter}, at most {@code maxMessages}. */
    public SearchResult getMessageList(MessageFilter filter, int maxMessages) {
        Objects.requireNonNull(filter, "filter");
        StringBuilder body = new StringBuilder("<op:filter>");
        filter.writeTo(body);
        body.append("</op:filter>");
        Soap.element(body, "op", "maxMessages", maxMessages);
        Reply reply = call("getMessageList", body.toString());
        return SearchResult.read(system.name(), reply.response(), reply.raw());
    }

    // Request children in the order of F-17 Appendix B. A required but nillable parameter (mark N) left null is sent
    // with xsi:nil="true"; an optional one (mark O) left null is not sent.

    /** Operation {@code getMessagesByIDs}: the messages with these message, reference or correlation IDs. */
    public SearchResult getMessagesByIds(List<String> messageIds, List<String> referenceIds,
            List<String> correlationIds, boolean archive) {
        StringBuilder body = new StringBuilder();
        Soap.nillableStrings(body, "op", "messageIds", messageIds);
        Soap.nillableStrings(body, "op", "referenceIds", referenceIds);
        Soap.nillableStrings(body, "op", "correlationIds", correlationIds);
        Soap.element(body, "op", "archive", archive);
        return search("getMessagesByIDs", body);
    }

    /** Operation {@code getMessagesByKeys}: the messages with these message keys (the parameter {@code filter}). */
    public SearchResult getMessagesByKeys(List<String> messageKeys, int maxMessages) {
        StringBuilder body = new StringBuilder();
        Soap.nillableStrings(body, "op", "filter", messageKeys);
        Soap.element(body, "op", "maxMessages", maxMessages);
        return search("getMessagesByKeys", body);
    }

    /** Operation {@code getMessagesWithSuccessors}: the messages with these IDs and their successors. */
    public SearchResult getMessagesWithSuccessors(List<String> messageIds, boolean archive) {
        StringBuilder body = new StringBuilder();
        Soap.nillableStrings(body, "op", "messageIds", messageIds);
        Soap.element(body, "op", "archive", archive);
        return search("getMessagesWithSuccessors", body);
    }

    /** Operation {@code getPredecessorMessageId}: the ID of the predecessor of a message in {@code direction}. */
    public ValueResult getPredecessorMessageId(String messageId, String direction) {
        StringBuilder body = new StringBuilder();
        Soap.nillable(body, "op", "messageId", messageId);
        Soap.nillable(body, "op", "direction", direction);
        return ValueResult.read(system.name(), call("getPredecessorMessageId", body.toString()));
    }

    /**
     * Operation {@code getLogEntries}: the audit log of a message, at most {@code maxResults} entries older than
     * {@code olderThan}; {@code locale} (language, country, variant; each sent, empty when the locale has none) is not
     * sent when null.
     */
    public LogResult getLogEntries(String messageKey, boolean archive, int maxResults, Locale locale,
            OffsetDateTime olderThan) {
        StringBuilder body = new StringBuilder();
        Soap.nillable(body, "op", "messageKey", messageKey);
        Soap.element(body, "op", "archive", archive);
        Soap.element(body, "op", "maxResults", maxResults);
        if (locale != null) {
            locale(body, locale);
        }
        Soap.nillable(body, "op", "olderThan", olderThan);
        return LogResult.read(system.name(), messageKey, call("getLogEntries", body.toString()));
    }

    /**
     * Operation {@code getUserDefinedSearchMessages}: the messages matching {@code filter} whose user-defined search
     * attributes match {@code attributes}, combined with {@code operator} (e.g. {@code AND}, {@code OR}).
     */
    public SearchResult getUserDefinedSearchMessages(MessageFilter filter, int maxMessages,
            List<BusinessAttribute> attributes, String operator) {
        StringBuilder body = new StringBuilder();
        if (filter == null) {
            Soap.nillable(body, "op", "filter", null);
        } else {
            body.append("<op:filter>");
            filter.writeTo(body);
            body.append("</op:filter>");
        }
        Soap.element(body, "op", "maxMessages", maxMessages);
        if (attributes == null) {
            Soap.nillable(body, "op", "attributes", null);
        } else {
            body.append("<op:attributes>");
            for (BusinessAttribute a : attributes) {
                if (a == null) {
                    Soap.nillable(body, "ws", "BusinessAttribute", null);
                } else {
                    body.append("<ws:BusinessAttribute>");
                    Soap.element(body, "ws", "name", a.name());
                    Soap.element(body, "ws", "value", a.value());
                    body.append("</ws:BusinessAttribute>");
                }
            }
            body.append("</op:attributes>");
        }
        Soap.nillable(body, "op", "operator", operator);
        return search("getUserDefinedSearchMessages", body);
    }

    /** Operation {@code getUserDefinedSearchFilters}: the interfaces that have a user-defined search filter. */
    public Lookup<MessageInterface> getUserDefinedSearchFilters() {
        return lookup("getUserDefinedSearchFilters", new StringBuilder(), Lookup.INTERFACE);
    }

    /** Operation {@code getUserDefinedSearchExtractors}: the search attributes defined for an interface. */
    public Lookup<AttributeMetadata> getUserDefinedSearchExtractors(String name, String namespace) {
        StringBuilder body = new StringBuilder();
        Soap.nillable(body, "op", "name", name);
        Soap.nillable(body, "op", "namespace", namespace);
        return lookup("getUserDefinedSearchExtractors", body, Lookup.ATTRIBUTE_METADATA);
    }

    /** Operation {@code getUserDefinedSearchAttributes}: the user-defined search attributes of one message. */
    public Lookup<BusinessAttribute> getUserDefinedSearchAttributes(String messageKey, boolean archive) {
        StringBuilder body = new StringBuilder();
        Soap.nillable(body, "op", "messageKey", messageKey);
        Soap.element(body, "op", "archive", archive);
        return lookup("getUserDefinedSearchAttributes", body, Lookup.BUSINESS_ATTRIBUTE);
    }

    /** Operation {@code getInterfaces}: the interfaces known to the monitoring. */
    public Lookup<MessageInterface> getInterfaces() {
        return lookup("getInterfaces", new StringBuilder(), Lookup.INTERFACE);
    }

    /** Operation {@code getParties}: the parties known to the monitoring. */
    public Lookup<Party> getParties() {
        return lookup("getParties", new StringBuilder(), Lookup.PARTY);
    }

    /** Operation {@code getServices}: the services (communication components) known to the monitoring. */
    public Lookup<String> getServices() {
        return lookup("getServices", new StringBuilder(), Lookup.STRING);
    }

    /** Operation {@code getConnections}: the connections (adapter types) known to the monitoring. */
    public Lookup<String> getConnections() {
        return lookup("getConnections", new StringBuilder(), Lookup.STRING);
    }

    /** Operation {@code getIntegrationFlows}: the integration flows, described in {@code language}. */
    public Lookup<IntegrationFlow> getIntegrationFlows(String language) {
        StringBuilder body = new StringBuilder();
        Soap.nillable(body, "op", "language", language);
        return lookup("getIntegrationFlows", body, Lookup.INTEGRATION_FLOW);
    }

    /** Operation {@code getErrorCodes}: the error codes of an error label. */
    public Lookup<String> getErrorCodes(int errorLabelId) {
        StringBuilder body = new StringBuilder();
        Soap.element(body, "op", "errorLabelID", errorLabelId);
        return lookup("getErrorCodes", body, Lookup.STRING);
    }

    /** Operation {@code getStatusDetails}: the descriptions of these error codes in {@code locale} (nil if null). */
    public Lookup<StatusDetail> getStatusDetails(List<String> errorCodes, Locale locale) {
        StringBuilder body = new StringBuilder();
        Soap.nillableStrings(body, "op", "errorCodes", errorCodes);
        requiredLocale(body, locale);
        return lookup("getStatusDetails", body, Lookup.STATUS_DETAIL);
    }

    /** Operation {@code getAllAvailableStatusDetails}: the descriptions of all error labels in {@code locale}. */
    public Lookup<StatusDetail> getAllAvailableStatusDetails(Locale locale) {
        StringBuilder body = new StringBuilder();
        requiredLocale(body, locale);
        return lookup("getAllAvailableStatusDetails", body, Lookup.STATUS_DETAIL);
    }

    /** {@code op:locale} with {@code ws:language}, {@code ws:country}, {@code ws:variant}, each sent, empty if unset. */
    private static void locale(StringBuilder body, Locale locale) {
        body.append("<op:locale>");
        Soap.element(body, "ws", "language", locale.getLanguage());
        Soap.element(body, "ws", "country", locale.getCountry());
        Soap.element(body, "ws", "variant", locale.getVariant());
        body.append("</op:locale>");
    }

    /** A required but nillable {@code op:locale}: nil when null. */
    private static void requiredLocale(StringBuilder body, Locale locale) {
        if (locale == null) {
            Soap.nillable(body, "op", "locale", null);
        } else {
            locale(body, locale);
        }
    }

    private <T> Lookup<T> lookup(String operation, StringBuilder body, Lookup.Item<T> item) {
        return Lookup.read(system.name(), call(operation, body.toString()), item);
    }

    /** Operation {@code getMessageBytesJavaLangStringBoolean}: the current payload of a message. */
    public MessagePayload getMessageBytes(String messageKey, boolean archive) {
        StringBuilder body = new StringBuilder();
        Soap.nillable(body, "op", "messageKey", messageKey);
        Soap.element(body, "op", "archive", archive);
        return payload("getMessageBytesJavaLangStringBoolean", messageKey, "current", body);
    }

    /** Operation {@code getMessageBytesJavaLangStringIntBoolean}: the payload of a message in this version. */
    public MessagePayload getMessageBytes(String messageKey, int version, boolean archive) {
        StringBuilder body = new StringBuilder();
        Soap.nillable(body, "op", "messageKey", messageKey);
        Soap.element(body, "op", "version", version);
        Soap.element(body, "op", "archive", archive);
        return payload("getMessageBytesJavaLangStringIntBoolean", messageKey, "version:" + version, body);
    }

    /** Operation {@code getLoggedMessageBytes}: the payload of a message logged at {@code version} (e.g. a log step). */
    public MessagePayload getLoggedMessageBytes(String messageKey, String version, boolean archive) {
        StringBuilder body = new StringBuilder();
        Soap.nillable(body, "op", "messageKey", messageKey);
        Soap.nillable(body, "op", "version", version);
        Soap.element(body, "op", "archive", archive);
        return payload("getLoggedMessageBytes", messageKey, "logged:" + version, body);
    }

    private SearchResult search(String operation, StringBuilder body) {
        Reply reply = call(operation, body.toString());
        return SearchResult.read(system.name(), reply.response(), reply.raw());
    }

    /** The base64Binary {@code Response} decoded (line breaks and blanks ignored) and split into its MIME parts. */
    private MessagePayload payload(String operation, String messageKey, String source, StringBuilder body) {
        Reply reply = call(operation, body.toString());
        if (Soap.nil(reply.response())) {
            return new MessagePayload(system.name(), messageKey, source, null, List.of(), "no payload", reply.raw());
        }
        String text = reply.response().getTextContent();
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(WHITESPACE.matcher(text).replaceAll(""));
        } catch (IllegalArgumentException e) {
            throw new MonitoringException(operation + ": unexpected response from " + system.name()
                    + " (Response is not base64: " + e.getMessage() + ")", e);
        }
        List<PayloadPart> parts = Mime.parts(bytes);
        return new MessagePayload(system.name(), messageKey, source, bytes, parts,
                parts.isEmpty() ? "not multipart" : null, reply.raw());
    }

    /** The answer of an operation: the body as received and its {@code OP:Response} element. */
    record Reply(String raw, Element response) {
    }

    /** Posts the envelope of {@code operation} with this operation content and returns its answer. */
    Reply call(String operation, String bodyXml) {
        HttpRequest req = HttpRequest.newBuilder(endpoint)
                .timeout(TIMEOUT)
                .header("Authorization", authorization)
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(Soap.envelope(operation, bodyXml), StandardCharsets.UTF_8))
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (ConnectException | HttpConnectTimeoutException e) {
            throw new MonitoringException(operation + " failed: cannot connect to " + endpoint.getHost() + " ("
                    + PoHttp.describe(e) + "; network / VPN?)", e);
        } catch (IOException e) {
            throw new MonitoringException(operation + " failed: " + PoHttp.describe(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MonitoringException(operation + " interrupted", e);
        }
        return new Reply(Soap.text(resp), Soap.response(Soap.parse(resp, operation, system), operation, system));
    }
}
