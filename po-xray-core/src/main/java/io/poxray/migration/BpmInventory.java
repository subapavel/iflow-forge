package io.poxray.migration;

import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmInventoryReport;
import io.poxray.model.BpmInventoryReport.EjbRow;
import io.poxray.model.BpmInventoryReport.ProcessRow;
import io.poxray.model.BpmInventoryReport.Totals;
import io.poxray.model.EjbModule;
import io.poxray.model.Issue;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import tools.jackson.core.JacksonException;

/**
 * Sizes the NW BPM processes and EJB modules of {@code po-xray export} outputs ({@link ScaInventory} JSON): steps per
 * node kind, human tasks by metaclass, metaclasses the SCA reader does not interpret, beans per role, totals.
 */
public final class BpmInventory {

    static final String NOT_SCA_INVENTORY = "NOT_SCA_INVENTORY";

    private BpmInventory() {
    }

    /**
     * A directory: every {@code *.json} directly in it, sorted by name; a file: that file. A {@code *.json} that is not
     * an {@link ScaInventory} with {@code bpm} or {@code ejb} is skipped with an issue {@code NOT_SCA_INVENTORY}.
     */
    public static BpmInventoryReport read(Path dirOrFile) throws IOException {
        if (!Files.exists(dirOrFile)) {
            throw new NoSuchFileException(dirOrFile.toString(), null, "export directory or file not found");
        }
        List<Path> files;
        if (Files.isDirectory(dirOrFile)) {
            try (Stream<Path> s = Files.list(dirOrFile)) {
                files = s.filter(Files::isRegularFile)
                        .filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted((a, b) -> a.getFileName().toString().compareTo(b.getFileName().toString()))
                        .toList();
            }
        } else {
            files = List.of(dirOrFile);
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
                issues.add(notInventory(label, Reports.firstLine(e.getOriginalMessage())));
                continue;
            }
            if (inv == null) {
                issues.add(notInventory(label, "empty document"));
                continue;
            }
            if (inv.bpm() == null && inv.ejb() == null) {
                issues.add(notInventory(label, "neither bpm nor ejb"));
                continue;
            }
            inventories.add(inv);
            labels.add(label);
        }
        return report(inventories, labels, issues);
    }

    /** In-memory: one label per inventory (its file name in the report rows). */
    public static BpmInventoryReport of(List<ScaInventory> inventories, List<String> labels) {
        return report(inventories, labels, List.of());
    }

    private static Issue notInventory(String label, String reason) {
        return new Issue(Issue.WARNING, NOT_SCA_INVENTORY,
                label + " is not an SCA inventory with bpm or ejb (" + reason + "); skipped", null, label);
    }

    private static BpmInventoryReport report(List<ScaInventory> inventories, List<String> labels, List<Issue> issues) {
        if (inventories.size() != labels.size()) {
            throw new IllegalArgumentException(inventories.size() + " inventories but " + labels.size() + " labels");
        }
        List<ProcessRow> processes = new ArrayList<>();
        List<EjbRow> ejb = new ArrayList<>();
        for (int i = 0; i < inventories.size(); i++) {
            ScaInventory inv = inventories.get(i);
            String file = labels.get(i);
            for (BpmComponent b : Reports.list(inv.bpm())) {
                for (Process p : Reports.list(b.processes())) {
                    processes.add(processRow(file, b.dc(), p));
                }
            }
            for (EjbModule e : Reports.list(inv.ejb())) {
                ejb.add(ejbRow(file, e));
            }
        }
        Map<String, Integer> beansByRole = new TreeMap<>();
        for (EjbRow row : ejb) {
            row.beansByRole().forEach((role, beans) -> beansByRole.merge(role, beans.size(), Integer::sum));
        }
        Totals totals = new Totals(processes.size(), processes.stream().mapToInt(ProcessRow::stepsTotal).sum(),
                processes.stream().mapToInt(ProcessRow::humanTasks).sum(), Collections.unmodifiableMap(beansByRole));
        return new BpmInventoryReport(Reports.SCHEMA_VERSION, Reports.generator(), List.copyOf(labels),
                List.copyOf(processes), List.copyOf(ejb), totals, List.copyOf(issues));
    }

    private static ProcessRow processRow(String file, String dc, Process p) {
        Map<String, Integer> steps = new TreeMap<>();
        TreeSet<String> unknown = new TreeSet<>();
        int human = 0;
        List<Node> nodes = Reports.list(p.nodes());
        for (Node n : nodes) {
            steps.merge(String.valueOf(n.kind()), 1, Integer::sum);
            if (n.metaclass() != null && n.metaclass().toLowerCase(Locale.ROOT).contains("human")) {
                human++;
            }
            if ("unknown".equals(n.kind()) && n.metaclass() != null) {
                unknown.add(n.metaclass());
            }
        }
        return new ProcessRow(file, dc, p.id(), p.name(), p.source(), Collections.unmodifiableMap(steps),
                nodes.size(), human, List.copyOf(unknown));
    }

    private static EjbRow ejbRow(String file, EjbModule e) {
        Map<String, TreeSet<String>> byRole = new TreeMap<>();
        for (EjbModule.Bean bean : Reports.list(e.beans())) {
            for (String role : Reports.list(bean.roles())) {
                byRole.computeIfAbsent(role, r -> new TreeSet<>()).add(bean.name());
            }
        }
        Map<String, List<String>> beansByRole = new TreeMap<>();
        byRole.forEach((role, beans) -> beansByRole.put(role, List.copyOf(beans)));
        return new EjbRow(file, e.dc(), Collections.unmodifiableMap(beansByRole));
    }

    // ---------------------------------------------------------------- Markdown

    public static String markdown(BpmInventoryReport report) {
        StringBuilder md = new StringBuilder("# BPM inventory\n\n## Processes\n\n");
        List<ProcessRow> processes = Reports.list(report.processes());
        if (processes.isEmpty()) {
            md.append("No process.\n");
        } else {
            md.append("| dc | process | steps | human tasks | unknown metaclasses |\n|---|---|---|---|---|\n");
            for (ProcessRow r : processes) {
                md.append("| ").append(Reports.cell(r.dc())).append(" | ").append(Reports.cell(title(r)))
                        .append(" | ").append(r.stepsTotal()).append(" | ").append(r.humanTasks()).append(" | ")
                        .append(Reports.cell(String.join(", ", Reports.list(r.unknownMetaclasses())))).append(" |\n");
            }
            for (ProcessRow r : processes) {
                md.append("\n### ").append(title(r)).append("\n\n");
                if (r.source() != null) {
                    md.append(r.file()).append(" → ").append(r.source()).append("\n\n");
                }
                md.append("| kind | count |\n|---|---|\n");
                Reports.map(r.steps()).forEach((kind, count) -> md.append("| ").append(Reports.cell(kind)).append(" | ")
                        .append(count).append(" |\n"));
            }
        }
        md.append("\n## EJB roles\n\n");
        List<EjbRow> ejb = Reports.list(report.ejb());
        if (ejb.isEmpty()) {
            md.append("No EJB module.\n");
        } else {
            md.append("| dc | role | beans |\n|---|---|---|\n");
            for (EjbRow r : ejb) {
                Reports.map(r.beansByRole()).forEach((role, beans) -> md.append("| ").append(Reports.cell(r.dc()))
                        .append(" | ").append(Reports.cell(role)).append(" | ")
                        .append(Reports.cell(String.join(", ", beans)))
                        .append(" |\n"));
            }
        }
        Totals t = report.totals();
        md.append("\n## Totals\n\n").append(t.processes()).append(" process(es), ").append(t.steps())
                .append(" step(s), ").append(t.humanTasks()).append(" human task(s)");
        Map<String, Integer> roles = Reports.map(t.beansByRole());
        if (!roles.isEmpty()) {
            md.append("; beans by role: ");
            List<String> parts = new ArrayList<>();
            roles.forEach((role, n) -> parts.add(role + " " + n));
            md.append(String.join(", ", parts));
        }
        md.append('\n');
        Reports.issues(md, report.issues());
        return md.toString();
    }

    private static String title(ProcessRow r) {
        return r.name() != null && !r.name().isBlank() ? r.name() : r.id();
    }
}
