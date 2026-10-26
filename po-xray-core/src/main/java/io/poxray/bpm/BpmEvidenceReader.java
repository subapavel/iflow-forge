package io.poxray.bpm;

import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Candidate;
import io.poxray.model.BpmSourceEvidence.Element;
import io.poxray.model.BpmSourceEvidence.Endpoint;
import io.poxray.model.BpmSourceEvidence.Link;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.BpmSourceEvidence.Property;
import io.poxray.moin.MoinElement;
import io.poxray.moin.MoinLink;
import io.poxray.moin.MoinPartition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Selects bpm-source-v1 facts from the existing parsed index without first-match resolution. */
final class BpmEvidenceReader {

    private BpmEvidenceReader() {
    }

    static BpmSourceEvidence read(BpmContext ctx, List<String> processPartitions) {
        Set<String> processPaths = new HashSet<>(processPartitions);
        List<String> indexedPartitions = new ArrayList<>();
        List<ElementOccurrence> occurrences = new ArrayList<>();
        Map<String, List<ElementOccurrence>> byId = new HashMap<>();
        Set<Locator> selected = new HashSet<>();
        Set<String> seedIds = new HashSet<>();
        for (MoinPartition partition : ctx.index.partitions()) {
            indexedPartitions.add(partition.path());
            int ordinal = 0;
            for (MoinElement element : partition.elements()) {
                Locator location = new Locator(ctx.sourceArchive, partition.path(), ++ordinal);
                ElementOccurrence occurrence = new ElementOccurrence(location, element);
                occurrences.add(occurrence);
                if (hasId(element.id())) {
                    byId.computeIfAbsent(element.id(), ignored -> new ArrayList<>()).add(occurrence);
                }
                if (processPaths.contains(partition.path()) || isSeed(element.metaclass())) {
                    selected.add(location);
                    if (hasId(element.id())) {
                        seedIds.add(element.id());
                    }
                }
            }
        }

        List<LinkOccurrence> selectedLinks = new ArrayList<>();
        for (MoinPartition partition : ctx.index.partitions()) {
            int ordinal = 0;
            for (MoinLink link : partition.links()) {
                Locator location = new Locator(ctx.sourceArchive, partition.path(), ++ordinal);
                if (processPaths.contains(partition.path()) || hasSourcePrefix(link.association())
                        || seedIds.contains(link.first()) || seedIds.contains(link.second())) {
                    selectedLinks.add(new LinkOccurrence(location, link));
                    selectSupports(link.first(), byId, selected);
                    selectSupports(link.second(), byId, selected);
                }
            }
        }

        List<Element> elements = new ArrayList<>();
        for (ElementOccurrence occurrence : occurrences) {
            if (selected.contains(occurrence.location())) {
                elements.add(element(ctx, occurrence));
            }
        }
        List<Link> links = new ArrayList<>();
        for (LinkOccurrence occurrence : selectedLinks) {
            MoinLink link = occurrence.link();
            links.add(new Link(occurrence.location(), link.association(),
                    endpoint(ctx, occurrence, "f", link.first(), byId, selected),
                    endpoint(ctx, occurrence, "s", link.second(), byId, selected)));
        }
        return new BpmSourceEvidence(BpmSourceEvidence.FORMAT_VERSION, BpmSourceEvidence.SELECTION_VERSION,
                indexedPartitions, processPartitions, elements, links);
    }

    private static boolean hasId(String id) {
        return id != null && !id.isBlank();
    }

    private static boolean hasSourcePrefix(String name) {
        return name != null && (name.startsWith("Galaxy.Workflow.") || name.startsWith("Galaxy.Mapping.")
                || name.startsWith("Galaxy.Rule."));
    }

    private static boolean isSeed(String metaclass) {
        return hasSourcePrefix(metaclass) || "Galaxy.ProcessSupplements.NotificationActivity".equals(metaclass);
    }

    private static void selectSupports(String id, Map<String, List<ElementOccurrence>> byId, Set<Locator> selected) {
        for (ElementOccurrence candidate : candidates(id, byId)) {
            String metaclass = candidate.element().metaclass();
            if ("XSD1.key.QName".equals(metaclass) || "properties.PropertyBag".equals(metaclass)) {
                selected.add(candidate.location());
            }
        }
    }

    private static List<ElementOccurrence> candidates(String id, Map<String, List<ElementOccurrence>> byId) {
        return hasId(id) ? byId.getOrDefault(id, List.of()) : List.of();
    }

    private static Element element(BpmContext ctx, ElementOccurrence occurrence) {
        MoinElement element = occurrence.element();
        List<Property> properties = new ArrayList<>();
        for (var entry : element.properties().entrySet()) {
            properties.add(new Property(entry.getKey(), entry.getValue()));
            if (entry.getKey() == null) {
                String path = path(occurrence.location(), "element") + "/property[" + properties.size() + "]";
                ctx.issues.warn("BPM_SOURCE_PROPERTY_KEY_MISSING",
                        "Missing property key on element '" + element.id() + "' at " + path,
                        ctx.at(null, element, path), ctx.source(occurrence.location().partition()));
            }
        }
        return new Element(occurrence.location(), element.id(), element.metaclass(), element.attributes(),
                element.texts(), element.values(), properties);
    }

    private static Endpoint endpoint(BpmContext ctx, LinkOccurrence occurrence, String side, String id,
            Map<String, List<ElementOccurrence>> byId, Set<Locator> selected) {
        List<Candidate> candidates = candidates(id, byId).stream()
                .map(e -> new Candidate(e.location(), e.element().metaclass(), selected.contains(e.location())))
                .toList();
        String resolution = candidates.isEmpty() ? "missing" : candidates.size() == 1 ? "unique" : "ambiguous";
        if (!"unique".equals(resolution)) {
            String path = path(occurrence.location(), "link") + "/" + side;
            String code = candidates.isEmpty() ? "BPM_SOURCE_REFERENCE_UNRESOLVED" : "BPM_SOURCE_REFERENCE_AMBIGUOUS";
            ctx.issues.warn(code, "Association " + occurrence.link().association() + " endpoint " + side
                            + " id " + (id == null ? "<null>" : "'" + id + "'") + " is " + resolution + " at " + path,
                    ctx.at(null, null, path), ctx.source(occurrence.location().partition()));
        }
        return new Endpoint(id, resolution, candidates);
    }

    private static String path(Locator location, String kind) {
        return location.archive() + "!/" + location.partition() + "#" + kind + "[" + location.ordinal() + "]";
    }

    private record ElementOccurrence(Locator location, MoinElement element) {
    }

    private record LinkOccurrence(Locator location, MoinLink link) {
    }
}
