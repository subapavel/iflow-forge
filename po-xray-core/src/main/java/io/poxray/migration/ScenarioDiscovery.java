package io.poxray.migration;

import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.DiscoveryReport;
import io.poxray.model.DiscoveryReport.ComponentRow;
import io.poxray.model.DiscoveryReport.IcoRow;
import io.poxray.model.DiscoveryReport.InterfaceRow;
import io.poxray.model.DiscoveryReport.ProcessRow;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Endpoint;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.InterfaceAssignment;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.IntegrationInventory.Target;
import io.poxray.model.Interface;
import io.poxray.model.Issue;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;

/**
 * Derives the ICOs around the NW BPM processes of {@code po-xray export} outputs ({@link ScaInventory} JSON) from an
 * {@link IntegrationInventory} ({@code po-xray ico}): an inbound interface (usage role start or intermediate) matches
 * an ICO whose receiver target interface is that interface, an outbound interface (outbound or notification) matches
 * an ICO whose sender interface is that interface; name and namespace are compared exactly. The communication
 * components of the matched ICOs get the roles {@code process}, {@code source} and {@code target}.
 */
public final class ScenarioDiscovery {

    static final String NOT_ICO_INVENTORY = "NOT_ICO_INVENTORY";
    static final String INTERFACE_WITHOUT_ICO = "INTERFACE_WITHOUT_ICO";
    static final String PROCESS_WITHOUT_INTERFACES = "PROCESS_WITHOUT_INTERFACES";
    static final String UNKNOWN_INTERFACE_ROLE = "UNKNOWN_INTERFACE_ROLE";

    static final String ROLE_PROCESS = "process";
    static final String ROLE_SOURCE = "source";
    static final String ROLE_TARGET = "target";

    private static final String NONE = "—";

    private ScenarioDiscovery() {
    }

    /**
     * Exports as {@link BpmInventory#read(Path)} lists them: a directory gives every {@code *.json} directly in it,
     * sorted by name, a file gives that file; a {@code *.json} that is not an {@link ScaInventory} with {@code bpm} or
     * {@code ejb} is skipped with an issue {@code NOT_SCA_INVENTORY}. {@code icosJson} is the
     * {@link IntegrationInventory} JSON {@code po-xray ico} writes (null = none); a file that cannot be read or parsed,
     * or has no {@code icos}, is an issue {@code NOT_ICO_INVENTORY} and every interface is then without an ICO.
     */
    public static DiscoveryReport read(Path exportDirOrFile, Path icosJson) throws IOException {
        if (!Files.exists(exportDirOrFile)) {
            throw new NoSuchFileException(exportDirOrFile.toString(), null, "export directory or file not found");
        }
        List<Path> files;
        if (Files.isDirectory(exportDirOrFile)) {
            try (Stream<Path> s = Files.list(exportDirOrFile)) {
                files = s.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
                        .toList();
            }
        } else {
            files = List.of(exportDirOrFile);
        }
        List<ScaInventory> inventories = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        for (Path file : files) {
            String label = file.toString();
            byte[] bytes = Files.readAllBytes(file);
            ScaInventory inv;
            try {
                inv = Json.mapper().readValue(bytes, ScaInventory.class);
            } catch (JacksonException e) {
                issues.add(notScaInventory(label, Reports.firstLine(e.getOriginalMessage())));
                continue;
            }
            if (inv == null) {
                issues.add(notScaInventory(label, "empty document"));
                continue;
            }
            if (inv.bpm() == null && inv.ejb() == null) {
                issues.add(notScaInventory(label, "neither bpm nor ejb"));
                continue;
            }
            inventories.add(inv);
            labels.add(label);
        }
        IntegrationInventory icos = null;
        String icosLabel = null;
        if (icosJson != null) {
            String label = icosJson.toString();
            try {
                IntegrationInventory read = Reports.readJson(icosJson, IntegrationInventory.class);
                if (read.icos() == null) {
                    issues.add(notIcoInventory(label, "no icos"));
                } else {
                    icos = read;
                    icosLabel = label;
                }
            } catch (IOException e) {
                issues.add(notIcoInventory(label, Reports.firstLine(e.getMessage())));
            }
        }
        return report(inventories, labels, icos, icosLabel, issues);
    }

    /**
     * In-memory: one label per export (its file name in the process rows); {@code icos} null = no ICO inventory,
     * every interface is then without an ICO.
     */
    public static DiscoveryReport of(List<ScaInventory> exports, List<String> exportLabels, IntegrationInventory icos,
            String icosLabel) {
        return report(exports, exportLabels, icos, icosLabel, List.of());
    }

    private static Issue notScaInventory(String label, String reason) {
        return new Issue(Issue.WARNING, BpmInventory.NOT_SCA_INVENTORY,
                label + " is not an SCA inventory with bpm or ejb (" + reason + "); skipped", null, label);
    }

    private static Issue notIcoInventory(String label, String reason) {
        return new Issue(Issue.WARNING, NOT_ICO_INVENTORY, label + " is not an ICO inventory with icos (" + reason
                + "); every interface is without an ICO", null, label);
    }

