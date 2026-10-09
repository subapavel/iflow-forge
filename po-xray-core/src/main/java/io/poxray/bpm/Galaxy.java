package io.poxray.bpm;

import java.util.Set;

/**
 * Galaxy (NW BPM) metaclass and association names. Link direction (f, s) per association is documented in
 * {@code docs/HANDOFF.md} §2.2 and verified on the DigiSign sample.
 */
final class Galaxy {

    private Galaxy() {
    }

    // --- metaclasses: workflow
    static final String COLLABORATION = "Galaxy.Workflow.Collaboration";
    static final String POOL = "Galaxy.Workflow.Pool";
    static final String LANE = "Galaxy.Workflow.Lane";
    static final String PROCESS_ROLE = "Galaxy.Workflow.ProcessRole";
    static final String LANE_PROCESS_ROLE = "Galaxy.Workflow.LaneProcessRole";
    static final String DATA_OBJECT = "Galaxy.Workflow.DataObject";
    static final String TEXT_VARIABLE = "Galaxy.Workflow.TextVariable";
    static final String ANNOTATION = "Galaxy.Workflow.Annotation";
    static final String ANNOTATION_CONNECTOR = "Galaxy.Workflow.AnnotationConnector";
    static final String SEQUENCE_CONNECTOR = "Galaxy.Workflow.SequenceConnector";
    static final String CONDITIONAL_SEQUENCE_CONNECTOR = "Galaxy.Workflow.ConditionalSequenceConnector";
    static final String START_EVENT = "Galaxy.Workflow.StartEvent";
    static final String END_EVENT = "Galaxy.Workflow.EndEvent";
    static final String BOUNDARY_EVENT = "Galaxy.Workflow.BoundaryEvent";
    static final String AUTOMATED_ACTIVITY = "Galaxy.Workflow.AutomatedActivity";
    static final String MAPPING_ACTIVITY = "Galaxy.Workflow.MappingActivity";
    static final String NOTIFICATION_ACTIVITY = "Galaxy.ProcessSupplements.NotificationActivity";
    static final String EXCLUSIVE_GATEWAY = "Galaxy.Workflow.ExclusiveDataSplitGateway";
    static final String UNCONTROLLED_JOIN = "Galaxy.Workflow.UncontrolledJoinGateway";
    static final String EMBEDDED_SCOPE = "Galaxy.Workflow.EmbeddedScope";
    static final String ERROR_EVENT_DEFINITION = "Galaxy.Workflow.ErrorEventDefinition";
    static final String MESSAGE_EVENT_DEFINITION = "Galaxy.Workflow.MessageEventDefinition";

    // --- metaclasses: rules, mappings, functions
    static final String EXPRESSION = "Galaxy.Rule.Expression";
    static final String REFERENCE = "Galaxy.Rule.Reference";
    static final String LITERAL = "Galaxy.Rule.Literal";
    static final String FUNCTION_INVOCATION = "Galaxy.Rule.FunctionInvocation";
    static final String CONDITION = "Galaxy.Rule.Condition";
    static final String PARENT = "Galaxy.Rule.Parent";
    static final String FUNCTION_INTERFACE = "Galaxy.Rule.FunctionInterface";
    static final String EJB_FUNCTION = "Galaxy.Rule.EjbFunction";
    static final String MAPPING = "Galaxy.Mapping.Mapping";
    static final String MAPPING_PART = "Galaxy.Mapping.MappingPart";
    static final String MAPPING_PART_CONFIGURATION = "Galaxy.Mapping.MappingPartConfiguration";
    static final String NS_PREFIX = "Galaxy.Mapping.NSPrefix";

    // --- metaclasses: WSDL / XSD / mc
    static final String WSDL_INTERFACE = "WSDL2.Interface";
    static final String WSDL_OPERATION = "WSDL2.Operation";
    static final String WSDL_FAULT = "WSDL2.Fault";
    static final String XSD_ELEMENT = "XSD1.XsdElementDeclaration";
    static final String XSD_COMPLEX_TYPE = "XSD1.XsdComplexTypeDefinition";
    static final String XSD_SIMPLE_TYPE = "XSD1.XsdSimpleTypeDefinition";
    static final String XSD_PARTICLE = "XSD1.XsdParticle";
    static final String XSD_MODEL_GROUP = "XSD1.XsdModelGroup";
    static final String QNAME = "XSD1.key.QName";
    static final String PROPERTY_BAG = "properties.PropertyBag";
    static final String SERVICE_REFERENCE = "mc.servicereferences.ServiceReference";

