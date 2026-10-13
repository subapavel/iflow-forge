package io.poxray.integration;

import io.poxray.integration.ObjectCatalog.Hit;
import io.poxray.model.BpmComponent;
import io.poxray.model.EsrOperationMapping;
import io.poxray.model.Interface;
import io.poxray.model.IntegrationInventory.ObjectSummary;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.QNameRef;
import io.poxray.model.ServiceReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cross-reference of NW BPM processes (SCA) with the ESR / Integration Directory of a PO system: for every service
 * interface a process uses, the matching ESR service interfaces and the objects referencing them (ICOs, receiver /
 * interface determinations, agreements, operation mappings), plus the users of the ESR operation mapping called
 * through the service reference of an automated activity. The deployed BPM often runs under another namespace or
 * component name than the SCA sources say (service groups are configured at deployment), so interfaces not found by
 * name + namespace are also matched by name alone, with a note.
 */
public final class BpmIntegration {

    private BpmIntegration() {
    }

    /** The whole report: one entry per process, notes of the object source (offline snapshot / TPZ exports). */
    public record Report(List<ProcessIntegration> processes, List<String> warnings) {
    }

    /** The integration of one process; {@code dc} is the Process Composer development component. */
    public record ProcessIntegration(String process, String dc, List<InterfaceIntegration> interfaces) {
    }

    /**
     * One service interface the process uses. {@code bindingType}, {@code serviceGroup} and
     * {@code operationMapping} come from the service reference the using node calls (outbound only);
     * {@code esr} are the matching ESR service interfaces and {@code note} says how they were matched
     * (empty = by name + namespace) or that nothing matches; {@code usedBy} the objects referencing the
     * interface and {@code operationMappingUsedBy} the objects referencing the called operation mapping.
     */
    public record InterfaceIntegration(
            String name,
            String namespace,
            List<Use> uses,
            String bindingType,
            String serviceGroup,
            EsrOperationMapping operationMapping,
            List<ObjectSummary> esr,
            String note,
            List<ObjectSummary> usedBy,
            List<ObjectSummary> operationMappingUsedBy) {
    }

    /** A node of the process using the interface; {@code role}: start | intermediate | outbound | notification. */
    public record Use(String node, String id, String role) {
    }

    /**
     * The integration of the processes named {@code process} (name or id, case-insensitive; empty = all processes).
     * The PO system is asked once per object type (listings are kept by the catalog for the session).
     */
    public static Report report(List<BpmComponent> bpm, String process, ObjectCatalog catalog) {
        String filter = process == null ? "" : process.strip().toLowerCase(Locale.ROOT);
        Map<String, List<Hit>> found = new HashMap<>();
        List<ProcessIntegration> out = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (BpmComponent b : bpm) {
            for (Process p : b.processes()) {
                names.add(p.name());
                if (filter.isEmpty() || eq(p.name(), filter) || eq(p.id(), filter)) {
                    out.add(processIntegration(b, p, catalog, found));
                }
            }
        }
        if (out.isEmpty()) {
            throw new IllegalArgumentException(names.isEmpty() ? "No BPM processes in the SCA"
                    : "No BPM process named '" + process + "'; processes: " + String.join(", ", names));
        }
        return new Report(List.copyOf(out), catalog.notes());
    }

