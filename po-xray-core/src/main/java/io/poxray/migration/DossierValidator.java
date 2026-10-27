package io.poxray.migration;

import io.poxray.migration.DossierReader.Document;
import io.poxray.migration.DossierReport.Decision;
import io.poxray.migration.DossierReport.Dependency;
import io.poxray.migration.DossierReport.DerivedDocument;
import io.poxray.migration.DossierReport.Diagnostic;
import io.poxray.migration.DossierReport.Risk;
import io.poxray.migration.DossierReport.Scope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/** Validates scoped approvals and projects their history without modifying any input. */
public final class DossierValidator {
    private DossierValidator() {
    }

    public static DossierReport check(Path dossier) throws IOException {
        return check(dossier, null);
    }

    public static DossierReport check(Path dossier, Path baseline) throws IOException {
        return assess(dossier, baseline).dossier();
    }

    public static DossierAssessment assess(Path dossier) throws IOException {
        return assess(dossier, null);
    }

    public static DossierAssessment assess(Path dossier, Path baseline) throws IOException {
        Document document = DossierReader.read(dossier);
        Projection projection = new Projection();
        Path directory = dossier.toAbsolutePath().getParent();
        DossierReadiness readiness = new DossierReadiness(document, directory, projection);
        for (DossierReader.ReadResult result : document.results()) {
            // A later malformed occurrence invalidates the dossier, not earlier applied history.
            if (result.diagnostic() != null) {
                projection.diagnostics.add(result.diagnostic());
            } else {
                readiness.apply(result.event());
            }
        }
        String history = history(document, baseline, projection.diagnostics);
        projection.checkConflicts();
        projection.checkSnapshots(directory);
        readiness.checkBases();
        List<DerivedDocument> derived = projection.derivedDocuments(directory, readiness);
        DossierReport report = new DossierReport(document.formatVersion(), document.migrationId(), document.sha256(),
                projection.diagnostics.stream().noneMatch(d -> d.severity().equals("error")), history,
                projection.decisions.values().stream().map(DecisionState::report).toList(), projection.risks,
                derived, projection.nextAction, projection.diagnostics);
        return readiness.report(report);
    }

    private static String history(Document current, Path baseline, List<Diagnostic> diagnostics) {
        if (baseline == null) {
            diagnostics.add(new Diagnostic("warning", "HISTORY_NOT_CHECKED", null,
                    "No baseline supplied; append-only history has not been checked"));
            return "not-checked";
        }
        Document previous;
        try {
            previous = DossierReader.read(baseline);
        } catch (IOException e) {
            diagnostics.add(new Diagnostic("error", "INVALID_BASELINE", null, "Baseline cannot be read as dossier JSON"));
            return "invalid-baseline";
        }
        boolean upgrade = previous.formatVersion() >= 1 && previous.formatVersion() < current.formatVersion()
                && current.formatVersion() <= 3;
        if (!previous.diagnostics().isEmpty() || (!upgrade && previous.formatVersion() != current.formatVersion())
                || !previous.migrationId().equals(current.migrationId())) {
            diagnostics.add(new Diagnostic("error", "INVALID_BASELINE", null, "Baseline schema or identity is invalid"));
            return "invalid-baseline";
        }
        JsonNode oldEvents = previous.root().path("events");
        JsonNode newEvents = current.root().path("events");
        boolean prefix = newEvents.isArray() && oldEvents.size() <= newEvents.size();
        for (int i = 0; prefix && i < oldEvents.size(); i++) {
            prefix = oldEvents.get(i).equals(newEvents.get(i));
        }
        if (!prefix) {
            diagnostics.add(new Diagnostic("error", "HISTORY_CHANGED", null,
                    "Baseline events are not an unchanged prefix of the current ledger"));
            return "changed";
        }
        if (upgrade) {
            diagnostics.add(new Diagnostic("warning", "HISTORY_FORMAT_UPGRADED", null,
                    "Unchanged version " + previous.formatVersion() + " history explicitly upgraded to version " + current.formatVersion()));
        }
        return "verified";
    }

    static final class Projection {
        private final Map<String, DecisionState> decisions = new LinkedHashMap<>();
        private final Map<Revision, Scope> revisions = new LinkedHashMap<>();
        private final Map<String, Set<String>> replacements = new LinkedHashMap<>();
        private final Map<String, JsonNode> documents = new LinkedHashMap<>();
        private final List<Risk> risks = new ArrayList<>();
        final List<Diagnostic> diagnostics = new ArrayList<>();
        private String nextAction;

