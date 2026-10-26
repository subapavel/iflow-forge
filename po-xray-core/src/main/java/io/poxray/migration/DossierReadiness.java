package io.poxray.migration;

import static io.poxray.migration.DossierReader.fields;
import static io.poxray.migration.DossierReader.string;

import io.poxray.migration.DossierAssessment.*;
import io.poxray.migration.DossierReader.InvalidInput;
import io.poxray.migration.DossierReport.Dependency;
import io.poxray.migration.DossierReport.Diagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/** Ordered v2 projection over the reader's retained tree and the existing P1 state. */
final class DossierReadiness {
    private static final Map<String, List<String>> FIELDS = Map.ofEntries(
            Map.entry("section-declare", List.of("sectionId", "text")),
            Map.entry("milestone-declare", List.of("milestoneId", "kind", "sections", "scope", "connections", "connectionRationale", "requires")),
            Map.entry("question-open", List.of("text", "topic", "owner", "expectedEvidence", "sections", "classification", "classificationSource", "classificationReason", "dependencies")),
            Map.entry("question-classify", List.of("question", "sections", "classification", "classificationSource", "classificationReason", "dependencies")),
            Map.entry("question-coverage", List.of("sections", "questions", "owner", "evidenceSource")),
            Map.entry("question-answer", List.of("question", "text", "evidenceSource", "replaces")),
            Map.entry("question-defer", List.of("question", "reason", "reviewAt")),
            Map.entry("assumption-accepted", List.of("question", "text", "owner", "approvalSource", "scope", "milestones", "verifyAtMilestoneId")),
            Map.entry("scope-basis", List.of("scope", "answers")),
            Map.entry("scope-basis-import", List.of("scope", "answers", "approval", "legacyPath", "legacySha256", "evidenceSource")),
            Map.entry("risk-detail", List.of("risk", "owner", "approvalSource", "scope", "limitations", "triggers", "milestones", "questions")),
            Map.entry("risk-reopen", List.of("risk", "triggerType", "evidenceSource", "text")),
            Map.entry("milestone-approve", List.of("milestone", "definition", "owner", "approvalSource", "inputs")));
    private static final Set<String> KINDS = Set.of("design", "final-plan", "specification", "implementation", "deployment", "verification");
    private final DossierReader.Document document;
    private final Path directory;
    private final DossierValidator.Projection p1;
    private final Set<String> sections = new LinkedHashSet<>();
    private final Map<String, QuestionState> questions = new LinkedHashMap<>();
    private final Map<String, JsonNode> definitions = new LinkedHashMap<>();
    private final Map<String, JsonNode> approvals = new LinkedHashMap<>();
    private final Map<String, Assumption> assumptions = new LinkedHashMap<>();
    private final Map<String, Risk> risks = new LinkedHashMap<>();
    private final Map<Dependency, Basis> bases = new LinkedHashMap<>();
    private final Map<String, Coverage> coverages = new LinkedHashMap<>();
    private final Map<String, JsonNode> applied = new LinkedHashMap<>();

    DossierReadiness(DossierReader.Document document, Path directory, DossierValidator.Projection p1) {
        this.document = document;
        this.directory = directory;
        this.p1 = p1;
    }

    static boolean isEvent(String type) { return FIELDS.containsKey(type); }

