package io.poxray.migration;

import io.poxray.integration.ContractResources;
import io.poxray.model.BpmDataReport;
import io.poxray.model.ContractCatalogInput.Evidence;
import io.poxray.model.ContractCatalogInput.PortClaim;
import io.poxray.model.ContractCatalogReport.*;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.*;
import io.poxray.model.Issue;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** One build's ordered state; matching never picks the first of several identities. */
final class ContractCatalogBuilder {
    final List<Artifact> artifacts = new ArrayList<>();
    final List<Contract> contracts = new ArrayList<>();
    final List<Dependency> dependencies = new ArrayList<>();
    final List<Issue> issues = new ArrayList<>();
    final BpmDataReport data;
    private final List<Identity> identities = new ArrayList<>();
    private final List<Resource> resources = new ArrayList<>();

    private record Identity(ObjRef ref, String artifact, String contract) { }
    private record Match(List<Identity> identities, String state) {
        List<String> artifacts() { return identities.stream().map(Identity::artifact).distinct().toList(); }
    }
    private record Resource(String id, ContractResource resource, List<Evidence> evidence) { }

    ContractCatalogBuilder(BpmDataReport data) {
        this.data = data;
        if (data == null) issue("data", "Missing BPM data coverage");
        else issues.addAll(data.issues());
    }

    static <T> List<T> list(List<T> items) { return items == null ? List.of() : items; }
    static boolean present(String value) { return value != null && !value.isBlank(); }
    static String id(String kind, int index) { return "source:" + kind + ":" + index; }
    static List<Evidence> export(String locator) {
        return List.of(new Evidence("export", "inventory", locator, "observed"));
    }
    void issue(String locator, String message) {
        issues.add(new Issue(Issue.WARNING, "contract-catalog-unknown", message,
                new Issue.Location(null, null, null, locator), "contract-catalog"));
    }

