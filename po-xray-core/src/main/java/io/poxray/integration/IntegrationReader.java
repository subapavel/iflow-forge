package io.poxray.integration;

import io.poxray.IssueLog;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.DataType;
import io.poxray.model.IntegrationInventory.DirRef;
import io.poxray.model.IntegrationInventory.Endpoint;
import io.poxray.model.IntegrationInventory.ExternalDefinition;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.InterfaceAssignment;
import io.poxray.model.IntegrationInventory.MappingStep;
import io.poxray.model.IntegrationInventory.MessageMapping;
import io.poxray.model.IntegrationInventory.MessageType;
import io.poxray.model.IntegrationInventory.Module;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.Operation;
import io.poxray.model.IntegrationInventory.OperationMapping;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.IntegrationInventory.ServiceInterface;
import io.poxray.model.Ref;
import io.poxray.sca.XmlDocs;
import io.poxray.xi.XiLink;
import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectSource;
import io.poxray.xi.XiRef;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Element;

/**
 * Expands ICOs into the objects they use: channels (Directory) and service interfaces, operation mappings,
 * message mappings, message / fault types, data types and external definitions (ESR), following the typed links
 * of the {@code xiObj} envelope through an {@link XiObjectSource} (TPZ or online). Every object is read once;
 * unresolvable references are reported as issues.
 */
public final class IntegrationReader {

    private static final Pattern GENERATED_FROM = Pattern.compile("Generated for dir://IFLOW/\\S+ '([^']*)'");
    /** Attribute / parameter names whose values are masked (PO does not export passwords, this is a safety net). */
    private static final Pattern SECRET =
            Pattern.compile("(?i).*(password|passwd|pwd|secret|token|credential|privatekey|apikey).*");

    private final XiObjectSource source;
    private final IssueLog issues;
    private final Set<String> visited = new HashSet<>();
    private final Map<String, Channel> channels = new LinkedHashMap<>();
    private final Map<String, ServiceInterface> interfaces = new LinkedHashMap<>();
    private final Map<String, OperationMapping> operationMappings = new LinkedHashMap<>();
    private final Map<String, MessageMapping> messageMappings = new LinkedHashMap<>();
    private final Map<String, MessageType> messageTypes = new LinkedHashMap<>();
    private final Map<String, DataType> dataTypes = new LinkedHashMap<>();
    private final Map<String, ExternalDefinition> externalDefinitions = new LinkedHashMap<>();
    private final Map<String, ObjRef> mappingPrograms = new LinkedHashMap<>();

    public IntegrationReader(XiObjectSource source, IssueLog issues) {
        this.source = source;
        this.issues = issues;
    }

    public List<Ico> icos(List<XiObject> icos) {
        return icos.stream().map(this::ico).toList();
    }

    /** Expands any supported ESR object (service interface, operation / message mapping, message or data type). */
    public void expand(XiRef ref) {
        switch (String.valueOf(ref.typeId())) {
            case "ifmmessif" -> serviceInterface(ref);
            case "MAPPING" -> operationMapping(ref);
            case "XI_TRAFO" -> messageMapping(ref);
            case "ifmmessage", "ifmfaultm", "ifmextmes" -> message(ref);
            case "ifmtypedef" -> dataType(ref);
            default -> throw new IllegalArgumentException("Cannot expand " + ref.label());
        }
    }

    public List<Channel> channels() {
        return List.copyOf(channels.values());
    }

    public IntegrationInventory.Esr esr() {
        return new IntegrationInventory.Esr(List.copyOf(interfaces.values()), List.copyOf(operationMappings.values()),
                List.copyOf(messageMappings.values()), List.copyOf(messageTypes.values()),
                List.copyOf(dataTypes.values()), List.copyOf(externalDefinitions.values()),
                List.copyOf(mappingPrograms.values()));
    }

    // ------------------------------------------------------------------------------------------ ICO

