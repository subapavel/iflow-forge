package io.poxray.nativeimage;

import io.poxray.cpi.IFlow;
import io.poxray.cpi.PackageExport;
import io.poxray.cpi.Param;
import io.poxray.cpi.Participant;
import io.poxray.cpi.Step;
import io.poxray.migration.DossierReport;
import io.poxray.migration.DossierAssessment;
import io.poxray.model.AttributesReport;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmDataReport;
import io.poxray.model.BpmFaultReport;
import io.poxray.model.BehaviorReport;
import io.poxray.model.BehaviorSnapshots;
import io.poxray.model.BpmInventoryReport;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BuiltinFunction;
import io.poxray.model.DataType;
import io.poxray.model.DcRef;
import io.poxray.model.DevelopmentComponent;
import io.poxray.model.DiscoveryReport;
import io.poxray.model.EjbModule;
import io.poxray.model.EventDefinition;
import io.poxray.model.Expression;
import io.poxray.model.Flow;
import io.poxray.model.Function;
import io.poxray.model.FunctionCall;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.Interface;
import io.poxray.model.Issue;
import io.poxray.model.Mapping;
import io.poxray.model.Node;
import io.poxray.model.Process;
import io.poxray.model.QNameRef;
import io.poxray.model.Ref;
import io.poxray.model.ScaInventory;
import io.poxray.model.ServiceReference;
import io.poxray.model.UnmappedElement;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Native image: classes (de)serialised reflectively by Jackson 3 (no Quarkus extension for Jackson 3).
 * Output model = all of {@code io.poxray.model} (checked by NativeReflectionTest) plus the iFlow reader records of
 * {@code io.poxray.cpi}; catalog records of the built-in function catalog loader are package-private, hence by name.
 */
