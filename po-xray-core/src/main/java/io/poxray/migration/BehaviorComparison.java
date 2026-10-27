package io.poxray.migration;

import io.poxray.json.Json;
import io.poxray.model.*;
import io.poxray.model.BehaviorComparisonInput.*;
import io.poxray.model.BehaviorComparisonReport.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Offline comparison of explicit source occurrences; no matching or execution inference. */
public final class BehaviorComparison {
    private BehaviorComparison() { }

    public static BehaviorComparisonReport read(Path inputJson) throws IOException {
        try { return of(Json.mapper().readValue(Files.readAllBytes(inputJson), BehaviorComparisonInput.class)); }
        catch (RuntimeException e) { throw new IOException("Cannot bind comparison input", e); }
    }

    public static BehaviorComparisonReport of(BehaviorComparisonInput input) {
        return new Builder(input).build();
    }

    static record Occurrence(int implementation, int process, int step, StepRef ref, JsonNode processNode, JsonNode stepNode) { }

    private static final class Choice {
        final Alignment alignment;
        final Map<Integer,List<Occurrence>> members = new LinkedHashMap<>();
        final Map<Integer,Integer> columnUses = new LinkedHashMap<>();
        boolean invalid;
        Choice(Alignment alignment) { this.alignment=alignment; }
    }

    private static final class Builder {
        final BehaviorComparisonInput input;
        final List<Implementation> implementations;
        final List<Issue> issues = new ArrayList<>();
        final List<Occurrence> occurrences = new ArrayList<>();
        final List<BehaviorComparisonFacets> sources = new ArrayList<>();
        Builder(BehaviorComparisonInput input) {
            this.input=input; implementations=input == null ? List.of() : input.implementations();
        }
        void issue(String message) { issues.add(new Issue(Issue.ERROR,"BEHAVIOR_COMPARISON_INPUT",message,null,"comparison")); }
        BehaviorComparisonReport build() {
            var contexts=new ArrayList<Context>();
            for (int i=0;i<implementations.size();i++) {
                var impl=implementations.get(i);
                if (impl == null || blank(impl.id()) || implementations.stream().filter(x->x!=null && Objects.equals(x.id(),impl.id())).count()!=1)
                    issue("Invalid implementation ID at column " + i + ": " + (impl==null ? null:impl.id()));
                var source=new BehaviorComparisonFacets(i,impl,issues); sources.add(source);
                contexts.add(source.context());
                occurrences.addAll(source.occurrences());
            }
            var rows=new ArrayList<Row>();
            if (input==null || input.formatVersion()!=1) {
                issue("Missing or unsupported comparison formatVersion: " + (input==null ? null:input.formatVersion()));
                return report(rows,contexts);
            }
            var choices=new ArrayList<Choice>();
            var uses=new LinkedHashMap<Occurrence,List<Integer>>();
            for(int index=0;index<input.alignments().size();index++) {
                var a=input.alignments().get(index); var c=new Choice(a); choices.add(c);
                if(a==null) { c.invalid=true; issue("Null alignment at " + index); continue; }
                if(blank(a.id()) || input.alignments().stream().filter(x->x!=null && Objects.equals(x.id(),a.id())).count()!=1) c.invalid=true;
                boolean proof=false, confirmed=false;
                for(int evidenceIndex=0;evidenceIndex<a.evidence().size();evidenceIndex++) {
                    var e=a.evidence().get(evidenceIndex);
                    if(e==null || blank(e.kind()) || blank(e.source()) || blank(e.locator()) || blank(e.status()))
                        issue("Malformed supplemental evidence for alignment index "+index+" ID "+a.id()+
                                " at evidence index "+evidenceIndex+": "+Json.write(e));
                    else { proof=true; confirmed |= "confirmed".equals(e.status()); }
                }
                if(!proof || !("candidate".equals(a.status()) || "confirmed".equals(a.status()) && confirmed)) c.invalid=true;
                if(a.steps().isEmpty()) c.invalid=true;
                for(var ref:a.steps()) {
                    if(ref!=null) for(int column=0;column<implementations.size();column++) {
                        var impl=implementations.get(column);
                        if(impl!=null && Objects.equals(impl.id(),ref.implementation())) c.columnUses.merge(column,1,Integer::sum);
                    }
                    List<Occurrence> found=resolve(ref);
                    if(found.size()!=1) { c.invalid=true; continue; }
                    var o=found.getFirst(); c.members.computeIfAbsent(o.implementation(),k->new ArrayList<>()).add(o);
                    uses.computeIfAbsent(o,k->new ArrayList<>()).add(index);
                }
                if(c.members.values().stream().anyMatch(x->x.size()!=1)) c.invalid=true;
                if(c.columnUses.values().stream().anyMatch(x->x!=1)) c.invalid=true;
            }
            for(var used:uses.values()) if(new HashSet<>(used).size()>1) for(int index:used) choices.get(index).invalid=true;
            var consumed=new HashSet<Occurrence>();
            for(int index=0;index<choices.size();index++) {
                var c=choices.get(index); var a=c.alignment;
                if(c.invalid) issue("Invalid alignment index " + index + ": " + Json.write(a));
                var selected=new LinkedHashMap<Integer,Occurrence>();
                c.members.forEach((column,values)->{ if(values.size()==1 && c.columnUses.get(column)==1) selected.put(column,values.getFirst()); });
                if(!c.invalid) consumed.addAll(selected.values());
                rows.add(new Row("alignment:"+index,a==null?null:a.id(),a==null?null:a.label(),c.invalid?"invalid":a.status(),
                        a==null?List.of():a.evidence(),cells(selected,c.invalid)));
            }
            for(var o:occurrences) if(!consumed.contains(o)) rows.add(new Row("unmatched:"+o.implementation()+":"+o.process()+":"+o.step(),
                    null,null,"unmatched",List.of(),cells(Map.of(o.implementation(),o),false)));
            return report(rows,contexts);
        }
        List<Occurrence> resolve(StepRef ref) {
            if(ref==null || blank(ref.implementation()) || ref.process()==null) return List.of();
            var cols=new ArrayList<Integer>();
            for(int i=0;i<implementations.size();i++) if(implementations.get(i)!=null && ref.implementation().equals(implementations.get(i).id())) cols.add(i);
            if(cols.size()!=1) return List.of();
            var source=sources.get(cols.getFirst());
            if(!source.uniqueProcess(ref.process())) return List.of();
            return occurrences.stream().filter(o->o.implementation()==cols.getFirst() && o.ref().equals(ref)).toList();
        }
        List<Cell> cells(Map<Integer,Occurrence> selected,boolean invalid) {
            var cells=new ArrayList<Cell>();
            for(int i=0;i<implementations.size();i++) {
                var o=selected.get(i);
                cells.add(o==null ? new Cell(i,null,"not-aligned",List.of(),List.of()) :
                        new Cell(i,o.ref(),"source-step",sources.get(i).facets(o),invalid?List.of("invalid-alignment"):List.of()));
            }
            return cells;
        }
        BehaviorComparisonReport report(List<Row> rows,List<Context> contexts) {
            return new BehaviorComparisonReport(1,"behavior-comparison-v1",implementations,rows,contexts,issues);
        }
    }
    static boolean blank(String value) { return value==null || value.isBlank(); }

