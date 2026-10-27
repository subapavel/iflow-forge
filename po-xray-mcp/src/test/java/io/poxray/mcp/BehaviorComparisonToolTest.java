package io.poxray.mcp;

import io.poxray.SyntheticComparison;
import io.poxray.json.Json;
import io.poxray.migration.BehaviorComparison;
import io.poxray.model.BehaviorComparisonReport;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.SerializationFeature;
import static org.junit.jupiter.api.Assertions.*;

class BehaviorComparisonToolTest {
    @TempDir Path dir;
    @Test void publicToolDeclaresLocalReadonlyComparisonAndRequiredInput() throws Exception {
        var method=PoXrayTools.class.getMethod("behavior_comparison",String.class);
        assertEquals(String.class,method.getReturnType());
        var tool=method.getAnnotation(Tool.class); assertNotNull(tool); assertEquals("behavior_comparison",tool.name());
        assertFalse(tool.description().isBlank()); assertTrue(tool.description().toLowerCase(java.util.Locale.ROOT).contains("comparison"));
        assertTrue(tool.annotations().readOnlyHint()); assertFalse(tool.annotations().openWorldHint());
        var argument=method.getParameters()[0].getAnnotation(ToolArg.class); assertNotNull(argument);
        assertEquals("Local comparison JSON with embedded reports",argument.description());
    }

    @Test void inlineResultContainsActualAlignmentFacetsUnknownsAndContextWithoutInputChanges() throws Exception {
        var fixture=SyntheticComparison.write(dir,1,"Supplied|alignment","candidate"); var before=fixture.hashes();
        var expected=BehaviorComparison.read(fixture.input());
        assertTrue(Json.mapper().writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(expected).length()<=80_000,"fixture must exercise inline path");
        String output=new PoXrayTools().behavior_comparison(fixture.input().toString());
        assertTrue(Json.mapper().readTree(output).path("file").isMissingNode()); assertTrue(output.length()<=80_000);
        var report=Json.mapper().readValue(output,BehaviorComparisonReport.class); assertEquals(expected,report);
        assertEquals(5,report.rows().size()); assertEquals("candidate",report.rows().getFirst().status());
        assertEquals(fixture.document().alignments().getFirst().evidence(),report.rows().getFirst().evidence());
        assertEquals(4,report.rows().stream().filter(r->"unmatched".equals(r.status())).count());
        var cell=report.rows().getFirst().cells().getFirst(); assertEquals(0,cell.implementation()); assertEquals("source-step",cell.state());
        assertEquals(List.of("presence","conditions","data","loops","faults","calls","channels","contracts"),cell.facets().stream().map(BehaviorComparisonReport.Facet::kind).toList());
        var loops=cell.facets().stream().filter(f->"loops".equals(f.kind())).findFirst().orElseThrow();
        assertEquals("unknown",loops.state()); assertTrue(loops.pointers().isEmpty());
        assertEquals(1,report.contexts().size());
        for(String token:List.of("condition-0","default|0","Payload0","boundary-trigger","explicit-membership","channel-0")) assertTrue(output.contains(token),token);
        assertEquals(before,fixture.hashes());
    }

    @Test void largeResultSpillsCompleteReportIncludingBothPayloadMarkers() throws Exception {
        String payload="BEGIN-COMPARISON-"+"x".repeat(90_000)+"-END-COMPARISON";
        var fixture=SyntheticComparison.write(dir,2,payload,"candidate"); var before=fixture.hashes();
        var expected=BehaviorComparison.read(fixture.input());
        assertTrue(Json.mapper().writer().without(SerializationFeature.INDENT_OUTPUT).writeValueAsString(expected).length()>80_000);
        String output=new PoXrayTools().behavior_comparison(fixture.input().toString()); var envelope=Json.mapper().readTree(output);
        assertTrue(output.length()<2_000); assertTrue(envelope.path("message").asString().contains("characters"));
        Path file=Path.of(envelope.path("file").asString());
        assertTrue(file.isAbsolute()); assertTrue(file.getFileName().toString().startsWith("behavior_comparison-")); assertTrue(Files.isRegularFile(file));
        try {
            String contents=Files.readString(file); assertTrue(contents.length()>80_000);
            assertTrue(contents.contains("BEGIN-COMPARISON-")); assertTrue(contents.contains("-END-COMPARISON"));
            var report=Json.mapper().readValue(contents,BehaviorComparisonReport.class); assertEquals(expected,report);
            assertEquals(payload,report.rows().getFirst().label()); assertEquals(9,report.rows().size());
            assertEquals(2,report.implementations().size()); assertEquals(2,report.contexts().size());
            assertTrue(contents.contains("condition-1")); assertTrue(contents.contains("channel-1"));
            assertEquals(before,fixture.hashes());
        } finally {
            Files.delete(file); // Only the actual spill created by this invocation; cleanup errors remain test failures.
        }
    }

    @Test void inputFailuresBecomeToolErrorsWhileSemanticDiagnosticsRemainReports() throws Exception {
        var tool=new PoXrayTools();
        var missing=assertThrows(ToolCallException.class,()->tool.behavior_comparison(dir.resolve("missing.json").toString()));
        assertTrue(missing.getMessage().contains("missing.json"));
        var fixture=SyntheticComparison.write(dir.resolve("semantic"),1,"Unsupported status","approved"); var before=fixture.hashes();
        var report=Json.mapper().readValue(tool.behavior_comparison(fixture.input().toString()),BehaviorComparisonReport.class);
        assertEquals(BehaviorComparison.read(fixture.input()),report); assertEquals("invalid",report.rows().getFirst().status());
        assertFalse(report.issues().isEmpty()); assertEquals(5,report.rows().stream().filter(r->"unmatched".equals(r.status())).count());
        assertTrue(Json.write(report.issues()).contains("approved"));
        var shape=Json.mapper().valueToTree(fixture.document());
        ((tools.jackson.databind.node.ObjectNode)shape.at("/implementations/0/behavior")).putNull("processes");
        Path incompatible=dir.resolve("incompatible.json"); Json.write(shape,incompatible);
        Path malformed=Files.writeString(dir.resolve("malformed.json"),"{broken");
        for(Path file:List.of(incompatible,malformed)) {
            byte[] bytes=Files.readAllBytes(file);
            var error=assertThrows(ToolCallException.class,()->tool.behavior_comparison(file.toString()));
            assertTrue(error.getMessage().contains("Cannot bind comparison input")); assertArrayEquals(bytes,Files.readAllBytes(file));
        }
        assertEquals(before,fixture.hashes());
    }
}
