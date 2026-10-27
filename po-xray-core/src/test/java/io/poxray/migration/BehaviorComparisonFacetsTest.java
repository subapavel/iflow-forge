package io.poxray.migration;

import io.poxray.json.Json;
import io.poxray.model.*;
import io.poxray.model.BehaviorComparisonInput.*;
import io.poxray.model.BehaviorComparisonReport.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;
import static io.poxray.migration.BehaviorComparisonFixtures.*;
import static io.poxray.migration.BehaviorComparisonTest.*;

class BehaviorComparisonFacetsTest {
    @TempDir Path temp;
    @Test void sourceOnlyCatalogWithoutStepClaimsStaysContextualWithoutAncestryError() throws Exception {
        var s=source(temp.resolve("behavior"),0);
        var inventory=ContractCatalogFixtures.read(temp.resolve("source-contracts.tpz"),ContractCatalogFixtures.definition());
        var proof=ContractCatalogFixtures.DESIGN;
        var unbound=new ContractCatalogInput.ContractClaim("unbound","source:external-definition:0","proposed-internal",null,
                new IntegrationInventory.XmlName("urn:synthetic","UnboundPayload"),
                List.of(new ContractCatalogInput.PortClaim("request","Caller","unbound-operation",null,proof)),
                List.of(new ContractCatalogInput.ValueClaim("/unbound","default",null,List.of(),"unbound-default",proof)),proof);
        var catalogs=List.of(ContractCatalog.of(inventory,null,ContractCatalogFixtures.EMPTY),
                ContractCatalog.of(inventory,null,new ContractCatalogInput(null,List.of(unbound),null)));
        assertAll(catalogs.stream().map(catalog -> (org.junit.jupiter.api.function.Executable) () -> {
            assertNull(catalog.data()); assertFalse(catalog.contracts().isEmpty());
            var i=new Implementation("a","a",s.behavior(),catalog); var r=compare(List.of(i));
            assertEquals(catalog,r.implementations().getFirst().contracts());
            var context=r.contexts().getFirst().facets().stream().filter(f->"contract-context".equals(f.kind())).findFirst().orElseThrow();
            assertFalse(context.pointers().isEmpty());
            String contextValues=selected(i,context);
            assertTrue(contextValues.contains("source:external-definition:0"));
            if(catalog == catalogs.get(1)) {
                assertTrue(contextValues.contains("unbound-operation")); assertTrue(contextValues.contains("unbound-default"));
                assertTrue(BehaviorComparison.markdown(r).contains("unbound-default"));
            }
            for(var row:r.rows()) for(var c:row.cells()) if("source-step".equals(c.state())) {
                assertEquals("unknown",facet(c,"contracts").state()); assertTrue(facet(c,"contracts").pointers().isEmpty());
            }
            pointersResolve(r);
            assertFalse(Json.write(r).contains("contract-data-ancestry-mismatch"),"no step claim requires data ancestry");
        }));
    }

    static ObjectNode object(ObjectNode n,String p) { return (ObjectNode)n.at(p); }
    static ArrayNode array(ObjectNode n,String p) { return (ArrayNode)n.at(p); }
    static Implementation changed(Implementation i, java.util.function.Consumer<ObjectNode> edit) {
        return new Implementation(i.id(),i.label(),malformed(i.behavior(),edit),i.contracts());
    }
    static void unknown(Facet f) { assertEquals("unknown",f.state()); assertFalse(f.reasons().isEmpty(),f.kind()); }
    static String selected(Implementation i,Facet f) {
        var node=tree(i); return f.pointers().stream().map(p->node.at(p).toString()).reduce("",String::concat);
    }

