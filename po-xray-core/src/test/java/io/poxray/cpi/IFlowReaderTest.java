package io.poxray.cpi;

import static io.poxray.cpi.IFlowFixture.END;
import static io.poxray.cpi.IFlowFixture.START;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link IFlowReader} on synthetic artifacts built by {@link IFlowFixture}. */
class IFlowReaderTest {

    @Test
    void manifestFields(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id").name("My Flow").version("1.0.0")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .write(dir.resolve("my.id"));
        assertThat(Files.readString(artifact.resolve("META-INF/MANIFEST.MF")))
                .as("fixture folds the symbolic name").contains("Bundle-SymbolicName: my\r\n .id;singleton:=true\r\n");

        IFlow f = IFlowReader.read(artifact);

        assertThat(f.id()).isEqualTo("my.id");
        assertThat(f.name()).isEqualTo("My Flow");
        assertThat(f.version()).isEqualTo("1.0.0");
    }

    @Test
    void participantsWithComponentType(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .messageFlow("In", "JMS", "Sender", "InQueue", "QueueName_inbound", "{{Q_IN}}")
                .messageFlow("Out", "ProcessDirect", "Receiver", "OutEndpoint", "address", "/out")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .write(dir.resolve("my.id"));

        IFlow f = IFlowReader.read(artifact);

        Map<String, String> inProps = new LinkedHashMap<>();
        inProps.put("ComponentType", "JMS");
        inProps.put("Description", "");
        inProps.put("ComponentNS", "sap");
        inProps.put("Name", "In");
        inProps.put("componentVersion", "1.0");
        inProps.put("direction", "Sender");
        inProps.put("QueueName_inbound", "{{Q_IN}}");
        Map<String, String> outProps = new LinkedHashMap<>();
        outProps.put("ComponentType", "ProcessDirect");
        outProps.put("Description", "");
        outProps.put("ComponentNS", "sap");
        outProps.put("Name", "Out");
        outProps.put("componentVersion", "1.0");
        outProps.put("direction", "Receiver");
        outProps.put("address", "/out");
        assertThat(f.participants()).containsExactly(
                new Participant("In", "JMS", "Sender", "InQueue", inProps),
                new Participant("Out", "ProcessDirect", "Receiver", "OutEndpoint", outProps));
        assertThat(f.participants().get(0).properties()).as("document order").containsExactlyEntriesOf(inProps);
        assertThat(f.participants().get(1).properties()).as("document order").containsExactlyEntriesOf(outProps);
    }

