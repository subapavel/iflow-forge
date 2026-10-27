package io.poxray.migration;

import io.poxray.json.Json;
import io.poxray.model.*;
import io.poxray.model.BehaviorComparisonInput.*;
import io.poxray.model.BehaviorComparisonReport.*;
import io.poxray.migration.BehaviorComparison.Occurrence;
import java.util.*;
import java.util.function.Predicate;
import tools.jackson.databind.JsonNode;

/** Conservative associations over the serialized shape of the retained, typed source reports. */
final class BehaviorComparisonFacets {
    private static final String PATHS="/behavior/data/paths";
    final int column;
    final Implementation implementation;
    final JsonNode root;
    final List<Issue> issues;
    final Set<String> reported=new LinkedHashSet<>();
    final Map<Occurrence,List<Facet>> cache=new HashMap<>();

    BehaviorComparisonFacets(int column,Implementation implementation,List<Issue> issues) {
        this.column=column; this.implementation=implementation; this.issues=issues;
        root=Json.mapper().valueToTree(implementation);
        propagate(root,"");
        for(String path:List.of("/behavior","/behavior/data",PATHS,"/behavior/faults","/contracts")) supported(path);
        validateEntries(root,"");
    }
    void validateEntries(JsonNode node,String path) {
        if(node.isArray()) for(int i=0;i<node.size();i++) {
            if(node.path(i).isNull()) diagnostic("Null source list member "+path+"/"+i);
            else validateEntries(node.path(i),path+"/"+i);
        }
        else if(node.isObject()) for(var entry:node.properties()) validateEntries(entry.getValue(),path+"/"+entry.getKey());
    }
    void diagnostic(String message) {
        if(reported.add(message)) issues.add(new Issue(Issue.ERROR,"BEHAVIOR_COMPARISON_ASSOCIATION",
                "Implementation column "+column+" ("+(implementation==null?null:implementation.id())+"): "+message,null,"implementation:"+column));
    }
    // Retain every upstream issue with the source pointer and column, including nested report issues.
    void propagate(JsonNode n,String path) {
        if(n.isObject()) for(var entry:n.properties()) {
            String p=path+"/"+entry.getKey().replace("~","~0").replace("/","~1");
            if("issues".equals(entry.getKey())) for(var issue:entry.getValue()) {
                issues.add(new Issue(issue.path("severity").asString(Issue.WARNING),issue.path("code").asString("SOURCE_ISSUE"),
                        "Implementation column "+column+" ("+(implementation==null?null:implementation.id())+") "+p+": "+issue,null,"implementation:"+column));
            } else propagate(entry.getValue(),p);
        } else if(n.isArray()) for(int i=0;i<n.size();i++) propagate(n.path(i),path+"/"+i);
    }
    boolean supported(String path) {
        var node=root.at(path);
        if(!node.isObject() || node.path("formatVersion").asInt(-1)!=1) {
            diagnostic("Missing or unsupported report "+path+" formatVersion="+node.path("formatVersion")); return false;
        }
        return true;
    }
    List<Occurrence> occurrences() {
        if(!supported("/behavior") || !supported("/behavior/data") || !supported(PATHS)) return List.of();
        var result=new ArrayList<Occurrence>(); var processes=root.at(PATHS+"/processes");
        for(int p=0;p<processes.size();p++) {
            var process=processes.path(p);
            if(!process.isObject() || !process.path("key").isObject()) { diagnostic("Malformed path process "+p); continue; }
            BpmPathReport.ProcessKey key=Json.mapper().treeToValue(process.path("key"),BpmPathReport.ProcessKey.class);
            if(!uniqueProcess(key)) diagnostic("Ambiguous path ProcessKey "+key);
            var steps=process.path("steps");
            for(int s=0;s<steps.size();s++) {
                var step=steps.path(s);
                if(!step.isObject()) { diagnostic("Malformed path step "+p+":"+s); continue; }
                if(!present(step.path("location"))) diagnostic("Missing source step location "+p+":"+s);
                if(matches(steps,"ordinal",step.path("ordinal")).size()!=1) diagnostic("Ambiguous path step ordinal "+p+":"+step.path("ordinal"));
                result.add(new Occurrence(column,p,s,new StepRef(implementation.id(),key,step.path("ordinal").asInt()),process,step));
            }
        }
        return result;
    }
    boolean uniqueProcess(BpmPathReport.ProcessKey key) {
        return key!=null && matches(root.at(PATHS+"/processes"),"key",Json.mapper().valueToTree(key)).size()==1;
    }
    Context context() {
        var facets=new ArrayList<Facet>();
        String[][] paths={{"data-context","/behavior/data"},{"fault-context","/behavior/faults"},
                {"configuration-context","/behavior/integration"},{"wiring-context","/behavior/processes"},{"contract-context","/contracts"}};
        for(var pair:paths) { var f=new Selection(pair[0]); if(present(root.at(pair[1]))) f.add(pair[1]); facets.add(f.finish()); }
        return new Context(column,facets,List.of("implementation-level evidence; not attached to every step"));
    }
    List<Facet> facets(Occurrence o) { return cache.computeIfAbsent(o,this::build); }
    List<Facet> build(Occurrence o) {
        var result=new ArrayList<Facet>(); String pp=PATHS+"/processes/"+o.process();
        var presence=new Selection("presence"); presence.add(pp+"/steps/"+o.step());
        result.add(new Facet("presence",List.copyOf(presence.pointers),"available",List.copyOf(presence.reasons)));
        var location=o.stepNode().path("location");
        var conditions=new Selection("conditions");
        select(conditions,pp+"/arcs",n->endpointLocations(n.path("sources"),location,false) || endpointLocations(n.path("targets"),location,false));
        for(var pointer:conditions.pointers) for(String side:List.of("sources","targets"))
            for(var endpoint:root.at(pointer+"/"+side)) if(endpoint.path("candidates").size()!=1)
                conditions.defect("ambiguous-or-unresolved-arc-endpoint "+pointer+"/"+side);
        result.add(conditions.finish());
        boolean unique=uniqueProcess(o.ref().process()) && matches(o.processNode().path("steps"),"ordinal",o.stepNode().path("ordinal")).size()==1;
        result.add(data(o,unique));
        var loops=new Selection("loops"); select(loops,pp+"/loops",n->contains(n.path("hosts"),location)); result.add(loops.finish());
        result.add(faults(o));
        var call=new Selection("calls"); var channels=new Selection("channels"); calls(o,unique,call,channels);
        result.add(call.finish()); result.add(channels.finish()); result.add(contracts(o,unique));
        return List.copyOf(result);
    }
    Facet data(Occurrence o,boolean unique) {
        var selected=new Selection("data");
        if(!unique) { selected.defect("ambiguous-source-step"); return selected.finish(); }
        String process=process("/behavior/data/processes",o,true,selected);
        if(process==null) return selected.finish();
        var step=root.at(process+"/steps"); var indices=matches(step,"step",o.stepNode().path("ordinal"));
        if(indices.size()!=1 || !sameLocation(step.path(indices.isEmpty()? -1:indices.getFirst()).path("location"),o.stepNode().path("location"))) {
            selected.defect("missing-or-ambiguous-data-step-location "+process+" ordinal="+o.ref().step()); return selected.finish();
        }
        String state=process+"/steps/"+indices.getFirst(); selected.add(state); selected.sourceReasons(root.at(process).path("reasons"));
        var ordinals=new LinkedHashSet<JsonNode>();
        for(String side:List.of("before","after")) for(var origin:root.at(state+"/"+side)) {
            if(!origin.isObject()) { selected.defect("malformed-data-origin "+state); continue; }
            for(var id:origin.path("declarations")) ordinals.add(id);
        }
        var declarations=root.at(process+"/declarations"); var selectedDeclarations=new LinkedHashSet<JsonNode>();
        for(var ordinal:ordinals) {
            var index=uniqueIndex(declarations,"ordinal",ordinal,selected,"declaration "+process);
            if(index!=null) selectedDeclarations.add(declarations.path(index).path("ordinal"));
        }
        // Emit in source-list order, not origin traversal order.
        var mappings=new LinkedHashSet<JsonNode>();
        for(int i=0;i<declarations.size();i++) if(selectedDeclarations.contains(declarations.path(i).path("ordinal"))) {
            selected.add(process+"/declarations/"+i); mappings.add(declarations.path(i).path("mapping"));
        }
        var mappingList=root.at(process+"/mappings"); var mappingIndices=new TreeSet<Integer>();
        for(var ordinal:mappings) { var i=uniqueIndex(mappingList,"ordinal",ordinal,selected,"mapping "+process); if(i!=null) mappingIndices.add(i); }
        for(int i:mappingIndices) selected.add(process+"/mappings/"+i);
        var reads=root.at(process+"/reads");
        for(int i=0;i<reads.size();i++) if(selectedDeclarations.contains(reads.path(i).path("declaration"))) selected.add(process+"/reads/"+i);
        return selected.finish();
    }
    Facet faults(Occurrence o) {
        var selected=new Selection("faults");
        if(!supported("/behavior/faults")) { selected.defect("unsupported-or-missing-faults"); return selected.finish(); }
        if(!uniqueProcess(o.ref().process())) { selected.defect("ambiguous-source-process"); return selected.finish(); }
        String process=process("/behavior/faults/processes",o,true,selected);
        if(process==null) return selected.finish();
        var input=Json.mapper().valueToTree(o.ref().process().input());
        var left=root.at(PATHS+"/inputs"); var right=root.at("/behavior/faults/inputs");
        var a=uniqueIndex(left,"ordinal",input,selected,"path input"); var b=uniqueIndex(right,"ordinal",input,selected,"fault input");
        if(a==null || b==null || !left.path(a).equals(right.path(b))) { selected.defect("fault-input-ancestry-mismatch"); return selected.finish(); }
        selected.sourceReasons(root.at(process).path("reasons"));
        var location=o.stepNode().path("location");
        select(selected,process+"/declarations",n->endpoint(n.path("owner"),location,true) || endpoint(n.path("event"),location,true));
        for(var pointer:selected.pointers) for(String role:List.of("owner","event"))
            if(root.at(pointer+"/"+role+"/candidates").size()>1) selected.defect("ambiguous-fault-endpoint "+pointer+"/"+role);
        return selected.finish();
    }
    void calls(Occurrence o,boolean unique,Selection calls,Selection channels) {
        if(!unique) { calls.defect("ambiguous-source-step"); channels.defect("ambiguous-source-step"); return; }
        String process=process("/behavior/processes",o,false,calls);
        if(process==null) { channels.reasons.addAll(calls.reasons); return; }
        var callList=root.at(process+"/calls"); var found=matches(callList,"step",o.stepNode().path("ordinal"));
        if(found.size()!=1 || !sameLocation(callList.path(found.isEmpty()?-1:found.getFirst()).path("location"),o.stepNode().path("location"))) {
            calls.defect("missing-or-ambiguous-call-step-location "+process+" ordinal="+o.ref().step()); channels.reasons.addAll(calls.reasons); return;
        }
        String call=process+"/calls/"+found.getFirst(); calls.add(call); channels.add(call);
        // Process-level reasons cover unrelated ingress/configuration; only the selected call
        // and followed interface/configuration evidence can qualify this step's call facet.
        var interfaceList=root.at(process+"/interfaces"); var interfaceIndices=new TreeSet<Integer>();
        for(var ordinal:root.at(call+"/interfaces")) {
            var i=uniqueIndex(interfaceList,"ordinal",ordinal,channels,"interface "+process); if(i!=null) interfaceIndices.add(i);
        }
        var icoIndices=new TreeSet<Integer>();
        for(int i:interfaceIndices) {
            String ip=process+"/interfaces/"+i; channels.add(ip);
            var matches=root.at(ip+"/matches");
            for(int m=0;m<matches.size();m++) {
                channels.add(ip+"/matches/"+m);
                var ico=uniqueIndex(root.at("/behavior/integration/icos"),"ordinal",matches.path(m).path("ico"),channels,"ICO "+ip);
                if(ico!=null) icoIndices.add(ico);
            }
        }
        var icoOrdinals=new LinkedHashSet<JsonNode>();
        for(int i:icoIndices) { channels.add("/behavior/integration/icos/"+i); icoOrdinals.add(root.at("/behavior/integration/icos/"+i+"/ordinal")); }
        var uses=root.at("/behavior/integration/channelUses"); var channelIndices=new TreeSet<Integer>();
        for(int i=0;i<uses.size();i++) if(icoOrdinals.contains(uses.path(i).path("ico"))) {
            channels.add("/behavior/integration/channelUses/"+i);
            for(var ordinal:uses.path(i).path("candidates")) {
                var c=uniqueIndex(root.at("/behavior/integration/channels"),"ordinal",ordinal,channels,"channel use "+i);
                if(c!=null) channelIndices.add(c);
            }
        }
        for(int i:channelIndices) channels.add("/behavior/integration/channels/"+i);
        if(channelIndices.isEmpty()) channels.reasons.add("no-associated-channel-configuration; referring call evidence only");
    }
    Facet contracts(Occurrence o,boolean unique) {
        var selected=new Selection("contracts");
        if(!supported("/contracts")) { selected.defect("unsupported-or-missing-contracts"); return selected.finish(); }
        if(!unique) { selected.defect("ambiguous-source-step"); return selected.finish(); }
        var catalog=root.at("/contracts");
        // A source catalog with no attributed step references has no ancestry association
        // to validate. Its complete evidence remains in implementation context.
        if(!hasContractStepClaims(catalog)) return selected.finish();
        if(!present(catalog.path("data")) || !catalog.path("data").equals(root.at("/behavior/data"))) {
            selected.defect("contract-data-ancestry-mismatch"); return selected.finish();
        }
        select(selected,"/contracts/contracts",n->{
            for(var port:n.path("ports")) if(contractRef(port.path("step"),o)) return true;
            for(var value:n.path("values")) {
                if(contractRef(value.path("producer"),o)) return true;
                for(var consumer:value.path("consumers")) if(contractRef(consumer,o)) return true;
            }
            return false;
        });
        return selected.finish();
    }
    boolean hasContractStepClaims(JsonNode catalog) {
        for(var contract:catalog.path("contracts")) {
            for(var port:contract.path("ports")) if(present(port.path("step"))) return true;
            for(var value:contract.path("values")) {
                if(present(value.path("producer"))) return true;
                for(var consumer:value.path("consumers")) if(present(consumer)) return true;
            }
        }
        return false;
    }
    boolean contractRef(JsonNode ref,Occurrence o) {
        return ref.isObject() && ref.path("process").equals(o.processNode().path("key")) && ref.path("step").equals(o.stepNode().path("ordinal"));
    }
    String process(String path,Occurrence o,boolean ancestry,Selection selected) {
        var entries=root.at(path); var index=uniqueIndex(entries,"key",o.processNode().path("key"),selected,"process "+path);
        if(index==null) return null;
        var p=entries.path(index);
        if(ancestry) for(String field:List.of("dc","id","source")) if(!present(p.path(field)) || !present(o.processNode().path(field)) || !p.path(field).equals(o.processNode().path(field))) {
            selected.defect("process-ancestry-mismatch "+path+"/"+index+"/"+field); return null;
        }
        return path+"/"+index;
    }
    Integer uniqueIndex(JsonNode array,String field,JsonNode value,Selection selected,String label) {
        var matches=matches(array,field,value);
        if(!present(value) || matches.size()!=1) { selected.defect("missing-or-ambiguous-"+label+" reference="+value); return null; }
        return matches.getFirst();
    }
    static List<Integer> matches(JsonNode array,String field,JsonNode value) {
        var result=new ArrayList<Integer>();
        if(present(value)) for(int i=0;i<array.size();i++) if(array.path(i).isObject() && value.equals(array.path(i).path(field))) result.add(i);
        return result;
    }
    static boolean present(JsonNode node) { return node!=null && !node.isNull() && !node.isMissingNode(); }
    static boolean sameLocation(JsonNode a,JsonNode b) { return present(a) && present(b) && a.equals(b); }
    static boolean contains(JsonNode array,JsonNode value) { if(present(value)) for(var n:array) if(value.equals(n)) return true; return false; }
    static boolean endpointLocations(JsonNode endpoints,JsonNode location,boolean candidateObjects) {
        for(var e:endpoints) if(endpoint(e,location,candidateObjects)) return true; return false;
    }
    static boolean endpoint(JsonNode endpoint,JsonNode location,boolean candidateObjects) {
        for(var candidate:endpoint.path("candidates")) if(sameLocation(candidateObjects?candidate.path("location"):candidate,location)) return true;
        return false;
    }
    void select(Selection selected,String path,Predicate<JsonNode> predicate) {
        var values=root.at(path);
        for(int i=0;i<values.size();i++) {
            if(!values.path(i).isObject()) selected.defect("malformed-source-item "+path+"/"+i);
            else if(predicate.test(values.path(i))) selected.add(path+"/"+i);
        }
    }
    final class Selection {
        final String kind;
        final LinkedHashSet<String> pointers=new LinkedHashSet<>(), reasons=new LinkedHashSet<>();
        Selection(String kind) { this.kind=kind; }
        void defect(String reason) { reasons.add(reason); diagnostic(kind+": "+reason); }
        void sourceReasons(JsonNode values) {
            for(var reason:values) reasons.add(reason.isString()?reason.asString():reason.toString());
        }
        void collectReasons(JsonNode value) {
            if(value.isObject()) for(var entry:value.properties()) {
                if("reasons".equals(entry.getKey())) sourceReasons(entry.getValue()); else collectReasons(entry.getValue());
            } else if(value.isArray()) for(var item:value) collectReasons(item);
        }
        void add(String pointer) {
            var value=root.at(pointer);
            if(!present(value)) { defect("missing-source "+pointer); return; }
            pointers.add(pointer); collectReasons(value);
        }
        Facet finish() {
            if(pointers.isEmpty()) reasons.add("no-associated-source-evidence; runtime absence is not established");
            return new Facet(kind,List.copyOf(pointers),reasons.isEmpty()?"candidate":"unknown",List.copyOf(reasons));
        }
    }
}
