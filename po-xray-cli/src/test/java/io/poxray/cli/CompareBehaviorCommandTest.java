package io.poxray.cli;

import io.poxray.SyntheticComparison;
import io.poxray.json.Json;
import io.poxray.migration.BehaviorComparison;
import io.poxray.model.BehaviorComparisonReport;
import io.quarkus.test.junit.main.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusMainTest
class CompareBehaviorCommandTest {
    @Test void registeredHelpDescribesOfflineInputAndJson(QuarkusMainLauncher launcher) {
        var root=launcher.launch("--help"); assertEquals(0,root.exitCode()); assertTrue(root.getOutput().contains("compare-behavior"));
        var help=launcher.launch("compare-behavior","--help"); assertEquals(0,help.exitCode());
        for(String token:List.of("comparison.json","--json")) assertTrue(help.getOutput().contains(token),token);
        assertTrue(help.getOutput().toLowerCase(java.util.Locale.ROOT).contains("offline"));
        var required=launcher.launch("compare-behavior"); assertNotEquals(0,required.exitCode());
        assertFalse((required.getErrorOutput()+required.getOutput()).isBlank());
    }

    @Test void jsonAndMarkdownExposeRealComparisonValuesWithoutChangingInputs(QuarkusMainLauncher launcher,@TempDir Path dir) throws Exception {
        var fixture=SyntheticComparison.write(dir,2,"Supplied|alignment","candidate"); var before=fixture.hashes();
        var result=launcher.launch("compare-behavior",fixture.input().toString(),"--json");
        assertEquals(0,result.exitCode()); var report=read(result);
        assertEquals(BehaviorComparison.read(fixture.input()),report);
        assertEquals("behavior-comparison-v1",report.analysisVersion()); assertEquals(2,report.implementations().size());
        assertEquals(9,report.rows().size()); assertEquals("candidate",report.rows().getFirst().status());
        assertEquals(fixture.document().alignments().getFirst().evidence(),report.rows().getFirst().evidence());
        assertEquals(8,report.rows().stream().filter(r->"unmatched".equals(r.status())).count());
        for(var row:report.rows()) assertEquals(List.of(0,1),row.cells().stream().map(BehaviorComparisonReport.Cell::implementation).toList());
        for(var c:report.rows().getFirst().cells()) {
            assertEquals("source-step",c.state());
            assertEquals(List.of("presence","conditions","data","loops","faults","calls","channels","contracts"),c.facets().stream().map(BehaviorComparisonReport.Facet::kind).toList());
            var loops=c.facets().stream().filter(f->"loops".equals(f.kind())).findFirst().orElseThrow();
            assertEquals("unknown",loops.state()); assertTrue(loops.pointers().isEmpty());
        }
        assertEquals(2,report.contexts().size());
        assertEquals("channel-1",report.implementations().get(1).behavior().integration().channels().getFirst().configuration().attributes().get("syntheticValue"));
        var markdown=launcher.launch("compare-behavior",fixture.input().toString()); assertEquals(0,markdown.exitCode());
        for(String token:List.of("condition-0","condition-1","default","Payload0","Payload1","scopes","active","explicit-membership","candidate","unknown","unmatched","context"))
            assertTrue(markdown.getOutput().contains(token),token);
        assertFalse(markdown.getOutput().contains("Column|0")); assertFalse(markdown.getOutput().contains("Supplied|alignment"));
        assertTrue(markdown.getOutput().contains("default\\|0") || markdown.getOutput().contains("default&#124;0"));
        assertEquals(before,fixture.hashes());
    }

    @Test void semanticErrorsReturnSuccessWithCompleteUnmatchedEvidence(QuarkusMainLauncher launcher,@TempDir Path dir) throws Exception {
        var fixture=SyntheticComparison.write(dir,1,"Invalid supplied status","approved"); var before=fixture.hashes();
        var result=launcher.launch("compare-behavior",fixture.input().toString(),"--json"); assertEquals(0,result.exitCode());
        var report=read(result); assertEquals(BehaviorComparison.read(fixture.input()),report);
        assertEquals("invalid",report.rows().getFirst().status()); assertFalse(report.issues().isEmpty());
        assertEquals(5,report.rows().stream().filter(r->"unmatched".equals(r.status())).count());
        assertTrue(Json.write(report.issues()).contains("approved")); assertEquals(before,fixture.hashes());
    }

    @Test void missingMalformedAndIncompatibleInputsFailWithoutMutation(QuarkusMainLauncher launcher,@TempDir Path dir) throws Exception {
        var missing=launcher.launch("compare-behavior",dir.resolve("missing.json").toString());
        assertEquals(1,missing.exitCode()); assertTrue(missing.getErrorOutput().contains("missing.json"));
        var fixture=SyntheticComparison.write(dir.resolve("good"),1,"alignment","candidate"); var before=fixture.hashes();
        var shape=Json.mapper().valueToTree(fixture.document());
        ((tools.jackson.databind.node.ObjectNode)shape.at("/implementations/0/behavior")).putNull("processes");
        Path incompatible=dir.resolve("incompatible.json"); Json.write(shape,incompatible);
        Path malformed=Files.writeString(dir.resolve("malformed.json"),"{broken");
        for(Path file:List.of(incompatible,malformed)) {
            byte[] bytes=Files.readAllBytes(file); var result=launcher.launch("compare-behavior",file.toString(),"--json");
            assertEquals(1,result.exitCode()); assertTrue(result.getErrorOutput().contains("Cannot bind comparison input"));
            assertArrayEquals(bytes,Files.readAllBytes(file));
        }
        assertEquals(before,fixture.hashes());
    }
    private static BehaviorComparisonReport read(LaunchResult result) {
        String output=result.getOutput();
        return Json.mapper().readValue(output.substring(output.indexOf('{'),output.lastIndexOf('}')+1),BehaviorComparisonReport.class);
    }
}
