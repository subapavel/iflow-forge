package io.poxray.bpm;

import io.poxray.model.BpmDataReport.*;
import io.poxray.model.BpmPathReport.Arc;
import io.poxray.model.BpmPathReport.Step;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Locator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Two finite monotone unions: declared producer IDs and independent uncertainty reasons. */
final class BpmDataFlow {
    private final BpmDataContext c;
    private final Map<Locator, State> states = new LinkedHashMap<>();

    private static final class Cell {
        final Set<Integer> ids = new TreeSet<>();
        final Set<String> reasons = new LinkedHashSet<>();
        boolean merge(Cell other) {
            boolean changed = ids.addAll(other.ids);
            return reasons.addAll(other.reasons) || changed;
        }
    }

    private static final class State {
        final Step step;
        final Map<Integer, Cell> before = new LinkedHashMap<>();
        final Map<Integer, Cell> after = new LinkedHashMap<>();
        final Set<String> beforeUnknown = new LinkedHashSet<>();
        final Set<String> afterUnknown = new LinkedHashSet<>();
        final Set<String> reasons = new LinkedHashSet<>();
        State(Step step) { this.step = step; }
        boolean connected() { return step.connectivity().equals("connected"); }
    }

    BpmDataFlow(BpmDataContext context) { c = context; }

    List<StepState> read() {
        identifyGlobalEffects();
        boolean objectVisibilityUnknown = c.objects.stream().anyMatch(o -> o.scopeState().equals("unknown"));
        for (Step step : c.paths.steps()) {
            State state = new State(step); states.put(step.location(), state);
            state.beforeUnknown.addAll(c.global);
            for (ObjectData object : c.visible(step.location())) {
                Cell before = new Cell(); before.reasons.addAll(c.global);
                state.before.put(object.ordinal(), before); state.after.put(object.ordinal(), new Cell());
            }
            if (step.connectivity().equals("disconnected")) state.reasons.add("not-reached-in-modeled-graph");
            else if (!state.connected()) { state.reasons.add("step-connectivity-unknown"); c.reasons.add("step-connectivity-unknown"); }
            if (objectVisibilityUnknown && !step.connectivity().equals("disconnected")) {
                state.reasons.add("object-visibility-unknown"); c.reasons.add("object-visibility-unknown");
            }
            if (!c.scopeState(step.location()).equals("active")) {
                state.reasons.add("step-visibility-unknown"); c.reasons.add("step-visibility-unknown");
            }
            state.reasons.addAll(c.global);
        }
        seedInitials();
        boolean changed;
        do {
            changed = false;
            for (State state : states.values()) {
                if (!state.connected()) continue;
                for (Cell cell : state.before.values()) changed |= cell.reasons.addAll(state.beforeUnknown);
                changed |= state.afterUnknown.addAll(state.beforeUnknown);
                for (var entry : state.before.entrySet()) changed |= state.after.get(entry.getKey()).merge(entry.getValue());
                changed |= generate(state);
                for (Cell cell : state.after.values()) changed |= cell.reasons.addAll(state.afterUnknown);
                changed |= state.reasons.addAll(state.beforeUnknown);
                changed |= state.reasons.addAll(state.afterUnknown);
            }
            for (Arc arc : c.paths.arcs()) {
                if (!arc.resolution().equals("resolved")) continue;
                State from = states.get(arc.sources().getFirst().candidates().getFirst());
                State to = states.get(arc.targets().getFirst().candidates().getFirst());
                if (from == null || to == null || !from.connected() || !to.connected()) continue;
                changed |= to.beforeUnknown.addAll(from.afterUnknown);
                for (var entry : to.before.entrySet()) {
                    Cell produced = from.after.get(entry.getKey());
                    if (produced != null) changed |= entry.getValue().merge(produced);
                    if (arc.kind().equals("boundary-attachment")) {
                        Cell earlier = from.before.get(entry.getKey());
                        if (earlier != null) changed |= entry.getValue().merge(earlier);
                        changed |= entry.getValue().reasons.add("boundary-timing-unknown");
                    }
                    if (arc.kind().equals("sequence") && from.step.kind().equals("embeddedSubprocess"))
                        changed |= entry.getValue().reasons.add("child-effects-unmodeled");
                }
                if (arc.kind().equals("boundary-attachment")) changed |= to.beforeUnknown.add("boundary-timing-unknown");
                if (arc.kind().equals("sequence") && from.step.kind().equals("embeddedSubprocess"))
                    changed |= to.beforeUnknown.add("child-effects-unmodeled");
                changed |= to.reasons.addAll(from.reasons);
            }
        } while (changed);
        List<StepState> result = new ArrayList<>();
        for (State state : states.values()) {
            List<OriginSet> before = snapshot(state.before, state);
            List<OriginSet> after = snapshot(state.after, state);
            before.stream().filter(o -> o.coverage().equals("incomplete")).forEach(o -> c.reasons.addAll(o.reasons()));
            after.stream().filter(o -> o.coverage().equals("incomplete")).forEach(o -> c.reasons.addAll(o.reasons()));
            result.add(new StepState(state.step.ordinal(), state.step.location(), state.step.connectivity(), before, after, List.copyOf(state.reasons)));
        }
        resolveReads();
        return result;
    }

