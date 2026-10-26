package io.poxray.migration;

import io.poxray.model.IntegrationInventory;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Local admission of exports; a failed optional configuration does not discard usable exports. */
final class BehaviorInputs {
    record Loaded(List<ScaInventory> inventories, List<String> labels, IntegrationInventory icos,
            String icosLabel, boolean icoUnavailable, List<Issue> issues) { }

    static Loaded read(Path root, Path icoFile) throws IOException {
        if (root == null || !Files.exists(root) || !Files.isReadable(root))
            throw new IOException("Missing or unreadable export root: " + root);
        List<Path> files;
        if (Files.isDirectory(root)) {
            try (var entries = Files.list(root)) {
                files = entries.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
            } catch (UncheckedIOException e) { throw new IOException("Cannot list export root: " + root, e.getCause()); }
        } else if (Files.isRegularFile(root)) files = List.of(root);
        else throw new IOException("Export root is not a file or directory: " + root);
        List<ScaInventory> inventories = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        for (Path file : files) {
            try {
                ScaInventory inventory = Reports.readJson(file, ScaInventory.class);
                if (inventory.bpm() == null && inventory.ejb() == null) throw new IOException("Not an SCA inventory");
                if (!supported(inventory.schemaVersion())) throw new IOException("Unsupported SCA schema: " + inventory.schemaVersion());
                inventories.add(inventory); labels.add(file.toString());
            } catch (IOException e) { issues.add(issue("BEHAVIOR_INPUT_UNAVAILABLE", file.toString(), e.getMessage())); }
        }
        if (inventories.isEmpty()) issues.add(issue("BEHAVIOR_NO_USABLE_EXPORT", root.toString(), "No usable SCA export"));
        IntegrationInventory icos = null;
        boolean unavailable = false;
        if (icoFile != null) {
            try { icos = Reports.readJson(icoFile, IntegrationInventory.class); }
            catch (IOException e) {
                unavailable = true; issues.add(issue("BEHAVIOR_ICO_UNAVAILABLE", icoFile.toString(), e.getMessage()));
            }
        }
        return new Loaded(inventories, labels, icos, icoFile == null ? null : icoFile.toString(), unavailable, issues);
    }

    static boolean supported(String schema) { return schema == null || "0.1".equals(schema); }
    static Issue issue(String code, String source, String message) { return new Issue(Issue.WARNING, code, message, null, source); }
    private BehaviorInputs() { }
}
