package io.poxray.migration;

import io.poxray.model.AttributesReport;
import io.poxray.model.AttributesReport.Attribute;
import io.poxray.model.AttributesReport.IcoRef;
import io.poxray.model.AttributesReport.ModuleRef;
import io.poxray.model.AttributesReport.Scenario;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.Condition;
import io.poxray.model.IntegrationInventory.ConditionLine;
import io.poxray.model.IntegrationInventory.DirRef;
import io.poxray.model.IntegrationInventory.Endpoint;
import io.poxray.model.IntegrationInventory.Esr;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.InterfaceAssignment;
import io.poxray.model.IntegrationInventory.MessageType;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.Operation;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.IntegrationInventory.ServiceInterface;
import io.poxray.model.IntegrationInventory.Target;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Derives the eleven D-025 scenario attributes of every ICO of {@link IntegrationInventory} files
 * ({@code po-xray ico} output). A resolved attribute names the input path its value came from; what the inventory
 * cannot decide is {@code ?} with the still possible candidates and a question, never a guess.
 */
public final class ScenarioAttributes {

    static final String OPEN = "?";

    private static final List<String> QOS = List.of("EO", "EOIO", "BE", "ALO");
    private static final List<String> RUNTIME = List.of("integrated-async", "integrated-sync", "decoupled");
    private static final List<String> ADAPTER = List.of("XI", "IDOC", "OTHER");
    private static final List<String> COUNT = List.of("1", "n");
    private static final List<String> RECEIVER_DETERMINATION = List.of("static", "xpath", "custom", "reuse-xrd");
    private static final List<String> MAPPING = List.of("none", "one", "one-to-many-one-type",
            "one-to-many-many-types", "ordered");
    private static final List<String> ERROR_HANDLING = List.of("standard", "custom");
    private static final List<String> EXIT = List.of("none", "pre", "post", "pre+post");
    private static final List<String> CONVERSION = List.of("none", "custom");

    /** Module names (after an optional {@code localejbs/} prefix) that run the adapter, not custom code. */
    private static final Set<String> ADAPTER_BEANS = Set.of("CallSapAdapter", "CallAdapterWithMessageAsXMB",
            "XISOAPAdapterBean", "CallXIAdapter");
    private static final String LOCALEJBS = "localejbs/";
    private static final String MESSAGE_TRANSFORM_BEAN = "AF_Modules/MessageTransformBean";

    static final String Q_RUNTIME = "tenant or scenario choice";
    static final String Q_ERROR_HANDLING = "tenant-level switch CustomXError_Enabled (D-025); record the request";
    static final String Q_CONVERSION = "does the sender payload need conversion to XML (Step03)";
    static final String NOTE_ONE_TO_MANY = "ordered if PO keeps order at runtime (not in the dump)";

    /** D-040: the receiver_determination question of a BE ICO with dynamic routing. */
    static final String Q_BE_DYNAMIC_ROUTING = "dynamic routing with qos BE: the reuse-XRD copy routes only on the decoupled runtime (Step04); a custom receiver determination returning one receiver (Step02 sync rejects several), or a blocking entry";
    static final String NOTE_BE_DYNAMIC_COUNT = "BE: Step02 sync accepts one receiver; see receiver_determination";
    /** D-040: appended to the EO/EOIO runtime question when the ICO uses dynamic routing. */
    static final String Q_DYNAMIC_RUNTIME = "dynamic routing: reuse-xrd routes only on the decoupled runtime (Step04), integrated-async needs a custom receiver determination (D-036)";

    /** D-040: a prefix {@code p:} before a name start, not preceded by a name character. */
    private static final Pattern PREFIX = Pattern.compile("(?<![\\w.:-])([A-Za-z_][\\w.-]*):(?=[A-Za-z_*])");
    private static final Pattern QUOTED = Pattern.compile("\"[^\"]*\"|'[^']*'");
    /** A context-object operand as po-xray renders it: {@code context:<name>}, optionally {@code  (<namespace>)}. */
    private static final Pattern CONTEXT_OBJECT = Pattern.compile("(?<![\\w.:-])context:[A-Za-z_][\\w.-]*(?: \\([^)]*\\))?");
    private static final String CONTEXT = "context:";