    private static ProcessIntegration processIntegration(BpmComponent b, Process p, ObjectCatalog catalog,
            Map<String, List<Hit>> found) {
        Map<String, Node> nodes = new HashMap<>();
        for (Node n : p.nodes()) {
            nodes.put(n.id(), n);
        }
        List<InterfaceIntegration> interfaces = new ArrayList<>();
        for (Interface i : b.catalog() == null ? List.<Interface>of() : b.catalog().interfaces()) {
            List<Interface.Usage> usages = i.usedBy() == null ? List.of() : i.usedBy().stream()
                    .filter(u -> u.process() != null && u.process().equals(p.name()))
                    .toList();
            if (usages.isEmpty()) {
                continue;
            }
            List<Use> uses = usages.stream()
                    .map(u -> new Use(u.node() == null ? null : u.node().name(),
                            u.node() == null ? null : u.node().ref(), u.role()))
                    .toList();
            ServiceReference sr = serviceReference(b, i, usages, nodes);
            EsrOperationMapping om = sr == null ? null : sr.operationMapping();
            StringBuilder note = new StringBuilder();
            List<Hit> hits = find(catalog, "serviceInterface", i.name(), i.namespace(), found);
            if (hits.isEmpty()) {
                // deployment configuration (service groups) often points to another namespace than the SCA WSDL
                hits = find(catalog, "serviceInterface", i.name(), null, found);
                note.append(hits.isEmpty()
                        ? "Not found in the ESR, neither by name + namespace nor by name alone"
                        : "Matched by name only: the ESR namespace differs from the BPM WSDL ("
                                + i.namespace() + ")");
            }
            List<Hit> omHits = om == null ? List.of()
                    : byOid(find(catalog, "operationMapping", om.name(), om.namespace(), found), om.oid());
            if (om != null && omHits.isEmpty()) {
                note.append(note.isEmpty() ? "" : "; ").append("the called operation mapping ").append(om.name())
                        .append(" is not in the ESR");
            }
            interfaces.add(new InterfaceIntegration(i.name(), i.namespace(), uses,
                    sr == null ? null : sr.bindingType(), sr == null ? null : sr.serviceGroup(), om,
                    hits.stream().map(Hit::summary).toList(),
                    note.isEmpty() ? null : note.toString(),
                    hits.isEmpty() ? List.of() : catalog.whereUsed(hits),
                    omHits.isEmpty() ? List.of() : catalog.whereUsed(omHits)));
        }
        return new ProcessIntegration(p.name(), b.dc(), interfaces);
    }

    /**
     * The service reference the using nodes call (authoritative: a node's reference), falling back to the first
     * reference declared for the qualified interface name when the nodes carry none.
     */
    private static ServiceReference serviceReference(BpmComponent b, Interface i, List<Interface.Usage> usages,
            Map<String, Node> nodes) {
        Set<String> referenced = new LinkedHashSet<>();
        for (Interface.Usage u : usages) {
            Node node = u.node() == null ? null : nodes.get(u.node().ref());
            if (node != null && node.serviceReference() != null) {
                referenced.add(node.serviceReference().ref());
            }
        }
        for (ServiceReference sr : b.catalog().serviceReferences()) {
            if (referenced.contains(sr.id())) {
                return sr;
            }
        }
        for (ServiceReference sr : b.catalog().serviceReferences()) {
            QNameRef ref = sr.iface();
            if (ref != null && equalsIgnoreCase(ref.name(), i.name())
                    && equalsIgnoreCase(ref.namespace(), i.namespace())) {
                return sr;
            }
        }
        return null;
    }

    /** ESR matches, kept per (type, name, namespace) so shared interfaces are looked up once per report. */
    private static List<Hit> find(ObjectCatalog catalog, String type, String name, String namespace,
            Map<String, List<Hit>> found) {
        String key = type + "|" + name + "|" + (namespace == null ? "" : namespace);
        return found.computeIfAbsent(key.toLowerCase(Locale.ROOT),
                k -> catalog.find(type, name, namespace == null ? "" : namespace));
    }

    /** The hits with the ESR object id of the service reference, all hits when none carries it. */
    private static List<Hit> byOid(List<Hit> hits, String oid) {
        if (oid == null || oid.isBlank()) {
            return hits;
        }
        List<Hit> exact = hits.stream().filter(h -> oid.equalsIgnoreCase(h.ref().oid())).toList();
        return exact.isEmpty() ? hits : exact;
    }

    private static boolean eq(String value, String lowerName) {
        return value != null && value.toLowerCase(Locale.ROOT).equals(lowerName);
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        return a == null ? b == null : a.equalsIgnoreCase(b);
    }
}
