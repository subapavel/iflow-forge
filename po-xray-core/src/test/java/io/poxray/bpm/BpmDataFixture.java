package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.SyntheticBpmSca.element;
import static io.poxray.SyntheticBpmSca.link;
import static io.poxray.SyntheticBpmSca.partition;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.model.BpmComponent;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Literal synthetic MOIN only; this builder performs no data-analysis calculations. */
final class BpmDataFixture {
    static final String W = "Galaxy.Workflow.";
    static final String M = "Galaxy.Mapping.";
    static final String R = "Galaxy.Rule.";
    static final String BOOLEAN = "4743E048A5B22940980411DC83220011252E548A";
    private final StringBuilder elements = new StringBuilder();
    private final StringBuilder relations = new StringBuilder();
    private String active = "true";

    static BpmDataFixture chain() {
        return new BpmDataFixture().node("StartEvent", "start", "pool")
                .node("MappingActivity", "map", "pool").node("EndEvent", "end", "pool")
                .sequence("enter", "start", "map", "pool").sequence("leave", "map", "end", "pool");
    }

    static BpmDataFixture nested() {
        return new BpmDataFixture().node("StartEvent", "start", "pool")
                .node("EmbeddedScope", "host", "pool").node("MappingActivity", "after", "pool")
                .node("EndEvent", "end", "pool").node("StartEvent", "child-start", "host")
                .node("MappingActivity", "child-map", "host").node("EndEvent", "child-end", "host")
                .sequence("enter", "start", "host", "pool").sequence("leave", "host", "after", "pool")
                .sequence("finish", "after", "end", "pool")
                .sequence("child-enter", "child-start", "child-map", "host")
                .sequence("child-leave", "child-map", "child-end", "host");
    }

    BpmDataFixture active(String value) { active = value; return this; }

    BpmDataFixture raw(String type, String id, String attributes, String children) {
        elements.append(element(type, id, attributes, children));
        return this;
    }

    BpmDataFixture relation(String type, String first, String second) {
        relations.append(link(type, first, second));
        return this;
    }

    BpmDataFixture node(String type, String id, String scope) {
        return named(type, id, id, scope);
    }

    BpmDataFixture named(String type, String id, String name, String scope) {
        raw(W + type, id, "", "<name originalText='" + escape(name) + "'/>");
        if (scope != null) member(id, scope);
        return this;
    }

    BpmDataFixture object(String id, String name, String scope) { return named("DataObject", id, name, scope); }
    BpmDataFixture member(String id, String scope) { return relation(W + "ScopeContainsScopeObjects", id, scope); }

    BpmDataFixture pool(String id, String value) {
        return raw(W + "Pool", id, attr("active", value), "").relation(W + "CollaborationContainsPools", id, "collab");
    }

    BpmDataFixture sequence(String id, String from, String to, String scope) {
        return edge("SequenceConnector", id, from, to, scope);
    }

    BpmDataFixture edge(String type, String id, String from, String to, String scope) {
        return node(type, id, scope).relation(W + "SourceOfIncomingConnectors", from, id)
                .relation(W + "TargetOfOutgoingConnectors", to, id);
    }

    BpmDataFixture mapping(String id, String owner, String direction) {
        return raw(M + "Mapping", id, "", "").owns(id, owner, direction);
    }

    BpmDataFixture owns(String id, String owner, String direction) {
        String association = switch (direction) {
            case "input" -> "InputMappingOfMappableObject";
            case "output" -> "OutputMappingOfMappableObject";
            case "view" -> "ViewContainsMapping";
            default -> throw new IllegalArgumentException(direction);
        };
        return relation(W + association, id, owner);
    }

    BpmDataFixture root(String mapping, String part, String target, String enabled) {
        return part(part, target, enabled).relation(M + "MappingContainsMappingParts", part, mapping);
    }

    BpmDataFixture child(String parent, String part, String target, String enabled) {
        return part(part, target, enabled).relation(M + "MappingPartHasChildren", part, parent);
    }