    private static DiscoveryReport report(List<ScaInventory> exports, List<String> labels, IntegrationInventory icos,
            String icosLabel, List<Issue> readIssues) {
        if (exports.size() != labels.size()) {
            throw new IllegalArgumentException(exports.size() + " exports but " + labels.size() + " labels");
        }
        List<Ico> icoList = icos == null ? List.of()
                : Reports.list(icos.icos()).stream().filter(Objects::nonNull).toList();
        List<Issue> issues = new ArrayList<>(readIssues);
        Map<String, TreeSet<String>> roles = new TreeMap<>();
        List<ProcessRow> processes = new ArrayList<>();
        for (int i = 0; i < exports.size(); i++) {
            ScaInventory inv = exports.get(i);
            String file = labels.get(i);
            if (inv == null) {
                continue;
            }
            for (BpmComponent b : Reports.list(inv.bpm())) {
                if (b == null) {
                    continue;
                }
                List<Interface> interfaces = b.catalog() == null ? List.of() : Reports.list(b.catalog().interfaces());
                for (Process p : Reports.list(b.processes())) {
                    if (p != null) {
                        processes.add(processRow(file, b.dc(), p, interfaces, icoList, roles, issues));
                    }
                }
            }
        }
        List<ComponentRow> components = new ArrayList<>();
        roles.forEach((name, r) -> components.add(new ComponentRow(name, List.copyOf(r))));
        return new DiscoveryReport(Reports.SCHEMA_VERSION, Reports.generator(), List.copyOf(labels), icosLabel,
                List.copyOf(processes), List.copyOf(components), List.copyOf(issues));
    }

    private static ProcessRow processRow(String file, String dc, Process p, List<Interface> interfaces,
            List<Ico> icos, Map<String, TreeSet<String>> roles, List<Issue> issues) {
        String title = title(p.name(), p.id());
        Issue.Location where = new Issue.Location(dc, title, null, null);
        TreeSet<String> own = new TreeSet<>();
        List<InterfaceRow> inbound = new ArrayList<>();
        List<InterfaceRow> outbound = new ArrayList<>();
        for (Interface iface : interfaces) {
            if (iface == null) {
                continue;
            }
            // Interface.Usage.process holds the process name (ProcessReader.use), not Process.id.
            Set<String> usageRoles = new LinkedHashSet<>();
            for (Interface.Usage u : Reports.list(iface.usedBy())) {
                if (u != null && p.name() != null && p.name().equals(u.process())) {
                    usageRoles.add(u.role());
                }
            }
            for (String role : usageRoles) {
                boolean in = "start".equals(role) || "intermediate".equals(role);
                boolean out = "outbound".equals(role) || "notification".equals(role);
                if (!in && !out) {
                    issues.add(new Issue(Issue.WARNING, UNKNOWN_INTERFACE_ROLE, "process " + title + " (dc " + dc
                            + "): interface " + iface.name() + " {" + iface.namespace() + "} has usage role " + role
                            + " (known: start, intermediate, outbound, notification); not listed", where, file));
                    continue;
                }
                List<IcoRow> matches = new ArrayList<>();
                for (Ico ico : icos) {
                    if (in ? inboundMatch(ico, iface, roles, own) : outboundMatch(ico, iface, roles, own)) {
                        matches.add(icoRow(ico));
                    }
                }
                matches.sort(Comparator.comparing(IcoRow::oid, Comparator.nullsLast(Comparator.naturalOrder())));
                if (matches.isEmpty()) {
                    issues.add(new Issue(Issue.WARNING, INTERFACE_WITHOUT_ICO, "process " + title + " (dc " + dc
                            + "): no ICO for " + role + " interface " + iface.name() + " {" + iface.namespace() + "}",
                            where, file));
                }
                InterfaceRow row = new InterfaceRow(iface.name(), iface.namespace(), role, List.copyOf(matches));
                (in ? inbound : outbound).add(row);
            }
        }
        if (inbound.isEmpty() && outbound.isEmpty()) {
            issues.add(new Issue(Issue.WARNING, PROCESS_WITHOUT_INTERFACES, "process " + title + " (dc " + dc
                    + "): no catalog interface used in a start, intermediate, outbound or notification role", where,
                    file));
        }
        return new ProcessRow(file, dc, p.id(), p.name(), List.copyOf(own), List.copyOf(inbound),
                List.copyOf(outbound));
    }

    /** Some receiver target interface equals {@code iface}: that receiver is a process component, the sender a source. */
    private static boolean inboundMatch(Ico ico, Interface iface, Map<String, TreeSet<String>> roles,
            Set<String> own) {
        boolean matched = false;
        for (Receiver r : Reports.list(ico.receivers())) {
            if (r != null && targets(r, iface)) {
                matched = true;
                if (r.component() != null) {
                    own.add(r.component());
                    role(roles, r.component(), ROLE_PROCESS);
                }
            }
        }
        if (matched && ico.sender() != null) {
            role(roles, ico.sender().component(), ROLE_SOURCE);
        }
        return matched;
    }

