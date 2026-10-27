package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.poxray.model.ContractCatalogInput.Evidence;
import io.poxray.model.ContractCatalogInput.PortClaim;
import io.poxray.model.ContractCatalogInput.ValueClaim;
import java.util.List;
import static io.poxray.model.ContractCatalogInput.copy;

/** Source evidence and separate supplied claims; no runtime wiring or approval inference. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ContractCatalogReport(int formatVersion, String analysisVersion, List<Artifact> artifacts,
        List<Contract> contracts, List<Dependency> dependencies, BpmDataReport data, List<Issue> issues) {
    public ContractCatalogReport {
        artifacts = copy(artifacts); contracts = copy(contracts); dependencies = copy(dependencies); issues = copy(issues);
        // BpmDataReport and every nested P3 record already snapshot lists and source maps in constructors.
        // Sharing these immutable values preserves the exact P3 semantics, including null scalar unknowns.
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Artifact(String id, String kind, IntegrationInventory.ObjRef esr, String owner,
            String basis, List<Evidence> evidence) {
        public Artifact { evidence = copy(evidence); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Contract(String id, String artifact, String disposition, IntegrationInventory.ObjRef esr,
            IntegrationInventory.XmlName xmlName, List<PortClaim> ports, List<ValueClaim> values, List<Evidence> evidence) {
        public Contract { ports = copy(ports); values = copy(values); evidence = copy(evidence); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Dependency(String artifact, String kind, IntegrationInventory.ObjRef esr, String location,
            String namespace, List<String> candidates, String state, List<Evidence> evidence) {
        public Dependency { candidates = copy(candidates); evidence = copy(evidence); }
    }
}
