package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.SyntheticBpmSca.partition;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Literal MOIN builder using the already accepted graph fixture, with separate metadata partitions. */
final class BpmFaultFixture {
    static final String W = "Galaxy.Workflow.";
    static final String R = "Galaxy.Rule.";
    static final String M = "Galaxy.Mapping.";
    static final String TECHNICAL = "E0EE4DB2B3D39C3FEAEE11E2BF54001999A12632";
    private final BpmDataFixture graph;
    private final Map<String, String> external = new LinkedHashMap<>();

    private BpmFaultFixture(BpmDataFixture graph) { this.graph = graph; }
    static BpmFaultFixture chain() { return new BpmFaultFixture(BpmDataFixture.chain()); }
    static BpmFaultFixture nested() { return new BpmFaultFixture(BpmDataFixture.nested()); }
    BpmFaultFixture raw(String type, String id, String attributes, String children) {
        graph.raw(type, id, attributes, children); return this;
    }
    BpmFaultFixture relation(String association, String first, String second) {
        graph.relation(association, first, second); return this;
    }
    BpmFaultFixture node(String type, String id, String scope) { graph.node(type, id, scope); return this; }
    BpmFaultFixture member(String id, String scope) { graph.member(id, scope); return this; }
    BpmFaultFixture pool(String id, String active) { graph.pool(id, active); return this; }
    BpmFaultFixture sequence(String id, String from, String to, String scope) { graph.sequence(id, from, to, scope); return this; }
    BpmFaultFixture active(String value) { graph.active(value); return this; }
    BpmFaultFixture error(String id, String critical, String technical) {
        return raw(W + "ErrorEventDefinition", id,
                attr("isCritical", critical) + " " + attr("isTechnical", technical), "<name originalText='" + id + "'/>");
    }
    BpmFaultFixture message(String id) { return raw(W + "MessageEventDefinition", id, "", "<name originalText='" + id + "'/>"); }
    BpmFaultFixture activity(String owner, String definition) { return relation(W + "ActivityContainsEventDefinitions", definition, owner); }
    BpmFaultFixture attach(String owner, String boundary) { return relation(W + "ActivityHasBoundaryEvent", boundary, owner); }
    BpmFaultFixture trigger(String owner, String definition) { return relation(W + "CatchEventHasTrigger", definition, owner); }
    BpmFaultFixture result(String owner, String definition) { return relation(W + "ThrowEventHasResult", definition, owner); }
    BpmFaultFixture boundary(String owner, String boundary, String definition) {
        return node("BoundaryEvent", boundary, null).attach(owner, boundary).trigger(boundary, definition);
    }
    BpmFaultFixture fault(String definition, String fault) { return relation(W + "ErrorEventDefinitionHasWSDLFault", fault, definition); }
    BpmFaultFixture condition(String owner, String expression, String editor) {
        return raw(R + "Expression", expression, attr("stringExpression", editor), "")
                .relation(W + "CatchEventContainsExpression", owner, expression);
    }
    BpmFaultFixture literal(String expression, String id, String value) { graph.literalStep(expression, id, value); return this; }
    BpmFaultFixture reference(String expression, String id, String name, String namespace) {
        graph.referenceStep(expression, id, name, namespace); return this;
    }
    BpmFaultFixture external(String path, String body) { external.merge(path, body, String::concat); return this; }
    Map<String, String> partitions() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(PROCESS, graph.xml());
        external.forEach((path, body) -> result.put(path, partition(body)));
        return result;
    }
    ScaInventory read(Path file) { return PoXray.read(SyntheticBpmSca.write(file, partitions())); }
    static BpmSourceEvidence evidence(ScaInventory inventory) { return inventory.bpm().getFirst().sourceEvidence(); }
    static List<Locator> locations(ScaInventory inventory, String id) {
        return evidence(inventory).elements().stream().filter(e -> id.equals(e.id())).map(BpmSourceEvidence.Element::location).toList();
    }
    static Locator location(ScaInventory inventory, String id) { return locations(inventory, id).getFirst(); }
    static String attr(String name, String value) { return BpmDataFixture.attr(name, value); }
}