    // --- associations: workflow
    static final String SCOPE_CONTAINS_OBJECTS = "Galaxy.Workflow.ScopeContainsScopeObjects"; // f=object s=scope
    static final String LANE_HAS_OBJECTS = "Galaxy.Workflow.LaneHasScopeObjects"; // f=object s=lane
    static final String COLLABORATION_CONTAINS_POOLS = "Galaxy.Workflow.CollaborationContainsPools"; // f=pool s=collab
    static final String SOURCE_OF_CONNECTORS = "Galaxy.Workflow.SourceOfIncomingConnectors"; // f=source s=connector
    static final String TARGET_OF_CONNECTORS = "Galaxy.Workflow.TargetOfOutgoingConnectors"; // f=target s=connector
    static final String CONNECTOR_EXPRESSION =
            "Galaxy.Workflow.ConditionalSequenceConnectorContainsExpression"; // f=expr s=connector
    static final String ACTIVITY_OPERATION = "Galaxy.Workflow.ActivityHasOperation"; // f=operation s=activity
    static final String ACTIVITY_SERVICE_REFERENCE =
            "Galaxy.Workflow.AutomatedActivityHasServiceReference"; // f=serviceref s=activity
    static final String ACTIVITY_BOUNDARY_EVENT = "Galaxy.Workflow.ActivityHasBoundaryEvent"; // f=event s=activity
    static final String ACTIVITY_EVENT_DEFINITIONS =
            "Galaxy.Workflow.ActivityContainsEventDefinitions"; // f=eventdef s=activity
    static final String CATCH_EVENT_TRIGGER = "Galaxy.Workflow.CatchEventHasTrigger"; // f=trigger s=event
    static final String CATCH_EVENT_EXPRESSION = "Galaxy.Workflow.CatchEventContainsExpression"; // f=event s=expr
    static final String THROW_EVENT_RESULT = "Galaxy.Workflow.ThrowEventHasResult"; // f=eventdef s=end event
    static final String ERROR_EVENT_FAULT = "Galaxy.Workflow.ErrorEventDefinitionHasWSDLFault"; // f=fault s=eventdef
    static final String EVENT_DEFINITION_PARAMETER =
            "Galaxy.Workflow.EventDefinitionHasParameter"; // f=xsd element s=eventdef
    static final String MESSAGE_EVENT_OPERATION = "Galaxy.Workflow.MessageEventDefinitionHasOperation";
    static final String INPUT_MAPPING = "Galaxy.Workflow.InputMappingOfMappableObject"; // f=mapping s=object
    static final String OUTPUT_MAPPING = "Galaxy.Workflow.OutputMappingOfMappableObject"; // f=mapping s=object
    static final String VIEW_MAPPING = "Galaxy.Workflow.ViewContainsMapping"; // f=mapping s=role/text variable
    static final String DATA_CONTAINER_TYPE =
            "Galaxy.Workflow.DataContainerReferencesXsdElementDeclaration"; // f=xsd element s=data object
    static final String ANNOTATION_OF_CONNECTOR =
            "Galaxy.Workflow.AnnotationConnectorHasAnnotation"; // f=annotation s=annotation connector
    static final String CONNECTOR_MODEL_ELEMENT =
            "Core.AnnotationConnectorHasModelElement"; // f=model element s=annotation connector

    // --- associations: rules / mappings / functions
    static final String MAPPING_PARTS = "Galaxy.Mapping.MappingContainsMappingParts"; // f=part s=mapping (roots)
    static final String PART_CHILDREN = "Galaxy.Mapping.MappingPartHasChildren"; // f=child s=parent
    static final String PART_QNAME = "Galaxy.Mapping.MappingPartHasQName"; // f=qname s=part
    static final String PART_EXPRESSION = "Galaxy.Mapping.MappingPartContainsExpression"; // f=expr s=part
    static final String EXPRESSION_STEPS = "Galaxy.Rule.ExpressionHasSteps"; // f=step s=expr, file order
    static final String REFERENCE_QNAME = "Galaxy.Rule.ReferenceHasQName"; // f=qname s=reference
    static final String REFERENCE_FEATURE = "Galaxy.Rule.ReferencePointToXsdFeature"; // f=xsd feature s=reference
    static final String INVOCATION_FUNCTION =
            "Galaxy.Rule.FunctionInvocationHasFunctionInterface"; // f=function s=invocation
    static final String INVOCATION_PARAMETERS =
            "Galaxy.Rule.FunctionInvocationHasParameterMapping"; // f=mapping s=invocation
    static final String LITERAL_TYPE = "Galaxy.Rule.LiteralHasSimpleDataType"; // f=type s=literal
    static final String CONDITION_CONDITION = "Galaxy.Rule.ConditionHasConditionExpression"; // f=expr s=condition
    static final String CONDITION_POSITIVE = "Galaxy.Rule.ConditionHasPositiveExpression";
    static final String CONDITION_NEGATIVE = "Galaxy.Rule.ConditionHasNegativeExpression";
    static final String FUNCTION_PARAMETER =
            "Galaxy.Rule.FunctionInterfaceHasFormalParameter"; // f=xsd element s=function interface
    static final String FUNCTION_RESULT = "Galaxy.Rule.FunctionInterfaceHasResult"; // f=xsd element s=fi
    static final String FUNCTION_IMPLEMENTATIONS = "Galaxy.Rule.FunctionInterfaceHasFunctions"; // f=ejb fn s=fi

