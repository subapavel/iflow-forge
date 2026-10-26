package io.poxray;

import static io.poxray.SyntheticBpmSca.element;
import static io.poxray.SyntheticBpmSca.link;
import static io.poxray.SyntheticBpmSca.partition;

import io.poxray.integration.IntegrationReader;
import io.poxray.json.Json;
import io.poxray.model.BpmComponent;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.ScaInventory;
import io.poxray.xi.InMemoryObjectSource;
import io.poxray.xi.TpzArchive;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Literal synthetic MOIN and xiObj fixtures. No behavior-analysis calculations or replacement source facts. */
public final class SyntheticBehavior {
    public static final String W = "Galaxy.Workflow.";
    public static final String R = "Galaxy.Rule.";
    public static final String M = "Galaxy.Mapping.";
    public static final String NS = "urn:synthetic:behavior";
    public static final String IN = "Inbound|Contract";
    public static final String OUT = "Outbound";
    private final StringBuilder extraElements = new StringBuilder();
    private final StringBuilder extraLinks = new StringBuilder();
    private String activityKind = W + "AutomatedActivity";
    private boolean operation = true, service = true, messageOperation = true, reverseMessage, externalTargets;

    public record Pair(ScaInventory sca, IntegrationInventory icos, Path archive, Path tpz, Path exportJson, Path icosJson) { }