    static void validateEvent(JsonNode event) {
        String type = string(event, "type");
        Set<String> names = new HashSet<>(Set.of("id", "type", "at", "source"));
        names.addAll(FIELDS.get(type));
        fields(event, names);
        string(event, "id"); string(event, "source");
        DossierReader.timestamp(string(event, "at"));
        for (String name : FIELDS.get(type)) {
            JsonNode value = event.get(name);
            switch (name) {
                case "scope" -> {
                    if (type.startsWith("scope-basis")) dependency(value);
                    else dependencyArray(value, type.equals("milestone-declare"));
                }
                case "connections" -> dependencyArray(value, true);
                case "sections", "questions", "milestones", "triggers", "requires", "inputs" -> {
                    boolean empty = name.equals("requires") || name.equals("inputs")
                            || name.equals("questions") && type.equals("question-coverage")
                            || name.equals("sections") && type.startsWith("question-") && !type.equals("question-coverage");
                    ids(value, empty);
                }
                case "dependencies" -> {
                    array(value, true);
                    Set<Block> unique = new HashSet<>();
                    for (JsonNode block : value) {
                        fields(block, Set.of("sectionId", "neededByMilestoneId"));
                        require(unique.add(new Block(string(block, "sectionId"), string(block, "neededByMilestoneId"))),
                                "INVALID_DOCUMENT", "Question dependencies must be unique");
                    }
                }
                case "answers" -> {
                    array(value, false);
                    Set<String> unique = new HashSet<>();
                    for (JsonNode answer : value) {
                        fields(answer, Set.of("questionId", "resolutionEventId"));
                        require(unique.add(string(answer, "questionId")), "INVALID_DOCUMENT", "Basis questions must be unique");
                        string(answer, "resolutionEventId");
                    }
                }
                case "replaces" -> { if (!value.isNull()) string(event, name); }
                case "legacyPath" -> DossierReader.localPath(string(event, name));
                case "legacySha256" -> DossierReader.digest(string(event, name));
                default -> string(event, name);
            }
        }
        if (type.equals("milestone-declare")) require(KINDS.contains(string(event, "kind")), "INVALID_DOCUMENT", "Unknown milestone kind");
        if (type.equals("question-open") || type.equals("question-classify")) {
            String classification = string(event, "classification");
            require(Set.of("scoped", "nonblocking", "unclassified").contains(classification), "INVALID_DOCUMENT", "Unknown question classification");
            require(classification.equals("unclassified") || !event.get("sections").isEmpty(), "INVALID_SCOPE", "Classified questions need affected sections");
            require(classification.equals("scoped") != event.get("dependencies").isEmpty(), "INVALID_SCOPE", "Only scoped questions have nonempty dependencies");
        }
        if (type.equals("risk-detail")) require(strings(event.get("triggers")).stream().allMatch(Set.of("new-evidence", "requirement-change")::contains),
                "INVALID_DOCUMENT", "Unknown risk trigger");
    }

    void apply(JsonNode event) {
        String id = string(event, "id");
        String type = string(event, "type");
        if (!isEvent(type)) {
            int before = p1.diagnostics.size();
            p1.apply(event);
            if (p1.diagnostics.size() == before) {
                applied.put(id, event);
                if (type.equals("propose")) event.get("scope").forEach(scope -> sections.add(string(scope, "sectionId")));
            }
            return;
        }
        try {
            switch (type) {
                case "section-declare" -> {
                    require(!sections.contains(string(event, "sectionId")), "INVALID_REFERENCE", "Section has already been introduced");
                    sections.add(string(event, "sectionId"));
                }
                case "milestone-declare" -> declare(event);
                case "question-open", "question-classify" -> classify(event);
                case "question-coverage" -> coverage(event);
                case "question-answer" -> answer(event);
                case "question-defer" -> {
                    QuestionState q = question(string(event, "question"));
                    require(q.answer == null, "INVALID_TRANSITION", "An answered question cannot be deferred");
                    q.deferral = event;
                }
                case "assumption-accepted" -> assumption(event);
                case "scope-basis", "scope-basis-import" -> basis(event);
                case "risk-detail" -> risk(event);
                case "risk-reopen" -> reopen(event);
                case "milestone-approve" -> approve(event);
                default -> throw new IllegalStateException("Unknown readiness event");
            }
            applied.put(id, event);
        } catch (InvalidInput e) {
            p1.diagnostics.add(new Diagnostic("error", e.code, id, e.getMessage()));
        }
    }

    private void declare(JsonNode event) {
        String id = string(event, "milestoneId");
        List<String> parts = strings(event.get("sections"));
        knownSections(parts);
        List<Dependency> scopes = dependencies(event.get("scope"));
        List<Dependency> connections = dependencies(event.get("connections"));
        validateScopes(scopes); validateScopes(connections);
        require(scopes.stream().allMatch(d -> parts.contains(d.sectionId())), "INVALID_SCOPE", "Milestone scope must name declared parts");
        require(string(event, "kind").equals("design") || scopes.size() == parts.size(), "INVALID_SCOPE", "Every part needs one exact scope");
        Set<String> effective = effective(event);
        for (String predecessor : strings(event.get("requires"))) {
            require(!predecessor.equals(id) && !ancestors(predecessor).contains(id), "MILESTONE_CYCLE", "Milestone prerequisites must be acyclic");
            milestone(predecessor);
        }
        for (QuestionState q : questions.values()) {
            for (Block block : q.blocks()) require(!block.neededByMilestoneId().equals(id) || effective.contains(block.sectionId()),
                    "INVALID_SCOPE", "Milestone cannot remove a question dependency section");
        }
        for (Assumption a : assumptions.values()) {
            if (question(a.questionId()).answer == null && a.verifyAtMilestoneId().equals(id)) {
                require(!string(event, "kind").equals("design") && a.scope().stream().allMatch(d -> effective.contains(d.sectionId())),
                        "INVALID_SCOPE", "Milestone must retain outstanding assumption verification");
            }
        }
        definitions.put(id, event);
    }

