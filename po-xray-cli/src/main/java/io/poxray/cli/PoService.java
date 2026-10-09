package io.poxray.cli;

import io.poxray.integration.IntegrationQuery;
import io.poxray.po.CachingObjectSource;
import io.poxray.po.PoClient;
import io.poxray.po.PoSystem;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;

/** Access to a configured PO system (~/.po-xray/systems/<name>.properties) with the local snapshot cache. */
@ApplicationScoped
public class PoService {

    /**
     * Query API for a system; objects are cached under {@code cache} (default ~/.po-xray/cache/<system>),
     * {@code refresh} re-reads them.
     */
    IntegrationQuery query(String system, String cache, boolean refresh) {
        PoClient client = new PoClient(PoSystem.load(system));
        Path dir = cache == null ? CachingObjectSource.defaultDirectory(client.system()) : Path.of(cache);
        return new IntegrationQuery(client, new CachingObjectSource(client, dir, refresh));
    }

    /** File name part for a component pattern: {@code icos-V3S_D}, {@code icos-_DIGISIGN_}. */
    static String baseName(String component) {
        return "icos-" + component.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
