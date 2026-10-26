package io.poxray.migration;

import static io.poxray.migration.DossierReadinessTest.*;
import static io.poxray.migration.DossierValidatorTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Strict event validation, atomic registration, and preserved v1 consent. */
class DossierReadinessReaderTest {
    @TempDir Path dir;

    @Test
    void legacyAssessmentIsExplicitlyNotAssessedAndHistoryUpgradeRequiresUnchangedPrefix() throws IOException {
        String original = event("N", "next-action", "\"text\":\"Review\"");
        Path baseline = write(dir.resolve("legacy.json"), ledger(original));
        byte[] before = Files.readAllBytes(baseline);
        DossierAssessment legacy = DossierValidator.assess(baseline);
        assertThat(legacy.dossier().valid()).isTrue();
        assertThat(legacy.status()).isEqualTo("not-assessed");
        List.of(legacy.questions(), legacy.assumptions(), legacy.risks(), legacy.scopeBases(), legacy.coverages(), legacy.milestones())
                .forEach(values -> assertThat(values).isEmpty());
        Path current = write(dir.resolve("current.json"), v2(List.of(original, section("S", "a"))));
        byte[] currentBytes = Files.readAllBytes(current);
        DossierAssessment upgraded = DossierValidator.assess(current, baseline);
        assertThat(upgraded.dossier().valid()).isTrue();
        assertThat(upgraded.status()).isEqualTo("assessed");
        assertThat(upgraded.dossier().historyStatus()).isEqualTo("verified");
        diagnostic(upgraded.dossier(), "HISTORY_FORMAT_UPGRADED", "warning");
        assertThat(DossierValidator.check(current, baseline)).isEqualTo(upgraded.dossier());
        assertThat(Files.readAllBytes(baseline)).isEqualTo(before);
        assertThat(Files.readAllBytes(current)).isEqualTo(currentBytes);
        write(current, v2(List.of(original.replace("Review", "Rewritten"), section("S", "a"))));
        error(DossierValidator.assess(current, baseline).dossier(), "HISTORY_CHANGED");
        write(current, v2(List.of(original)));
        assertThat(DossierValidator.assess(baseline, current).dossier().valid()).isFalse();
    }

    @Test
    void v1RejectsNewEventsAndV2RejectsUnknownMissingAndWronglyTypedFields() throws IOException {
        String section = section("S", "a");
        assertThat(DossierValidator.check(write(dir.resolve("v1.json"), ledger(section))).valid()).isFalse();
        String declaration = milestone("D", "design", List.of("a"), List.of(), List.of(), List.of());
        String question = question("Q", List.of("a"), "nonblocking");
        List<String> invalidEvents = List.of(
                section.replace("\"sectionId\"", "\"unexpected\""),
                declaration.replace("\"connections\"", "\"unknownConnections\""),
                declaration.replace("\"design\"", "\"operate\""),
                question.replace("\"classification\"", "\"missingClassification\""),
                question.replace("\"nonblocking\"", "true"),
                question.replace("\"owner\" : \"owner\"", "\"owner\" : null"),
                question.replace("\"Explicit scope analysis\"", "\" \""),
                e("AN", "question-answer", "question", "Q", "text", "Answer", "replaces", null),
                e("DF", "question-defer", "question", "Q", "reason", "Later", "reviewAt", false));
        for (String invalidEvent : invalidEvents) {
            Fixture f = new Fixture(dir);
            f.add(section("BASE", "a"), milestone("BASE", "design", List.of("a"), List.of(), List.of(), List.of()),
                    question("KNOWN", List.of("a"), "nonblocking"), invalidEvent);
            DossierAssessment report = f.assess();
            invalid(report);
            assertThat(DossierValidator.check(f.path()).valid()).isFalse();
        }
    }

