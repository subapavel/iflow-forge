package io.poxray.mcp;

import io.poxray.PoXray;
import io.poxray.integration.IntegrationQuery;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Component;
import io.poxray.model.ScaInventory;
import io.poxray.po.CachingObjectSource;
import io.poxray.po.PoClient;
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
 * Reads and keeps inventories for the session: integration inventories per (system, component, direction) on top
 * of the on-disk snapshot (~/.po-xray/cache/&lt;system&gt;), SCA inventories per file and modification time.
 * Credentials come from ~/.po-xray/systems/&lt;system&gt;.properties (or POXRAY_USER / POXRAY_PASSWORD) and never
 * leave {@link PoSystem}.
 */
@ApplicationScoped
public class Inventories {

    private final Map<String, IntegrationInventory> integration = new ConcurrentHashMap<>();
    private final Map<String, ScaInventory> sca = new ConcurrentHashMap<>();

    /** Components matching the pattern; {@code warnings} when served from the offline snapshot. */
    record Components(List<Component> components, List<String> warnings) {
    }

    Components components(String system, String pattern) {
        IntegrationQuery q = query(system, false);
        List<Component> found = q.components(pattern);
        return new Components(found, q.offlineNotes());
    }

    IntegrationInventory icos(String system, String component, String direction, boolean refresh) {
        IntegrationQuery.Direction d = direction(direction);
        String key = system + "|" + component.toLowerCase(Locale.ROOT) + "|" + d;
        if (refresh) {
            integration.remove(key);
        }
        return integration.computeIfAbsent(key, k -> query(system, refresh).icos(component, d));
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

    private static IntegrationQuery query(String system, boolean refresh) {
        PoClient client = new PoClient(PoSystem.load(system));
        return new IntegrationQuery(client,
                new CachingObjectSource(client, CachingObjectSource.defaultDirectory(client.system()), refresh));
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
