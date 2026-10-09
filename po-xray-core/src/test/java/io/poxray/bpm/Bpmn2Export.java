package io.poxray.bpm;

import io.poxray.sca.XmlDocs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Test oracle: BPMN 2.0 export from NWDS. Elements carry {@code bpmn2_1:BPMelementID} = Galaxy MOF ID,
 * so nodes and flows pair 1:1 with the reader output.
 */
final class Bpmn2Export {

    private static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String SAP = "http://www.sap.com/bpm/bpmn2";

    record Edge(String source, String target) {
    }

    /** MOF ID → name of every element with a BPMelementID. */
    final Map<String, String> names = new LinkedHashMap<>();
    /** Sequence flows by MOF ID of the flow. */
    final Map<String, Edge> flows = new LinkedHashMap<>();
    /** Condition text by MOF ID of the flow. */
    final Map<String, String> conditions = new LinkedHashMap<>();

    static Bpmn2Export read(Path file) {
        try {
            return new Bpmn2Export(Files.readAllBytes(file), file.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Bpmn2Export(byte[] content, String name) {
        Element root = XmlDocs.parse(content, name).getDocumentElement();
        Map<String, String> xmlIdToMof = new HashMap<>();
        NodeList ids = root.getElementsByTagNameNS(SAP, "BPMelementID");
        for (int i = 0; i < ids.getLength(); i++) {
            Element ext = (Element) ids.item(i).getParentNode();
            Element owner = (Element) ext.getParentNode();
            String mof = ids.item(i).getTextContent().trim();
            xmlIdToMof.put(owner.getAttribute("id"), mof);
            names.put(mof, owner.getAttribute("name"));
        }
        NodeList sfs = root.getElementsByTagNameNS(BPMN, "sequenceFlow");
        for (int i = 0; i < sfs.getLength(); i++) {
            Element sf = (Element) sfs.item(i);
            String mof = xmlIdToMof.get(sf.getAttribute("id"));
            flows.put(mof, new Edge(xmlIdToMof.get(sf.getAttribute("sourceRef")),
                    xmlIdToMof.get(sf.getAttribute("targetRef"))));
            Element cond = XmlDocs.child(sf, "conditionExpression");
            if (cond != null) {
                conditions.put(mof, cond.getTextContent());
            }
        }
    }

    Set<Edge> edges() {
        return new LinkedHashSet<>(flows.values());
    }

    /** MOF IDs of all flow nodes (ends of sequence flows). */
    Set<String> flowNodes() {
        Set<String> out = new LinkedHashSet<>();
        for (Edge e : flows.values()) {
            out.add(e.source());
            out.add(e.target());
        }
        return out;
    }
}
