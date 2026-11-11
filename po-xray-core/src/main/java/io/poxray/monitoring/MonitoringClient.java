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
import java.util.Objects;
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
