package io.poxray.xi;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Where ESR / Directory objects come from: a TPZ export ({@link InMemoryObjectSource}) or, later, the online
 * SimpleQuery / read/ext API. Implementations return the current version of an object.
 */
public interface XiObjectSource {

    /** The object a reference points to (resolved by OID if present, else by type + SWCV + key). */
    Optional<XiObject> resolve(XiRef ref);

    /** Why the last {@link #resolve} of {@code ref} returned nothing (e.g. the server's error), if known. */
    default Optional<String> failure(XiRef ref) {
        return Optional.empty();
    }

    /**
     * All references of a type; {@code online} lists them on the PO system. A snapshot source keeps the last
     * listing and falls back to it when the system cannot be reached (see {@link #offlineNotes}).
     */
    default List<XiRef> list(String typeId, Supplier<List<XiRef>> online) {
        return online.get();
    }

    /** Notes on results served from an offline snapshot instead of the PO system (empty when online). */
    default List<String> offlineNotes() {
        return List.of();
    }
}
