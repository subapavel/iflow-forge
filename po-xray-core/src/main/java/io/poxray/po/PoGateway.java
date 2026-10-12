package io.poxray.po;

import io.poxray.xi.XiRef;

/**
 * Access to the ESR / Integration Directory content of a system: discovery (SimpleQuery) and raw object content.
 * Implementations: {@link PoClient} (online), {@link TpzGateway} (a directory of TPZ exports).
 */
public interface PoGateway {

    PoSystem system();

    /** Runs a SimpleQuery (or answers it from the exports). */
    QueryResult query(QueryRequest request);

    /** The raw {@code p1:xiObj} XML of an object. */
    String readRaw(XiRef ref);
}