        void apply(JsonNode event) {
            String id = text(event, "id");
            switch (text(event, "type")) {
                case "propose" -> propose(event, id);
                case "approve" -> approve(event, id);
                case "reject", "defer", "approval-unresolved" -> pending(event, id);
                case "supersede" -> supersede(event, id);
                case "risk-accepted" -> {
                    List<String> sections = strings(event.get("sections"));
                    if (sections.stream().anyMatch(section -> revisions.keySet().stream()
                            .noneMatch(revision -> revision.sectionId().equals(section)))) {
                        error("INVALID_REFERENCE", id, "Risk refers to an unknown section");
                        return;
                    }
                    risks.add(new Risk(id, text(event, "text"), text(event, "source"), sections));
                }
                case "derived-document" -> {
                    for (Dependency dependency : dependencies(event)) {
                        DecisionState decision = decisions.get(dependency.decisionId());
                        if (decision == null || decision.proposed.stream()
                                .noneMatch(scope -> scope.sectionId().equals(dependency.sectionId()))) {
                            error("INVALID_REFERENCE", id, "Derived dependency refers to an unknown decision or section");
                            return;
                        }
                    }
                    documents.put(text(event, "documentId"), event);
                }
                case "next-action" -> nextAction = text(event, "text");
                default -> throw new IllegalStateException("Reader admitted an unknown event type");
            }
        }

        private void propose(JsonNode event, String id) {
            List<Scope> scopes = scopes(event);
            for (Scope scope : scopes) {
                Scope previous = revisions.get(new Revision(scope.sectionId(), scope.revision()));
                if (previous != null && !sameScope(previous, scope)) {
                    error("INVALID_SCOPE", id, "A section revision must keep the same snapshot path and digest");
                    return;
                }
            }
            scopes.forEach(scope -> revisions.putIfAbsent(new Revision(scope.sectionId(), scope.revision()), scope));
            decisions.put(id, new DecisionState(id, text(event, "text"), text(event, "source"), scopes));
        }

        private void approve(JsonNode event, String id) {
            DecisionState decision = reference(event, "decision", id);
            if (decision == null) {
                return;
            }
            List<Scope> scopes = scopes(event);
            // Preflight all scopes: a failed event cannot leave a partial approval behind.
            for (Scope scope : scopes) {
                if (decision.proposed.stream().noneMatch(proposed -> sameScope(proposed, scope))) {
                    error("INVALID_SCOPE", id, "Approval scope must exactly match a proposed section revision");
                    return;
                }
                if (decision.superseded.contains(scope.sectionId())) {
                    error("INVALID_TRANSITION", id, "A superseded scope cannot be approved again");
                    return;
                }
            }
            for (Scope scope : scopes) {
                decision.active.add(scope.sectionId());
                decision.approved.putIfAbsent(scope.sectionId(), id);
            }
            decision.approvals.add(id);
        }

        private void pending(JsonNode event, String id) {
            DecisionState decision = reference(event, "decision", id);
            if (decision == null) {
                return;
            }
            if (!decision.approved.isEmpty()) {
                error("INVALID_TRANSITION", id, "A previously approved decision cannot be globally rejected, deferred or unresolved");
                return;
            }
            decision.pending = switch (text(event, "type")) {
                case "reject" -> "rejected";
                case "defer" -> "deferred";
                default -> "approval-unresolved";
            };
        }

        private void supersede(JsonNode event, String id) {
            DecisionState successor = reference(event, "decision", id);
            DecisionState previous = reference(event, "replaces", id);
            if (successor == null || previous == null) {
                return;
            }
            if (reachable(successor.id, previous.id)) {
                error("SUPERSESSION_CYCLE", id, "Replacement would create a cycle or replace a decision with itself");
                return;
            }
            List<String> sections = strings(event.get("sections"));
            for (String section : sections) {
                if (!successor.active.contains(section) || !previous.active.contains(section)) {
                    error("INVALID_TRANSITION", id, "Replacement requires active approval on both decisions for every named section");
                    return;
                }
            }
            for (String section : sections) {
                previous.active.remove(section);
                previous.superseded.add(section);
            }
            replacements.computeIfAbsent(previous.id, ignored -> new LinkedHashSet<>()).add(successor.id);
        }

