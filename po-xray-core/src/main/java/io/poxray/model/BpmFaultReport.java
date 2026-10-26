package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.poxray.model.BpmPathReport.Condition;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.Flag;
import io.poxray.model.BpmPathReport.Input;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Endpoint;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Source-declared event contexts, without runtime handler or propagation inference. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BpmFaultReport(int formatVersion, String analysisVersion, List<Input> inputs,
        List<ProcessFaults> processes, List<Unattributed> unattributed, List<Issue> issues) {
    public BpmFaultReport {
        inputs = snapshot(inputs); processes = snapshot(processes);
        unattributed = snapshot(unattributed); issues = snapshot(issues);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProcessFaults(ProcessKey key, String dc, String id, String name, String source,
            String coverage, List<Declaration> declarations, List<String> reasons) {
        public ProcessFaults { declarations = snapshot(declarations); reasons = snapshot(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Declaration(int ordinal, String role, Endpoint owner, Endpoint event, Endpoint definition,
            List<Scope> scopes, List<Definition> definitions, Builtin builtin, List<Condition> conditions,
            Evidence evidence, String state, List<String> reasons) {
        public Declaration {
            scopes = snapshot(scopes); definitions = snapshot(definitions);
            conditions = snapshot(conditions); reasons = snapshot(reasons);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Scope(Locator owner, String state, String origin, List<Locator> path, List<Fact> basis,
            List<String> reasons) {
        public Scope { path = snapshot(path); basis = snapshot(basis); reasons = snapshot(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Definition(Element source, String kind, Flag critical, Flag technical,
            List<FaultReference> faults, Evidence evidence, List<String> reasons) {
        public Definition { faults = snapshot(faults); reasons = snapshot(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FaultReference(int ordinal, Link link, String state, Evidence evidence, List<String> reasons) {
        public FaultReference { reasons = snapshot(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Builtin(String id, String kind, String name, boolean critical, boolean technical,
            Parameter parameter, String file, String sourceDc, String resource) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Parameter(String name, String namespace, String type) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Evidence(List<Fact> path, List<Element> elements, List<Link> links) {
        public Evidence { path = snapshot(path); elements = snapshot(elements); links = snapshot(links); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Unattributed(int input, int component, String dc, String kind, Evidence evidence,
            List<String> reasons) {
        public Unattributed { reasons = snapshot(reasons); }
    }

    private static <T> List<T> snapshot(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