    private static boolean targets(Receiver r, Interface iface) {
        for (InterfaceAssignment a : Reports.list(r.interfaces())) {
            if (a == null) {
                continue;
            }
            for (Target t : Reports.list(a.targets())) {
                if (t != null && same(t.iface(), iface)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The sender interface equals {@code iface}: the sender is a process component, every receiver a target. */
    private static boolean outboundMatch(Ico ico, Interface iface, Map<String, TreeSet<String>> roles,
            Set<String> own) {
        Endpoint sender = ico.sender();
        if (sender == null || !same(sender.iface(), iface)) {
            return false;
        }
        if (sender.component() != null) {
            own.add(sender.component());
            role(roles, sender.component(), ROLE_PROCESS);
        }
        for (Receiver r : Reports.list(ico.receivers())) {
            if (r != null) {
                role(roles, r.component(), ROLE_TARGET);
            }
        }
        return true;
    }

    /** Exact, case-sensitive name and namespace; a null reference or a null name matches nothing. */
    private static boolean same(ObjRef ref, Interface iface) {
        return ref != null && ref.name() != null && ref.name().equals(iface.name())
                && Objects.equals(ref.namespace(), iface.namespace());
    }

    private static void role(Map<String, TreeSet<String>> roles, String component, String role) {
        if (component != null) {
            roles.computeIfAbsent(component, c -> new TreeSet<>()).add(role);
        }
    }

    private static IcoRow icoRow(Ico ico) {
        Endpoint s = ico.sender();
        ObjRef si = s == null ? null : s.iface();
        TreeSet<String> receivers = new TreeSet<>();
        for (Receiver r : Reports.list(ico.receivers())) {
            if (r != null && r.component() != null) {
                receivers.add(r.component());
            }
        }
        return new IcoRow(ico.oid(), s == null ? null : s.party(), s == null ? null : s.component(),
                si == null ? null : si.name(), si == null ? null : si.namespace(), List.copyOf(receivers));
    }

    private static String title(String name, String id) {
        return name != null && !name.isBlank() ? name : id;
    }

    // ---------------------------------------------------------------- Markdown

    public static String markdown(DiscoveryReport report) {
        StringBuilder md = new StringBuilder("# Scenario discovery\n\n");
        md.append("Exports: ").append(String.join(", ", Reports.list(report.inputs()))).append("; ICO inventory: ")
                .append(report.icosInput() == null ? "none" : report.icosInput()).append("\n\n## Processes\n");
        List<ProcessRow> processes = Reports.list(report.processes());
        if (processes.isEmpty()) {
            md.append("\nNo process.\n");
        }
        for (ProcessRow r : processes) {
            md.append("\n### ").append(title(r.name(), r.id())).append("\n\n");
            List<String> own = Reports.list(r.processComponents());
            md.append("dc ").append(r.dc()).append(" in ").append(r.file()).append("; process components: ")
                    .append(own.isEmpty() ? "none" : String.join(", ", own)).append("\n\n");
            List<InterfaceRow> rows = new ArrayList<>(Reports.list(r.inbound()));
            rows.addAll(Reports.list(r.outbound()));
            if (rows.isEmpty()) {
                md.append("No interface.\n");
                continue;
            }
            md.append("| Role | Interface | Namespace | ICO | Sender | Receivers |\n|---|---|---|---|---|---|\n");
            for (InterfaceRow i : rows) {
                String head = "| " + Reports.cell(i.role()) + " | " + Reports.cell(i.name()) + " | "
                        + Reports.cell(i.namespace()) + " | ";
                List<IcoRow> icos = Reports.list(i.icos());
                if (icos.isEmpty()) {
                    md.append(head).append(NONE).append(" | ").append(NONE).append(" | ").append(NONE).append(" |\n");
                }
                for (IcoRow ico : icos) {
                    md.append(head).append(Reports.cell(ico.oid())).append(" | ").append(Reports.cell(sender(ico)))
                            .append(" | ").append(Reports.cell(String.join(", ", Reports.list(ico.receiverComponents()))))
                            .append(" |\n");
                }
            }
        }
        md.append("\n## Components\n\n");
        List<ComponentRow> components = Reports.list(report.components());
        if (components.isEmpty()) {
            md.append("No component.\n");
        } else {
            md.append("| Component | Roles |\n|---|---|\n");
            for (ComponentRow c : components) {
                md.append("| ").append(Reports.cell(c.name())).append(" | ")
                        .append(Reports.cell(String.join(", ", Reports.list(c.roles())))).append(" |\n");
            }
        }
        md.append("\n## Issues\n\n");
        List<Issue> issues = Reports.list(report.issues());
        if (issues.isEmpty()) {
            md.append("none\n");
        } else {
            md.append("| Code | Message |\n|---|---|\n");
            for (Issue i : issues) {
                md.append("| ").append(Reports.cell(i.code())).append(" | ").append(Reports.cell(i.message()))
                        .append(" |\n");
            }
        }
        return md.toString();
    }

    private static String sender(IcoRow ico) {
        if (ico.senderParty() == null || ico.senderParty().isBlank()) {
            return ico.senderComponent();
        }
        return ico.senderParty() + " / " + ico.senderComponent();
    }
}
