package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.bpm.BpmPathAnalysisTest.W;
import static io.poxray.bpm.BpmPathAnalysisTest.assertConnected;
import static io.poxray.bpm.BpmPathAnalysisTest.chain;
import static io.poxray.bpm.BpmPathAnalysisTest.evidence;
import static io.poxray.bpm.BpmPathAnalysisTest.location;
import static io.poxray.bpm.BpmPathAnalysisTest.report;
import static io.poxray.bpm.BpmPathAnalysisTest.step;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.json.Json;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmPathReport.Step;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.Node;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Review regressions: malformed retained source must never silently prove disconnection. */
class BpmPathCompletenessTest {
    @TempDir Path dir;

    @Test
    void wrongKindCollaborationEndpointOnCurrentPoolMakesCoverageIncomplete() {
        ScaInventory inventory = read(chain().relation(W + "CollaborationContainsPools", "pool", "middle"));
        BpmSourceEvidence.Link malformed = sourceLink(inventory, W + "CollaborationContainsPools", "pool", "middle");
        assertThat(malformed.location().archive()).isEqualTo(ARCHIVE);
        assertThat(malformed.location().partition()).isEqualTo(PROCESS);
        assertThat(malformed.first().resolution()).isEqualTo("unique");
        assertThat(malformed.first().candidates()).extracting(BpmSourceEvidence.Candidate::metaclass)
                .containsExactly(W + "Pool");
        assertThat(malformed.second().resolution()).isEqualTo("unique");
        assertThat(malformed.second().candidates()).extracting(BpmSourceEvidence.Candidate::metaclass)
                .containsExactly(W + "MappingActivity");
        assertThat(malformed.second().candidates()).extracting(BpmSourceEvidence.Candidate::location)
                .containsExactly(location(inventory, "middle"));
        // The ordinary declaration still exists; the malformed extra relation must not disappear.
        assertThat(sourceLink(inventory, W + "CollaborationContainsPools", "pool", "collab").second().resolution())
                .isEqualTo("unique");

        BpmPathReport report = report(inventory);
        ProcessPaths p = report.processes().getFirst();
        assertThat(p.coverage()).isEqualTo("incomplete");
        assertThat(p.reasons()).isNotEmpty();
        assertThat(step(p, "end").connectivity()).isEqualTo("unknown");
        assertThat(step(p, "end").witness()).isEmpty();
        assertDiagnosticContext(report, "pool", "middle");
    }

    @Test
    void unlinkedPoolScopeMemberIsRetainedAsUnknownAndPreventsDisconnectedVerdict() {
        ScaInventory inventory = read(chain().raw(W + "Pool", "member-pool", "active='false'",
                "<name originalText='Unlinked member pool'/>").member("member-pool", "pool"));
        assertThat(evidence(inventory).links()).noneMatch(l -> l.association().equals(W + "CollaborationContainsPools")
                && "member-pool".equals(l.first().id()));
        assertUnknownScopeMember(inventory, "member-pool", W + "Pool");
    }

    @Test
    void collaborationScopeMemberIsRetainedAsUnknownAndPreventsDisconnectedVerdict() {
        ScaInventory inventory = read(chain().raw(W + "Collaboration", "member-collaboration", "",
                "<name originalText='Member collaboration'/>").member("member-collaboration", "pool"));
        assertThat(inventory.bpm().getFirst().processes()).hasSize(1);
        assertThat(inventory.bpm().getFirst().processes().getFirst().id()).isEqualTo("collab");
        assertUnknownScopeMember(inventory, "member-collaboration", W + "Collaboration");
    }

    private void assertUnknownScopeMember(ScaInventory inventory, String id, String metaclass) {
        BpmSourceEvidence.Link membership = sourceLink(inventory, W + "ScopeContainsScopeObjects", id, "pool");
        assertThat(membership.location().archive()).isEqualTo(ARCHIVE);
        assertThat(membership.location().partition()).isEqualTo(PROCESS);
        assertThat(membership.first().resolution()).isEqualTo("unique");
        assertThat(membership.first().candidates()).extracting(BpmSourceEvidence.Candidate::metaclass).containsExactly(metaclass);
        assertThat(membership.first().candidates()).extracting(BpmSourceEvidence.Candidate::location)
                .containsExactly(location(inventory, id));
        assertThat(membership.second().candidates()).extracting(BpmSourceEvidence.Candidate::location)
                .containsExactly(location(inventory, "pool"));
        List<Node> legacyNodes = inventory.bpm().getFirst().processes().getFirst().nodes().stream()
                .filter(n -> id.equals(n.id())).toList();
        assertThat(legacyNodes).hasSize(1);
        assertThat(legacyNodes.getFirst().kind()).isEqualTo("unknown");

        BpmPathReport report = report(inventory);
        ProcessPaths p = report.processes().getFirst();
        assertThat(p.coverage()).isEqualTo("incomplete");
        assertThat(p.reasons()).isNotEmpty();
        assertConnected(p, "middle", 1);
        assertThat(step(p, "end").connectivity()).isEqualTo("unknown");
        assertThat(step(p, "end").witness()).isEmpty();
        List<Step> retained = p.steps().stream().filter(s -> id.equals(s.id())).toList();
        assertThat(retained).hasSize(1);
        Step member = retained.getFirst();
        assertThat(member.kind()).isEqualTo("unknown");
        assertThat(member.location()).isEqualTo(location(inventory, id));
        assertThat(member.scopes()).containsExactly(location(inventory, "pool"));
        assertThat(member.connectivity()).isEqualTo("unknown");
        assertThat(member.witness()).isEmpty();
        assertThat(member.reasons()).isNotEmpty();
        assertDiagnosticContext(report, id);
    }

    private static void assertDiagnosticContext(BpmPathReport report, String... identifiers) {
        assertThat(report.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).startsWith("BPM_PATH_");
            assertThat(Json.write(issue)).contains(PROCESS).contains(identifiers);
        });
    }

    private ScaInventory read(BpmPathAnalysisTest.Fixture fixture) {
        return PoXray.read(SyntheticBpmSca.write(dir.resolve("completeness.sca"), Map.of(PROCESS, fixture.xml())));
    }

    private static BpmSourceEvidence.Link sourceLink(ScaInventory inventory, String association, String first, String second) {
        List<BpmSourceEvidence.Link> matches = evidence(inventory).links().stream().filter(l ->
                association.equals(l.association()) && first.equals(l.first().id()) && second.equals(l.second().id())).toList();
        assertThat(matches).as(association + " " + first + " -> " + second).hasSize(1);
        return matches.getFirst();
    }
}
