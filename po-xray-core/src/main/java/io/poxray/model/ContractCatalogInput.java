package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Attributed design claims; provenance structure is not an approval or authenticity check. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ContractCatalogInput(List<ArtifactClaim> artifacts, List<ContractClaim> contracts,
        List<ResourceBinding> resources) {
    public ContractCatalogInput { artifacts = copy(artifacts); contracts = copy(contracts); resources = copy(resources); }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Evidence(String kind, String source, String locator, String status) { }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StepRef(BpmPathReport.ProcessKey process, int step) { }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ArtifactClaim(String id, String kind, String owner, List<Evidence> evidence) {
        public ArtifactClaim { evidence = copy(evidence); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PortClaim(String role, String participant, String operation, StepRef step, List<Evidence> evidence) {
        public PortClaim { evidence = copy(evidence); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ValueClaim(String path, String role, StepRef producer, List<StepRef> consumers,
            String defaultValue, List<Evidence> evidence) {
        public ValueClaim { consumers = copy(consumers); evidence = copy(evidence); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ContractClaim(String id, String artifact, String disposition, IntegrationInventory.ObjRef esr,
            IntegrationInventory.XmlName xmlName, List<PortClaim> ports, List<ValueClaim> values, List<Evidence> evidence) {
        public ContractClaim { ports = copy(ports); values = copy(values); evidence = copy(evidence); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ResourceBinding(String id, String ownerArtifact, IntegrationInventory.ContractResource resource,
            List<Evidence> evidence) {
        public ResourceBinding { evidence = copy(evidence); }
    }
    // All component records (including ObjRef and ContractResource) snapshot their own collections.
    static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