        private boolean reachable(String from, String to) {
            Set<String> visited = new HashSet<>();
            ArrayDeque<String> remaining = new ArrayDeque<>();
            remaining.add(from);
            while (!remaining.isEmpty()) {
                String next = remaining.removeFirst();
                if (next.equals(to)) {
                    return true;
                }
                if (visited.add(next)) {
                    remaining.addAll(replacements.getOrDefault(next, Set.of()));
                }
            }
            return false;
        }

        private DecisionState reference(JsonNode event, String field, String id) {
            DecisionState decision = decisions.get(text(event, field));
            if (decision == null) {
                error("INVALID_REFERENCE", id, "Decision reference must name an earlier valid proposal");
            }
            return decision;
        }

        void checkConflicts() {
            Map<String, String> owners = new LinkedHashMap<>();
            for (DecisionState decision : decisions.values()) {
                for (Scope scope : decision.proposed) {
                    if (decision.active.contains(scope.sectionId()) && owners.putIfAbsent(scope.sectionId(), decision.id) != null) {
                        error("APPROVAL_CONFLICT", decision.id, "More than one decision has active approval for section " + scope.sectionId());
                    }
                }
            }
        }

        void checkSnapshots(Path directory) {
            for (DecisionState decision : decisions.values()) {
                for (Scope scope : decision.proposed) {
                    String approval = decision.approved.get(scope.sectionId());
                    if (approval == null) {
                        continue;
                    }
                    Path path = directory.resolve(scope.path());
                    try {
                        if (!Files.isRegularFile(path)) {
                            error("SNAPSHOT_MISSING", approval, "Approved snapshot is not a readable regular file for section " + scope.sectionId());
                        } else if (!DossierReader.sha256(Files.readAllBytes(path)).equalsIgnoreCase(scope.sha256())) {
                            error("SNAPSHOT_CHANGED", approval, "Approved snapshot digest differs for section " + scope.sectionId());
                        }
                    } catch (IOException e) {
                        error("SNAPSHOT_MISSING", approval, "Approved snapshot cannot be read for section " + scope.sectionId());
                    }
                }
            }
        }

        List<DerivedDocument> derivedDocuments(Path directory, DossierReadiness readiness) {
            List<DerivedDocument> result = new ArrayList<>();
            for (JsonNode event : documents.values()) {
                List<Dependency> consumes = dependencies(event);
                String status = !Files.isRegularFile(directory.resolve(text(event, "path"))) ? "missing"
                        : consumes.stream().allMatch(d -> isCurrent(d) && readiness.basisCurrent(d)) ? "current" : "stale";
                boolean required = event.get("required").booleanValue();
                if (!status.equals("current")) {
                    diagnostics.add(new Diagnostic(required ? "error" : "warning",
                            status.equals("missing") ? "DERIVED_MISSING" : "DERIVED_STALE", text(event, "id"),
                            "Derived document " + text(event, "documentId") + " is " + status));
                }
                result.add(new DerivedDocument(text(event, "documentId"), text(event, "path"), status, required, consumes));
            }
            return result;
        }

        boolean isCurrent(Dependency dependency) {
            DecisionState decision = decisions.get(dependency.decisionId());
            return decision != null && decision.active.contains(dependency.sectionId()) && decision.proposed.stream()
                    .anyMatch(scope -> scope.sectionId().equals(dependency.sectionId())
                            && scope.revision().equals(dependency.revision()) && scope.sha256().equalsIgnoreCase(dependency.sha256()));
        }

        Scope proposed(Dependency dependency) {
            DecisionState decision = decisions.get(dependency.decisionId());
            return decision == null ? null : decision.proposed.stream().filter(scope ->
                    scope.sectionId().equals(dependency.sectionId()) && scope.revision().equals(dependency.revision())
                            && scope.sha256().equalsIgnoreCase(dependency.sha256())).findFirst().orElse(null);
        }

        String approval(Dependency dependency) {
            DecisionState decision = decisions.get(dependency.decisionId());
            return proposed(dependency) == null ? null : decision.approved.get(dependency.sectionId());
        }

