package io.poxray.bpm;

import static io.poxray.SyntheticBpmSca.ARCHIVE;
import static io.poxray.SyntheticBpmSca.METADATA;
import static io.poxray.SyntheticBpmSca.PROCESS;
import static io.poxray.SyntheticBpmSca.link;
import static io.poxray.SyntheticBpmSca.partition;
import static io.poxray.bpm.BpmPathAnalysisTest.W;
import static io.poxray.bpm.BpmPathAnalysisTest.assertConnected;
import static io.poxray.bpm.BpmPathAnalysisTest.boundary;
import static io.poxray.bpm.BpmPathAnalysisTest.chain;
import static io.poxray.bpm.BpmPathAnalysisTest.evidence;
import static io.poxray.bpm.BpmPathAnalysisTest.issues;
import static io.poxray.bpm.BpmPathAnalysisTest.location;
import static io.poxray.bpm.BpmPathAnalysisTest.nested;
import static io.poxray.bpm.BpmPathAnalysisTest.paths;
import static io.poxray.bpm.BpmPathAnalysisTest.report;
import static io.poxray.bpm.BpmPathAnalysisTest.step;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import io.poxray.PoXray;
import io.poxray.SyntheticBpmSca;
import io.poxray.json.Json;
import io.poxray.model.BpmPathReport;
import io.poxray.model.BpmPathReport.Arc;
import io.poxray.model.BpmPathReport.Fact;
import io.poxray.model.BpmPathReport.ProcessPaths;
import io.poxray.model.BpmSourceEvidence;
import io.poxray.model.BpmSourceEvidence.Locator;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Independent post-implementation integrity checks of the public parsed-source contract. */
class BpmPathIntegrityTest {
    @TempDir Path dir;
    private int files;

    @Test
    void boundaryMembershipInAnotherPartitionStillConflictsWithOwnerScope() {
        Map<String, String> partitions = new LinkedHashMap<>();
        partitions.put(PROCESS, boundary().pool("passive", "false").xml());
        partitions.put(METADATA, partition(link(W + "ScopeContainsScopeObjects", "boundary", "passive")));
        ScaInventory inventory = read(partitions);
        BpmSourceEvidence.Link membership = sourceLink(inventory, W + "ScopeContainsScopeObjects", "boundary", "passive");
        assertThat(evidence(inventory).indexedPartitions()).containsExactly(PROCESS, METADATA);
        assertThat(membership.location()).isEqualTo(new Locator(ARCHIVE, METADATA, 1));
        assertThat(membership.first().candidates()).extracting(BpmSourceEvidence.Candidate::location)
                .containsExactly(location(inventory, "boundary"));
        assertThat(membership.second().candidates()).extracting(BpmSourceEvidence.Candidate::location)
                .containsExactly(location(inventory, "passive"));

        ProcessPaths p = paths(inventory);
        assertThat(p.coverage()).isEqualTo("incomplete");
        assertConnected(p, "middle", 1);
        assertThat(step(p, "end").connectivity()).isEqualTo("unknown");
        assertThat(step(p, "boundary").connectivity()).isEqualTo("unknown");
        assertThat(step(p, "boundary").witness()).isEmpty();
        assertThat(step(p, "boundary").scopes()).containsExactly(location(inventory, "passive"));
        assertThat(step(p, "boundary").reasons()).doesNotContain("boundary-scope-from-owner");
        List<Arc> attachments = p.arcs().stream().filter(a -> "boundary-attachment".equals(a.kind())).toList();
        assertThat(attachments).hasSize(1);
        Arc attachment = attachments.getFirst();
        assertThat(attachment.resolution()).isEqualTo("invalid");
        assertThat(attachment.sources()).extracting(BpmPathReport.Endpoint::id).containsExactly("middle");
        assertThat(attachment.targets()).extracting(BpmPathReport.Endpoint::id).containsExactly("boundary");
        assertThat(attachment.evidence()).contains(new Fact("link",
                sourceLink(inventory, W + "ActivityHasBoundaryEvent", "boundary", "middle").location()));
    }