    Ico ico(XiObject o) {
        XiRef id = o.id();
        String generatedFrom = null;
        if (o.description() != null) {
            Matcher m = GENERATED_FROM.matcher(o.description());
            generatedFrom = m.find() ? m.group(1) : null;
        }
        ObjRef senderIf = o.link("outboundInterface").map(l -> objRef(l.target())).orElse(
                new ObjRef("ifmmessif", id.key(3), id.key(2), null, null, null));
        o.link("outboundInterface").ifPresent(l -> serviceInterface(l.target()));
        List<String> operations = o.links("senderOperation").stream()
                .sorted(java.util.Comparator.comparingInt(XiLink::kpos))
                .map(l -> operationName(l.target().key(0))).toList();
        DirRef senderChannel = o.link("senderChannel").map(l -> channel(l.target())).orElse(null);
        Endpoint sender = new Endpoint(blank(id.key(0)), blank(id.key(1)), senderIf, operations, senderChannel);

        List<Receiver> receivers = new ArrayList<>();
        Element content = o.content();
        Element configs = content == null ? null : XmlDocs.child(content, "ReceiverConfigurations");
        if (configs != null) {
            int index = 0;
            for (Element rc : XmlDocs.children(configs, "ReceiverConfiguration")) {
                receivers.add(receiver(o, rc, index++));
            }
        }
        if (receivers.isEmpty() && !id.key(5).isEmpty()) {
            receivers.add(new Receiver(blank(id.key(4)), id.key(5), null, List.of(), List.of()));
        }
        String noReceiver = null;
        Element nrb = content == null ? null : XmlDocs.child(content, "NoReceiverBehaviour");
        if (nrb != null) {
            String code = XmlDocs.childText(nrb, "IfNoReceiverFound");
            noReceiver = "0".equals(code) ? "error" : code;
        }
        return new Ico(id.oid(), o.description(), generatedFrom, sender, receivers, noReceiver, o.modifiedBy(),
                o.modifiedAt());
    }

    /**
     * One receiver (index {@code r} = position of the receiver configuration). Link roles verified on xipod:
     * {@code MAP<r>} with kpos = interface determination counter → operation mapping; {@code IIF<r>$<counter>}
     * (or {@code IIF<r>} for single determinations) with kpos = split counter → target interfaces;
     * {@code PAR<r>_<counter>_<parameter>} → channel bound to a mapping parameter.
     */
    private Receiver receiver(XiObject ico, Element rc, int r) {
        Element receiver = XmlDocs.child(rc, "Receiver");
        String party = extractor(receiver, "PartyExtractor");
        String component = extractor(receiver, "ServiceExtractor");
        String condition = null;
        Element cond = XmlDocs.child(rc, "Condition");
        if (cond != null && !cond.getTextContent().isBlank()) {
            condition = cond.getTextContent().replaceAll("\\s+", " ").strip();
        }
        List<InterfaceAssignment> assignments = new ArrayList<>();
        Element dets = XmlDocs.child(rc, "InterfaceDeterminations");
        if (dets != null) {
            for (Element d : XmlDocs.children(dets, "InterfaceDetermination")) {
                String counterText = XmlDocs.childText(d, "Counter");
                int counter = counterText == null ? 0 : Integer.parseInt(counterText);
                Optional<XiLink> map = ico.links("MAP" + r).stream().filter(l -> l.kpos() == counter).findFirst();
                map.ifPresent(l -> operationMapping(l.target()));

                Map<Integer, String> multiplicities = new LinkedHashMap<>();
                Element split = XmlDocs.child(d, "SplitItems");
                if (split != null) {
                    for (Element item : XmlDocs.children(split, "SplitItem")) {
                        String sc = XmlDocs.childText(item, "SplitCounter");
                        multiplicities.put(sc == null ? 0 : Integer.parseInt(sc), XmlDocs.childText(item, "Multiplicity"));
                    }
                }
                // IIF<r>$<counter>: kpos = split counter; plain IIF<r>: kpos = counter, single split item
                List<XiLink> iifs = ico.links("IIF" + r + "$" + counter);
                boolean perSplit = !iifs.isEmpty();
                if (!perSplit) {
                    iifs = ico.links("IIF" + r).stream().filter(l -> l.kpos() == counter).toList();
                }
                List<IntegrationInventory.Target> targets = new ArrayList<>();
                for (XiLink l : iifs.stream().sorted(java.util.Comparator.comparingInt(XiLink::kpos)).toList()) {
                    serviceInterface(l.target());
                    targets.add(new IntegrationInventory.Target(objRef(l.target()),
                            multiplicities.get(perSplit ? l.kpos() : 0)));
                }
                Map<String, DirRef> parameters = new LinkedHashMap<>();
                String prefix = "PAR" + r + "_" + counter + "_";
                for (XiLink l : ico.linksStartingWith(prefix)) {
                    parameters.put(l.role().substring(prefix.length()), channel(l.target()));
                }
                assignments.add(new InterfaceAssignment(XmlDocs.childText(d, "Operation"),
                        map.map(l -> objRef(l.target())).orElse(null), targets, parameters));
            }
        }
        List<DirRef> receiverChannels = new ArrayList<>();
        for (XiLink l : ico.links("receiverChannel")) {
            if (component == null || component.equals(l.target().key(1))) {
                receiverChannels.add(channel(l.target()));
            }
        }
        return new Receiver(party, component, condition, assignments, receiverChannels);
    }