    void source(IntegrationInventory inventory) {
        if (inventory == null) { issue("inventory", "Missing integration inventory coverage"); return; }
        issues.addAll(list(inventory.issues()));
        var esr = inventory.esr();
        if (esr == null) { issue("inventory.esr", "Missing ESR coverage"); return; }
        coverage(esr);
        for (int i = 0; i < list(esr.messageTypes()).size(); i++) {
            var mt = esr.messageTypes().get(i);
            String id = id("native-message-type", i), loc = "esr.messageTypes[" + i + "]";
            if (mt == null) { issue(loc, "Missing message type entry"); continue; }
            sourceArtifact(id, "native-message-type", mt.id(), null, loc);
            String contract = id + ":contract";
            var name = present(mt.xmlNamespace()) && mt.id() != null && present(mt.id().name())
                    ? new XmlName(mt.xmlNamespace(), mt.id().name()) : null;
            addContract(contract, id, mt.id(), name, export(loc));
            identities.add(new Identity(mt.id(), id, contract));
            if (name == null) issue(loc, "Unknown native XML name for " + mt.id());
        }
        for (int i = 0; i < list(esr.dataTypes()).size(); i++) {
            var dt = esr.dataTypes().get(i);
            String id = id("native-data-type", i), loc = "esr.dataTypes[" + i + "]";
            if (dt == null) { issue(loc, "Missing data type entry"); continue; }
            sourceArtifact(id, "native-data-type", dt.id(), null, loc);
            identities.add(new Identity(dt.id(), id, null));
            if (present(dt.xsd())) {
                try {
                    int declaration = 0;
                    var schemas = ContractResources.read(null, dt.xsd());
                    for (int schema = 0; schema < schemas.size(); schema++) {
                        var resource = schemas.get(schema);
                        var evidence = export(loc + ".xsd");
                        resources.add(new Resource(id, resource, evidence));
                        for (var item : resource.declarations()) {
                            var proof = new ArrayList<>(evidence);
                            proof.add(new Evidence("export", "inventory", loc + ".xsd#" + item.locator(), "observed"));
                            addContract(id + ":declaration:" + declaration++, id, dt.id(), item.name(), proof);
                        }
                    }
                    if (schemas.isEmpty()) issue(loc + ".xsd", "No recognized native DT XML schema declarations");
                }
                catch (IllegalArgumentException e) { issue(loc + ".xsd", "Unreadable native DT XML: " + e.getMessage()); }
            } else issue(loc + ".xsd", "Missing native DT XML declaration coverage for " + dt.id());
        }
        for (int i = 0; i < list(esr.externalDefinitions()).size(); i++) {
            var definition = esr.externalDefinitions().get(i);
            String id = id("external-definition", i), loc = "esr.externalDefinitions[" + i + "]";
            if (definition == null) { issue(loc, "Missing external definition entry"); continue; }
            sourceArtifact(id, "external-definition", definition.id(), null, loc);
            identities.add(new Identity(definition.id(), id, null));
            for (int j = 0; j < definition.messageReferences().size(); j++) {
                var ref = definition.messageReferences().get(j);
                String contract = id + ":message:" + j;
                addContract(contract, id, ref, null, export(loc + ".messageReferences[" + j + "]"));
                identities.add(new Identity(ref, id, contract));
                issue(loc + ".messageReferences[" + j + "]", "Unknown external-message QName (source binding unknown): " + ref);
            }
            if (definition.messageReferences().isEmpty()) {
                for (int j = 0; j < definition.messages().size(); j++) {
                    String name = definition.messages().get(j);
                    addContract(id + ":legacy-message:" + j, id,
                            new ObjRef("ifmextmes", name, null, null, null, null), null,
                            export(loc + ".messages[" + j + "]"));
                    issue(loc + ".messages[" + j + "]", "Legacy external message lacks full reference and QName: " + name);
                }
            }
            sourceResources(id, loc + ".resources", definition.resources());
            if (definition.resources().isEmpty()) issue(loc + ".resources", "Missing retained XML resource coverage for " + definition.id());
        }
        for (int i = 0; i < list(esr.messageMappings()).size(); i++) {
            var mapping = esr.messageMappings().get(i);
            String id = id("esr-message-mapping", i), loc = "esr.messageMappings[" + i + "]";
            if (mapping == null) { issue(loc, "Missing message mapping entry"); continue; }
            sourceArtifact(id, "esr-message-mapping", mapping.id(), null, loc);
            identities.add(new Identity(mapping.id(), id, null));
            if (mapping.logic() != null && mapping.logic().localFunctions() != null)
                functions(id, loc + ".logic.localFunctions.functions", mapping.logic().localFunctions());
        }
        for (int i = 0; i < list(esr.functionLibraries()).size(); i++) {
            var library = esr.functionLibraries().get(i);
            String id = id("esr-function-library", i), loc = "esr.functionLibraries[" + i + "]";
            if (library == null) { issue(loc, "Missing function library entry"); continue; }
            sourceArtifact(id, "esr-function-library", library.id(), null, loc);
            identities.add(new Identity(library.id(), id, null));
            functions(id, loc + ".functions", library);
        }
        sourceUses(esr);
    }

    private void coverage(Esr esr) {
        if (esr.messageTypes() == null) issue("esr.messageTypes", "Unknown message type coverage");
        if (esr.dataTypes() == null) issue("esr.dataTypes", "Unknown data type coverage");
        if (esr.externalDefinitions() == null) issue("esr.externalDefinitions", "Unknown external definition coverage");
        if (esr.messageMappings() == null) issue("esr.messageMappings", "Unknown mapping coverage");
        if (esr.functionLibraries() == null) issue("esr.functionLibraries", "Unknown function library coverage");
        if (esr.serviceInterfaces() == null) issue("esr.serviceInterfaces", "Unknown service port coverage");
    }

    private void sourceArtifact(String id, String kind, ObjRef ref, String owner, String locator) {
        artifacts.add(new Artifact(id, kind, ref, owner, "source", export(locator)));
    }

    private void functions(String owner, String locator, FunctionLibrary library) {
        for (int i = 0; i < list(library.functions()).size(); i++)
            sourceArtifact(owner + ":function:" + i, "mapping-function", null, owner, locator + "[" + i + "]");
    }

    private void sourceResources(String owner, String locator, List<ContractResource> values) {
        for (int i = 0; i < values.size(); i++) {
            String id = owner + ":resource:" + i;
            sourceArtifact(id, "xml-resource", null, owner, locator + "[" + i + "]");
            resource(id, values.get(i), export(locator + "[" + i + "]"));
        }
    }

    void resource(String id, ContractResource resource, List<Evidence> evidence) {
        if (resource == null) { issue(id, "Missing resource evidence"); return; }
        resources.add(new Resource(id, resource, evidence));
        for (int i = 0; i < resource.declarations().size(); i++) {
            var declaration = resource.declarations().get(i);
            var proof = new ArrayList<>(evidence);
            proof.addAll(resourceEvidence(evidence, resource.sourcePath(), ".declarations[" + i + "]", declaration.locator()));
            addContract(id + ":declaration:" + i, id, null, declaration.name(), proof);
        }
    }

