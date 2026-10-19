package io.poxray.cpi;

import io.poxray.json.Json;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builder of a synthetic (non-confidential) SAP Integration Suite artifact, written as an unpacked folder shaped like
 * the real ones under {@code knowledge/sources/}: {@code META-INF/MANIFEST.MF} (CRLF, the symbolic name folded over a
 * continuation line), {@code .project}, {@code src/main/resources/scenarioflows/integrationflow/<id>.iflw} (BPMN 2.0
 * with {@code ifl:property} key/value pairs), {@code parameters.propdef}, optionally {@code parameters.prop} and
 * further resources. Plus helpers that zip a folder and build a package export ({@code resources.cnt}).
 */
final class IFlowFixture {

    /** Id of the main integration process. */
    static final String MAIN = "Process_1";
    /** Id of the start event of the main process. */
    static final String START = MAIN + "_Start";
    /** Id of the end event of the main process. */
    static final String END = MAIN + "_End";

    static final String IFLW_DIR = "src/main/resources/scenarioflows/integrationflow";

    private final String id;
    private String name = "Synthetic Flow";
    private String version = "1.0.0";
    private boolean scriptCollection;
    private boolean localProcessesFirst;
    private final Proc main = new Proc(MAIN, "Integration Process", false);
    private final List<Proc> localProcesses = new ArrayList<>();
    private final List<MessageFlow> messageFlows = new ArrayList<>();
    private final List<String[]> params = new ArrayList<>();
    private final Map<String, String> configured = new LinkedHashMap<>();
    private final Map<String, String> files = new LinkedHashMap<>();

    private IFlowFixture(String id) {
        this.id = id;
    }

    static IFlowFixture iflow(String id) {
        return new IFlowFixture(id);
    }

    IFlowFixture name(String value) {
        this.name = value;
        return this;
    }

    IFlowFixture version(String value) {
        this.version = value;
        return this;
    }

    /** No {@code .iflw} and no {@code parameters.propdef}: a ScriptCollection artifact. */
    IFlowFixture scriptCollection() {
        this.scriptCollection = true;
        return this;
    }

    /** A {@code bpmn2:callActivity} in the main process; {@code kv} are further property key/value pairs. */
    IFlowFixture step(String stepId, String stepName, String activityType, String... kv) {
        main.step(stepId, stepName, activityType, kv);
        return this;
    }

    /** A {@code bpmn2:sequenceFlow} in the main process. */
    IFlowFixture flow(String from, String to) {
        main.flow(from, to);
        return this;
    }

    /** An exception subprocess (error start event) in the main process; {@code body} adds its steps and flows. */
    IFlowFixture subprocess(String subId, String subName, Consumer<Proc> body) {
        main.subprocess(subId, subName, body);
        return this;
    }

    /** A further {@code bpmn2:process} (local process), written after the main process. */
    IFlowFixture localProcess(String processId, String processName, Consumer<Proc> body) {
        Proc p = new Proc(processId, processName, false);
        body.accept(p);
        localProcesses.add(p);
        return this;
    }

    /** Writes the local processes before the main process in document order (as some real iFlows do). */
    IFlowFixture localProcessesFirst() {
        this.localProcessesFirst = true;
        return this;
    }

    /**
     * A sender or receiver channel: an endpoint participant named {@code <flowName>_Endpoint} plus a
     * {@code bpmn2:messageFlow}.
     */
    IFlowFixture messageFlow(String flowName, String componentType, String direction) {
        return messageFlow(flowName, componentType, direction, flowName + "_Endpoint");
    }

    /**
     * A sender or receiver channel whose endpoint participant has the {@code name} attribute {@code endpointName};
     * {@code null} writes no participant element, so the flow's {@code sourceRef} (Sender) or {@code targetRef}
     * (Receiver) names nothing. {@code extraProps} are key/value pairs appended to the flow's {@code ifl:property}
     * list after {@code ComponentType}, {@code Description}, {@code ComponentNS}, {@code Name},
     * {@code componentVersion}, {@code direction}.
     */
    IFlowFixture messageFlow(String flowName, String componentType, String direction, String endpointName,
            String... extraProps) {
        messageFlows.add(new MessageFlow(flowName, componentType, direction, endpointName, extraProps));
        return this;
    }

    private record MessageFlow(String name, String componentType, String direction, String endpoint,
            String[] extraProps) {
    }

    /** A {@code <parameter>} of {@code parameters.propdef}; {@code defaultOrNull} becomes a {@code <value>} child. */
    IFlowFixture param(String paramName, String defaultOrNull) {
        params.add(new String[] {paramName, defaultOrNull});
        return this;
    }