    /** Constant value of a receiver extractor ({@code TYPE KO}); otherwise the extractor type. */
    private static String extractor(Element receiver, String name) {
        Element ex = receiver == null ? null : XmlDocs.child(receiver, name);
        Element trd = ex == null ? null : XmlDocs.child(ex, "TRD_EXTRACTOR");
        if (trd == null) {
            return null;
        }
        String type = XmlDocs.childText(trd, "TYPE");
        String value = XmlDocs.childText(trd, "VALUE");
        return "KO".equals(type) ? value : value == null ? type : type + ":" + value;
    }

    // ------------------------------------------------------------------------------------------ Directory

    private DirRef channel(XiRef ref) {
        DirRef dir = new DirRef(blank(ref.key(0)), blank(ref.key(1)), blank(ref.key(2)), ref.oid());
        String key = "Channel|" + ref.keyString();
        if (!visited.add(key)) {
            return dir;
        }
        resolve(ref).ifPresent(o -> channels.put(key, channelOf(dir, o)));
        return dir;
    }

    private static Channel channelOf(DirRef id, XiObject o) {
        Element c = o.content();
        Map<String, String> attributes = new LinkedHashMap<>();
        List<Module> modules = new ArrayList<>();
        String direction = null;
        String transport = null;
        String message = null;
        String engine = null;
        if (c != null) {
            direction = switch (String.valueOf(XmlDocs.childText(c, "ChannelDirection"))) {
                case "I" -> "sender";
                case "O" -> "receiver";
                default -> XmlDocs.childText(c, "ChannelDirection");
            };
            transport = XmlDocs.childText(c, "TransportProtocol");
            message = XmlDocs.childText(c, "MessageProtocol");
            engine = XmlDocs.childText(c, "EngineType");
            Element attrs = XmlDocs.child(c, "ChannelAttributes");
            Element data = attrs == null ? null : XmlDocs.child(attrs, "AdapterTypeData");
            if (data != null) {
                for (Element a : XmlDocs.children(data, "Attribute")) {
                    String name = XmlDocs.childText(a, "Name");
                    String value = XmlDocs.childText(a, "Value");
                    if (name != null && value != null) {
                        attributes.put(name, SECRET.matcher(name).matches() ? "<hidden>" : value);
                    }
                }
            }
            modules = modules(c);
        }
        String adapter = transport == null && message == null ? null : transport + "/" + message;
        return new Channel(id, direction, adapter, transport, message, engine, attributes, modules, o.description());
    }

