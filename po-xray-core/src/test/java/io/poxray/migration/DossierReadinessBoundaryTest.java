package io.poxray.migration;

import static io.poxray.migration.DossierReadinessTest.*;
import static io.poxray.migration.DossierValidatorTest.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Additional boundary regressions discovered while implementing the approved contract. */
class DossierReadinessBoundaryTest {
    @TempDir Path dir;

    @Test
    void hexadecimalDigestCaseCannotHideAStaleBasisFromDerivedDocuments() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(question("Q", List.of("a"), "nonblocking"), answer("AN", "Q", "Original", null), basis("B", a, "Q", "AN"));
        f.approve(a);
        write(dir.resolve("derived.md"), "Synthetic output");
        String digest = io.poxray.json.Json.mapper().readTree(a.dependency()).get("sha256").stringValue();
        f.add(derived("DOC", "derived", "derived.md", false, a.dependency().replace(digest, digest.toUpperCase(Locale.ROOT))));
        assertThat(f.assess().dossier().derivedDocuments().getFirst().status()).isEqualTo("current");
        f.add(answer("AN2", "Q", "Changed", "AN"));
        DossierAssessment stale = f.assess();
        assertThat(stale.dossier().valid()).isTrue();
        assertThat(stale.scopeBases().getFirst().status()).isEqualTo("stale");
        assertThat(stale.dossier().derivedDocuments().getFirst().status()).isEqualTo("stale");
    }

    @Test
    void unclassifiedPrerequisiteQuestionRemainsVisibleAcrossSectionBoundaries() throws IOException {
        Fixture f = designFixture(dir, "a", "b");
        f.add(milestone("CHILD", "design", List.of("b"), List.of(), List.of(), List.of("D-a")),
                question("Q", List.of("a"), "unclassified"), coverage("C", List.of("a", "b"), "Q"));
        DossierAssessment report = f.assess();
        assertThat(report.dossier().valid()).isTrue();
        assertThat(ms(report, "D-b").ready()).isTrue();
        blocked(ms(report, "CHILD"), "UNCLASSIFIED_QUESTION", "a", "Q");
        blocked(ms(report, "CHILD"), "PREREQUISITE_NOT_READY", null, null);
        assertThat(ms(report, "CHILD").status()).isEqualTo("not-assessed");
    }

    @Test
    void milestoneApprovalCannotBeRegisteredAgainstChangedSnapshotBytes() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.approve(a);
        f.add(milestone("PLAN", "final-plan", List.of("a"), List.of(a), List.of(), List.of()), coverage("C", List.of("a")));
        List<String> inputs = ms(f.assess(), "PLAN").approvalInputs();
        String snapshot = io.poxray.json.Json.mapper().readTree(a.scope()).get("path").stringValue();
        write(dir.resolve(snapshot), "Altered immutable evidence");
        f.add(milestoneApproval("MP", "PLAN", "MD-PLAN", inputs));
        DossierAssessment report = f.assess();
        invalid(report);
        assertThat(ms(report, "PLAN").approvalEventId()).isNull();
        error(report.dossier(), "SNAPSHOT_CHANGED");
    }

    @Test
    void onlyExplicitMatchingRiskCanWaiveAssumptionVerificationDebtAndReopeningRestoresIt() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(milestone("D", "design", List.of("a"), List.of(a), List.of(), List.of()),
                milestone("VERIFY", "verification", List.of("a"), List.of(a), List.of(), List.of()),
                milestone("AFTER", "verification", List.of("a"), List.of(a), List.of(), List.of("VERIFY")),
                question("Q", List.of("a"), "scoped", block("a", "D")),
                assumption("AS", "Q", List.of(a), List.of("D"), "VERIFY"), basis("B", a, "Q", "AS"), coverage("C", List.of("a"), "Q"));
        f.approve(a);
        blocked(ms(f.assess(), "VERIFY"), "QUESTION_OPEN", "a", "Q");
        f.add(e("R", "risk-accepted", "text", "Bounded verification exception", "sections", List.of("a")));
        blocked(ms(f.assess(), "VERIFY"), "QUESTION_OPEN", "a", "Q");
        f.add(riskDetail("RD", "R", List.of(a), List.of("VERIFY"), List.of("Q")));
        DossierAssessment waived = f.assess();
        assertThat(waived.dossier().valid()).isTrue();
        assertThat(ms(waived, "VERIFY").ready()).isTrue();
        assertThat(ms(waived, "VERIFY").exceptions()).containsExactly("RD");
        assertThat(ms(waived, "VERIFY").approvalInputs()).contains("RD");
        blocked(ms(waived, "AFTER"), "QUESTION_OPEN", "a", "Q");
        assertThat(ms(waived, "AFTER").exceptions()).isEmpty();
        assertThat(waived.questions().getFirst().status()).isEqualTo("open");
        assertThat(waived.questions().getFirst().answerEventId()).isNull();
        f.add(e("RR", "risk-reopen", "risk", "R", "triggerType", "new-evidence", "evidenceSource", "lab:new", "text", "Changed observation"));
        DossierAssessment reopened = f.assess();
        assertThat(reopened.dossier().valid()).isTrue();
        blocked(ms(reopened, "VERIFY"), "QUESTION_OPEN", "a", "Q");
        assertThat(ms(reopened, "VERIFY").exceptions()).isEmpty();
        assertThat(reopened.questions().getFirst().status()).isEqualTo("open");
        assertThat(reopened.questions().getFirst().answerEventId()).isNull();
    }
}