    private void classify(JsonNode event) {
        boolean opening = string(event, "type").equals("question-open");
        QuestionState q = opening ? new QuestionState(event) : question(string(event, "question"));
        List<String> affected = strings(event.get("sections"));
        knownSections(affected);
        for (Block block : blocks(event)) {
            require(affected.contains(block.sectionId()) && effective(milestone(block.neededByMilestoneId())).contains(block.sectionId()),
                    "INVALID_SCOPE", "Question dependency must name an affected section covered by its milestone");
        }
        for (Assumption a : assumptions.values()) {
            if (a.questionId().equals(q.id()) && q.answer == null) require(a.scope().stream().allMatch(d -> affected.contains(d.sectionId())),
                    "INVALID_SCOPE", "Classification cannot discard outstanding assumption verification");
        }
        q.classification = event;
        if (opening) questions.put(q.id(), q);
    }

    private void coverage(JsonNode event) {
        List<String> affected = strings(event.get("sections"));
        knownSections(affected);
        Set<String> expected = new LinkedHashSet<>();
        affected.forEach(s -> expected.addAll(applicableQuestions(s)));
        require(expected.equals(new HashSet<>(strings(event.get("questions")))), "INVALID_REFERENCE", "Inventory must list exactly the current applicable questions");
        for (String section : affected) coverages.put(section, new Coverage(section, string(event, "id"), applicableQuestions(section),
                string(event, "owner"), string(event, "evidenceSource"), "current"));
    }

    private void answer(JsonNode event) {
        QuestionState q = question(string(event, "question"));
        String replaces = nullable(event, "replaces");
        require(Objects.equals(replaces, q.answer == null ? null : string(q.answer, "id")), "INVALID_TRANSITION", "Answer correction must replace the exact latest answer");
        q.answer = event;
        q.deferral = null;
    }

    private void assumption(JsonNode event) {
        QuestionState q = question(string(event, "question"));
        List<Dependency> scope = dependencies(event.get("scope"));
        validateScopes(scope);
        require(scope.stream().allMatch(d -> q.sections().contains(d.sectionId())), "INVALID_SCOPE", "Assumption must stay inside its question scope");
        List<String> permitted = strings(event.get("milestones"));
        for (String id : permitted) {
            JsonNode m = milestone(id);
            require(string(m, "kind").equals("design") && scope.stream().allMatch(d -> effective(m).contains(d.sectionId())),
                    "INVALID_SCOPE", "Assumption permission must cover named design milestones");
        }
        JsonNode verify = milestone(string(event, "verifyAtMilestoneId"));
        require(!string(verify, "kind").equals("design") && scope.stream().allMatch(d -> effective(verify).contains(d.sectionId())),
                "INVALID_SCOPE", "Assumption verification must cover the same sections outside design");
        Assumption a = new Assumption(string(event, "id"), q.id(), string(event, "text"), string(event, "owner"),
                string(event, "approvalSource"), scope, permitted, string(event, "verifyAtMilestoneId"));
        assumptions.put(a.id(), a);
    }

    private void basis(JsonNode event) {
        Dependency scope = dependency(event.get("scope"));
        validateScopes(List.of(scope));
        require(!bases.containsKey(scope), "INVALID_TRANSITION", "A scope basis can only be registered once");
        List<BasisInput> inputs = event.get("answers").valueStream().map(a -> new BasisInput(string(a, "questionId"), string(a, "resolutionEventId"))).toList();
        for (BasisInput input : inputs) require(resolutionCurrent(scope, input), "INVALID_REFERENCE", "Basis must consume the current answer or applicable assumption of an affected question");
        boolean imported = string(event, "type").equals("scope-basis-import");
        if (imported) legacyImport(event, scope, inputs);
        else require(p1.approval(scope) == null, "INVALID_TRANSITION", "Scope basis must precede its first approval");
        bases.put(scope, new Basis(string(event, "id"), scope, inputs, "current", imported ? string(event, "approval") : null,
                imported ? string(event, "evidenceSource") : null));
    }

