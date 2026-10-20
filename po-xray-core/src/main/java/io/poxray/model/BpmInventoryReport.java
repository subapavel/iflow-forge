package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Output of {@code po-xray bpm-inventory}: size of the NW BPM processes and EJB modules of {@code po-xray export}
 * outputs ({@link ScaInventory} JSON files). {@code inputs} are the files read as inventories; skipped files are
 * {@code NOT_SCA_INVENTORY} issues. Maps are sorted by key. {@code issues} is always written (an empty array
 * means none were found).
 */
public record BpmInventoryReport(
        String schemaVersion,
        ScaInventory.Generator generator,
        List<String> inputs,
        List<ProcessRow> processes,
        List<EjbRow> ejb,
        Totals totals,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Issue> issues) {

    /**
     * One process of {@code bpm[b].processes[p]}: node count per {@code kind}, nodes whose {@code metaclass} contains
     * "human" (case-insensitive), distinct metaclasses of {@code unknown} nodes.
     */
    public record ProcessRow(String file, String dc, String id, String name, String source,
            Map<String, Integer> steps, int stepsTotal, int humanTasks, List<String> unknownMetaclasses) {
    }

    /** One EJB module of {@code ejb[e]}: role → bean names (a bean with two roles is listed under both). */
    public record EjbRow(String file, String dc, Map<String, List<String>> beansByRole) {
    }

    /** Sums over all rows; {@code beansByRole} role → number of beans. */
    public record Totals(int processes, int steps, int humanTasks, Map<String, Integer> beansByRole) {
    }
}
