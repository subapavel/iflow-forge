package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import static io.poxray.model.ContractCatalogInput.copy;

/** Side-by-side evidence, never a semantic equivalence verdict. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BehaviorComparisonReport(int formatVersion, String analysisVersion,
        List<BehaviorComparisonInput.Implementation> implementations, List<Row> rows, List<Context> contexts, List<Issue> issues) {
    public BehaviorComparisonReport { implementations=copy(implementations); rows=copy(rows); contexts=copy(contexts); issues=copy(issues); }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String id, String alignment, String label, String status, List<BehaviorComparisonInput.Evidence> evidence, List<Cell> cells) {
        public Row { evidence=copy(evidence); cells=copy(cells); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Cell(int implementation, BehaviorComparisonInput.StepRef step, String state, List<Facet> facets, List<String> reasons) {
        public Cell { facets=copy(facets); reasons=copy(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Facet(String kind, List<String> pointers, String state, List<String> reasons) {
        public Facet { pointers=copy(pointers); reasons=copy(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Context(int implementation, List<Facet> facets, List<String> reasons) {
        public Context { facets=copy(facets); reasons=copy(reasons); }
    }
}