    /** Explicit fixture writer for the packaged CLI smoke; never invoked during class loading. */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected one synthetic output directory");
        Pair pair = new SyntheticBehavior().write(Path.of(args[0]));
        System.out.println(pair.exportJson());
        System.out.println(pair.icosJson());
    }

    public SyntheticBehavior activityKind(String value) { activityKind = value; return this; }
    public SyntheticBehavior operation(boolean value) { operation = value; return this; }
    public SyntheticBehavior service(boolean value) { service = value; return this; }
    public SyntheticBehavior messageOperation(boolean value) { messageOperation = value; return this; }
    public SyntheticBehavior reverseMessage(boolean value) { reverseMessage = value; return this; }
    public SyntheticBehavior externalTargets(boolean value) { externalTargets = value; return this; }
    public SyntheticBehavior raw(String kind, String id, String attrs, String children) {
        extraElements.append(element(kind, id, attrs, children)); return this;
    }
    public SyntheticBehavior relation(String association, String first, String second) {
        extraLinks.append(link(association, first, second)); return this;
    }
    public SyntheticBehavior node(String kind, String id) {
        return raw(kind, id, "", "<name originalText='" + escape(id) + "'/>")
                .relation(W + "ScopeContainsScopeObjects", id, "p-pool");
    }

    public ScaInventory sca(Path file) {
        String nodes = element(W + "StartEvent", "start", "", "<name originalText='Start'/>")
                + element(activityKind, "call", "", "<name originalText='Call|external'/>")
                + element(W + "EndEvent", "end", "", "")
                + element(W + "BoundaryEvent", "boundary", "", "")
                + element(W + "SequenceConnector", "enter", "", "")
                + element(W + "SequenceConnector", "leave", "", "")
                + element(W + "DataObject", "data", "", "<name originalText='payload'/>")
                + element(W + "MessageEventDefinition", "message", "", "")
                + element(W + "ErrorEventDefinition", "error", "isCritical='false' isTechnical='true'", "<name originalText='Fault|Declared'/>")
                + element(M + "Mapping", "mapping", "", "")
                + element(M + "MappingPart", "part", "category='set' enabled='true'", "")
                + element("XSD1.key.QName", "target-q", "name='payload' namespace='" + NS + "'", "")
                + element(R + "Expression", "expression", "stringExpression='payload'", "")
                + element(R + "Reference", "read", "", "")
                + element("XSD1.key.QName", "read-q", "name='payload' namespace='" + NS + "'", "");
        String targets = element("WSDL2.Interface", "in-if", "name='" + IN + "' namespace='" + NS + "'", "")
                + element("WSDL2.Interface", "out-if", "name='" + OUT + "' namespace='" + NS + "'", "")
                + element("WSDL2.Operation", "in-op", "", "<name originalText='Consume'/>")
                + element("WSDL2.Operation", "out-op", "", "<name originalText='Send'/>")
                + element("WSDL2.MessageExchangePattern", "mep", "kind='IN_ONLY'", "")
                + element("WSDL2.MessageReference", "request-ref", "direction='IN'", "")
                + element("XSD1.XsdElementDeclaration", "request", "name='Request' namespace='" + NS + "'", "")
                + element("WSDL2.FaultReference", "fault-ref", "", "")
                + element("WSDL2.Fault", "wsdl-fault", "name='ContractFault'", "")
                + element("XSD1.XsdElementDeclaration", "fault-payload", "name='FaultPayload' namespace='" + NS + "'", "")
                + element("mc.servicereferences.ServiceReference", "service", "id='uuid-is-not-source-id' type='WS' bindingType='XI'", "")
                + element("XSD1.key.QName", "service-q", "name='" + OUT + "' namespace='" + NS + "'", "");
        String relations = link(W + "SourceOfIncomingConnectors", "start", "enter")
                + link(W + "TargetOfOutgoingConnectors", "call", "enter")
                + link(W + "SourceOfIncomingConnectors", "call", "leave")
                + link(W + "TargetOfOutgoingConnectors", "end", "leave")
                + link(W + "CatchEventHasTrigger", "message", "start")
                + link(W + "ActivityHasBoundaryEvent", "boundary", "call")
                + link(W + "CatchEventHasTrigger", "error", "boundary")
                + link(W + "ErrorEventDefinitionHasWSDLFault", "wsdl-fault", "error")
                + link(W + "InputMappingOfMappableObject", "mapping", "call")
                + link(M + "MappingContainsMappingParts", "part", "mapping")
                + link(M + "MappingPartHasQName", "target-q", "part")
                + link(M + "MappingPartContainsExpression", "expression", "part")
                + link(R + "ExpressionHasSteps", "read", "expression")
                + link(R + "ReferenceHasQName", "read-q", "read")
                + link("WSDL2.InterfaceOperations", "in-op", "in-if")
                + link("WSDL2.InterfaceOperations", "out-op", "out-if")
                + link("WSDL2.OperationPattern", "mep", "out-op")
                + link("WSDL2.OperationMessages", "request-ref", "out-op")
                + link("WSDL2.ReferenceToElement", "request-ref", "request")
                + link("WSDL2.OperationFaults", "fault-ref", "out-op")
                + link("WSDL2.ReferenceToInterfaceFault", "wsdl-fault", "fault-ref")
                + link("WSDL2.ReferenceToElement", "wsdl-fault", "fault-payload")
                + link("mc.servicereferences.serviceref_qname", "service-q", "service");
        if (operation) relations += link(W + "ActivityHasOperation", "out-op", "call");
        if (service) relations += link(W + "AutomatedActivityHasServiceReference", "service", "call");
        if (messageOperation) relations += link(W + "MessageEventDefinitionHasOperation",
                reverseMessage ? "message" : "in-op", reverseMessage ? "in-op" : "message");
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put(SyntheticBpmSca.PROCESS, SyntheticBpmSca.process("p-", "Synthetic behavior", nodes
                + (externalTargets ? "" : targets) + extraElements, relations + extraLinks,
                "start", "call", "end", "enter", "leave", "data"));
        if (externalTargets) partitions.put(SyntheticBpmSca.METADATA, partition(targets));
        return PoXray.read(SyntheticBpmSca.write(file, partitions));
    }

    public Pair write(Path dir) throws IOException { return write(dir, "configured-value"); }
    public Pair write(Path dir, String channelValue) throws IOException {
        Files.createDirectories(dir);
        Path archive = dir.resolve("synthetic.sca"), tpz = dir.resolve("synthetic.tpz");
        ScaInventory sca = sca(archive);
        IntegrationInventory icos = configuration(tpz, channelValue);
        Path exportJson = dir.resolve("export.json"), icosJson = dir.resolve("icos.json");
        Json.write(sca, exportJson); Json.write(icos, icosJson);
        return new Pair(sca, icos, archive, tpz, exportJson, icosJson);
    }

    public static IntegrationInventory configuration(Path file, String value) {
        String inboundLinks = xiLink("senderChannel", 0, "D", "Channel", "sender-channel", "Party", "SOURCE", "SND")
                + xiLink("IIF0$0", 0, "S", "ifmmessif", null, IN, NS)
                + xiLink("IIF0$0", 1, "S", "ifmmessif", null, "Unrelated", NS)
                + xiLink("senderOperation", 0, "S", "ifmoper", null, "Source.SenderOperation", NS)
                + xiLink("receiverChannel", 0, "D", "Channel", "receiver-channel", "Party", "BPM", "RCV")
                + xiLink("PAR0_0_LOOKUP", 0, "D", "Channel", "lookup-channel", "Party", "BPM", "LOOKUP")
                + xiLink("PAR0_0_SECOND", 1, "D", "Channel", "receiver-channel", "Party", "BPM", "RCV")
                + xiLink("MAP0", 0, "S", "MAPPING", null, "SyntheticMapping", NS);
        String inbound = "<AllInOne><NoReceiverBehaviour><IfNoReceiverFound>0</IfNoReceiverFound></NoReceiverBehaviour>"
                + "<ReceiverConfigurations>" + receiverXml("BPM", "R1", true) + receiverXml("OTHER", "R2", false)
                + "</ReceiverConfigurations><ReceiverAssignmentList><ReceiverAssignment><ReceiverConditionId>C1</ReceiverConditionId>"
                + "<ReceiverIds><ReceiverId>R1</ReceiverId></ReceiverIds></ReceiverAssignment></ReceiverAssignmentList>"
                + "<Conditions><RDS_CONDSHORT><CONDITIONID>C1</CONDITIONID><CONDLINE><COMPGROUP>1</COMPGROUP><COMPOP>EQ</COMPOP>"
                + "<LEXTRACTOR><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>false</VALUE></TRD_EXTRACTOR></LEXTRACTOR>"
                + "<REXTRACTOR><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>true</VALUE></TRD_EXTRACTOR></REXTRACTOR>"
                + "</CONDLINE></RDS_CONDSHORT></Conditions><DynamicalRouting isOpSpecific='true'/></AllInOne>";
        String outbound = "<AllInOne><ReceiverConfigurations>" + receiverXml("TARGET", "R1", false)
                + "</ReceiverConfigurations></AllInOne>";
        List<String> objects = List.of(
                xiObject("AllInOne", "ingress", List.of("Party", "SOURCE", NS, "Source", "", ""), inboundLinks, inbound),
                xiObject("AllInOne", "egress", List.of("Party", "BPM", NS, OUT, "", ""), "", outbound),
                channel("sender-channel", "SOURCE", "SND", "I", value),
                channel("receiver-channel", "BPM", "RCV", "O", "receiver-value"),
                channel("lookup-channel", "BPM", "LOOKUP", "O", "lookup-value"));
        SyntheticTpz.write(file, "systemtype=IBC 7.1\nsourcesystem=SYNTHETIC\n", objects);
        InMemoryObjectSource source = new InMemoryObjectSource().addAll(TpzArchive.open(file).objects());
        IssueLog issues = new IssueLog();
        IntegrationReader reader = new IntegrationReader(source, issues);
        List<IntegrationInventory.Ico> icos = reader.icos(source.ofType("AllInOne"));
        return new IntegrationInventory("0.1", null, new IntegrationInventory.Source(null, null, List.of(file.toString())),
                null, List.of(), icos, reader.channels(), List.of(), reader.esr(), issues.issues());
    }

    private static String receiverXml(String component, String id, boolean determination) {
        return "<ReceiverConfiguration><ReceiverId>" + id + "</ReceiverId><Receiver>"
                + "<PartyExtractor><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>Party</VALUE></TRD_EXTRACTOR></PartyExtractor>"
                + "<ServiceExtractor><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>" + component + "</VALUE></TRD_EXTRACTOR></ServiceExtractor>"
                + "</Receiver><QualityOfService>EOIO</QualityOfService>" + (determination ? "<InterfaceDeterminations>"
                + "<InterfaceDetermination><Counter>0</Counter><Operation>SenderOperation</Operation><SplitItems>"
                + "<SplitItem><SplitCounter>0</SplitCounter><Multiplicity>1</Multiplicity></SplitItem>"
                + "<SplitItem><SplitCounter>1</SplitCounter><Multiplicity>0..1</Multiplicity></SplitItem>"
                + "</SplitItems></InterfaceDetermination></InterfaceDeterminations>" : "") + "</ReceiverConfiguration>";
    }
    private static String channel(String oid, String component, String name, String direction, String value) {
        String content = "<Channel><ChannelDirection>" + direction + "</ChannelDirection><TransportProtocol>SOAP</TransportProtocol>"
                + "<MessageProtocol>XI</MessageProtocol><EngineType>AE</EngineType><ChannelAttributes><AdapterTypeData>"
                + "<Attribute><Name>syntheticValue</Name><Value>" + escape(value) + "</Value></Attribute></AdapterTypeData></ChannelAttributes>"
                + "<PipelineAttributes><ModuleData><ModuleConfig><ModuleNs>one</ModuleNs><ParamName>option</ParamName>"
                + "<ParamValue>configured</ParamValue></ModuleConfig></ModuleData><PipelineData><PipelineConfig><ModuleNs>one</ModuleNs>"
                + "<Position>7</Position><ModuleName>SyntheticModule</ModuleName><ModuleType>L</ModuleType></PipelineConfig>"
                + "</PipelineData></PipelineAttributes></Channel>";
        return xiObject("Channel", oid, List.of("Party", component, name), "", content);
    }
    private static String xiObject(String type, String oid, List<String> key, String links, String content) {
        return "<p:xiObj xmlns:p='urn:sap-com:xi'><p:idInfo VID='synthetic'><p:vc vcType='D'/><p:key typeID='" + type
                + "' oid='" + oid + "'>" + xiKey(key) + "</p:key></p:idInfo><p:generic><p:lnks>" + links
                + "</p:lnks></p:generic><p:content>" + content + "</p:content></p:xiObj>";
    }
    private static String xiLink(String role, int index, String vc, String type, String oid, String... key) {
        return "<p:lnkRole role='" + role + "' kpos='" + index + "'><p:lnk rMode='A'><p:vc vcType='" + vc
                + "'/><p:key typeID='" + type + "'" + (oid == null ? "" : " oid='" + oid + "'") + ">"
                + xiKey(List.of(key)) + "</p:key></p:lnk></p:lnkRole>";
    }
    private static String xiKey(List<String> key) {
        StringBuilder text = new StringBuilder();
        for (String part : key) text.append("<p:elem>").append(escape(part)).append("</p:elem>");
        return text.toString();
    }
    public static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace("'", "&apos;").replace("\n", "&#10;");
    }
    /** Legacy/projection wrappers preserve the parsed source-evidence object unchanged. */
    public static ScaInventory withComponents(ScaInventory original, List<BpmComponent> components) {
        return new ScaInventory(original.schemaVersion(), original.generator(), original.source(), original.developmentComponents(),
                components, original.ejb(), original.issues(), original.unmapped());
    }
    public static ScaInventory withCatalog(ScaInventory original, BpmComponent.Catalog catalog) {
        BpmComponent component = original.bpm().getFirst();
        return withComponents(original, List.of(new BpmComponent(component.dc(), component.processes(), catalog, component.sourceEvidence())));
    }
    public static IntegrationInventory withConfiguration(IntegrationInventory original, List<IntegrationInventory.Ico> icos,
            List<IntegrationInventory.Channel> channels) {
        return new IntegrationInventory(original.schemaVersion(), original.generator(), original.source(), original.query(),
                original.components(), icos, channels, original.classicConfiguration(), original.esr(), original.issues());
    }
    public static Map<Path, String> hashes(Pair pair) throws IOException {
        Map<Path, String> hashes = new LinkedHashMap<>();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Path file : List.of(pair.archive(), pair.tpz(), pair.exportJson(), pair.icosJson()))
                hashes.put(file, HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file))));
            return hashes;
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
