package io.poxray.xi;

import io.poxray.sca.XmlDocs;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

/** Parser of the {@code p1:xiObj} envelope (namespace {@value #XI_NS}). */
public final class XiObjectParser {

    public static final String XI_NS = "urn:sap-com:xi";

    private XiObjectParser() {
    }

    public static XiObject parse(String xml, String name) {
        Element root = XmlDocs.parse(xml.getBytes(StandardCharsets.UTF_8), name).getDocumentElement();
        if (!"xiObj".equals(root.getLocalName())) {
            throw new IllegalArgumentException("Not an xiObj (root <" + root.getLocalName() + ">): " + name);
        }
        return object(root, null, xml);
    }

    public static XiObject parse(byte[] xml, String name) {
        return parse(new String(xml, StandardCharsets.UTF_8), name);
    }

    private static XiObject object(Element obj, XiRef owner, String raw) {
        Element idInfo = XmlDocs.child(obj, "idInfo");
        XiRef id = ref(idInfo, owner);
        Element generic = XmlDocs.child(obj, "generic");
        Element adm = generic == null ? null : XmlDocs.child(generic, "admInf");

        List<XiLink> links = new ArrayList<>();
        Element lnks = generic == null ? null : XmlDocs.child(generic, "lnks");
        if (lnks != null) {
            for (Element role : XmlDocs.children(lnks, "lnkRole")) {
                int kpos = parseInt(role.getAttribute("kpos"));
                for (Element lnk : XmlDocs.children(role, "lnk")) {
                    links.add(new XiLink(role.getAttribute("role"), kpos, emptyToNull(lnk.getAttribute("rMode")),
                            ref(lnk, id)));
                }
            }
        }

        String description = null;
        Map<String, String> texts = new LinkedHashMap<>();
        Element textInfo = generic == null ? null : XmlDocs.child(generic, "textInfo");
        if (textInfo != null) {
            for (Element textObj : XmlDocs.children(textInfo, "textObj")) {
                String master = textObj.getAttribute("masterL");
                for (Element texts0 : XmlDocs.children(textObj, "texts")) {
                    if (!master.isEmpty() && !master.equals(texts0.getAttribute("lang"))) {
                        continue;
                    }
                    for (Element text : XmlDocs.children(texts0, "text")) {
                        String label = text.getAttribute("label");
                        String value = text.getTextContent();
                        if (label.isEmpty()) {
                            description = description == null ? emptyToNull(value) : description;
                        } else {
                            texts.putIfAbsent(label, value);
                        }
                    }
                }
            }
        }

        Element contentEl = XmlDocs.child(obj, "content");
        Element content = null;
        if (contentEl != null) {
            for (org.w3c.dom.Node n = contentEl.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element e) {
                    content = e;
                    break;
                }
            }
        }

        List<XiObject> inner = new ArrayList<>();
        Element innerEl = XmlDocs.child(obj, "inner");
        if (innerEl != null) {
            for (Element child : XmlDocs.children(innerEl, "xiObj")) {
                inner.add(object(child, id, null));
            }
        }

        return new XiObject(id,
                idInfo == null ? null : emptyToNull(idInfo.getAttribute("VID")),
                XmlDocs.childText(adm, "modifBy"),
                XmlDocs.childText(adm, "modifAt"),
                description, texts, List.copyOf(links), content, List.copyOf(inner), raw);
    }

    /** Reference from an element holding {@code vc} and {@code key}; missing vc is inherited from {@code owner}. */
    private static XiRef ref(Element holder, XiRef owner) {
        if (holder == null) {
            return null;
        }
        Element vc = XmlDocs.child(holder, "vc");
        Element key = XmlDocs.child(holder, "key");
        String vcType = vc != null ? emptyToNull(vc.getAttribute("vcType")) : owner == null ? null : owner.vcType();
        String swcGuid = vc != null ? emptyToNull(vc.getAttribute("swcGuid")) : owner == null ? null : owner.swcGuid();
        String caption = vc != null ? emptyToNull(vc.getAttribute("caption")) : owner == null ? null : owner.swcCaption();
        List<String> elems = new ArrayList<>();
        String typeId = null;
        String oid = null;
        if (key != null) {
            typeId = emptyToNull(key.getAttribute("typeID"));
            oid = emptyToNull(key.getAttribute("oid"));
            for (Element e : XmlDocs.children(key, "elem")) {
                elems.add(e.getTextContent());
            }
        }
        return new XiRef(vcType, swcGuid == null ? null : normalizeGuid(swcGuid), caption, typeId, oid, List.copyOf(elems));
    }

    /** SWCV GUIDs appear with and without dashes (Directory WS API vs. ESR); normalised to lower case, no dashes. */
    public static String normalizeGuid(String guid) {
        return guid.replace("-", "").toLowerCase(java.util.Locale.ROOT);
    }

    private static int parseInt(String s) {
        try {
            return s == null || s.isEmpty() ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
