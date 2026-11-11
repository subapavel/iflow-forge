package io.poxray.monitoring;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

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
     * {@code Envelope} (F-18). Only the start of the text is read, up to the first start tag (see
     * {@link #startsWithSoapEnvelope(Reader)}): what follows the root start tag is not checked, so a truncated
     * envelope still counts. A base64 or blank part is false. Never throws.
     */
    @JsonIgnore
    public boolean isXiHeader() {
        if (text == null || text.isBlank()) {
            return false;
        }
        return startsWithSoapEnvelope(new StringReader(text));
    }

    /**
     * True when the first start element read from {@code source} is {@code Envelope} in the SOAP 1.1 namespace.
     * Read with StAX, namespace-aware, and stopped at that first start element. A DOCTYPE before it, a malformed
     * prolog, an unbound prefix on the root or text that is not XML is false; DTDs and external entities are off,
     * nothing is fetched. Never throws; {@code source} is closed.
     */
    static boolean startsWithSoapEnvelope(Reader source) {
        XMLStreamReader xml = null;
        try {
            xml = factory().createXMLStreamReader(source);
            while (xml.hasNext()) {
                int event = xml.next();
                if (event == XMLStreamConstants.DTD) {
                    return false;
                }
                if (event == XMLStreamConstants.START_ELEMENT) {
                    return SOAP_ENV.equals(xml.getNamespaceURI()) && "Envelope".equals(xml.getLocalName());
                }
            }
            return false;
        } catch (XMLStreamException | RuntimeException e) {
            return false;
        } finally {
            close(xml, source);
        }
    }

    /** The JDK's own StAX factory (not a provider found on the class path), with DTDs and external access off. */
    private static XMLInputFactory factory() {
        XMLInputFactory f = XMLInputFactory.newDefaultFactory();
        f.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return f;
    }

    /** Closes both: {@link XMLStreamReader#close()} does not close the underlying reader. */
    private static void close(XMLStreamReader xml, Reader source) {
        if (xml != null) {
            try {
                xml.close();
            } catch (XMLStreamException | RuntimeException e) {
                // nothing to report: the answer is already decided
            }
        }
        try {
            source.close();
        } catch (IOException | RuntimeException e) {
            // nothing to report: the answer is already decided
        }
    }
}