    @Test
    void malformedLoopMetadataOwnedByControlNodeOrScopePreventsDisconnectedVerdict() {
        for (String association : List.of("MILoopCharacteristicsContainsExpression",
                "MILoopCharacteristicsContainsCurrentCollectionItemDO", "MILoopCharacteristicsHasNumberOfCompletedIterationsDO")) {
            for (String owner : List.of("middle", "pool")) {
                String relatedType = association.endsWith("Expression") ? "Galaxy.Rule.Expression" : W + "DataObject";
                ScaInventory inventory = read(Map.of(PROCESS, chain().raw(relatedType, "related", "", "")
                        .relation(W + association, "related", owner).xml()));
                BpmSourceEvidence.Link source = sourceLink(inventory, W + association, "related", owner);
                assertThat(source.first().resolution()).isEqualTo("unique");
                assertThat(source.second().resolution()).isEqualTo("unique");
                assertThat(source.second().candidates()).extracting(BpmSourceEvidence.Candidate::location)
                        .containsExactly(location(inventory, owner));

                BpmPathReport report = report(inventory);
                ProcessPaths p = report.processes().getFirst();
                assertThat(p.coverage()).as(association + " owner=" + owner).isEqualTo("incomplete");
                assertConnected(p, "middle", 1);
                assertThat(step(p, "end").connectivity()).isEqualTo("unknown");
                assertThat(step(p, "end").witness()).isEmpty();
                assertThat(p.loops()).isEmpty();
                assertThat(issues(report, "BPM_PATH_UNSUPPORTED_CONTROL_RELATION")).hasSize(1);
                assertThat(Json.write(issues(report, "BPM_PATH_UNSUPPORTED_CONTROL_RELATION")))
                        .contains(association, "related", owner, PROCESS);
            }
        }
    }

    @Test
    void poolScopeMembersCannotEstablishRootAncestryIncludingRootInsideItsEmbeddedChild() {
        ScaInventory passiveMember = read(Map.of(PROCESS, chain().pool("nested-pool", "false")
                .node("StartEvent", "pool-start", "nested-pool").member("nested-pool", "pool").xml()));
        BpmSourceEvidence.Link nestedMembership = sourceLink(passiveMember, W + "ScopeContainsScopeObjects", "nested-pool", "pool");
        assertThat(nestedMembership.first().candidates()).extracting(BpmSourceEvidence.Candidate::metaclass)
                .containsExactly(W + "Pool");
        ProcessPaths nestedPool = paths(passiveMember);
        assertThat(nestedPool.coverage()).isEqualTo("incomplete");
        assertThat(step(nestedPool, "pool-start").connectivity()).isEqualTo("unknown");
        assertThat(step(nestedPool, "pool-start").witness()).isEmpty();
        assertThat(step(nestedPool, "end").connectivity()).isEqualTo("unknown");

        // This source is safe for the legacy reader: it recurses into EmbeddedScope members,
        // not Pool members. The root ancestry nevertheless forms pool -> host -> pool.
        ScaInventory rootInChild = read(Map.of(PROCESS, nested().member("pool", "host").xml()));
        BpmSourceEvidence.Link rootMembership = sourceLink(rootInChild, W + "ScopeContainsScopeObjects", "pool", "host");
        assertThat(rootMembership.first().candidates()).extracting(BpmSourceEvidence.Candidate::location)
                .containsExactly(location(rootInChild, "pool"));
        assertThat(rootMembership.second().candidates()).extracting(BpmSourceEvidence.Candidate::location)
                .containsExactly(location(rootInChild, "host"));
        ProcessPaths cyclicRoot = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> paths(rootInChild));
        assertThat(cyclicRoot.coverage()).isEqualTo("incomplete");
        assertThat(cyclicRoot.reasons()).isNotEmpty();
        for (String id : List.of("start", "host", "child-start", "child-end")) {
            assertThat(step(cyclicRoot, id).connectivity()).as(id).isEqualTo("unknown");
            assertThat(step(cyclicRoot, id).witness()).as(id).isEmpty();
        }
    }

    private ScaInventory read(Map<String, String> partitions) {
        return PoXray.read(SyntheticBpmSca.write(dir.resolve("integrity-" + (++files) + ".sca"), partitions));
    }

    private static BpmSourceEvidence.Link sourceLink(ScaInventory inventory, String association, String first, String second) {
        List<BpmSourceEvidence.Link> matches = evidence(inventory).links().stream().filter(l ->
                association.equals(l.association()) && first.equals(l.first().id()) && second.equals(l.second().id())).toList();
        assertThat(matches).as(association + " " + first + " -> " + second).hasSize(1);
        return matches.getFirst();
    }
}