    private static List<Module> modules(Element channel) {
        Element pipeline = XmlDocs.child(channel, "PipelineAttributes");
        if (pipeline == null) {
            return List.of();
        }
        Map<String, Map<String, String>> params = new LinkedHashMap<>();
        Element moduleData = XmlDocs.child(pipeline, "ModuleData");
        if (moduleData != null) {
            for (Element mc : XmlDocs.children(moduleData, "ModuleConfig")) {
                String name = XmlDocs.childText(mc, "ParamName");
                if (name != null) {
                    String value = XmlDocs.childText(mc, "ParamValue");
                    params.computeIfAbsent(String.valueOf(XmlDocs.childText(mc, "ModuleNs")), k -> new LinkedHashMap<>())
                            .put(name, SECRET.matcher(name).matches() ? "<hidden>" : value == null ? "" : value);
                }
            }
        }
        List<Module> out = new ArrayList<>();
        Element data = XmlDocs.child(pipeline, "PipelineData");
        if (data != null) {
            for (Element pc : XmlDocs.children(data, "PipelineConfig")) {
                String ns = XmlDocs.childText(pc, "ModuleNs");
                String position = XmlDocs.childText(pc, "Position");
                out.add(new Module(position == null ? 0 : Integer.parseInt(position), XmlDocs.childText(pc, "ModuleName"),
                        XmlDocs.childText(pc, "ModuleType"), ns, params.getOrDefault(String.valueOf(ns), Map.of())));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ ESR

    private void serviceInterface(XiRef ref) {
        if (!visited.add(key(ref))) {
            return;
        }
        resolve(ref).ifPresent(o -> {
            Element c = o.content();
            List<Operation> ops = new ArrayList<>();
            for (XiObject op : o.inner()) {
                if (!"ifmoper".equals(op.typeId())) {
                    continue;
                }
                Element oc = op.content();
                String mode = oc == null ? null : "true".equals(oc.getAttribute("isSynchronous")) ? "sync" : "async";
                ObjRef request = op.link("REQUEST").map(l -> message(l.target())).orElse(null);
                ObjRef response = op.link("RESPONSE").map(l -> message(l.target())).orElse(null);
                List<ObjRef> faults = op.links("FAULT").stream().map(l -> message(l.target())).toList();
                ops.add(new Operation(operationName(op.id().key(0)), mode, request, response, faults, op.description()));
            }
            interfaces.put(key(ref), new ServiceInterface(objRef(o.id()),
                    c == null ? null : blank(c.getAttribute("category")),
                    c == null ? null : blank(c.getAttribute("interfacePattern")),
                    c == null ? null : XmlDocs.childText(c, "xmlPayloadNs"), ops, o.description()));
        });
    }

    private void operationMapping(XiRef ref) {
        if (!visited.add(key(ref))) {
            return;
        }
        resolve(ref).ifPresent(o -> {
            List<MappingStep> steps = new ArrayList<>();
            for (String kind : List.of("REQUEST", "RESPONSE", "FAULT")) {
                Optional<XiLink> program = o.link(kind + "_TRAFO");
                Optional<XiLink> out = o.link(kind + "_OUT_MES");
                Optional<XiLink> in = o.link(kind + "_IN_MES");
                if (program.isEmpty() && out.isEmpty() && in.isEmpty()) {
                    continue;
                }
                ObjRef programRef = program.map(l -> mappingProgram(l.target())).orElse(null);
                ObjRef sourceMessage = null;
                ObjRef targetMessage = null;
                MessageMapping mm = programRef == null ? null : messageMappings.get(key(program.get().target()));
                if (mm != null) { // authoritative direction: the message mapping itself
                    sourceMessage = mm.source();
                    targetMessage = mm.target();
                } else { // role convention observed in samples: fault messages are swapped
                    Optional<XiLink> src = "FAULT".equals(kind) ? in : out;
                    Optional<XiLink> tgt = "FAULT".equals(kind) ? out : in;
                    sourceMessage = src.map(l -> message(l.target())).orElse(null);
                    targetMessage = tgt.map(l -> message(l.target())).orElse(null);
                }
                steps.add(new MappingStep(kind.toLowerCase(java.util.Locale.ROOT), programRef, sourceMessage, targetMessage));
            }
            operationMappings.put(key(ref), new OperationMapping(objRef(o.id()),
                    o.link("OUTBOUND_IF").map(l -> objRef(l.target())).orElse(null),
                    o.link("INBOUND_IF").map(l -> objRef(l.target())).orElse(null), steps, o.description()));
        });
    }

    /** Message mapping (XI_TRAFO) is expanded; other programs (Java, XSLT) are listed as mapping programs. */
    private ObjRef mappingProgram(XiRef ref) {
        ObjRef r = objRef(ref);
        if ("XI_TRAFO".equals(ref.typeId())) {
            messageMapping(ref);
        } else {
            mappingPrograms.putIfAbsent(key(ref), r);
        }
        return r;
    }

    private void messageMapping(XiRef ref) {
        if (!visited.add(key(ref))) {
            return;
        }
        resolve(ref).ifPresent(o -> {
            Element c = o.content();
            messageMappings.put(key(ref), new MessageMapping(objRef(o.id()),
                    o.link("SOURCE_IFR_MESS").map(l -> message(l.target())).orElse(null),
                    o.link("TARGET_IFR_MESS").map(l -> message(l.target())).orElse(null),
                    c == null ? null : XmlDocs.childText(c, "Multiplicity"), o.description()));
        });
    }

    /** A message reference: message type, fault message type or external message (resolved to its definition). */
    private ObjRef message(XiRef ref) {
        switch (String.valueOf(ref.typeId())) {
            case "ifmmessage", "ifmfaultm" -> messageType(ref);
            case "ifmextmes" -> externalDefinition(ref);
            default -> {
                // e.g. context objects: listed by reference only
            }
        }
        return objRef(ref);
    }

    private void messageType(XiRef ref) {
        if (!visited.add(key(ref))) {
            return;
        }
        resolve(ref).ifPresent(o -> {
            ObjRef dt = o.link("TYPE").map(l -> {
                dataType(l.target());
                return objRef(l.target());
            }).orElse(null);
            Element c = o.content();
            messageTypes.put(key(ref), new MessageType(objRef(o.id()), dt,
                    c == null ? null : XmlDocs.childText(c, "xmlPayloadNs"), o.description()));
        });
    }

    private void dataType(XiRef ref) {
        if (!visited.add(key(ref))) {
            return;
        }
        resolve(ref).ifPresent(o -> {
            List<ObjRef> uses = new ArrayList<>();
            for (XiLink l : o.links("TYPE")) {
                uses.add(objRef(l.target()));
                dataType(l.target());
            }
            Element schema = o.content() == null ? null : XmlDocs.child(o.content(), "schema");
            Element xsd = schema == null ? null : firstElement(schema);
            dataTypes.put(key(ref), new DataType(objRef(o.id()), uses, xsd == null ? null : serialize(xsd),
                    o.description()));
        });
    }

    /** External message {@code defName|defNs|message|messageNs} → external definition {@code defName|defNs}. */
    private void externalDefinition(XiRef message) {
        XiRef def = new XiRef(message.vcType(), message.swcGuid(), message.swcCaption(), "ifmextdef", null,
                List.of(message.key(0), message.key(1)));
        if (!visited.add(key(def))) {
            return;
        }
        resolve(def).ifPresent(o -> {
            List<String> messages = new ArrayList<>();
            for (XiObject inner : o.inner()) {
                if ("ifmextmes".equals(inner.typeId())) {
                    messages.add(inner.id().key(2));
                }
            }
            externalDefinitions.put(key(def), new ExternalDefinition(objRef(o.id()), messages, o.description()));
        });
    }

    // ------------------------------------------------------------------------------------------ helpers

    private Optional<XiObject> resolve(XiRef ref) {
        Optional<XiObject> o = source.resolve(ref);
        if (o.isEmpty()) {
            String where = ref.swcCaption() == null ? "" : " (" + ref.swcCaption() + ")";
            Optional<String> failure = source.failure(ref);
            if (failure.isPresent()) {
                issues.warn("OBJECT_NOT_READABLE", "Object " + ref.label() + where + " cannot be read: " + failure.get(),
                        IssueLog.at(null, null, new Ref(ref.oid(), ref.label()), null), null);
            } else {
                issues.warn("UNRESOLVED_OBJECT", "Object not found: " + ref.label() + where,
                        IssueLog.at(null, null, new Ref(ref.oid(), ref.label()), null), null);
            }
        }
        return o;
    }

    static ObjRef objRef(XiRef r) {
        return new ObjRef(r.typeId(), blank(r.key(0)), blank(r.key(1)), r.swcCaption(), r.swcGuid(), r.oid());
    }

    private static String key(XiRef r) {
        return r.typeId() + "|" + (r.isDirectory() ? "D" : r.swcGuid()) + "|" + r.keyString();
    }

    /** Operation name from an operation key element {@code <interface>.<operation>}. */
    static String operationName(String key) {
        int dot = key.lastIndexOf('.');
        return dot >= 0 ? key.substring(dot + 1) : key;
    }

    private static Element firstElement(Element parent) {
        for (org.w3c.dom.Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                return e;
            }
        }
        return null;
    }

    private static String serialize(Element e) {
        try {
            var t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter w = new StringWriter();
            t.transform(new DOMSource(e), new StreamResult(w));
            return w.toString();
        } catch (TransformerException ex) {
            return null;
        }
    }

    private static String blank(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
