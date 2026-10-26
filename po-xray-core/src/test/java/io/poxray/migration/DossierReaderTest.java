package io.poxray.migration;

import static io.poxray.migration.DossierValidatorTest.approval;
import static io.poxray.migration.DossierValidatorTest.decision;
import static io.poxray.migration.DossierValidatorTest.dependency;
import static io.poxray.migration.DossierValidatorTest.derived;
import static io.poxray.migration.DossierValidatorTest.diagnostic;
import static io.poxray.migration.DossierValidatorTest.dossier;
import static io.poxray.migration.DossierValidatorTest.error;
import static io.poxray.migration.DossierValidatorTest.event;
import static io.poxray.migration.DossierValidatorTest.ledger;
import static io.poxray.migration.DossierValidatorTest.proposal;
import static io.poxray.migration.DossierValidatorTest.scope;
import static io.poxray.migration.DossierValidatorTest.snapshot;
import static io.poxray.migration.DossierValidatorTest.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reader guarantees through the public check boundary; no dependency on helper implementation details. */
class DossierReaderTest {

    @TempDir
    Path dir;

    @Test
    void strictInputCannotLoseUnknownRootEventScopeOrDependencyFields() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        String propose = proposal("D1", "Design", scope);
        String approve = approval("A1", "D1", scope);
        List<String> invalidInputs = List.of(
                ledger(propose, approve).replace("\"events\":", "\"futureApproval\":true,\"events\":"),
                ledger(propose, approve.replace("\"type\":", "\"futureScope\":\"all\",\"type\":")),
                ledger(propose, approval("A1", "D1", scope.replace("\"revision\":", "\"futureField\":true,\"revision\":"))),
                ledger(propose, approve, derived("F1", "plan", "plan.md", true,
                        dependency("D1", "placement", "r1", "one").replace("\"revision\":", "\"futureField\":true,\"revision\":"))));

