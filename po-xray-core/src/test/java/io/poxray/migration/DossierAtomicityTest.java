package io.poxray.migration;

import static io.poxray.migration.DossierValidatorTest.approval;
import static io.poxray.migration.DossierValidatorTest.decision;
import static io.poxray.migration.DossierValidatorTest.dependency;
import static io.poxray.migration.DossierValidatorTest.derived;
import static io.poxray.migration.DossierValidatorTest.dossier;
import static io.poxray.migration.DossierValidatorTest.error;
import static io.poxray.migration.DossierValidatorTest.event;
import static io.poxray.migration.DossierValidatorTest.proposal;
import static io.poxray.migration.DossierValidatorTest.snapshot;
import static io.poxray.migration.DossierValidatorTest.supersede;
import static io.poxray.migration.DossierValidatorTest.write;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.migration.DossierReport.Scope;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** No rejected multi-item event may apply its valid first item before discovering a later error. */
class DossierAtomicityTest {
    @TempDir
    Path dir;

    @Test
    void mixedApprovalDoesNotGrantItsValidFirstScopeOrRecordApprovalEvidence() throws IOException {
        String placement = snapshot(dir, "placement", "r1", "placement");
        String rules = snapshot(dir, "rules", "r1", "rules");
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Only placement", placement),
                approval("A1", "D1", placement, rules)));

        error(report, "INVALID_SCOPE");
        assertThat(decision(report, "D1").activeScopes()).isEmpty();
        assertThat(decision(report, "D1").approvalEventIds()).isEmpty();
        assertThat(decision(report, "D1").status()).isEqualTo("proposed");
    }

    @Test
    void laterSupersededScopePreventsApprovalOfAnEarlierUnapprovedScope() throws IOException {
        String placement = snapshot(dir, "placement", "r1", "placement");
        String replacement = snapshot(dir, "placement", "r2", "replacement");
        String rules = snapshot(dir, "rules", "r1", "rules");
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Initial", placement, rules),
                approval("A1", "D1", placement), proposal("D2", "Replacement", replacement),
                approval("A2", "D2", replacement), supersede("S1", "D2", "D1", "placement"),
                approval("A3", "D1", rules, placement)));

        error(report, "INVALID_TRANSITION");
        assertThat(decision(report, "D1").activeScopes()).isEmpty();
        assertThat(decision(report, "D1").approvalEventIds()).containsExactly("A1");
        assertThat(decision(report, "D2").activeScopes()).extracting(Scope::revision).containsExactly("r2");
    }

    @Test
    void mixedSupersessionDoesNotRemoveItsValidFirstSection() throws IOException {
        String placement = snapshot(dir, "placement", "r1", "placement");
        String replacement = snapshot(dir, "placement", "r2", "replacement");
        String rules = snapshot(dir, "rules", "r1", "rules");
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Initial", placement, rules),
                approval("A1", "D1", placement, rules), proposal("D2", "Placement only", replacement),
                approval("A2", "D2", replacement), event("S1", "supersede",
                        "\"decision\":\"D2\",\"replaces\":\"D1\",\"sections\":[\"placement\",\"rules\"]")));

        error(report, "INVALID_TRANSITION");
        error(report, "APPROVAL_CONFLICT");
        assertThat(decision(report, "D1").activeScopes()).extracting(Scope::sectionId).containsExactly("placement", "rules");
        assertThat(decision(report, "D1").status()).isEqualTo("approved");
        assertThat(decision(report, "D2").activeScopes()).extracting(Scope::sectionId).containsExactly("placement");
    }

    @Test
    void invalidLaterDerivedRegistrationKeepsThePriorRegistration() throws IOException {
        String placement = snapshot(dir, "placement", "r1", "placement");
        write(dir.resolve("plan.md"), "Synthetic plan");
        String validDependency = dependency("D1", "placement", "r1", "placement");
        String invalidDependency = dependency("future", "placement", "r1", "placement");
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Placement", placement),
                approval("A1", "D1", placement), derived("F1", "plan", "plan.md", true, validDependency),
                event("F2", "derived-document", "\"documentId\":\"plan\",\"path\":\"missing.md\",\"required\":true,"
                        + "\"consumes\":[" + validDependency + "," + invalidDependency + "]")));

        error(report, "INVALID_REFERENCE");
        assertThat(report.derivedDocuments()).hasSize(1);
        assertThat(report.derivedDocuments().getFirst().path()).isEqualTo("plan.md");
        assertThat(report.derivedDocuments().getFirst().status()).isEqualTo("current");
    }
}