    private void addContract(String id, String artifact, ObjRef ref, XmlName name, List<Evidence> evidence) {
        contracts.add(new Contract(id, artifact, "unknown", ref, name, List.of(), List.of(), evidence));
    }

    private static List<Evidence> resourceEvidence(List<Evidence> basis, String path, String field, String locator) {
        var source = basis.stream().filter(e -> e != null && "export".equals(e.kind()) && "inventory".equals(e.source())).findFirst();
        return source.isPresent() ? export(source.get().locator() + field + "#" + locator)
                : List.of(new Evidence("artifact-declaration", path, locator, "observed"));
    }

    private void sourceUses(Esr esr) {
        for (int i = 0; i < list(esr.messageTypes()).size(); i++) {
            var mt = esr.messageTypes().get(i);
            if (mt != null) reference(id("native-message-type", i), "message-data-type", mt.dataType(), "esr.messageTypes[" + i + "].dataType");
        }
        for (int i = 0; i < list(esr.dataTypes()).size(); i++) {
            var dt = esr.dataTypes().get(i);
            if (dt == null) continue;
            for (int j = 0; j < list(dt.uses()).size(); j++)
                reference(id("native-data-type", i), "data-type-use", dt.uses().get(j), "esr.dataTypes[" + i + "].uses[" + j + "]");
        }
        // Service uses precede mapping uses in original operation order.
        for (int i = 0; i < list(esr.serviceInterfaces()).size(); i++) {
            var service = esr.serviceInterfaces().get(i);
            if (service == null) continue;
            for (int j = 0; j < list(service.operations()).size(); j++) {
                var operation = service.operations().get(j);
                if (operation == null) continue;
                String loc = "esr.serviceInterfaces[" + i + "].operations[" + j + "]";
                port(operation.request(), "request", String.valueOf(service.id()), operation.name(), loc + ".request");
                port(operation.response(), "response", String.valueOf(service.id()), operation.name(), loc + ".response");
                for (int k = 0; k < list(operation.faults()).size(); k++)
                    port(operation.faults().get(k), "fault", String.valueOf(service.id()), operation.name(), loc + ".faults[" + k + "]");
            }
        }
        for (int i = 0; i < list(esr.messageMappings()).size(); i++) {
            var mapping = esr.messageMappings().get(i);
            if (mapping == null) continue;
            String id = id("esr-message-mapping", i), loc = "esr.messageMappings[" + i + "]";
            mappingEnd(id, mapping.source(), "mapping-input", loc + ".source");
            mappingEnd(id, mapping.target(), "mapping-output", loc + ".target");
            if (mapping.logic() != null) {
                for (int j = 0; j < list(mapping.logic().libraries()).size(); j++)
                    reference(id, "function-library", mapping.logic().libraries().get(j), loc + ".logic.libraries[" + j + "]");
            }
        }
    }

    private void mappingEnd(String id, ObjRef ref, String role, String locator) {
        if (ref == null) return;
        reference(id, role, ref, locator);
        port(ref, role, id, null, locator);
    }

    private void reference(String artifact, String kind, ObjRef ref, String locator) {
        var match = match(ref);
        dependencies.add(new Dependency(artifact, kind, ref, null, null, match.artifacts(), match.state(), export(locator)));
        if (!"resolved".equals(match.state())) issue(locator, kind + " " + match.state() + ": " + ref + "; candidates " + match.artifacts());
    }

    private void port(ObjRef ref, String role, String participant, String operation, String locator) {
        if (ref == null) return; // No response on an asynchronous operation is not a missing reference.
        var match = match(ref);
        var bound = match.identities().stream().map(Identity::contract).filter(Objects::nonNull).distinct().toList();
        if (!"resolved".equals(match.state()) || bound.isEmpty())
            issue(locator, "Port " + role + " participant=" + participant + " operation=" + operation + " reference=" + ref
                    + "; identity=" + match.state() + "; contract binding=" + (bound.isEmpty() ? "unknown" : "candidate") + "; candidates=" + bound);
        for (int i = 0; i < contracts.size(); i++) {
            var contract = contracts.get(i);
            if (!bound.contains(contract.id())) continue;
            var ports = new ArrayList<>(contract.ports());
            ports.add(new PortClaim(role, participant, operation, null, export(locator)));
            contracts.set(i, new Contract(contract.id(), contract.artifact(), contract.disposition(), contract.esr(),
                    contract.xmlName(), ports, contract.values(), contract.evidence()));
        }
    }

