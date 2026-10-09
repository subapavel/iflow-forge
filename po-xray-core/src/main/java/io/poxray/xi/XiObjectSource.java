package io.poxray.xi;

import java.util.Optional;

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
}
