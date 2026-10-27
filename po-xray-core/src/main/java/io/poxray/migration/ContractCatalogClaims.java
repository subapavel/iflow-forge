package io.poxray.migration;

import io.poxray.model.ContractCatalogInput;
import io.poxray.model.ContractCatalogInput.*;
import io.poxray.model.ContractCatalogReport.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static io.poxray.migration.ContractCatalogBuilder.present;

/** Validation retains every supplied occurrence and its unmodified provenance. */
final class ContractCatalogClaims {
    static final Set<String> KINDS = Set.of("shared-message-mapping", "iflow-mapping", "script-collection",
            "local-script", "mapping-function", "external-definition", "xml-resource", "native-message-type", "native-data-type");
    static final Set<String> PORTS = Set.of("request", "response", "fault", "mapping-input", "mapping-output", "producer", "consumer");
    static final Set<String> VALUES = Set.of("input", "default", "api-response");
    private final ContractCatalogBuilder out;
    private ContractCatalogInput input;
    private List<String> artifactIds;

    ContractCatalogClaims(ContractCatalogBuilder out) { this.out = out; }

    void add(ContractCatalogInput supplied) {
        if (supplied == null) { out.issue("supplied", "Missing supplied claim coverage"); return; }
        input = supplied;
        artifactIds = ids(input.artifacts().stream().map(a -> a == null ? null : a.id()).toList(), "claim:", "artifacts");
        for (int i = 0; i < input.artifacts().size(); i++) {
            var claim = input.artifacts().get(i);
            String loc = "supplied.artifacts[" + i + "]";
            if (claim == null) { out.issue(loc, "Missing artifact claim"); continue; }
            boolean supported = claim.kind() != null && KINDS.contains(claim.kind()) && supports(claim.evidence());
            if (!supported) out.issue(loc, "Unsupported artifact classification or missing supporting evidence; original claim=" + claim);
            out.artifacts.add(new Artifact(artifactIds.get(i), supported ? claim.kind() : "unknown", null,
                    claim.owner(), "supplied", claim.evidence()));
        }
        var contractIds = ids(input.contracts().stream().map(c -> c == null ? null : c.id()).toList(), "claim-contract:", "contracts");
        for (int i = 0; i < input.contracts().size(); i++) {
            var claim = input.contracts().get(i);
            String loc = "supplied.contracts[" + i + "]";
            if (claim == null) { out.issue(loc, "Missing contract claim"); continue; }
            String artifact = owner(claim.artifact(), loc, claim.toString());
            String disposition = claim.disposition();
            boolean supported = supports(claim.evidence());
            if (!"unknown".equals(disposition) && (!("preserved-external".equals(disposition)
                    || "proposed-internal".equals(disposition)) || !supported)) {
                out.issue(loc, "Unsupported disposition or missing supporting evidence; original claim=" + claim);
                disposition = "unknown";
            }
            var name = claim.xmlName();
            if (name != null && (!supported || !present(name.localName()) || name.namespace() == null)) {
                out.issue(loc, "Unsupported QName or missing supporting evidence; original claim=" + claim);
                name = null;
            }
            for (int j = 0; j < claim.ports().size(); j++) {
                var port = claim.ports().get(j);
                String at = loc + ".ports[" + j + "]", original = "claim id=" + claim.id() + " port=" + port;
                if (port == null) { out.issue(at, "Missing port; " + original); continue; }
                if (port.role() == null || !PORTS.contains(port.role())) out.issue(at, "Unsupported port role; " + original);
                if (!evidenced(port.evidence())) out.issue(at, "Missing port evidence; " + original);
                step(port.step(), at + ".step", original);
            }
            for (int j = 0; j < claim.values().size(); j++) {
                var value = claim.values().get(j);
                String at = loc + ".values[" + j + "]", original = "claim id=" + claim.id() + " value=" + value;
                if (value == null) { out.issue(at, "Missing value; " + original); continue; }
                if (value.role() == null || !VALUES.contains(value.role())) out.issue(at, "Unsupported value role; " + original);
                if (!evidenced(value.evidence())) out.issue(at, "Missing value evidence; " + original);
                if (("default".equals(value.role()) || "api-response".equals(value.role())) && value.producer() == null)
                    out.issue(at, "Missing producer; " + original);
                step(value.producer(), at + ".producer", original);
                for (int k = 0; k < value.consumers().size(); k++) {
                    var consumer = value.consumers().get(k);
                    if (consumer == null) out.issue(at + ".consumers[" + k + "]", "Missing consumer StepRef; " + original);
                    else step(consumer, at + ".consumers[" + k + "]", original);
                }
            }
            out.contracts.add(new Contract(contractIds.get(i), artifact, disposition, claim.esr(), name,
                    claim.ports(), claim.values(), claim.evidence()));
        }
        var resourceIds = ids(input.resources().stream().map(r -> r == null ? null : r.id()).toList(), "supplied-resource:", "resources");
        for (int i = 0; i < input.resources().size(); i++) {
            var claim = input.resources().get(i);
            String loc = "supplied.resources[" + i + "]";
            if (claim == null) { out.issue(loc, "Missing resource binding"); continue; }
            var owner = owner(claim.ownerArtifact(), loc, claim.toString());
            boolean supported = supports(claim.evidence());
            if (!supported) out.issue(loc, "Missing resource classification evidence; original claim=" + claim);
            out.artifacts.add(new Artifact(resourceIds.get(i), supported ? "xml-resource" : "unknown", null,
                    owner, "supplied", claim.evidence()));
            out.resource(resourceIds.get(i), claim.resource(), claim.evidence());
        }
    }

