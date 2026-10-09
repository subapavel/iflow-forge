package io.poxray.model;

import java.util.List;
import java.util.Map;

/**
 * Integration Directory / ESR inventory: ICOs (selected e.g. by communication component) with their channels and
 * the ESR objects they use (service interfaces, operation and message mappings, message and data types).
 * See docs/OUTPUT-MODEL.md, section "Integration inventory".
 */
public record IntegrationInventory(
        String schemaVersion,
        ScaInventory.Generator generator,
        Source source,
        Query query,
        List<Component> components,
        List<Ico> icos,
        List<Channel> channels,
        List<ObjectDetail> classicConfiguration,
        Esr esr,
        List<Issue> issues) {

    /**
     * Any ESR / Directory object as listed by SimpleQuery. {@code key}: key elements by name (e.g. NAME,
     * NAMESPACE; FROMPARTY, FROMSRVC, ... for configuration objects); {@code area}: rep | dir.
     */
    public record ObjectSummary(String type, String area, Map<String, String> key, String swc, String swcGuid,
            String oid, String versionId, String modifiedAt, String modifiedBy, String description) {
    }

    /**
     * Generic view of an object without a dedicated reader (e.g. classic configuration: receiver / interface
     * determination, sender / receiver agreement; value mappings): its links by role and the raw content XML.
     */
    public record ObjectDetail(ObjectSummary id, List<ObjectLink> links, String content) {
    }

    /** Link of a generic object: role and target (type, key elements joined by |, SWCV). */
    public record ObjectLink(String role, String type, String key, String swc, String oid) {
    }

    /** {@code system}: PO system (name, URL) for online reads; {@code files}: TPZ exports for offline reads. */
    public record Source(String system, String url, List<String> files) {
    }

    /** {@code direction}: in | out | both, relative to {@code component}. */
    public record Query(String component, String direction) {
    }

    /** Communication component (business system / business component) in the Integration Directory. */
    public record Component(String party, String name, String description) {
    }

    /**
     * ESR/Directory object reference: type, name, namespace, SWCV (caption + GUID) and OID. {@code program}: for a
     * program in an imported archive ({@code MAP_ARCHIVE_PRG}, name = archive) the Java class or file used, e.g.
     * {@code cz.acme.OrderMapping} or {@code xsl/Strip.xsl}; null otherwise.
     */
    public record ObjRef(String type, String name, String namespace, String swc, String swcGuid, String oid,
            String program) {

        public ObjRef(String type, String name, String namespace, String swc, String swcGuid, String oid) {
            this(type, name, namespace, swc, swcGuid, oid, null);
        }
    }

    /** Directory reference by party / component / name (channels). */
    public record DirRef(String party, String component, String name, String oid) {
    }

    /**
     * Integrated Configuration. Key: sender party / component, sender interface (name, namespace), optional
     * receiver party / component. {@code generatedFrom}: iFlow the ICO was generated from (description
     * "Generated for dir://IFLOW/...").
     */
    public record Ico(
            String oid,
            String description,
            String generatedFrom,
            Endpoint sender,
            List<Receiver> receivers,
            DynamicRouting dynamicRouting,
            String noReceiverBehaviour,
            String modifiedBy,
            String modifiedAt) {
    }

    /**
     * Extended (dynamic) receiver determination: an operation mapping computes the receivers at runtime
     * ({@code DynamicalRouting}, link role {@code RoutingProgram}); the receivers listed in the ICO are then the
     * possible receivers. {@code operationSpecific}: one mapping per sender operation (not yet seen on real data).
     */
    public record DynamicRouting(boolean operationSpecific, List<ObjRef> mappings) {
    }

    /** Sender: interface, the operations covered by the ICO and the sender channel. */
    public record Endpoint(String party, String component, ObjRef iface, List<String> operations, DirRef channel) {
    }

    /**
     * Receiver with its routing condition (null = unconditional) and quality of service (EO, EOIO, BE; null = as
     * the sender).
     */
    public record Receiver(String party, String component, Condition condition, String qualityOfService,
            List<InterfaceAssignment> interfaces, List<DirRef> channels) {
    }

    /**
     * Routing condition. Lines of the same {@code group} are combined with AND, groups with OR (SAP data model:
     * AtomicConditionBlock). {@code text} is the rendered expression, e.g. {@code (A = "1") OR (B = "2")}.
     */
    public record Condition(String text, List<ConditionLine> lines) {
    }

    /**
     * One elementary condition. Operands: XPath into the payload, {@code context:<name> (<namespace>)} for context
     * objects, quoted constants. {@code operator}: EQ, NE, CP (matches pattern), EX (exists), ...
     */
    public record ConditionLine(int group, String left, String operator, String right) {
    }

    /**
     * Receiver interface determination for one sender operation: the operation mapping and its target interfaces
     * (more than one for 1:n mappings), plus mapping parameters bound to channels (e.g. RFC lookup channels).
     */
    public record InterfaceAssignment(String operation, Condition condition, ObjRef mapping, List<Target> targets,
            Map<String, DirRef> parameters) {
    }

    /** Target interface of an interface determination with its multiplicity ({@code 1}, {@code 0..1}, ...). */
    public record Target(ObjRef iface, String multiplicity) {
    }

    /**
     * Communication channel. {@code direction}: sender | receiver. {@code adapter}: transport / message protocol
     * (e.g. {@code SOAP/XI}, {@code HTTP/REST}). {@code attributes}: adapter attributes with non-empty values
     * (environment specific values like URLs are included; passwords are never available).
     */
    public record Channel(
            DirRef id,
            String direction,
            String adapter,
            String transportProtocol,
            String messageProtocol,
            String engine,
            Map<String, String> attributes,
            List<Module> modules,
            String description) {
    }

    /** Adapter module in the channel pipeline; {@code type} L = local enterprise bean. */
    public record Module(int position, String name, String type, String namespace, Map<String, String> parameters) {
    }

    public record Esr(
            List<ServiceInterface> serviceInterfaces,
            List<OperationMapping> operationMappings,
            List<MessageMapping> messageMappings,
            List<MessageType> messageTypes,
            List<DataType> dataTypes,
            List<ExternalDefinition> externalDefinitions,
            List<ObjRef> mappingPrograms,
            List<FunctionLibrary> functionLibraries,
            List<ImportedArchive> importedArchives) {
    }

    /**
     * Imported archive ({@code TRAFO_JAR}): Java mapping programs and other classes using SAP APIs (read from the
     * byte code, not decompiled), XSLT and Java sources with their text; library classes only counted per package.
     *
     * @param fileName   uploaded file name
     * @param maven      groupId:artifactId:version from META-INF/maven (if packaged)
     * @param programs   mapping classes, classes using SAP APIs, XSLT and Java sources
     * @param packages   package → number of classes
     * @param otherFiles other files (resources), at most 200
     * @param sapApis    SAP / platform APIs used anywhere in the archive
     */
    public record ImportedArchive(ObjRef id, String fileName, String maven, List<ArchiveProgram> programs,
            Map<String, Integer> packages, List<String> otherFiles, List<String> sapApis, String description) {
    }

    /**
     * Program in an imported archive. {@code kind}: javaMapping | class | xslt | javaSource; {@code mappingApi}:
     * AbstractTransformation (PI 7.1+ API) or StreamTransformation (XI 3.0 API) for Java mappings, also when
     * inherited from a class of the same archive; {@code text}: XSLT / Java source.
     */
    public record ArchiveProgram(String path, String kind, String className, String superclass,
            List<String> interfaces, String mappingApi, List<String> sapApis, String text) {
    }

    /**
     * Value mapping group (Integration Directory {@code ValueMapping}) with its representations as stored:
     * {@code system}, {@code objectTypeName}, {@code value} (element names of the content XML). Values of entries
     * whose system or name looks like a secret (password, secret, token, ...) are masked.
     */
    public record ValueMappingGroup(String groupId, String groupName, List<ValueMappingEntry> entries) {
    }

    public record ValueMappingEntry(String system, String objectTypeName, String value) {
    }

    /** {@code category}: I inbound | O outbound | A abstract; {@code pattern}: interface pattern (L = stateless). */
    public record ServiceInterface(ObjRef id, String category, String pattern, String xmlNamespace,
            List<Operation> operations, String description) {
    }

    /** {@code mode}: sync | async. Messages: message types, fault messages or external messages. */
    public record Operation(String name, String mode, ObjRef request, ObjRef response, List<ObjRef> faults,
            String description) {
    }

    /** Operation mapping: source / target operation and one step per message (request, response, fault). */
    public record OperationMapping(ObjRef id, ObjRef sourceOperation, ObjRef targetOperation, List<MappingStep> steps,
            String description) {
    }

    /**
     * {@code kind}: request | response | fault. {@code program}: message mapping ({@code XI_TRAFO}), Java mapping
     * ({@code MAP_ARCHIVE_PRG}, class in an imported archive), XSLT, ...
     */
    public record MappingStep(String kind, ObjRef program, ObjRef sourceMessage, ObjRef targetMessage) {
    }

    /** Graphical message mapping; {@code logic}: field logic and user-defined functions (null if unreadable). */
    public record MessageMapping(ObjRef id, ObjRef source, ObjRef target, String multiplicity, String description,
            MappingLogic logic) {
    }

    /**
     * Field logic of a message mapping (from its MetaData blob).
     *
     * @param namespaces     prefix → namespace URI of the prefixes used in paths
     * @param fields         mapped target fields in mapping order
     * @param disabledFields target fields switched off ("disabled" in the mapping editor)
     * @param parameters     mapping parameters (import / export)
     * @param libraries      function libraries used ({@code FUNC_LIB}), details in {@link Esr#functionLibraries}
     * @param localFunctions user-defined functions of the mapping itself (null if none)
     */
    public record MappingLogic(Map<String, String> namespaces, List<FieldMapping> fields, List<String> disabledFields,
            List<MappingParameter> parameters, List<ObjRef> libraries, FunctionLibrary localFunctions) {
    }

    /**
     * One target field: {@code expression} is the mapping as a function term over source paths, e.g.
     * {@code concat(/ns0:A/B, "-", Lib.fn(/ns0:A/C))}; standard functions keep their SAP names ({@code iF},
     * {@code createIf}, {@code FixValues}, ...), constants are quoted, mapping parameters are {@code $name}.
     * {@code sources}: source paths used.
     */
    public record FieldMapping(String target, String expression, List<String> sources) {
    }

    /** {@code type}: XSD type or {@code channel:<adapter>}; {@code direction}: import | export. */
    public record MappingParameter(String name, String type, String direction, boolean mandatory) {
    }

    /**
     * User-defined functions: a function library ({@code FUNC_LIB}) or the local functions of a message mapping
     * ({@code id} null). {@code imports}: imports beyond the SAP defaults; {@code globals}, {@code init},
     * {@code cleanup}: Java code (null if empty); {@code archives}: imported archives ({@code TRAFO_JAR}) on the class
     * path; {@code sapApis}: SAP APIs used in globals / init / cleanup.
     */
    public record FunctionLibrary(ObjRef id, List<String> imports, String globals, String init, String cleanup,
            List<UserFunction> functions, List<ObjRef> archives, List<String> sapApis, String description) {
    }

    /**
     * User-defined function. {@code key}: ID used in mapping metadata; {@code execution}: singleValues |
     * contextValues | queueValues (UDF execution type); {@code sapApis}: SAP APIs used by the code (LookupService,
     * DynamicConfiguration, Trace, GlobalContainer, StructureNode, ValueMapping, JDBC, MessageHeader, ...).
     */
    public record UserFunction(String key, String name, String title, String description, String execution,
            List<FunctionArgument> arguments, String code, List<String> sapApis) {
    }

    /** {@code kind}: input | result | parameter. */
    public record FunctionArgument(String name, String kind, String javaType, String description) {
    }

    public record MessageType(ObjRef id, ObjRef dataType, String xmlNamespace, String description) {
    }

    /** Data type with its XSD as stored in the ESR; {@code uses}: referenced data types. */
    public record DataType(ObjRef id, List<ObjRef> uses, String xsd, String description) {
    }

    /** Imported WSDL / XSD with the messages it defines. */
    public record ExternalDefinition(ObjRef id, List<String> messages, String description) {
    }
}
