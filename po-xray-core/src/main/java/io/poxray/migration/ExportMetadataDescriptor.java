package io.poxray.migration;

import io.poxray.model.ExportProvenanceInput;
import io.poxray.model.ExportProvenanceInput.ArtifactInput;
import io.poxray.model.ExportProvenanceInput.VersionObservation;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Strict reader of the provenance descriptor (format version 1): duplicate keys, trailing tokens, unknown or missing
 * fields, null entries and values outside the vocabularies are an IOException; nothing is defaulted or repaired.
 */
final class ExportMetadataDescriptor {

    static final List<String> ARTIFACT_ROLES = List.of("build", "skill");
    private static final Set<String> VERSION_ROLES = Set.of("export-label", "package-release", "bundle-version",
            "target", "design-time", "deployed", "legacy-installed");
    private static final Set<String> SOURCE_KINDS = Set.of("export", "documentation", "derived", "owner-confirmed",
            "tenant-observed");
    private static final List<String> ROOT_FIELDS = List.of("formatVersion", "artifacts", "versions");
    private static final List<String> ARTIFACT_FIELDS = List.of("role", "label", "path");
    private static final List<String> VERSION_FIELDS = List.of("role", "subject", "value", "sourceKind", "locator",
            "sourceSha256", "observedAt", "scope", "limitations");
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private final Path file;

    private ExportMetadataDescriptor(Path file) {
        this.file = file;
    }

    static ExportProvenanceInput parse(byte[] bytes, Path file) throws IOException {
        JsonNode root;
        try {
            root = MAPPER.readTree(bytes);
        } catch (JacksonException e) {
            // Parser messages may contain input tokens: name the file and the rule, not the token.
            throw new IOException("Cannot parse provenance descriptor " + file
                    + " as strict JSON (syntax error, duplicate key or trailing content)", e);
        }
        if (root == null || root.isMissingNode()) {
            throw new IOException("Empty provenance descriptor: " + file);
        }
        return new ExportMetadataDescriptor(file).input(root);
    }

    private ExportProvenanceInput input(JsonNode root) throws IOException {
        object(root, "descriptor", ROOT_FIELDS);
        JsonNode format = root.get("formatVersion");
        if (!format.isInt() || format.intValue() != 1) {
            throw invalid("unsupported formatVersion (only the integer 1 is supported)");
        }
        List<ArtifactInput> artifacts = new ArrayList<>();
        JsonNode artifactArray = array(root, "artifacts");
        for (int i = 0; i < artifactArray.size(); i++) {
            artifacts.add(artifact(artifactArray.get(i), "artifacts[" + i + "]"));
        }
        List<VersionObservation> versions = new ArrayList<>();
        JsonNode versionArray = array(root, "versions");
        for (int i = 0; i < versionArray.size(); i++) {
            versions.add(version(versionArray.get(i), "versions[" + i + "]"));
        }
        return new ExportProvenanceInput(1, artifacts, versions);
    }

    private ArtifactInput artifact(JsonNode n, String where) throws IOException {
        object(n, where, ARTIFACT_FIELDS);
        String role = string(n, "role", where);
        if (!ARTIFACT_ROLES.contains(role)) {
            throw invalid(where + ".role must be one of " + ARTIFACT_ROLES);
        }
        String label = string(n, "label", where);
        String path = string(n, "path", where);
        localPath(path, where + ".path");
        return new ArtifactInput(role, label, path);
    }

    private VersionObservation version(JsonNode n, String where) throws IOException {
        object(n, where, VERSION_FIELDS);
        String role = string(n, "role", where);
        if (!VERSION_ROLES.contains(role)) {
            throw invalid(where + ".role must be one of " + VERSION_ROLES);
        }
        String sourceKind = string(n, "sourceKind", where);
        if (!SOURCE_KINDS.contains(sourceKind)) {
            throw invalid(where + ".sourceKind must be one of " + SOURCE_KINDS);
        }
        String digest = nullableString(n, "sourceSha256", where);
        if (digest != null && !digest.matches("[0-9a-fA-F]{64}")) {
            throw invalid(where + ".sourceSha256 must be null or 64 hexadecimal characters");
        }
        String observedAt = nullableString(n, "observedAt", where);
        if (observedAt != null) {
            timestamp(observedAt, where + ".observedAt");
        }
        return new VersionObservation(role, string(n, "subject", where), string(n, "value", where), sourceKind,
                string(n, "locator", where), digest, observedAt, string(n, "scope", where),
                string(n, "limitations", where));
    }

    private void object(JsonNode n, String where, List<String> names) throws IOException {
        if (n == null || !n.isObject()) {
            throw invalid(where + " must be a JSON object");
        }
        for (String key : n.propertyNames()) {
            if (!names.contains(key)) {
                throw invalid(where + " has the unknown field \"" + key + "\"");
            }
        }
        for (String name : names) {
            if (!n.has(name)) {
                throw invalid(where + " is missing the field \"" + name + "\"");
            }
        }
    }

    private JsonNode array(JsonNode n, String field) throws IOException {
        JsonNode value = n.get(field);
        if (!value.isArray()) {
            throw invalid(field + " must be an array");
        }
        return value;
    }

    private String string(JsonNode n, String field, String where) throws IOException {
        JsonNode value = n.get(field);
        if (!value.isString() || value.stringValue().isBlank()) {
            throw invalid(where + "." + field + " must be a nonblank string");
        }
        return value.stringValue();
    }

    private String nullableString(JsonNode n, String field, String where) throws IOException {
        JsonNode value = n.get(field);
        if (value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw invalid(where + "." + field + " must be null or a string");
        }
        return value.stringValue();
    }

    /** A dossier date or offset timestamp ({@link DossierReader#timestamp}); {@code unknown} is not accepted here. */
    private void timestamp(String value, String where) throws IOException {
        if (value.equals("unknown")) {
            throw invalid(where + " must be null instead of \"unknown\"");
        }
        try {
            DossierReader.timestamp(value);
        } catch (DossierReader.InvalidInput e) {
            throw invalid(where + " must be null, an ISO date or an offset timestamp");
        }
    }

    /**
     * A syntactically local file path: no URI, no UNC or device namespace, no drive- or root-relative form, parseable
     * by the default file system. Whether it exists is decided later (unavailable artifact), never here.
     */
    private void localPath(String value, String where) throws IOException {
        if (value.replace('\\', '/').startsWith("//")) {
            throw invalid(where + " must not be a UNC or device namespace path");
        }
        if (value.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*") && !value.matches("[a-zA-Z]:[\\\\/].*")) {
            throw invalid(where + " must be a local file path, not a URI or drive-relative path");
        }
        Path path;
        try {
            path = Path.of(value);
        } catch (InvalidPathException e) {
            throw invalid(where + " is not a valid local file path");
        }
        if (path.getRoot() != null && !path.isAbsolute()) {
            throw invalid(where + " must be relative to the descriptor or absolute, not root-relative");
        }
    }

    private IOException invalid(String reason) {
        return new IOException("Invalid provenance descriptor " + file + ": " + reason);
    }
}