    private List<String> ids(List<String> raw, String prefix, String domain) {
        var bases = new ArrayList<String>();
        for (int i = 0; i < raw.size(); i++) {
            String value = raw.get(i);
            long occurrences = raw.stream().filter(v -> java.util.Objects.equals(v, value)).count();
            bases.add(prefix + (!present(value) ? i : value + (occurrences > 1 ? ":" + i : "")));
            if (!present(value) || occurrences > 1)
                out.issue("supplied." + domain + "[" + i + "]", "Blank/duplicate " + domain + " original id=" + value + "; occurrence=" + i);
        }
        var reserved = new HashSet<>(bases);
        var assigned = new HashSet<String>();
        var result = new ArrayList<String>();
        for (int i = 0; i < bases.size(); i++) {
            String base = bases.get(i), candidate = base;
            if (bases.stream().filter(base::equals).count() > 1) {
                do { candidate += ":occurrence:" + i; } while (reserved.contains(candidate) || assigned.contains(candidate));
                out.issue("supplied." + domain + "[" + i + "]", "Output ID collision for original id=" + raw.get(i)
                        + "; base=" + base + "; retained as=" + candidate);
            }
            assigned.add(candidate);
            result.add(candidate);
        }
        return result;
    }

    private String owner(String raw, String locator, String original) {
        var candidates = new ArrayList<String>();
        if (present(raw)) {
            for (var artifact : out.artifacts)
                if ("source".equals(artifact.basis()) && raw.equals(artifact.id())) candidates.add(artifact.id());
            for (int i = 0; i < input.artifacts().size(); i++) {
                var artifact = input.artifacts().get(i);
                if (artifact != null && raw.equals(artifact.id())) candidates.add(artifactIds.get(i));
            }
        }
        if (candidates.size() == 1) return candidates.getFirst();
        out.issue(locator, "Artifact reference " + (candidates.isEmpty() ? "unresolved" : "ambiguous")
                + " original owner=" + raw + "; candidates=" + candidates + "; original claim=" + original);
        return null;
    }

    private void step(StepRef step, String locator, String original) {
        if (step == null) return;
        var processes = out.data == null ? List.<io.poxray.model.BpmDataReport.ProcessData>of()
                : out.data.processes().stream().filter(p -> step.process() != null && step.process().equals(p.key())).toList();
        if (processes.size() != 1 || processes.getFirst().steps().stream().filter(s -> s.step() == step.step()).count() != 1)
            out.issue(locator, "Unresolved/ambiguous StepRef=" + step + "; " + original);
    }

    private static boolean usable(Evidence evidence) {
        return evidence != null && present(evidence.kind()) && present(evidence.source())
                && present(evidence.locator()) && present(evidence.status());
    }
    private static boolean evidenced(List<Evidence> evidence) { return evidence.stream().anyMatch(ContractCatalogClaims::usable); }
    private static boolean supports(List<Evidence> evidence) {
        return evidence.stream().anyMatch(e -> usable(e) && ("artifact-declaration".equals(e.kind()) || "design-decision".equals(e.kind())));
    }
}
