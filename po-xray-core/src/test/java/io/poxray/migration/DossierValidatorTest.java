package io.poxray.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.json.Json;
import io.poxray.migration.DossierReport.Decision;
import io.poxray.migration.DossierReport.Dependency;
import io.poxray.migration.DossierReport.DerivedDocument;
import io.poxray.migration.DossierReport.Diagnostic;
import io.poxray.migration.DossierReport.Risk;
import io.poxray.migration.DossierReport.Scope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/** Synthetic event ledgers: approval is bound to explicit immutable section revisions. */
class DossierValidatorTest {

    @TempDir
    Path dir;

    @Test
    void replacementRequiresApprovedSuccessorAndOnlyReplacesNamedSection() throws IOException {
        String placement = snapshot(dir, "placement", "r1", "placement one\n");
        String rules = snapshot(dir, "rules", "r1", "rules one\n");
        String successor = snapshot(dir, "placement", "r2", "placement two\n");
        String d1 = proposal("D1", "Initial design", placement, rules);
        String a1 = approval("A1", "D1", placement, rules);
        String d2 = proposal("D2", "New placement", successor);
        Path file = dossier(dir, d1, a1, d2);

        DossierReport proposed = DossierValidator.check(file);

        assertThat(proposed.valid()).isTrue();
        assertThat(decision(proposed, "D1").status()).isEqualTo("approved");
        assertThat(decision(proposed, "D1").activeScopes()).extracting(Scope::sectionId)
                .containsExactly("placement", "rules");
        assertThat(decision(proposed, "D2").status()).isEqualTo("proposed");
        assertThat(decision(proposed, "D2").activeScopes()).isEmpty();

        dossier(dir, d1, a1, d2, approval("A2", "D2", successor), supersede("S1", "D2", "D1", "placement"));
        DossierReport replaced = DossierValidator.check(file);

        assertThat(replaced.valid()).isTrue();
        assertThat(replaced.decisions()).extracting(Decision::id).containsExactly("D1", "D2");
        assertThat(decision(replaced, "D1").status()).isEqualTo("partially-superseded");
        assertThat(decision(replaced, "D1").activeScopes()).extracting(Scope::sectionId).containsExactly("rules");
        assertThat(decision(replaced, "D1").proposedScopes()).extracting(Scope::sectionId)
                .containsExactly("placement", "rules");
        assertThat(decision(replaced, "D2").status()).isEqualTo("approved");
        assertThat(decision(replaced, "D2").activeScopes()).extracting(Scope::revision).containsExactly("r2");
    }

    @Test
    void approvalDoesNotCoverNeighbourSectionOrNewRevision() throws IOException {
        String placement = snapshot(dir, "placement", "r1", "one");
        String rules = snapshot(dir, "rules", "r1", "rules");
        String successor = snapshot(dir, "placement", "r2", "two");
        DossierReport report = DossierValidator.check(dossier(dir,
                proposal("D1", "Two sections", placement, rules), approval("A1", "D1", placement),
                proposal("D2", "Unapproved revision", successor)));

        assertThat(report.valid()).isTrue();
        assertThat(decision(report, "D1").status()).isEqualTo("partially-approved");
        assertThat(decision(report, "D1").activeScopes()).extracting(Scope::sectionId).containsExactly("placement");
        assertThat(decision(report, "D1").activeScopes()).extracting(Scope::revision).containsExactly("r1");
        assertThat(decision(report, "D2").activeScopes()).isEmpty();

        for (String outside : List.of(rules, successor)) {
            DossierReport invalid = DossierValidator.check(dossier(dir,
                    proposal("D1", "Placement only", placement), approval("A1", "D1", outside)));
            error(invalid, "INVALID_SCOPE");
            assertThat(decision(invalid, "D1").activeScopes()).isEmpty();
        }
    }

