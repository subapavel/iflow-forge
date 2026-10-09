package io.poxray.moin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Global index over all partitions of one DC: elements by MOF ID and association links in both directions.
 * <p>
 * Naming follows the link ends: for a link {@code (f, s)}, {@link #firsts(String, String)} returns all {@code f}
 * for a given {@code s}, {@link #seconds(String, String)} all {@code s} for a given {@code f}.
 * Order is file order (significant e.g. for {@code Rule.ExpressionHasSteps}).
 */
public final class MoinIndex {

    private final Map<String, MoinPartition> partitions = new LinkedHashMap<>();
    private final Map<String, MoinElement> elements = new HashMap<>();
    private final Map<String, Map<String, List<String>>> firstsBySecond = new HashMap<>();
    private final Map<String, Map<String, List<String>>> secondsByFirst = new HashMap<>();
    private final List<String> duplicateIds = new ArrayList<>();

    public void add(MoinPartition partition) {
        partitions.put(partition.path(), partition);
        for (MoinElement e : partition.elements()) {
            if (elements.putIfAbsent(e.id(), e) != null) {
                duplicateIds.add(e.id());
            }
        }
        for (MoinLink l : partition.links()) {
            firstsBySecond.computeIfAbsent(l.association(), k -> new HashMap<>())
                    .computeIfAbsent(l.second(), k -> new ArrayList<>()).add(l.first());
            secondsByFirst.computeIfAbsent(l.association(), k -> new HashMap<>())
                    .computeIfAbsent(l.first(), k -> new ArrayList<>()).add(l.second());
        }
    }

    public Collection<MoinPartition> partitions() {
        return partitions.values();
    }

    public Optional<MoinElement> get(String id) {
        return Optional.ofNullable(id == null ? null : elements.get(id));
    }

    public boolean contains(String id) {
        return id != null && elements.containsKey(id);
    }

    /** All {@code f} ends of association links whose {@code s} end is {@code second}. */
    public List<String> firsts(String association, String second) {
        return firstsBySecond.getOrDefault(association, Map.of()).getOrDefault(second, List.of());
    }

    /** All {@code s} ends of association links whose {@code f} end is {@code first}. */
    public List<String> seconds(String association, String first) {
        return secondsByFirst.getOrDefault(association, Map.of()).getOrDefault(first, List.of());
    }

    public Optional<String> first(String association, String second) {
        List<String> l = firsts(association, second);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.getFirst());
    }

    public Optional<String> second(String association, String first) {
        List<String> l = seconds(association, first);
        return l.isEmpty() ? Optional.empty() : Optional.of(l.getFirst());
    }

    /** Resolved elements of {@link #firsts}; unresolved IDs are skipped. */
    public List<MoinElement> firstElements(String association, String second) {
        return resolve(firsts(association, second));
    }

    /** Resolved elements of {@link #seconds}; unresolved IDs are skipped. */
    public List<MoinElement> secondElements(String association, String first) {
        return resolve(seconds(association, first));
    }

    public Optional<MoinElement> firstElement(String association, String second) {
        return first(association, second).flatMap(this::get);
    }

    public Optional<MoinElement> secondElement(String association, String first) {
        return second(association, first).flatMap(this::get);
    }

    public List<MoinElement> elementsOf(String metaclass) {
        List<MoinElement> out = new ArrayList<>();
        for (MoinPartition p : partitions.values()) {
            for (MoinElement e : p.elements()) {
                if (e.is(metaclass)) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    /** IDs declared in more than one partition (first declaration wins). */
    public List<String> duplicateIds() {
        return List.copyOf(duplicateIds);
    }

    private List<MoinElement> resolve(List<String> ids) {
        List<MoinElement> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            MoinElement e = elements.get(id);
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }
}
