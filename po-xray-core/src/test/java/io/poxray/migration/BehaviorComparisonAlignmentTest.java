package io.poxray.migration;

import io.poxray.json.Json;
import io.poxray.model.*;
import io.poxray.model.BehaviorComparisonInput.*;
import io.poxray.model.BehaviorComparisonReport.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static io.poxray.migration.BehaviorComparisonFixtures.*;
import static io.poxray.migration.BehaviorComparisonTest.*;

class BehaviorComparisonAlignmentTest {
    @TempDir Path temp;
    @Test void mixedSupplementalEvidenceKeepsQualifiedCandidateAndConfirmedAlignment() throws Exception {
        var a=implementation("a",source(temp,0)); var call=ref(a,"call");
        var incomplete=new Evidence("supplemental"," ","retained-locator","unverified");
        assertAll(List.of("candidate","confirmed").stream().map(status -> (org.junit.jupiter.api.function.Executable) () -> {
            var qualified=new Evidence("decision","synthetic.md","section",status.equals("confirmed")?"confirmed":"proposed");
            var evidence=Arrays.asList(qualified,null,incomplete);
            var row=new Alignment("mixed-"+status,"attributed",status,List.of(call),evidence);
            var r=compare(List.of(a),row);
            assertEquals(evidence,r.rows().getFirst().evidence());
            String diagnostics=Json.write(r.issues());
            assertFalse(r.issues().isEmpty()); assertTrue(diagnostics.contains("mixed-"+status));
            assertTrue(diagnostics.contains("retained-locator")); assertTrue(diagnostics.contains("unverified"));
            var withoutProof=compare(List.of(a),new Alignment("without-proof","attributed",status,List.of(call),Arrays.asList(null,incomplete)));
            assertEquals("invalid",withoutProof.rows().getFirst().status()); assertEquals(6,withoutProof.rows().size());
            assertEquals(status,r.rows().getFirst().status(),"qualified "+status+" survives malformed supplemental evidence");
            assertEquals("source-step",r.rows().getFirst().cells().getFirst().state());
            assertEquals(5,r.rows().size()); assertEquals(4,r.rows().stream().filter(x->"unmatched".equals(x.status())).count());
            assertEquals(1,r.rows().stream().flatMap(x->x.cells().stream()).filter(c->call.equals(c.step())).count());
            assertEquals(List.of("unmatched:0:0:0","unmatched:0:0:2","unmatched:0:0:3","unmatched:0:0:4"),
                    r.rows().subList(1,r.rows().size()).stream().map(Row::id).toList());
        }));
    }

    @Test void invalidAttributionMembersAndColumnConflictsNeverConsumeSourceSteps() throws Exception {
        var a=implementation("a",source(temp.resolve("a"),0)); var call=ref(a,"call");
        var cases=List.of(
                new Alignment("no-evidence","x","candidate",List.of(call),List.of()),
                new Alignment("false-confirmation","x","confirmed",List.of(call),PROOF),
                new Alignment("unsupported","x","approved",List.of(call),PROOF),
                new Alignment("bad-evidence","x","candidate",List.of(call),List.of(new Evidence("design"," ","where","proposed"))),
                alignment("duplicate-member",call,call),
                alignment("duplicate-column",call,ref(a,"start")),
                alignment("missing",new StepRef("a",call.process(),999)),
                alignment("wrong-case",new StepRef("A",call.process(),call.step())),
                alignment("wrong-process",new StepRef("a",new BpmPathReport.ProcessKey(9,1,1),call.step())));
        for (var alignment:cases) {
            var r=compare(List.of(a),alignment);
            assertEquals("invalid",r.rows().getFirst().status(),alignment.id());
            assertEquals(5,r.rows().stream().filter(row->"unmatched".equals(row.status())).count(),alignment.id());
            assertFalse(r.issues().isEmpty(),alignment.id());
            assertTrue(Json.write(r.issues()).contains(alignment.id()));
            if (alignment.id().startsWith("duplicate-")) assertEquals("not-aligned",r.rows().getFirst().cells().getFirst().state());
            if (alignment.id().equals("no-evidence")) {
                assertEquals("source-step",r.rows().getFirst().cells().getFirst().state());
                assertTrue(r.rows().getFirst().cells().getFirst().reasons().contains("invalid-alignment"));
            }
            assertEquals(alignment.evidence(),r.rows().getFirst().evidence());
        }
    }

    @Test void crossRowReuseInvalidatesBothRowsBeforeConsumptionAndConfirmationRemainsAttributed() throws Exception {
        var a=implementation("a",source(temp,0)); var call=ref(a,"call");
        var r=compare(List.of(a),alignment("first",call),alignment("second",call));
        assertEquals(List.of("invalid","invalid"),r.rows().subList(0,2).stream().map(Row::status).toList());
        assertEquals(7,r.rows().size());
        var proof=List.of(new Evidence("owner-decision","synthetic.md","section","confirmed"));
        var confirmed=compare(List.of(a),new Alignment("chosen","supplied","confirmed",List.of(call),proof));
        assertEquals("confirmed",confirmed.rows().getFirst().status()); assertEquals(proof,confirmed.rows().getFirst().evidence());
        assertEquals("source-step",confirmed.rows().getFirst().cells().getFirst().state());
        assertTrue(BehaviorComparison.markdown(confirmed).contains("confirmed"));
    }