        for (String input : invalidInputs) {
            DossierReport report = DossierValidator.check(write(dir.resolve("dossier.json"), input));
            error(report, "INVALID_DOCUMENT");
        }
        DossierReport invalidApproval = DossierValidator.check(write(dir.resolve("dossier.json"), invalidInputs.get(1)));
        assertThat(decision(invalidApproval, "D1").activeScopes()).isEmpty();
    }

    @Test
    void strictInputRejectsMissingAndWronglyTypedRequiredFields() throws IOException {
        String valid = ledger(event("N1", "next-action", "\"text\":\"Review\""));
        List<String> inputs = List.of("null", "[]", "{}",
                "{\"formatVersion\":1,\"migrationId\":\"M-1\"}",
                "{\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":{}}",
                valid.replace("\"formatVersion\":1", "\"formatVersion\":\"1\""),
                valid.replace("\"formatVersion\":1", "\"formatVersion\":1.0"),
                valid.replace("\"M-1\"", "\"M-0\""),
                valid.replace("\"M-1\"", "12"),
                valid.replace("\"type\":\"next-action\",", ""),
                valid.replace("\"id\":\"N1\"", "\"id\":null"),
                valid.replace("\"at\":\"unknown\",", ""),
                valid.replace("\"source\":\"reply:N1\"", "\"source\":\" \""),
                valid.replace("\"text\":\"Review\"", "\"text\":false"),
                valid.replace("\"next-action\"", "\"unknown-action\""),
                valid.replace("\"text\":\"Review\"", "\"text\":\"\""));

        for (String input : inputs) {
            error(DossierValidator.check(write(dir.resolve("dossier.json"), input)), "INVALID_DOCUMENT");
        }
    }

    @Test
    void malformedApprovalCannotSilentlyAcquireDefaultScope() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        String validApproval = approval("A1", "D1", scope);
        for (String invalidApproval : List.of(
                event("A1", "approve", "\"decision\":\"D1\""),
                event("A1", "approve", "\"decision\":\"D1\",\"scope\":null"),
                event("A1", "approve", "\"decision\":\"D1\",\"scope\":[]"),
                validApproval.replace("\"revision\":\"r1\",", ""),
                validApproval.replace("\"sectionId\":\"placement\"", "\"sectionId\":7"),
                validApproval.replace("\"decision\":\"D1\",", ""))) {
            DossierReport report = DossierValidator.check(dossier(dir, proposal("D1", "Design", scope), invalidApproval));
            assertThat(report.valid()).isFalse();
            assertThat(report.diagnostics()).anySatisfy(d -> assertThat(d.severity()).isEqualTo("error"));
            assertThat(decision(report, "D1").activeScopes()).isEmpty();
        }
    }

    @Test
    void duplicateEventIdAndUnsupportedVersionAreExplicitErrors() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        DossierReport duplicate = DossierValidator.check(dossier(dir,
                proposal("D1", "Design", scope), approval("D1", "D1", scope)));
        error(duplicate, "DUPLICATE_EVENT_ID");
        assertThat(decision(duplicate, "D1").activeScopes()).isEmpty();

        DossierReport unsupported = DossierValidator.check(write(dir.resolve("dossier.json"),
                ledger().replace("\"formatVersion\":1", "\"formatVersion\":2")));
        error(unsupported, "UNSUPPORTED_FORMAT");
    }

    @Test
    void duplicateJsonKeysAtAnyDepthAndBrokenJsonThrowIOException() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        for (String input : List.of("{", "{} {}",
                "{\"formatVersion\":1,\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":[]}",
                ledger(event("N1", "next-action", "\"text\":\"first\",\"text\":\"last\"")),
                ledger(proposal("D1", "Design", scope.replace("\"revision\":", "\"revision\":\"r1\",\"revision\":"))))) {
            Path file = write(dir.resolve("dossier.json"), input);
            assertThatThrownBy(() -> DossierValidator.check(file)).as(input).isInstanceOf(IOException.class);
        }
        assertThatThrownBy(() -> DossierValidator.check(dir.resolve("missing.json"))).isInstanceOf(IOException.class);
    }

    @Test
    void sameSectionRevisionCannotChangeItsPathOrDigestAndScopeSectionsCannotRepeat() throws IOException {
        String scope = snapshot(dir, "placement", "r1", "one");
        String changedPath = scope.replace("placement-r1.txt", "other.txt");
        String changedHash = scope("placement", "r1", "placement-r1.txt", "b".repeat(64));
        for (String inconsistent : List.of(changedPath, changedHash)) {
            error(DossierValidator.check(dossier(dir, proposal("D1", "First", scope),
                    proposal("D2", "Inconsistent revision", inconsistent))), "INVALID_SCOPE");
        }
        error(DossierValidator.check(dossier(dir, proposal("D1", "Repeated section", scope, scope))), "INVALID_SCOPE");
        DossierReport duplicateApproval = DossierValidator.check(dossier(dir, proposal("D1", "Design", scope),
                approval("A1", "D1", scope, scope)));
        error(duplicateApproval, "INVALID_SCOPE");
        assertThat(decision(duplicateApproval, "D1").activeScopes()).isEmpty();
    }

    @Test
    void timestampsAreValidatedButEventArrayOrderDefinesTheProjection() throws IOException {
        for (String date : List.of("2026-10-26", "2026-10-26T09:30:00+01:00", "unknown")) {
            DossierReport report = DossierValidator.check(dossier(dir,
                    event("N1", "next-action", "\"text\":\"First\"").replace("\"unknown\"", "\"2030-01-01\""),
                    event("N2", "next-action", "\"text\":\"Second\"").replace("\"unknown\"", "\"" + date + "\"")));
            assertThat(report.valid()).as(date).isTrue();
            assertThat(report.nextAction()).isEqualTo("Second");
        }
        for (String date : List.of("yesterday", "2026-02-30", "2026-10-26T09:30:00")) {
            error(DossierValidator.check(dossier(dir,
                    event("N1", "next-action", "\"text\":\"First\"").replace("\"unknown\"", "\"" + date + "\""))),
                    "INVALID_DOCUMENT");
        }
    }

    @Test
    void historyRequiresUnchangedBaselinePrefixButIgnoresWhitespaceAndObjectKeyOrder() throws IOException {
        String first = event("N1", "next-action", "\"text\":\"Review\"");
        Path baseline = write(dir.resolve("baseline.json"), """
                {
                  "events": [{"source":"reply:N1","text":"Review","at":"unknown","type":"next-action","id":"N1"}],
                  "migrationId": "M-1", "formatVersion": 1
                }
                """);
        Path current = dossier(dir, first, event("N2", "next-action", "\"text\":\"Approve\""));
        byte[] baselineBytes = Files.readAllBytes(baseline);
        byte[] currentBytes = Files.readAllBytes(current);

        DossierReport appended = DossierValidator.check(current, baseline);

        assertThat(appended.valid()).isTrue();
        assertThat(appended.historyStatus()).isEqualTo("verified");
        assertThat(appended.diagnostics()).isEmpty();
        assertThat(appended.nextAction()).isEqualTo("Approve");
        assertThat(Files.readAllBytes(baseline)).isEqualTo(baselineBytes);
        assertThat(Files.readAllBytes(current)).isEqualTo(currentBytes);

        DossierReport identical = DossierValidator.check(write(current, ledger(first)), baseline);
        assertThat(identical.valid()).isTrue();
        assertThat(identical.historyStatus()).isEqualTo("verified");
    }

    @Test
    void rewritingTruncatingOrReorderingHistoryCannotPassTheBaseline() throws IOException {
        String first = event("N1", "next-action", "\"text\":\"First\"");
        String second = event("N2", "next-action", "\"text\":\"Second\"");
        Path baseline = write(dir.resolve("baseline.json"), ledger(first, second));
        for (String history : List.of(ledger(first.replace("First", "Edited"), second), ledger(first), ledger(second, first))) {
            DossierReport report = DossierValidator.check(write(dir.resolve("dossier.json"), history), baseline);
            error(report, "HISTORY_CHANGED");
            assertThat(report.historyStatus()).isEqualTo("changed");
        }
    }

    @Test
    void baselineComparisonPreservesNestedArrayOrderAndAllSourceValues() throws IOException {
        String first = scope("placement", "r1", "placement.txt", "a".repeat(64));
        String second = scope("rules", "r1", "rules.txt", "b".repeat(64));
        String original = proposal("D1", "Design", first, second);
        Path baseline = write(dir.resolve("baseline.json"), ledger(original));
        for (String changed : List.of(proposal("D1", "Design", second, first), original.replace("reply:D1", "reply:changed"))) {
            DossierReport report = DossierValidator.check(dossier(dir, changed), baseline);
            error(report, "HISTORY_CHANGED");
            assertThat(report.historyStatus()).isEqualTo("changed");
        }
    }

    @Test
    void invalidBaselineIdentitySchemaOrSyntaxNeverMeansVerifiedHistory() throws IOException {
        Path current = dossier(dir, event("N1", "next-action", "\"text\":\"Review\""));
        String valid = Files.readString(current);
        for (String invalid : List.of(valid.replace("M-1", "M-2"),
                valid.replace("\"formatVersion\":1", "\"formatVersion\":2"),
                valid.replace("\"events\":", "\"unknown\":true,\"events\":"),
                valid.replace("\"text\":\"Review\"", "\"extra\":true,\"text\":\"Review\""),
                "{", "{\"formatVersion\":1,\"formatVersion\":1,\"migrationId\":\"M-1\",\"events\":[]}")) {
            Path baseline = write(dir.resolve("baseline.json"), invalid);
            DossierReport report = DossierValidator.check(current, baseline);
            error(report, "INVALID_BASELINE");
            assertThat(report.historyStatus()).isEqualTo("invalid-baseline");
        }
        DossierReport missing = DossierValidator.check(current, dir.resolve("missing-baseline.json"));
        error(missing, "INVALID_BASELINE");
        assertThat(missing.historyStatus()).isEqualTo("invalid-baseline");
    }

    @Test
    void absentBaselineIsExplicitlyNotCheckedIncludingTheNullOverload() throws IOException {
        Path current = dossier(dir);

        DossierReport report = DossierValidator.check(current);

        assertThat(report.valid()).isTrue();
        assertThat(report.historyStatus()).isEqualTo("not-checked");
        diagnostic(report, "HISTORY_NOT_CHECKED", "warning");
        assertThat(DossierValidator.check(current, null)).isEqualTo(report);
        assertThat(report.decisions()).isEmpty();
        assertThat(Json.mapper().readTree(Json.write(report)).path("decisions").isArray()).isTrue();
    }
}
