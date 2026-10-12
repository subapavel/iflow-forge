package io.poxray.mcp;

import io.poxray.PoXray;
import io.poxray.integration.IntegrationQuery;
import io.poxray.integration.ObjectCatalog;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Component;
import io.poxray.model.ScaInventory;
import io.poxray.po.PoConnection;
import io.poxray.po.PoSystem;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads and keeps inventories for the session: one connection per system (so TPZ exports and snapshot listings are
 * not re-read on every tool call), integration inventories per (system, component, direction), SCA inventories per
 * file and modification time. Credentials come from ~/.po-xray/systems/&lt;system&gt;.properties (or POXRAY_USER /
 * POXRAY_PASSWORD) and never leave {@link PoSystem}.
 */
@ApplicationScoped
public class Inventories {

    private final Map<String, PoConnection> connections = new ConcurrentHashMap<>();
    private final Map<String, IntegrationInventory> integration = new ConcurrentHashMap<>();
    private final Map<String, ScaInventory> sca = new ConcurrentHashMap<>();
    private final Map<String, ObjectCatalog> catalogs = new ConcurrentHashMap<>();

    /** Components matching the pattern; {@code warnings} when served from the offline snapshot. */
    record Components(List<Component> components, List<String> warnings) {
    }

    Components components(String system, String pattern) {
        IntegrationQuery q = query(connection(system));
        List<Component> found = q.components(pattern);
        return new Components(found, q.offlineNotes());
    }

    IntegrationInventory icos(String system, String component, String direction, boolean refresh) {
        IntegrationQuery.Direction d = direction(direction);
        String key = system + "|" + component.toLowerCase(Locale.ROOT) + "|" + d;
        if (refresh) {
            integration.remove(key);
            connections.remove(system);
            catalogs.remove(system);
        }
        return integration.computeIfAbsent(key,
                k -> query(refresh ? openConnection(system, true) : connection(system)).icos(component, d));
    }

    /** Object search / read / where-used of a system; listings are kept for the session. */
    ObjectCatalog catalog(String system) {
        return catalogs.computeIfAbsent(system, s -> {
            PoConnection c = connection(s);
            return new ObjectCatalog(c.gateway(), c.source());
        });
    }

    ScaInventory sca(String path) {
        Path file = Path.of(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("SCA file not found: " + path);
        }
        try {
            String key = file.toAbsolutePath().normalize() + "|" + Files.getLastModifiedTime(file).toMillis();
            return sca.computeIfAbsent(key, k -> PoXray.read(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The session connection of a system (opened once; TPZ systems load their exports here). */
    private PoConnection connection(String system) {
        return connections.computeIfAbsent(system, s -> openConnection(s, false));
    }

    /**
     * A refreshing connection re-reads every object it serves, so it is used for one inventory and not kept;
     * {@link #icos} drops the session connection first, so later calls see the refreshed snapshot / TPZ files.
     */
    PoConnection openConnection(String system, boolean refresh) {
        return PoConnection.open(PoSystem.load(system), null, refresh);
    }

    private static IntegrationQuery query(PoConnection connection) {
        return new IntegrationQuery(connection.gateway(), connection.source());
    }

    static IntegrationQuery.Direction direction(String direction) {
        try {
            return IntegrationQuery.Direction.valueOf((direction == null || direction.isBlank() ? "both" : direction)
                    .strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("direction must be in, out or both, not '" + direction + "'");
        }
    }
}
