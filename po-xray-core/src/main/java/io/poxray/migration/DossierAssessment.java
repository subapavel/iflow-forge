package io.poxray.migration;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Immutable, read-only readiness projection. It grants no tenant operation permission. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DossierAssessment(DossierReport dossier, String status, List<Question> questions,
        List<Assumption> assumptions, List<Risk> risks, List<Basis> scopeBases,
        List<Coverage> coverages, List<Milestone> milestones, DossierEvidenceReport evidence) {
    public DossierAssessment(DossierReport dossier, String status, List<Question> questions,
            List<Assumption> assumptions, List<Risk> risks, List<Basis> scopeBases,
            List<Coverage> coverages, List<Milestone> milestones) {
        this(dossier, status, questions, assumptions, risks, scopeBases, coverages, milestones, null);
    }
    public DossierAssessment {
        if (evidence == null) evidence = new DossierEvidenceReport(null, null);
        questions = List.copyOf(questions);
        assumptions = List.copyOf(assumptions);
        risks = List.copyOf(risks);
        scopeBases = List.copyOf(scopeBases);
        coverages = List.copyOf(coverages);
        milestones = List.copyOf(milestones);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Block(String sectionId, String neededByMilestoneId) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Question(String id, String text, String topic, String owner, String expectedEvidence,
            List<String> sections, String classification, String classificationSource, List<Block> dependencies,
            String status, String answerEventId, String answer, String evidenceSource, String deferralReason, String reviewAt) {
        public Question { sections = List.copyOf(sections); dependencies = List.copyOf(dependencies); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Assumption(String id, String questionId, String text, String owner, String approvalSource,
            List<DossierReport.Dependency> scope, List<String> milestones, String verifyAtMilestoneId) {
        public Assumption { scope = List.copyOf(scope); milestones = List.copyOf(milestones); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Risk(String id, String detailEventId, String owner, String approvalSource,
            List<DossierReport.Dependency> scope, String limitations, List<String> triggers,
            List<String> milestones, List<String> questions, String status, String reopenEventId) {
        public Risk {
            scope = List.copyOf(scope); triggers = List.copyOf(triggers);
            milestones = List.copyOf(milestones); questions = List.copyOf(questions);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record BasisInput(String questionId, String resolutionEventId) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Basis(String id, DossierReport.Dependency scope, List<BasisInput> answers, String status,
            String legacyApprovalEventId, String historicalEvidenceSource) {
        public Basis { answers = List.copyOf(answers); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Coverage(String sectionId, String eventId, List<String> questions, String owner,
            String evidenceSource, String status) {
        public Coverage { questions = List.copyOf(questions); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Blocker(String code, String sectionId, String questionId, String message) { }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Milestone(String id, String definitionEventId, String kind, List<String> sections,
            List<DossierReport.Dependency> requiredScopes, List<DossierReport.Dependency> connections,
            String connectionRationale, List<String> requires, String status, boolean canWork,
            boolean complete, boolean approved, boolean ready, List<Blocker> blockers,
            List<String> exceptions, List<String> approvalInputs, String approvalEventId) {
        public Milestone {
            sections = List.copyOf(sections); requiredScopes = List.copyOf(requiredScopes);
            connections = List.copyOf(connections); requires = List.copyOf(requires);
            blockers = List.copyOf(blockers); exceptions = List.copyOf(exceptions);
            approvalInputs = List.copyOf(approvalInputs);
        }
    }
}
