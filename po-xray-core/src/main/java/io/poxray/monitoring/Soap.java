package io.poxray.monitoring;

import io.poxray.po.PoHttp;
import io.poxray.po.PoSystem;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * SOAP 1.1 for the AdapterMessageMonitoring service without a SOAP library: the request envelope, the answer parsed
 * namespace-aware (secure processing on, DOCTYPE and external entities off) and the error mapping of
 * {@code PoClient}: 401, 3xx and the logon page are an authentication failure, 403 names the monitoring role, a SOAP
 * fault carries its fault string and the {@code OperationFailedException} code and args (F-17 Appendix D).
 */
final class Soap {

    static final String ENV = "http://schemas.xmlsoap.org/soap/envelope/";
    /** Operation elements and their children (F-17 Appendix A, OP). */
    static final String OP = "urn:AdapterMessageMonitoringVi";
    /** AdapterFilter, AdapterFrameworkData, MessageSearchReturnValue, BusinessAttribute, ... (WS). */
    static final String WS = "urn:com.sap.aii.mdt.server.adapterframework.ws";
    /** MessageInterface, MessageParty, BooleanAttribute, DurationAttribute, ... (DATA). */
    static final String DATA = "urn:com.sap.aii.mdt.api.data";
    /** Items of string arrays (JL). */
    static final String JL = "urn:java/lang";
    private static final String XSI = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    /** Characters of an unexpected body quoted in the error message. */
    private static final int EXCERPT = 200;
    /** The only operations a request may name: the 21 read operations of F-17 Appendix B (the allow-list). */
    static final Set<String> READ_OPERATIONS = Set.of(
            "getMessageList", "getUserDefinedSearchMessages", "getMessagesByIDs", "getMessagesByKeys",
            "getMessagesWithSuccessors", "getPredecessorMessageId", "getLogEntries",
            "getMessageBytesJavaLangStringBoolean", "getMessageBytesJavaLangStringIntBoolean", "getLoggedMessageBytes",
            "getUserDefinedSearchFilters", "getUserDefinedSearchExtractors", "getUserDefinedSearchAttributes",
            "getInterfaces", "getParties", "getServices", "getConnections", "getIntegrationFlows", "getErrorCodes",
            "getStatusDetails", "getAllAvailableStatusDetails");
    /** The value kept in an {@code otherFields} map for a child marked {@code xsi:nil="true"}. */
    static final String NIL = "(nil)";
    /** A {@code charset} parameter in a content type (same test as {@link PoHttp#charset}). */
    private static final Pattern CHARSET_PARAM = Pattern.compile("(?i)charset=");
    /** The encoding of an XML declaration at the very start of a body read as ISO-8859-1. */
    private static final Pattern XML_ENCODING =
            Pattern.compile("^<\\?xml\\s[^>]*?encoding\\s*=\\s*[\"']([A-Za-z][A-Za-z0-9._-]*)[\"']");
    /** Bytes of a body searched for the XML declaration. */
    private static final int DECLARATION = 200;

    private Soap() {
    }

    // ------------------------------------------------------------------------------------------ request

