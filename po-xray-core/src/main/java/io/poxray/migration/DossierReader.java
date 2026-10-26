package io.poxray.migration;

import io.poxray.migration.DossierReport.Diagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict v1 input boundary. The original tree is retained for lossless baseline comparison. */
final class DossierReader {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private DossierReader() {
    }

    record Document(JsonNode root, int formatVersion, String migrationId, String sha256,
            List<JsonNode> events, List<Diagnostic> diagnostics) {
    }

    static Document read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        JsonNode root;
        try {
            root = MAPPER.readTree(bytes);
        } catch (JacksonException e) {
            // Parser messages may contain input tokens: never forward them to the report or CLI.
            throw new IOException("Cannot parse dossier JSON: " + file, e);
        }
        if (root == null || root.isMissingNode()) {
            throw new IOException("Empty dossier JSON: " + file);
        }
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<JsonNode> events = new ArrayList<>();
        int version = 0;
        String migrationId = null;
        try {
            fields(root, Set.of("formatVersion", "migrationId", "events"));
            JsonNode format = root.get("formatVersion");
            require(format.isIntegralNumber() && format.canConvertToInt(), "formatVersion must be an integer");
            version = format.intValue();
            if (version != 1) {
                throw new InvalidInput("UNSUPPORTED_FORMAT", "Only dossier formatVersion 1 is supported");
            }
            migrationId = string(root, "migrationId");
            require(migrationId.matches("M-[1-9][0-9]*"), "migrationId must be M- followed by a positive integer");
            require(root.get("events").isArray(), "events must be an array");
        } catch (InvalidInput e) {
            diagnostics.add(new Diagnostic("error", e.code, null, e.getMessage()));
            return new Document(root, version, migrationId, sha256(bytes), List.of(), List.copyOf(diagnostics));
        }
        Set<String> ids = new HashSet<>();
        for (JsonNode event : root.get("events")) {
            String id = event.path("id").isString() ? event.path("id").stringValue() : null;
            if (id != null && !ids.add(id)) {
                diagnostics.add(new Diagnostic("error", "DUPLICATE_EVENT_ID", id, "Event ID is not unique"));
                continue;
            }
            try {
                event(event);
                events.add(event);
            } catch (InvalidInput e) {
                diagnostics.add(new Diagnostic("error", e.code, id, e.getMessage()));
            }
        }
        return new Document(root, version, migrationId, sha256(bytes), List.copyOf(events), List.copyOf(diagnostics));
    }

    private static void event(JsonNode event) {
        String type = string(event, "type");
        List<String> extra = switch (type) {
            case "propose" -> List.of("text", "scope");
            case "approve" -> List.of("decision", "scope");
            case "reject", "defer", "approval-unresolved" -> List.of("decision");
            case "supersede" -> List.of("decision", "replaces", "sections");
            case "risk-accepted" -> List.of("text", "sections");
            case "derived-document" -> List.of("documentId", "path", "required", "consumes");
            case "next-action" -> List.of("text");
            default -> throw new InvalidInput("INVALID_DOCUMENT", "Unknown event type");
        };
        Set<String> allowed = new HashSet<>(Set.of("id", "type", "at", "source"));
        allowed.addAll(extra);
        fields(event, allowed);
        string(event, "id");
        string(event, "source");
        timestamp(string(event, "at"));
        // Validate the complete event before it can affect the projection.
        for (String field : extra) {
            switch (field) {
                case "scope" -> scopes(event.get(field));
                case "sections" -> sections(event.get(field));
                case "consumes" -> dependencies(event.get(field));
                case "required" -> require(event.get(field).isBoolean(), "required must be a boolean");
                case "path" -> localPath(string(event, field));
                default -> string(event, field);
            }
        }
    }

    private static void fields(JsonNode node, Set<String> names) {
        require(node.isObject(), "Expected a JSON object");
        require(node.properties().stream().allMatch(entry -> names.contains(entry.getKey()))
                && names.stream().allMatch(node::has), "Object has unknown or missing fields");
    }

    private static String string(JsonNode node, String field) {
        JsonNode value = node.get(field);
        require(value != null && value.isString() && !value.stringValue().isBlank(), field + " must be a nonblank string");
        return value.stringValue();
    }

    private static void scopes(JsonNode array) {
        nonemptyArray(array, "scope");
        Set<String> sections = new HashSet<>();
        for (JsonNode scope : array) {
            fields(scope, Set.of("sectionId", "revision", "path", "sha256"));
            if (!sections.add(string(scope, "sectionId"))) {
                throw new InvalidInput("INVALID_SCOPE", "A scope lists the same section more than once");
            }
            string(scope, "revision");
            localPath(string(scope, "path"));
            digest(string(scope, "sha256"));
        }
    }

    private static void dependencies(JsonNode array) {
        nonemptyArray(array, "consumes");
        for (JsonNode dependency : array) {
            fields(dependency, Set.of("decisionId", "sectionId", "revision", "sha256"));
            string(dependency, "decisionId");
            string(dependency, "sectionId");
            string(dependency, "revision");
            digest(string(dependency, "sha256"));
        }
    }

    private static void sections(JsonNode array) {
        nonemptyArray(array, "sections");
        Set<String> names = new HashSet<>();
        for (JsonNode section : array) {
            require(section.isString() && !section.stringValue().isBlank(), "sections must contain nonblank strings");
            require(names.add(section.stringValue()), "sections must not repeat a section");
        }
    }

    private static void nonemptyArray(JsonNode array, String name) {
        require(array.isArray() && !array.isEmpty(), name + " must be a nonempty array");
    }

    private static void digest(String value) {
        require(value.matches("[0-9a-fA-F]{64}"), "sha256 must contain 64 hexadecimal characters");
    }

    private static void localPath(String value) {
        require(!value.matches("[a-zA-Z][a-zA-Z0-9+.-]*:.*") || value.matches("[a-zA-Z]:[\\\\/].*"),
                "path must be a local file path, not a URI");
        try {
            Path.of(value);
        } catch (InvalidPathException e) {
            throw new InvalidInput("INVALID_DOCUMENT", "path must be a local file path");
        }
    }

    private static void timestamp(String value) {
        if (value.equals("unknown")) {
            return;
        }
        try {
            if (value.contains("T") || value.contains("t")) {
                OffsetDateTime.parse(value);
            } else {
                LocalDate.parse(value);
            }
        } catch (DateTimeParseException e) {
            throw new InvalidInput("INVALID_DOCUMENT", "at must be an ISO date, an offset timestamp, or unknown");
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidInput("INVALID_DOCUMENT", message);
        }
    }

    private static final class InvalidInput extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;

        InvalidInput(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
