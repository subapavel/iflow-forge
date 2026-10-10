package io.poxray.bpm;

import io.poxray.model.EsrOperationMapping;
import io.poxray.model.QNameRef;
import io.poxray.sca.XmlDocs;
import io.poxray.sca.ZipArchive;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * SCA composite of the deployed BPM DC ({@code META-INF/*.composite} in its SDA): per service reference UUID the
 * service group and authentication profile (service group configuration itself is runtime, NWA), and the PI
 * operation mappings the process calls: component with {@code implementation.piom}, one service per operation
 * mapping with {@code sapextensions omguid} (object ID in the ESR) and {@code svguid} (SWCV), keyed by the service
 * interface (port type) the process uses to call it. Verified on the PPAS_OTE archive BPM.
 */
final class Composite {

    private static final String SAPCONFIG_NS = "http://www.sap.com/webas/2007/03/esoa/config/system";
    /** {@code namespace#wsdl.interface(name)} */
    private static final Pattern WSDL_INTERFACE = Pattern.compile("^(.*)#wsdl\\.interface\\((.*)\\)$");

    record Reference(String serviceGroup, String authenticationProfile) {
    }

    /** {@code references}: by service reference UUID; {@code operationMappings}: by called interface {@code {ns}name}. */
    record Content(Map<String, Reference> references, Map<String, EsrOperationMapping> operationMappings) {
    }

    private Composite() {
    }

    static Content read(ZipArchive sda) {
        Map<String, Reference> references = new HashMap<>();
        Map<String, EsrOperationMapping> mappings = new HashMap<>();
        if (sda == null) {
            return new Content(references, mappings);
        }
        for (var e : sda.entries().entrySet()) {
            if (e.getKey().startsWith("META-INF/") && e.getKey().endsWith(".composite")) {
                Element root = XmlDocs.parse(e.getValue(), e.getKey()).getDocumentElement();
                for (Element component : XmlDocs.children(root, "component")) {
                    for (Element ref : XmlDocs.children(component, "reference")) {
                        references.put(ref.getAttribute("name"), new Reference(
                                emptyToNull(ref.getAttributeNS(SAPCONFIG_NS, "serviceGroup")),
                                emptyToNull(ref.getAttributeNS(SAPCONFIG_NS, "authenticationProfile"))));
                    }
                    if (XmlDocs.child(component, "implementation.piom") != null) {
                        for (Element service : XmlDocs.children(component, "service")) {
                            operationMapping(service, mappings);
                        }
                    }
                }
            }
        }
        return new Content(references, mappings);
    }

    private static void operationMapping(Element service, Map<String, EsrOperationMapping> out) {
        Element iface = XmlDocs.child(service, "interface.wsdl");
        Element ext = XmlDocs.child(service, "sapextensions");
        Matcher m = iface == null ? null : WSDL_INTERFACE.matcher(iface.getAttribute("interface"));
        if (m == null || !m.matches() || ext == null) {
            return;
        }
        String namespace = m.group(1);
        // the WSDL of the generated port type is named like the operation mapping: …/<mapping>.wsdl
        String location = iface.getAttributeNS(iface.lookupNamespaceURI("sapinterfacewsdlloc"), "location");
        if (location == null || location.isEmpty()) {
            location = attributeEndingWith(iface, "location");
        }
        String file = location == null ? "" : location.substring(location.lastIndexOf('/') + 1);
        String name = file.endsWith(".wsdl") ? file.substring(0, file.length() - 5) : null;
        out.put("{" + namespace + "}" + m.group(2), new EsrOperationMapping(name, namespace,
                emptyToNull(ext.getAttribute("omguid")), emptyToNull(ext.getAttribute("svguid")),
                type(XmlDocs.child(ext, "input")), type(XmlDocs.child(ext, "output"))));
    }

    private static QNameRef type(Element holder) {
        Element t = holder == null ? null : XmlDocs.child(holder, "type");
        return t == null ? null : new QNameRef(t.getAttribute("name"), emptyToNull(t.getAttribute("namespace")));
    }

    private static String attributeEndingWith(Element e, String localName) {
        var attrs = e.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            var a = attrs.item(i);
            String n = a.getLocalName() == null ? a.getNodeName() : a.getLocalName();
            if (n.equals(localName)) {
                return a.getNodeValue();
            }
        }
        return null;
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