    @Test
    void repeatedActiveApprovalAddsEvidenceWithoutDuplicatingTheScope() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "content");
        String upper = scope.replace(sha256("content".getBytes(StandardCharsets.UTF_8)),
                sha256("content".getBytes(StandardCharsets.UTF_8)).toUpperCase(Locale.ROOT));
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Design", scope),
                approval("A1", "D1", upper), approval("A2", "D1", scope)));

        assertThat(report.valid()).isTrue();
        assertThat(decision(report, "D1").activeScopes()).hasSize(1);
        assertThat(decision(report, "D1").approvalEventIds()).containsExactly("A1", "A2");
    }

    @Test
    void supersededApprovalCannotBeResurrectedByAnotherApproval() throws IOException {
        String one = snapshot(dir, "placement", "r1", "one");
        String two = snapshot(dir, "placement", "r2", "two");
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Old", one),
                approval("A1", "D1", one), proposal("D2", "New", two), approval("A2", "D2", two),
                supersede("S1", "D2", "D1", "placement"), approval("A3", "D1", one)));

        error(report, "INVALID_TRANSITION");
        assertThat(decision(report, "D1").status()).isEqualTo("superseded");
        assertThat(decision(report, "D1").activeScopes()).isEmpty();
        assertThat(decision(report, "D2").activeScopes()).extracting(Scope::revision).containsExactly("r2");
    }

    @Test
    void conflictingApprovalIsRejectedEvenWhenTheContentIsIdentical() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "same");
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "First", scope),
                approval("A1", "D1", scope), proposal("D2", "Second", scope), approval("A2", "D2", scope)));

        error(report, "APPROVAL_CONFLICT");
        assertThat(report.decisions()).extracting(Decision::id).containsExactly("D1", "D2");
    }

    @Test
    void danglingOrUnapprovedSupersessionCannotRemoveTheOldApproval() throws IOException {
        String one = snapshot(dir, "placement", "r1", "one");
        String two = snapshot(dir, "placement", "r2", "two");
        DossierReport missing = DossierValidator.check(dossier(dir, proposal("D1", "Old", one),
                approval("A1", "D1", one), supersede("S1", "absent", "D1", "placement")));
        error(missing, "INVALID_REFERENCE");
        assertThat(decision(missing, "D1").activeScopes()).hasSize(1);

        DossierReport unapproved = DossierValidator.check(dossier(dir, proposal("D1", "Old", one),
                approval("A1", "D1", one), proposal("D2", "New", two), supersede("S1", "D2", "D1", "placement")));
        assertThat(unapproved.valid()).isFalse();
        assertThat(unapproved.diagnostics()).anySatisfy(d -> assertThat(d.severity()).isEqualTo("error"));
        assertThat(decision(unapproved, "D1").activeScopes()).hasSize(1);
        assertThat(decision(unapproved, "D2").activeScopes()).isEmpty();
    }

    @Test
    void selfSupersessionAndASectionSpanningCycleAreRejected() throws IOException {
        String p1 = snapshot(dir, "placement", "r1", "p1");
        String p2 = snapshot(dir, "placement", "r2", "p2");
        String r1 = snapshot(dir, "rules", "r1", "r1");
        String r2 = snapshot(dir, "rules", "r2", "r2");
        DossierReport self = DossierValidator.check(dossier(dir, proposal("D1", "Design", p1),
                approval("A1", "D1", p1), supersede("S1", "D1", "D1", "placement")));
        assertThat(self.valid()).isFalse();
        assertThat(self.diagnostics()).anySatisfy(d -> assertThat(d.severity()).isEqualTo("error"));

        DossierReport cycle = DossierValidator.check(dossier(dir,
                proposal("D1", "First", p1, r1), approval("A1", "D1", p1, r1),
                proposal("D2", "Second", p2, r2), approval("A2", "D2", p2, r2),
                supersede("S1", "D2", "D1", "placement"), supersede("S2", "D1", "D2", "rules")));
        error(cycle, "SUPERSESSION_CYCLE");
    }

    @Test
    void unresolvedDeferredRejectedAndRiskAreNotApprovals() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        for (String state : List.of("approval-unresolved", "defer", "reject")) {
            String status = switch (state) {
                case "defer" -> "deferred";
                case "reject" -> "rejected";
                default -> state;
            };
            DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Design", scope),
                    event("E1", state, "\"decision\":\"D1\""),
                    event("R1", "risk-accepted", "\"text\":\"Manual recovery accepted\",\"sections\":[\"placement\"]"),
                    event("N1", "next-action", "\"text\":\"Earlier action\"")
                            .replace("\"unknown\"", "\"2030-01-01\""),
                    event("N2", "next-action", "\"text\":\"Obtain explicit scope approval\"")));

            assertThat(report.valid()).as(state).isTrue();
            assertThat(decision(report, "D1").status()).isEqualTo(status);
            assertThat(decision(report, "D1").activeScopes()).isEmpty();
            assertThat(decision(report, "D1").approvalEventIds()).isEmpty();
            assertThat(report.risks()).singleElement().satisfies(risk -> {
                assertThat(risk.id()).isEqualTo("R1");
                assertThat(risk.text()).isEqualTo("Manual recovery accepted");
                assertThat(risk.sections()).containsExactly("placement");
            });
            assertThat(report.nextAction()).isEqualTo("Obtain explicit scope approval");
        }
    }

    @Test
    void nonApprovedStatusesCanLaterReceiveExplicitApproval() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        for (String state : List.of("approval-unresolved", "defer", "reject")) {
            DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Design", scope),
                    event("E1", state, "\"decision\":\"D1\""), approval("A1", "D1", scope)));
            assertThat(report.valid()).as(state).isTrue();
            assertThat(decision(report, "D1").status()).isEqualTo("approved");
            assertThat(decision(report, "D1").approvalEventIds()).containsExactly("A1");
        }
    }

    @Test
    void approvedAndPartiallyApprovedDecisionsCannotBeGloballyRejectedDeferredOrUnresolved() throws IOException {
        String placement = snapshot(dir, "placement", "r1", "one");
        String rules = snapshot(dir, "rules", "r1", "rules");
        for (String state : List.of("approval-unresolved", "defer", "reject")) {
            for (String approved : List.of(approval("A1", "D1", placement), approval("A1", "D1", placement, rules))) {
                DossierReport report = DossierValidator.check(dossier(dir,
                        proposal("D1", "Design", placement, rules), approved,
                        event("E1", state, "\"decision\":\"D1\"")));
                error(report, "INVALID_TRANSITION");
                assertThat(decision(report, "D1").activeScopes()).extracting(Scope::sectionId).contains("placement");
            }
        }
    }

    @Test
    void forwardAndWrongTypeReferencesCannotGrantApproval() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        DossierReport forward = DossierValidator.check(dossier(dir, approval("A1", "D1", scope),
                proposal("D1", "Future proposal", scope)));
        error(forward, "INVALID_REFERENCE");
        assertThat(decision(forward, "D1").activeScopes()).isEmpty();

        DossierReport wrongType = DossierValidator.check(dossier(dir,
                event("N1", "next-action", "\"text\":\"Review\""), approval("A1", "N1", scope)));
        error(wrongType, "INVALID_REFERENCE");
        assertThat(wrongType.decisions()).isEmpty();
    }

    @Test
    void changedRawSnapshotIsDetectedEvenAfterItWasSupersededWithoutLeakingPayload() throws IOException {
        String one = snapshot(dir, "placement", "r1", "one\r\n");
        String two = snapshot(dir, "placement", "r2", "two\n");
        Path ledger = dossier(dir, proposal("D1", "Old", one), approval("A1", "D1", one),
                proposal("D2", "New", two), approval("A2", "D2", two), supersede("S1", "D2", "D1", "placement"));
        Files.writeString(dir.resolve("placement-r1.txt"), "synthetic-secret-payload\n");

        DossierReport report = DossierValidator.check(ledger);

        error(report, "SNAPSHOT_CHANGED");
        assertThat(decision(report, "D1").status()).isEqualTo("superseded");
        assertThat(report.diagnostics()).noneSatisfy(d -> assertThat(d.message()).contains("synthetic-secret-payload"));
        assertThat(DossierValidator.markdown(report)).doesNotContain("synthetic-secret-payload");
    }

    @Test
    void rawByteHashDoesNotNormalizeLineEndings() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "line\r\n");
        Path ledger = dossier(dir, proposal("D1", "Design", scope), approval("A1", "D1", scope));
        Files.writeString(dir.resolve("placement-r1.txt"), "line\n");

        error(DossierValidator.check(ledger), "SNAPSHOT_CHANGED");
    }

    @Test
    void derivedDocumentConsumingSupersededRevisionIsStaleWithRequiredSeverity() throws IOException {
        String one = snapshot(dir, "placement", "r1", "one");
        String two = snapshot(dir, "placement", "r2", "two");
        write(dir.resolve("plan.md"), "Derived draft");
        for (boolean required : List.of(true, false)) {
            DossierReport report = DossierValidator.check(dossier(dir,
                    proposal("D1", "Old", one), approval("A1", "D1", one),
                    derived("F1", "plan", "plan.md", required, dependency("D1", "placement", "r1", "one")),
                    proposal("D2", "New", two), approval("A2", "D2", two), supersede("S1", "D2", "D1", "placement")));

            assertThat(report.valid()).isEqualTo(!required);
            assertThat(report.derivedDocuments()).singleElement().satisfies(document -> {
                assertThat(document.id()).isEqualTo("plan");
                assertThat(document.status()).isEqualTo("stale");
                assertThat(document.required()).isEqualTo(required);
                assertThat(document.consumes()).extracting(Dependency::decisionId).containsExactly("D1");
            });
            diagnostic(report, "DERIVED_STALE", required ? "error" : "warning");
        }
    }

    @Test
    void derivedMissingDocumentAndWrongRevisionCannotBeCurrent() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        String d1 = proposal("D1", "Design", scope);
        String a1 = approval("A1", "D1", scope);
        for (boolean required : List.of(true, false)) {
            DossierReport missing = DossierValidator.check(dossier(dir, d1, a1,
                    derived("F1", "plan", "absent.md", required, dependency("D1", "placement", "r1", "one"))));
            assertThat(missing.valid()).isEqualTo(!required);
            assertThat(missing.derivedDocuments().getFirst().status()).isEqualTo("missing");
            diagnostic(missing, "DERIVED_MISSING", required ? "error" : "warning");
        }
        write(dir.resolve("plan.md"), "Draft");
        for (String dependency : List.of(dependency("D1", "placement", "r2", "one"),
                dependency("D1", "placement", "r1", "different bytes"))) {
            DossierReport stale = DossierValidator.check(dossier(dir, d1, a1,
                    derived("F2", "plan", "plan.md", true, dependency)));
            error(stale, "DERIVED_STALE");
            assertThat(stale.derivedDocuments().getFirst().status()).isEqualTo("stale");
        }
    }

    @Test
    void latestDerivedRegistrationWinsWithoutChangingFirstIntroductionOrder() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        write(dir.resolve("first.md"), "first");
        write(dir.resolve("second.md"), "second");
        String consumes = dependency("D1", "placement", "r1", "one");
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Design", scope),
                approval("A1", "D1", scope), derived("F1", "z-document", "missing.md", true, consumes),
                derived("F2", "a-document", "second.md", true, consumes),
                derived("F3", "z-document", "first.md", true, consumes)));

        assertThat(report.valid()).isTrue();
        assertThat(report.derivedDocuments()).extracting(DerivedDocument::id).containsExactly("z-document", "a-document");
        assertThat(report.derivedDocuments()).extracting(DerivedDocument::path).containsExactly("first.md", "second.md");
        assertThat(report.derivedDocuments()).extracting(DerivedDocument::status).containsOnly("current");
        assertThat(report.diagnostics()).extracting(Diagnostic::code).doesNotContain("DERIVED_MISSING");
    }

    @Test
    void snapshotsResolveAgainstCurrentDossierIncludingAbsolutePathsAndBaselineElsewhere() throws IOException {
        Path current = Files.createDirectories(dir.resolve("current"));
        String relative = snapshot(current, "placement", "r1", "relative bytes");
        Path external = write(dir.resolve("external.txt"), "external bytes");
        String absolute = scope("rules", "r1", external.toAbsolutePath().toString(), sha256(Files.readAllBytes(external)));
        Path ledger = dossier(current, proposal("D1", "Design", relative, absolute), approval("A1", "D1", relative, absolute));
        byte[] before = Files.readAllBytes(ledger);
        Path baseline = write(dir.resolve("archive/baseline.json"), Files.readString(ledger));
        write(dir.resolve("archive/placement-r1.txt"), "wrong base directory");

        DossierReport report = DossierValidator.check(ledger, baseline);

        assertThat(report.valid()).isTrue();
        assertThat(report.historyStatus()).isEqualTo("verified");
        assertThat(report.diagnostics()).isEmpty();
        assertThat(decision(report, "D1").activeScopes()).extracting(Scope::sectionId).containsExactly("placement", "rules");
        assertThat(Files.readAllBytes(ledger)).isEqualTo(before);
        assertThat(Files.readAllBytes(baseline)).isEqualTo(before);
        assertThat(Files.readString(external)).isEqualTo("external bytes");
        assertThat(Files.readString(current.resolve("placement-r1.txt"))).isEqualTo("relative bytes");
    }

    @Test
    void missingOrDirectorySnapshotIsAnErrorOnlyAfterApproval() throws IOException {
        String missing = scope("placement", "r1", "absent.txt", "a".repeat(64));
        String proposal = proposal("D1", "Unverified proposal", missing);
        DossierReport proposed = DossierValidator.check(dossier(dir, proposal));
        assertThat(proposed.valid()).isTrue();
        assertThat(decision(proposed, "D1").activeScopes()).isEmpty();
        assertThat(proposed.diagnostics()).extracting(Diagnostic::code).doesNotContain("SNAPSHOT_MISSING", "SNAPSHOT_CHANGED");
        error(DossierValidator.check(dossier(dir, proposal, approval("A1", "D1", missing))), "SNAPSHOT_MISSING");

        Files.createDirectory(dir.resolve("directory.txt"));
        String directory = scope("placement", "r2", "directory.txt", "b".repeat(64));
        error(DossierValidator.check(dossier(dir, proposal("D2", "Directory", directory),
                approval("A2", "D2", directory))), "SNAPSHOT_MISSING");
    }

    @Test
    void outputIsStableExplicitAndIncludesLedgerDigestAndEscapedSourceText() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        String hostile = "choice | injected\n| forged | `unsafe`";
        String propose = proposal("D9", hostile, scope).replace(q("reply:D9"), q("reply | source\n| source-forged | `source-code`"));
        Path ledger = dossier(dir, propose,
                event("R9", "risk-accepted", "\"text\":\"Risk nine\",\"sections\":[\"placement\"]"),
                event("R2", "risk-accepted", "\"text\":\"Risk two\",\"sections\":[\"placement\"]"),
                event("N1", "next-action", "\"text\":\"Approve placement explicitly\""));

        DossierReport first = DossierValidator.check(ledger);
        DossierReport second = DossierValidator.check(ledger);
        String markdown = DossierValidator.markdown(first);

        assertThat(first).isEqualTo(second);
        assertThat(Json.write(first)).isEqualTo(Json.write(second));
        assertThat(markdown).isEqualTo(DossierValidator.markdown(second));
        assertThat(first.ledgerSha256()).isEqualTo(sha256(Files.readAllBytes(ledger)));
        assertThat(first.risks()).extracting(Risk::id).containsExactly("R9", "R2");
        assertThat(markdown).contains("M-1", first.ledgerSha256(), "not-checked", "HISTORY_NOT_CHECKED", "proposed",
                "placement", "r1", "Risk nine", "Risk two", "Approve placement explicitly",
                "choice", "injected", "forged", "unsafe", "source-forged", "source-code");
        assertThat(markdown).doesNotContain(hostile, "choice | injected", "\n| forged |", "`unsafe`",
                "reply | source", "\n| source-forged |", "`source-code`");
        assertThat(markdown.toLowerCase(Locale.ROOT)).contains("question", "milestone", "not");
    }

    @Test
    void emptyListsRemainPresentInJsonAndRoundTripWithoutNulls() throws IOException {
        String scope = scope("placement", "r1", "not-yet-verified.txt", "a".repeat(64));
        Path ledger = dossier(dir, proposal("D1", "Proposal", scope));
        Path baseline = write(dir.resolve("baseline.json"), Files.readString(ledger));
        DossierReport report = DossierValidator.check(ledger, baseline);
        JsonNode json = Json.mapper().readTree(Json.write(report));

        assertThat(report.valid()).isTrue();
        for (String name : List.of("diagnostics", "risks", "derivedDocuments")) {
            assertThat(json.path(name).isArray()).as(name).isTrue();
            assertThat(json.path(name).size()).as(name).isZero();
        }
        for (String name : List.of("activeScopes", "approvalEventIds")) {
            assertThat(json.path("decisions").get(0).path(name).isArray()).as(name).isTrue();
            assertThat(json.path("decisions").get(0).path(name).size()).as(name).isZero();
        }
        DossierReport back = Json.mapper().readValue(Json.write(report), DossierReport.class);
        assertThat(back).isEqualTo(report);
        assertUnmodifiable(back.diagnostics());
        assertUnmodifiable(back.decisions().getFirst().activeScopes());
    }

    @Test
    void everyReportListIsAnImmutableSnapshotOfItsInput() {
        Scope scope = new Scope("placement", "r1", "snapshot.txt", "a".repeat(64));
        List<Scope> proposed = new ArrayList<>(List.of(scope));
        List<Scope> active = new ArrayList<>(List.of(scope));
        List<String> approvals = new ArrayList<>(List.of("A1"));
        Decision decision = new Decision("D1", "approved", "Design", "reply:D1", proposed, active, approvals);
        List<String> sections = new ArrayList<>(List.of("placement"));
        Risk risk = new Risk("R1", "Risk", "reply:R1", sections);
        List<Dependency> consumes = new ArrayList<>(List.of(new Dependency("D1", "placement", "r1", "a".repeat(64))));
        DerivedDocument document = new DerivedDocument("plan", "plan.md", "current", true, consumes);
        List<Decision> decisions = new ArrayList<>(List.of(decision));
        List<Risk> risks = new ArrayList<>(List.of(risk));
        List<DerivedDocument> documents = new ArrayList<>(List.of(document));
        List<Diagnostic> diagnostics = new ArrayList<>(List.of(new Diagnostic("warning", "HISTORY_NOT_CHECKED", null, "No baseline")));
        DossierReport report = new DossierReport(1, "M-1", "b".repeat(64), true, "not-checked",
                decisions, risks, documents, "Review", diagnostics);

        List.of(proposed, active, approvals, sections, consumes, decisions, risks, documents, diagnostics).forEach(List::clear);

        assertThat(report.decisions()).containsExactly(decision);
        assertThat(report.risks()).containsExactly(risk);
        assertThat(report.derivedDocuments()).containsExactly(document);
        assertThat(report.diagnostics()).extracting(Diagnostic::code).containsExactly("HISTORY_NOT_CHECKED");
        assertThat(decision.proposedScopes()).containsExactly(scope);
        assertThat(decision.activeScopes()).containsExactly(scope);
        assertThat(decision.approvalEventIds()).containsExactly("A1");
        assertThat(risk.sections()).containsExactly("placement");
        assertThat(document.consumes()).extracting(Dependency::decisionId).containsExactly("D1");
        List.of(report.decisions(), report.risks(), report.derivedDocuments(), report.diagnostics(),
                decision.proposedScopes(), decision.activeScopes(), decision.approvalEventIds(), risk.sections(),
                document.consumes()).forEach(DossierValidatorTest::assertUnmodifiable);
    }

    @Test
    void invalidMarkdownCannotPresentItselfAsAnAuthoritativeCurrentDesign() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "One", scope),
                approval("A1", "D1", scope), proposal("D2", "Two", scope), approval("A2", "D2", scope)));

        String markdown = DossierValidator.markdown(report);

        assertThat(markdown).contains("APPROVAL_CONFLICT", "M-1", report.ledgerSha256());
        assertThat(markdown.toLowerCase(Locale.ROOT)).contains("invalid").containsPattern("not[^\\n]*authoritative");
    }

    private static void assertUnmodifiable(List<?> values) {
        assertThatThrownBy(values::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    static Decision decision(DossierReport report, String id) {
        return report.decisions().stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    static void error(DossierReport report, String code) {
        assertThat(report.valid()).as(code).isFalse();
        diagnostic(report, code, "error");
    }

    static void diagnostic(DossierReport report, String code, String severity) {
        assertThat(report.diagnostics()).anySatisfy(value -> {
            assertThat(value.code()).isEqualTo(code);
            assertThat(value.severity()).isEqualTo(severity);
        });
    }

    static Path dossier(Path directory, String... events) throws IOException {
        return write(directory.resolve("dossier.json"), ledger(events));
    }

    static String ledger(String... events) {
        return "{\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":[" + String.join(",", events) + "]}";
    }

    static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        return Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    static String event(String id, String type, String fields) {
        return "{\"id\":" + q(id) + ",\"type\":" + q(type) + ",\"at\":\"unknown\",\"source\":" + q("reply:" + id)
                + (fields.isEmpty() ? "" : "," + fields) + "}";
    }

    static String proposal(String id, String text, String... scopes) {
        return event(id, "propose", "\"text\":" + q(text) + ",\"scope\":[" + String.join(",", scopes) + "]");
    }

    static String approval(String id, String decision, String... scopes) {
        return event(id, "approve", "\"decision\":" + q(decision) + ",\"scope\":[" + String.join(",", scopes) + "]");
    }

    static String supersede(String id, String decision, String replaces, String section) {
        return event(id, "supersede", "\"decision\":" + q(decision) + ",\"replaces\":" + q(replaces)
                + ",\"sections\":[" + q(section) + "]");
    }

    static String snapshot(Path directory, String section, String revision, String text) throws IOException {
        String name = section + "-" + revision + ".txt";
        Path file = write(directory.resolve(name), text);
        return scope(section, revision, name, sha256(Files.readAllBytes(file)));
    }

    static String scope(String section, String revision, String path, String hash) {
        return "{\"sectionId\":" + q(section) + ",\"revision\":" + q(revision) + ",\"path\":" + q(path)
                + ",\"sha256\":" + q(hash) + "}";
    }

    static String dependency(String decision, String section, String revision, String content) {
        return "{\"decisionId\":" + q(decision) + ",\"sectionId\":" + q(section) + ",\"revision\":" + q(revision)
                + ",\"sha256\":" + q(sha256(content.getBytes(StandardCharsets.UTF_8))) + "}";
    }

    static String derived(String id, String documentId, String path, boolean required, String dependency) {
        return event(id, "derived-document", "\"documentId\":" + q(documentId) + ",\"path\":" + q(path)
                + ",\"required\":" + required + ",\"consumes\":[" + dependency + "]");
    }

    static String q(String text) {
        return Json.write(text);
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