    /**
     * The request envelope of an operation: {@code bodyXml} is the content of the operation element and may use the
     * prefixes {@code op} (OP), {@code ws} (WS), {@code d} (DATA) and {@code jl} (JL) declared on the envelope. An
     * operation outside {@link #READ_OPERATIONS} is a {@link MonitoringException}, so it is never sent.
     */
    static String envelope(String operation, String bodyXml) {
        if (!READ_OPERATIONS.contains(operation)) {
            throw new MonitoringException("Operation " + operation + " is not one of the " + READ_OPERATIONS.size()
                    + " read operations of the monitoring service; nothing was sent");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"" + ENV + "\" xmlns:op=\"" + OP + "\" xmlns:ws=\"" + WS
                + "\" xmlns:d=\"" + DATA + "\" xmlns:jl=\"" + JL + "\">"
                + "<soapenv:Body><op:" + operation + ">" + bodyXml + "</op:" + operation + "></soapenv:Body>"
                + "</soapenv:Envelope>";
    }

    /**
     * Appends {@code <prefix:name>value</prefix:name>}; nothing for a null value. A text with a character XML 1.0 does
     * not allow is a {@link MonitoringException} naming {@code name}.
     */
    static void element(StringBuilder out, String prefix, String name, Object value) {
        if (value != null) {
            text(out, prefix, name, name, value);
        }
    }

    /**
     * Appends a required but nillable element: {@code <prefix:name>value</prefix:name>}, or for a null value the
     * element with {@code xsi:nil="true"} (F-17 Appendix B, mark {@code N}).
     */
    static void nillable(StringBuilder out, String prefix, String name, Object value) {
        if (value == null) {
            nil(out, prefix, name);
        } else {
            text(out, prefix, name, name, value);
        }
    }

    /** Appends a {@code JL:ArrayOfString} element with one {@code jl:String} per item; nothing for a null list. */
    static void strings(StringBuilder out, String prefix, String name, List<String> items) {
        if (items != null) {
            array(out, prefix, name, items);
        }
    }

    /** Appends a required but nillable {@code JL:ArrayOfString}: a null list is the element with {@code xsi:nil}. */
    static void nillableStrings(StringBuilder out, String prefix, String name, List<String> items) {
        if (items == null) {
            nil(out, prefix, name);
        } else {
            array(out, prefix, name, items);
        }
    }

    private static void array(StringBuilder out, String prefix, String name, List<String> items) {
        out.append('<').append(prefix).append(':').append(name).append('>');
        for (String item : items) {
            if (item == null) {
                nil(out, "jl", "String");
            } else {
                text(out, "jl", "String", name, item);
            }
        }
        out.append("</").append(prefix).append(':').append(name).append('>');
    }

    private static void nil(StringBuilder out, String prefix, String name) {
        out.append('<').append(prefix).append(':').append(name).append(" xmlns:xsi=\"").append(XSI)
                .append("\" xsi:nil=\"true\"/>");
    }

    private static void text(StringBuilder out, String prefix, String name, String field, Object value) {
        String text = value instanceof OffsetDateTime t ? DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t)
                : value.toString();
        out.append('<').append(prefix).append(':').append(name).append('>').append(escape(field, text))
                .append("</").append(prefix).append(':').append(name).append('>');
    }

