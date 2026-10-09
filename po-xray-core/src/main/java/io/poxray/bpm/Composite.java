package io.poxray.bpm;

import io.poxray.sca.XmlDocs;
import io.poxray.sca.ZipArchive;
import java.util.HashMap;
import java.util.Map;
import org.w3c.dom.Element;

/**
 * SCA references from the deployed {@code META-INF/*.composite} (in the BPM DC's SDA): per service reference UUID
 * the service group and authentication profile. Service group configuration itself is runtime (NWA).
 */
final class Composite {

    private static final String SAPCONFIG_NS = "http://www.sap.com/webas/2007/03/esoa/config/system";

    record Reference(String serviceGroup, String authenticationProfile) {
    }

    private Composite() {
    }

    static Map<String, Reference> read(ZipArchive sda) {
        Map<String, Reference> out = new HashMap<>();
        if (sda == null) {
            return out;
        }
        for (var e : sda.entries().entrySet()) {
            if (e.getKey().startsWith("META-INF/") && e.getKey().endsWith(".composite")) {
                Element root = XmlDocs.parse(e.getValue(), e.getKey()).getDocumentElement();
                for (Element component : XmlDocs.children(root, "component")) {
                    for (Element ref : XmlDocs.children(component, "reference")) {
                        out.put(ref.getAttribute("name"), new Reference(
                                emptyToNull(ref.getAttributeNS(SAPCONFIG_NS, "serviceGroup")),
                                emptyToNull(ref.getAttributeNS(SAPCONFIG_NS, "authenticationProfile"))));
                    }
                }
            }
        }
        return out;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