    BpmDataFixture part(String id, String target, String enabled) {
        raw(M + "MappingPart", id, attr("enabled", enabled) + " category='set'", "");
        if (target != null) qname(id + "-q", target, "urn:target").relation(M + "MappingPartHasQName", id + "-q", id);
        return this;
    }

    BpmDataFixture qname(String id, String name, String namespace) {
        return raw("XSD1.key.QName", id, attr("name", name) + " " + attr("namespace", namespace), "");
    }

    BpmDataFixture expression(String part, String expression) {
        return raw(R + "Expression", expression, "", "").relation(M + "MappingPartContainsExpression", expression, part);
    }

    BpmDataFixture literal(String part, String value) {
        return expression(part, part + "-expr").literalStep(part + "-expr", part + "-literal", value);
    }

    BpmDataFixture literalStep(String expression, String id, String value) {
        return raw(R + "Literal", id, attr("value", value), "")
                .relation(R + "ExpressionHasSteps", id, expression)
                .relation(R + "LiteralHasSimpleDataType", BOOLEAN, id);
    }

    BpmDataFixture reference(String part, String name) {
        return expression(part, part + "-expr").referenceStep(part + "-expr", part + "-ref", name, "urn:read");
    }

    BpmDataFixture referenceStep(String expression, String id, String name, String namespace) {
        raw(R + "Reference", id, "", "").relation(R + "ExpressionHasSteps", id, expression);
        if (name != null) qname(id + "-q", name, namespace).relation(R + "ReferenceHasQName", id + "-q", id);
        return this;
    }

    BpmDataFixture write(String owner, String mapping, String part, String target) {
        return mapping(mapping, owner, "output").root(mapping, part, target, "true").literal(part, "true");
    }

    BpmDataFixture initial(String object, String mapping, String part, String target) {
        return mapping(mapping, object, "view").root(mapping, part, target, "true").literal(part, "false");
    }

    BpmDataFixture invocation(String part, String id, String argumentMapping, boolean knownFunction) {
        expression(part, part + "-expr").raw(R + "FunctionInvocation", id, "", "")
                .relation(R + "ExpressionHasSteps", id, part + "-expr");
        if (knownFunction) raw(R + "FunctionInterface", id + "-function", "name='syntheticFunction'", "")
                .relation(R + "FunctionInvocationHasFunctionInterface", id + "-function", id);
        if (argumentMapping != null) relation(R + "FunctionInvocationHasParameterMapping", argumentMapping, id);
        return this;
    }

    BpmDataFixture argument(String mapping, String part, String target, String read) {
        return raw(M + "Mapping", mapping, "", "").root(mapping, part, target, "true").reference(part, read);
    }

    String xml() {
        return partition(element(W + "Collaboration", "collab", "", "<name originalText='Synthetic data'/>")
                + element(W + "Pool", "pool", attr("active", active), "") + elements
                + link(W + "CollaborationContainsPools", "pool", "collab") + relations);
    }

    ScaInventory read(Path file) { return PoXray.read(SyntheticBpmSca.write(file, Map.of(PROCESS, xml()))); }

    static BpmSourceEvidence evidence(ScaInventory inventory) { return inventory.bpm().getFirst().sourceEvidence(); }

    static ScaInventory withEvidence(ScaInventory inventory, BpmSourceEvidence replacement) {
        BpmComponent old = inventory.bpm().getFirst();
        return withComponents(inventory, List.of(new BpmComponent(old.dc(), old.processes(), old.catalog(), replacement)));
    }

    static ScaInventory withComponents(ScaInventory old, List<BpmComponent> components) {
        return new ScaInventory(old.schemaVersion(), old.generator(), old.source(), old.developmentComponents(),
                components, old.ejb(), old.issues(), old.unmapped());
    }

    static String attr(String name, String value) { return value == null ? "" : name + "='" + escape(value) + "'"; }
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("'", "&apos;").replace("<", "&lt;")
                .replace("\n", "&#10;").replace("\r", "&#13;");
    }
}