    private void identifyGlobalEffects() {
        for (MappingUse use : c.mappings) {
            if (BpmDataMappings.external(use.writeDomain())) continue;
            if (use.rootOwner() == null || c.scopeState(use.rootOwner()).equals("unknown")) {
                c.global.add("unknown-mapping-owner"); c.reasons.add("unknown-mapping-owner");
            }
        }
    }

    private void seedInitials() {
        for (MappingUse use : c.mappings) {
            if (!use.role().equals("initial") || use.rootOwner() == null || !c.scopeState(use.rootOwner()).equals("active")) continue;
            List<Locator> ownerPath = c.scopePath(use.rootOwner());
            if (ownerPath.isEmpty()) continue;
            boolean hasEntry = false;
            for (State state : states.values()) {
                if (!state.step.kind().equals("startEvent")
                        || state.step.scopes().size() != 1 || !state.step.scopes().getFirst().equals(ownerPath.getFirst())) continue;
                hasEntry = true;
                if (state.connected()) applyUse(use, state.before, state.beforeUnknown);
            }
            if (!hasEntry) {
                c.reasons.add("initial-entry-unknown");
                for (ObjectData object : c.objects) {
                    if (!object.source().location().equals(use.rootOwner())) continue;
                    for (State state : states.values()) {
                        Cell cell = state.before.get(object.ordinal());
                        if (cell != null) {
                            cell.reasons.add("initial-entry-unknown"); state.reasons.add("initial-entry-unknown");
                        }
                    }
                }
            }
        }
    }

    private boolean generate(State state) {
        boolean changed = false;
        if (state.step.kind().equals("unknown")) changed |= state.afterUnknown.add("opaque-write");
        for (MappingUse use : c.mappings) {
            if (!use.role().equals("initial") && state.step.location().equals(use.rootOwner())) changed |= applyUse(use, state.after, state.afterUnknown);
        }
        return changed;
    }

    private boolean applyUse(MappingUse use, Map<Integer, Cell> cells, Set<String> broad) {
        if (BpmDataMappings.external(use.writeDomain())) return false;
        boolean changed = false;
        List<Declaration> declarations = c.declarations.stream().filter(d -> d.mapping() == use.ordinal()).toList();
        if (declarations.isEmpty()) {
            if (use.role().equals("initial")) {
                for (ObjectData object : c.objects) {
                    if (object.source().location().equals(use.rootOwner()) && cells.containsKey(object.ordinal()))
                        changed |= cells.get(object.ordinal()).reasons.add("opaque-write");
                }
            } else changed |= broad.add("opaque-write");
            return changed;
        }
        for (Declaration declaration : declarations) {
            if (declaration.candidacy().equals("disabled")) continue;
            boolean unknownTarget = !(declaration.target().state().equals("owned") || declaration.target().state().equals("lexical-candidates"));
            if (unknownTarget) {
                changed |= broad.addAll(declaration.reasons()); changed |= broad.addAll(use.reasons());
                if (declaration.candidacy().equals("unknown") || use.writeDomain().equals("unknown")) changed |= broad.add("opaque-write");
            }
            List<Integer> targets = unknownTarget ? new ArrayList<>(cells.keySet()) : declaration.target().objects();
            for (int object : targets) {
                Cell cell = cells.get(object); if (cell == null) continue;
                if (declaration.candidacy().equals("candidate")) changed |= cell.ids.add(declaration.ordinal());
                if (declaration.candidacy().equals("unknown") || use.writeDomain().equals("unknown")) changed |= cell.reasons.add("opaque-write");
                changed |= cell.reasons.addAll(declaration.reasons());
                changed |= cell.reasons.addAll(use.reasons());
            }
        }
        return changed;
    }

