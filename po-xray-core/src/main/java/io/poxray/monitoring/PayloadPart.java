package io.poxray.monitoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.IOException;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * One MIME part of a message payload: {@code index} from 0 in the order of the message, {@code contentId} without
 * the surrounding {@code <>}, {@code contentType} as declared (null if the part declares none), {@code size} the
 * number of content bytes, and exactly one of {@code text} (the content decoded in the declared charset) and
 * {@code base64} (the content bytes) set.
 */
public record PayloadPart(int index, String contentId, String contentType, long size, String text, String base64) {

    /** The SOAP 1.1 envelope namespace: the document element of an XI 3.0 message header part (F-18). */
    private static final String SOAP_ENV = "http://schemas.xmlsoap.org/soap/envelope/";

    public PayloadPart {
        if ((text == null) == (base64 == null)) {
            throw new IllegalArgumentException("exactly one of text and base64 must be set (part " + index + ")");
        }
    }

    /**
     * True when this is an XI message header part: a text part whose document element is the SOAP 1.1
     * {@code Envelope} (F-18). Parsed namespace-aware with DOCTYPE disallowed and external entities off; a base64
     * part, text that is not well-formed XML, a DOCTYPE or another document element is false. Never throws.
     */
    @JsonIgnore
    public boolean isXiHeader() {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            Element root = builder().parse(new InputSource(new StringReader(text))).getDocumentElement();
            return SOAP_ENV.equals(root.getNamespaceURI()) && "Envelope".equals(root.getLocalName());
        } catch (SAXException | IOException | ParserConfigurationException | RuntimeException e) {
            return false;
        }
    }

    private static DocumentBuilder builder() throws ParserConfigurationException {
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
        return b;
    }
}
