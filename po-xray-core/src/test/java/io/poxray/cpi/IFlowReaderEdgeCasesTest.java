package io.poxray.cpi;

import static io.poxray.cpi.IFlowFixture.END;
import static io.poxray.cpi.IFlowFixture.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link IFlowReader} behaviour beyond the reading rules' main cases, on synthetic artifacts. */
class IFlowReaderEdgeCasesTest {

    private static Path simple(String id, Path dir) throws IOException {
        return IFlowFixture.iflow(id).name("Flow " + id).version("1.0.0")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .write(dir.resolve(id));
    }

    @Test
    void manifestWithLfLineEnds(@TempDir Path dir) throws Exception {
        Path artifact = simple("my.lf.id", dir);
        Path manifest = artifact.resolve("META-INF/MANIFEST.MF");
        Files.writeString(manifest, Files.readString(manifest).replace("\r\n", "\n"), StandardCharsets.UTF_8);
        assertThat(Files.readString(manifest)).doesNotContain("\r")
                .contains("Bundle-SymbolicName: my.l\n f.id;singleton:=true\n");

        IFlow f = IFlowReader.read(artifact);

        assertThat(f.id()).isEqualTo("my.lf.id");
        assertThat(f.name()).isEqualTo("Flow my.lf.id");
        assertThat(f.version()).isEqualTo("1.0.0");
    }

    @Test
    void unreachedChainKeepsItsFlowOrder(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("Y", "Y", "Enricher").step("Z", "Z", "Script").step("A", "A", "Script")
                .flow(START, "A").flow("A", END).flow("Z", "Y").flow("Y", END)
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "Z", "Y");
        assertThat(steps).extracting(Step::reached).containsExactly(true, false, false);
        assertThat(steps).extracting(Step::depth).containsExactly(0, 0, 0);
    }

    @Test
    void callToAnUnknownProcessIsListedAlone(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("Call", "Call nowhere", "ProcessCallElement", "processId", "Process_Missing")
                .step("B", "B", "Enricher")
                .flow(START, "Call").flow("Call", "B").flow("B", END)
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("Call nowhere", "B");
        assertThat(steps.getFirst().properties()).containsEntry("processId", "Process_Missing");
    }

    @Test
    void idempotentProcessCallInlinesItsProcess(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .messageFlow("In", "JMS", "Sender")
                .step("A", "A", "Script")
                .step("Idem", "Idempotent Process Call", "IdempotentProcessCall", "processId", "X",
                        "skipOnDuplicate", "true")
                .step("B", "B", "Enricher")
                .flow(START, "A").flow("A", "Idem").flow("Idem", "B").flow("B", END)
                .localProcess("X", "Local X", p -> p.step("X1", "X1", "ExternalCall").flow(p.start(), "X1")
                        .flow("X1", p.end()))
                .localProcessesFirst()
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "Idempotent Process Call", "X1", "B");
        assertThat(steps).extracting(Step::depth).containsExactly(0, 0, 1, 0);
        assertThat(steps).allMatch(Step::reached);
    }

    @Test
    void localProcessNeverCalledIsListedAfterTheMainProcessAtDepthZero(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .messageFlow("In", "JMS", "Sender")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .localProcess("X", "Local X", p -> p.step("X1", "X1", "Script").flow(p.start(), "X1")
                        .flow("X1", p.end()))
                .localProcessesFirst()
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "X1");
        assertThat(steps).extracting(Step::depth).containsExactly(0, 0);
        assertThat(steps).allMatch(Step::reached);
    }

    @Test
    void readAllSearchesFoldersOfPackagesAndPackageZips(@TempDir Path dir) throws Exception {
        Path label = dir.resolve("label");
        simple("c.three", label.resolve("package-one"));
        Files.writeString(label.resolve("package-one/CHANGELOG.txt"), "Change log\n");
        simple("a.one", label.resolve("package-two"));
        Path b = simple("b.two", dir.resolve("src"));
        Path export = IFlowFixture.packageExport(dir.resolve("export"), List.of(b), "Change log\r\n");
        IFlowFixture.zip(export, label.resolve("zips/package-three.zip"));

        List<IFlow> all = IFlowReader.readAll(label);

        assertThat(all).extracting(IFlow::id).containsExactly("a.one", "b.two", "c.three");
        assertThat(all.get(1)).isEqualTo(IFlowReader.read(b));
    }

    @Test
    void aFolderWithoutManifestIsNoArtifact(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("readme.txt"), "no artifact");

        assertThatThrownBy(() -> IFlowReader.read(dir)).isInstanceOf(IOException.class)
                .hasMessageContaining("MANIFEST.MF");
        assertThatThrownBy(() -> IFlowReader.readAll(dir.resolve("missing"))).isInstanceOf(IOException.class);
    }
}