    /**
     * The text escaped for element content; a character outside the XML 1.0 {@code Char} production (a control
     * character other than tab, line feed and carriage return, an unpaired surrogate, U+FFFE, U+FFFF) is a
     * {@link MonitoringException} naming the request field, so the request is never sent.
     */
    static String escape(String field, String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                b.append(c).append(s.charAt(++i));
                continue;
            }
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r' || Character.isSurrogate(c) || c == 0xFFFE
                    || c == 0xFFFF) {
                throw new MonitoringException(String.format("Request field %s contains a character XML 1.0 does not"
                        + " allow (U+%04X at index %d); nothing was sent", field, (int) c, i));
            }
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '\r' -> b.append("&#13;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    // ------------------------------------------------------------------------------------------ response

    /**
     * The body of an answer decoded with the charset of its content type (UTF-8 if unknown); without a charset
     * parameter, with the encoding of the body's XML declaration (UTF-8 if there is none or it is unknown).
     */
    static String text(HttpResponse<byte[]> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        byte[] body = response.body();
        Charset charset = CHARSET_PARAM.matcher(contentType).find() ? PoHttp.charset(contentType) : declared(body);
        return new String(body, charset);
    }

    /** The encoding of the XML declaration that starts {@code body}, UTF-8 if there is none or it is unknown. */
    private static Charset declared(byte[] body) {
        Matcher m = XML_ENCODING.matcher(
                new String(body, 0, Math.min(body.length, DECLARATION), StandardCharsets.ISO_8859_1));
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (IllegalArgumentException e) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * The SOAP envelope of an answer of {@code operation}; every other answer is a {@link MonitoringException}:
     * 401, 3xx or a logon page (authentication failed), 403 (the monitoring role), a SOAP fault (fault string, code,
     * args), another HTTP status, or an HTTP 200 body that is not a SOAP envelope (unexpected response, with the first
     * 200 characters of the body).
     */
    static Document parse(HttpResponse<byte[]> response, String operation, PoSystem system) {
        int status = response.statusCode();
        String body = text(response);
        if (status == 401 || status / 100 == 3) {
            throw authenticationFailed(operation, system, status);
        }
        if (status == 403) {
            throw new MonitoringException(operation + ": authentication failed or not authorized for " + system
                    + " (HTTP 403; role SAP_XI_API_DISPLAY_J2EE needed for message monitoring): "
                    + PoHttp.errorText(body));
        }
        Document doc;
        String notSoap;
        try {
            doc = read(body);
            Element root = doc.getDocumentElement();
            notSoap = is(root, ENV, "Envelope") && child(root, ENV, "Body") != null ? null
                    : "root element {" + root.getNamespaceURI() + "}" + root.getLocalName() + " is not a SOAP 1.1 envelope";
        } catch (SAXException e) {
            doc = null;
            notSoap = "not well-formed or not allowed XML: " + e.getMessage();
        }
        if (notSoap == null) {
            Element fault = child(child(doc.getDocumentElement(), ENV, "Body"), ENV, "Fault");
            if (fault != null) {
                throw fault(operation, system, status, fault);
            }
            if (status == 200) {
                return doc;
            }
        }
        if (PoHttp.isLogonPage(body)) {
            throw authenticationFailed(operation, system, status);
        }
        if (status != 200) {
            throw new MonitoringException(operation + ": HTTP " + status + ": " + PoHttp.errorText(body));
        }
        throw new MonitoringException(operation + ": unexpected response from " + system.name() + " (HTTP 200, "
                + (notSoap == null ? "SOAP envelope" : notSoap) + "): "
                + body.substring(0, Math.min(EXCERPT, body.length())));
    }

    /**
     * The {@code OP:Response} element of {@code OP:<operation>Response} in the SOAP body of an envelope; an envelope
     * without it is an unexpected response.
     */
    static Element response(Document envelope, String operation, PoSystem system) {
        Element body = child(envelope.getDocumentElement(), ENV, "Body");
        Element wrapper = child(body, OP, operation + "Response");
        Element result = child(wrapper, OP, "Response");
        if (result == null) {
            throw new MonitoringException(operation + ": unexpected response from " + system.name()
                    + " (no " + operation + "Response/Response in the SOAP body)");
        }
        return result;
    }

    private static MonitoringException authenticationFailed(String operation, PoSystem system, int status) {
        return new MonitoringException(operation + ": authentication failed for " + system + " (HTTP " + status + ")");
    }

    /** SOAP 1.1 fault: faultcode, faultstring and the F-17 Appendix D detail (exception, code, args, messageKey). */
    private static MonitoringException fault(String operation, PoSystem system, int status, Element fault) {
        StringBuilder m = new StringBuilder(operation).append(": SOAP fault from ").append(system.name())
                .append(" (HTTP ").append(status).append(')');
        String code = anyChildText(fault, "faultcode");
        if (code != null) {
            m.append(' ').append(code.strip());
        }
        String text = anyChildText(fault, "faultstring");
        m.append(": ").append(text == null ? "(no fault string)" : text.strip());
        Element detail = anyChild(fault, "detail");
        for (Element d : detail == null ? List.<Element>of() : children(detail)) {
            String name = d.getLocalName();
            String exception = name.substring(name.lastIndexOf('.') + 1);
            m.append(" [").append(exception);
            String errorCode = childText(d, WS, "code");
            if (errorCode != null) {
                m.append(", code ").append(errorCode.strip());
            }
            Element args = child(d, WS, "args");
            if (args != null) {
                List<String> values = new ArrayList<>();
                for (Element a : children(args)) {
                    values.add(nil(a) ? "null" : a.getTextContent());
                }
                m.append(", args ").append(values);
            }
            String key = childText(d, WS, "messageKey");
            if (key != null) {
                m.append(", messageKey ").append(key);
            }
            m.append(']');
        }
        return new MonitoringException(m.toString());
    }

    private static Document read(String xml) throws SAXException {
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            DocumentBuilder b = f.newDocumentBuilder();
            b.setErrorHandler(new ErrorHandler() { // no "[Fatal Error]" on stderr: the exception carries it
                @Override
                public void warning(SAXParseException e) {
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return b.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser without secure processing: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------------------------------ DOM helpers

    /** Element children of a node in document order; empty for null. */
    static List<Element> children(Node parent) {
        List<Element> out = new ArrayList<>();
        if (parent == null) {
            return out;
        }
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                out.add(e);
            }
        }
        return out;
    }

    /** The first element child with this namespace and local name, or null (also for a null parent). */
    static Element child(Element parent, String namespace, String localName) {
        for (Element e : children(parent)) {
            if (is(e, namespace, localName)) {
                return e;
            }
        }
        return null;
    }

    /** Text of {@link #child}, null if the child is absent or nil; not trimmed. */
    static String childText(Element parent, String namespace, String localName) {
        Element c = child(parent, namespace, localName);
        return c == null || nil(c) ? null : c.getTextContent();
    }

    static boolean is(Element e, String namespace, String localName) {
        return namespace.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName());
    }

    /** True for {@code xsi:nil="true"} (or {@code "1"}). */
    static boolean nil(Element e) {
        String v = e.getAttributeNS(XSI, "nil").strip();
        return v.equals("true") || v.equals("1");
    }

    /** True when an element has element children. */
    static boolean complex(Element e) {
        return !children(e).isEmpty();
    }

    /**
     * Keeps a child in an {@code otherFields} map under its local name: {@link #NIL} if it is nil, its XML string if
     * it is complex, its text otherwise. The {@code occurrence}-th child of a name (counted from 1) is kept as
     * {@code name#occurrence} from the second on; a key already taken gets the next free number.
     */
    static void keep(Map<String, String> fields, Element e, int occurrence) {
        String name = e.getLocalName();
        String key = occurrence <= 1 ? name : name + "#" + occurrence;
        for (int n = Math.max(occurrence, 1) + 1; fields.containsKey(key); n++) {
            key = name + "#" + n;
        }
        fields.put(key, nil(e) ? NIL : complex(e) ? xml(e) : e.getTextContent());
    }

    /**
     * Keeps an element in an {@code otherFields} map under {@code key} ({@link #NIL} if it is nil, its XML string if
     * it is complex, its text otherwise); a key already taken becomes {@code key#2}, {@code key#3}, ...
     */
    static void keepAt(Map<String, String> fields, String key, Element e) {
        put(fields, key, nil(e) ? NIL : complex(e) ? xml(e) : e.getTextContent());
    }

    /** Puts {@code value} under {@code key}, or under {@code key#2}, {@code key#3}, ... when the key is taken. */
    static void put(Map<String, String> fields, String key, String value) {
        String k = key;
        for (int n = 2; fields.containsKey(k); n++) {
            k = key + "#" + n;
        }
        fields.put(k, value);
    }

    /** An element as an XML string without declaration (namespace declarations added as needed). */
    static String xml(Element e) {
        DOMImplementationLS ls = (DOMImplementationLS) e.getOwnerDocument().getImplementation();
        LSSerializer s = ls.createLSSerializer();
        s.getDomConfig().setParameter("xml-declaration", false);
        return s.writeToString(e);
    }

    /** SOAP 1.1 fault children ({@code faultcode}, ...) are unqualified; matched by local name in any namespace. */
    private static Element anyChild(Element parent, String localName) {
        for (Element e : children(parent)) {
            if (localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    private static String anyChildText(Element parent, String localName) {
        Element c = anyChild(parent, localName);
        return c == null ? null : c.getTextContent();
    }
}