        Risk risk(String id) { return risks.stream().filter(r -> r.id().equals(id)).findFirst().orElse(null); }

        private void error(String code, String id, String message) {
            diagnostics.add(new Diagnostic("error", code, id, message));
        }
    }

    private record Revision(String sectionId, String revision) {
    }

    private static final class DecisionState {
        private final String id;
        private final String text;
        private final String source;
        private final List<Scope> proposed;
        private final Set<String> active = new LinkedHashSet<>();
        private final Map<String, String> approved = new LinkedHashMap<>();
        private final Set<String> superseded = new LinkedHashSet<>();
        private final List<String> approvals = new ArrayList<>();
        private String pending = "proposed";

        DecisionState(String id, String text, String source, List<Scope> proposed) {
            this.id = id;
            this.text = text;
            this.source = source;
            this.proposed = proposed;
        }

        Decision report() {
            String status = !superseded.isEmpty() ? (active.isEmpty() ? "superseded" : "partially-superseded")
                    : active.isEmpty() ? pending : active.size() == proposed.size() ? "approved" : "partially-approved";
            return new Decision(id, status, text, source, proposed,
                    proposed.stream().filter(scope -> active.contains(scope.sectionId())).toList(), approvals);
        }
    }

    private static String text(JsonNode object, String field) {
        return object.get(field).stringValue();
    }

    private static List<String> strings(JsonNode array) {
        return array.valueStream().map(JsonNode::stringValue).toList();
    }

    private static List<Scope> scopes(JsonNode event) {
        return event.get("scope").valueStream().map(scope -> new Scope(text(scope, "sectionId"), text(scope, "revision"),
                text(scope, "path"), text(scope, "sha256"))).toList();
    }

    private static List<Dependency> dependencies(JsonNode event) {
        return event.get("consumes").valueStream().map(dependency -> new Dependency(text(dependency, "decisionId"),
                text(dependency, "sectionId"), text(dependency, "revision"), text(dependency, "sha256"))).toList();
    }

    private static boolean sameScope(Scope first, Scope second) {
        return first.sectionId().equals(second.sectionId()) && first.revision().equals(second.revision())
                && first.path().equals(second.path()) && first.sha256().equalsIgnoreCase(second.sha256());
    }

    public static String markdown(DossierReport report) {
        StringBuilder md = new StringBuilder("# Dossier current design — ").append(cell(report.migrationId())).append("\n\n");
        md.append(report.valid() ? "Valid ledger projection.\n" : "**INVALID — this view is not authoritative current design.**\n");
        md.append("\nLedger SHA-256: ").append(cell(report.ledgerSha256()))
                .append("\n\nHistory: ").append(cell(report.historyStatus())).append(".\n\n")
                .append("Questions, milestone readiness, runtime evidence and authenticity of approval sources are not checked. ")
                .append("Proposed scopes are not verified approvals. Derived document checks do not validate prose semantics.\n");
        md.append("\n## Decisions\n\n| Decision | Status | Text | Source | Proposed scopes (not approval) | Active approved scopes | Approval events |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (Decision decision : report.decisions()) {
            row(md, decision.id(), decision.status(), decision.text(), decision.source(), scopeText(decision.proposedScopes()),
                    scopeText(decision.activeScopes()), String.join(", ", decision.approvalEventIds()));
        }
        md.append("\n## Risks\n\nAccepted risks do not grant approval or resolve unknown facts.\n\n")
                .append("| Risk | Text | Source | Sections |\n| --- | --- | --- | --- |\n");
        for (Risk risk : report.risks()) {
            row(md, risk.id(), risk.text(), risk.source(), String.join(", ", risk.sections()));
        }
        md.append("\n## Derived documents\n\n| Document | Path | Status | Required | Consumes |\n| --- | --- | --- | --- | --- |\n");
        for (DerivedDocument document : report.derivedDocuments()) {
            String consumes = document.consumes().stream().map(dependency -> dependency.decisionId() + ": "
                    + dependency.sectionId() + " @ " + dependency.revision() + " [" + dependency.sha256() + "]")
                    .collect(Collectors.joining("; "));
            row(md, document.id(), document.path(), document.status(), Boolean.toString(document.required()), consumes);
        }
        md.append("\n## Next action\n\n").append(cell(report.nextAction() == null ? "Not recorded" : report.nextAction()))
                .append("\n\n## Diagnostics\n\n| Severity | Code | Event | Message |\n| --- | --- | --- | --- |\n");
        for (Diagnostic diagnostic : report.diagnostics()) {
            row(md, diagnostic.severity(), diagnostic.code(), diagnostic.eventId(), diagnostic.message());
        }
        return md.toString();
    }

