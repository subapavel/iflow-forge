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
        Esr esr,
        List<Issue> issues) {

    /** {@code system}: PO system (name, URL) for online reads; {@code files}: TPZ exports for offline reads. */
    public record Source(String system, String url, List<String> files) {
    }

    /** {@code direction}: in | out | both, relative to {@code component}. */
    public record Query(String component, String direction) {
    }

    /** Communication component (business system / business component) in the Integration Directory. */
    public record Component(String party, String name, String description) {
    }

    /** ESR/Directory object reference: type, name, namespace, SWCV (caption + GUID) and OID. */
    public record ObjRef(String type, String name, String namespace, String swc, String swcGuid, String oid) {
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
            String noReceiverBehaviour,
            String modifiedBy,
            String modifiedAt) {
    }

    public record Endpoint(String party, String component, ObjRef iface, String operation, DirRef channel) {
    }

    /** {@code condition}: routing condition as stored (null = unconditional). */
    public record Receiver(String party, String component, String condition, List<InterfaceAssignment> interfaces,
            List<DirRef> channels) {
    }

    /** Receiver interface determination: target interface / operation and the operation mapping used. */
    public record InterfaceAssignment(String operation, ObjRef iface, ObjRef mapping, String multiplicity) {
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
            List<ObjRef> mappingPrograms) {
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

    public record MessageMapping(ObjRef id, ObjRef source, ObjRef target, String multiplicity, String description) {
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