@RegisterForReflection(
        targets = {
            ScaInventory.class, ScaInventory.Generator.class, ScaInventory.ScaSource.class,
            ScaInventory.SoftwareComponent.class,
            DevelopmentComponent.class, DevelopmentComponent.Archives.class, DcRef.class,
            BpmComponent.class, BpmComponent.Catalog.class,
            BpmSourceEvidence.class, BpmSourceEvidence.Locator.class, BpmSourceEvidence.Element.class,
            BpmSourceEvidence.Property.class, BpmSourceEvidence.Candidate.class, BpmSourceEvidence.Endpoint.class,
            BpmSourceEvidence.Link.class,
            BpmPathReport.class, BpmPathReport.Input.class, BpmPathReport.ProcessKey.class,
            BpmPathReport.Fact.class, BpmPathReport.Flag.class, BpmPathReport.Endpoint.class,
            BpmPathReport.Condition.class, BpmPathReport.Step.class, BpmPathReport.Arc.class,
            BpmPathReport.Loop.class, BpmPathReport.ProcessPaths.class,
            BpmDataReport.class, BpmDataReport.ProcessData.class, BpmDataReport.ObjectData.class,
            BpmDataReport.MappingUse.class, BpmDataReport.Evidence.class, BpmDataReport.Name.class,
            BpmDataReport.Binding.class, BpmDataReport.Declaration.class, BpmDataReport.Read.class,
            BpmDataReport.OriginSet.class, BpmDataReport.StepState.class,
            BpmFaultReport.class, BpmFaultReport.ProcessFaults.class, BpmFaultReport.Declaration.class,
            BpmFaultReport.Scope.class, BpmFaultReport.Definition.class, BpmFaultReport.FaultReference.class,
            BpmFaultReport.Builtin.class, BpmFaultReport.Parameter.class, BpmFaultReport.Evidence.class,
            BpmFaultReport.Unattributed.class,
            BehaviorSnapshots.class, BehaviorReport.class, BehaviorReport.Integration.class, BehaviorReport.IcoSource.class,
            BehaviorReport.ChannelSource.class, BehaviorReport.ChannelUse.class, BehaviorReport.ProcessWiring.class,
            BehaviorReport.InterfaceUse.class, BehaviorReport.InterfaceCandidate.class, BehaviorReport.IcoMatch.class,
            BehaviorReport.Route.class, BehaviorReport.StepCall.class, BehaviorReport.ServiceCandidate.class,
            Process.class, Process.Pool.class, Process.Lane.class, Process.Role.class, Process.Scope.class,
            Process.DataObject.class, Process.TypeRef.class, Process.Annotation.class, Process.EditorArtifacts.class,
            Node.class, Node.OperationRef.class, Node.InterfaceRef.class, Node.Trigger.class, Node.ErrorEvent.class,
            Node.Builder.class,
            Flow.class, Expression.class, FunctionCall.class, FunctionCall.Argument.class,
            Mapping.class, Mapping.Assignment.class,
            Interface.class, Interface.Operation.class, Interface.Fault.class, Interface.Usage.class,
            ServiceReference.class, io.poxray.model.EsrOperationMapping.class, Function.class, Function.EjbLink.class, Function.Parameter.class, Function.Usage.class,
            BuiltinFunction.class, EventDefinition.class, DataType.class, DataType.Field.class,
            Issue.class, Issue.Location.class, UnmappedElement.class, Ref.class, QNameRef.class,
            EjbModule.class, EjbModule.Bean.class, EjbModule.Interfaces.class, EjbModule.WebService.class,
            EjbModule.Usage.class, EjbModule.SourceRef.class, EjbModule.JavaClass.class, EjbModule.JpaEntity.class,
            EjbModule.PersistenceUnit.class, EjbModule.JobDefinition.class,
            IntegrationInventory.class, IntegrationInventory.Source.class, IntegrationInventory.Query.class,
            IntegrationInventory.Component.class, IntegrationInventory.ObjRef.class, IntegrationInventory.DirRef.class,
            IntegrationInventory.Ico.class, IntegrationInventory.DynamicRouting.class, IntegrationInventory.Endpoint.class, IntegrationInventory.Receiver.class,
            IntegrationInventory.InterfaceAssignment.class, IntegrationInventory.Target.class,
            IntegrationInventory.Condition.class, IntegrationInventory.ConditionLine.class,
            IntegrationInventory.Channel.class,
            IntegrationInventory.Module.class, IntegrationInventory.Esr.class, IntegrationInventory.ServiceInterface.class,
            IntegrationInventory.Operation.class, IntegrationInventory.OperationMapping.class,
            IntegrationInventory.MappingStep.class, IntegrationInventory.MessageMapping.class,
            IntegrationInventory.ObjectSummary.class, IntegrationInventory.ObjectDetail.class,
            IntegrationInventory.ObjectLink.class, IntegrationInventory.ImportedArchive.class,
            IntegrationInventory.ArchiveProgram.class, IntegrationInventory.ValueMappingGroup.class,
            IntegrationInventory.ValueMappingEntry.class,
            io.poxray.integration.ObjectCatalog.SearchResult.class,
            io.poxray.integration.BpmIntegration.Report.class,
            io.poxray.integration.BpmIntegration.ProcessIntegration.class,
            io.poxray.integration.BpmIntegration.InterfaceIntegration.class,
            io.poxray.integration.BpmIntegration.Use.class,
            IntegrationInventory.MappingLogic.class, IntegrationInventory.FieldMapping.class,
            IntegrationInventory.MappingParameter.class, IntegrationInventory.FunctionLibrary.class,
            IntegrationInventory.UserFunction.class, IntegrationInventory.FunctionArgument.class,
            IntegrationInventory.MessageType.class, IntegrationInventory.DataType.class,
            IntegrationInventory.ExternalDefinition.class,
            IntegrationInventory.XmlName.class, IntegrationInventory.ContractDeclaration.class,
            IntegrationInventory.ContractDependency.class, IntegrationInventory.ContractResource.class,
            io.poxray.integration.ContractResources.class,
            AttributesReport.class, AttributesReport.Scenario.class,
            AttributesReport.IcoRef.class, AttributesReport.Attribute.class,
            AttributesReport.ModuleRef.class,
            BpmInventoryReport.class, BpmInventoryReport.ProcessRow.class,
            BpmInventoryReport.EjbRow.class, BpmInventoryReport.Totals.class,
            DiscoveryReport.class, DiscoveryReport.ProcessRow.class, DiscoveryReport.InterfaceRow.class,
            DiscoveryReport.IcoRow.class, DiscoveryReport.ComponentRow.class,
            DossierReport.class, DossierReport.Scope.class, DossierReport.Decision.class, DossierReport.Risk.class,
            DossierReport.Dependency.class, DossierReport.DerivedDocument.class, DossierReport.Diagnostic.class,
            DossierAssessment.class, DossierAssessment.Block.class, DossierAssessment.Question.class,
            DossierAssessment.Assumption.class, DossierAssessment.Risk.class, DossierAssessment.BasisInput.class,
            DossierAssessment.Basis.class, DossierAssessment.Coverage.class, DossierAssessment.Blocker.class,
            DossierAssessment.Milestone.class,
            IFlow.class, Participant.class, Step.class, Param.class, PackageExport.class, PackageExport.Entry.class
        },
        classNames = {
            "io.poxray.bpm.BuiltinFunctionCatalog$Data",
            "io.poxray.bpm.BuiltinFunctionCatalog$Entry",
            "io.poxray.bpm.BuiltinFunctionCatalog$Parameter",
            "io.poxray.bpm.BuiltinFunctionCatalog$Trigger",
            "io.poxray.bpm.BuiltinFunctionCatalog$TriggerParameter"
        })
public final class NativeReflection {

    private NativeReflection() {
    }
}
