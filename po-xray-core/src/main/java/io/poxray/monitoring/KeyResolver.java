package io.poxray.monitoring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Turns what a user gives (a message ID or a message key) into one message key, never picking silently: a value with
 * a backslash is a key and is returned as is; any other value is a message ID looked up with
 * {@code getMessagesByIDs}, narrowed to {@code direction} when it is given. Exactly one key (and no message without
 * one) is a {@link Resolution.Key}; several keys (the sender and the receiver side of one message, for example), or a
 * key next to a message without one, are {@link Resolution.Ambiguous} with every candidate; no message, or only
 * messages without a key, are {@link Resolution.NotFound} with the reason. A key returned twice is one candidate.
 */
public final class KeyResolver {

    private KeyResolver() {
    }

    /** The outcome of {@link #resolve}. */
    public sealed interface Resolution permits Resolution.Key, Resolution.Ambiguous, Resolution.NotFound {

        /** Exactly one message key. */
        record Key(String messageKey) implements Resolution {
        }

        /**
         * More than one candidate: the messages in the order of the answer, one per key, then those without a key.
         */
        record Ambiguous(List<Message> candidates) implements Resolution {

            public Ambiguous {
                candidates = List.copyOf(candidates);
            }
        }

        /** No usable key for {@code idOrKey}; {@code reason} says why (no message, other direction, no key). */
        record NotFound(String idOrKey, String reason) implements Resolution {
        }
    }

    /**
     * Resolves {@code idOrKey}; {@code direction} ({@code OUTBOUND} or {@code INBOUND}, case-insensitive) narrows the
     * messages of an ID and may be null or blank; {@code archive} also searches the archive.
     */
    public static Resolution resolve(MonitoringClient client, String idOrKey, String direction, boolean archive) {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(idOrKey, "idOrKey");
        if (idOrKey.indexOf('\\') >= 0) {
            return new Resolution.Key(idOrKey);
        }
        SearchResult found = client.getMessagesByIds(List.of(idOrKey), List.of(), List.of(), archive);
        boolean any = direction == null || direction.isBlank();
        List<Message> keyed = new ArrayList<>();
        List<Message> keyless = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Message m : found.messages()) {
            if (!any && !direction.strip().equalsIgnoreCase(m.direction())) {
                continue;
            }
            if (m.messageKey() == null) {
                keyless.add(m);
            } else if (keys.add(m.messageKey())) {
                keyed.add(m);
            }
        }
        if (keyed.isEmpty() && keyless.isEmpty()) {
            return new Resolution.NotFound(idOrKey, found.messages().isEmpty() ? "no message with this ID"
                    : "no message with this ID in direction " + direction.strip() + " (found: "
                            + String.join(", ", directions(found.messages())) + ")");
        }
        if (keyed.isEmpty()) {
            return new Resolution.NotFound(idOrKey, keyless.size() == 1 ? "message found but has no key"
                    : keyless.size() + " messages found but all have no key");
        }
        if (keyed.size() == 1 && keyless.isEmpty()) {
            return new Resolution.Key(keyed.getFirst().messageKey());
        }
        List<Message> candidates = new ArrayList<>(keyed);
        candidates.addAll(keyless);
        return new Resolution.Ambiguous(candidates);
    }

    /** The distinct directions of these messages in order, {@code (none)} for a message without one. */
    private static Set<String> directions(List<Message> messages) {
        Set<String> out = new LinkedHashSet<>();
        for (Message m : messages) {
            out.add(m.direction() == null ? "(none)" : m.direction());
        }
        return out;
    }
}
