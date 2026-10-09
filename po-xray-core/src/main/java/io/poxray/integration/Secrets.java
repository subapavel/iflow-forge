package io.poxray.integration;

import io.poxray.sca.XmlDocs;
import java.util.regex.Pattern;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Masking of secrets in what is read from the PO system. PO does not export channel passwords, but secrets also end
 * up in plain values (e.g. a client secret in a value mapping group, verified on xipod), so names that look like
 * secrets are masked wherever names and values are read.
 */
final class Secrets {

    static final String HIDDEN = "<hidden>";
    private static final Pattern SECRET =
            Pattern.compile("(?i).*(password|passwd|pwd|secret|token|credential|privatekey|apikey).*");

    private Secrets() {
    }

    static boolean isSecret(String name) {
        return name != null && SECRET.matcher(name).matches();
    }

    static String mask(String name, String value) {
        return isSecret(name) && value != null && !value.isEmpty() ? HIDDEN : value;
    }

    /**
     * Masks, in place, text of elements whose name looks like a secret, attributes whose name does, and the value
     * of name / value pairs ({@code <Name>clientSecret</Name><Value>…</Value>} and similar sibling conventions).
     */
    static void mask(Element e) {
        if (e == null) {
            return;
        }
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (isSecret(a.getLocalName() == null ? a.getNodeName() : a.getLocalName()) && !a.getNodeValue().isEmpty()) {
                a.setNodeValue(HIDDEN);
            }
        }
        boolean leaf = true;
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element) {
                leaf = false;
                break;
            }
        }
        if (leaf) {
            if (isSecret(e.getLocalName()) && !e.getTextContent().isBlank()) {
                e.setTextContent(HIDDEN);
            }
            return;
        }
        String pairName = null;
        for (String nameElement : new String[] {"Name", "name", "ParamName", "objectTypeName", "key", "Key"}) {
            String t = XmlDocs.childText(e, nameElement);
            if (t != null) {
                pairName = t;
                break;
            }
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element c) {
                String local = c.getLocalName();
                boolean valueElement = "Value".equals(local) || "value".equals(local) || "ParamValue".equals(local);
                if (valueElement && isSecret(pairName) && !c.getTextContent().isBlank()) {
                    c.setTextContent(HIDDEN);
                } else {
                    mask(c);
                }
            }
        }
    }
}
