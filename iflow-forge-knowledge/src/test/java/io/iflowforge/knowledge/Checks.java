package io.iflowforge.knowledge;

import io.poxray.cpi.IFlow;
import io.poxray.cpi.IFlowReader;
import io.poxray.cpi.Step;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Consistency checks of the pipeline knowledge base ({@code knowledge/pipeline}). Every check returns its failure
 * messages, empty when it passes; page names in messages are relative to the pipeline folder with {@code /}.
 */
final class Checks {

    /** H2 headings of an iFlow or template page, in order. */
    static final List<String> FLOW_HEADINGS = List.of("Purpose", "Adapters", "Steps", "Headers and properties",
            "Partner directory", "Scripts and resources", "Externalized parameters", "Error handling",
            "Extension points", "Version notes");
    /** H2 headings of a script page, in order. */
    static final List<String> SCRIPT_HEADINGS = List.of("Purpose", "Inputs", "Outputs", "Gotchas", "Used by");
    /** The pages loaded with the skill. */
    static final List<String> LOADED_SET = List.of("SKILL.md", "overview.md", "contracts.md",
            "scenario-contribution.md");
    static final int LOADED_LIMIT = 5000;
    static final int PAGE_LIMIT = 1500;
    static final String STUB_MARKER = "_Stub,";
    static final String ID_PREFIX = "com.sap.integration.cloud.pipeline.";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{tenant\\.[a-z0-9_.]+\\}\\}");
    private static final Pattern BACKTICKED = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final int MIN_FORBIDDEN_LENGTH = 4;
    private static final String MANIFEST = "META-INF/MANIFEST.MF";
    private static final String RESOURCES = "src/main/resources";
    /** The {@code Name} cell of a content-modifier table row. */
    private static final Pattern NAME_CELL = Pattern.compile("<cell id=['\"]Name['\"]>([^<]*)</cell>");
    /** A header or property call whose first argument is a plain single- or double-quoted string literal. */
    private static final Pattern CONTRACT_CALL = Pattern.compile("(?:"
            + "\\b(?:setHeader|getHeader|setProperty|getProperty)"
            + "|\\bgetHeaders\\(\\)\\.get|\\bgetProperties\\(\\)\\.get"
            + "|\\bheaders\\.get|\\bproperties\\.get)"
            + "\\(\\s*(?:\"([^\"\\\\]*)\"|'([^'\\\\]*)')\\s*[,)]");

    private Checks() {
    }

    // --- C1 coverage --------------------------------------------------------------------------------------------

    /**
     * Every artifact of a {@code generic-*} package has {@code iflows/<id>.md}, every artifact of the
     * {@code templates} package has {@code templates/<id>.md}, every {@code .groovy} / {@code .xsl} resource has
     * {@code scripts/<file-stem>.md}. Messages {@code missing page: <path>}, sorted.
     */
    static List<String> coverage(Path kb, Path sources) throws IOException {
        Set<String> required = new TreeSet<>();
        List<String> messages = new ArrayList<>();
        for (Path pkg : packages(sources)) {
            String folder = pkg.getFileName().toString();
            for (IFlow flow : IFlowReader.readAll(pkg)) {
                if (folder.startsWith("generic-") || folder.equals("templates")) {
                    if (flow.id() == null || flow.id().isBlank()) {
                        messages.add("artifact without id in package " + folder);
                    } else {
                        required.add((folder.equals("templates") ? "templates/" : "iflows/") + flow.id() + ".md");
                    }
                }
                for (String resource : flow.resources()) {
                    String file = basename(resource);
                    if (isScriptName(file)) {
                        required.add("scripts/" + file.substring(0, file.lastIndexOf('.')) + ".md");
                    }
                }
            }
        }
        for (String page : required) {
            if (!Files.isRegularFile(kb.resolve(page))) {
                messages.add("missing page: " + page);
            }
        }
        return messages;
    }

    // --- C2 headings --------------------------------------------------------------------------------------------

    /**
     * The H2 headings of every page under {@code iflows/} and {@code templates/} are exactly {@link #FLOW_HEADINGS},
     * under {@code scripts/} exactly {@link #SCRIPT_HEADINGS}, in order; stub pages are skipped. One message per
     * page, naming its first deviation.
     */
    static List<String> headings(Path kb) throws IOException {
        List<String> messages = new ArrayList<>();
        for (String folder : List.of("iflows", "templates", "scripts")) {
            List<String> contract = folder.equals("scripts") ? SCRIPT_HEADINGS : FLOW_HEADINGS;
            for (Path page : KnowledgeBase.markdown(kb.resolve(folder))) {
                String text = read(page);
                if (isStub(text)) {
                    continue;
                }
                List<String> found = text.lines().filter(l -> l.startsWith("## ")).map(l -> l.substring(3).strip())
                        .toList();
                String deviation = firstDeviation(contract, found);
                if (deviation != null) {
                    messages.add(KnowledgeBase.relative(kb, page) + ": " + deviation);
                }
            }
        }
        return messages;
    }

    private static String firstDeviation(List<String> contract, List<String> found) {
        for (int i = 0; i < Math.max(contract.size(), found.size()); i++) {
            if (i >= found.size()) {
                return "missing \"" + contract.get(i) + "\"";
            }
            if (i >= contract.size()) {
                return "unexpected \"" + found.get(i) + "\" at position " + (i + 1);
            }
            if (!contract.get(i).equals(found.get(i))) {
                return "expected \"" + contract.get(i) + "\" at position " + (i + 1) + ", found \"" + found.get(i)
                        + "\"";
            }
        }
        return null;
    }

    // --- C3 placeholders ----------------------------------------------------------------------------------------

    /**
     * The {@code {{tenant.*}}} tokens used in the pages (every {@code *.md} except {@code naming.md}) equal the
     * backticked tokens declared in {@code naming.md}. Messages {@code undeclared placeholder: X (in <page>)} and
     * {@code unused placeholder: Y}, sorted.
     */
    static List<String> placeholders(Path kb) throws IOException {
        Set<String> declared = new TreeSet<>();
        Path naming = kb.resolve("naming.md");
        if (Files.isRegularFile(naming)) {
            for (String token : backticked(read(naming))) {
                if (PLACEHOLDER.matcher(token).matches()) {
                    declared.add(token);
                }
            }
        }
        Set<String> used = new HashSet<>();
        Set<String> undeclared = new TreeSet<>();
        for (Path page : KnowledgeBase.markdown(kb)) {
            if (page.equals(naming)) {
                continue;
            }
            Matcher m = PLACEHOLDER.matcher(read(page));
            while (m.find()) {
                used.add(m.group());
                if (!declared.contains(m.group())) {
                    undeclared.add("undeclared placeholder: " + m.group() + " (in " + KnowledgeBase.relative(kb, page)
                            + ")");
                }
            }
        }
        List<String> messages = new ArrayList<>(undeclared);
        for (String token : declared) {
            if (!used.contains(token)) {
                messages.add("unused placeholder: " + token);
            }
        }
        return messages;
    }

    // --- C4 names -----------------------------------------------------------------------------------------------

    /**
     * Every backticked token starting with {@link #ID_PREFIX} is an artifact id of {@code sources}; every backticked
     * token ending in {@code .groovy} or {@code .xsl} has the file name (after any path prefix) of a resource of
     * some artifact. Messages {@code unknown name: <token> (in <page>)}, sorted.
     */
    static List<String> names(Path kb, Path sources) throws IOException {
        Set<String> ids = new HashSet<>();
        Set<String> resources = new HashSet<>();
        for (Path pkg : packages(sources)) {
            for (IFlow flow : IFlowReader.readAll(pkg)) {
                if (flow.id() != null) {
                    ids.add(flow.id());
                }
                for (String resource : flow.resources()) {
                    resources.add(basename(resource));
                }
            }
        }
        Set<String> messages = new TreeSet<>();
        for (Path page : KnowledgeBase.markdown(kb)) {
            for (String token : backticked(read(page))) {
                boolean unknown = token.startsWith(ID_PREFIX) && !ids.contains(token)
                        || isScriptName(token) && !resources.contains(basename(token));
                if (unknown) {
                    messages.add("unknown name: " + token + " (in " + KnowledgeBase.relative(kb, page) + ")");
                }
            }
        }
        return new ArrayList<>(messages);
    }

    // --- C5 budget ----------------------------------------------------------------------------------------------

    /**
     * The loaded set ({@link #LOADED_SET}) has at most {@link #LOADED_LIMIT} words, every {@code *.md} at most
     * {@link #PAGE_LIMIT}; a word is a whitespace-separated token, stub pages count 0. Messages
     * {@code loaded set: N words (limit 5000)} first, then {@code <page>: N words (limit 1500)} by page.
     */
    static List<String> budget(Path kb) throws IOException {
        List<String> messages = new ArrayList<>();
        int loaded = 0;
        for (String name : LOADED_SET) {
            Path page = kb.resolve(name);
            if (Files.isRegularFile(page)) {
                loaded += words(read(page));
            }
        }
        if (loaded > LOADED_LIMIT) {
            messages.add("loaded set: " + loaded + " words (limit " + LOADED_LIMIT + ")");
        }
        for (Path page : KnowledgeBase.markdown(kb)) {
            int n = words(read(page));
            if (n > PAGE_LIMIT) {
                messages.add(KnowledgeBase.relative(kb, page) + ": " + n + " words (limit " + PAGE_LIMIT + ")");
            }
        }
        return messages;
    }

    private static int words(String text) {
        if (isStub(text)) {
            return 0;
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? 0 : WHITESPACE.split(stripped).length;
    }

    // --- C6 forbidden values ------------------------------------------------------------------------------------

    /**
     * No value of {@code forbiddenFile} (each non-blank line of at least 4 characters, stripped) occurs in any
     * regular file under {@code kb}, case-insensitively. A hit is reported as
     * {@code forbidden value #<n> (<length> chars) in <file>:<line>} with {@code n} the 1-based index among the
     * values; the message never contains the value.
     */
    static List<String> forbidden(Path kb, Path forbiddenFile) throws IOException {
        List<String> values = new ArrayList<>();
        for (String line : Files.readAllLines(forbiddenFile, StandardCharsets.UTF_8)) {
            String value = line.strip();
            if (value.length() >= MIN_FORBIDDEN_LENGTH) {
                values.add(value);
            }
        }
        List<String> lowered = values.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
        List<String> messages = new ArrayList<>();
        for (Path file : KnowledgeBase.files(kb)) {
            List<String> lines = read(file).toLowerCase(Locale.ROOT).lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                for (int v = 0; v < lowered.size(); v++) {
                    if (lines.get(i).contains(lowered.get(v))) {
                        messages.add("forbidden value #" + (v + 1) + " (" + values.get(v).length() + " chars) in "
                                + KnowledgeBase.relative(kb, file) + ":" + (i + 1));
                    }
                }
            }
        }
        return messages;
    }

    // --- C7 contract names --------------------------------------------------------------------------------------

    /**
     * Every contract name of {@code sources} is a backticked token of {@code contracts.md} (D-021). A contract name
     * is the {@code Name} cell of every {@code headerTable} / {@code propertyTable} row of every step, and the
     * string-literal first argument of a header or property call in every {@code .groovy} resource; names that
     * contain a GString or placeholder opener (dollar-brace or double brace) or whitespace are skipped, and so are
     * names starting with a dollar sign (a Content Modifier Delete pattern such as {@code $name=...} or
     * {@code $include=...}, D-021). A stub or missing {@code contracts.md} documents nothing. Messages
     * {@code missing contract name: <name> (<first artifact id>)}, sorted, one per distinct name.
     */
    static List<String> contractNames(Path kb, Path sources) throws IOException {
        Map<String, String> firstSeen = new LinkedHashMap<>();
        for (Path pkg : packages(sources)) {
            for (Artifact artifact : artifacts(pkg)) {
                Path dir = artifact.dir();
                IFlow flow = artifact.flow();
                String where = flow.id() == null ? dir.getFileName().toString() : flow.id();
                List<String> names = new ArrayList<>();
                for (Step step : flow.steps()) {
                    for (String key : List.of("headerTable", "propertyTable")) {
                        String table = step.properties().get(key);
                        if (table != null) {
                            Matcher m = NAME_CELL.matcher(table);
                            while (m.find()) {
                                names.add(m.group(1));
                            }
                        }
                    }
                }
                for (String resource : flow.resources()) {
                    if (resource.endsWith(".groovy")) {
                        names.addAll(scriptContractNames(read(dir.resolve(RESOURCES).resolve(resource))));
                    }
                }
                for (String name : names) {
                    String trimmed = name.strip();
                    if (isContractName(trimmed)) {
                        firstSeen.putIfAbsent(trimmed, where);
                    }
                }
            }
        }
        Set<String> documented = new HashSet<>();
        Path contracts = kb.resolve("contracts.md");
        if (Files.isRegularFile(contracts)) {
            String text = read(contracts);
            if (!isStub(text)) {
                backticked(text).forEach(t -> documented.add(t.strip()));
            }
        }
        Set<String> messages = new TreeSet<>();
        firstSeen.forEach((name, where) -> {
            if (!documented.contains(name)) {
                messages.add("missing contract name: " + name + " (" + where + ")");
            }
        });
        return new ArrayList<>(messages);
    }

    /** An unpacked artifact folder and its parsed model. */
    private record Artifact(Path dir, IFlow flow) {
    }

    /**
     * The unpacked artifact folders (folders holding {@code META-INF/MANIFEST.MF} under the package folder, sorted),
     * each read and the list sorted by artifact id; the folder is kept because the Groovy text is read from it.
     */
    private static List<Artifact> artifacts(Path pkg) throws IOException {
        List<Path> dirs;
        try (Stream<Path> walk = Files.walk(pkg)) {
            dirs = walk.filter(Files::isDirectory).filter(d -> Files.isRegularFile(d.resolve(MANIFEST))).sorted()
                    .toList();
        }
        List<Artifact> out = new ArrayList<>();
        for (Path dir : dirs) {
            out.add(new Artifact(dir, IFlowReader.read(dir)));
        }
        out.sort(Comparator.comparing((Artifact a) -> a.flow().id(), Comparator.nullsFirst(Comparator.naturalOrder())));
        return out;
    }

    /** The plain string-literal first arguments of header/property calls, lines starting with {@code //} skipped. */
    private static List<String> scriptContractNames(String script) {
        List<String> names = new ArrayList<>();
        for (String line : script.lines().toList()) {
            if (line.strip().startsWith("//")) {
                continue;
            }
            Matcher m = CONTRACT_CALL.matcher(line);
            while (m.find()) {
                names.add(m.group(1) != null ? m.group(1) : m.group(2));
            }
        }
        return names;
    }

    private static boolean isContractName(String name) {
        return !name.isEmpty() && !name.startsWith("$") && !name.contains("${") && !name.contains("{{")
                && !WHITESPACE.matcher(name).find();
    }

    // --- shared -------------------------------------------------------------------------------------------------

    /** The package folders of a label folder (directories only), sorted; none when it is not a folder. */
    private static List<Path> packages(Path sources) throws IOException {
        if (!Files.isDirectory(sources)) {
            return List.of();
        }
        try (Stream<Path> list = Files.list(sources)) {
            return list.filter(Files::isDirectory).sorted().toList();
        }
    }

    /** Text between single backticks on one line, in order of occurrence. */
    private static List<String> backticked(String text) {
        List<String> tokens = new ArrayList<>();
        for (String line : text.lines().toList()) {
            Matcher m = BACKTICKED.matcher(line);
            while (m.find()) {
                tokens.add(m.group(1));
            }
        }
        return tokens;
    }

    private static boolean isScriptName(String name) {
        return name.endsWith(".groovy") || name.endsWith(".xsl");
    }

    private static String basename(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static boolean isStub(String text) {
        return text.contains(STUB_MARKER);
    }

    /** UTF-8 text; malformed bytes (a binary file) become replacement characters rather than an error. */
    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