    // --- associations: WSDL / XSD / mc
    static final String INTERFACE_OPERATIONS = "WSDL2.InterfaceOperations"; // f=operation s=interface
    static final String OPERATION_MESSAGES = "WSDL2.OperationMessages"; // f=message reference s=operation
    static final String OPERATION_FAULTS = "WSDL2.OperationFaults"; // f=fault reference s=operation
    static final String OPERATION_PATTERN = "WSDL2.OperationPattern"; // f=pattern s=operation
    static final String REFERENCE_TO_ELEMENT = "WSDL2.ReferenceToElement"; // f=message ref/fault s=xsd element
    static final String REFERENCE_TO_INTERFACE_FAULT = "WSDL2.ReferenceToInterfaceFault"; // f=fault s=fault ref
    static final String ELEMENT_TYPE = "XSD1.TypeDefinitionForElementDeclaration"; // f=element s=type
    static final String ELEMENT_ANONYMOUS_TYPE = "XSD1.AnonymousTypeDefinitionForElementDeclaration";
    static final String COMPLEX_TYPE_CONTENT = "XSD1.ContentForComplexTypeDefinition"; // f=complex type s=particle
    static final String PARTICLE_CONTENT = "XSD1.ContentForParticle"; // f=particle s=model group/element
    static final String MODEL_GROUP_PARTICLES = "XSD1.ParticlesForModelGroup"; // f=model group s=particle
    static final String SIMPLE_TYPE_BASE = "XSD1.BaseTypeDefinitionForSimpleTypeDefinition"; // f=type s=base
    static final String SERVICE_REFERENCE_QNAME = "mc.servicereferences.serviceref_qname"; // f=qname s=serviceref
    static final String SERVICE_REFERENCE_AUTH_PROFILE = "mc.servicereferences.serviceref_authenticationProfile";
    static final String SERVICE_REFERENCE_LOGICAL_SYSTEM = "mc.servicereferences.serviceref_logicalSystem";
    static final String SERVICE_REFERENCE_ATTRIBUTES = "mc.servicereferences.ref_CustomAttributes";

    /** Metaclasses in process partitions that the reader interprets (directly or as part of another element). */
    static final Set<String> HANDLED_IN_PROCESS = Set.of(
            COLLABORATION, POOL, LANE, PROCESS_ROLE, LANE_PROCESS_ROLE, DATA_OBJECT, TEXT_VARIABLE, ANNOTATION,
            ANNOTATION_CONNECTOR, SEQUENCE_CONNECTOR, CONDITIONAL_SEQUENCE_CONNECTOR, START_EVENT, END_EVENT,
            BOUNDARY_EVENT, AUTOMATED_ACTIVITY, MAPPING_ACTIVITY, NOTIFICATION_ACTIVITY, EXCLUSIVE_GATEWAY,
            UNCONTROLLED_JOIN,
            EMBEDDED_SCOPE, ERROR_EVENT_DEFINITION, EXPRESSION, REFERENCE, LITERAL, FUNCTION_INVOCATION, CONDITION,
            PARENT, MAPPING, MAPPING_PART, MAPPING_PART_CONFIGURATION, NS_PREFIX, QNAME, PROPERTY_BAG);

    /** XSD and WSDL elements: read on demand (types) or by the catalog (inline interfaces), wherever they live. */
    static boolean isXsdOrWsdl(String metaclass) {
        return metaclass.startsWith("XSD1.") || metaclass.startsWith("WSDL2.");
    }
}
