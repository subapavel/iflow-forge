package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.ProcessKey;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Endpoint;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Contextual source declarations and structural provenance, never runtime values. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BpmDataReport(int formatVersion, String analysisVersion, BpmPathReport paths,
        List<ProcessData> processes, List<Issue> issues) {
    public BpmDataReport { processes = copy(processes); issues = copy(issues); }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProcessData(ProcessKey key, String dc, String id, String name, String source, String coverage,
            List<String> reasons, List<ObjectData> objects, List<MappingUse> mappings,
            List<Declaration> declarations, List<Read> reads, List<StepState> steps) {
        public ProcessData {
            reasons = copy(reasons); objects = copy(objects); mappings = copy(mappings);
            declarations = copy(declarations); reads = copy(reads); steps = copy(steps);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ObjectData(int ordinal, Element source, List<Link> memberships, List<Locator> scopePath,
            List<Link> types, List<Element> typeCandidates, String scopeState, List<String> reasons) {
        public ObjectData {
            memberships = copy(memberships); scopePath = copy(scopePath); types = copy(types);
            typeCandidates = copy(typeCandidates); reasons = copy(reasons);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record MappingUse(int ordinal, Integer parent, Integer parentDeclaration, Link ownership,
            Endpoint mapping, Locator rootOwner, String role, String readDomain, String writeDomain,
            Evidence evidence, List<String> reasons) {
        public MappingUse { reasons = copy(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Evidence(List<Fact> path, List<Element> elements, List<Link> links) {
        public Evidence { path = copy(path); elements = copy(elements); links = copy(links); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Name(List<Link> links, List<Element> candidates, String state) {
        public Name { links = copy(links); candidates = copy(candidates); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Binding(String state, List<Integer> objects, List<String> reasons) {
        public Binding { objects = copy(objects); reasons = copy(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Declaration(int ordinal, int mapping, Endpoint part, List<Name> targetPath,
            Evidence evidence, Binding target, String valueKind, String candidacy, List<String> reasons) {
        public Declaration { targetPath = copy(targetPath); reasons = copy(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Read(int ordinal, int declaration, String domain, Evidence evidence, Binding binding,
            List<OriginSet> candidates, List<String> reasons) {
        public Read { candidates = copy(candidates); reasons = copy(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record OriginSet(int object, List<Integer> declarations, String coverage, List<String> reasons) {
        public OriginSet { declarations = copy(declarations); reasons = copy(reasons); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StepState(int step, Locator location, String connectivity, List<OriginSet> before,
            List<OriginSet> after, List<String> reasons) {
        public StepState { before = copy(before); after = copy(after); reasons = copy(reasons); }
    }

    private static <T> List<T> copy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }
}
