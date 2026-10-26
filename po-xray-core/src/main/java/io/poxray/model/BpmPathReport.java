package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.poxray.model.BpmSourceEvidence.Locator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Structural paths in retained parsed evidence; neither execution nor whole-archive coverage. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BpmPathReport(int formatVersion, String analysisVersion, List<Input> inputs,
        List<ProcessPaths> processes, List<Issue> issues) {
    public BpmPathReport {
        inputs = snapshot(inputs);
        processes = snapshot(processes);
        issues = snapshot(issues);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Input(int ordinal, String label, String sha256) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProcessKey(int input, int component, int process) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Fact(String kind, Locator location) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Flag(boolean present, String value, String interpretation) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Endpoint(String id, String resolution, List<Locator> candidates) {
        public Endpoint { candidates = snapshot(candidates); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Condition(String state, String text, List<Fact> evidence, List<String> reasons) {
        public Condition {
            evidence = snapshot(evidence);
            reasons = snapshot(reasons);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Step(int ordinal, Locator location, String id, String kind, String name,
            List<Locator> scopes, String connectivity, List<Integer> witness, Flag terminate,
            List<String> reasons) {
        public Step {
            scopes = snapshot(scopes);
            witness = snapshot(witness);
            reasons = snapshot(reasons);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Arc(int ordinal, String kind, Locator location, String id, List<Endpoint> sources,
            List<Endpoint> targets, String resolution, Condition condition, Flag isDefault,
            List<Fact> evidence, List<String> reasons) {
        public Arc {
            sources = snapshot(sources);
            targets = snapshot(targets);
            evidence = snapshot(evidence);
            reasons = snapshot(reasons);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Loop(Locator location, String id, List<Locator> hosts, String mode, Flag sequential,
            String flowCondition, String completion, List<Fact> evidence, List<String> reasons) {
        public Loop {
            hosts = snapshot(hosts);
            evidence = snapshot(evidence);
            reasons = snapshot(reasons);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProcessPaths(ProcessKey key, String dc, String id, String name, String source,
            String coverage, List<String> reasons, List<Step> steps, List<Arc> arcs, List<Loop> loops) {
        public ProcessPaths {
            reasons = snapshot(reasons);
            steps = snapshot(steps);
            arcs = snapshot(arcs);
            loops = snapshot(loops);
        }
    }

    private static <T> List<T> snapshot(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