    public static String markdown(DossierAssessment assessment) {
        StringBuilder md = new StringBuilder("# Dossier readiness — ").append(cell(assessment.dossier().migrationId()))
                .append("\n\nAssessment: ").append(cell(assessment.status())).append(".\n\n")
                .append("Read-only assessment of declared dependencies and evidence integrity. ")
                .append("It does not verify prose semantics, historical source authenticity or runtime behaviour, ")
                .append("and does not authorize deployment or tenant operations.\n")
                .append("\n## Questions\n\n| Question | Status | Text | Classification | Sections | Answer | Evidence | Deferral | Review at |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Question q : assessment.questions()) {
            row(md, q.id(), q.status(), q.text(), q.classification(), String.join(", ", q.sections()), q.answer(),
                    q.evidenceSource(), q.deferralReason(), q.reviewAt());
        }
        md.append("\n| Question | Owner | Expected evidence | Classification source | Needed by | Answer event |\n| --- | --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Question q : assessment.questions()) row(md, q.id(), q.owner(), q.expectedEvidence(), q.classificationSource(),
                q.dependencies().stream().map(b -> b.sectionId() + ": " + b.neededByMilestoneId()).collect(Collectors.joining("; ")), q.answerEventId());
        md.append("\n## Exceptions\n\nAssumptions and accepted risks do not answer factual questions. Their scope and milestone limits remain explicit.\n")
                .append("\n| Exception | Type/status | Question | Scope | Milestones | Verification or limitations |\n| --- | --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Assumption a : assessment.assumptions()) {
            row(md, a.id(), "assumption", a.questionId(), dependencyText(a.scope()), String.join(", ", a.milestones()), a.verifyAtMilestoneId());
        }
        for (DossierAssessment.Risk r : assessment.risks()) {
            row(md, r.detailEventId(), r.status(), String.join(", ", r.questions()), dependencyText(r.scope()),
                    String.join(", ", r.milestones()), r.limitations());
        }
        md.append("\n| Assumption | Text | Owner | Approval source |\n| --- | --- | --- | --- |\n");
        for (DossierAssessment.Assumption a : assessment.assumptions()) row(md, a.id(), a.text(), a.owner(), a.approvalSource());
        md.append("\n| Risk | Detail event | Owner | Approval source | Permitted triggers | Reopen event |\n| --- | --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Risk r : assessment.risks()) row(md, r.id(), r.detailEventId(), r.owner(), r.approvalSource(), String.join(", ", r.triggers()), r.reopenEventId());
        md.append("\n## Question coverage\n\n| Section | Inventory | Status | Questions | Owner | Evidence |\n| --- | --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Coverage c : assessment.coverages()) row(md, c.sectionId(), c.eventId(), c.status(), String.join(", ", c.questions()), c.owner(), c.evidenceSource());
        md.append("\n## Scope bases\n\n| Basis | Scope | Status | Answers | Legacy approval | Historical evidence |\n| --- | --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Basis b : assessment.scopeBases()) row(md, b.id(), dependencyText(List.of(b.scope())), b.status(),
                b.answers().stream().map(a -> a.questionId() + ": " + a.resolutionEventId()).collect(Collectors.joining(", ")),
                b.legacyApprovalEventId(), b.historicalEvidenceSource());
        md.append("\n## Milestones\n\n| Milestone | Kind | Status | Can work | Complete | Approved | Ready | Approval | Exceptions | Inputs |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Milestone m : assessment.milestones()) row(md, m.id(), m.kind(), m.status(), Boolean.toString(m.canWork()),
                Boolean.toString(m.complete()), Boolean.toString(m.approved()), Boolean.toString(m.ready()), m.approvalEventId(),
                String.join(", ", m.exceptions()), String.join(", ", m.approvalInputs()));
        md.append("\n| Milestone | Definition | Declared parts | Required scopes | Connections | Connection rationale | Prerequisites |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Milestone m : assessment.milestones()) row(md, m.id(), m.definitionEventId(), String.join(", ", m.sections()),
                dependencyText(m.requiredScopes()), dependencyText(m.connections()), m.connectionRationale(), String.join(", ", m.requires()));
        md.append("\n## Readiness blockers\n\n| Milestone | Code | Section | Question | Message |\n| --- | --- | --- | --- | --- |\n");
        for (DossierAssessment.Milestone m : assessment.milestones()) for (DossierAssessment.Blocker b : m.blockers()) row(md, m.id(), b.code(), b.sectionId(), b.questionId(), b.message());
        md.append("\n## Local evidence\n\nFile digests and independent attribution do not authenticate sources or prove tenant execution. "
                + "Mounted or mapped volume locality cannot be authenticated by portable Java.\n")
                .append("\n| Registration | State | Packet path | Packet SHA256 | Review path | Review SHA256 | Reasons |\n| --- | --- | --- | --- | --- | --- | --- |\n");
        for (DossierEvidenceReport.Packet p : assessment.evidence().packets()) {
            row(md, p.eventId(), p.state(), p.packetPath(), p.packetSha256(), p.reviewPath(), p.reviewSha256(), String.join("; ", p.reasons()));
            if (p.packet() != null) {
                var d = p.packet(); var v = d.versions();
                md.append("\n| Packet | Author | Source kind | Source locator | Source SHA256 | Observed at | Limitations | KB difference |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n");
                row(md, d.id(), d.author(), d.sourceKind(), d.sourceLocator(), d.sourceSha256(), d.observedAt(), d.limitations(), d.kbDifference());
                md.append("\n| Export label | Package release | Bundle version | Target version | Design-time version | Deployed version |\n| --- | --- | --- | --- | --- | --- |\n");
                row(md, v.exportLabel(), v.packageRelease(), v.bundleVersion(), v.targetVersion(), v.designTimeVersion(), v.deployedVersion());
                md.append("\n| Claim | Text | Scope | Limitations |\n| --- | --- | --- | --- |\n");
                for (var c : d.claims()) row(md, c.id(), c.text(), dependencyText(c.scope()), c.limitations());
            }
            if (p.review() != null) {
                var r = p.review();
                md.append("\n| Reviewer | Reviewed at | Packet ID | Packet SHA256 |\n| --- | --- | --- | --- |\n");
                row(md, r.reviewer(), r.reviewedAt(), r.packetId(), r.packetSha256());
                md.append("\n| Reviewed claim | Scope | Verdict | Limitations |\n| --- | --- | --- | --- |\n");
                for (var c : r.claims()) row(md, c.claimId(), dependencyText(c.scope()), c.verdict(), c.limitations());
            }
        }
        md.append("\n| Use | Scope | Evidence | Claim | Text | Replaces | State | Reasons |\n| --- | --- | --- | --- | --- | --- | --- | --- |\n");
        for (var u : assessment.evidence().uses()) row(md, u.eventId(), dependencyText(List.of(u.scope())), u.evidenceEventId(),
                u.claimId(), u.text(), u.replaces(), u.state(), String.join("; ", u.reasons()));
        // Preserve the P1 evidence/history rendering while removing its deliberately P1-only disclaimer.
        md.append('\n').append(markdown(assessment.dossier()).replace(
                "Questions, milestone readiness, runtime evidence and authenticity of approval sources are not checked. ", ""));
        return md.toString();
    }

    private static String dependencyText(List<Dependency> scopes) {
        return scopes.stream().map(d -> d.decisionId() + ": " + d.sectionId() + " @ " + d.revision() + " [" + d.sha256() + "]")
                .collect(Collectors.joining("; "));
    }

    private static String scopeText(List<Scope> scopes) {
        return scopes.stream().map(scope -> scope.sectionId() + " @ " + scope.revision() + ": " + scope.path()
                + " [" + scope.sha256() + "]").collect(Collectors.joining("; "));
    }

    private static void row(StringBuilder md, String... values) {
        md.append('|');
        for (String value : values) {
            md.append(' ').append(cell(value)).append(" |");
        }
        md.append('\n');
    }

    private static String cell(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\\", "\\\\").replace("`", "\\`").replace("|", "\\|").replace("[", "\\[").replace("]", "\\]")
                .replace("*", "\\*").replace("\r", " ").replace("\n", " ");
    }
}
