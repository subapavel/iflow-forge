package io.poxray.cpi;

import io.poxray.sca.XmlDocs;
import io.poxray.sca.ZipArchive;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads SAP Integration Suite artifacts (integration flows, script collections, value mappings) into {@link IFlow}
 * records. An artifact is an unpacked folder or its zip; both are loaded into the same path-to-bytes map, so both
 * forms yield equal records.
 *
 * <p>Step order: the main {@code bpmn2:process} (the one holding the target of the first sender message flow) is
 * walked depth-first from each start event along the outgoing sequence flows (document order), every node once. A
 * subprocess reached by a flow lists its own steps at depth + 1 right after it; a step whose property
 * {@code processId} names a process of the file ({@code ProcessCallElement}, {@code IdempotentProcessCall}, ...)
 * lists that local process at depth + 1 right after it (each process inlined once).
 * After the flow walk of a process its event subprocesses (subprocesses without incoming flow) follow at the same
 * depth, their steps at depth + 1. Processes neither main nor inlined follow at depth 0, in document order. Steps no
 * walk reached come last with {@code reached = false}.
 */
public final class IFlowReader {

    static final String BPMN = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    static final String IFL = "http:///com.sap.ifl.model/Ifl.xsd";

    static final String MANIFEST = "META-INF/MANIFEST.MF";
    static final String RESOURCES = "src/main/resources/";
    static final String IFLW_DIR = RESOURCES + "scenarioflows/integrationflow/";
    static final String PROPDEF = RESOURCES + "parameters.propdef";
    static final String PROP = RESOURCES + "parameters.prop";

    /** Resource types of a package export that are deployable artifacts with a {@code MANIFEST.MF}. */
    static final Set<String> ARTIFACT_TYPES = Set.of("IFlow", "ScriptCollection", "ValueMapping");

    private static final Set<String> STEP_ELEMENTS =
            Set.of("callActivity", "serviceTask", "exclusiveGateway", "parallelGateway", "subProcess");

    private IFlowReader() {
    }

    /** Reads one artifact: an unpacked folder (with {@code META-INF/MANIFEST.MF}) or its zip. */
    public static IFlow read(Path iflowZipOrDir) throws IOException {
        if (Files.isDirectory(iflowZipOrDir) && !Files.isRegularFile(iflowZipOrDir.resolve(MANIFEST))) {
            throw new IOException("Not an artifact folder (no " + MANIFEST + "): " + iflowZipOrDir);
        }
        return read(contents(iflowZipOrDir), iflowZipOrDir.toString());
    }

    /**
     * Reads every artifact found at {@code path}, sorted by id: an artifact folder or zip (one), a package export
     * folder or zip with {@code resources.cnt} (its {@code IFlow}, {@code ScriptCollection} and {@code ValueMapping}
     * entries), or a folder of any of these (searched recursively through plain subfolders, symbolic links not
     * followed).
     */
    public static List<IFlow> readAll(Path path) throws IOException {
        List<IFlow> out = new ArrayList<>();
        collect(path, out);
        out.sort(Comparator.comparing(IFlow::id, Comparator.nullsFirst(Comparator.naturalOrder())));
        return out;
    }

    private static void collect(Path path, List<IFlow> out) throws IOException {
        if (Files.isDirectory(path)) {
            if (Files.isRegularFile(path.resolve(MANIFEST))) {
                out.add(read(path));
            } else if (Files.isRegularFile(path.resolve(PackageExport.RESOURCES_CNT))) {
                out.addAll(readExport(contents(path), path.toString()));
            } else {
                List<Path> children;
                try (Stream<Path> list = Files.list(path)) {
                    children = list.sorted().toList();
                }
                for (Path child : children) {
                    if (Files.isDirectory(child) && !Files.isSymbolicLink(child)
                            || Files.isRegularFile(child) && isZipName(child)) {
                        collect(child, out);
                    }
                }
            }
        } else if (Files.isRegularFile(path)) {
            Map<String, byte[]> files = contents(path);
            if (PackageExport.isExport(files)) {
                out.addAll(readExport(files, path.toString()));
            } else {
                out.add(read(files, path.toString()));
            }
        } else {
            throw new IOException("No such file or folder: " + path);
        }
    }

