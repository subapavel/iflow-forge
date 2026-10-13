package io.poxray.integration;

import io.poxray.sca.XmlDocs;
import java.util.regex.Pattern;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Masking of secrets in what is read from the PO system. PO does not export channel passwords, but secrets also end
 * up in plain values (e.g. a client secret in a value mapping group, verified on xipod), so names that look like
 * secrets are masked wherever names and values are read. Table attributes ({@code <Value isTable="true">} with
 * {@code Row}/{@code Field}) carry positional values whose column names live in the adapter metadata, so fields of
 * authentication-related tables are masked completely.
 */
final class Secrets {

    static final String HIDDEN = "<hidden>";
    private static final Pattern SECRET = Pattern.compile(
            "(?i).*(password|passwd|pwd|secret|token|credential|privatekey|apikey|accesskey|passphrase).*");
    /** Names that merely configure how a secret is used (flags, sources, URLs): not secrets themselves. */
    private static final Pattern ABOUT_SECRET = Pattern.compile(
            "(?i).*(source|caching|cache|mode|type|url|flag|enabled|disabled|interval|period|timeout|xsrf"
                    + "|use[a-z0-9]*token).*");
    /** Table attributes whose rows may carry credentials (column names are not in the channel itself). */
    private static final Pattern SENSITIVE_TABLE =
            Pattern.compile("(?i).*(oauth|auth|credential|token|secret|key|param).*");

    private Secrets() {
    }

    static boolean isSecret(String name) {
        return name != null && SECRET.matcher(name).matches() && !ABOUT_SECRET.matcher(name).matches();
    }

    /** Whether the rows of a table attribute with this name should be masked completely. */
    static boolean isSensitiveTable(String name) {
        return name != null && SENSITIVE_TABLE.matcher(name).matches();
    }

    static String mask(String name, String value) {
        return isSecret(name) && isMaskable(value) ? HIDDEN : value;
    }

    /** Booleans are never secrets, whatever the name says. */
    private static boolean isMaskable(String value) {
        return value != null && !value.isEmpty() && !value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false");
    }

    /**
     * Masks, in place, text of elements whose name looks like a secret, attributes whose name does, the value
     * of name / value pairs ({@code <Name>clientSecret</Name><Value>…</Value>} and similar sibling conventions),
     * and every field of a sensitive table attribute.
     */
    static void mask(Element e) {
        if (e == null) {
            return;
        }
        NamedNodeMap attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            if (isSecret(a.getLocalName() == null ? a.getNodeName() : a.getLocalName()) && isMaskable(a.getNodeValue())) {
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
            if (isSecret(e.getLocalName()) && isMaskable(e.getTextContent().strip())) {
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
                if (valueElement && isSecret(pairName) && isMaskable(c.getTextContent().strip())) {
                    c.setTextContent(HIDDEN);
                } else if (valueElement && isSensitiveTable(pairName)) {
                    maskFields(c);
                } else {
                    mask(c);
                }
            }
        }
    }

    /** Masks every non-empty {@code Field} text under a sensitive table value. */
    private static void maskFields(Element value) {
        for (Element row : XmlDocs.children(value, "Row")) {
            for (Element field : XmlDocs.children(row, "Field")) {
                if (!field.getTextContent().isBlank()) {
                    field.setTextContent(HIDDEN);
                }
            }
        }
    }
}