    private void legacyImport(JsonNode event, Dependency scope, List<BasisInput> inputs) {
        try {
            DossierReader.Document legacy = DossierReader.read(directory.resolve(string(event, "legacyPath")));
            require(legacy.formatVersion() == 1 && legacy.diagnostics().isEmpty()
                    && legacy.migrationId().equals(document.migrationId())
                    && legacy.sha256().equalsIgnoreCase(string(event, "legacySha256")), "LEGACY_IMPORT_INVALID", "Legacy snapshot identity, format or digest is invalid");
            JsonNode old = legacy.root().get("events");
            JsonNode current = document.root().get("events");
            int importIndex = -1;
            for (int i = 0; i < current.size(); i++) if (current.get(i).equals(event)) { importIndex = i; break; }
            require(old.size() <= importIndex, "LEGACY_IMPORT_INVALID", "Legacy events must precede the import");
            for (int i = 0; i < old.size(); i++) require(old.get(i).equals(current.get(i)), "LEGACY_IMPORT_INVALID", "Legacy history is not an unchanged prefix");
            String approvalId = string(event, "approval");
            JsonNode approval = legacy.events().stream().filter(e -> string(e, "id").equals(approvalId)).findFirst().orElse(null);
            require(approval != null && string(approval, "type").equals("approve")
                    && string(approval, "decision").equals(scope.decisionId()) && p1.isCurrent(scope)
                    && applied.containsKey(approvalId), "LEGACY_IMPORT_INVALID", "Import must identify an active legacy scope approval");
            require(approval.get("scope").valueStream().anyMatch(s -> string(s, "sectionId").equals(scope.sectionId())
                    && string(s, "revision").equals(scope.revision()) && string(s, "sha256").equalsIgnoreCase(scope.sha256())),
                    "LEGACY_IMPORT_INVALID", "Legacy approval does not cover the exact scope");
            for (BasisInput input : inputs) {
                JsonNode answer = question(input.questionId()).answer;
                require(answer != null && answer.get("replaces").isNull() && string(answer, "id").equals(input.resolutionEventId()),
                        "LEGACY_IMPORT_INVALID", "Only historical first answers can be imported");
                require(!later(string(answer, "at"), string(approval, "at")), "LEGACY_IMPORT_INVALID", "Historical answer is later than approval");
            }
        } catch (IOException e) {
            throw new InvalidInput("LEGACY_IMPORT_INVALID", "Legacy snapshot cannot be read");
        }
    }

    private static boolean later(String answer, String approval) {
        if (answer.equals("unknown") || approval.equals("unknown")) return false;
        boolean answerTime = answer.contains("T") || answer.contains("t");
        boolean approvalTime = approval.contains("T") || approval.contains("t");
        if (answerTime != approvalTime) return false;
        return answerTime ? OffsetDateTime.parse(answer).isAfter(OffsetDateTime.parse(approval))
                : LocalDate.parse(answer).isAfter(LocalDate.parse(approval));
    }

    private void risk(JsonNode event) {
        String id = string(event, "risk");
        DossierReport.Risk accepted = p1.risk(id);
        require(accepted != null && !risks.containsKey(id), "INVALID_REFERENCE", "Risk detail needs an earlier risk and can be registered once");
        List<Dependency> scope = dependencies(event.get("scope"));
        validateScopes(scope);
        require(scope.stream().allMatch(d -> accepted.sections().contains(d.sectionId())), "INVALID_SCOPE", "Risk detail must stay inside accepted sections");
        List<String> namedQuestions = strings(event.get("questions"));
        for (String q : namedQuestions) require(scope.stream().allMatch(d -> question(q).sections().contains(d.sectionId())),
                "INVALID_SCOPE", "Risk questions must affect every waived scope");
        List<String> permitted = strings(event.get("milestones"));
        for (String m : permitted) require(scope.stream().allMatch(d -> effective(milestone(m)).contains(d.sectionId())),
                "INVALID_SCOPE", "Risk milestones must cover every waived scope");
        risks.put(id, new Risk(id, string(event, "id"), string(event, "owner"), string(event, "approvalSource"), scope,
                string(event, "limitations"), strings(event.get("triggers")), permitted, namedQuestions, "accepted", null));
    }