    /** D-032: the question both {@code custom_exit} and {@code inbound_conversion} ask about a sender module. */
    static String senderModuleQuestion(String rawName) {
        return "sender module " + rawName + ": conversion to XML (Step03, default custom) or pre-processing exit";
    }

    /** D-033: the question {@code custom_exit} asks about a module of a resolved receiver channel. */
    static String receiverModuleQuestion(String rawName) {
        return "receiver module " + rawName + ": outbound conversion (Step07) or post-processing exit";
    }

    private ScenarioAttributes() {
    }

    /** Reads every file as an {@link IntegrationInventory}; a file that cannot be read or parsed fails the call. */
    public static AttributesReport derive(List<Path> icoJsonFiles) throws IOException {
        List<String> inputs = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        for (Path file : icoJsonFiles) {
            IntegrationInventory inv = Reports.readJson(file, IntegrationInventory.class);
            String label = file.toString();
            inputs.add(label);
            scenarios.addAll(scenarios(inv, label));
        }
        return new AttributesReport(Reports.SCHEMA_VERSION, Reports.generator(), List.copyOf(inputs),
                List.copyOf(scenarios), List.of());
    }

    /** In-memory: one inventory, {@code fileLabel} as its input name. */
    public static AttributesReport derive(IntegrationInventory inv, String fileLabel) {
        return new AttributesReport(Reports.SCHEMA_VERSION, Reports.generator(), List.of(fileLabel),
                scenarios(inv, fileLabel), List.of());
    }

    private static List<Scenario> scenarios(IntegrationInventory inv, String label) {
        List<Channel> channels = Reports.list(inv.channels());
        List<Ico> icos = Reports.list(inv.icos());
        List<Scenario> out = new ArrayList<>();
        for (int i = 0; i < icos.size(); i++) {
            out.add(new IcoDerivation(channels, inv.esr(), icos.get(i), "icos[" + i + "]").scenario(label));
        }
        return List.copyOf(out);
    }

    // ---------------------------------------------------------------- Markdown