    @Test
    void duplicateKeysAndIdentifiersCannotGrantReadiness() throws IOException {
        Fixture f = designFixture(dir, "a");
        f.add(coverage("C", List.of("a")), coverage("C", List.of("a")));
        error(f.assess().dossier(), "DUPLICATE_EVENT_ID");
        Path file = write(dir.resolve("duplicates.json"), v2(List.of(section("S", "a"))).replace("\"formatVersion\":2", "\"formatVersion\":2,\"formatVersion\":2"));
        assertThatThrownBy(() -> DossierValidator.assess(file)).isInstanceOf(IOException.class);
        f = designFixture(dir, "a");
        f.add(question("Q", List.of("a", "a"), "nonblocking"));
        invalid(f.assess());
        assertThat(f.assess().questions()).isEmpty();
    }

    @Test
    void incompleteOrExtraneousCoverageIsAtomicAndCannotReplaceCurrentInventory() throws IOException {
        Fixture f = designFixture(dir, "a", "b");
        f.add(question("QA", List.of("a"), "nonblocking"), question("QB", List.of("b"), "nonblocking"),
                coverage("CA", List.of("a"), "QA"), coverage("CB", List.of("b"), "QB"));
        for (String invalidCoverage : List.of(coverage("BAD", List.of("a")), coverage("BAD", List.of("a"), "QA", "QB"),
                coverage("BAD", List.of("a", "b"), "QA"))) {
            f.add(invalidCoverage);
            DossierAssessment report = f.assess();
            invalid(report);
            assertThat(report.coverages()).extracting(DossierAssessment.Coverage::eventId).containsExactly("CA", "CB");
            f.events.removeLast();
        }
    }