    @Test void allEightFacetsUseResolvableOriginalValuesAndUnknownOverridesSelectedSourceReasons() throws Exception {
        var i=implementation("a",source(temp,0)); var r=compare(List.of(i)); var call=cell(r,0,"call");
        assertEquals(KINDS,call.facets().stream().map(Facet::kind).toList());
        for (var f:call.facets()) assertFalse(f.pointers().isEmpty(),f.kind());
        assertEquals("available",facet(call,"presence").state());
        assertEquals(List.of("/behavior/data/paths/processes/0/steps/1"),facet(call,"presence").pointers());
        assertTrue(selected(i,facet(call,"conditions")).contains("condition-0"));
        assertTrue(selected(i,facet(call,"conditions")).contains("isDefault"));
        var data=facet(call,"data");
        assertTrue(data.pointers().contains("/behavior/data/processes/0/steps/1"));
        assertTrue(data.pointers().contains("/behavior/data/processes/0/declarations/1"));
        assertTrue(data.pointers().contains("/behavior/data/processes/0/mappings/1"));
        assertTrue(selected(i,data).contains("default-0")); assertTrue(selected(i,data).contains("initial"));
        unknown(data); assertTrue(data.reasons().contains("literal-value-or-type-unknown"));
        assertTrue(selected(i,facet(call,"loops")).contains("sequential"));
        unknown(facet(call,"loops")); assertTrue(facet(call,"loops").reasons().contains("BPM_PATH_LOOP_UNSUPPORTED"));
        assertTrue(selected(i,facet(call,"faults")).contains("boundary-trigger"));
        assertTrue(selected(i,facet(call,"calls")).contains("out-op"));
        assertEquals("candidate",facet(call,"calls").state());
        assertTrue(selected(i,facet(call,"contracts")).contains("claim-default-0"));
        var ingress=facet(cell(r,0,"start"),"channels");
        assertTrue(selected(i,ingress).contains("channel-0"));
        assertTrue(ingress.pointers().contains("/behavior/integration/icos/0"));
        assertTrue(ingress.pointers().contains("/behavior/integration/channels/0"));
        var island=cell(r,0,"island"); unknown(facet(island,"loops")); assertTrue(facet(island,"loops").pointers().isEmpty());
        assertTrue(facet(island,"faults").pointers().isEmpty(),"shared scope alone cannot attach a boundary fault");
        assertEquals(List.of("data-context","fault-context","configuration-context","wiring-context","contract-context"),r.contexts().getFirst().facets().stream().map(Facet::kind).toList());
        assertTrue(Json.write(r.issues()).contains("a"),"source issues need implementation provenance");
        pointersResolve(r);
    }

    @Test void dataFaultAndContractAncestryMustMatchBeyondNumericProcessKey() throws Exception {
        var i=implementation("a",source(temp.resolve("a"),0)); var foreign=source(temp.resolve("foreign"),1);
        var wrongCatalog=new Implementation("a","a",i.behavior(),foreign.contracts());
        var r=compare(List.of(wrongCatalog)); var contract=facet(cell(r,0,"call"),"contracts");
        unknown(contract); assertTrue(contract.pointers().isEmpty()); assertFalse(r.issues().isEmpty());
        assertTrue(BehaviorComparison.markdown(r).contains("claim-default-1"),"unattached contracts remain context");
        for (String path:List.of("/data/processes/0/dc","/data/processes/0/id","/data/processes/0/source",
                "/faults/processes/0/dc","/faults/processes/0/id","/faults/processes/0/source","/faults/inputs/0/sha256")) {
            int slash=path.lastIndexOf('/');
            var bad=changed(i,n->object(n,path.substring(0,slash)).put(path.substring(slash+1),"foreign"));
            var result=compare(List.of(bad)); String kind=path.startsWith("/data")?"data":"faults";
            var f=facet(cell(result,0,"call"),kind); unknown(f); assertTrue(f.pointers().isEmpty(),path); pointersResolve(result);
        }
        var duplicateInput=changed(i,n->array(n,"/faults/inputs").add(n.at("/faults/inputs/0").deepCopy()));
        assertTrue(facet(cell(compare(List.of(duplicateInput)),0,"call"),"faults").pointers().isEmpty());
    }

    @Test void unsupportedFacetVersionsDoNotSuppressSupportedEvidenceAndRawContextsRemain() throws Exception {
        var i=implementation("a",source(temp,0));
        var badFault=changed(i,n->object(n,"/faults").put("formatVersion",99));
        var catalog=tree(i.contracts()); catalog.put("formatVersion",99);
        var bad=new Implementation("a","a",badFault.behavior(),Json.mapper().treeToValue(catalog,ContractCatalogReport.class));
        var r=compare(List.of(bad)); var call=cell(r,0,"call");
        assertEquals(5,r.rows().size());
        for(String kind:List.of("faults","contracts")) { unknown(facet(call,kind)); assertTrue(facet(call,kind).pointers().isEmpty()); }
        for(String kind:List.of("presence","conditions","data","loops","calls","channels")) assertFalse(facet(call,kind).pointers().isEmpty(),kind);
        String context=Json.write(r.contexts()); assertTrue(context.contains("/behavior/faults")); assertTrue(context.contains("/contracts"));
        assertTrue(BehaviorComparison.markdown(r).contains("claim-default-0")); assertFalse(r.issues().isEmpty()); pointersResolve(r);
    }

