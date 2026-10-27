package io.poxray.migration;

import static io.poxray.migration.DossierValidatorTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.json.Json;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/** Synthetic append-only histories exercise the public readiness boundary. */
class DossierReadinessTest {
    @TempDir Path dir;

    @Test
    void deferredQuestionBlocksOnlyItsNamedMilestoneAndDependants() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(section("S-b", "b"));
        f.approve(a);
        f.add(milestone("DA", "design", List.of("a"), List.of(), List.of(), List.of()),
                milestone("IA", "implementation", List.of("a"), List.of(a), List.of(), List.of("DA")),
                milestone("VA", "verification", List.of("a"), List.of(a), List.of(), List.of("IA")),
                milestone("DB", "design", List.of("b"), List.of(), List.of(), List.of()),
                question("Q-a", List.of("a"), "scoped", block("a", "IA")),
                question("Q-b", List.of("b"), "nonblocking").replace("\"naming\"", "\"blocking\""),
                e("DF-a", "question-defer", "question", "Q-a", "reason", "Await laboratory result", "reviewAt", "2001-01-01"),
                coverage("C", List.of("a", "b"), "Q-a", "Q-b"));

        DossierAssessment report = f.assess();

        assertThat(report.dossier().valid()).isTrue();
        assertThat(report.questions()).filteredOn(q -> q.id().equals("Q-a")).singleElement().satisfies(q -> {
            assertThat(q.topic()).isEqualTo("naming");
            assertThat(q.status()).isEqualTo("deferred");
            assertThat(q.deferralReason()).isEqualTo("Await laboratory result");
            assertThat(q.reviewAt()).isEqualTo("2001-01-01");
            assertThat(q.dependencies()).containsExactly(new DossierAssessment.Block("a", "IA"));
        });
        assertThat(ms(report, "DA").canWork()).isTrue();
        assertThat(ms(report, "DA").complete()).isFalse();
        assertThat(ms(report, "DB").ready()).isTrue();
        assertThat(ms(report, "DB").blockers()).isEmpty();
        blocked(ms(report, "IA"), "QUESTION_OPEN", "a", "Q-a");
        blocked(ms(report, "VA"), "QUESTION_OPEN", "a", "Q-a");
        assertThat(report.dossier().diagnostics()).noneMatch(d -> d.severity().equals("error"));
    }

    @Test
    void deferringAnAnsweredQuestionIsRejectedWithoutChangingItsAnswer() throws IOException {
        Fixture f = designFixture(dir, "a");
        f.add(question("Q", List.of("a"), "scoped", block("a", "D-a")), answer("AN", "Q", "Measured", null),
                coverage("C", List.of("a"), "Q"),
                e("DF", "question-defer", "question", "Q", "reason", "Later", "reviewAt", "review-session"));
        DossierAssessment report = f.assess();
        invalid(report);
        assertThat(report.questions().getFirst().status()).isEqualTo("answered");
        assertThat(report.questions().getFirst().answerEventId()).isEqualTo("AN");
        assertThat(report.questions().getFirst().deferralReason()).isNull();
    }

    @Test
    void acceptedAssumptionIsExactDesignPermissionAndRetainsVerificationDebt() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        Part b = f.part("b");
        f.add(milestone("DA", "design", List.of("a"), List.of(a), List.of(), List.of()),
                milestone("DAB", "design", List.of("a", "b"), List.of(a, b), List.of(), List.of()),
                milestone("OTHER", "design", List.of("a"), List.of(a), List.of(), List.of()),
                milestone("VERIFY", "verification", List.of("a", "b"), List.of(a, b), List.of(), List.of()),
                milestone("AFTER", "verification", List.of("a"), List.of(a), List.of(), List.of("VERIFY")),
                question("Q", List.of("a", "b"), "scoped", block("a", "DA"), block("a", "DAB"),
                        block("b", "DAB"), block("a", "OTHER")),
                coverage("C", List.of("a", "b"), "Q"),
                assumption("AS", "Q", List.of(a), List.of("DA", "DAB"), "VERIFY"));
        DossierAssessment report = f.assess();
        assertThat(report.dossier().valid()).isTrue();
        assertThat(report.questions().getFirst().status()).isEqualTo("open");
        assertThat(report.questions().getFirst().answerEventId()).isNull();
        assertThat(ms(report, "DA").ready()).isTrue();
        assertThat(ms(report, "DA").exceptions()).containsExactly("AS");
        assertThat(ms(report, "DAB").blockers()).noneMatch(bk -> "QUESTION_OPEN".equals(bk.code()) && "a".equals(bk.sectionId()));
        blocked(ms(report, "DAB"), "QUESTION_OPEN", "b", "Q");
        blocked(ms(report, "OTHER"), "QUESTION_OPEN", "a", "Q");
        blocked(ms(report, "VERIFY"), "QUESTION_OPEN", "a", "Q");
        blocked(ms(report, "AFTER"), "QUESTION_OPEN", "a", "Q");
    }

    @Test
    void bareRiskCannotWaiveQuestionAndOnlyAnAllowedEvidenceTriggerReopensDetail() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(milestone("DA", "design", List.of("a"), List.of(a), List.of(), List.of()),
                question("Q", List.of("a"), "scoped", block("a", "DA")), coverage("C", List.of("a"), "Q"),
                e("R", "risk-accepted", "text", "Bounded unknown", "sections", List.of("a")));
        blocked(ms(f.assess(), "DA"), "QUESTION_OPEN", "a", "Q");
        assertThat(f.assess().risks()).isEmpty();
        f.add(riskDetail("RD", "R", List.of(a), List.of("DA"), List.of("Q")));
        DossierAssessment accepted = f.assess();
        assertThat(accepted.dossier().valid()).isTrue();
        assertThat(ms(accepted, "DA").ready()).isTrue();
        assertThat(ms(accepted, "DA").exceptions()).containsExactly("RD");
        assertThat(accepted.questions().getFirst().status()).isEqualTo("open");
        f.add(e("BAD", "risk-reopen", "risk", "R", "triggerType", "phase-change", "evidenceSource", "review:phase", "text", "Next phase"));
        DossierAssessment bad = f.assess();
        invalid(bad);
        assertThat(bad.risks().getFirst().status()).isEqualTo("accepted");
        f.events.removeLast();
        f.add(e("RR", "risk-reopen", "risk", "R", "triggerType", "new-evidence", "evidenceSource", "lab:changed", "text", "Different observed result"));
        DossierAssessment reopened = f.assess();
        assertThat(reopened.dossier().valid()).isTrue();
        assertThat(reopened.risks().getFirst().status()).isEqualTo("reopened");
        assertThat(reopened.risks().getFirst().reopenEventId()).isEqualTo("RR");
        assertThat(ms(reopened, "DA").exceptions()).isEmpty();
        blocked(ms(reopened, "DA"), "QUESTION_OPEN", "a", "Q");
        f.add(e("RR2", "risk-reopen", "risk", "R", "triggerType", "new-evidence", "evidenceSource", "lab:again", "text", "Repeat"));
        invalid(f.assess());
        assertThat(f.assess().risks().getFirst().reopenEventId()).isEqualTo("RR");
    }

    @Test
    void unknownQuestionScopeBlocksAllUntilExplicitlyClassified() throws IOException {
        Fixture f = designFixture(dir, "a", "b");
        f.add(question("Q", List.of(), "unclassified"), coverage("C", List.of("a", "b"), "Q"));
        DossierAssessment unknown = f.assess();
        assertThat(unknown.dossier().valid()).isTrue();
        assertThat(unknown.milestones()).allSatisfy(m -> {
            assertThat(m.status()).isEqualTo("not-assessed");
            assertThat(m.ready()).isFalse();
        });
        f.add(classify("QC", "Q", List.of("a"), "unclassified"), coverage("C2", List.of("a", "b"), "Q"));
        DossierAssessment scopedUnknown = f.assess();
        blocked(ms(scopedUnknown, "D-a"), "UNCLASSIFIED_QUESTION", "a", "Q");
        assertThat(ms(scopedUnknown, "D-b").ready()).isTrue();
        f.add(classify("QC2", "Q", List.of("a"), "nonblocking"));
        DossierAssessment classified = f.assess();
        assertThat(classified.dossier().valid()).isTrue();
        assertThat(ms(classified, "D-a").ready()).isTrue();
        assertThat(classified.questions().getFirst().status()).isEqualTo("open");
        assertThat(ms(classified, "D-a").approvalInputs()).contains("QC2").doesNotContain("Q", "QC");
    }

    @Test
    void explicitCoverageHasPerSectionContentsAndOnlyRelevantChangesMakeItStale() throws IOException {
        Fixture f = designFixture(dir, "a", "b");
        DossierAssessment missing = f.assess();
        assertThat(missing.milestones()).allSatisfy(m -> {
            assertThat(m.status()).isEqualTo("not-assessed");
            assertThat(m.blockers()).extracting(DossierAssessment.Blocker::code).contains("QUESTION_COVERAGE_MISSING");
        });
        f.add(coverage("C0", List.of("a", "b")));
        assertThat(f.assess().milestones()).allSatisfy(m -> assertThat(m.ready()).isTrue());
        f.add(question("Q-a", List.of("a"), "nonblocking"));
        DossierAssessment stale = f.assess();
        assertThat(ms(stale, "D-a").status()).isEqualTo("not-assessed");
        assertThat(ms(stale, "D-a").blockers()).extracting(DossierAssessment.Blocker::code).contains("QUESTION_COVERAGE_STALE");
        assertThat(ms(stale, "D-b").ready()).isTrue();
        f.add(coverage("C1", List.of("a", "b"), "Q-a"));
        DossierAssessment refreshed = f.assess();
        assertThat(refreshed.coverages()).extracting(DossierAssessment.Coverage::sectionId).containsExactly("a", "b");
        assertThat(refreshed.coverages().get(0).questions()).containsExactly("Q-a");
        assertThat(refreshed.coverages().get(1).questions()).isEmpty();
        assertThat(refreshed.coverages()).allSatisfy(c -> assertThat(c.status()).isEqualTo("current"));
        f.add(question("Q-a2", List.of("a"), "nonblocking"));
        assertThat(ms(f.assess(), "D-b").ready()).isTrue();
    }

    @Test
    void connectionSectionNeedsItsOwnCoverageAndResolutionBasis() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        Part link = f.part("link");
        f.approve(a);
        f.add(milestone("PLAN", "final-plan", List.of("a"), List.of(a), List.of(link), List.of()), coverage("C-a", List.of("a")));
        DossierAssessment missing = f.assess();
        assertThat(ms(missing, "PLAN").status()).isEqualTo("not-assessed");
        blocked(ms(missing, "PLAN"), "QUESTION_COVERAGE_MISSING", "link", null);
        f.add(question("Q-link", List.of("link"), "scoped", block("link", "PLAN")), coverage("C-link", List.of("link"), "Q-link"));
        DossierAssessment open = f.assess();
        assertThat(open.dossier().valid()).isTrue();
        blocked(ms(open, "PLAN"), "QUESTION_OPEN", "link", "Q-link");
        f.add(answer("AN-link", "Q-link", "Connection verified", null), basis("B-link", link, "Q-link", "AN-link"));
        f.approve(link);
        DossierAssessment resolved = f.assess();
        assertThat(resolved.dossier().valid()).isTrue();
        assertThat(ms(resolved, "PLAN").complete()).isTrue();
        assertThat(ms(resolved, "PLAN").ready()).isFalse();
        assertThat(ms(resolved, "PLAN").approvalInputs()).contains("C-link", "Q-link", "AN-link", "B-link");
        assertThat(ms(resolved, "PLAN").connections()).extracting(DossierReport.Dependency::sectionId).containsExactly("link");
        assertThat(ms(resolved, "PLAN").sections()).containsExactly("a");
    }

    @Test
    void connectionOnlySectionIsAlsoEligibleForExplicitAssumptionAndRiskMembership() throws IOException {
        for (String exception : List.of("assumption", "risk")) {
            Fixture f = new Fixture(dir.resolve(exception));
            Part a = f.part("a");
            Part link = f.part("link");
            f.add(milestone("D", "design", List.of("a"), List.of(a), List.of(link), List.of()),
                    milestone("V", "verification", List.of("a"), List.of(a), List.of(link), List.of()),
                    question("Q", List.of("link"), "scoped", block("link", "D")), coverage("C", List.of("a", "link"), "Q"));
            if (exception.equals("assumption")) {
                f.add(assumption("EX", "Q", List.of(link), List.of("D"), "V"));
            } else {
                f.add(e("R", "risk-accepted", "text", "Connection uncertainty", "sections", List.of("link")),
                        riskDetail("EX", "R", List.of(link), List.of("D"), List.of("Q")));
            }
            DossierAssessment report = f.assess();
            assertThat(report.dossier().valid()).as(exception).isTrue();
            assertThat(ms(report, "D").ready()).isTrue();
            assertThat(ms(report, "D").exceptions()).containsExactly("EX");
            assertThat(report.questions().getFirst().status()).isEqualTo("open");
        }
    }

    @Test
    void finalApprovalBindsExactInputsAndPartialScopeApprovalCannotReleaseSpecification() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        Part b = f.part("b");
        Part link = f.part("link");
        f.approve(a);
        f.add(milestone("PLAN", "final-plan", List.of("a", "b"), List.of(a, b), List.of(link), List.of()),
                milestone("SPEC", "specification", List.of("a", "b"), List.of(a, b), List.of(link), List.of("PLAN")),
                coverage("C", List.of("a", "b", "link")));
        DossierAssessment partial = f.assess();
        assertThat(ms(partial, "PLAN").complete()).isFalse();
        assertThat(ms(partial, "SPEC").ready()).isFalse();
        blocked(ms(partial, "PLAN"), "CONNECTION_NOT_APPROVED", "link", null);
        f.approve(b);
        f.approve(link);
        DossierAssessment complete = f.assess();
        assertThat(ms(complete, "PLAN").complete()).isTrue();
        assertThat(ms(complete, "PLAN").approved()).isFalse();
        assertThat(ms(complete, "SPEC").ready()).isFalse();
        assertThat(ms(complete, "PLAN").approvalInputs()).containsExactlyInAnyOrder("MD-PLAN", "A-a-r1", "A-b-r1", "A-link-r1", "C");
        f.add(milestoneApproval("BAD", "PLAN", "MD-PLAN", List.of("MD-PLAN", "A-a-r1", "A-b-r1", "C")));
        invalid(f.assess());
        assertThat(ms(f.assess(), "PLAN").approvalEventId()).isNull();
        f.events.removeLast();
        f.approveMilestone("MP", "PLAN");
        DossierAssessment approved = f.assess();
        assertThat(approved.dossier().valid()).isTrue();
        assertThat(ms(approved, "PLAN").approved()).isTrue();
        assertThat(ms(approved, "PLAN").ready()).isTrue();
        assertThat(ms(approved, "SPEC").ready()).isTrue();
        assertThat(ms(approved, "SPEC").approvalInputs()).contains("MD-PLAN", "MD-SPEC", "MP");
        f.add(e("NEXT", "next-action", "text", "Continue drafting"));
        assertThat(ms(f.assess(), "PLAN").approved()).isTrue();
    }

    @Test
    void finalPlanForOtherContentCannotAuthorizeAnOtherwiseCompleteImplementation() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        Part b = f.part("b");
        f.approve(a);
        f.approve(b);
        f.add(coverage("C", List.of("a", "b")),
                milestone("PLAN-A", "final-plan", List.of("a"), List.of(a), List.of(), List.of()));
        f.approveMilestone("MP-A", "PLAN-A");
        f.add(milestone("IMPL-B", "implementation", List.of("b"), List.of(b), List.of(), List.of("PLAN-A")));
        DossierAssessment report = f.assess();
        assertThat(report.dossier().valid()).isTrue();
        assertThat(ms(report, "PLAN-A").ready()).isTrue();
        assertThat(ms(report, "IMPL-B").complete()).isTrue();
        blocked(ms(report, "IMPL-B"), "FINAL_PLAN_REQUIRED", null, null);
        f.add(milestone("PLAN-B", "final-plan", List.of("b"), List.of(b), List.of(), List.of()));
        f.approveMilestone("MP-B", "PLAN-B");
        f.add(milestone("IMPL-AB", "implementation", List.of("a", "b"), List.of(a, b), List.of(), List.of("PLAN-A", "PLAN-B")));
        DossierAssessment split = f.assess();
        assertThat(ms(split, "IMPL-AB").complete()).isTrue();
        blocked(ms(split, "IMPL-AB"), "FINAL_PLAN_REQUIRED", null, null);
    }

    @Test
    void finalPlanCannotCoverAnOmittedConnectionOrDifferentRevision() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        Part link = f.part("link");
        f.approve(a);
        f.approve(link);
        f.add(coverage("C", List.of("a", "link")), milestone("PLAN", "final-plan", List.of("a"), List.of(a), List.of(), List.of()));
        f.approveMilestone("MP", "PLAN");
        f.add(milestone("IMPL", "implementation", List.of("a"), List.of(a), List.of(link), List.of("PLAN")));
        DossierAssessment omitted = f.assess();
        assertThat(omitted.dossier().valid()).isTrue();
        assertThat(ms(omitted, "IMPL").complete()).isTrue();
        blocked(ms(omitted, "IMPL"), "FINAL_PLAN_REQUIRED", null, null);
        Part next = f.part("a", "r2");
        f.approve(next);
        f.add(supersede("SS", next.decision(), a.decision(), "a"),
                milestone("IMPL2", "implementation", List.of("a"), List.of(next), List.of(), List.of("PLAN")));
        DossierAssessment changed = f.assess();
        assertThat(changed.dossier().valid()).isTrue();
        assertThat(ms(changed, "IMPL2").ready()).isFalse();
        assertThat(ms(changed, "PLAN").approved()).isFalse();
        assertThat(ms(changed, "PLAN").approvalEventId()).isEqualTo("MP");
    }

    @Test
    void replacingConsumedAnswerStalesBasisDerivedDocumentAndMilestoneApproval() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(section("SB", "b"), milestone("PLAN", "final-plan", List.of("a"), List.of(a), List.of(), List.of()),
                question("Q", List.of("a"), "scoped", block("a", "PLAN")),
                question("UNRELATED", List.of("b"), "nonblocking"), answer("AN", "Q", "Initial evidence", null),
                basis("B", a, "Q", "AN"), coverage("C", List.of("a"), "Q"));
        f.approve(a);
        write(dir.resolve("plan.md"), "Synthetic derived text");
        f.add(derived("DOC", "plan", "plan.md", false, a.dependency()));
        f.approveMilestone("MP", "PLAN");
        DossierAssessment initial = f.assess();
        assertThat(ms(initial, "PLAN").approvalInputs()).containsExactlyInAnyOrder("MD-PLAN", "A-a-r1", "B", "Q", "AN", "C");
        f.add(answer("UAN", "UNRELATED", "Independent answer", null));
        assertThat(ms(f.assess(), "PLAN").approved()).isTrue();
        f.add(answer("AN2", "Q", "Corrected evidence", "AN"));
        DossierAssessment stale = f.assess();
        assertThat(stale.dossier().valid()).isTrue();
        assertThat(stale.scopeBases().getFirst().status()).isEqualTo("stale");
        assertThat(stale.dossier().derivedDocuments().getFirst().status()).isEqualTo("stale");
        diagnostic(stale.dossier(), "ANSWER_BASIS_CHANGED", "warning");
        assertThat(ms(stale, "PLAN").approved()).isFalse();
        assertThat(ms(stale, "PLAN").approvalEventId()).isEqualTo("MP");
        assertThat(ms(stale, "PLAN").blockers()).extracting(DossierAssessment.Blocker::code).contains("STALE_BASIS");
        f.add(basis("B2", a, "Q", "AN2"));
        invalid(f.assess());
        assertThat(f.assess().scopeBases()).extracting(DossierAssessment.Basis::id).containsExactly("B");
    }

    @Test
    void requiredDerivedDocumentCannotRemainValidAfterItsAnswerBasisChanges() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(milestone("V", "verification", List.of("a"), List.of(a), List.of(), List.of()),
                question("Q", List.of("a"), "scoped", block("a", "V")), answer("AN", "Q", "One", null),
                basis("B", a, "Q", "AN"), coverage("C", List.of("a"), "Q"));
        f.approve(a);
        write(dir.resolve("required.md"), "Required synthetic output");
        f.add(derived("DOC", "required", "required.md", true, a.dependency()), answer("AN2", "Q", "Two", "AN"));
        DossierAssessment report = f.assess();
        invalid(report);
        assertThat(report.dossier().derivedDocuments().getFirst().status()).isEqualTo("stale");
    }

    @Test
    void actualAnswerAndMovedQuestionScopeCannotSilentlyKeepAnApprovedBasisCurrent() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(section("SB", "b"), milestone("DA", "design", List.of("a"), List.of(a), List.of(), List.of()),
                milestone("V", "verification", List.of("a"), List.of(a), List.of(), List.of()),
                question("Q", List.of("a"), "scoped", block("a", "DA")),
                assumption("AS", "Q", List.of(a), List.of("DA"), "V"), basis("B", a, "Q", "AS"), coverage("C", List.of("a"), "Q"));
        f.approve(a);
        DossierAssessment assumed = f.assess();
        assertThat(assumed.dossier().valid()).isTrue();
        assertThat(ms(assumed, "DA").complete()).isTrue();
        blocked(ms(assumed, "V"), "QUESTION_OPEN", "a", "Q");
        f.add(answer("AN", "Q", "Actual evidence", null));
        DossierAssessment answered = f.assess();
        assertThat(answered.dossier().valid()).isTrue();
        assertThat(answered.questions().getFirst().status()).isEqualTo("answered");
        assertThat(answered.scopeBases().getFirst().status()).isEqualTo("stale");
        assertThat(ms(answered, "V").ready()).isFalse();
        f.add(classify("QC", "Q", List.of("b"), "nonblocking"), coverage("C2", List.of("a")));
        DossierAssessment moved = f.assess();
        assertThat(moved.dossier().valid()).isTrue();
        assertThat(moved.scopeBases().getFirst().status()).isEqualTo("stale");
        assertThat(ms(moved, "DA").complete()).isFalse();
    }

    @Test
    void refreshedInventoryInvalidatesApprovalUntilExplicitReapproval() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.approve(a);
        f.add(milestone("PLAN", "final-plan", List.of("a"), List.of(a), List.of(), List.of()), coverage("C1", List.of("a")));
        f.approveMilestone("MP1", "PLAN");
        f.add(coverage("C2", List.of("a")));
        DossierAssessment refreshed = f.assess();
        assertThat(refreshed.dossier().valid()).isTrue();
        assertThat(ms(refreshed, "PLAN").complete()).isTrue();
        assertThat(ms(refreshed, "PLAN").approved()).isFalse();
        assertThat(ms(refreshed, "PLAN").approvalEventId()).isEqualTo("MP1");
        assertThat(ms(refreshed, "PLAN").approvalInputs()).contains("C2").doesNotContain("C1");
        f.approveMilestone("MP2", "PLAN");
        assertThat(ms(f.assess(), "PLAN").ready()).isTrue();
        assertThat(ms(f.assess(), "PLAN").approvalEventId()).isEqualTo("MP2");
    }

    @Test
    void verificationMayPrecedeDeploymentAndRedeclarationStalesDownstreamApproval() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.approve(a);
        f.add(coverage("C", List.of("a")), milestone("VERIFY", "verification", List.of("a"), List.of(a), List.of(), List.of()));
        assertThat(ms(f.assess(), "VERIFY").ready()).isTrue();
        f.add(milestone("PLAN", "final-plan", List.of("a"), List.of(a), List.of(), List.of("VERIFY")));
        f.approveMilestone("MP", "PLAN");
        f.add(milestone("DEPLOY", "deployment", List.of("a"), List.of(a), List.of(), List.of("PLAN", "VERIFY")));
        assertThat(ms(f.assess(), "DEPLOY").ready()).isTrue();
        f.add(milestone("VERIFY", "verification", List.of("a"), List.of(a), List.of(), List.of()).replace("MD-VERIFY", "MD-VERIFY2"));
        DossierAssessment changed = f.assess();
        assertThat(changed.dossier().valid()).isTrue();
        assertThat(ms(changed, "PLAN").approved()).isFalse();
        assertThat(ms(changed, "PLAN").approvalEventId()).isEqualTo("MP");
        assertThat(ms(changed, "DEPLOY").ready()).isFalse();
        assertThat(changed.milestones()).extracting(DossierAssessment.Milestone::id).containsExactly("VERIFY", "PLAN", "DEPLOY");
    }

    @Test
    void assessmentJsonAndMarkdownPreserveExplicitEmptyValuesAndLegacyReportShape() throws IOException {
        Fixture f = designFixture(dir, "a");
        f.add(question("Q", List.of("a"), "scoped", block("a", "D-a")).replace("Synthetic question", "Question | &lt;safe&gt;"),
                coverage("C", List.of("a"), "Q"));
        DossierAssessment assessment = f.assess();
        JsonNode json = Json.mapper().readTree(Json.write(assessment));
        assertThat(json.propertyNames()).containsExactly("dossier", "status", "questions", "assumptions", "risks", "scopeBases", "coverages", "milestones", "evidence");
        assertThat(json.path("evidence").path("packets").isArray()).isTrue();
        assertThat(json.path("evidence").path("packets").size()).isZero();
        assertThat(json.path("evidence").path("uses").isArray()).isTrue();
        assertThat(json.path("evidence").path("uses").size()).isZero();
        assertThat(json.path("assumptions").isArray()).isTrue();
        assertThat(json.path("assumptions").size()).isZero();
        assertThat(json.path("questions").get(0).path("answerEventId").isNull()).isTrue();
        assertThat(json.path("milestones").get(0).path("approvalEventId").isNull()).isTrue();
        assertThat(json.path("milestones").get(0).path("connections").isArray()).isTrue();
        assertThat(json.path("dossier").propertyNames()).containsExactly("formatVersion", "migrationId", "ledgerSha256", "valid", "historyStatus", "decisions", "risks", "derivedDocuments", "nextAction", "diagnostics");
        assertThat(DossierValidator.check(f.path())).isEqualTo(assessment.dossier());
        String markdown = DossierValidator.markdown(assessment);
        assertThat(markdown).contains("Q", "QUESTION_OPEN", "D-a", assessment.dossier().ledgerSha256(), "not-checked");
        assertThat(markdown.toLowerCase(Locale.ROOT)).contains("question", "exception", "work", "complete", "approv");
        assertThat(markdown).doesNotContain("Question | &lt;safe&gt;");
        assertThat(Json.mapper().readValue(Json.write(assessment), DossierAssessment.class)).isEqualTo(assessment);
        assertThat(ms(f.assess(), "D-a").approvalInputs()).isEqualTo(ms(assessment, "D-a").approvalInputs());
    }

    @Test
    void everyAssessmentCollectionIsAnImmutableSnapshot() {
        var dep = new DossierReport.Dependency("D", "a", "r1", "a".repeat(64));
        var sections = new ArrayList<>(List.of("a"));
        var blocks = new ArrayList<>(List.of(new DossierAssessment.Block("a", "M")));
        var scopes = new ArrayList<>(List.of(dep));
        var ids = new ArrayList<>(List.of("M"));
        var answers = new ArrayList<>(List.of(new DossierAssessment.BasisInput("Q", "AN")));
        var blockers = new ArrayList<>(List.of(new DossierAssessment.Blocker("QUESTION_OPEN", "a", "Q", "Unknown")));
        var q = new DossierAssessment.Question("Q", "Question", "naming", "owner", "lab", sections, "scoped", "reply:Q", blocks, "open", null, null, null, null, null);
        var assumption = new DossierAssessment.Assumption("AS", "Q", "Drafting only", "owner", "reply:AS", scopes, ids, "V");
        var risk = new DossierAssessment.Risk("R", "RD", "owner", "reply:RD", scopes, "bounded", ids, ids, ids, "accepted", null);
        var basis = new DossierAssessment.Basis("B", dep, answers, "current", null, null);
        var coverage = new DossierAssessment.Coverage("a", "C", ids, "owner", "analysis:C", "current");
        var milestone = new DossierAssessment.Milestone("M", "MD", "design", sections, scopes, scopes, "Declared link", ids, "blocked", false, false, false, false, blockers, ids, ids, null);
        var questions = new ArrayList<>(List.of(q));
        var assumptions = new ArrayList<>(List.of(assumption));
        var risks = new ArrayList<>(List.of(risk));
        var bases = new ArrayList<>(List.of(basis));
        var coverages = new ArrayList<>(List.of(coverage));
        var milestones = new ArrayList<>(List.of(milestone));
        var dossier = new DossierReport(2, "M-1", "b".repeat(64), true, "not-checked", List.of(), List.of(), List.of(), null, List.of());
        var report = new DossierAssessment(dossier, "assessed", questions, assumptions, risks, bases, coverages, milestones);
        List.of(sections, blocks, scopes, ids, answers, blockers, questions, assumptions, risks, bases, coverages, milestones).forEach(List::clear);
        assertThat(report.questions()).containsExactly(q);
        assertThat(report.assumptions()).containsExactly(assumption);
        assertThat(report.risks()).containsExactly(risk);
        assertThat(report.scopeBases()).containsExactly(basis);
        assertThat(report.coverages()).containsExactly(coverage);
        assertThat(report.milestones()).containsExactly(milestone);
        JsonNode riskJson = Json.mapper().readTree(Json.write(risk));
        assertThat(riskJson.path("reopenEventId").isNull()).isTrue();
        JsonNode basisJson = Json.mapper().readTree(Json.write(basis));
        assertThat(basisJson.path("legacyApprovalEventId").isNull()).isTrue();
        assertThat(basisJson.path("historicalEvidenceSource").isNull()).isTrue();
        JsonNode blockerJson = Json.mapper().readTree(Json.write(new DossierAssessment.Blocker("INVALID_DOSSIER", null, null, "Invalid")));
        assertThat(blockerJson.path("sectionId").isNull()).isTrue();
        assertThat(blockerJson.path("questionId").isNull()).isTrue();
        JsonNode emptyCoverage = Json.mapper().readTree(Json.write(new DossierAssessment.Coverage("a", "C0", List.of(), "owner", "inventory:C0", "current")));
        assertThat(emptyCoverage.path("questions").isArray()).isTrue();
        assertThat(emptyCoverage.path("questions").size()).isZero();
        List.of(report.questions(), report.assumptions(), report.risks(), report.scopeBases(), report.coverages(), report.milestones(),
                q.sections(), q.dependencies(), assumption.scope(), assumption.milestones(), risk.scope(), risk.triggers(), risk.milestones(), risk.questions(),
                basis.answers(), coverage.questions(), milestone.sections(), milestone.requiredScopes(), milestone.connections(), milestone.requires(),
                milestone.blockers(), milestone.exceptions(), milestone.approvalInputs()).forEach(values -> {
                    assertThat(values).hasSize(1);
                    assertThatThrownBy(values::clear).isInstanceOf(UnsupportedOperationException.class);
                });
    }

    static Fixture designFixture(Path dir, String... sections) {
        Fixture f = new Fixture(dir);
        for (String section : sections) f.add(section("S-" + section, section), milestone("D-" + section, "design", List.of(section), List.of(), List.of(), List.of()));
        return f;
    }

    static DossierAssessment.Milestone ms(DossierAssessment report, String id) {
        return report.milestones().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
    }

    static void blocked(DossierAssessment.Milestone milestone, String code, String section, String question) {
        assertThat(milestone.ready()).isFalse();
        assertThat(milestone.blockers()).anySatisfy(b -> {
            assertThat(b.code()).isEqualTo(code);
            if (section != null) assertThat(b.sectionId()).isEqualTo(section);
            if (question != null) assertThat(b.questionId()).isEqualTo(question);
        });
    }

    static void invalid(DossierAssessment report) {
        assertThat(report.status()).isEqualTo("invalid");
        assertThat(report.dossier().valid()).isFalse();
        assertThat(report.dossier().diagnostics()).anyMatch(d -> d.severity().equals("error"));
        assertThat(report.milestones()).allSatisfy(m -> {
            assertThat(m.canWork()).isFalse();
            assertThat(m.complete()).isFalse();
            assertThat(m.approved()).isFalse();
            assertThat(m.ready()).isFalse();
        });
    }

    static String e(String id, String type, Object... values) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", id); fields.put("type", type); fields.put("at", "unknown"); fields.put("source", "reply:" + id);
        for (int i = 0; i < values.length; i += 2) fields.put((String) values[i], values[i + 1]);
        return Json.write(fields);
    }

    static String section(String id, String section) { return e(id, "section-declare", "sectionId", section, "text", "Synthetic section"); }
    static List<JsonNode> deps(List<Part> parts) { return parts.stream().map(p -> Json.mapper().readTree(p.dependency())).toList(); }
    static String milestone(String id, String kind, List<String> sections, List<Part> scopes, List<Part> connections, List<String> requires) {
        return e("MD-" + id, "milestone-declare", "milestoneId", id, "kind", kind, "sections", sections, "scope", deps(scopes), "connections", deps(connections), "connectionRationale", connections.isEmpty() ? "No connections apply" : "Explicit synthetic connections", "requires", requires);
    }
    static Map<String, String> block(String section, String milestone) { return Map.of("sectionId", section, "neededByMilestoneId", milestone); }
    @SafeVarargs
    static String question(String id, List<String> sections, String classification, Map<String, String>... blocks) {
        return e(id, "question-open", "text", "Synthetic question", "topic", "naming", "owner", "owner", "expectedEvidence", "lab report", "sections", sections, "classification", classification, "classificationSource", "analysis:" + id, "classificationReason", "Explicit scope analysis", "dependencies", List.of(blocks));
    }
    @SafeVarargs
    static String classify(String id, String question, List<String> sections, String classification, Map<String, String>... blocks) {
        return e(id, "question-classify", "question", question, "sections", sections, "classification", classification, "classificationSource", "analysis:" + id, "classificationReason", "Revised scope analysis", "dependencies", List.of(blocks));
    }
    static String coverage(String id, List<String> sections, String... questions) { return e(id, "question-coverage", "sections", sections, "questions", List.of(questions), "owner", "owner", "evidenceSource", "inventory:" + id); }
    static String answer(String id, String question, String text, String replaces) { return e(id, "question-answer", "question", question, "text", text, "evidenceSource", "lab:" + id, "replaces", replaces); }
    static String assumption(String id, String question, List<Part> scopes, List<String> milestones, String verify) { return e(id, "assumption-accepted", "question", question, "text", "Bounded drafting assumption", "owner", "owner", "approvalSource", "approval:" + id, "scope", deps(scopes), "milestones", milestones, "verifyAtMilestoneId", verify); }
    static String basis(String id, Part part, String question, String resolution) { return e(id, "scope-basis", "scope", Json.mapper().readTree(part.dependency()), "answers", List.of(Map.of("questionId", question, "resolutionEventId", resolution))); }
    static String riskDetail(String id, String risk, List<Part> scopes, List<String> milestones, List<String> questions) { return e(id, "risk-detail", "risk", risk, "owner", "owner", "approvalSource", "approval:" + id, "scope", deps(scopes), "limitations", "Synthetic drafting only", "triggers", List.of("new-evidence", "requirement-change"), "milestones", milestones, "questions", questions); }
    static String milestoneApproval(String id, String milestone, String definition, List<String> inputs) { return e(id, "milestone-approve", "milestone", milestone, "definition", definition, "owner", "owner", "approvalSource", "approval:" + id, "inputs", inputs); }
    static String v2(List<String> events) { return "{\"formatVersion\":2,\"migrationId\":\"M-1\",\"events\":[" + String.join(",", events) + "]}"; }

    record Part(String section, String decision, String scope, String dependency) { }
    static final class Fixture {
        final Path dir;
        final List<String> events = new ArrayList<>();
        Fixture(Path dir) { this.dir = dir; }
        Fixture add(String... additions) { events.addAll(List.of(additions)); return this; }
        Part part(String section) throws IOException { return part(section, "r1"); }
        Part part(String section, String revision) throws IOException {
            String content = "Synthetic " + section + " " + revision;
            String scope = snapshot(dir, section, revision, content);
            String decision = "D-" + section + "-" + revision;
            add(proposal(decision, "Synthetic design", scope));
            return new Part(section, decision, scope, dependency(decision, section, revision, content));
        }
        void approve(Part p) { add(approval("A-" + p.decision().substring(2), p.decision(), p.scope())); }
        void approveMilestone(String id, String milestone) throws IOException {
            var current = ms(assess(), milestone);
            add(milestoneApproval(id, milestone, current.definitionEventId(), current.approvalInputs()));
        }
        Path path() throws IOException { return write(dir.resolve("dossier-v2.json"), v2(events)); }
        DossierAssessment assess() throws IOException { return DossierValidator.assess(path()); }
    }
}