    private void reopen(JsonNode event) {
        String id = string(event, "risk");
        Risk r = risks.get(id);
        require(r != null && r.status().equals("accepted") && r.triggers().contains(string(event, "triggerType")),
                "INVALID_TRANSITION", "Risk reopening requires a permitted evidence trigger and accepted detail");
        risks.put(id, new Risk(r.id(), r.detailEventId(), r.owner(), r.approvalSource(), r.scope(), r.limitations(), r.triggers(),
                r.milestones(), r.questions(), "reopened", string(event, "id")));
    }

    private void approve(JsonNode event) {
        String id = string(event, "milestone");
        JsonNode definition = milestone(id);
        Milestone current = evaluate(id, new LinkedHashMap<>());
        require(current.complete() && string(definition, "id").equals(string(event, "definition"))
                        && new HashSet<>(current.approvalInputs()).equals(new HashSet<>(strings(event.get("inputs"))))
                        && p1.diagnostics.stream().noneMatch(d -> d.severity().equals("error")),
                "INVALID_TRANSITION", "Milestone approval must bind its complete current content and exact inputs");
        approvals.put(id, event);
    }

    boolean basisCurrent(Dependency scope) {
        Dependency key = new Dependency(scope.decisionId(), scope.sectionId(), scope.revision(), scope.sha256().toLowerCase(java.util.Locale.ROOT));
        Basis basis = bases.get(key);
        return basis == null || basis.answers().stream().allMatch(input -> resolutionCurrent(key, input));
    }

    private boolean resolutionCurrent(Dependency scope, BasisInput input) {
        QuestionState q = questions.get(input.questionId());
        if (q == null || !q.sections().contains(scope.sectionId())) return false;
        if (q.answer != null) return string(q.answer, "id").equals(input.resolutionEventId());
        Assumption a = assumptions.get(input.resolutionEventId());
        return a != null && a.questionId().equals(q.id()) && a.scope().contains(scope);
    }

    void checkBases() {
        for (Basis b : bases.values()) if (!basisCurrent(b.scope())) p1.diagnostics.add(new Diagnostic("warning", "ANSWER_BASIS_CHANGED", b.id(),
                "Consumed question resolution or scope has changed; the recorded basis is stale"));
    }