    @Test void duplicateSourceIdentitiesRetainOccurrencesButSuppressOrdinalAssociations() throws Exception {
        var i=implementation("a",source(temp,0));
        for(String duplicate:List.of("step","process")) {
            var bad=changed(i,n->{
                if(duplicate.equals("step")) array(n,"/data/paths/processes/0/steps").add(n.at("/data/paths/processes/0/steps/1").deepCopy());
                else array(n,"/data/paths/processes").add(n.at("/data/paths/processes/0").deepCopy());
            });
            var r=compare(List.of(bad),alignment("ambiguous",ref(bad,"call")));
            assertEquals("invalid",r.rows().getFirst().status()); assertEquals(duplicate.equals("step")?7:11,r.rows().size());
            var calls=r.rows().stream().filter(row->"unmatched".equals(row.status())).flatMap(row->row.cells().stream())
                    .filter(c->"call".equals(tree(bad).at(facet(c,"presence").pointers().getFirst()).path("id").asString())).toList();
            assertEquals(2,calls.size());
            for(var c:calls) {
                for(String kind:List.of("data","calls","channels","contracts")) { unknown(facet(c,kind)); assertTrue(facet(c,kind).pointers().isEmpty(),kind); }
                assertFalse(facet(c,"conditions").pointers().isEmpty()); assertFalse(facet(c,"loops").pointers().isEmpty());
            }
            pointersResolve(r);
        }
    }

    @Test void danglingOrDuplicateIcoCannotBindChannelsByCoincidentOrdinalAndAmbiguousChannelsNeverChooseFirst() throws Exception {
        var i=implementation("a",source(temp,0));
        for(String defect:List.of("missing-ico","duplicate-ico","duplicate-interface","duplicate-channel")) {
            var bad=changed(i,n->{
                switch(defect) {
                    case "missing-ico" -> array(n,"/integration/icos").remove(0);
                    case "duplicate-ico" -> array(n,"/integration/icos").add(n.at("/integration/icos/0").deepCopy());
                    case "duplicate-interface" -> array(n,"/processes/0/interfaces").add(n.at("/processes/0/interfaces/0").deepCopy());
                    case "duplicate-channel" -> array(n,"/integration/channels").add(n.at("/integration/channels/0").deepCopy());
                }
            });
            var r=compare(List.of(bad)); var f=facet(cell(r,0,"start"),"channels"); unknown(f);
            assertTrue(f.pointers().contains("/behavior/processes/0/calls/0"),"referring call survives "+defect);
            if(!defect.equals("duplicate-channel")) assertTrue(f.pointers().stream().noneMatch(p->p.startsWith("/behavior/integration/channel")),defect);
            else {
                assertFalse(f.pointers().contains("/behavior/integration/channels/0"));
                assertFalse(f.pointers().contains("/behavior/integration/channels/3"));
                assertTrue(f.pointers().contains("/behavior/integration/channelUses/0"));
            }
            assertFalse(r.issues().isEmpty()); pointersResolve(r);
        }
    }

    @Test void mixedValidAndBrokenAssociationsKeepValidPointersButMakeWholeFacetUnknown() throws Exception {
        var i=implementation("a",source(temp,0));
        var bad=changed(i,n->{
            array(n,"/processes/0/calls/1/interfaces").add(999);
            array(n,"/data/processes/0/steps/1/before/0/declarations").add(999);
            array(n,"/data/paths/processes/0/arcs/0/reasons").add("synthetic-source-reason");
        });
        var r=compare(List.of(bad)); var c=cell(r,0,"call");
        for(String kind:List.of("data","channels","conditions")) { unknown(facet(c,kind)); assertFalse(facet(c,kind).pointers().isEmpty(),kind); }
        assertTrue(facet(c,"data").pointers().contains("/behavior/data/processes/0/declarations/1"));
        assertTrue(facet(c,"channels").pointers().contains("/behavior/processes/0/interfaces/1"));
        assertTrue(facet(c,"conditions").reasons().contains("synthetic-source-reason"));
        assertFalse(r.issues().isEmpty()); pointersResolve(r);
    }

    @Test void stepLocationAndUniqueProcessAssociationsAreRequiredWithoutNameFallback() throws Exception {
        var i=implementation("a",source(temp,0));
        for(String defect:List.of("data-location","call-location","duplicate-data-process","duplicate-wiring-process")) {
            var bad=changed(i,n->{
                switch(defect) {
                    case "data-location" -> object(n,"/data/processes/0/steps/1/location").put("ordinal",999);
                    case "call-location" -> object(n,"/processes/0/calls/1/location").put("ordinal",999);
                    case "duplicate-data-process" -> array(n,"/data/processes").add(n.at("/data/processes/0").deepCopy());
                    case "duplicate-wiring-process" -> array(n,"/processes").add(n.at("/processes/0").deepCopy());
                }
            });
            var r=compare(List.of(bad)); var c=cell(r,0,"call");
            String kind=defect.contains("data")?"data":"calls";
            unknown(facet(c,kind)); assertTrue(facet(c,kind).pointers().isEmpty(),defect);
            assertFalse(facet(c,"presence").pointers().isEmpty()); assertFalse(r.issues().isEmpty()); pointersResolve(r);
        }
    }
}