    public static String markdown(AttributesReport report) {
        StringBuilder md = new StringBuilder("# Scenario attributes\n");
        for (Scenario s : Reports.list(report.scenarios())) {
            IcoRef ico = s.ico();
            String heading = ico.generatedFrom() != null && !ico.generatedFrom().isBlank() ? ico.generatedFrom()
                    : ico.oid();
            md.append("\n## ").append(heading).append("\n\n");
            if (ico.description() != null && !ico.description().isBlank()) {
                md.append(ico.description().strip()).append("\n\n");
            }
            md.append("sender ").append(orDash(ico.senderComponent())).append(' ')
                    .append(orDash(ico.senderInterface())).append(" → receivers ")
                    .append(Reports.list(ico.receiverComponents()).stream().map(ScenarioAttributes::orDash)
                            .collect(Collectors.joining(", ")))
                    .append("\n\n");
            md.append("| attribute | value | source |\n|---|---|---|\n");
            for (Attribute a : Reports.list(s.attributes())) {
                String value = OPEN.equals(a.value())
                        ? "? (" + String.join(", ", Reports.list(a.candidates())) + ")"
                        : Reports.code(a.value());
                String source = Reports.code(a.source())
                        + (a.note() == null || a.note().isBlank() ? "" : " — " + a.note());
                md.append("| ").append(Reports.cell(a.name())).append(" | ").append(Reports.cell(value)).append(" | ")
                        .append(Reports.cell(source.strip())).append(" |\n");
            }
            List<Attribute> open = Reports.list(s.attributes()).stream().filter(a -> OPEN.equals(a.value())).toList();
            if (!open.isEmpty()) {
                md.append("\n### Questions\n\n");
                for (Attribute a : open) {
                    md.append("- `").append(a.name()).append("` — ").append(a.question()).append('\n');
                }
            }
            if (!Reports.list(s.modules()).isEmpty()) {
                md.append("\n### Modules\n\n");
                for (ModuleRef m : s.modules()) {
                    md.append("- ").append(m.side()).append(' ').append(m.channel()).append(" #").append(m.position())
                            .append(' ').append(m.name()).append('\n');
                }
            }
        }
        Reports.issues(md, report.issues());
        return md.toString();
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    // ---------------------------------------------------------------- shared rules

    /** Index of the channel {@code ref} names: same oid when both carry one, else same party, component and name. */
    static int channelIndex(List<Channel> channels, DirRef ref) {
        if (ref == null) {
            return -1;
        }
        if (ref.oid() != null) {
            for (int k = 0; k < channels.size(); k++) {
                DirRef id = channels.get(k).id();
                if (id != null && ref.oid().equals(id.oid())) {
                    return k;
                }
            }
        }
        for (int k = 0; k < channels.size(); k++) {
            DirRef id = channels.get(k).id();
            if (id != null && (ref.oid() == null || id.oid() == null) && Objects.equals(ref.party(), id.party())
                    && Objects.equals(ref.component(), id.component()) && Objects.equals(ref.name(), id.name())) {
                return k;
            }
        }
        return -1;
    }

    /** XI | IDOC | OTHER from {@code transport/message}: a part starting with XI or IDoc decides; null if absent. */
    static String adapterClass(String adapter) {
        if (adapter == null || adapter.isBlank()) {
            return null;
        }
        String[] parts = adapter.split("/");
        for (String part : parts) {
            if (part.strip().toUpperCase(Locale.ROOT).startsWith("XI")) {
                return "XI";
            }
        }
        for (String part : parts) {
            if (part.strip().toUpperCase(Locale.ROOT).startsWith("IDOC")) {
                return "IDOC";
            }
        }
        return "OTHER";
    }

    static String normaliseQos(String qos) {
        if (qos == null) {
            return null;
        }
        return switch (qos.strip().toUpperCase(Locale.ROOT)) {
            case "EXACTLYONCE", "EO" -> "EO";
            case "EXACTLYONCEINORDER", "EOIO" -> "EOIO";
            case "BESTEFFORT", "BE" -> "BE";
            default -> null;
        };
    }

    static boolean hasCondition(Condition c) {
        return c != null && ((c.text() != null && !c.text().isBlank()) || (c.lines() != null && !c.lines().isEmpty()));
    }

    /**
     * D-040: the distinct prefixes of a condition in order of first appearance, from {@code lines[].left} (a
     * context-object operand skipped); from {@code text} without its quoted strings and context-object operands only
     * when there are no lines. {@code right} is never scanned.
     */
    static List<String> prefixes(Condition c) {
        List<ConditionLine> lines = Reports.list(c.lines());
        List<String> operands = !lines.isEmpty()
                ? lines.stream().map(ConditionLine::left).filter(l -> l != null && !l.startsWith(CONTEXT)).toList()
                : c.text() == null ? List.of()
                        : List.of(CONTEXT_OBJECT.matcher(QUOTED.matcher(c.text()).replaceAll("")).replaceAll(""));
        Set<String> out = new LinkedHashSet<>();
        for (String operand : operands) {
            Matcher m = PREFIX.matcher(operand);
            while (m.find()) {
                out.add(m.group(1));
            }
        }
        return List.copyOf(out);
    }

    /** Same {@code name} and {@code namespace}; the ObjRef {@code type} is not compared. */
    static boolean sameObject(ObjRef a, ObjRef b) {
        return a != null && b != null && Objects.equals(a.name(), b.name())
                && Objects.equals(a.namespace(), b.namespace());
    }

    /** Module name without the {@code localejbs/} prefix. */
    static String bare(String name) {
        return name != null && name.startsWith(LOCALEJBS) ? name.substring(LOCALEJBS.length()) : name;
    }

    static Attribute resolved(String name, String value, String source, String note) {
        return new Attribute(name, value, source, null, null, note);
    }

    /** An open row; {@code candidates} are put in the order of {@code valueSet} (D-025 order). */
    static Attribute open(String name, List<String> valueSet, Set<String> candidates, String question, String source) {
        return new Attribute(name, OPEN, source, valueSet.stream().filter(candidates::contains).toList(), question,
                null);
    }

    static Attribute open(String name, List<String> valueSet, String question, String source) {
        return open(name, valueSet, Set.copyOf(valueSet), question, source);
    }

    static String label(DirRef ref) {
        return (ref.party() == null ? "" : ref.party() + "/") + ref.component() + "/" + ref.name();
    }

    // ---------------------------------------------------------------- one ICO

    /** A receiver channel reference {@code receivers[j].channels[m]} and the channel index it resolves to. */
    private record ChannelUse(int receiver, int position, DirRef ref, int index) {
    }

    private static final class IcoDerivation {
        private final List<Channel> channels;
        private final Esr esr;
        private final Ico ico;
        private final String p;
        private final List<Receiver> receivers;
        private final DirRef senderRef;
        private final int sender;
        private final List<ChannelUse> receiverChannels = new ArrayList<>();
        private final List<ModuleRef> modules = new ArrayList<>();
        private String conversionModule;
        /** Path and raw name of the first sender module that is neither an adapter bean nor MessageTransformBean. */
        private String senderModule;
        private String senderModuleName;

        IcoDerivation(List<Channel> channels, Esr esr, Ico ico, String path) {
            this.channels = channels;
            this.esr = esr;
            this.ico = ico;
            this.p = path;
            this.receivers = Reports.list(ico.receivers());
            this.senderRef = ico.sender() == null ? null : ico.sender().channel();
            this.sender = channelIndex(channels, senderRef);
            for (int j = 0; j < receivers.size(); j++) {
                List<DirRef> refs = Reports.list(receivers.get(j).channels());
                for (int m = 0; m < refs.size(); m++) {
                    receiverChannels.add(new ChannelUse(j, m, refs.get(m), channelIndex(channels, refs.get(m))));
                }
            }
        }

        Scenario scenario(String file) {
            Attribute qos = qos();
            Attribute senderType = senderType();
            Attribute customExit = customExit();
            List<Attribute> rows = List.of(runtime(qos), qos, senderType, receiverType(), receiverCount(qos),
                    receiverDetermination(qos), interfaceDetermination(), mapping(),
                    open("error_handling", ERROR_HANDLING, Q_ERROR_HANDLING, null), customExit,
                    inboundConversion(senderType));
            return new Scenario(file, icoRef(), rows, List.copyOf(modules));
        }

        private IcoRef icoRef() {
            Endpoint s = ico.sender();
            return new IcoRef(ico.oid(), ico.generatedFrom(), ico.description(), s == null ? null : s.component(),
                    s == null || s.iface() == null ? null : s.iface().name(),
                    s == null || s.iface() == null ? null : s.iface().namespace(),
                    receivers.stream().map(Receiver::component).toList());
        }

        private Attribute qos() {
            String path = p + ".receivers";
            if (receivers.isEmpty()) {
                return open("qos", QOS, "the ICO has no receiver; which quality of service does the scenario use",
                        path);
            }
            List<String> raw = receivers.stream().map(Receiver::qualityOfService).toList();
            if (raw.stream().allMatch(Objects::isNull)) {
                return open("qos", QOS, "no receiver names a qualityOfService; which quality of service does the "
                        + "scenario use", path);
            }
            Set<String> values = new LinkedHashSet<>();
            boolean unrecognised = false;
            for (String q : raw) {
                String n = normaliseQos(q);
                if (n == null) {
                    unrecognised = true;
                } else {
                    values.add(n);
                }
            }
            if (unrecognised) {
                return open("qos", QOS, "qualityOfService missing or not recognised on a receiver (" + raw
                        + "); which quality of service does the scenario use", path);
            }
            if (values.size() == 1) {
                return resolved("qos", values.iterator().next(), path + "[0].qualityOfService", null);
            }
            List<String> ordered = QOS.stream().filter(values::contains).toList();
            return open("qos", QOS, values, "receivers differ in qualityOfService: " + String.join(", ", ordered)
                    + "; which one applies to the scenario", path);
        }

        private Attribute runtime(Attribute qos) {
            return switch (qos.value()) {
                case "BE" -> resolved("runtime", "integrated-sync", qos.source(), "from qos BE");
                case "EO", "EOIO" -> open("runtime", RUNTIME, Set.of("integrated-async", "decoupled"),
                        dynamicRouting() ? Q_RUNTIME + "; " + Q_DYNAMIC_RUNTIME : Q_RUNTIME, qos.source());
                default -> open("runtime", RUNTIME, "qos is open; BE gives integrated-sync, EO or EOIO "
                        + "integrated-async or decoupled", qos.source());
            };
        }

        private Attribute senderType() {
            if (sender < 0) {
                String q = senderRef == null ? "the ICO names no sender channel; which adapter does the sender use"
                        : "sender channel " + label(senderRef) + " is not in channels[]; which adapter does the "
                                + "sender use";
                return open("sender_type", ADAPTER, q, p + ".sender.channel");
            }
            String source = "channels[" + sender + "].adapter";
            String cls = adapterClass(channels.get(sender).adapter());
            if (cls == null) {
                return open("sender_type", ADAPTER, "the sender channel has no adapter; which adapter does the "
                        + "sender use", source);
            }
            return resolved("sender_type", cls, source, null);
        }

        private Attribute receiverType() {
            String path = p + ".receivers";
            if (receiverChannels.isEmpty()) {
                return open("receiver_type", ADAPTER, "no receiver names a channel; which adapter do the receivers "
                        + "use", path);
            }
            List<String> unknown = new ArrayList<>();
            Map<String, Integer> classes = new LinkedHashMap<>();
            for (ChannelUse use : receiverChannels) {
                String cls = use.index() < 0 ? null : adapterClass(channels.get(use.index()).adapter());
                if (cls == null) {
                    unknown.add(use.ref() == null ? "receivers[" + use.receiver() + "].channels[" + use.position()
                            + "]" : label(use.ref()));
                } else {
                    classes.putIfAbsent(cls, use.index());
                }
            }
            if (!unknown.isEmpty()) {
                return open("receiver_type", ADAPTER, "receiver channel(s) " + String.join(", ", unknown)
                        + " not in channels[] or without adapter; which adapter do they use", path);
            }
            if (classes.size() == 1) {
                Map.Entry<String, Integer> only = classes.entrySet().iterator().next();
                return resolved("receiver_type", only.getKey(), "channels[" + only.getValue() + "].adapter", null);
            }
            List<String> seen = ADAPTER.stream().filter(classes::containsKey).toList();
            return open("receiver_type", ADAPTER, classes.keySet(), "receiver channels use different adapter types: "
                    + String.join(", ", seen) + "; one scenario or one per type", path);
        }

        private boolean dynamicRouting() {
            return ico.dynamicRouting() != null && !Reports.list(ico.dynamicRouting().mappings()).isEmpty();
        }

        private Attribute receiverCount(Attribute qos) {
            if (dynamicRouting()) {
                return resolved("receiver_count", "n", p + ".dynamicRouting",
                        "BE".equals(qos.value()) ? NOTE_BE_DYNAMIC_COUNT : null);
            }
            if (receivers.isEmpty()) {
                return open("receiver_count", COUNT, "the ICO has no receiver and no dynamic routing; how many "
                        + "receivers does the scenario have", p + ".receivers");
            }
            return resolved("receiver_count", receivers.size() == 1 ? "1" : "n", p + ".receivers", null);
        }

        private Attribute receiverDetermination(Attribute qos) {
            if (dynamicRouting()) {
                String source = p + ".dynamicRouting.mappings[0]";
                if ("BE".equals(qos.value())) {
                    return open("receiver_determination", RECEIVER_DETERMINATION, Set.of("custom"),
                            Q_BE_DYNAMIC_ROUTING, source);
                }
                return resolved("receiver_determination", "reuse-xrd", source, "custom if the mapping is rewritten");
            }
            for (int j = 0; j < receivers.size(); j++) {
                Condition c = receivers.get(j).condition();
                if (hasCondition(c)) {
                    return resolved("receiver_determination", "xpath", p + ".receivers[" + j + "].condition",
                            prefixNote(c));
                }
            }
            return resolved("receiver_determination", "static", p + ".receivers", null);
        }

        private Attribute interfaceDetermination() {
            for (int j = 0; j < receivers.size(); j++) {
                List<InterfaceAssignment> as = Reports.list(receivers.get(j).interfaces());
                for (int k = 0; k < as.size(); k++) {
                    if (hasCondition(as.get(k).condition())) {
                        return resolved("interface_determination", "xpath",
                                p + ".receivers[" + j + "].interfaces[" + k + "].condition",
                                prefixNote(as.get(k).condition()));
                    }
                }
            }
            return resolved("interface_determination", "static",
                    receivers.isEmpty() ? p + ".receivers" : p + ".receivers[0].interfaces", null);
        }

        /** D-040: the note of an {@code xpath} row whose condition uses prefixes; null without a prefix. */
        private String prefixNote(Condition c) {
            List<String> prefixes = prefixes(c);
            if (prefixes.isEmpty()) {
                return null;
            }
            String messageType = senderMessageType();
            return "prefixes " + String.join(", ", prefixes) + ": namespace not declared in the dump; "
                    + (messageType != null ? messageType : "no sender message type in the esr section — ask");
        }

        /**
         * The sender request message type with its {@code xmlNamespace} and esr path, or null when the esr section
         * does not name it: interface by name and namespace, the operation named by {@code operations[0]} (else
         * the first), its {@code request} message type by name and namespace.
         */
        private String senderMessageType() {
            Endpoint s = ico.sender();
            if (esr == null || s == null || s.iface() == null) {
                return null;
            }
            List<ServiceInterface> interfaces = Reports.list(esr.serviceInterfaces());
            for (int i = 0; i < interfaces.size(); i++) {
                if (!sameObject(interfaces.get(i).id(), s.iface())) {
                    continue;
                }
                List<Operation> ops = Reports.list(interfaces.get(i).operations());
                if (ops.isEmpty()) {
                    return null;
                }
                List<String> named = Reports.list(s.operations());
                int j = 0;
                for (int o = 0; o < ops.size() && !named.isEmpty(); o++) {
                    if (named.get(0).equals(ops.get(o).name())) {
                        j = o;
                        break;
                    }
                }
                ObjRef request = ops.get(j).request();
                for (MessageType mt : Reports.list(esr.messageTypes())) {
                    if (request != null && sameObject(mt.id(), request)) {
                        return mt.xmlNamespace() == null ? null : "sender request message type " + mt.id().name()
                                + " {" + mt.xmlNamespace() + "} (esr.serviceInterfaces[" + i + "].operations[" + j
                                + "].request)";
                    }
                }
                return null;
            }
            return null;
        }

        private Attribute mapping() {
            Map<String, String> classes = new LinkedHashMap<>();
            for (int j = 0; j < receivers.size(); j++) {
                List<InterfaceAssignment> as = Reports.list(receivers.get(j).interfaces());
                for (int k = 0; k < as.size(); k++) {
                    if (as.get(k).mapping() != null) {
                        classes.putIfAbsent(mappingClass(as.get(k)),
                                p + ".receivers[" + j + "].interfaces[" + k + "].mapping");
                    }
                }
            }
            if (classes.isEmpty()) {
                return resolved("mapping", "none", p + ".receivers", null);
            }
            if (classes.size() == 1) {
                Map.Entry<String, String> only = classes.entrySet().iterator().next();
                return resolved("mapping", only.getKey(), only.getValue(),
                        only.getKey().startsWith("one-to-many") ? NOTE_ONE_TO_MANY : null);
            }
            List<String> seen = MAPPING.stream().filter(classes::containsKey).toList();
            return open("mapping", MAPPING, classes.keySet(), "interface assignments differ in mapping shape: "
                    + String.join(", ", seen) + "; one scenario or several", p + ".receivers");
        }

        private static String mappingClass(InterfaceAssignment a) {
            List<Target> targets = Reports.list(a.targets());
            boolean unbounded = targets.stream().map(Target::multiplicity).filter(Objects::nonNull)
                    .map(m -> m.toLowerCase(Locale.ROOT)).anyMatch(m -> m.contains("unbounded") || m.contains("n"));
            if (targets.size() < 2 && !unbounded) {
                return "one";
            }
            long types = targets.stream()
                    .map(t -> t.iface() == null ? null : List.of(Objects.toString(t.iface().name(), ""),
                            Objects.toString(t.iface().namespace(), "")))
                    .distinct().count();
            return types <= 1 ? "one-to-many-one-type" : "one-to-many-many-types";
        }

        private Attribute customExit() {
            String firstExit = null;
            Boolean pre = null;
            if (sender >= 0) {
                pre = false;
                Channel ch = channels.get(sender);
                List<IntegrationInventory.Module> ms = Reports.list(ch.modules());
                for (int m = 0; m < ms.size(); m++) {
                    String bare = bare(ms.get(m).name());
                    if (ADAPTER_BEANS.contains(bare)) {
                        continue;
                    }
                    String path = "channels[" + sender + "].modules[" + m + "].name";
                    modules.add(new ModuleRef("sender", channelName(ch), ms.get(m).position(), ms.get(m).name()));
                    if (MESSAGE_TRANSFORM_BEAN.equalsIgnoreCase(bare)) {
                        if (conversionModule == null) {
                            conversionModule = path;
                        }
                        continue;
                    }
                    pre = true;
                    if (firstExit == null) {
                        firstExit = path;
                        senderModule = path;
                        senderModuleName = ms.get(m).name();
                    }
                }
            }
            String receiverModuleName = null;
            List<String> unresolved = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (ChannelUse use : receiverChannels) {
                if (use.index() < 0) {
                    unresolved.add(use.ref() == null ? "receivers[" + use.receiver() + "].channels[" + use.position()
                            + "]" : label(use.ref()));
                    continue;
                }
                if (!seen.add(use.index())) {
                    continue;
                }
                Channel ch = channels.get(use.index());
                List<IntegrationInventory.Module> ms = Reports.list(ch.modules());
                for (int m = 0; m < ms.size(); m++) {
                    if (ADAPTER_BEANS.contains(bare(ms.get(m).name()))) {
                        continue;
                    }
                    modules.add(new ModuleRef("receiver", channelName(ch), ms.get(m).position(), ms.get(m).name()));
                    if (firstExit == null) {
                        firstExit = "channels[" + use.index() + "].modules[" + m + "].name";
                    }
                    if (receiverModuleName == null) {
                        receiverModuleName = ms.get(m).name();
                    }
                }
            }
            // D-032 / D-033: a sender module (other than MTB) or a receiver module is a question, never a value;
            // an unknown channel leaves its side open as well.
            boolean preOpen = pre == null || pre;
            boolean postOpen = receiverModuleName != null || !unresolved.isEmpty();
            String source = firstExit != null ? firstExit : p + ".sender.channel";
            if (!preOpen && !postOpen) {
                return resolved("custom_exit", "none", source, null);
            }
            if (pre == null) {
                unresolved.addFirst(senderRef == null ? "sender channel (none named)" : label(senderRef));
            }
            List<String> questions = new ArrayList<>();
            if (senderModuleName != null) {
                questions.add(senderModuleQuestion(senderModuleName));
            }
            if (receiverModuleName != null) {
                questions.add(receiverModuleQuestion(receiverModuleName));
            }
            if (!unresolved.isEmpty()) {
                questions.add("modules of channel(s) not in channels[] are unknown: " + String.join(", ", unresolved)
                        + "; do they run custom modules");
            }
            Set<String> candidates = new HashSet<>();
            for (boolean a : preOpen ? new boolean[] {false, true} : new boolean[] {false}) {
                for (boolean b : postOpen ? new boolean[] {false, true} : new boolean[] {false}) {
                    candidates.add(exit(a, b));
                }
            }
            return open("custom_exit", EXIT, candidates, String.join("; ", questions), source);
        }

        private static String exit(boolean pre, boolean post) {
            return pre && post ? "pre+post" : pre ? "pre" : post ? "post" : "none";
        }

        private static String channelName(Channel ch) {
            return ch.id() == null ? null : ch.id().name();
        }

        private Attribute inboundConversion(Attribute senderType) {
            if (conversionModule != null) {
                return resolved("inbound_conversion", "custom", conversionModule,
                        "MessageTransformBean on the sender channel");
            }
            if (senderModule != null) {
                return open("inbound_conversion", CONVERSION, senderModuleQuestion(senderModuleName), senderModule);
            }
            return switch (senderType.value()) {
                case "XI", "IDOC" -> resolved("inbound_conversion", "none", senderType.source(), null);
                case "OTHER" -> open("inbound_conversion", CONVERSION, Q_CONVERSION, senderType.source());
                default -> open("inbound_conversion", CONVERSION, "sender adapter unknown; " + Q_CONVERSION,
                        senderType.source());
            };
        }
    }
}