    private List<OriginSet> snapshot(Map<Integer, Cell> cells, State state) {
        List<OriginSet> result = new ArrayList<>();
        for (var entry : cells.entrySet()) {
            Cell cell = entry.getValue();
            if (state.step.connectivity().equals("disconnected")) result.add(new OriginSet(entry.getKey(), List.of(), "not-applicable", List.of("not-reached-in-modeled-graph")));
            else {
                Set<String> why = new LinkedHashSet<>(cell.reasons);
                why.addAll(c.global);
                if (!state.connected()) why.add("step-connectivity-unknown");
                result.add(new OriginSet(entry.getKey(), List.copyOf(cell.ids), why.isEmpty() ? "complete" : "incomplete", List.copyOf(why)));
            }
        }
        return result;
    }

    private void resolveReads() {
        for (int i = 0; i < c.reads.size(); i++) {
            Read read = c.reads.get(i);
            Declaration declaration = c.declarations.get(read.declaration() - 1);
            MappingUse use = c.mappings.get(declaration.mapping() - 1);
            State state = states.get(use.rootOwner());
            List<OriginSet> origins = new ArrayList<>();
            Set<Integer> enclosing = new HashSet<>(); enclosing.add(declaration.ordinal());
            MappingUse cursor = use;
            while (cursor.parent() != null) {
                if (cursor.parentDeclaration() != null) enclosing.add(cursor.parentDeclaration());
                cursor = c.mappings.get(cursor.parent() - 1);
            }
            for (int object : read.binding().objects()) {
                Cell result = new Cell(); result.reasons.addAll(read.reasons());
                if (state != null && state.before.containsKey(object)) result.merge(state.before.get(object));
                else result.reasons.add("read-step-or-visibility-unknown");
                if (!read.binding().state().equals("lexical-candidates")) result.reasons.add("read-binding-unknown");
                if (cursor.role().equals("initial")) result.reasons.add("initializer-order-unknown");
                if (state != null && state.connected() && read.domain().equals("process-context")) {
                    Element owner = c.elements.get(use.rootOwner());
                    if (owner != null && Galaxy.MAPPING_ACTIVITY.equals(owner.metaclass())) {
                        for (Declaration other : c.declarations) {
                            MappingUse otherUse = c.mappings.get(other.mapping() - 1);
                            if (other.candidacy().equals("candidate") && !enclosing.contains(other.ordinal())
                                    && use.rootOwner().equals(otherUse.rootOwner()) && other.target().objects().contains(object)) {
                                result.ids.add(other.ordinal()); result.reasons.add("intra-step-order-unknown");
                                result.reasons.addAll(other.reasons()); result.reasons.addAll(otherUse.reasons());
                            }
                        }
                    }
                }
                String coverage = result.reasons.isEmpty() ? "complete" : "incomplete";
                if (state != null && state.step.connectivity().equals("disconnected")) {
                    result.ids.clear(); coverage = "not-applicable"; result.reasons.add("not-reached-in-modeled-graph");
                } else if (state == null || !state.connected()) { coverage = "incomplete"; result.reasons.add("read-connectivity-unknown"); }
                origins.add(new OriginSet(object, List.copyOf(result.ids), coverage, List.copyOf(result.reasons)));
                if (coverage.equals("incomplete")) c.reasons.addAll(result.reasons);
            }
            c.reads.set(i, new Read(read.ordinal(), read.declaration(), read.domain(), read.evidence(), read.binding(), origins, read.reasons()));
        }
    }
}
