package io.poxray.integration;

import io.poxray.integration.ObjectCatalog.Hit;
import io.poxray.model.BpmComponent;
import io.poxray.model.EsrOperationMapping;
import io.poxray.model.Interface;
import io.poxray.model.IntegrationInventory.ObjectSummary;
import io.poxray.model.Process;
import io.poxray.model.QNameRef;
import io.poxray.model.ServiceReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cross-reference of NW BPM processes (SCA) with the ESR / Integration Directory of a PO system: for every service
 * interface a process uses, the matching ESR service interfaces and the objects referencing them (ICOs, receiver /
 * interface determinations, agreements, operation mappings), plus the users of the ESR operation mapping called
 * through the service reference of an automated activity.
 */
public final class BpmIntegration {

    private BpmIntegration() {
    }

    /** The integration of one process; {@code dc} is the Process Composer development component. */
    public record ProcessIntegration(String process, String dc, List<InterfaceIntegration> interfaces) {
    }

    /**
     * One service interface the process uses. {@code bindingType}, {@code serviceGroup} and
     * {@code operationMapping} come from the service reference calling the interface (outbound only);
     * {@code esr} are the matching ESR service interfaces (empty = not in the ESR, e.g. a local WSDL);
     * {@code usedBy} the objects referencing the interface and {@code operationMappingUsedBy} the objects
     * referencing the called operation mapping.
     */
    public record InterfaceIntegration(
            String name,
            String namespace,
            List<Use> uses,
            String bindingType,
            String serviceGroup,
            EsrOperationMapping operationMapping,
            List<ObjectSummary> esr,
            List<ObjectSummary> usedBy,
            List<ObjectSummary> operationMappingUsedBy) {
    }

    /** A node of the process using the interface; {@code role}: start | intermediate | outbound | notification. */
    public record Use(String node, String role) {
    }

    /**
     * The integration of the processes named {@code process} (name or id, case-insensitive; empty = all processes).
     * The PO system is asked once per object type (listings are kept by the catalog for the session).
     */
    public static List<ProcessIntegration> report(List<BpmComponent> bpm, String process, ObjectCatalog catalog) {
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
        return List.copyOf(out);
    }

    private static ProcessIntegration processIntegration(BpmComponent b, Process p, ObjectCatalog catalog,
            Map<String, List<Hit>> found) {
        List<InterfaceIntegration> interfaces = new ArrayList<>();
        for (Interface i : b.catalog() == null ? List.<Interface>of() : b.catalog().interfaces()) {
            List<Use> uses = i.usedBy() == null ? List.of() : i.usedBy().stream()
                    .filter(u -> u.process() != null && u.process().equals(p.name()))
                    .map(u -> new Use(u.node() == null || u.node().name() == null
                            ? (u.node() == null ? null : u.node().ref()) : u.node().name(), u.role()))
                    .toList();
            if (uses.isEmpty()) {
                continue;
            }
            ServiceReference sr = serviceReference(b, i);
            EsrOperationMapping om = sr == null ? null : sr.operationMapping();
            List<Hit> hits = find(catalog, "serviceInterface", i.name(), i.namespace(), found);
            List<Hit> omHits = om == null ? List.of()
                    : byOid(find(catalog, "operationMapping", om.name(), om.namespace(), found), om.oid());
            interfaces.add(new InterfaceIntegration(i.name(), i.namespace(), uses,
                    sr == null ? null : sr.bindingType(), sr == null ? null : sr.serviceGroup(), om,
                    hits.stream().map(Hit::summary).toList(),
                    hits.isEmpty() ? List.of() : catalog.whereUsed(hits),
                    omHits.isEmpty() ? List.of() : catalog.whereUsed(omHits)));
        }
        return new ProcessIntegration(p.name(), b.dc(), interfaces);
    }

    /** The service reference calling the interface (match by qualified interface name). */
    private static ServiceReference serviceReference(BpmComponent b, Interface i) {
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
        return found.computeIfAbsent(key.toLowerCase(Locale.ROOT), k -> catalog.find(type, name, namespace));
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