    DossierAssessment report(DossierReport report) {
        if (document.formatVersion() != 2) return new DossierAssessment(report, report.valid() ? "not-assessed" : "invalid",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        Map<String, Milestone> evaluated = new LinkedHashMap<>();
        definitions.keySet().forEach(id -> evaluate(id, evaluated));
        List<Milestone> milestones = definitions.keySet().stream().map(evaluated::get).map(m -> report.valid() ? m : invalid(m)).toList();
        return new DossierAssessment(report, report.valid() ? "assessed" : "invalid", questions.values().stream().map(QuestionState::report).toList(),
                List.copyOf(assumptions.values()), List.copyOf(risks.values()), bases.values().stream().map(b -> new Basis(b.id(), b.scope(), b.answers(),
                        basisCurrent(b.scope()) ? "current" : "stale", b.legacyApprovalEventId(), b.historicalEvidenceSource())).toList(),
                coverages.values().stream().map(c -> new Coverage(c.sectionId(), c.eventId(), c.questions(), c.owner(), c.evidenceSource(),
                        coverageCurrent(c) ? "current" : "stale")).toList(), milestones);
    }

    private static Milestone invalid(Milestone m) {
        List<Blocker> blockers = new ArrayList<>(m.blockers());
        blockers.add(new Blocker("INVALID_DOSSIER", null, null, "Invalid dossier cannot affirm readiness"));
        return new Milestone(m.id(), m.definitionEventId(), m.kind(), m.sections(), m.requiredScopes(), m.connections(), m.connectionRationale(),
                m.requires(), "blocked", false, false, false, false, blockers, m.exceptions(), m.approvalInputs(), m.approvalEventId());
    }

    private Milestone evaluate(String id, Map<String, Milestone> cache) {
        if (cache.containsKey(id)) return cache.get(id);
        JsonNode definition = milestone(id);
        String kind = string(definition, "kind");
        List<String> parts = strings(definition.get("sections"));
        List<Dependency> scopes = dependencies(definition.get("scope"));
        List<Dependency> connections = dependencies(definition.get("connections"));
        List<Dependency> consumed = new ArrayList<>(scopes); consumed.addAll(connections);
        List<String> requires = strings(definition.get("requires"));
        Set<String> effective = effective(definition);
        Set<String> ancestors = ancestors(id);
        LinkedHashSet<String> inputs = new LinkedHashSet<>();
        LinkedHashSet<String> exceptions = new LinkedHashSet<>();
        List<Blocker> blockers = new ArrayList<>();
        inputs.add(string(definition, "id"));
        boolean dependenciesClear = true;
        boolean assessed = true;
        for (String predecessor : requires) {
            Milestone m = evaluate(predecessor, cache);
            inputs.addAll(m.approvalInputs());
            if (m.approved()) inputs.add(m.approvalEventId());
            if (!m.ready()) {
                dependenciesClear = false;
                add(blockers, "PREREQUISITE_NOT_READY", null, null, "Prerequisite " + predecessor + " is not ready");
            }
            if (m.status().equals("not-assessed")) assessed = false;
        }
        for (String section : effective) {
            Coverage coverage = coverages.get(section);
            if (coverage == null || !coverageCurrent(coverage)) {
                dependenciesClear = false; assessed = false;
                add(blockers, coverage == null ? "QUESTION_COVERAGE_MISSING" : "QUESTION_COVERAGE_STALE", section, null,
                        "An explicit current question inventory is required");
            } else inputs.add(coverage.eventId());
        }
        for (QuestionState q : questions.values()) {
            Set<String> relevant = new LinkedHashSet<>();
            for (String section : effective) if (q.affects(section)) relevant.add(section);
            for (Block block : q.blocks()) if (block.neededByMilestoneId().equals(id) || ancestors.contains(block.neededByMilestoneId())) relevant.add(block.sectionId());
            for (Assumption a : assumptions.values()) if (a.questionId().equals(q.id()) && (a.verifyAtMilestoneId().equals(id) || ancestors.contains(a.verifyAtMilestoneId()))) {
                a.scope().forEach(d -> relevant.add(d.sectionId()));
            }
            // Unclassified questions from prerequisites remain visible even across section boundaries.
            if (q.classification().equals("unclassified")) for (String ancestor : ancestors) {
                effective(milestone(ancestor)).stream().filter(q::affects).forEach(relevant::add);
            }
            if (relevant.isEmpty()) continue;
            inputs.add(string(q.classification, "id"));
            if (q.answer != null) inputs.add(string(q.answer, "id"));
            for (String section : relevant) {
                if (q.classification().equals("unclassified")) {
                    dependenciesClear = false; assessed = false;
                    add(blockers, "UNCLASSIFIED_QUESTION", section, q.id(), "Question dependency is unclassified");
                    continue;
                }
                boolean needed = q.blocks().stream().anyMatch(b -> b.sectionId().equals(section)
                        && (b.neededByMilestoneId().equals(id) || ancestors.contains(b.neededByMilestoneId())));
                boolean verificationDebt = assumptions.values().stream().anyMatch(a -> a.questionId().equals(q.id())
                        && a.scope().stream().anyMatch(d -> d.sectionId().equals(section))
                        && (a.verifyAtMilestoneId().equals(id) || ancestors.contains(a.verifyAtMilestoneId())));
                if (q.answer != null || !needed && !verificationDebt) continue;
                String exception = exception(q.id(), section, id, kind, consumed, verificationDebt);
                if (exception == null) {
                    dependenciesClear = false;
                    add(blockers, "QUESTION_OPEN", section, q.id(), "Question remains " + (q.deferral == null ? "open" : "deferred"));
                } else { exceptions.add(exception); inputs.add(exception); }
            }
        }
        boolean content = scopes.size() == parts.size();
        for (Dependency scope : consumed) {
            String approval = p1.approval(scope);
            if (approval != null) inputs.add(approval);
            if (!p1.isCurrent(scope) || !snapshotCurrent(scope)) {
                content = false;
                add(blockers, connections.contains(scope) ? "CONNECTION_NOT_APPROVED" : "SCOPE_NOT_APPROVED", scope.sectionId(), null,
                        "Exact active approval and intact snapshot are required");
            }
            Basis basis = bases.get(scope);
            if (basis != null) inputs.add(basis.id());
            boolean current = basisCurrent(scope);
            for (QuestionState q : questions.values()) if (q.sections().contains(scope.sectionId())) {
                String resolution = q.answer == null ? exception(q.id(), scope.sectionId(), id, kind, consumed, false) : string(q.answer, "id");
                // Risk detail excuses a dependency; it is not an answer basis.
                if (resolution != null && (q.answer != null || assumptions.containsKey(resolution))) {
                    if (basis == null || !basis.answers().contains(new BasisInput(q.id(), resolution))) current = false;
                }
            }
            if (!current) {
                content = false;
                add(blockers, "STALE_BASIS", scope.sectionId(), null, "Content needs a current basis for every consumed resolution");
            }
        }
        boolean complete = dependenciesClear && content;
        boolean canWork = dependenciesClear && (kind.equals("design") || content);
        JsonNode approval = approvals.get(id);
        boolean approved = complete && approval != null && string(approval, "definition").equals(string(definition, "id"))
                && new HashSet<>(strings(approval.get("inputs"))).equals(inputs);
        if (approval != null && !approved) add(blockers, "FINAL_APPROVAL_REQUIRED", null, null, "Recorded milestone approval no longer binds current inputs");
        boolean ready = switch (kind) {
            case "design" -> canWork;
            case "verification" -> complete;
            case "final-plan" -> complete && approved;
            default -> complete && ancestors.stream().map(a -> evaluate(a, cache)).anyMatch(m -> m.kind().equals("final-plan") && m.ready()
                    && covers(m, consumed));
        };
        if (kind.equals("final-plan") && !approved && approval == null) add(blockers, "FINAL_APPROVAL_REQUIRED", null, null, "Explicit whole milestone approval is required");
        if (Set.of("specification", "implementation", "deployment").contains(kind) && !ancestors.stream().map(a -> evaluate(a, cache))
                .anyMatch(m -> m.kind().equals("final-plan") && m.ready() && covers(m, consumed))) {
            add(blockers, "FINAL_PLAN_REQUIRED", null, null, "A ready final-plan ancestor must cover all exact parts and connections");
        }
        Milestone result = new Milestone(id, string(definition, "id"), kind, parts, scopes, connections, string(definition, "connectionRationale"),
                requires, !assessed ? "not-assessed" : ready ? "ready" : "blocked", canWork, complete, approved, ready,
                blockers, List.copyOf(exceptions), List.copyOf(inputs), approval == null ? null : string(approval, "id"));
        cache.put(id, result);
        return result;
    }

    private String exception(String question, String section, String milestone, String kind, List<Dependency> consumed, boolean verificationDebt) {
        if (!verificationDebt && kind.equals("design")) for (Assumption a : assumptions.values()) {
            if (a.questionId().equals(question) && a.milestones().contains(milestone)
                    && a.scope().stream().anyMatch(d -> d.sectionId().equals(section) && consumed.contains(d))) return a.id();
        }
        for (Risk r : risks.values()) if (r.status().equals("accepted") && r.questions().contains(question) && r.milestones().contains(milestone)
                && r.scope().stream().anyMatch(d -> d.sectionId().equals(section) && consumed.contains(d))) return r.detailEventId();
        return null;
    }

    private static boolean covers(Milestone plan, List<Dependency> consumed) {
        Set<Dependency> all = new HashSet<>(plan.requiredScopes()); all.addAll(plan.connections());
        return all.containsAll(consumed);
    }

    private boolean snapshotCurrent(Dependency dependency) {
        DossierReport.Scope scope = p1.proposed(dependency);
        if (scope == null) return false;
        try {
            Path path = directory.resolve(scope.path());
            return Files.isRegularFile(path) && DossierReader.sha256(Files.readAllBytes(path)).equalsIgnoreCase(scope.sha256());
        } catch (IOException e) { return false; }
    }

    private boolean coverageCurrent(Coverage c) { return new HashSet<>(c.questions()).equals(new HashSet<>(applicableQuestions(c.sectionId()))); }
    private List<String> applicableQuestions(String section) { return questions.values().stream().filter(q -> q.affects(section)).map(QuestionState::id).toList(); }
    private void knownSections(List<String> names) { require(sections.containsAll(names), "INVALID_REFERENCE", "Unknown section reference"); }
    private void validateScopes(List<Dependency> scopes) { for (Dependency d : scopes) require(p1.proposed(d) != null, "INVALID_SCOPE", "Scope must exactly match an earlier valid proposal"); }
    private QuestionState question(String id) { QuestionState q = questions.get(id); require(q != null, "INVALID_REFERENCE", "Unknown question reference"); return q; }
    private JsonNode milestone(String id) { JsonNode m = definitions.get(id); require(m != null, "INVALID_REFERENCE", "Unknown milestone reference"); return m; }

    private Set<String> ancestors(String id) {
        Set<String> result = new LinkedHashSet<>();
        collectAncestors(id, result);
        return result;
    }
    private void collectAncestors(String id, Set<String> result) {
        JsonNode m = definitions.get(id);
        if (m == null) return;
        for (String parent : strings(m.get("requires"))) if (result.add(parent)) collectAncestors(parent, result);
    }

    private static Set<String> effective(JsonNode definition) {
        Set<String> result = new LinkedHashSet<>(strings(definition.get("sections")));
        dependencies(definition.get("connections")).forEach(d -> result.add(d.sectionId()));
        return result;
    }
    private static void add(List<Blocker> blockers, String code, String section, String question, String message) {
        Blocker blocker = new Blocker(code, section, question, message);
        if (!blockers.contains(blocker)) blockers.add(blocker);
    }
    private static List<Block> blocks(JsonNode e) { return e.get("dependencies").valueStream().map(b -> new Block(string(b, "sectionId"), string(b, "neededByMilestoneId"))).toList(); }
    private static List<String> strings(JsonNode array) { return array.valueStream().map(JsonNode::stringValue).toList(); }
    private static String nullable(JsonNode e, String field) { return e.get(field).isNull() ? null : string(e, field); }
    private static List<Dependency> dependencies(JsonNode array) { return array.valueStream().map(DossierReadiness::dependency).toList(); }
    private static Dependency dependency(JsonNode node) {
        fields(node, Set.of("decisionId", "sectionId", "revision", "sha256"));
        String digest = string(node, "sha256"); DossierReader.digest(digest);
        return new Dependency(string(node, "decisionId"), string(node, "sectionId"), string(node, "revision"), digest.toLowerCase(java.util.Locale.ROOT));
    }
    private static void dependencyArray(JsonNode node, boolean empty) {
        array(node, empty);
        Set<String> sections = new HashSet<>();
        for (JsonNode scope : node) require(sections.add(dependency(scope).sectionId()), "INVALID_SCOPE", "Scope sections must be unique");
    }
    private static void ids(JsonNode node, boolean empty) {
        array(node, empty);
        Set<String> values = new HashSet<>();
        for (JsonNode value : node) require(value.isString() && !value.stringValue().isBlank() && values.add(value.stringValue()),
                "INVALID_DOCUMENT", "ID arrays require unique nonblank strings");
    }
    private static void array(JsonNode node, boolean empty) { require(node.isArray() && (empty || !node.isEmpty()), "INVALID_DOCUMENT", "Expected an array with the required cardinality"); }
    private static void require(boolean condition, String code, String message) { if (!condition) throw new InvalidInput(code, message); }

    private static final class QuestionState {
        private final JsonNode opened;
        private JsonNode classification;
        private JsonNode answer;
        private JsonNode deferral;
        QuestionState(JsonNode opened) { this.opened = opened; this.classification = opened; }
        String id() { return string(opened, "id"); }
        String classification() { return string(classification, "classification"); }
        List<String> sections() { return strings(classification.get("sections")); }
        List<Block> blocks() { return DossierReadiness.blocks(classification); }
        boolean affects(String section) { return sections().contains(section) || sections().isEmpty() && classification().equals("unclassified"); }
        Question report() {
            return new Question(id(), string(opened, "text"), string(opened, "topic"), string(opened, "owner"), string(opened, "expectedEvidence"),
                    sections(), classification(), string(classification, "classificationSource"), blocks(), answer != null ? "answered" : deferral != null ? "deferred" : "open",
                    answer == null ? null : string(answer, "id"), answer == null ? null : string(answer, "text"), answer == null ? null : string(answer, "evidenceSource"),
                    deferral == null ? null : string(deferral, "reason"), deferral == null ? null : string(deferral, "reviewAt"));
        }
    }
}
