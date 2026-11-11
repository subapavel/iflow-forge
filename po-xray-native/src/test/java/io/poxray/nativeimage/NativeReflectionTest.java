package io.poxray.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.migration.DossierReport;
import io.poxray.migration.DossierAssessment;
import io.poxray.model.ScaInventory;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmDataReport;
import io.poxray.model.BpmFaultReport;
import io.poxray.model.BehaviorReport;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Guards the native build: every class of io.poxray.model must be registered for reflection. */
class NativeReflectionTest {

    @Test
    void dossierEvidenceProjectionRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());
        for (String suffix : java.util.List.of("", "$Packet", "$PacketDocument", "$Versions", "$Claim", "$ReviewDocument", "$ReviewClaim", "$Use")) {
            org.junit.jupiter.api.Assertions.assertTrue(registered.contains("io.poxray.migration.DossierEvidenceReport" + suffix), suffix);
        }
    }

    @Test
    void behaviorComparisonInputAndReportRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());
        for (String name : java.util.List.of("BehaviorComparisonInput", "BehaviorComparisonInput$Implementation",
                "BehaviorComparisonInput$StepRef", "BehaviorComparisonInput$Alignment", "BehaviorComparisonInput$Evidence",
                "BehaviorComparisonReport", "BehaviorComparisonReport$Row", "BehaviorComparisonReport$Cell",
                "BehaviorComparisonReport$Facet", "BehaviorComparisonReport$Context"))
            org.junit.jupiter.api.Assertions.assertTrue(registered.contains("io.poxray.model." + name), name);
    }

    @Test
    void contractCatalogInputAndReportRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());
        for (String name : java.util.List.of("ContractCatalogInput", "ContractCatalogInput$Evidence", "ContractCatalogInput$StepRef",
                "ContractCatalogInput$ArtifactClaim", "ContractCatalogInput$PortClaim", "ContractCatalogInput$ValueClaim",
                "ContractCatalogInput$ContractClaim", "ContractCatalogInput$ResourceBinding", "ContractCatalogReport",
                "ContractCatalogReport$Artifact", "ContractCatalogReport$Contract", "ContractCatalogReport$Dependency"))
            org.junit.jupiter.api.Assertions.assertTrue(registered.contains("io.poxray.model." + name), name);
    }

    @Test
    void exportProvenanceInputAndManifestRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());
        for (String name : java.util.List.of("ExportProvenanceInput", "ExportProvenanceInput$ArtifactInput",
                "ExportProvenanceInput$VersionObservation", "ExportManifest", "ExportManifest$FileDigest"))
            org.junit.jupiter.api.Assertions.assertTrue(registered.contains("io.poxray.model." + name), name);
    }

    @Test
    void allModelClassesAreRegistered() throws IOException, URISyntaxException {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Arrays.stream(reg.targets()).map(Class::getName).collect(Collectors.toSet());

        assertThat(modelClasses()).isNotEmpty().allSatisfy(c -> assertThat(registered).as("registered").contains(c));
    }

    @Test
    void catalogRecordsExist() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        assertThat(reg.classNames()).allSatisfy(name -> assertThat(classExists(name)).as(name).isTrue());
    }

    @Test
    void externalContractRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());
        for (String nested : java.util.List.of("XmlName", "ContractDeclaration", "ContractDependency", "ContractResource")) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    registered.contains("io.poxray.model.IntegrationInventory$" + nested), nested);
        }
    }

    @Test
    void dossierReportAndAllSixNestedRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());

        assertThat(registered).contains(DossierReport.class.getName(), DossierReport.Scope.class.getName(),
                DossierReport.Decision.class.getName(), DossierReport.Risk.class.getName(),
                DossierReport.Dependency.class.getName(), DossierReport.DerivedDocument.class.getName(),
                DossierReport.Diagnostic.class.getName());
    }

    @Test
    void dossierAssessmentAndAllNineNestedRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());

        assertThat(registered).contains(DossierAssessment.class.getName(), DossierAssessment.Block.class.getName(),
                DossierAssessment.Question.class.getName(), DossierAssessment.Assumption.class.getName(),
                DossierAssessment.Risk.class.getName(), DossierAssessment.BasisInput.class.getName(),
                DossierAssessment.Basis.class.getName(), DossierAssessment.Coverage.class.getName(),
                DossierAssessment.Blocker.class.getName(), DossierAssessment.Milestone.class.getName());
    }

    @Test
    void bpmSourceEvidenceAndAllSixNestedRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());

        assertThat(registered).contains(BpmSourceEvidence.class.getName(), BpmSourceEvidence.Locator.class.getName(),
                BpmSourceEvidence.Element.class.getName(), BpmSourceEvidence.Property.class.getName(),
                BpmSourceEvidence.Candidate.class.getName(), BpmSourceEvidence.Endpoint.class.getName(),
                BpmSourceEvidence.Link.class.getName());
    }

    @Test
    void bpmPathReportAndAllTenNestedRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());

        assertThat(registered).contains(BpmPathReport.class.getName(), BpmPathReport.Input.class.getName(),
                BpmPathReport.ProcessKey.class.getName(), BpmPathReport.Fact.class.getName(), BpmPathReport.Flag.class.getName(),
                BpmPathReport.Endpoint.class.getName(), BpmPathReport.Condition.class.getName(), BpmPathReport.Step.class.getName(),
                BpmPathReport.Arc.class.getName(), BpmPathReport.Loop.class.getName(), BpmPathReport.ProcessPaths.class.getName());
    }

    @Test
    void bpmDataReportAndAllTenNestedRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());

        assertThat(registered).contains(BpmDataReport.class.getName(), BpmDataReport.ProcessData.class.getName(),
                BpmDataReport.ObjectData.class.getName(), BpmDataReport.MappingUse.class.getName(),
                BpmDataReport.Evidence.class.getName(), BpmDataReport.Name.class.getName(), BpmDataReport.Binding.class.getName(),
                BpmDataReport.Declaration.class.getName(), BpmDataReport.Read.class.getName(),
                BpmDataReport.OriginSet.class.getName(), BpmDataReport.StepState.class.getName());
    }

    @Test
    void bpmFaultReportAndAllNineNestedRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());

        assertThat(registered).contains(BpmFaultReport.class.getName(), BpmFaultReport.ProcessFaults.class.getName(),
                BpmFaultReport.Declaration.class.getName(), BpmFaultReport.Scope.class.getName(),
                BpmFaultReport.Definition.class.getName(), BpmFaultReport.FaultReference.class.getName(),
                BpmFaultReport.Builtin.class.getName(), BpmFaultReport.Parameter.class.getName(),
                BpmFaultReport.Evidence.class.getName(), BpmFaultReport.Unattributed.class.getName());
    }

    @Test
    void behaviorReportAndAllElevenNestedRecordsAreRegistered() {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());
        assertThat(registered).contains(BehaviorReport.class.getName(), BehaviorReport.Integration.class.getName(),
                BehaviorReport.IcoSource.class.getName(), BehaviorReport.ChannelSource.class.getName(),
                BehaviorReport.ChannelUse.class.getName(), BehaviorReport.ProcessWiring.class.getName(),
                BehaviorReport.InterfaceUse.class.getName(), BehaviorReport.InterfaceCandidate.class.getName(),
                BehaviorReport.IcoMatch.class.getName(), BehaviorReport.Route.class.getName(),
                BehaviorReport.StepCall.class.getName(), BehaviorReport.ServiceCandidate.class.getName());
    }

    /** Class names under io/poxray/model in the core classes (directory or jar). */
    private static Set<String> modelClasses() throws IOException, URISyntaxException {
        Path location = Path.of(ScaInventory.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isDirectory(location)) {
            return list(location.resolve("io/poxray/model"));
        }
        try (FileSystem jar = FileSystems.newFileSystem(location, Map.of())) {
            return list(jar.getPath("io/poxray/model"));
        }
    }

    private static Set<String> list(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".class"))
                    .map(n -> "io.poxray.model." + n.substring(0, n.length() - ".class".length()))
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void allPublicMonitoringRecordsAreRegistered() throws IOException, URISyntaxException {
        RegisterForReflection reg = NativeReflection.class.getAnnotation(RegisterForReflection.class);
        Set<String> registered = Stream.concat(Arrays.stream(reg.targets()).map(Class::getName),
                Arrays.stream(reg.classNames())).collect(Collectors.toSet());
        Path location = Path.of(io.poxray.monitoring.Message.class.getProtectionDomain().getCodeSource().getLocation()
                .toURI());
        Set<String> names;
        if (Files.isDirectory(location)) {
            names = classNames(location.resolve("io/poxray/monitoring"), "io.poxray.monitoring.");
        } else {
            try (FileSystem jar = FileSystems.newFileSystem(location, Map.of())) {
                names = classNames(jar.getPath("io/poxray/monitoring"), "io.poxray.monitoring.");
            }
        }
        Set<String> records = names.stream().map(NativeReflectionTest::load)
                .filter(c -> c.isRecord() && java.lang.reflect.Modifier.isPublic(c.getModifiers()))
                .map(Class::getName).collect(Collectors.toSet());

        assertThat(records).contains("io.poxray.monitoring.Message", "io.poxray.monitoring.SearchResult",
                "io.poxray.monitoring.LogEntry", "io.poxray.monitoring.PayloadPart",
                "io.poxray.monitoring.KeyResolver$Resolution$Key");
        assertThat(records).allSatisfy(c -> assertThat(registered).as("registered").contains(c));
    }

    private static Set<String> classNames(Path dir, String packagePrefix) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".class"))
                    .map(n -> packagePrefix + n.substring(0, n.length() - ".class".length()))
                    .collect(Collectors.toSet());
        }
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, NativeReflectionTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError("listed but not loadable: " + name, e);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, NativeReflectionTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
