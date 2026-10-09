package io.poxray.po;

import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectParser;
import io.poxray.xi.XiObjectSource;
import io.poxray.xi.XiRef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Objects read online ({@code read/ext}) and kept as raw {@code xiObj} XML in a local snapshot directory, so that
 * repeated queries (e.g. by an agent) do not hit the PO system and work offline. Layout:
 * {@code <dir>/<rep|dir>/<typeId>/<hash of SWCV + key>.xml}, listings (SimpleQuery) as
 * {@code <dir>/lists/<typeId>.json}. {@code refresh} re-reads cached objects. When the PO system cannot be reached,
 * listings come from the snapshot and {@link #offlineNotes()} says so.
 * <p>
 * Freshness: before the first object is served from the snapshot, the PO system is asked once which objects of the
 * cached types changed since the last check ({@code MODIFYDATE > last check - 1 day}, the margin covers time zones);
 * cached objects whose version ID differs are dropped and read again. State: {@code <dir>/sync.properties}.
 */
public final class CachingObjectSource implements XiObjectSource {

    /** Date format of SimpleQuery date conditions; {@code LocalDateTime.toString()} drops zero seconds. */
    private static final java.time.format.DateTimeFormatter SERVER_TIME =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Pattern VID = Pattern.compile("VID=\"([^\"]+)\"");

    private final PoClient client;
    private final Path directory;
    private final boolean refresh;
    private final Map<Path, String> failures = new ConcurrentHashMap<>();
    private final List<String> offlineNotes = new CopyOnWriteArrayList<>();
    private volatile boolean synced;
    private int dropped;

    public CachingObjectSource(PoClient client, Path directory, boolean refresh) {
        this.client = client;
        this.directory = directory;
        this.refresh = refresh;
    }

    /** Default snapshot location: {@code ~/.po-xray/cache/<system>}. */
    public static Path defaultDirectory(PoSystem system) {
        return Path.of(System.getProperty("user.home"), ".po-xray", "cache", system.name());
    }

    @Override
    public Optional<XiObject> resolve(XiRef ref) {
        if (ref == null || ref.typeId() == null || (!ref.isDirectory() && ref.swcGuid() == null)) {
            return Optional.empty();
        }
        Path file = fileOf(ref);
        try {
            if (!refresh && Files.isRegularFile(file)) {
                syncOnce();
            }
            if (!refresh && Files.isRegularFile(file)) {
                return Optional.of(XiObjectParser.parse(Files.readString(file, StandardCharsets.UTF_8), file.toString()));
            }
            String raw = client.readRaw(ref);
            Files.createDirectories(file.getParent());
            Files.writeString(file, raw, StandardCharsets.UTF_8);
            return Optional.of(XiObjectParser.parse(raw, ref.label()));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot access snapshot " + file, e);
        } catch (PoException e) {
            if (isAuthentication(e)) {
                throw e;
            }
            failures.put(file, e.getMessage());
            return Optional.empty(); // deleted or not renderable: reported by the caller with this reason
        }
    }

    /** Number of cached objects dropped because they changed on the PO system (after the check ran). */
    public int changedObjects() {
        return dropped;
    }

    private void syncOnce() {
        if (synced) {
            return;
        }
        synchronized (this) {
            if (!synced) {
                synced = true;
                sync();
            }
        }
    }

    /** Drops cached objects changed on the PO system since the last check (see class comment). */
    void sync() {
        Path stateFile = directory.resolve("sync.properties");
        Properties state = new Properties();
        try {
            if (Files.isRegularFile(stateFile)) {
                try (var in = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
                    state.load(in);
                }
            }
            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            for (Area area : Area.values()) {
                Path areaDir = directory.resolve(area.path());
                List<String> types = subdirectories(areaDir);
                if (types.isEmpty()) {
                    continue;
                }
                String last = state.getProperty(area.path());
                LocalDateTime since = (last != null ? LocalDateTime.parse(last) : oldest(areaDir)).minusDays(1);
                QueryResult changed;
                try {
                    changed = client.query(new QueryRequest(area, types, "MODIFYDATE", since.format(SERVER_TIME), "GT", false,
                            List.of("RA_XILINK", "VERSIONID"), "date"));
                } catch (PoException e) {
                    if (isAuthentication(e)) {
                        throw e;
                    }
                    offlineNotes.add(e.getMessage() + " - " + area.path()
                            + " snapshot objects used without checking for changes");
                    continue;
                }
                for (QueryResult.Row row : changed.rows()) {
                    XiRef ref = row.xiLink();
                    String vid = row.text("VERSIONID");
                    Path file = ref == null || vid == null ? null : fileOf(ref);
                    if (file != null && Files.isRegularFile(file) && !vid.equalsIgnoreCase(cachedVersion(file))) {
                        Files.delete(file);
                        dropped++;
                    }
                }
                state.setProperty(area.path(), now.format(SERVER_TIME));
            }
            Files.createDirectories(directory);
            try (var out = Files.newBufferedWriter(stateFile, StandardCharsets.UTF_8)) {
                state.store(out, "po-xray snapshot: last check for changed objects (local time, 1 day margin)");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot update snapshot " + directory, e);
        }
    }

    private static List<String> subdirectories(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var s = Files.list(dir)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    /** Modification time of the oldest snapshot file (first check of a snapshot taken before checks existed). */
    private static LocalDateTime oldest(Path dir) throws IOException {
        try (var files = Files.walk(dir)) {
            long min = files.filter(Files::isRegularFile).mapToLong(f -> {
                try {
                    return Files.getLastModifiedTime(f).toMillis();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }).min().orElse(System.currentTimeMillis());
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(min), ZoneId.systemDefault())
                    .truncatedTo(ChronoUnit.SECONDS);
        }
    }

    /** {@code VID} of a cached {@code xiObj} (in its first lines). */
    static String cachedVersion(Path file) throws IOException {
        try (var in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            char[] buf = new char[4096];
            int n = in.read(buf);
            Matcher m = VID.matcher(n < 0 ? "" : new String(buf, 0, n));
            return m.find() ? m.group(1) : null;
        }
    }

    @Override
    public List<XiRef> list(String typeId, Supplier<List<XiRef>> online) {
        Path file = directory.resolve("lists").resolve(typeId + ".json");
        List<XiRef> refs;
        try {
            refs = online.get();
        } catch (PoException e) {
            if (isAuthentication(e)) {
                throw e;
            }
            if (!Files.isRegularFile(file)) {
                List<XiRef> cached = cachedObjects(typeId);
                if (cached.isEmpty()) {
                    throw e;
                }
                offlineNotes.add(e.getMessage() + " - " + typeId + " listed from the " + cached.size()
                        + " objects in the snapshot (may be incomplete)");
                return cached;
            }
            try {
                Instant at = Files.getLastModifiedTime(file).toInstant().truncatedTo(ChronoUnit.SECONDS);
                offlineNotes.add(e.getMessage() + " - using the snapshot of " + typeId + " from " + at
                        + " (objects not in the snapshot cannot be read)");
                return readList(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException io) {
                throw new UncheckedIOException("Cannot read snapshot " + file, io);
            }
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, writeList(refs), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write snapshot " + file, e);
        }
        return refs;
    }

    @Override
    public List<String> offlineNotes() {
        return List.copyOf(offlineNotes);
    }

    /** References of the snapshot objects of a type (fallback for snapshots taken before listings were kept). */
    private List<XiRef> cachedObjects(String typeId) {
        List<XiRef> out = new ArrayList<>();
        for (Area area : Area.values()) {
            Path dir = directory.resolve(area.path()).resolve(typeId);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (var files = Files.list(dir)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".xml")).sorted().toList()) {
                    out.add(XiObjectParser.parse(Files.readString(f, StandardCharsets.UTF_8), f.toString()).id());
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read snapshot " + dir, e);
            }
        }
        return out;
    }

    private static boolean isAuthentication(PoException e) {
        return e.getMessage() != null && e.getMessage().contains("authentication failed");
    }

    /** Listing as a JSON tree (no reflection, works in native images). */
    static String writeList(List<XiRef> refs) {
        ArrayNode array = io.poxray.json.Json.mapper().createArrayNode();
        for (XiRef r : refs) {
            ObjectNode o = array.addObject();
            o.put("vcType", r.vcType());
            o.put("swcGuid", r.swcGuid());
            o.put("swcCaption", r.swcCaption());
            o.put("typeId", r.typeId());
            o.put("oid", r.oid());
            ArrayNode key = o.putArray("key");
            r.key().forEach(key::add);
        }
        return io.poxray.json.Json.mapper().writeValueAsString(array);
    }

    static List<XiRef> readList(String json) {
        List<XiRef> out = new ArrayList<>();
        for (JsonNode o : io.poxray.json.Json.mapper().readTree(json)) {
            List<String> key = new ArrayList<>();
            o.path("key").forEach(k -> key.add(k.asString()));
            out.add(new XiRef(text(o, "vcType"), text(o, "swcGuid"), text(o, "swcCaption"), text(o, "typeId"),
                    text(o, "oid"), List.copyOf(key)));
        }
        return out;
    }

    private static String text(JsonNode o, String field) {
        JsonNode n = o.get(field);
        return n == null || n.isNull() ? null : n.asString();
    }

    @Override
    public Optional<String> failure(XiRef ref) {
        return ref == null || ref.typeId() == null ? Optional.empty() : Optional.ofNullable(failures.get(fileOf(ref)));
    }

    Path fileOf(XiRef ref) {
        Area area = Area.of(ref);
        String scope = area == Area.DIRECTORY ? "" : ref.swcGuid();
        return directory.resolve(area.path()).resolve(ref.typeId()).resolve(hash(scope + "|" + ref.keyString()) + ".xml");
    }

    private static String hash(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
