package io.poxray.nativeimage;

import io.poxray.model.BpmComponent;
import io.poxray.model.BuiltinFunction;
import io.poxray.model.DataType;
import io.poxray.model.DcRef;
import io.poxray.model.DevelopmentComponent;
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
 * Output model = all of {@code io.poxray.model} (checked by NativeReflectionTest); catalog records of the built-in
 * function catalog loader are package-private, hence by name.
 */
@RegisterForReflection(
        targets = {
            ScaInventory.class, ScaInventory.Generator.class, ScaInventory.ScaSource.class,
            ScaInventory.SoftwareComponent.class,
            DevelopmentComponent.class, DevelopmentComponent.Archives.class, DcRef.class,
            BpmComponent.class, BpmComponent.Catalog.class,
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
            io.poxray.integration.BpmIntegration.ProcessIntegration.class,
            io.poxray.integration.BpmIntegration.InterfaceIntegration.class,
            io.poxray.integration.BpmIntegration.Use.class,
            IntegrationInventory.MappingLogic.class, IntegrationInventory.FieldMapping.class,
            IntegrationInventory.MappingParameter.class, IntegrationInventory.FunctionLibrary.class,
            IntegrationInventory.UserFunction.class, IntegrationInventory.FunctionArgument.class,
            IntegrationInventory.MessageType.class, IntegrationInventory.DataType.class,
            IntegrationInventory.ExternalDefinition.class
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
