package io.poxray.migration;

import static io.poxray.migration.DossierReadinessTest.*;
import static io.poxray.migration.DossierValidatorTest.error;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Invalid append-only suffixes must not rewrite the applied approval history. */
class DossierReadinessChronologyTest {
    @TempDir Path dir;

    @Test
    void malformedSuffixPreservesTheEarlierAppliedMilestoneApproval() throws IOException {
        Fixture f = approvedPlan();
        f.add(e("BAD", "section-declare", "sectionId", "bad")); // Missing required text.

        DossierAssessment report = f.assess();

        invalid(report);
        assertThat(ms(report, "PLAN").approvalEventId()).isEqualTo("MP");
        assertThat(report.dossier().diagnostics()).anySatisfy(d -> {
            assertThat(d.eventId()).isEqualTo("BAD");
            assertThat(d.code()).isEqualTo("INVALID_DOCUMENT");
        }).noneMatch(d -> "MP".equals(d.eventId()) && "INVALID_TRANSITION".equals(d.code()));
    }

    @Test
    void duplicateIdSuffixCannotEraseTheFirstApprovalWithThatId() throws IOException {
        Fixture f = approvedPlan();
        f.add(section("MP", "later"));

        DossierAssessment report = f.assess();

        invalid(report);
        error(report.dossier(), "DUPLICATE_EVENT_ID");
        assertThat(ms(report, "PLAN").approvalEventId()).isEqualTo("MP");
        assertThat(report.dossier().diagnostics()).noneMatch(d -> "MP".equals(d.eventId()) && "INVALID_TRANSITION".equals(d.code()));
    }

    @Test
    void malformedPrefixStillPreventsApplyingTheLaterMilestoneApproval() throws IOException {
        Fixture f = approvedPlan();
        f.events.add(f.events.size() - 1, e("BAD", "section-declare", "sectionId", "bad"));

        DossierAssessment report = f.assess();

        invalid(report);
        assertThat(ms(report, "PLAN").approvalEventId()).isNull();
        assertThat(report.dossier().diagnostics()).anySatisfy(d -> {
            assertThat(d.eventId()).isEqualTo("MP");
            assertThat(d.code()).isEqualTo("INVALID_TRANSITION");
        });
    }

    @Test
    void malformedAnswerEvidenceAgainstAnExistingQuestionDoesNotAnswerIt() throws IOException {
        Fixture f = questionFixture();
        for (Object malformed : List.of(false, "", 42)) {
            f.add(e("AN", "question-answer", "question", "Q", "text", "Claimed observation", "evidenceSource", malformed, "replaces", null));
            DossierAssessment report = f.assess();
            invalid(report);
            assertThat(report.questions().getFirst().status()).isEqualTo("open");
            assertThat(report.questions().getFirst().answerEventId()).isNull();
            assertThat(report.questions().getFirst().answer()).isNull();
            assertThat(report.questions().getFirst().evidenceSource()).isNull();
            assertShapeDiagnostic(report, "AN");
            f.events.removeLast();
        }
    }

    @Test
    void malformedDeferralReviewAgainstAnExistingQuestionLeavesItsDependencyIntact() throws IOException {
        Fixture f = questionFixture();
        for (Object malformed : List.of(false, "", List.of("review"))) {
            f.add(e("DF", "question-defer", "question", "Q", "reason", "Await evidence", "reviewAt", malformed));
            DossierAssessment report = f.assess();
            invalid(report);
            assertThat(report.questions().getFirst().status()).isEqualTo("open");
            assertThat(report.questions().getFirst().deferralReason()).isNull();
            assertThat(report.questions().getFirst().reviewAt()).isNull();
            assertThat(report.questions().getFirst().dependencies()).containsExactly(new DossierAssessment.Block("a", "D-a"));
            assertShapeDiagnostic(report, "DF");
            f.events.removeLast();
        }
    }

    private Fixture approvedPlan() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.approve(a);
        f.add(milestone("PLAN", "final-plan", List.of("a"), List.of(a), List.of(), List.of()), coverage("C", List.of("a")));
        f.approveMilestone("MP", "PLAN");
        assertThat(ms(f.assess(), "PLAN").ready()).isTrue();
        return f;
    }

    private Fixture questionFixture() throws IOException {
        Fixture f = designFixture(dir, "a");
        f.add(question("Q", List.of("a"), "scoped", block("a", "D-a")), coverage("C", List.of("a"), "Q"));
        assertThat(f.assess().dossier().valid()).isTrue();
        return f;
    }

    private static void assertShapeDiagnostic(DossierAssessment report, String eventId) {
        assertThat(report.dossier().diagnostics()).anySatisfy(d -> {
            assertThat(d.eventId()).isEqualTo(eventId);
            assertThat(d.code()).isEqualTo("INVALID_DOCUMENT");
        }).noneMatch(d -> "INVALID_REFERENCE".equals(d.code()));
    }
}