    @Test void duplicateAndBlankIdsPreserveColumnOrdinalsAndOriginalAlignmentIds() throws Exception {
        var s=source(temp,0); var a=implementation("same",s); var b=implementation("same",s); var blank=implementation(" ",s);
        var r=compare(List.of(a,b,blank),alignment("duplicate",ref(a,"call")),alignment("duplicate",ref(a,"start")),alignment(" ",ref(blank,"call")));
        assertEquals(List.of("alignment:0","alignment:1","alignment:2"),r.rows().subList(0,3).stream().map(Row::id).toList());
        assertEquals(List.of("duplicate","duplicate"," "),r.rows().subList(0,3).stream().map(Row::alignment).toList());
        assertEquals(18,r.rows().size()); assertEquals(3,r.implementations().size());
        assertTrue(r.rows().subList(0,3).stream().allMatch(row->"invalid".equals(row.status())));
        assertTrue(r.rows().stream().allMatch(row->row.cells().stream().map(Cell::implementation).toList().equals(List.of(0,1,2))));
        assertFalse(r.issues().isEmpty()); pointersResolve(r);
    }

    @Test void nullableMembersAndUnsupportedVersionsAreDiagnosticsRatherThanInventedCoverage() throws Exception {
        var s=source(temp,0); var a=implementation("a",s);
        assertFalse(BehaviorComparison.of(null).issues().isEmpty());
        var unsupported=BehaviorComparison.of(new BehaviorComparisonInput(99,List.of(a),List.of()));
        assertTrue(unsupported.rows().isEmpty()); assertEquals(List.of(a),unsupported.implementations()); assertFalse(unsupported.issues().isEmpty());
        for (String path:List.of("", "/data", "/data/paths")) {
            var bad=malformed(a.behavior(),node->((tools.jackson.databind.node.ObjectNode)node.at(path)).put("formatVersion",99));
            var r=compare(List.of(new Implementation("a","a",bad,a.contracts())));
            assertTrue(r.rows().isEmpty(),path); assertFalse(r.issues().isEmpty(),path);
        }
        var r=BehaviorComparison.of(new BehaviorComparisonInput(1,Arrays.asList(null,new Implementation("empty",null,null,null),a),
                Arrays.asList(null,new Alignment(null,null,null,Arrays.asList((StepRef)null),Arrays.asList((Evidence)null)))));
        assertEquals(3,r.implementations().size()); assertNull(r.implementations().getFirst());
        assertFalse(r.issues().isEmpty()); assertEquals(5,r.rows().stream().filter(row->"unmatched".equals(row.status())).count());
        assertEquals(3,r.contexts().size());
    }

    @Test void allNewCollectionsSnapshotInputsAllowDiagnosticNullsAndRejectMutation() {
        var reasons=new ArrayList<>(List.of("reason")); var pointers=new ArrayList<>(List.of("/behavior/data"));
        var f=new Facet("data",pointers,"unknown",reasons); var facets=new ArrayList<>(List.of(f));
        var cell=new Cell(0,null,"source-step",facets,reasons); var cells=new ArrayList<>(List.of(cell));
        var evidence=new ArrayList<Evidence>(Arrays.asList((Evidence)null)); var refs=new ArrayList<StepRef>(Arrays.asList((StepRef)null));
        var alignment=new Alignment(null,null,null,refs,evidence); var alignments=new ArrayList<>(List.of(alignment));
        var implementations=new ArrayList<Implementation>(Arrays.asList((Implementation)null));
        var input=new BehaviorComparisonInput(1,implementations,alignments);
        var row=new Row("r",null,null,"invalid",evidence,cells); var rows=new ArrayList<>(List.of(row));
        var context=new Context(0,facets,reasons); var contexts=new ArrayList<>(List.of(context));
        var issues=new ArrayList<Issue>(); var report=new BehaviorComparisonReport(1,"v",implementations,rows,contexts,issues);
        for (List<?> list:List.of(reasons,pointers,facets,cells,evidence,refs,alignments,implementations,rows,contexts,issues)) list.clear();
        assertEquals(List.of("reason"),f.reasons()); assertEquals(List.of("/behavior/data"),f.pointers());
        assertEquals(1,input.implementations().size()); assertNull(input.implementations().getFirst());
        assertEquals(1,input.alignments().getFirst().steps().size()); assertEquals(1,report.rows().size());
        assertTrue(Json.write(row).contains("\"alignment\" : null"));
        for (List<?> list:List.of(input.implementations(),input.alignments(),alignment.steps(),alignment.evidence(),report.implementations(),
                report.rows(),report.contexts(),report.issues(),row.cells(),row.evidence(),cell.facets(),cell.reasons(),f.pointers(),f.reasons(),context.facets(),context.reasons()))
            assertThrows(UnsupportedOperationException.class,()->list.add(null));
        assertTrue(new BehaviorComparisonInput(1,null,null).alignments().isEmpty());
        assertTrue(new Row(null,null,null,null,null,null).cells().isEmpty());
        assertTrue(new Cell(0,null,null,null,null).reasons().isEmpty());
        assertTrue(new Facet(null,null,null,null).pointers().isEmpty());
        assertTrue(new Context(0,null,null).facets().isEmpty());
        assertTrue(new BehaviorComparisonReport(1,null,null,null,null,null).issues().isEmpty());
    }
}
