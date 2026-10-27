package io.poxray.migration;

import static io.poxray.migration.DossierEvidenceFixtures.*;
import static io.poxray.migration.DossierEvidenceTest.unavailable;
import static io.poxray.migration.DossierReadinessTest.*;
import static io.poxray.migration.DossierValidatorTest.*;
import static org.junit.jupiter.api.Assertions.*;

import io.poxray.json.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DossierEvidenceReadinessTest {
    @TempDir Path dir;

    @Test void unavailableEvidenceBlocksDesignWithoutBasisAndDoesNotBindDifferentRevisionByName() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        Part next = f.ledger.part("a", "r2");
        f.ledger.add(milestone("OTHER", "design", List.of("a"), List.of(next), List.of(), List.of()),
                milestone("UNRESOLVED", "design", List.of("a"), List.of(), List.of(), List.of()),
                milestone("CHILD", "design", List.of("b"), List.of(f.b), List.of(), List.of("DA")));
        var files = f.register("E", f.a); f.use("U", "E", f.a, null); Files.delete(files.packet());
        var report = f.assess(); unavailable(report, "U", "no scope basis");
        assertTrue(report.scopeBases().isEmpty());
        assertFalse(ms(report, "DA").ready()); assertFalse(ms(report, "DA").canWork());
        assertFalse(ms(report, "DA").complete());
        assertTrue(ms(report, "DA").blockers().stream().anyMatch(b -> b.code().startsWith("EVIDENCE_") && "a".equals(b.sectionId())));
        assertTrue(ms(report, "UNRESOLVED").blockers().stream().anyMatch(b -> b.code().equals("EVIDENCE_SCOPE_UNRESOLVED")));
        assertTrue(ms(report, "OTHER").ready(), "Different exact consumed revision must not inherit a same-name evidence use");
        assertTrue(ms(report, "DB").ready()); assertFalse(ms(report, "CHILD").ready());
    }

    @Test void connectionEvidenceGuardUsesExactConsumedConnection() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        Part link = f.ledger.part("link"); f.ledger.approve(link);
        f.ledger.add(coverage("CL", List.of("link")), milestone("CONNECTED", "design", List.of("a"), List.of(f.a), List.of(link), List.of()));
        var files = f.register("E", link); f.use("U", "E", link, null); Files.delete(files.review());
        var report = f.assess(); unavailable(report, "U", "connection");
        assertFalse(ms(report, "CONNECTED").ready());
        assertTrue(ms(report, "CONNECTED").blockers().stream().anyMatch(b -> b.code().startsWith("EVIDENCE_") && "link".equals(b.sectionId())));
        assertTrue(ms(report, "DA").ready()); assertTrue(ms(report, "DB").ready());
    }

    @Test void validEvidenceNeverAnswersAnOpenQuestionOrApprovesFinalPlan() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        f.ledger.add(milestone("PLAN", "final-plan", List.of("a"), List.of(f.a), List.of(), List.of()),
                milestone("IMPL", "implementation", List.of("a"), List.of(f.a), List.of(), List.of("PLAN")),
                question("Q", List.of("a"), "scoped", block("a", "DA")), coverage("CQ", List.of("a", "b"), "Q"));
        f.register("E", f.a); f.use("U", "E", f.a, null);
        var report = f.assess(); assertTrue(report.dossier().valid(), Json.write(report));
        assertEquals("supported", use(report, "U").path("state").stringValue());
        assertEquals("open", report.questions().getFirst().status()); assertNull(report.questions().getFirst().answerEventId());
        assertFalse(ms(report, "DA").ready()); assertFalse(ms(report, "PLAN").approved());
        assertFalse(ms(report, "IMPL").ready()); assertTrue(ms(report, "DB").ready());
        assertFalse(ms(report, "PLAN").approvalInputs().contains("E")); assertFalse(ms(report, "PLAN").approvalInputs().contains("U"));
    }

    @Test void approvedMilestoneAndDeclaredBasisSurviveEvidenceLossAndRestoreWithoutHistoryRewrite() throws Exception {
        var f = new DossierEvidenceFixtures(dir, false);
        f.ledger.add(question("Q", List.of("a"), "scoped", block("a", "DA")), answer("AN", "Q", "Explicit PoC answer", null),
                basis("BASIS", f.a, "Q", "AN"), coverage("CQ", List.of("a", "b"), "Q"),
                milestone("PLAN", "final-plan", List.of("a"), List.of(f.a), List.of(), List.of()),
                milestone("IMPL", "implementation", List.of("a"), List.of(f.a), List.of(), List.of("PLAN")));
        // Approve using the evidence-independent v2 prefix. Evidence does not become an approval input.
        f.ledger.approve(f.a); f.ledger.approve(f.b);
        f.ledger.approveMilestone("MP", "PLAN");
        assertTrue(f.legacyPrerequisites().dossier().valid(), Json.write(f.legacyPrerequisites()));
        assertTrue(ms(f.legacyPrerequisites(), "IMPL").ready());
        var files = f.register("E", f.a); f.use("U", "E", f.a, null);
        var original = f.assess(); assertTrue(original.dossier().valid(), Json.write(original));
        assertTrue(ms(original, "PLAN").approved()); assertTrue(ms(original, "IMPL").ready());
        assertEquals("current", original.scopeBases().getFirst().status());
        byte[] saved = Files.readAllBytes(files.packet()); String ledgerHash = sha256(Files.readAllBytes(f.path()));
        Files.delete(files.packet());
        var unavailable = f.assess(); assertTrue(unavailable.dossier().valid(), Json.write(unavailable));
        assertEquals("stale", unavailable.scopeBases().getFirst().status());
        assertEquals("BASIS", unavailable.scopeBases().getFirst().id());
        assertEquals(original.questions(), unavailable.questions());
        assertEquals(original.risks(), unavailable.risks());
        assertEquals(ms(original, "DB"), ms(unavailable, "DB"));
        assertTrue(ms(unavailable, "PLAN").approved()); assertEquals("MP", ms(unavailable, "PLAN").approvalEventId());
        assertEquals(ms(original, "PLAN").approvalInputs(), ms(unavailable, "PLAN").approvalInputs());
        for (String id : List.of("PLAN", "IMPL")) {
            assertFalse(ms(unavailable, id).ready(), id); assertFalse(ms(unavailable, id).canWork(), id); assertFalse(ms(unavailable, id).complete(), id);
        }
        assertEquals(ledgerHash, sha256(Files.readAllBytes(f.path())));
        Files.write(files.packet(), saved); var restored = f.assess();
        assertEquals(original, restored, "Restoring identical bytes restores current applicability, not a fabricated historic validation");
    }

    @Test void approvalAfterEvidenceDeclarationIsAdmittedIndependentlyOfCurrentAvailability() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        f.ledger.add(milestone("PLAN", "final-plan", List.of("a"), List.of(f.a), List.of(), List.of()));
        List<String> inputs = ms(f.ledger.assess(), "PLAN").approvalInputs();
        var files = f.register("E", f.a); f.use("U", "E", f.a, null);
        f.ledger.add(milestoneApproval("MP", "PLAN", "MD-PLAN", inputs));
        Files.delete(files.review());
        var report = f.assess(); assertTrue(report.dossier().valid(), Json.write(report));
        assertTrue(ms(report, "PLAN").approved()); assertEquals("MP", ms(report, "PLAN").approvalEventId());
        assertFalse(ms(report, "PLAN").ready()); assertEquals(inputs, ms(report, "PLAN").approvalInputs());
    }

    @Test void noUseCreatesNoGuardAndBasisAdmissionDoesNotDependOnMutableEvidence() throws Exception {
        var f = new DossierEvidenceFixtures(dir, false);
        var files = f.register("E", f.a); byte[] bytes = Files.readAllBytes(files.packet()); Files.delete(files.packet());
        var unused = f.assess(); assertTrue(unused.dossier().valid(), Json.write(unused));
        assertTrue(ms(unused, "DA").ready()); assertTrue(ms(unused, "DB").ready());
        f.use("U", "E", f.a, null);
        f.ledger.add(question("Q", List.of("a"), "scoped", block("a", "DA")), answer("AN", "Q", "Explicit answer", null),
                basis("B", f.a, "Q", "AN"), coverage("CQ", List.of("a", "b"), "Q"));
        f.ledger.approve(f.a); f.ledger.approve(f.b);
        assertTrue(f.legacyPrerequisites().dossier().valid(), Json.write(f.legacyPrerequisites()));
        var unavailable = f.assess(); assertTrue(unavailable.dossier().valid(), Json.write(unavailable));
        assertEquals("B", unavailable.scopeBases().getFirst().id()); assertEquals("stale", unavailable.scopeBases().getFirst().status());
        Files.write(files.packet(), bytes);
        var restored = f.assess(); assertTrue(restored.dossier().valid(), Json.write(restored));
        assertEquals("current", restored.scopeBases().getFirst().status()); assertTrue(ms(restored, "DA").ready());
    }

    @Test void explicitPocAnswerCorrectionChangesOnlyConsumedBasisAndPreservesNeighborRiskApproval() throws Exception {
        var f = new DossierEvidenceFixtures(dir, false);
        f.ledger.add(question("QA", List.of("a"), "scoped", block("a", "DA")), answer("ANA", "QA", "Initial result", null), basis("BA", f.a, "QA", "ANA"),
                question("QB", List.of("b"), "scoped", block("b", "DB")), answer("ANB", "QB", "Independent result", null), basis("BB", f.b, "QB", "ANB"),
                e("R", "risk-accepted", "text", "Independent bounded risk", "sections", List.of("b")),
                riskDetail("RD", "R", List.of(f.b), List.of("DB"), List.of("QB")), coverage("CQ", List.of("a", "b"), "QA", "QB"),
                milestone("PLANB", "final-plan", List.of("b"), List.of(f.b), List.of(), List.of()));
        f.ledger.approve(f.a); f.ledger.approve(f.b);
        f.ledger.approveMilestone("MPB", "PLANB");
        assertTrue(f.legacyPrerequisites().dossier().valid(), Json.write(f.legacyPrerequisites()));
        f.register("E1", f.a); f.use("U1", "E1", f.a, null);
        var initial = f.assess(); assertTrue(initial.dossier().valid(), Json.write(initial));
        f.ledger.add(answer("ANA2", "QA", "PoC observed a different response", "ANA"));
        Part next = f.ledger.part("a", "r2");
        f.ledger.add(basis("BA2", next, "QA", "ANA2")); f.ledger.approve(next);
        f.ledger.add(supersede("SS", next.decision(), f.a.decision(), "a"), milestone("DA2", "design", List.of("a"), List.of(next), List.of(), List.of()));
        f.register("E2", next); f.use("U2", "E2", next, null);
        assertTrue(f.legacyPrerequisites().dossier().valid(), Json.write(f.legacyPrerequisites()));
        assertTrue(ms(f.legacyPrerequisites(), "DA2").ready());
        var corrected = f.assess(); assertTrue(corrected.dossier().valid(), Json.write(corrected));
        assertEquals("stale", corrected.scopeBases().stream().filter(b -> b.id().equals("BA")).findFirst().orElseThrow().status());
        assertEquals("current", corrected.scopeBases().stream().filter(b -> b.id().equals("BA2")).findFirst().orElseThrow().status());
        assertEquals(initial.scopeBases().stream().filter(b -> b.id().equals("BB")).findFirst(), corrected.scopeBases().stream().filter(b -> b.id().equals("BB")).findFirst());
        assertEquals(initial.risks(), corrected.risks()); assertEquals(ms(initial, "PLANB"), ms(corrected, "PLANB"));
        assertTrue(ms(corrected, "DA2").ready()); assertEquals("supported", use(corrected, "U2").path("state").stringValue());
    }

    @Test void replacementChainReplaySelectsLatestSupportedNodeWithoutRewritingDeclaredAncestry() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        var a = f.register("EA", f.a); f.use("UA", "EA", f.a, null);
        var b = f.register("EB", f.a); f.use("UB", "EB", f.a, "UA");
        var c = f.register("EC", f.a); f.use("UC", "EC", f.a, "UB");
        byte[] ab = Files.readAllBytes(a.packet()), bb = Files.readAllBytes(b.packet()), cb = Files.readAllBytes(c.packet());
        states(f.assess(), "superseded", "superseded", "supported");
        Files.delete(b.packet()); states(f.assess(), "superseded", "superseded", "supported");
        assertFalse(use(f.assess(), "UB").path("reasons").isEmpty(), "Lost source reasons remain on superseded nodes");
        Files.delete(c.packet()); states(f.assess(), "supported", "proposed-unavailable", "proposed-unavailable");
        assertTrue(ms(f.assess(), "DA").ready(), "Unsupported successors cannot retire supported predecessor");
        Files.write(b.packet(), bb); states(f.assess(), "superseded", "supported", "proposed-unavailable");
        Files.delete(a.packet()); states(f.assess(), "superseded", "supported", "proposed-unavailable");
        Files.delete(b.packet()); states(f.assess(), "unavailable", "proposed-unavailable", "proposed-unavailable");
        assertFalse(ms(f.assess(), "DA").ready()); assertTrue(ms(f.assess(), "DB").ready());
        Files.write(c.packet(), cb); states(f.assess(), "superseded", "superseded", "supported");
        Files.write(a.packet(), ab); Files.write(b.packet(), bb); states(f.assess(), "superseded", "superseded", "supported");
    }

    @Test void independentRootsRemainRequiredAndUnavailableTailCanBeExtended() throws Exception {
        var f = new DossierEvidenceFixtures(dir);
        var a = f.register("EA", f.a); f.use("UA", "EA", f.a, null);
        var b = f.register("EB", f.a); f.use("UB", "EB", f.a, "UA"); Files.delete(b.packet());
        f.register("EC", f.a); f.use("UC", "EC", f.a, "UB");
        var independent = f.register("EI", f.a); f.use("UI", "EI", f.a, null); Files.delete(independent.review());
        var report = f.assess(); states(report, "superseded", "superseded", "supported");
        assertEquals("unavailable", use(report, "UI").path("state").stringValue());
        assertFalse(ms(report, "DA").ready(), "An independent root is not merged by same claim or scope");
        assertTrue(ms(report, "DB").ready());
    }

    @Test void forksNonTailForwardAndWrongScopeReplacementsAreAlwaysStructuralErrors() throws Exception {
        for (String invalid : List.of("fork", "non-tail", "forward", "self", "wrong-scope")) {
            var f = new DossierEvidenceFixtures(dir.resolve(invalid));
            var a = f.register("EA", f.a); f.use("UA", "EA", f.a, null);
            var b = f.register("EB", f.a); f.use("UB", "EB", f.a, "UA");
            f.register("EC", f.a);
            if (invalid.equals("non-tail")) f.use("UC", "EC", f.a, "UB");
            f.use("BAD", "EC", invalid.equals("wrong-scope") ? f.b : f.a,
                    switch (invalid) { case "forward" -> "LATER"; case "self" -> "BAD"; case "wrong-scope" -> "UB"; default -> "UA"; });
            if (invalid.equals("forward")) f.use("LATER", "EC", f.a, "UB");
            for (boolean missing : List.of(false, true)) {
                if (missing) { Files.delete(a.packet()); Files.delete(b.packet()); }
                var report = f.assess(); assertFalse(report.dossier().valid(), invalid + missing);
                assertTrue(report.dossier().diagnostics().stream().anyMatch(d -> d.severity().equals("error")), invalid);
                for (var milestone : report.milestones()) { assertFalse(milestone.ready()); assertFalse(milestone.canWork()); }
            }
        }
    }

    @Test void originalStructuralErrorsStillInvalidateUnaffectedReadiness() throws Exception {
        var f = new DossierEvidenceFixtures(dir); f.register("E", f.a); f.use("U", "E", f.a, null);
        f.ledger.add(answer("BAD", "NONEXISTENT", "Unknown answer", null));
        var report = f.assess(); assertFalse(report.dossier().valid());
        assertFalse(report.milestones().isEmpty(), "v3 must project invalid original events, not reject the version");
        assertFalse(ms(report, "DB").ready()); assertFalse(ms(report, "DB").canWork());
    }

    private static void states(DossierAssessment report, String a, String b, String c) {
        assertTrue(report.dossier().valid(), Json.write(report));
        assertEquals(a, use(report, "UA").path("state").stringValue());
        assertEquals(b, use(report, "UB").path("state").stringValue());
        assertEquals(c, use(report, "UC").path("state").stringValue());
        assertEquals("UA", use(report, "UB").path("replaces").stringValue());
        assertEquals("UB", use(report, "UC").path("replaces").stringValue());
    }
}