    @Test
    void compoundAssumptionAndRiskFailWithoutRegisteringAnyPartialException() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(milestone("DA", "design", List.of("a"), List.of(a), List.of(), List.of()),
                milestone("VA", "verification", List.of("a"), List.of(a), List.of(), List.of()),
                question("Q", List.of("a"), "scoped", block("a", "DA")), coverage("C", List.of("a"), "Q"),
                e("R", "risk-accepted", "text", "Limited risk", "sections", List.of("a")));
        String assumption = assumption("AS", "Q", List.of(a), List.of("DA"), "VA");
        for (String bad : List.of(
                assumption.replace("\"owner\" : \"owner\"", "\"owner\" : null"),
                assumption.replace("\"approval:AS\"", "\" \""),
                assumption("AS", "Q", List.of(a), List.of("DA", "missing"), "VA"),
                assumption("AS", "Q", List.of(a), List.of("VA"), "VA"),
                riskDetail("RD", "R", List.of(a), List.of("DA"), List.of("Q", "missing")))) {
            f.add(bad);
            DossierAssessment report = f.assess();
            invalid(report);
            assertThat(report.assumptions()).isEmpty();
            assertThat(report.risks()).isEmpty();
            assertThat(ms(report, "DA").exceptions()).isEmpty();
            f.events.removeLast();
        }
    }

    @Test
    void compoundClassificationBasisAndMilestoneCannotPartiallyReplaceValidState() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(milestone("DA", "design", List.of("a"), List.of(a), List.of(), List.of()),
                question("Q", List.of("a"), "unclassified"), answer("AN", "Q", "Observed", null), coverage("C", List.of("a"), "Q"));
        f.add(classify("BAD-Q", "Q", List.of("a"), "scoped", block("a", "DA"), block("a", "missing")));
        DossierAssessment classification = f.assess();
        invalid(classification);
        assertThat(classification.questions().getFirst().classification()).isEqualTo("unclassified");
        assertThat(classification.questions().getFirst().dependencies()).isEmpty();
        f.events.removeLast();
        f.add(e("BAD-B", "scope-basis", "scope", Json.mapper().readTree(a.dependency()), "answers",
                List.of(Map.of("questionId", "Q", "resolutionEventId", "AN"), Map.of("questionId", "missing", "resolutionEventId", "AN"))));
        invalid(f.assess());
        assertThat(f.assess().scopeBases()).isEmpty();
        f.events.removeLast();
        f.add(milestone("DA", "design", List.of("a"), List.of(a), List.of(), List.of("missing")).replace("MD-DA", "BAD-M"));
        invalid(f.assess());
        assertThat(ms(f.assess(), "DA").definitionEventId()).isEqualTo("MD-DA");
    }

    @Test
    void assumptionVerificationDependencyCannotBeDroppedByReclassificationOrRedeclaration() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        Part b = f.part("b");
        f.add(milestone("D", "design", List.of("a"), List.of(a), List.of(), List.of()),
                milestone("V", "verification", List.of("a", "b"), List.of(a, b), List.of(), List.of()),
                question("Q", List.of("a", "b"), "scoped", block("a", "D")),
                assumption("AS", "Q", List.of(a), List.of("D"), "V"), coverage("C", List.of("a", "b"), "Q"));
        f.add(classify("QC", "Q", List.of("b"), "nonblocking"));
        DossierAssessment removedScope = f.assess();
        invalid(removedScope);
        assertThat(removedScope.questions().getFirst().sections()).containsExactly("a", "b");
        f.events.removeLast();
        f.add(milestone("V", "verification", List.of("b"), List.of(b), List.of(), List.of()).replace("MD-V", "MD-V2"));
        invalid(f.assess());
        assertThat(ms(f.assess(), "V").sections()).containsExactly("a", "b");
    }

    @Test
    void selfReferencesUpdateCyclesAndRemovalOfQuestionSectionsAreAtomic() throws IOException {
        Fixture f = designFixture(dir, "a", "b");
        f.add(milestone("CHILD", "design", List.of("a"), List.of(), List.of(), List.of("D-a")),
                question("Q", List.of("a"), "scoped", block("a", "D-a")), coverage("C", List.of("a", "b"), "Q"));
        for (String cyclic : List.of(milestone("SELF", "design", List.of("a"), List.of(), List.of(), List.of("SELF")),
                milestone("D-a", "design", List.of("a"), List.of(), List.of(), List.of("CHILD")).replace("MD-D-a", "MD-CYCLE"))) {
            f.add(cyclic);
            error(f.assess().dossier(), "MILESTONE_CYCLE");
            assertThat(ms(f.assess(), "D-a").definitionEventId()).isEqualTo("MD-D-a");
            assertThat(f.assess().milestones()).noneMatch(m -> m.id().equals("SELF"));
            f.events.removeLast();
        }
        f.add(milestone("D-a", "design", List.of("b"), List.of(), List.of(), List.of()).replace("MD-D-a", "MD-REMOVED"));
        invalid(f.assess());
        assertThat(ms(f.assess(), "D-a").sections()).containsExactly("a");
    }

    @Test
    void answerCorrectionRequiresTheLatestAnswerAndInvalidCorrectionIsAtomic() throws IOException {
        Fixture f = designFixture(dir, "a");
        f.add(question("Q", List.of("a"), "nonblocking"), answer("AN", "Q", "One", null), answer("AN2", "Q", "Two", "AN"));
        for (String bad : List.of(answer("BAD", "Q", "Three", null), answer("BAD", "Q", "Three", "AN"),
                answer("BAD", "Q", "Three", "missing"))) {
            f.add(bad);
            invalid(f.assess());
            assertThat(f.assess().questions().getFirst().answerEventId()).isEqualTo("AN2");
            assertThat(f.assess().questions().getFirst().answer()).isEqualTo("Two");
            f.events.removeLast();
        }
    }

    @Test
    void connectionOnlyQuestionDependencyPreventsRemovingThatConnectionFromDefinition() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        Part link = f.part("link");
        f.add(milestone("D", "design", List.of("a"), List.of(a), List.of(link), List.of()),
                question("Q", List.of("link"), "scoped", block("link", "D")), coverage("C", List.of("a", "link"), "Q"));
        assertThat(f.assess().dossier().valid()).isTrue();
        f.add(milestone("D", "design", List.of("a"), List.of(a), List.of(), List.of()).replace("MD-D", "MD-D2"));
        DossierAssessment report = f.assess();
        invalid(report);
        assertThat(ms(report, "D").definitionEventId()).isEqualTo("MD-D");
        assertThat(ms(report, "D").connections()).extracting(DossierReport.Dependency::sectionId).containsExactly("link");
    }

    @Test
    void milestoneApprovalCannotCreateCompletenessOrAcceptDuplicateInputs() throws IOException {
        Fixture f = designFixture(dir, "a");
        f.add(coverage("C", List.of("a")), milestoneApproval("MP", "D-a", "MD-D-a", List.of("MD-D-a", "C")));
        invalid(f.assess());
        assertThat(ms(f.assess(), "D-a").approvalEventId()).isNull();
        f = new Fixture(dir);
        Part a = f.part("a");
        f.approve(a);
        f.add(milestone("PLAN", "final-plan", List.of("a"), List.of(a), List.of(), List.of()), coverage("C", List.of("a")),
                milestoneApproval("BAD", "PLAN", "MD-PLAN", List.of("MD-PLAN", "A-a-r1", "C", "C")));
        invalid(f.assess());
        assertThat(ms(f.assess(), "PLAN").approvalEventId()).isNull();
        f.events.removeLast();
        f.add(milestoneApproval("GOOD", "PLAN", "MD-PLAN", List.of("C", "A-a-r1", "MD-PLAN")));
        assertThat(ms(f.assess(), "PLAN").approved()).isTrue();
    }

    @Test
    void preservedLegacyApprovalCanImportItsFirstHistoricalAnswerBasisWithoutNewConsent() throws IOException {
        Legacy legacy = legacyFixture(dir);
        byte[] archiveBytes = Files.readAllBytes(legacy.archive());
        legacy.fixture().add(legacy.importEvent());
        DossierAssessment report = legacy.fixture().assess();
        assertThat(report.dossier().valid()).isTrue();
        assertThat(report.scopeBases()).singleElement().satisfies(b -> {
            assertThat(b.id()).isEqualTo("IMPORT");
            assertThat(b.status()).isEqualTo("current");
            assertThat(b.legacyApprovalEventId()).isEqualTo("A-a-r1");
            assertThat(b.historicalEvidenceSource()).isEqualTo("archive:approval-relationship");
            assertThat(b.answers()).containsExactly(new DossierAssessment.BasisInput("Q", "AN"));
        });
        assertThat(decision(report.dossier(), legacy.part().decision()).approvalEventIds()).containsExactly("A-a-r1");
        assertThat(ms(report, "VERIFY").ready()).isTrue();
        assertThat(Files.readAllBytes(legacy.archive())).isEqualTo(archiveBytes);
        legacy.fixture().add(answer("AN2", "Q", "Correction", "AN"));
        DossierAssessment changed = legacy.fixture().assess();
        assertThat(changed.dossier().valid()).isTrue();
        assertThat(changed.scopeBases().getFirst().status()).isEqualTo("stale");
        assertThat(ms(changed, "VERIFY").ready()).isFalse();
        diagnostic(changed.dossier(), "ANSWER_BASIS_CHANGED", "warning");
    }

    @Test
    void importCannotRebindUseCorrectionsOrClaimAnotherApprovalScope() throws IOException {
        for (String mode : List.of("correction", "wrong-approval", "wrong-scope", "second-import", "normal-rebind")) {
            Legacy legacy = legacyFixture(dir.resolve(mode));
            Fixture f = legacy.fixture();
            String importEvent = legacy.importEvent();
            switch (mode) {
                case "correction" -> { f.add(answer("AN2", "Q", "Corrected", "AN")); importEvent = importEvent.replace("\"AN\"", "\"AN2\""); }
                case "wrong-approval" -> importEvent = importEvent.replace("\"A-a-r1\"", "\"D-a-r1\"");
                case "wrong-scope" -> importEvent = importEvent.replace("\"revision\" : \"r1\"", "\"revision\" : \"r9\"");
                case "second-import" -> { f.add(importEvent); importEvent = importEvent.replace("\"IMPORT\"", "\"IMPORT2\""); }
                case "normal-rebind" -> { f.add(importEvent); importEvent = basis("B2", legacy.part(), "Q", "AN"); }
                default -> throw new AssertionError(mode);
            }
            f.add(importEvent);
            DossierAssessment report = f.assess();
            invalid(report);
            assertThat(report.scopeBases()).hasSize(mode.equals("second-import") || mode.equals("normal-rebind") ? 1 : 0);
            assertThat(decision(report.dossier(), legacy.part().decision()).approvalEventIds()).containsExactly("A-a-r1");
        }
    }

    @Test
    void legacyImportChecksSnapshotDigestIdentityFormatAndEntireEventPrefixEveryRun() throws IOException {
        for (String mode : List.of("hash", "identity", "format", "prefix", "missing")) {
            Legacy legacy = legacyFixture(dir.resolve(mode));
            String archive = Files.readString(legacy.archive());
            String importEvent = legacy.importEvent();
            switch (mode) {
                case "hash" -> write(legacy.archive(), archive + " ");
                case "identity" -> write(legacy.archive(), archive.replace("M-1", "M-2"));
                case "format" -> write(legacy.archive(), archive.replace("\"formatVersion\":1", "\"formatVersion\":2"));
                case "prefix" -> write(legacy.archive(), archive.replace("Synthetic design", "Changed design"));
                case "missing" -> importEvent = importEvent.replace("legacy.json", "missing.json");
                default -> throw new AssertionError(mode);
            }
            if (!mode.equals("hash") && !mode.equals("missing")) {
                importEvent = importEvent.replace(sha256(archive.getBytes(java.nio.charset.StandardCharsets.UTF_8)), sha256(Files.readAllBytes(legacy.archive())));
            }
            legacy.fixture().add(importEvent);
            DossierAssessment report = legacy.fixture().assess();
            error(report.dossier(), "LEGACY_IMPORT_INVALID");
            assertThat(report.scopeBases()).isEmpty();
        }
    }

    @Test
    void knownLaterHistoricalAnswerIsRejectedButUnknownOrMixedPrecisionIsNotInvented() throws IOException {
        for (String answerDate : List.of("2026-02-02", "unknown", "2026-02-02T12:00:00Z")) {
            Legacy legacy = legacyFixture(dir.resolve(answerDate.replace(":", "-")));
            Fixture f = legacy.fixture();
            int answerIndex = f.events.size() - 2;
            f.events.set(answerIndex, f.events.get(answerIndex).replace("2026-01-31", answerDate));
            f.add(legacy.importEvent());
            DossierAssessment report = f.assess();
            if (answerDate.equals("2026-02-02")) {
                invalid(report);
                assertThat(report.scopeBases()).isEmpty();
            } else {
                assertThat(report.dossier().valid()).as(answerDate).isTrue();
                assertThat(report.scopeBases()).hasSize(1);
            }
        }
    }

    @Test
    void normalScopeBasisMustPrecedeApprovalAndCannotBindAQuestionFromAnotherSection() throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.add(section("SB", "b"), question("QA", List.of("a"), "nonblocking"), answer("ANA", "QA", "A", null),
                question("QB", List.of("b"), "nonblocking"), answer("ANB", "QB", "B", null));
        f.add(basis("BAD", a, "QB", "ANB"));
        invalid(f.assess());
        assertThat(f.assess().scopeBases()).isEmpty();
        f.events.removeLast();
        f.approve(a);
        f.add(basis("LATE", a, "QA", "ANA"));
        invalid(f.assess());
        assertThat(f.assess().scopeBases()).isEmpty();
    }

    private static Legacy legacyFixture(Path dir) throws IOException {
        Fixture f = new Fixture(dir);
        Part a = f.part("a");
        f.approve(a);
        f.events.set(1, f.events.get(1).replace("unknown", "2026-02-01"));
        Path archive = write(dir.resolve("legacy.json"), ledger(f.events.toArray(String[]::new)));
        f.add(milestone("VERIFY", "verification", List.of("a"), List.of(a), List.of(), List.of()),
                question("Q", List.of("a"), "scoped", block("a", "VERIFY")),
                answer("AN", "Q", "Historical first answer", null).replace("unknown", "2026-01-31"),
                coverage("C", List.of("a"), "Q"));
        String importer = e("IMPORT", "scope-basis-import", "scope", Json.mapper().readTree(a.dependency()),
                "answers", List.of(Map.of("questionId", "Q", "resolutionEventId", "AN")), "approval", "A-a-r1",
                "legacyPath", "legacy.json", "legacySha256", sha256(Files.readAllBytes(archive)), "evidenceSource", "archive:approval-relationship");
        return new Legacy(f, a, archive, importer);
    }
    private record Legacy(Fixture fixture, Part part, Path archive, String importEvent) { }
}