    /** A key of {@code parameters.prop} (the configured value). */
    IFlowFixture configured(String paramName, String value) {
        configured.put(paramName, value);
        return this;
    }

    /** A file under {@code src/main/resources/}. */
    IFlowFixture resource(String relPath, String content) {
        return file("src/main/resources/" + relPath, content);
    }

    /** A file relative to the artifact root. */
    IFlowFixture file(String relPath, String content) {
        files.put(relPath, content);
        return this;
    }

    /** Writes the artifact into {@code dir} (the artifact folder itself) and returns {@code dir}. */
    Path write(Path dir) throws IOException {
        write(dir.resolve("META-INF/MANIFEST.MF"), manifest());
        write(dir.resolve(".project"), project());
        if (!scriptCollection) {
            write(dir.resolve(IFLW_DIR).resolve(id + ".iflw"), iflw());
            write(dir.resolve("src/main/resources/parameters.propdef"), propdef());
        }
        if (!configured.isEmpty()) {
            StringBuilder sb = new StringBuilder("#Store parameters\n");
            configured.forEach((k, v) -> sb.append(k).append('=').append(v).append('\n'));
            write(dir.resolve("src/main/resources/parameters.prop"), sb.toString());
        }
        for (Map.Entry<String, String> f : files.entrySet()) {
            write(dir.resolve(f.getKey()), f.getValue());
        }
        return dir;
    }

    private String manifest() {
        String symbolic = id + ";singleton:=true";
        int fold = id.length() / 2;
        return "Manifest-Version: 1.0\r\n"
                + "Bundle-Description: \r\n"
                + "Bundle-SymbolicName: " + symbolic.substring(0, fold) + "\r\n"
                + " " + symbolic.substring(fold) + "\r\n"
                + "Bundle-ManifestVersion: 2\r\n"
                + "Bundle-Name: " + name + "\r\n"
                + "Bundle-Version: " + version + "\r\n"
                + "\r\n";
    }

