package io.poxray.nativeimage;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.migration.DossierReport;
import io.poxray.migration.DossierAssessment;
import io.poxray.model.ScaInventory;
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

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, NativeReflectionTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
