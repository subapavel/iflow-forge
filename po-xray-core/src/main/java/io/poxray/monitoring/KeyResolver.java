package io.poxray.monitoring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Turns what a user gives (a message ID or a message key) into one message key, never picking silently: a value with
 * a backslash is a key and is returned as is; any other value is a message ID looked up with
 * {@code getMessagesByIDs}, narrowed to {@code direction} when it is given. One key is a {@link Resolution.Key};
 * several (the sender and the receiver side of one message, for example) are {@link Resolution.Ambiguous} with every
 * candidate; none is {@link Resolution.NotFound}. A message the service returns without a key is not a candidate, and
 * a key returned twice is one candidate.
 */
public final class KeyResolver {

    private KeyResolver() {
    }

    /** The outcome of {@link #resolve}. */
    public sealed interface Resolution permits Resolution.Key, Resolution.Ambiguous, Resolution.NotFound {

        /** Exactly one message key. */
        record Key(String messageKey) implements Resolution {
        }

        /** Several message keys: the candidates in the order of the answer, one per key. */
        record Ambiguous(List<Message> candidates) implements Resolution {

            public Ambiguous {
                candidates = List.copyOf(candidates);
            }
        }

        /** No message (in {@code direction}, when given) has this ID. */
        record NotFound(String idOrKey) implements Resolution {
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
        List<Message> candidates = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Message m : found.messages()) {
            if (m.messageKey() != null && (any || direction.strip().equalsIgnoreCase(m.direction()))
                    && keys.add(m.messageKey())) {
                candidates.add(m);
            }
        }
        return switch (candidates.size()) {
            case 0 -> new Resolution.NotFound(idOrKey);
            case 1 -> new Resolution.Key(candidates.getFirst().messageKey());
            default -> new Resolution.Ambiguous(candidates);
        };
    }
}
