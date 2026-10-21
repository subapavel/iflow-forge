package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Output of the scenario discovery ({@code io.poxray.migration.ScenarioDiscovery}): the ICOs around the NW BPM
 * processes of {@code po-xray export} outputs ({@link ScaInventory} JSON files), matched against an
 * {@link IntegrationInventory} ({@code po-xray ico}). {@code inputs} are the export files read as inventories,
 * {@code icosInput} the label of the ICO inventory (null when none). {@code issues} is always written (an empty
 * array means none were found). See docs/OUTPUT-MODEL.md, section "Migration helpers".
 */
public record DiscoveryReport(
        String schemaVersion,
        ScaInventory.Generator generator,
        List<String> inputs,
        String icosInput,
        List<ProcessRow> processes,
        List<ComponentRow> components,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Issue> issues) {

    public DiscoveryReport {
        inputs = orEmpty(inputs);
        processes = orEmpty(processes);
        components = orEmpty(components);
        issues = orEmpty(issues);
    }

    /**
     * One process of {@code bpm[b].processes[p]}: its inbound interfaces (usage roles start, intermediate) and
     * outbound interfaces (outbound, notification), each with its matching ICOs. {@code processComponents}: the
     * communication components identified as the process's own (receiver of an inbound ICO, sender of an outbound
     * ICO), distinct and sorted.
     */
    public record ProcessRow(String file, String dc, String id, String name, List<String> processComponents,
            List<InterfaceRow> inbound, List<InterfaceRow> outbound) {

        public ProcessRow {
            processComponents = orEmpty(processComponents);
            inbound = orEmpty(inbound);
            outbound = orEmpty(outbound);
        }
    }

    /**
     * A catalog interface used by the process in one usage {@code role}; {@code icos} sorted by oid, empty when no
     * ICO matches.
     */
    public record InterfaceRow(String name, String namespace, String role, List<IcoRow> icos) {

        public InterfaceRow {
            icos = orEmpty(icos);
        }
    }

    /** One matching ICO: its sender key and its receiver components (distinct, sorted). */
    public record IcoRow(String oid, String senderParty, String senderComponent, String senderInterface,
            String senderNamespace, List<String> receiverComponents) {

        public IcoRow {
            receiverComponents = orEmpty(receiverComponents);
        }
    }

    /** A communication component of a matched ICO with its roles ⊆ {process, source, target}, sorted. */
    public record ComponentRow(String name, List<String> roles) {

        public ComponentRow {
            roles = orEmpty(roles);
        }
    }

    private static <T> List<T> orEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
