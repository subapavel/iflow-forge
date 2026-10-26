package io.poxray.migration;

import io.poxray.model.BehaviorReport.*;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmPathReport.*;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.*;
import io.poxray.model.ServiceReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static io.poxray.migration.Reports.list;
import static io.poxray.migration.BehaviorChannels.addReason;

/** Occurrence-qualified call associations. Catalogue hints cannot repair malformed raw grammar. */
final class BehaviorCalls {
    private static final String W = "Galaxy.Workflow.";
    private static final String OPERATION = W + "ActivityHasOperation";
    private static final String SERVICE = W + "AutomatedActivityHasServiceReference";
    private static final String TRIGGER = W + "CatchEventHasTrigger";
    private static final String MESSAGE_OPERATION = W + "MessageEventDefinitionHasOperation";
    private static final String AUTOMATED = W + "AutomatedActivity";
    private static final String NOTIFICATION = "Galaxy.ProcessSupplements.NotificationActivity";
    private static final String MESSAGE = W + "MessageEventDefinition";
    private static final String SERVICE_KIND = "mc.servicereferences.ServiceReference";
    private final BpmSourceEvidence source;
    private final Map<Locator, Element> elements = new LinkedHashMap<>();
    private final List<ServiceReference> services;

    private BehaviorCalls(BpmComponent component) {
        source = component.sourceEvidence();
        if (source != null) for (Element element : source.elements()) if (element != null) elements.put(element.location(), element);
        services = component.catalog() == null ? List.of() : list(component.catalog().serviceReferences());
    }

    static List<StepCall> calls(BpmComponent component, ProcessPaths path, List<InterfaceUse> interfaces, boolean available) {
        BehaviorCalls reader = new BehaviorCalls(component);
        List<StepCall> result = new ArrayList<>();
        for (Step step : path.steps()) result.add(reader.call(step, interfaces, available));
        return result;
    }

    private StepCall call(Step step, List<InterfaceUse> interfaces, boolean available) {
        List<Integer> hints = interfaces.stream().filter(i -> i.steps().contains(step.ordinal())).map(InterfaceUse::ordinal).toList();
        List<String> reasons = new ArrayList<>();
        List<ServiceCandidate> serviceCandidates = new ArrayList<>();
        Set<Locator> selected = new LinkedHashSet<>();
        boolean unknown = !available, declared = false;
        if (!available) reasons.add("source-unavailable");
        if (available) {
            List<Link> direct = source.links().stream().filter(Objects::nonNull).filter(l -> (OPERATION.equals(l.association()) || SERVICE.equals(l.association()))
                    && touches(l.second(), step.location())).toList();
            long operations = direct.stream().filter(l -> OPERATION.equals(l.association())).count();
            long references = direct.stream().filter(l -> SERVICE.equals(l.association())).count();
            if (operations > 1 || references > 1) { unknown = true; reasons.add("multiple-direct-relations"); }
            Set<Integer> seenServices = new LinkedHashSet<>();
            for (Link link : direct) {
                selected.add(link.location());
                Element owner = unique(link.second()), target = unique(link.first());
                boolean operation = OPERATION.equals(link.association());
                boolean valid = owner != null && target != null && Objects.equals(owner.location(), step.location())
                        && (operation ? AUTOMATED.equals(owner.metaclass()) || NOTIFICATION.equals(owner.metaclass()) : AUTOMATED.equals(owner.metaclass()))
                        && (operation ? "WSDL2.Operation".equals(target.metaclass()) : SERVICE_KIND.equals(target.metaclass()));
                if (valid) declared = true;
                else { unknown = true; addReason(reasons, "invalid-direct-call-evidence"); }
                if (!operation) {
                    List<Integer> candidates = new ArrayList<>();
                    for (int s = 0; s < services.size(); s++) if (services.get(s) != null && link.first() != null
                            && link.first().id() != null && link.first().id().equals(services.get(s).id())) candidates.add(s + 1);
                    if (candidates.isEmpty()) addReason(reasons, "service-projection-unavailable");
                    String state = target == null || !SERVICE_KIND.equals(target.metaclass()) ? "unavailable" : candidates.size() > 1 ? "ambiguous" : "candidate";
                    for (int ordinal : candidates) if (seenServices.add(ordinal)) serviceCandidates.add(new ServiceCandidate(ordinal,
                            services.get(ordinal - 1), state, "candidate".equals(state) ? List.of() : List.of("service-projection-" + state)));
                }
            }
            for (Link trigger : source.links()) {
                if (trigger == null || !TRIGGER.equals(trigger.association()) || !touches(trigger.second(), step.location())) continue;
                List<Link> operationsAtDefinition = source.links().stream().filter(Objects::nonNull).filter(l -> MESSAGE_OPERATION.equals(l.association())
                        && (overlaps(trigger.first(), l.first()) || overlaps(trigger.first(), l.second()))).toList();
                boolean message = trigger.first() != null && trigger.first().candidates().stream().anyMatch(c -> MESSAGE.equals(c.metaclass()));
                if (!message && operationsAtDefinition.isEmpty()) continue;
                selected.add(trigger.location());
                operationsAtDefinition.forEach(l -> selected.add(l.location()));
                Element owner = unique(trigger.second()), definition = unique(trigger.first());
                boolean valid = owner != null && (W.concat("StartEvent").equals(owner.metaclass()) || W.concat("BoundaryEvent").equals(owner.metaclass()))
                        && definition != null && MESSAGE.equals(definition.metaclass());
                unknown = true;
                if (!valid) addReason(reasons, "invalid-catch-operation-evidence");
                if (operationsAtDefinition.isEmpty()) addReason(reasons, "missing-message-operation");
                else addReason(reasons, "unknown-operation-direction");
            }
        }
        List<Link> relations = new ArrayList<>();
        Set<Locator> seenLinks = new LinkedHashSet<>(), payload = new LinkedHashSet<>();
        if (available) for (Link link : source.links()) if (link != null && selected.contains(link.location()) && seenLinks.add(link.location())) {
            relations.add(link);
            payload(link.first(), payload); payload(link.second(), payload);
        }
        List<Element> included = new ArrayList<>();
        if (available) for (Element element : source.elements()) if (element != null && payload.remove(element.location())) included.add(element);
        String state = unknown ? "unknown" : declared ? "source-declared" : hints.isEmpty() ? "no-interface-call-observed" : "candidate";
        return new StepCall(step.ordinal(), step.location(), relations, included, hints, serviceCandidates, state, "unknown", "unknown", reasons);
    }
    private Element unique(BpmSourceEvidence.Endpoint endpoint) {
        if (endpoint == null || !"unique".equals(endpoint.resolution()) || endpoint.candidates().size() != 1) return null;
        Candidate candidate = endpoint.candidates().getFirst();
        return candidate.included() ? elements.get(candidate.location()) : null;
    }
    private static boolean touches(BpmSourceEvidence.Endpoint endpoint, Locator at) {
        return endpoint != null && endpoint.candidates().stream().anyMatch(c -> Objects.equals(c.location(), at));
    }
    private static boolean overlaps(BpmSourceEvidence.Endpoint a, BpmSourceEvidence.Endpoint b) {
        return a != null && b != null && a.candidates().stream().anyMatch(c -> touches(b, c.location()));
    }
    private static void payload(BpmSourceEvidence.Endpoint endpoint, Set<Locator> into) {
        if (endpoint != null) for (Candidate c : endpoint.candidates()) if (c.included()) into.add(c.location());
    }
}
