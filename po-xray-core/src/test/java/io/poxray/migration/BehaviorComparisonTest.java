package io.poxray.migration;

import io.poxray.json.Json;
import io.poxray.model.*;
import io.poxray.model.BehaviorComparisonInput.*;
import io.poxray.model.BehaviorComparisonReport.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static io.poxray.migration.BehaviorComparisonFixtures.*;

class BehaviorComparisonTest {
    @TempDir Path temp;
    static final List<Evidence> PROOF = List.of(new Evidence("design", "synthetic.md", "section|1\nline", "proposed"));
    static Implementation implementation(String id, Source s) { return new Implementation(id, "Column|" + id, s.behavior(), s.contracts()); }
    static StepRef ref(Implementation i, String id) {
        var p = i.behavior().data().paths().processes().getFirst();
        return new StepRef(i.id(), p.key(), p.steps().stream().filter(s -> id.equals(s.id())).findFirst().orElseThrow().ordinal());
    }
    static Alignment alignment(String id, StepRef... refs) { return new Alignment(id, "Supplied|label", "candidate", List.of(refs), PROOF); }
    static BehaviorComparisonReport compare(List<Implementation> i, Alignment... a) { return BehaviorComparison.of(new BehaviorComparisonInput(1, i, List.of(a))); }
    static Facet facet(Cell c, String kind) { return c.facets().stream().filter(f -> kind.equals(f.kind())).findFirst().orElseThrow(); }
    static Cell cell(BehaviorComparisonReport r, int implementation, String stepId) {
        return r.rows().stream().flatMap(row -> row.cells().stream()).filter(c -> c.implementation() == implementation && "source-step".equals(c.state()))
                .filter(c -> facet(c, "presence").pointers().stream().anyMatch(p -> stepId.equals(tree(r.implementations().get(implementation)).at(p).path("id").asString())))
                .findFirst().orElseThrow();
    }
    static void pointersResolve(BehaviorComparisonReport r) {
        for (var row : r.rows()) for (var c : row.cells()) for (var f : c.facets()) {
            assertEquals(f.pointers().size(), new LinkedHashSet<>(f.pointers()).size());
            for (String p : f.pointers()) {
                assertTrue(p.startsWith("/behavior/") || p.startsWith("/contracts/"), p);
                assertFalse(tree(r.implementations().get(c.implementation())).at(p).isMissingNode(), p);
            }
        }
        for (var c : r.contexts()) for (var f : c.facets()) for (String p : f.pointers())
            assertFalse(tree(r.implementations().get(c.implementation())).at(p).isMissingNode(), p);
    }

    @Test void threeRealImplementationsAlignOnlyExplicitRenamedStepsAndKeepEveryUnmatchedOccurrence() throws Exception {
        var implementations = new ArrayList<Implementation>();
        for (int n=0;n<3;n++) implementations.add(implementation("i" + n, source(temp.resolve("i" + n), n)));
        String before = Json.write(implementations);
        var r = compare(implementations, alignment("chosen", implementations.stream().map(i -> ref(i,"call")).toArray(StepRef[]::new)));
        assertEquals(1, r.formatVersion()); assertEquals("behavior-comparison-v1", r.analysisVersion());
        assertEquals(before, Json.write(r.implementations())); assertEquals(before, Json.write(implementations));
        assertEquals(13, r.rows().size()); assertEquals("alignment:0", r.rows().getFirst().id());
        assertEquals("candidate", r.rows().getFirst().status()); assertEquals(PROOF, r.rows().getFirst().evidence());
        var expected = new ArrayList<String>(); expected.add("alignment:0");
        for (int i=0;i<3;i++) for (int s=0;s<5;s++) if (s != 1) expected.add("unmatched:"+i+":0:"+s);
        assertEquals(expected, r.rows().stream().map(Row::id).toList());
        for (var row : r.rows()) {
            assertEquals(List.of(0,1,2), row.cells().stream().map(Cell::implementation).toList());
            for (var c : row.cells()) if ("source-step".equals(c.state())) assertEquals(KINDS, c.facets().stream().map(Facet::kind).toList());
            else { assertEquals("not-aligned", c.state()); assertNull(c.step()); assertTrue(c.facets().isEmpty()); }
        }
        for (int i=0;i<3;i++) assertEquals("source-step", cell(r,i,"island").state());
        assertEquals(15, compare(implementations).rows().size(), "same names never auto-align");
        var sameNameA=tree(implementations.get(0)).at(facet(cell(r,0,"call"),"presence").pointers().getFirst());
        var sameNameB=tree(implementations.get(2)).at(facet(cell(r,2,"call"),"presence").pointers().getFirst());
        assertEquals(sameNameA.path("name"),sameNameB.path("name"));
        String firstCondition=BehaviorComparisonFacetsTest.selected(implementations.get(0),facet(cell(r,0,"call"),"conditions"));
        String thirdCondition=BehaviorComparisonFacetsTest.selected(implementations.get(2),facet(cell(r,2,"call"),"conditions"));
        assertTrue(firstCondition.contains("condition-0")); assertTrue(thirdCondition.contains("condition-2"));
        assertNotEquals(firstCondition,thirdCondition);
        var firstScope=implementations.get(0).behavior().faults().processes().getFirst().declarations().get(1).scopes().getFirst();
        var secondScope=implementations.get(1).behavior().faults().processes().getFirst().declarations().get(1).scopes().getFirst();
        assertEquals("active",firstScope.state()); assertEquals("inactive",secondScope.state()); assertNotEquals(firstScope.path(),secondScope.path());
        String secondFault=BehaviorComparisonFacetsTest.selected(implementations.get(1),facet(cell(r,1,"call"),"faults"));
        assertTrue(secondFault.contains("inactive")); assertTrue(secondFault.contains("explicit-membership"));
        pointersResolve(r);
        assertEquals(r, Json.mapper().readValue(Json.write(r), BehaviorComparisonReport.class));

        // Known fixture relationship: only its literal display name changes before the real readers run.
        var renamed=implementation("renamed",source(temp.resolve("renamed-only"),0,"Renamed operation"));
        var original=implementations.getFirst();
        var originalPath=original.behavior().data().paths().processes().getFirst();
        var renamedPath=renamed.behavior().data().paths().processes().getFirst();
        assertNotEquals(originalPath.steps().get(1).name(),renamedPath.steps().get(1).name());
        assertEquals(originalPath.arcs(),renamedPath.arcs()); assertEquals(originalPath.loops(),renamedPath.loops());
        assertEquals(original.behavior().faults().processes().getFirst().declarations().stream().map(BpmFaultReport.Declaration::scopes).toList(),
                renamed.behavior().faults().processes().getFirst().declarations().stream().map(BpmFaultReport.Declaration::scopes).toList());
        var unaligned=compare(List.of(original,renamed));
        assertEquals(10,unaligned.rows().size()); assertTrue(unaligned.rows().stream().allMatch(row->"unmatched".equals(row.status())));
        var supplied=compare(List.of(original,renamed),alignment("renamed-only",ref(original,"call"),ref(renamed,"call")));
        assertEquals(9,supplied.rows().size()); assertEquals("candidate",supplied.rows().getFirst().status());
        assertEquals(PROOF,supplied.rows().getFirst().evidence());
        assertTrue(supplied.rows().getFirst().cells().stream().allMatch(c->"source-step".equals(c.state())));
    }

