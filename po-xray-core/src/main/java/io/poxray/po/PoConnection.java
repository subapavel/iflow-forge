package io.poxray.po;

import io.poxray.xi.XiObjectSource;
import java.nio.file.Path;

/**
 * A configured system opened for reading: the online client with the on-disk snapshot cache, or, for a system
 * configured with {@code tpz=<directory>}, the TPZ exports in memory (no cache — the directory is the snapshot).
 */
public record PoConnection(PoGateway gateway, XiObjectSource source) {

    /** Opens {@code system}; {@code cacheDir} null = {@code ~/.po-xray/cache/<system>} (online systems only). */
    public static PoConnection open(PoSystem system, Path cacheDir, boolean refresh) {
        if (system.tpz() != null) {
            TpzGateway gateway = new TpzGateway(system);
            return new PoConnection(gateway, gateway);
        }
        PoClient client = new PoClient(system);
        return new PoConnection(client, new CachingObjectSource(client,
                cacheDir == null ? CachingObjectSource.defaultDirectory(system) : cacheDir, refresh));
    }
}
