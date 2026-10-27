package io.poxray;

import io.poxray.json.Json;
import io.poxray.migration.BehaviorAnalysis;
import io.poxray.migration.ContractCatalog;
import io.poxray.model.*;
import io.poxray.model.BehaviorComparisonInput.*;
import java.nio.file.*;
import java.util.*;

/** Shared wrapper-test inputs, built through literal synthetic exports and real P3/P4 analyses. */
public final class SyntheticComparison {
    private SyntheticComparison() { }
    public record Fixture(Path input, BehaviorComparisonInput document, List<Path> files) {
        public Map<Path,String> hashes() throws Exception {
            var result=new LinkedHashMap<Path,String>();
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            for(Path file:files) result.put(file,HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file))));
            return result;
        }
    }
    public static Fixture write(Path dir,int columns,String label,String status) throws Exception {
        Files.createDirectories(dir);
        var implementations=new ArrayList<Implementation>(); var steps=new ArrayList<StepRef>(); var files=new ArrayList<Path>();
        for(int n=0;n<columns;n++) {
            var pair=source(dir.resolve("implementation-"+n),n);
            files.addAll(List.of(pair.archive(),pair.tpz(),pair.exportJson(),pair.icosJson()));
            var behavior=BehaviorAnalysis.of(List.of(pair.sca()),List.of("synthetic"),pair.icos(),"synthetic-icos");
            var process=behavior.data().paths().processes().getFirst();
            var call=process.steps().stream().filter(s->"call".equals(s.id())).findFirst().orElseThrow();
            var ref=new ContractCatalogInput.StepRef(process.key(),call.ordinal());
            var proof=List.of(new ContractCatalogInput.Evidence("design","synthetic.md","section","proposed"));
            var claim=new ContractCatalogInput.ContractClaim("payload","script","proposed-internal",null,
                    new IntegrationInventory.XmlName("urn:synthetic","Payload"+n),
                    List.of(new ContractCatalogInput.PortClaim("request","Caller","invoke",ref,proof)),
                    List.of(new ContractCatalogInput.ValueClaim("/flag","default",ref,List.of(),"default|"+n,proof)),proof);
            var catalog=ContractCatalog.of(pair.icos(),behavior.data(),new ContractCatalogInput(
                    List.of(new ContractCatalogInput.ArtifactClaim("script","local-script",null,proof)),List.of(claim),null));
            implementations.add(new Implementation("i"+n,"Column|"+n,behavior,catalog));
            steps.add(new StepRef("i"+n,process.key(),call.ordinal()));
        }
        // Deliberately unreadable attribution: wrappers must never follow evidence locators.
        var evidence=List.of(new Evidence("design","file:///missing/synthetic-decision.json","https://invalid.example/inert","proposed"));
        var document=new BehaviorComparisonInput(1,implementations,List.of(new Alignment("chosen",label,status,steps,evidence)));
        Path input=dir.resolve("comparison.json"); Json.write(document,input); files.add(input);
        return new Fixture(input,document,List.copyOf(files));
    }

    private static SyntheticBehavior.Pair source(Path dir,int n) throws Exception {
        Files.createDirectories(dir); String w=SyntheticBehavior.W;
        String nodes=SyntheticBpmSca.element(w+"StartEvent","start","","")
                +SyntheticBpmSca.element(w+"AutomatedActivity","call","","<name originalText='Call|external'/>")
                +SyntheticBpmSca.element(w+"EndEvent","end","","")
                +SyntheticBpmSca.element(w+"BoundaryEvent","boundary","","")
                +SyntheticBpmSca.element(w+"AutomatedActivity","island","","<name originalText='Disconnected'/>")
                +SyntheticBpmSca.element(w+"SequenceConnector","enter","","")
                +SyntheticBpmSca.element(w+"ConditionalSequenceConnector","conditional","isDefault='false'","")
                +SyntheticBpmSca.element(SyntheticBehavior.R+"Expression","conditional-expr","stringExpression='condition-"+n+"'","")
                +SyntheticBpmSca.element(w+"ErrorEventDefinition","error","isCritical='false' isTechnical='true'","<name originalText='Fault|Declared'/>");
        String links=SyntheticBpmSca.link(w+"SourceOfIncomingConnectors","start","enter")
                +SyntheticBpmSca.link(w+"TargetOfOutgoingConnectors","call","enter")
                +SyntheticBpmSca.link(w+"SourceOfIncomingConnectors","call","conditional")
                +SyntheticBpmSca.link(w+"TargetOfOutgoingConnectors","end","conditional")
                +SyntheticBpmSca.link(w+"ConditionalSequenceConnectorContainsExpression","conditional-expr","conditional")
                +SyntheticBpmSca.link(w+"ActivityHasBoundaryEvent","boundary","call")
                +SyntheticBpmSca.link(w+"CatchEventHasTrigger","error","boundary");
        Path archive=dir.resolve("synthetic.sca"),tpz=dir.resolve("synthetic.tpz"),export=dir.resolve("export.json"),config=dir.resolve("icos.json");
        var sca=PoXray.read(SyntheticBpmSca.write(archive,Map.of(SyntheticBpmSca.PROCESS,
                SyntheticBpmSca.process("p-","Synthetic comparison",nodes,links,"start","call","end","island","enter","conditional"))));
        var icos=SyntheticBehavior.configuration(tpz,"channel-"+n);
        Json.write(sca,export); Json.write(icos,config);
        return new SyntheticBehavior.Pair(sca,icos,archive,tpz,export,config);
    }
}