    @Test void markdownShowsSelectedValuesContextUnknownsAndEscapesDeterministicallyWithoutMutation() throws Exception {
        var a=implementation("a",source(temp.resolve("a"),0)); var b=implementation("b",source(temp.resolve("b"),1));
        var r=compare(List.of(a,b),alignment("explicit",ref(a,"call"),ref(b,"call")));
        String before=Json.write(r), md=BehaviorComparison.markdown(r);
        for (String token : List.of("Operation-0","Operation-1","condition-0","condition-1","default-0","default-1",
                "claim-default-0","claim-default-1","channel-0","channel-1","Fault", "sequential","candidate","unknown","unmatched","/behavior/",
                "scopes","active","inactive","explicit-membership"))
            assertTrue(md.contains(token), token);
        assertTrue(md.toLowerCase().contains("context"));
        assertFalse(md.contains("Column|a")); assertFalse(md.contains("section|1\nline"));
        assertEquals(md,BehaviorComparison.markdown(r)); assertEquals(before,Json.write(r));
    }

    @Test void readIsOfflinePreservesFileHashesAndRejectsIncompatibleEmbeddedJson() throws Exception {
        var s=source(temp.resolve("source"),0); var i=implementation("a",s);
        var in=new BehaviorComparisonInput(1,List.of(i),List.of(alignment("one",ref(i,"call"))));
        Path file=temp.resolve("comparison.json"); Json.write(in,file);
        var hashes=List.of(hash(file),hash(s.archive()),hash(s.tpz()));
        assertEquals(BehaviorComparison.of(in),BehaviorComparison.read(file));
        assertEquals(hashes,List.of(hash(file),hash(s.archive()),hash(s.tpz())));
        var node=tree(in); ((tools.jackson.databind.node.ObjectNode)node.at("/implementations/0/behavior")).putNull("processes");
        Json.write(node,file); assertThrows(java.io.IOException.class,()->BehaviorComparison.read(file));
        Files.writeString(file,"{not-json"); assertThrows(java.io.IOException.class,()->BehaviorComparison.read(file));
        assertThrows(java.io.IOException.class,()->BehaviorComparison.read(temp.resolve("missing.json")));
        // Strings resembling external sources are attribution only; no file/network lookup is necessary.
        var offline=new BehaviorComparisonInput(1,List.of(i),List.of(new Alignment("offline","offline","candidate",List.of(ref(i,"call")),
                List.of(new Evidence("design","file:///definitely-missing/never-read.json","https://invalid.example/never-read","proposed")))));
        Json.write(offline,file); assertEquals("candidate",BehaviorComparison.read(file).rows().getFirst().status());
    }
}
