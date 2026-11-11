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

    /** When set, the SOAP body content of the answer to each request, by the local name of its operation element. */
    private volatile java.util.function.Function<String, String> perOperation;

    /**
     * Answers every following request with HTTP 200, {@code text/xml; charset=utf-8} and an envelope around
     * {@code bodyContent.apply(<operation name>)}, the operation name being the local name of the request's only
     * SOAP body child.
     */
    public FakeMonitoringPo respondPerOperation(java.util.function.Function<String, String> bodyContent) {
        perOperation = bodyContent;
        return this;
    }

    /** The local name of the only SOAP body child of a request body. */
    public static String operationName(String requestBody) {
        Element body = child(parse(requestBody).getDocumentElement(), SOAP_ENV, "Body");
        List<Element> ops = children(body);
        if (ops.size() != 1) {
            throw new AssertionError("expected one operation element in the SOAP body, found " + childNames(body));
        }
        return ops.getFirst().getLocalName();
    }

    /** Answers every following request with this status, headers and body. */
    public FakeMonitoringPo respond(int status, Map<String, String> headers, byte[] body) {
        perOperation = null;
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
        java.util.function.Function<String, String> perOp = perOperation;
        Response r = perOp == null ? response : new Response(200, Map.of("Content-Type", "text/xml; charset=utf-8"),
                envelope(perOp.apply(operationName(body))).getBytes(StandardCharsets.UTF_8));
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

    /**
     * A SOAP 1.1 fault envelope with the F-17 Appendix D {@code InvalidKeyException} detail:
     * {@code FLT:<operation>_com.sap.aii.mdt.server.adapterframework.ws.InvalidKeyException} with children
     * {@code WS:args} (items {@code JL:String}), {@code WS:code} and {@code WS:messageKey}.
     */
    public static String invalidKeyFault(String operation, String faultString, int code, String messageKey,
            String... args) {
        StringBuilder items = new StringBuilder();
        for (String a : args) {
            items.append("<j:String>").append(a).append("</j:String>");
        }
        String detail = operation + "_com.sap.aii.mdt.server.adapterframework.ws.InvalidKeyException";
        return envelope("<S:Fault><faultcode>S:Server</faultcode><faultstring>" + faultString + "</faultstring>"
                + "<detail><f:" + detail + " xmlns:f=\"" + FLT + "\" xmlns:w=\"" + WS + "\" xmlns:j=\"" + JL + "\">"
                + "<w:args>" + items + "</w:args><w:code>" + code + "</w:code>"
                + "<w:messageKey>" + messageKey + "</w:messageKey>"
                + "</f:" + detail + ">"
                + "</detail></S:Fault>");
    }

    /**
     * The SOAP body content of a search answer ({@code OP:<operation>Response/OP:Response} of type
     * {@code MessageSearchReturnValue}) with these {@code AdapterFrameworkData} items (prefixes {@code m} OP, {@code w}
     * WS, {@code d} DATA, {@code j} JL declared on the wrapper).
     */
    public static String searchResponse(String operation, int number, boolean warning, String... messagesXml) {
        return "<m:" + operation + "Response xmlns:m=\"" + OP + "\" xmlns:w=\"" + WS + "\" xmlns:d=\"" + DATA
                + "\" xmlns:j=\"" + JL + "\"><m:Response><w:list>" + String.join("", messagesXml) + "</w:list>"
                + "<w:number>" + number + "</w:number><w:warning>" + warning + "</w:warning>"
                + "<w:displayPermissionWarning>false</w:displayPermissionWarning></m:Response></m:"
                + operation + "Response>";
    }

    /** One {@code WS:AdapterFrameworkData} item with messageID, messageKey, direction and status (prefix {@code w}). */
    public static String message(String messageId, String messageKey, String direction, String status) {
        return "<w:AdapterFrameworkData><w:direction>" + direction + "</w:direction><w:messageID>" + messageId
                + "</w:messageID><w:messageKey>" + messageKey + "</w:messageKey><w:status>" + status
                + "</w:status></w:AdapterFrameworkData>";
    }

    /**
     * The SOAP body content {@code OP:<operation>Response/OP:Response} with this content (prefixes {@code m} OP,
     * {@code w} WS, {@code d} DATA, {@code j} JL, {@code xsi} declared on the wrapper).
     */
    public static String valueResponse(String operation, String responseContentXml) {
        return "<m:" + operation + "Response xmlns:m=\"" + OP + "\" xmlns:w=\"" + WS + "\" xmlns:d=\"" + DATA
                + "\" xmlns:j=\"" + JL + "\" xmlns:xsi=\"" + XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI + "\">"
                + "<m:Response>" + responseContentXml + "</m:Response></m:" + operation + "Response>";
    }

    /**
     * The operation element of a recorded request: the envelope is SOAP 1.1 and its body holds exactly one element,
     * {@code OP:<operation>}; fails otherwise.
     */
    public static Element operation(Request request, String operation) {
        Element envelope = request.document().getDocumentElement();
        if (!(SOAP_ENV + " Envelope").equals(qname(envelope))) {
            throw new AssertionError("not a SOAP 1.1 envelope: " + qname(envelope));
        }
        Element body = child(envelope, SOAP_ENV, "Body");
        List<String> names = childNames(body);
        if (!names.equals(List.of(OP + " " + operation))) {
            throw new AssertionError("expected the body to hold only {" + OP + "}" + operation + ", found " + names);
        }
        return children(body).getFirst();
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
