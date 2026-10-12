package io.poxray.po;

import io.poxray.xi.InMemoryObjectSource;
import io.poxray.xi.TpzArchive;
import io.poxray.xi.XiLink;
import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectSource;
import io.poxray.xi.XiRef;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A system backed by a directory of TPZ exports (ESR / Integration Directory transport files) instead of an online
 * connection: SimpleQuery answers are synthesised from the exported objects — {@code RA_LINK_LIST} from each
 * object's links, so where-used works offline — and object content comes from memory. Files are loaded in file name
 * order and an object occurring in several files is taken from the file loaded last, so date-stamped export names
 * behave predictably. TPZ exports do not carry environment-specific channel attributes (URLs, users) or passwords;
 * {@link #offlineNotes()} says so.
 */
public final class TpzGateway implements PoGateway, XiObjectSource {

    private final PoSystem system;
    private final InMemoryObjectSource objects = new InMemoryObjectSource();
    private final List<String> notes = new ArrayList<>();

    public TpzGateway(PoSystem system) {
        this.system = system;
        Path dir = system.tpz();
        if (dir == null || !Files.isDirectory(dir)) {
            throw new PoException("TPZ directory of system '" + system.name() + "' not found: " + dir);
        }
        List<Path> files;
        try (var list = Files.list(dir)) {
            files = list.filter(f -> f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tpz"))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + dir, e);
        }
        if (files.isEmpty()) {
            throw new PoException("No .tpz files in " + dir + " (system '" + system.name() + "')");
        }
        Map<String, String> sources = new LinkedHashMap<>();
        int duplicates = 0;
        int count = 0;
        for (Path file : files) {
            TpzArchive archive = TpzArchive.open(file);
            String source = archive.metadata().get("sourcesystem");
            if (source != null && !source.isBlank()) {
                sources.putIfAbsent(source, archive.name());
            }
            for (XiObject o : archive.objects()) {
                if (o.id() != null && objects.resolve(o.id()).isPresent()) {
                    duplicates++;
                }
                objects.add(o);
                count++;
            }
        }
        notes.add(files.size() + " TPZ exports with " + count + " objects (" + dir + "); environment-specific"
                + " channel attributes (URLs, users) are not part of TPZ exports");
        if (duplicates > 0) {
            notes.add(duplicates + " objects occur in several TPZ files: the version from the file loaded last"
                    + " (file name order) is used");
        }
        if (sources.size() > 1) {
            notes.add("The TPZ files come from different systems, check the directory: " + sources);
        }
    }

    @Override
    public PoSystem system() {
        return system;
    }

    /**
     * Answers a SimpleQuery from the exports. Conditions on {@code NAME} (any key element), {@code MODIFYUSER} and
     * {@code TEXT} are evaluated with {@code EQ} / {@code LIKE} ({@code *} {@code ?} wildcards); other condition
     * keys match everything (callers filter locally).
     */
    @Override
    public QueryResult query(QueryRequest request) {
        List<QueryResult.Row> rows = new ArrayList<>();
        Map<String, List<String>> keyNames = new LinkedHashMap<>();
        boolean links = request.attributes().contains("RA_LINK_LIST");
        for (String type : request.types()) {
            List<String> names = TypeKeys.NAMES.get(type);
            if (names != null) {
                keyNames.put(type, names);
            }
            for (XiObject o : objects.ofType(type)) {
                if (Area.of(o.id()) != request.area() || !matches(o, request)) {
                    continue;
                }
                Map<String, Object> cells = new LinkedHashMap<>();
                cells.put("RA_XILINK", o.id());
                put(cells, "VERSIONID", o.vid());
                put(cells, "MODIFYDATE", o.modifiedAt());
                put(cells, "MODIFYUSER", o.modifiedBy());
                put(cells, "TEXT", o.description());
                if (links) {
                    cells.put("RA_LINK_LIST", o.links().stream().map(XiLink::target).toList());
                }
                rows.add(new QueryResult.Row(cells));
            }
        }
        return new QueryResult(List.copyOf(request.attributes()), List.copyOf(rows), Map.copyOf(keyNames));
    }

    @Override
    public String readRaw(XiRef ref) {
        return objects.resolve(ref).map(XiObject::raw)
                .orElseThrow(() -> new PoException("Not in the TPZ exports of system '" + system.name() + "': "
                        + ref.label()));
    }

    // ------------------------------------------------------------------------------------------ object source

    @Override
    public Optional<XiObject> resolve(XiRef ref) {
        return objects.resolve(ref);
    }

    @Override
    public List<String> offlineNotes() {
        return List.copyOf(notes);
    }

    // ------------------------------------------------------------------------------------------ condition

    private static boolean matches(XiObject o, QueryRequest request) {
        String value = request.value();
        if (value == null || value.isBlank() || "*".equals(value.strip())) {
            return true;
        }
        return switch (request.key()) {
            case "NAME" -> o.id().key().stream().anyMatch(k -> matches(k, request));
            case "MODIFYUSER" -> o.modifiedBy() != null && matches(o.modifiedBy(), request);
            case "TEXT" -> o.description() != null && matches(o.description(), request);
            default -> true; // e.g. MODIFYDATE of the snapshot sync: not filtered offline
        };
    }

    private static boolean matches(String actual, QueryRequest request) {
        if ("LIKE".equals(request.operator())) {
            return like(request.value(), request.caseInsensitive()).matcher(actual).matches();
        }
        // EQ and everything else: SimpleQuery compares case-sensitively unless asked otherwise
        return request.caseInsensitive() ? actual.equalsIgnoreCase(request.value()) : actual.equals(request.value());
    }

    /** SimpleQuery LIKE: {@code *} and {@code ?} wildcards, no implicit substring match. */
    private static Pattern like(String pattern, boolean caseInsensitive) {
        StringBuilder regex = new StringBuilder();
        for (char ch : pattern.strip().toCharArray()) {
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> regex.append(Pattern.quote(String.valueOf(ch)));
            }
        }
        return Pattern.compile(regex.toString(),
                caseInsensitive ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
    }

    private static void put(Map<String, Object> cells, String column, String value) {
        if (value != null) {
            cells.put(column, value);
        }
    }
}