    private String project() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<projectDescription>\n\t<name>" + id
                + "</name>\n\t<comment></comment>\n\t<projects>\n\t</projects>\n</projectDescription>\n";
    }

    private String propdef() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><parameters>");
        for (String[] p : params) {
            sb.append("<parameter>\n    <key/>\n    <name>").append(esc(p[0])).append("</name>\n");
            if (p[1] != null) {
                sb.append("    <value>").append(esc(p[1])).append("</value>\n");
            }
            sb.append("    <type>xsd:string</type>\n    <isRequired>false</isRequired>\n    <constraint/>\n")
                    .append("    <description/>\n    <additionalMetadata>\n      <isCombobox>false</isCombobox>\n")
                    .append("    </additionalMetadata>\n  </parameter>");
        }
        return sb.append("</parameters>").toString();
    }

    private String iflw() {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<bpmn2:definitions xmlns:bpmn2=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
                + " xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\""
                + " xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\""
                + " xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\""
                + " xmlns:ifl=\"http:///com.sap.ifl.model/Ifl.xsd\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" id=\"Definitions_1\">\n");
        sb.append("    <bpmn2:collaboration id=\"Collaboration_1\" name=\"Default Collaboration\">\n");
        props(sb, "        ", "namespaceMapping", "", "httpSessionHandling", "None");
        for (int i = 0; i < messageFlows.size(); i++) {
            MessageFlow m = messageFlows.get(i);
            if (m.endpoint() == null) {
                continue;
            }
            String type = "Sender".equals(m.direction()) ? "EndpointSender" : "EndpointRecevier"; // SAP's spelling
            sb.append("        <bpmn2:participant id=\"Participant_").append(i + 1).append("\" ifl:type=\"")
                    .append(type).append("\" name=\"").append(esc(m.endpoint())).append("\">\n");
            props(sb, "            ", "enableBasicAuthentication", "false");
            sb.append("        </bpmn2:participant>\n");
        }
        List<Proc> processes = new ArrayList<>();
        processes.addAll(localProcesses);
        processes.add(localProcessesFirst ? processes.size() : 0, main);
        for (Proc p : processes) {
            sb.append("        <bpmn2:participant id=\"Participant_").append(p.id)
                    .append("\" ifl:type=\"IntegrationProcess\" name=\"").append(esc(p.name))
                    .append("\" processRef=\"").append(p.id).append("\">\n")
                    .append("            <bpmn2:extensionElements/>\n        </bpmn2:participant>\n");
        }
        for (int i = 0; i < messageFlows.size(); i++) {
            MessageFlow m = messageFlows.get(i);
            boolean sender = "Sender".equals(m.direction());
            String participant = "Participant_" + (i + 1);
            sb.append("        <bpmn2:messageFlow id=\"MessageFlow_").append(i + 1).append("\" name=\"")
                    .append(esc(m.name())).append("\" sourceRef=\"").append(sender ? participant : END)
                    .append("\" targetRef=\"").append(sender ? START : participant).append("\">\n");
            List<String> kv = new ArrayList<>(List.of("ComponentType", m.componentType(), "Description", "",
                    "ComponentNS", "sap", "Name", m.name(), "componentVersion", "1.0", "direction", m.direction()));
            kv.addAll(List.of(m.extraProps()));
            props(sb, "            ", kv.toArray(String[]::new));
            sb.append("        </bpmn2:messageFlow>\n");
        }
        sb.append("    </bpmn2:collaboration>\n");
        for (Proc p : processes) {
            p.render(sb, "    ", "process");
        }
        return sb.append("</bpmn2:definitions>\n").toString();
    }

    /** A process or subprocess body: start event, steps, end event, sequence flows, in that document order. */
    static final class Proc {
        private final String id;
        private final String name;
        private final boolean errorStart;
        private final List<String> nodes = new ArrayList<>();
        private final List<String[]> flows = new ArrayList<>();

        private Proc(String id, String name, boolean errorStart) {
            this.id = id;
            this.name = name;
            this.errorStart = errorStart;
        }

        String start() {
            return id + "_Start";
        }

        String end() {
            return id + "_End";
        }

        Proc step(String stepId, String stepName, String activityType, String... kv) {
            String[] all = new String[kv.length + 2];
            all[0] = "activityType";
            all[1] = activityType;
            System.arraycopy(kv, 0, all, 2, kv.length);
            StringBuilder sb = new StringBuilder();
            sb.append("<bpmn2:callActivity id=\"").append(esc(stepId)).append("\" name=\"").append(esc(stepName))
                    .append("\">\n");
            props(sb, "", all);
            sb.append("</bpmn2:callActivity>\n");
            nodes.add(sb.toString());
            return this;
        }

        Proc flow(String from, String to) {
            flows.add(new String[] {from, to});
            return this;
        }

        Proc subprocess(String subId, String subName, Consumer<Proc> body) {
            Proc sub = new Proc(subId, subName, true);
            body.accept(sub);
            StringBuilder sb = new StringBuilder();
            sub.render(sb, "", "subProcess");
            nodes.add(sb.toString());
            return this;
        }

        private void render(StringBuilder sb, String indent, String element) {
            String in = indent + "    ";
            sb.append(indent).append("<bpmn2:").append(element).append(" id=\"").append(esc(id)).append("\" name=\"")
                    .append(esc(name)).append("\">\n");
            if (errorStart) {
                props(sb, in, "componentVersion", "1.1", "activityType", "ErrorEventSubProcessTemplate",
                        "cmdVariantUri", "ctype::FlowstepVariant/cname::ErrorEventSubProcessTemplate/version::1.1.0");
            } else {
                props(sb, in, "transactionalHandling", "Not Required");
            }
            sb.append(in).append("<bpmn2:startEvent id=\"").append(start()).append("\" name=\"Start\">\n");
            if (errorStart) {
                sb.append(in).append("    <bpmn2:errorEventDefinition>\n");
                props(sb, in + "        ", "cmdVariantUri", "ctype::FlowstepVariant/cname::ErrorStartEvent",
                        "activityType", "StartErrorEvent");
                sb.append(in).append("    </bpmn2:errorEventDefinition>\n");
            } else {
                props(sb, in + "    ", "cmdVariantUri", "ctype::FlowstepVariant/cname::StartEvent",
                        "activityType", "StartEvent");
            }
            sb.append(in).append("</bpmn2:startEvent>\n");
            for (String node : nodes) {
                for (String line : node.split("\n")) {
                    sb.append(in).append(line).append('\n');
                }
            }
            sb.append(in).append("<bpmn2:endEvent id=\"").append(end()).append("\" name=\"End\">\n");
            props(sb, in + "    ", "componentVersion", "1.1");
            sb.append(in).append("</bpmn2:endEvent>\n");
            for (int i = 0; i < flows.size(); i++) {
                String[] f = flows.get(i);
                sb.append(in).append("<bpmn2:sequenceFlow id=\"SequenceFlow_").append(esc(id)).append('_').append(i + 1)
                        .append("\" sourceRef=\"").append(esc(f[0])).append("\" targetRef=\"").append(esc(f[1]))
                        .append("\"/>\n");
            }
            sb.append(indent).append("</bpmn2:").append(element).append(">\n");
        }
    }

    private static void props(StringBuilder sb, String indent, String... kv) {
        sb.append(indent).append("<bpmn2:extensionElements>\n");
        for (int i = 0; i < kv.length; i += 2) {
            sb.append(indent).append("    <ifl:property>\n")
                    .append(indent).append("        <key>").append(esc(kv[i])).append("</key>\n");
            if (kv[i + 1].isEmpty()) {
                sb.append(indent).append("        <value/>\n");
            } else {
                sb.append(indent).append("        <value>").append(esc(kv[i + 1])).append("</value>\n");
            }
            sb.append(indent).append("    </ifl:property>\n");
        }
        sb.append(indent).append("</bpmn2:extensionElements>\n");
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void write(Path file, String content) throws IOException {
        write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    /** Zips the regular files under {@code dir} (entries relative to {@code dir}, forward slashes, sorted). */
    static Path zip(Path dir, Path zipFile) throws IOException {
        Files.createDirectories(zipFile.toAbsolutePath().getParent());
        List<Path> regular;
        try (Stream<Path> walk = Files.walk(dir)) {
            regular = walk.filter(Files::isRegularFile).sorted().toList();
        }
        try (OutputStream out = Files.newOutputStream(zipFile); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Path f : regular) {
                zip.putNextEntry(new ZipEntry(dir.relativize(f).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(f));
                zip.closeEntry();
            }
        }
        return zipFile;
    }

    /** Synthetic {@code resources.cnt} entry id of the artifact at position {@code index} (0-based). */
    static String resourceId(int index) {
        return String.format("%032x", 0xa0L + index);
    }

    /**
     * Writes a package export folder: {@code resources.cnt} (Base64 of the JSON {@code {"resources":[...]}}) with one
     * {@code Url} and one {@code ContentPackage} entry, one entry per artifact folder (type {@code IFlow}, or
     * {@code ScriptCollection} when there is no {@code .iflw}), one {@code ChangeLog} entry; {@code <id>_content} for
     * each (the artifact zip, the change log text as given); {@code contentmetadata.md}.
     */
    static Path packageExport(Path exportDir, List<Path> artifactDirs, String changelog) throws IOException {
        Files.createDirectories(exportDir);
        List<Map<String, String>> resources = new ArrayList<>();
        String urlId = "f0000000000000000000000000000001";
        String packageId = "f0000000000000000000000000000002";
        String changelogId = "f0000000000000000000000000000003";
        resources.add(entry(urlId, "Url", "Documentation", "1.0.0", "text/plain"));
        write(exportDir.resolve(urlId + "_content"), "https://example.invalid/documentation");
        resources.add(entry(packageId, "ContentPackage", "Synthetic Package", "1.0.0", "application/json"));
        write(exportDir.resolve(packageId + "_content"), "{\"name\":\"Synthetic Package\"}");
        for (int i = 0; i < artifactDirs.size(); i++) {
            Path dir = artifactDirs.get(i);
            Map<String, String> headers = manifestHeaders(dir.resolve("META-INF/MANIFEST.MF"));
            String type = Files.isDirectory(dir.resolve(IFLW_DIR)) ? "IFlow" : "ScriptCollection";
            String rid = resourceId(i);
            resources.add(entry(rid, type, headers.get("Bundle-Name"), headers.get("Bundle-Version"), "application/zip"));
            zip(dir, exportDir.resolve(rid + "_content"));
        }
        resources.add(entry(changelogId, "ChangeLog", "Change log", "1.0.0", "text/plain"));
        write(exportDir.resolve(changelogId + "_content"), changelog);
        String json = Json.mapper().writeValueAsString(Map.of("resources", resources));
        write(exportDir.resolve("resources.cnt"), Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        write(exportDir.resolve("contentmetadata.md"),
                Base64.getEncoder().encodeToString("description=synthetic\n".getBytes(StandardCharsets.UTF_8)));
        return exportDir;
    }

    private static Map<String, String> entry(String rid, String type, String displayName, String version,
            String contentType) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", rid);
        m.put("resourceType", type);
        m.put("displayName", displayName);
        m.put("semanticVersion", version);
        m.put("contentType", contentType);
        return m;
    }

    /** Headers of a manifest with continuation lines unfolded (test helper, independent of the reader). */
    static Map<String, String> manifestHeaders(Path manifest) throws IOException {
        String text = Files.readString(manifest, StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\n ", "");
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            int colon = line.indexOf(": ");
            if (colon > 0) {
                headers.put(line.substring(0, colon), line.substring(colon + 2));
            }
        }
        return headers;
    }
}