    private Match match(ObjRef ref) {
        if (ref == null) return new Match(List.of(), "unknown");
        var typed = identities.stream().filter(i -> i.ref() != null && Objects.equals(ref.type(), i.ref().type())).toList();
        if (present(ref.oid())) {
            var oid = typed.stream().filter(i -> ref.oid().equals(i.ref().oid())).toList();
            if (!oid.isEmpty()) return matched(oid, true);
        }
        boolean fullKey = !ref.key().isEmpty();
        var candidates = typed.stream().filter(i -> fullKey ? ref.key().equals(i.ref().key())
                : Objects.equals(ref.name(), i.ref().name()) && Objects.equals(ref.namespace(), i.ref().namespace()))
                .filter(i -> compatibleVersion(ref, i.ref())).toList();
        boolean exact = fullKey && candidates.stream().allMatch(i -> exactVersion(ref, i.ref()));
        return matched(candidates, exact);
    }

    private static Match matched(List<Identity> candidates, boolean exact) {
        return new Match(candidates, candidates.isEmpty() ? "missing" : candidates.size() > 1 ? "ambiguous" : exact ? "resolved" : "candidate");
    }

    private static boolean compatibleVersion(ObjRef ref, ObjRef target) {
        // A missing caption on one side does not contradict the same GUID.
        if (present(ref.swcGuid()) && present(target.swcGuid())) return ref.swcGuid().equals(target.swcGuid());
        return !present(ref.swc()) || !present(target.swc()) || ref.swc().equals(target.swc());
    }

    private static boolean exactVersion(ObjRef ref, ObjRef target) {
        if (present(ref.swcGuid()) && present(target.swcGuid())) return ref.swcGuid().equals(target.swcGuid());
        return present(ref.swc()) && ref.swc().equals(target.swc());
    }

    void imports() {
        // String comparison intentionally preserves case and percent encoding, unlike URI.equals.
        List<String> paths = new ArrayList<>();
        for (var resource : resources) paths.add(normalized(resource.resource().sourcePath(), resource.id()));
        for (int i = 0; i < resources.size(); i++) {
            var resource = resources.get(i);
            for (int j = 0; j < resource.resource().dependencies().size(); j++) {
                var edge = resource.resource().dependencies().get(j);
                String locator = resource.id() + ".dependencies[" + j + "]";
                String resolved = null;
                if (paths.get(i) != null && present(edge.location())) {
                    try {
                        URI location = URI.create(edge.location());
                        if (location.isOpaque()) throw new IllegalArgumentException("opaque location");
                        resolved = URI.create(resource.resource().sourcePath()).resolve(location).normalize().toString();
                    } catch (IllegalArgumentException e) { issue(locator, "Malformed/opaque import location=" + edge.location() + ": " + e.getMessage()); }
                }
                List<String> candidates = new ArrayList<>();
                if (resolved != null) for (int k = 0; k < paths.size(); k++)
                    if (resolved.equals(paths.get(k))) candidates.add(resources.get(k).id());
                String state = resolved == null ? "unknown" : candidates.isEmpty() ? "missing" : candidates.size() > 1 ? "ambiguous" : "candidate";
                var evidence = new ArrayList<>(resource.evidence());
                evidence.addAll(resourceEvidence(resource.evidence(), resource.resource().sourcePath(), ".dependencies[" + j + "]", edge.locator()));
                dependencies.add(new Dependency(resource.id(), edge.kind(), null, edge.location(), edge.namespace(), candidates, state, evidence));
                if (!"candidate".equals(state)) issue(locator, "Import " + state + " sourcePath=" + resource.resource().sourcePath()
                        + " location=" + edge.location() + " namespace=" + edge.namespace() + " candidates=" + candidates);
            }
        }
    }

    private String normalized(String path, String locator) {
        if (!present(path)) { issue(locator, "Missing sourcePath; import coverage unknown"); return null; }
        try {
            URI uri = URI.create(path);
            if (uri.isOpaque()) throw new IllegalArgumentException("opaque sourcePath");
            return uri.normalize().toString();
        } catch (IllegalArgumentException e) { issue(locator, "Malformed/opaque sourcePath=" + path + ": " + e.getMessage()); return null; }
    }
}