    @Test
    void participantEndpointEmptyWhenUnresolved(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .messageFlow("In", "JMS", "Sender", null)
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .write(dir.resolve("my.id"));
        assertThat(Files.readString(artifact.resolve(IFlowFixture.IFLW_DIR).resolve("my.id.iflw")))
                .as("fixture: the flow's sourceRef names no element").contains("sourceRef=\"Participant_1\"")
                .doesNotContain("id=\"Participant_1\"");

        IFlow f = IFlowReader.read(artifact);

        assertThat(f.participants()).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("In");
            assertThat(p.componentType()).isEqualTo("JMS");
            assertThat(p.direction()).isEqualTo("Sender");
            assertThat(p.endpoint()).isEmpty();
        });
    }

    @Test
    void stepsInSequenceFlowOrder(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("B", "B", "Enricher")
                .step("A", "A", "Script", "script", "a.groovy")
                .flow("B", END).flow("A", "B").flow(START, "A")
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "B");
        assertThat(steps).extracting(Step::activityType).containsExactly("Script", "Enricher");
        assertThat(steps).extracting(Step::depth).containsExactly(0, 0);
        assertThat(steps).allMatch(Step::reached);
        assertThat(steps.getFirst().properties()).containsEntry("activityType", "Script")
                .containsEntry("script", "a.groovy");
    }

    @Test
    void eventSubprocessListedAfterFlowStepsWithDepthOne(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("A", "A", "Script").step("B", "B", "Enricher")
                .subprocess("Sub1", "Exception Subprocess 1", s -> s
                        .step("H", "H", "Script", "script", "h.groovy")
                        .flow(s.start(), "H").flow("H", s.end()))
                .flow(START, "A").flow("A", "B").flow("B", END)
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "B", "Exception Subprocess 1", "H");
        assertThat(steps).extracting(Step::depth).containsExactly(0, 0, 0, 1);
        assertThat(steps.get(2).activityType()).isEqualTo("ErrorEventSubProcessTemplate");
        assertThat(steps).allMatch(Step::reached);
    }

    @Test
    void localProcessInlinedUnderProcessCall(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("A", "A", "Script")
                .step("CallX", "Call X", "ProcessCallElement", "processId", "X")
                .step("B", "B", "Enricher")
                .step("CallX2", "Call X again", "ProcessCallElement", "processId", "X")
                .flow(START, "A").flow("A", "CallX").flow("CallX", "B").flow("B", "CallX2").flow("CallX2", END)
                .localProcess("X", "Local X", p -> p
                        .step("X1", "X1", "Script")
                        .flow(p.start(), "X1").flow("X1", p.end()))
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "Call X", "X1", "B", "Call X again");
        assertThat(steps).extracting(Step::depth).containsExactly(0, 0, 1, 0, 0);
        assertThat(steps).allMatch(Step::reached);
    }

    @Test
    void mainProcessIsTheOneTheSenderFlowTargetsEvenWhenNotFirst(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .messageFlow("In", "JMS", "Sender")
                .step("A", "A", "Script")
                .step("CallX", "Call X", "ProcessCallElement", "processId", "X")
                .flow(START, "A").flow("A", "CallX").flow("CallX", END)
                .localProcess("X", "Local X", p -> p
                        .step("X1", "X1", "Script")
                        .flow(p.start(), "X1").flow("X1", p.end()))
                .localProcessesFirst()
                .write(dir.resolve("my.id"));
        String iflw = Files.readString(artifact.resolve(IFlowFixture.IFLW_DIR).resolve("my.id.iflw"));
        assertThat(iflw.indexOf("<bpmn2:process id=\"X\""))
                .as("fixture writes the local process first").isNotNegative().isLessThan(iflw.indexOf("<bpmn2:process id=\"Process_1\""));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "Call X", "X1");
        assertThat(steps).extracting(Step::depth).containsExactly(0, 0, 1);
    }

    @Test
    void cycleTerminates(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("A", "A", "Script").step("B", "B", "Enricher")
                .flow(START, "A").flow("A", "B").flow("B", "A").flow("B", END)
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "B");
    }

    @Test
    void unreachedStepFlagged(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("Z", "Z", "Script")
                .step("A", "A", "Script").step("B", "B", "Enricher")
                .flow(START, "A").flow("A", "B").flow("B", END).flow("Z", END)
                .write(dir.resolve("my.id"));

        List<Step> steps = IFlowReader.read(artifact).steps();

        assertThat(steps).extracting(Step::name).containsExactly("A", "B", "Z");
        assertThat(steps).extracting(Step::reached).containsExactly(true, true, false);
    }

    @Test
    void parametersDefaultAndConfigured(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .param("p1", "d1").param("p2", "d2").param("p3", null)
                .configured("p1", "secret")
                .write(dir.resolve("my.id"));

        IFlow f = IFlowReader.read(artifact);

        assertThat(f.parameters()).containsExactly(
                new Param("p1", "d1", true), new Param("p2", "d2", false), new Param("p3", "", false));
        assertThat(IFlowDump.json(f)).doesNotContain("secret");
        assertThat(f.toString()).doesNotContain("secret");
    }

    @Test
    void resourcesListed(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.id")
                .step("A", "A", "Script").flow(START, "A").flow("A", END)
                .param("p1", "d1").configured("p1", "v1")
                .resource("script/x.groovy", "return message")
                .resource("mapping/y.xsl", "<xsl:stylesheet version=\"2.0\"/>")
                .resource("xsd/z.xsd", "<xs:schema/>")
                .write(dir.resolve("my.id"));

        IFlow f = IFlowReader.read(artifact);

        assertThat(f.resources()).containsExactly("mapping/y.xsl", "script/x.groovy", "xsd/z.xsd");
    }

    @Test
    void scriptCollectionWithoutIflwReads(@TempDir Path dir) throws Exception {
        Path artifact = IFlowFixture.iflow("my.scripts").name("My Scripts").version("2.0.1").scriptCollection()
                .resource("script/a.groovy", "return message")
                .write(dir.resolve("my.scripts"));

        IFlow f = IFlowReader.read(artifact);

        assertThat(f.id()).isEqualTo("my.scripts");
        assertThat(f.name()).isEqualTo("My Scripts");
        assertThat(f.version()).isEqualTo("2.0.1");
        assertThat(f.participants()).isEmpty();
        assertThat(f.steps()).isEmpty();
        assertThat(f.parameters()).isEmpty();
        assertThat(f.resources()).containsExactly("script/a.groovy");
    }

    @Test
    void zipFolderAndPackageYieldSameIFlows(@TempDir Path dir) throws Exception {
        Path unpacked = dir.resolve("unpacked");
        Path b = IFlowFixture.iflow("b.two").name("B Two").version("2.0.0")
                .messageFlow("In", "JMS", "Sender")
                .step("B1", "B1", "XSLTMapping").flow(START, "B1").flow("B1", END)
                .param("q", null).resource("mapping/b.xsl", "<xsl:stylesheet version=\"2.0\"/>")
                .write(unpacked.resolve("b.two"));
        Path a = IFlowFixture.iflow("a.one").name("A One").version("1.0.0")
                .messageFlow("In", "JMS", "Sender").messageFlow("Out", "ProcessDirect", "Receiver")
                .step("A1", "A1", "Script", "script", "a.groovy").step("A2", "A2", "Enricher")
                .subprocess("Sub1", "Exception Subprocess 1", s -> s
                        .step("H", "H", "Script").flow(s.start(), "H").flow("H", s.end()))
                .flow(START, "A1").flow("A1", "A2").flow("A2", END)
                .param("p", "d").resource("script/a.groovy", "return message")
                .write(unpacked.resolve("a.one"));
        Path zips = dir.resolve("zips");
        IFlowFixture.zip(b, zips.resolve("b.two.zip"));
        Path aZip = IFlowFixture.zip(a, zips.resolve("a.one.zip"));
        Path export = IFlowFixture.packageExport(dir.resolve("export"), List.of(b, a), "Change log\r\n");
        Path exportZip = IFlowFixture.zip(export, dir.resolve("export.zip"));

        List<IFlow> expected = List.of(IFlowReader.read(a), IFlowReader.read(b));
        assertThat(expected).extracting(IFlow::id).containsExactly("a.one", "b.two");
        assertThat(expected).allSatisfy(f -> assertThat(f.steps()).isNotEmpty());

        assertThat(IFlowReader.readAll(unpacked)).as("folder of unpacked folders").isEqualTo(expected);
        assertThat(IFlowReader.readAll(zips)).as("folder of zips").isEqualTo(expected);
        assertThat(IFlowReader.readAll(export)).as("package export folder").isEqualTo(expected);
        assertThat(IFlowReader.readAll(exportZip)).as("package export zip").isEqualTo(expected);
        assertThat(IFlowReader.read(aZip)).as("read(zip)").isEqualTo(IFlowReader.read(a));
    }
}
