package io.poxray.monitoring;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.poxray.po.PoSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Local fake of the PO service AdapterMessageMonitoring for tests: a JDK HTTP server on a random localhost port that
 * records every request (method, path and query, headers, body) and answers every request with one configured
 * response (status, headers, body). Synthetic names only; the system is called {@code QA1}, user {@code alice}.
 */
public final class FakeMonitoringPo implements AutoCloseable {

    public static final String SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";
    /** Operation elements and their children (F-17 Appendix A, OP). */
    public static final String OP = "urn:AdapterMessageMonitoringVi";
    /** AdapterFilter, AdapterFrameworkData, MessageSearchReturnValue, BusinessAttribute, ... (WS). */
    public static final String WS = "urn:com.sap.aii.mdt.server.adapterframework.ws";
    /** MessageInterface, MessageParty, BooleanAttribute, DurationAttribute, ... (DATA). */
    public static final String DATA = "urn:com.sap.aii.mdt.api.data";
    /** ArrayOfString items (JL). */
    public static final String JL = "urn:java/lang";
    /** Fault detail elements (FLT). */
    public static final String FLT = "urn:AdapterMessageMonitoringWsd/AdapterMessageMonitoringVi";
    public static final String ENDPOINT = "/AdapterMessageMonitoring/basic?style=document";
    public static final String USER = "alice";
    public static final String PASSWORD = "s3cret";

    /** One recorded request; header names are looked up case-insensitively. */
    public record Request(String method, String pathAndQuery, Map<String, List<String>> headers, String body) {

        /** First value of a header, null if absent. */
        public String header(String name) {
            List<String> values = headers.get(name);
            return values == null || values.isEmpty() ? null : values.getFirst();
        }

        /** The body parsed namespace-aware (secure processing on, DOCTYPE disallowed). */
        public Document document() {
            return parse(body);
        }
    }

    private record Response(int status, Map<String, String> headers, byte[] body) {
    }

    public final PoSystem system;
    private final HttpServer server;
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile Response response = new Response(500, Map.of("Content-Type", "text/plain"),
            "no response configured".getBytes(StandardCharsets.UTF_8));

    public FakeMonitoringPo() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
        system = new PoSystem("QA1", URI.create(baseUrl() + "/"), USER, PASSWORD);
    }

    /** {@code http://127.0.0.1:<port>} without a trailing slash. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Answers every following request with this status, headers and body. */
    public FakeMonitoringPo respond(int status, Map<String, String> headers, byte[] body) {
        response = new Response(status, Map.copyOf(headers), body.clone());
        return this;
    }

    /** Answers every following request with this status, content type and UTF-8 body. */
    public FakeMonitoringPo respond(int status, String contentType, String body) {
        return respond(status, Map.of("Content-Type", contentType), body.getBytes(StandardCharsets.UTF_8));
    }

    /** Answers with HTTP 200, {@code text/xml; charset=utf-8} and this SOAP body content in an envelope. */
    public FakeMonitoringPo respondSoap(String bodyContentXml) {
        return respond(200, "text/xml; charset=utf-8", envelope(bodyContentXml));
    }

    /** All requests received so far, in order. */
    public List<Request> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    public Request lastRequest() {
        synchronized (requests) {
            return requests.getLast();
        }
    }

    private void handle(HttpExchange ex) throws IOException {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        ex.getRequestHeaders().forEach((k, v) -> headers.put(k, List.copyOf(v)));
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new Request(ex.getRequestMethod(), ex.getRequestURI().toString(),
                Collections.unmodifiableMap(headers), body));
        Response r = response;
        r.headers().forEach((k, v) -> ex.getResponseHeaders().add(k, v));
        ex.sendResponseHeaders(r.status(), r.body().length == 0 ? -1 : r.body().length);
        if (r.body().length > 0) {
            ex.getResponseBody().write(r.body());
        }
        ex.close();
    }

    private boolean closed;

    /** Stops the server; a second call does nothing. */
    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------------------------------ fixtures

    /** A SOAP 1.1 envelope around the given body content (prefix {@code S}, not the usual one on purpose). */
    public static String envelope(String bodyContentXml) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<S:Envelope xmlns:S=\"" + SOAP_ENV + "\"><S:Body>"
                + bodyContentXml + "</S:Body></S:Envelope>";
    }

    /**
     * A SOAP 1.1 fault envelope with the F-17 Appendix D detail:
     * {@code FLT:<operation>_com.sap.aii.mdt.server.adapterframework.ws.OperationFailedException} with children
     * {@code WS:args} (items {@code JL:String}) and {@code WS:code}.
     */
    public static String operationFailedFault(String operation, String faultString, int code, String... args) {
        StringBuilder items = new StringBuilder();
        for (String a : args) {
            items.append("<j:String>").append(a).append("</j:String>");
        }
        return envelope("<S:Fault><faultcode>S:Server</faultcode><faultstring>" + faultString + "</faultstring>"
                + "<detail><f:" + operation + "_com.sap.aii.mdt.server.adapterframework.ws.OperationFailedException"
                + " xmlns:f=\"" + FLT + "\" xmlns:w=\"" + WS + "\" xmlns:j=\"" + JL + "\">"
                + "<w:args>" + items + "</w:args><w:code>" + code + "</w:code>"
                + "</f:" + operation + "_com.sap.aii.mdt.server.adapterframework.ws.OperationFailedException>"
                + "</detail></S:Fault>");
    }

    // ------------------------------------------------------------------------------------------ DOM helpers

    /** Namespace-aware parse with secure processing on and DOCTYPE disallowed. */
    public static Document parse(String xml) {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("not well-formed XML: " + e.getMessage(), e);
        }
    }

    /** Element children of a node, in document order. */
    public static List<Element> children(Node parent) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    /** {@code "<namespace URI> <local name>"} of each element child, in document order. */
    public static List<String> childNames(Node parent) {
        return children(parent).stream().map(FakeMonitoringPo::qname).toList();
    }

    /** {@code "<namespace URI> <local name>"} of an element. */
    public static String qname(Element e) {
        return e.getNamespaceURI() + " " + e.getLocalName();
    }

    /** The only element child with this namespace and local name; fails if there is none or more than one. */
    public static Element child(Node parent, String namespace, String localName) {
        List<Element> found = children(parent).stream()
                .filter(e -> namespace.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName()))
                .toList();
        if (found.size() != 1) {
            throw new AssertionError("expected one {" + namespace + "}" + localName + " under " + parent.getNodeName()
                    + ", found " + found.size() + " (children: " + childNames(parent) + ")");
        }
        return found.getFirst();
    }

    /** Text of each element child with this namespace and local name, keyed by local name, in document order. */
    public static Map<String, String> texts(Node parent, String namespace) {
        Map<String, String> out = new LinkedHashMap<>();
        children(parent).stream().filter(e -> namespace.equals(e.getNamespaceURI()))
                .forEach(e -> out.put(e.getLocalName(), e.getTextContent()));
        return out;
    }
}
