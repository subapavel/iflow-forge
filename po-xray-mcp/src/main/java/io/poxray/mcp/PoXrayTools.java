package io.poxray.mcp;

import io.poxray.IssueLog;
import io.poxray.doc.DocFormat;
import io.poxray.doc.Documentation;
import io.poxray.integration.ObjectCatalog;
import io.poxray.json.Json;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.ScaInventory;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import tools.jackson.databind.SerializationFeature;

/**
 * MCP tools over the po-xray readers. Integration (ESR / Integration Directory of a live PO system): find the
 * component, list its ICOs, then fetch single objects. SCA (NW BPM / EJB from an NWDI SCA file): overview, then
 * single processes / modules. Results are JSON (model: docs/OUTPUT-MODEL.md).
 */
public class PoXrayTools {

    private static final String SYSTEM = "PO system name as configured in ~/.po-xray/systems/<name>.properties";
    private static final String COMPONENT = "Exact communication component name (business system / component), "
            + "case-insensitive; * and ? wildcards allowed";
    private static final String TYPES = "messageMapping, operationMapping, serviceInterface, messageType, "
            + "dataType, externalDefinition, functionLibrary, importedArchive, ico, channel, component, "
            + "receiverDetermination, interfaceDetermination, senderAgreement, receiverAgreement, valueMapping, iflow "
            + "(or ESR / Directory type IDs such as XI_TRAFO, MAPPING, ifmmessif, RoutingRelation)";
    private static final String DIRECTION = "in = component is a receiver, out = component is the sender, both";

    @Inject
    Inventories inventories;

    @Tool(name = "find_components", description = "Find communication components (business systems / business "
            + "components) in the Integration Directory of a SAP PO system. Use it first to get the exact component "
            + "name; if nothing matches, ask the user for the component name.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String findComponents(@ToolArg(description = SYSTEM) String system,
            @ToolArg(description = "Part of the component name (substring) or * ? pattern; empty = all",
                    defaultValue = "") String name) {
        return call(() -> json(inventories.components(system, name)));
    }

    @Tool(name = "list_icos", description = "Integrated configurations (ICOs) to and/or from a component: sender, "
            + "receivers with routing conditions, interface determinations with operation mappings and target "
            + "interfaces, channels (adapter, modules) and the names of all ESR objects used. Fetch details with "
            + "get_object. The first call reads the PO system (may take a minute), later calls use the snapshot.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String listIcos(@ToolArg(description = SYSTEM) String system,
            @ToolArg(description = COMPONENT) String component,
            @ToolArg(description = DIRECTION, defaultValue = "both") String direction,
            @ToolArg(description = "Re-read objects from the PO system instead of the local snapshot",
                    defaultValue = "false") boolean refresh) {
        return call(() -> json(Views.overview(inventories.icos(system, component, direction, refresh))));
    }

    @Tool(name = "get_object", description = "One object from the ICO inventory of a component (as listed by "
            + "list_icos): ico (by sender interface), channel (attributes, modules), serviceInterface, "
            + "operationMapping, messageMapping (field logic as function terms, user-defined functions with Java "
            + "code), functionLibrary (UDF code, SAP APIs used), importedArchive (Java mapping classes, XSLT), "
            + "messageType, dataType (XSD and fields), externalDefinition.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String getObject(@ToolArg(description = SYSTEM) String system,
            @ToolArg(description = COMPONENT) String component,
            @ToolArg(description = "ico, channel, serviceInterface, operationMapping, messageMapping, "
                    + "functionLibrary, importedArchive, messageType, dataType or externalDefinition") String kind,
            @ToolArg(description = "Object name; channels as name or component|name") String name,
            @ToolArg(description = DIRECTION, defaultValue = "both") String direction) {
        return call(() -> {
            List<Object> found = Views.find(inventories.icos(system, component, direction, false), kind, name);
            if (found.isEmpty()) {
                throw new IllegalArgumentException("No " + kind + " named '" + name + "' in the ICO inventory of "
                        + component + "; see list_icos for the names");
            }
            return json(found.size() == 1 ? found.getFirst() : found);
        });
    }

