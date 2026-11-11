package io.poxray.monitoring;

import io.poxray.po.PoHttp;
import io.poxray.po.PoSystem;
import java.io.IOException;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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

    private Soap() {
    }

    // ------------------------------------------------------------------------------------------ request

    /**
     * The request envelope of an operation: {@code bodyXml} is the content of the operation element and may use the
     * prefixes {@code op} (OP), {@code ws} (WS), {@code d} (DATA) and {@code jl} (JL) declared on the envelope.
     */
    static String envelope(String operation, String bodyXml) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soapenv:Envelope xmlns:soapenv=\"" + ENV + "\" xmlns:op=\"" + OP + "\" xmlns:ws=\"" + WS
                + "\" xmlns:d=\"" + DATA + "\" xmlns:jl=\"" + JL + "\">"
                + "<soapenv:Body><op:" + operation + ">" + bodyXml + "</op:" + operation + "></soapenv:Body>"
                + "</soapenv:Envelope>";
    }

    /** Appends {@code <prefix:name>value</prefix:name>}; nothing for a null value. */
    static void element(StringBuilder out, String prefix, String name, Object value) {
        if (value == null) {
            return;
        }
        String text = value instanceof OffsetDateTime t ? DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t)
                : value.toString();
        out.append('<').append(prefix).append(':').append(name).append('>').append(escape(text))
                .append("</").append(prefix).append(':').append(name).append('>');
    }

    /** Appends a {@code JL:ArrayOfString} element with one {@code jl:String} per item; nothing for a null list. */
    static void strings(StringBuilder out, String prefix, String name, List<String> items) {
        if (items == null) {
            return;
        }
        out.append('<').append(prefix).append(':').append(name).append('>');
        for (String item : items) {
            element(out, "jl", "String", item);
        }
        out.append("</").append(prefix).append(':').append(name).append('>');
    }

    static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
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

    /** The body of an answer decoded with the charset of its content type (UTF-8 if absent or unknown). */
    static String text(HttpResponse<byte[]> response) {
        return new String(response.body(), PoHttp.charset(response.headers().firstValue("Content-Type").orElse("")));
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