    private static boolean isZipName(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    private static List<IFlow> readExport(Map<String, byte[]> files, String source) throws IOException {
        List<IFlow> out = new ArrayList<>();
        for (PackageExport.Entry e : PackageExport.parse(files, source).entries()) {
            if (ARTIFACT_TYPES.contains(e.resourceType())) {
                byte[] zip = PackageExport.content(files, e);
                if (zip == null) {
                    throw new IOException("Package export " + source + " has no content for " + e.resourceType()
                            + " '" + e.displayName() + "' (" + e.id() + "_content)");
                }
                String name = source + "!" + e.content();
                out.add(read(unzip(zip, name), name));
            }
        }
        return out;
    }

    /**
     * The regular files of a folder or a zip as relative path (forward slashes) to content, sorted by path. A zip's
     * directory entries are skipped.
     */
    static Map<String, byte[]> contents(Path zipOrDir) throws IOException {
        Map<String, byte[]> out = new TreeMap<>();
        if (Files.isDirectory(zipOrDir)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(zipOrDir)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            for (Path f : files) {
                out.put(zipOrDir.relativize(f).toString().replace('\\', '/'), Files.readAllBytes(f));
            }
            return out;
        }
        if (!Files.isRegularFile(zipOrDir)) {
            throw new IOException("No such file or folder: " + zipOrDir);
        }
        try {
            out.putAll(ZipArchive.open(zipOrDir).entries());
        } catch (UncheckedIOException e) {
            throw new IOException(e.getMessage() + ": " + e.getCause().getMessage(), e.getCause());
        }
        return out;
    }

    /** The entries of a zip held in memory, as {@link #contents}. */
    static Map<String, byte[]> unzip(byte[] zip, String name) throws IOException {
        try {
            return new TreeMap<>(ZipArchive.of(zip, name).entries());
        } catch (UncheckedIOException e) {
            throw new IOException(e.getMessage() + ": " + e.getCause().getMessage(), e.getCause());
        }
    }

    /** Reads one artifact from its loaded contents; {@code source} names it in error messages. */
    static IFlow read(Map<String, byte[]> files, String source) throws IOException {
        byte[] manifest = files.get(MANIFEST);
        if (manifest == null) {
            throw new IOException("No " + MANIFEST + " in " + source);
        }
        Map<String, String> headers = manifestHeaders(manifest);
        String symbolic = headers.get("Bundle-SymbolicName");
        String id = symbolic == null ? null : symbolic.split(";", 2)[0].strip();

        String iflwPath = null;
        for (String p : files.keySet()) {
            if (p.startsWith(IFLW_DIR) && p.endsWith(".iflw")) {
                if (iflwPath != null) {
                    throw new IOException("More than one .iflw in " + source + ": " + iflwPath + ", " + p);
                }
                iflwPath = p;
            }
        }
        List<Participant> participants = List.of();
        List<Step> steps = List.of();
        if (iflwPath != null) {
            Document doc = XmlDocs.parse(files.get(iflwPath), source + "!" + iflwPath);
            participants = participants(doc);
            steps = new Walk(doc).steps();
        }
        List<Param> parameters = parameters(files, source);
        List<String> resources = new ArrayList<>();
        for (String p : files.keySet()) {
            if (p.startsWith(RESOURCES) && !p.equals(iflwPath) && !p.equals(PROPDEF) && !p.equals(PROP)) {
                resources.add(p.substring(RESOURCES.length()));
            }
        }
        resources.sort(Comparator.naturalOrder());
        return new IFlow(id, headers.get("Bundle-Name"), headers.get("Bundle-Version"), participants, steps,
                parameters, resources);
    }

    /** Main headers of a manifest: continuation lines (line break + one space) unfolded, CRLF or LF. */
    static Map<String, String> manifestHeaders(byte[] manifest) {
        String unfolded = new String(manifest, StandardCharsets.UTF_8)
                .replace("\r\n", "\n").replace('\r', '\n').replace("\n ", "");
        Map<String, String> headers = new LinkedHashMap<>();
        for (String line : unfolded.split("\n")) {
            if (line.isEmpty()) {
                break; // end of the main section
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                String value = line.substring(colon + 1);
                headers.putIfAbsent(line.substring(0, colon), value.startsWith(" ") ? value.substring(1) : value);
            }
        }
        return headers;
    }

    /** One per message flow; the endpoint is the participant at the flow's sourceRef (sender) or targetRef (receiver). */
    private static List<Participant> participants(Document doc) {
        Map<String, String> endpointNames = new HashMap<>();
        NodeList endpoints = doc.getElementsByTagNameNS(BPMN, "participant");
        for (int i = 0; i < endpoints.getLength(); i++) {
            Element e = (Element) endpoints.item(i);
            endpointNames.putIfAbsent(e.getAttribute("id"), e.getAttribute("name"));
        }
        List<Participant> out = new ArrayList<>();
        NodeList flows = doc.getElementsByTagNameNS(BPMN, "messageFlow");
        for (int i = 0; i < flows.getLength(); i++) {
            Element flow = (Element) flows.item(i);
            Map<String, String> p = properties(flow);
            String direction = p.getOrDefault("direction", "");
            String ref = switch (direction) {
                case "Sender" -> flow.getAttribute("sourceRef");
                case "Receiver" -> flow.getAttribute("targetRef");
                default -> "";
            };
            out.add(new Participant(p.getOrDefault("Name", ""), p.getOrDefault("ComponentType", ""), direction,
                    endpointNames.getOrDefault(ref, ""), p));
        }
        return out;
    }

    /** The {@code ifl:property} key/value pairs of the element's own {@code bpmn2:extensionElements}. */
    static Map<String, String> properties(Element element) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Element ext : children(element, BPMN, "extensionElements")) {
            for (Element prop : children(ext, IFL, "property")) {
                Element key = XmlDocs.child(prop, "key");
                Element value = XmlDocs.child(prop, "value");
                if (key != null) {
                    out.putIfAbsent(key.getTextContent(), value == null ? "" : value.getTextContent());
                }
            }
        }
        return out;
    }

    private static List<Element> children(Element parent, String ns, String localName) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && ns.equals(e.getNamespaceURI()) && localName.equals(e.getLocalName())) {
                out.add(e);
            }
        }
        return out;
    }

    private static List<Param> parameters(Map<String, byte[]> files, String source) throws IOException {
        byte[] propdef = files.get(PROPDEF);
        if (propdef == null) {
            return List.of();
        }
        Set<String> configured = configuredKeys(files.get(PROP), source);
        List<Param> out = new ArrayList<>();
        Element root = XmlDocs.parse(propdef, source + "!" + PROPDEF).getDocumentElement();
        for (Element p : XmlDocs.children(root, "parameter")) {
            String name = XmlDocs.childText(p, "name");
            name = name == null ? "" : name;
            Element value = XmlDocs.child(p, "value");
            out.add(new Param(name, value == null ? "" : value.getTextContent(), configured.contains(name)));
        }
        return out;
    }

    /** Keys of {@code parameters.prop}; the configured values are read and dropped, never kept. */
    private static Set<String> configuredKeys(byte[] prop, String source) throws IOException {
        if (prop == null) {
            return Set.of();
        }
        Properties p = new Properties();
        try (Reader r = new InputStreamReader(new ByteArrayInputStream(prop), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot read " + PROP + " of " + source + ": " + e.getMessage(), e);
        }
        return Set.copyOf(p.stringPropertyNames());
    }

    /** The step walk over one parsed {@code .iflw} (rules in the class comment). */
    private static final class Walk {

        private final List<Element> processes = new ArrayList<>();
        private final List<Element> stepElements = new ArrayList<>();
        private final Map<String, Element> byId = new HashMap<>();
        private final Map<String, List<String>> outgoing = new HashMap<>();
        private final Set<String> hasIncoming = new HashSet<>();
        private final Set<Element> walkedProcesses = new HashSet<>();
        private final Set<Element> visited = new HashSet<>();
        private final Set<Element> emitted = new HashSet<>();
        private final Map<Element, Integer> containerDepth = new HashMap<>();
        private final List<Step> steps = new ArrayList<>();
        private final Document doc;

        Walk(Document doc) {
            this.doc = doc;
            NodeList all = doc.getElementsByTagNameNS(BPMN, "*");
            for (int i = 0; i < all.getLength(); i++) {
                Element e = (Element) all.item(i);
                String id = e.getAttribute("id");
                if (!id.isEmpty()) {
                    byId.putIfAbsent(id, e);
                }
                switch (e.getLocalName()) {
                    case "process" -> processes.add(e);
                    case "sequenceFlow" -> {
                        outgoing.computeIfAbsent(e.getAttribute("sourceRef"), k -> new ArrayList<>())
                                .add(e.getAttribute("targetRef"));
                        hasIncoming.add(e.getAttribute("targetRef"));
                    }
                    default -> {
                        if (STEP_ELEMENTS.contains(e.getLocalName())) {
                            stepElements.add(e);
                        }
                    }
                }
            }
        }

        List<Step> steps() {
            Element main = mainProcess();
            if (main != null) {
                walkedProcesses.add(main);
                walkContainer(main, 0, true);
            }
            for (Element p : processes) {
                if (walkedProcesses.add(p)) {
                    walkContainer(p, 0, true);
                }
            }
            for (Element s : stepElements) {
                if (!emitted.contains(s) && !hasIncoming.contains(s.getAttribute("id"))) {
                    flowWalk(s, depthOf(container(s)), false);
                }
            }
            for (Element s : stepElements) {
                if (!emitted.contains(s)) {
                    flowWalk(s, depthOf(container(s)), false);
                }
            }
            return steps;
        }

        /** R4: the process holding the target of a sender flow, else the source of a receiver flow, else the first. */
        private Element mainProcess() {
            NodeList flows = doc.getElementsByTagNameNS(BPMN, "messageFlow");
            for (String[] rule : new String[][] {{"Sender", "targetRef"}, {"Receiver", "sourceRef"}}) {
                for (int i = 0; i < flows.getLength(); i++) {
                    Element flow = (Element) flows.item(i);
                    if (rule[0].equals(properties(flow).get("direction"))) {
                        Element process = enclosingProcess(byId.get(flow.getAttribute(rule[1])));
                        if (process != null) {
                            return process;
                        }
                    }
                }
            }
            return processes.isEmpty() ? null : processes.getFirst();
        }

        private static Element enclosingProcess(Element e) {
            for (Node n = e; n != null; n = n.getParentNode()) {
                if (n instanceof Element el && BPMN.equals(el.getNamespaceURI())
                        && "process".equals(el.getLocalName())) {
                    return el;
                }
            }
            return null;
        }

        /** Flow walk from the container's start events, then its event subprocesses. */
        private void walkContainer(Element container, int depth, boolean reached) {
            containerDepth.put(container, depth);
            for (Element start : children(container, BPMN, "startEvent")) {
                flowWalk(start, depth, reached);
            }
            for (Element sub : children(container, BPMN, "subProcess")) {
                if (!emitted.contains(sub) && !hasIncoming.contains(sub.getAttribute("id"))) {
                    visited.add(sub);
                    emit(sub, depth, reached);
                }
            }
        }

        /** Depth-first walk along outgoing sequence flows in document order; every node at most once. */
        private void flowWalk(Element from, int depth, boolean reached) {
            Deque<Element> stack = new ArrayDeque<>();
            stack.push(from);
            while (!stack.isEmpty()) {
                Element node = stack.pop();
                if (!visited.add(node)) {
                    continue;
                }
                if (isStep(node)) {
                    emit(node, depth, reached);
                }
                List<String> targets = outgoing.getOrDefault(node.getAttribute("id"), List.of());
                for (int i = targets.size() - 1; i >= 0; i--) {
                    Element target = byId.get(targets.get(i));
                    if (target != null && !visited.contains(target)) {
                        stack.push(target);
                    }
                }
            }
        }

        private void emit(Element step, int depth, boolean reached) {
            emitted.add(step);
            Map<String, String> props = properties(step);
            String activityType = props.getOrDefault("activityType", "");
            steps.add(new Step(step.getAttribute("id"), step.getAttribute("name"), activityType, depth, props,
                    reached));
            if ("subProcess".equals(step.getLocalName())) {
                walkContainer(step, depth + 1, reached);
            }
            String processId = props.get("processId");
            if (processId != null) {
                Element called = byId.get(processId);
                if (called != null && processes.contains(called) && walkedProcesses.add(called)) {
                    walkContainer(called, depth + 1, reached);
                }
            }
        }

        private static boolean isStep(Element e) {
            return BPMN.equals(e.getNamespaceURI()) && STEP_ELEMENTS.contains(e.getLocalName());
        }

        /** The nearest enclosing {@code process} or {@code subProcess}. */
        private static Element container(Element e) {
            for (Node n = e.getParentNode(); n != null; n = n.getParentNode()) {
                if (n instanceof Element el && BPMN.equals(el.getNamespaceURI())
                        && ("process".equals(el.getLocalName()) || "subProcess".equals(el.getLocalName()))) {
                    return el;
                }
            }
            return null;
        }

        private int depthOf(Element container) {
            if (container == null) {
                return 0;
            }
            Integer d = containerDepth.get(container);
            if (d != null) {
                return d;
            }
            return "process".equals(container.getLocalName()) ? 0 : depthOf(container(container)) + 1;
        }
    }
}
