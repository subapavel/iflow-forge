package io.poxray.cpi;

import io.poxray.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * Table of contents of an SAP Integration Suite package export (the zip downloaded from the tenant, or that zip
 * unpacked into a folder): {@code resources.cnt} is the Base64 of a JSON object whose array {@code resources} lists
 * every entry; the content of entry {@code <id>} is the file {@code <id>_content} next to it (a zip for a deployable
 * artifact, plain text for the change log).
 *
 * @param entries the entries of {@code resources.cnt}, in file order
 */
public record PackageExport(List<Entry> entries) {

    /** Name of the table of contents at the top level of a package export. */
    static final String RESOURCES_CNT = "resources.cnt";

    public PackageExport {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * One entry of {@code resources.cnt}.
     *
     * @param id              the resource id (32 hex digits in SAP's exports)
     * @param resourceType    {@code IFlow}, {@code ScriptCollection}, {@code ValueMapping}, {@code File}, {@code Url},
     *                        {@code ContentPackage}, {@code Configured_Custom_Tags}, {@code ChangeLog}, ...
     * @param displayName     the display name
     * @param semanticVersion the version
     * @param content         {@code <id>_content} relative to the export root (the folder, or the entry name inside
     *                        the zip), the same for both forms; null when the export has no such file
     */
    public record Entry(String id, String resourceType, String displayName, String semanticVersion, Path content) {
    }

    /** Reads {@code resources.cnt} of a package export zip or folder. */
    public static PackageExport read(Path zipOrDir) throws IOException {
        return parse(IFlowReader.contents(zipOrDir), zipOrDir.toString());
    }

    /** True when the contents (path to bytes, see {@link IFlowReader#contents}) are those of a package export. */
    static boolean isExport(Map<String, byte[]> files) {
        return files.containsKey(RESOURCES_CNT);
    }

    /** Parses {@code resources.cnt} of already loaded contents; {@code source} names them in error messages. */
    static PackageExport parse(Map<String, byte[]> files, String source) throws IOException {
        byte[] cnt = files.get(RESOURCES_CNT);
        if (cnt == null) {
            throw new IOException("Not a package export (no " + RESOURCES_CNT + "): " + source);
        }
        JsonNode root;
        try {
            root = Json.mapper().readTree(decode(cnt, source));
        } catch (JacksonException e) {
            throw new IOException("Cannot parse " + RESOURCES_CNT + " of " + source + ": " + e.getOriginalMessage(), e);
        }
        JsonNode resources = root.get("resources");
        if (resources == null || !resources.isArray()) {
            throw new IOException(RESOURCES_CNT + " of " + source + " has no array 'resources'");
        }
        List<Entry> out = new ArrayList<>();
        for (JsonNode r : resources) {
            String id = text(r, "id");
            String content = id + "_content";
            out.add(new Entry(id, text(r, "resourceType"), text(r, "displayName"), text(r, "semanticVersion"),
                    id != null && files.containsKey(content) ? Path.of(content) : null));
        }
        return new PackageExport(out);
    }

    /** Content bytes of an entry, or null when the export has none. */
    static byte[] content(Map<String, byte[]> files, Entry entry) {
        return entry.content() == null ? null : files.get(entry.content().toString());
    }

    /** Base64 (one line, or MIME with line breaks); a plain JSON object is accepted as is. */
    private static byte[] decode(byte[] cnt, String source) throws IOException {
        String text = new String(cnt, StandardCharsets.ISO_8859_1).strip();
        if (text.startsWith("{")) {
            return text.getBytes(StandardCharsets.ISO_8859_1);
        }
        try {
            return text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
                    ? Base64.getMimeDecoder().decode(text)
                    : Base64.getDecoder().decode(text);
        } catch (IllegalArgumentException e) {
            throw new IOException(RESOURCES_CNT + " of " + source + " is not Base64: " + e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String key) {
        JsonNode n = node.get(key);
        return n == null || n.isNull() ? null : n.asString();
    }
}
