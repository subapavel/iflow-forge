package io.poxray.cli;

import io.poxray.integration.IntegrationQuery;
import io.poxray.integration.ObjectCatalog;
import io.poxray.po.PoConnection;
import io.poxray.po.PoSystem;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;

/**
 * Access to a configured PO system (~/.po-xray/systems/<name>.properties): online with the local snapshot cache,
 * or a directory of TPZ exports (tpz=...).
 */
@ApplicationScoped
public class PoService {

    /**
     * Query API for a system; objects are cached under {@code cache} (default ~/.po-xray/cache/<system>),
     * {@code refresh} re-reads them.
     */
    IntegrationQuery query(String system, String cache, boolean refresh) {
        PoConnection c = PoConnection.open(PoSystem.load(system), cache == null ? null : Path.of(cache), refresh);
        return new IntegrationQuery(c.gateway(), c.source());
    }

    /** Search, read and where-used for any object type of a system, on top of the same snapshot. */
    ObjectCatalog catalog(String system) {
        PoConnection c = PoConnection.open(PoSystem.load(system), null, false);
        return new ObjectCatalog(c.gateway(), c.source());
    }

    /** File name part for a component pattern: {@code icos-V3S_D}, {@code icos-_DIGISIGN_}. */
    static String baseName(String component) {
        return "icos-" + component.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