    public static String markdown(BehaviorComparisonReport report) {
        var out=new StringBuilder("# Behavior comparison\n\nExplicit supplied alignment is not semantic equivalence or runtime proof. Confirmation is supplied attribution only.\n\n| Row / supplied evidence |");
        var trees=new ArrayList<JsonNode>();
        for(int i=0;i<report.implementations().size();i++) {
            var impl=report.implementations().get(i); trees.add(Json.mapper().valueToTree(impl));
            out.append(' ').append(i).append(": ").append(escape(impl==null?"unknown":impl.label())).append(" |");
        }
        out.append("\n| --- |").append(" --- |".repeat(trees.size())).append('\n');
        for(var row:report.rows()) {
            out.append("| ").append(escape(row.id()+" / "+row.alignment()+" / "+row.label()+" / "+row.status())).append("<br>").append(escape(Json.write(row.evidence()))).append(" |");
            for(var c:row.cells()) {
                out.append(' ').append(escape(c.state())).append("<br>");
                render(out,c.facets(),trees.get(c.implementation()));
                out.append(escape(Json.write(c.reasons()))).append(" |");
            }
            out.append('\n');
        }
        out.append("\n## Implementation context — contextual, non-step-bound evidence\n\n");
        for(var c:report.contexts()) {
            out.append("Column ").append(c.implementation()).append("<br>"); render(out,c.facets(),trees.get(c.implementation()));
            out.append(escape(Json.write(c.reasons()))).append("\n\n");
        }
        out.append("## Diagnostics\n\n").append(escape(Json.write(report.issues()))).append('\n');
        return out.toString();
    }
    private static void render(StringBuilder out,List<Facet> facets,JsonNode tree) {
        for(var f:facets) {
            out.append(escape(f.kind()+": "+f.state())).append("<br>");
            for(var pointer:f.pointers()) out.append(escape(pointer)).append(" = ").append(escape(tree.at(pointer).toString())).append("<br>");
            out.append(escape(Json.write(f.reasons()))).append("<br>");
        }
    }
    private static String escape(String value) {
        return String.valueOf(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("|","&#124;").replace("\r","").replace("\n","<br>");
    }
}
