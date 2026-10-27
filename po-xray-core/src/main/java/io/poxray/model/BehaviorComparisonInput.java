package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.json.JsonMapper;
import static io.poxray.model.ContractCatalogInput.copy;

/** Explicit, attributed alignment choices over immutable source reports. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BehaviorComparisonInput(int formatVersion, List<Implementation> implementations, List<Alignment> alignments) {
    public BehaviorComparisonInput { implementations = copy(implementations); alignments = copy(alignments); }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Implementation(String id, String label,
            @JsonSerialize(using=SnapshotSerializer.class) BehaviorReport behavior,
            @JsonSerialize(using=SnapshotSerializer.class) ContractCatalogReport contracts) { }
    /** Comparison envelopes preserve empty source lists too; the standalone legacy JSON stays unchanged. */
    public static final class SnapshotSerializer extends ValueSerializer<Object> {
        private static final JsonMapper MAPPER=JsonMapper.builder()
                .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .changeDefaultPropertyInclusion(i->i.withValueInclusion(JsonInclude.Include.ALWAYS)).build();
        @Override public void serialize(Object value, JsonGenerator generator, SerializationContext context) {
            context.writeTree(generator,MAPPER.valueToTree(value));
        }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StepRef(String implementation, BpmPathReport.ProcessKey process, int step) { }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Alignment(String id, String label, String status, List<StepRef> steps, List<Evidence> evidence) {
        public Alignment { steps = copy(steps); evidence = copy(evidence); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Evidence(String kind, String source, String locator, String status) { }
}