    @Tool(name = "write_integration_documentation", description = "Writes the ICO inventory of a component as "
            + "documentation for people (Markdown, Jira wiki) and/or the full JSON into a directory; returns the "
            + "written file paths.",
            annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = true))
    String writeIntegrationDocumentation(@ToolArg(description = SYSTEM) String system,
            @ToolArg(description = COMPONENT) String component,
            @ToolArg(description = DIRECTION, defaultValue = "both") String direction,
            @ToolArg(description = "markdown, jira, json or all", defaultValue = "all") String format,
            @ToolArg(description = "Document language: cs or en", defaultValue = "cs") String lang,
            @ToolArg(description = "Output directory (created if missing)") String outputDir) {
        return call(() -> {
            IntegrationInventory inv = inventories.icos(system, component, direction, false);
            String base = "icos-" + component.replaceAll("[^A-Za-z0-9_.-]", "_");
            List<String> written = new ArrayList<>();
            Path dir = Path.of(outputDir);
            if (wants(format, "json")) {
                written.add(write(dir.resolve(base + ".json"), Json.write(inv)));
            }
            for (DocFormat f : DocFormat.values()) {
                if (wants(format, f == DocFormat.MARKDOWN ? "markdown" : "jira")) {
                    written.add(write(dir.resolve(base + f.extension()),
                            Documentation.render(inv, f, Locale.forLanguageTag(lang))));
                }
            }
            return json(written);
        });
    }

    @Tool(name = "search_objects", description = "Search ESR / Integration Directory objects of any type by name, "
            + "independent of ICOs. Types: " + TYPES + ". Returns type, key (name, namespace, ...), SWCV, version and "
            + "last change; read one with read_object, find its users with where_used.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String searchObjects(@ToolArg(description = SYSTEM) String system,
            @ToolArg(description = "Comma-separated types (aliases or type IDs)") String types,
            @ToolArg(description = "Name filter: plain text = contains, * ? wildcards, case-insensitive; empty = all",
                    defaultValue = "") String name,
            @ToolArg(description = "Namespace filter (same syntax), empty = any", defaultValue = "") String namespace,
            @ToolArg(description = "Maximum number of objects returned", defaultValue = "100") int limit) {
        return call(() -> json(inventories.catalog(system).search(split(types), name, namespace, limit)));
    }

    @Tool(name = "read_object", description = "One ESR / Integration Directory object of any type by exact name: "
            + "dedicated view for service interfaces, operation / message mappings (field logic, UDFs), function "
            + "libraries, message / data types, external definitions, channels and ICOs; other types (receiver / "
            + "interface determinations, agreements, value mappings, iFlows, ...) as links plus raw content XML.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String readObject(@ToolArg(description = SYSTEM) String system,
            @ToolArg(description = "Type alias or type ID, e.g. " + TYPES) String type,
            @ToolArg(description = "Exact name (case-insensitive); Directory objects by any key element or the whole"
                    + " key a|b|c, e.g. a channel as component|name") String name,
            @ToolArg(description = "Namespace if the name is not unique", defaultValue = "") String namespace) {
        return call(() -> {
            ObjectCatalog catalog = inventories.catalog(system);
            List<ObjectCatalog.Hit> hits = catalog.find(type, name, namespace);
            if (hits.isEmpty()) {
                throw new IllegalArgumentException("No " + type + " named '" + name + "'; try search_objects");
            }
            if (hits.size() > 5) {
                return json(new Ambiguous("More than 5 objects match, narrow down with namespace", hits.stream()
                        .map(ObjectCatalog.Hit::summary).toList()));
            }
            IssueLog issues = new IssueLog();
            List<Object> out = new ArrayList<>();
            for (ObjectCatalog.Hit hit : hits) {
                Object o = catalog.read(hit, issues);
                out.add(o == null ? hit.summary() : o);
            }
            return json(issues.issues().isEmpty() ? (out.size() == 1 ? out.getFirst() : out)
                    : new WithIssues(out.size() == 1 ? out.getFirst() : out, issues.issues()));
        });
    }

    @Tool(name = "where_used", description = "Objects referencing an object (exact name): e.g. operation mappings "
            + "using a message mapping, message mappings using a function library, ICOs / agreements using a channel"
            + " or operation mapping, interfaces / mappings using a message or data type. Uses the reference lists of"
            + " the PO system, the referring objects are not read.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = true))
    String whereUsed(@ToolArg(description = SYSTEM) String system,
            @ToolArg(description = "Type alias or type ID of the used object") String type,
            @ToolArg(description = "Exact name of the used object; channels as component|name") String name,
            @ToolArg(description = "Namespace if the name is not unique", defaultValue = "") String namespace) {
        return call(() -> {
            ObjectCatalog catalog = inventories.catalog(system);
            List<ObjectCatalog.Hit> targets = catalog.find(type, name, namespace);
            if (targets.isEmpty()) {
                throw new IllegalArgumentException("No " + type + " named '" + name + "'; try search_objects");
            }
            return json(catalog.whereUsed(targets));
        });
    }

    @Tool(name = "read_sca", description = "Overview of an NWDI SCA file with SAP NW BPM processes and EJB modules: "
            + "development components, processes (node / flow counts), BPM catalogs, EJB modules, issue counts. "
            + "Fetch details with get_sca_object.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = false))
    String readSca(@ToolArg(description = "Path to the .sca file") String path) {
        return call(() -> json(Views.overview(inventories.sca(path))));
    }

    @Tool(name = "get_sca_object", description = "One part of an SCA inventory: process (steps, gateways, flows "
            + "with conditions, events; mappings only listed), step (one step with its input / output mappings and "
            + "resolved functions, and its service reference incl. an ESR operation mapping it calls), processData (data objects with types, roles), catalog (interfaces, service "
            + "references, functions, data types of a BPM DC), ejbModule (beans, roles, web services, JPA), "
            + "developmentComponent, issues.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = false))
    String getScaObject(@ToolArg(description = "Path to the .sca file") String path,
            @ToolArg(description = "process, step, processData, catalog, ejbModule, developmentComponent or issues")
            String kind,
            @ToolArg(description = "Process name or id (process, processData); '<process>/<step name or id>' "
                    + "(step); DC name (catalog, ejbModule, developmentComponent); severity (error, warning, info) or "
                    + "empty (issues)", defaultValue = "") String name) {
        return call(() -> {
            List<Object> found = Views.find(inventories.sca(path), kind, name);
            if (found.isEmpty() && !"issues".equals(kind)) {
                throw new IllegalArgumentException("No " + kind + " named '" + name + "' in " + path
                        + "; see read_sca for the names");
            }
            return json("issues".equals(kind) || found.size() > 1 ? found : found.getFirst());
        });
    }

    @Tool(name = "write_sca_documentation", description = "Writes the documentation of an SCA (BPM processes, "
            + "EJB modules) for people (Markdown, Jira wiki) and/or the full JSON into a directory; returns the "
            + "written file paths.",
            annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = false, openWorldHint = false))
    String writeScaDocumentation(@ToolArg(description = "Path to the .sca file") String path,
            @ToolArg(description = "markdown, jira, json or all", defaultValue = "all") String format,
            @ToolArg(description = "Document language: cs or en", defaultValue = "cs") String lang,
            @ToolArg(description = "Output directory (created if missing)") String outputDir) {
        return call(() -> {
            ScaInventory inv = inventories.sca(path);
            String file = Path.of(path).getFileName().toString();
            String base = file.toLowerCase(Locale.ROOT).endsWith(".sca") ? file.substring(0, file.length() - 4) : file;
            List<String> written = new ArrayList<>();
            Path dir = Path.of(outputDir);
            if (wants(format, "json")) {
                written.add(write(dir.resolve(base + ".json"), Json.write(inv)));
            }
            for (DocFormat f : DocFormat.values()) {
                if (wants(format, f == DocFormat.MARKDOWN ? "markdown" : "jira")) {
                    written.add(write(dir.resolve(base + f.extension()),
                            Documentation.render(inv, f, Locale.forLanguageTag(lang))));
                }
            }
            return json(written);
        });
    }

    // ------------------------------------------------------------------------------------------ helpers

    record Ambiguous(String message, Object candidates) {
    }

    record WithIssues(Object object, Object issues) {
    }

    private static List<String> split(String types) {
        return java.util.Arrays.stream(types.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList();
    }

    /** Compact JSON for tool results (indentation would cost the agent tokens). */
    private static String json(Object value) {
        return Json.mapper().writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(value);
    }

    private static boolean wants(String format, String f) {
        String v = format == null || format.isBlank() ? "all" : format.strip().toLowerCase(Locale.ROOT);
        return v.equals("all") || v.equals(f);
    }

    private static String write(Path file, String content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toAbsolutePath().toString();
    }

    /** Errors become tool errors with a readable message (no stack trace for the agent). */
    private static String call(Callable<String> body) {
        try {
            return body.call();
        } catch (ToolCallException e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new ToolCallException(e.getMessage(), e);
        } catch (Exception e) {
            throw new ToolCallException(e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
