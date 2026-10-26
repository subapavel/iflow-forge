package io.poxray.migration;

import static io.poxray.SyntheticBehavior.withConfiguration;
import static io.poxray.migration.BehaviorAnalysisTest.analyze;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.SyntheticBehavior;
import io.poxray.SyntheticBehavior.Pair;
import io.poxray.model.BehaviorReport;
import io.poxray.model.BehaviorReport.ChannelSource;
import io.poxray.model.BehaviorReport.ChannelUse;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BehaviorChannelsTest {
    @TempDir Path dir;

    @Test
    void channelOidNeverFallsBackToName() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Channel channel = pair.icos().channels().getFirst();
        ChannelUse use = sender(pair, new DirRef("Party", "SOURCE", "SND", "absent-oid"), List.of(channel), "Party", "SOURCE");
        assertThat(use.resolution()).isEqualTo("missing");
        assertThat(use.candidates()).isEmpty();
        assertThat(use.association()).isEqualTo("unknown");
        assertThat(use.direction()).isEqualTo("unknown");
    }

    @Test
    void oidConflictAndDuplicateIdentityNeverSelectARepresentative() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Channel first = pair.icos().channels().getFirst();
        ChannelUse conflict = sender(pair, new DirRef("Other", "SOURCE", "SND", first.id().oid()), List.of(first), "Party", "SOURCE");
        assertThat(conflict.resolution()).isEqualTo("conflict");
        assertThat(conflict.candidates()).containsExactly(1);
        assertThat(conflict.association()).isEqualTo("unknown");
        assertThat(conflict.direction()).isEqualTo("unknown");
        Channel second = channel(first, new DirRef("Other", "OTHER", "different", first.id().oid()), "receiver");
        ChannelUse ambiguous = sender(pair, first.id(), Arrays.asList(first, null, second), "Party", "SOURCE");
        assertThat(ambiguous.resolution()).isEqualTo("ambiguous");
        assertThat(ambiguous.candidates()).containsExactly(1, 3);
        assertThat(ambiguous.association()).isEqualTo("unknown");
        assertThat(ambiguous.direction()).isEqualTo("unknown");
        assertThat(ambiguous.reasons()).contains("reference-identity-conflict", "candidate-owner-conflict", "candidate-direction-conflict");
    }

    @Test
    void partialAndSelectorlessKeysStayUnknownRegardlessOfCandidateCount() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Channel first = pair.icos().channels().getFirst();
        DirRef partial = new DirRef(null, "SOURCE", "SND", null);
        for (List<Channel> candidates : List.of(List.<Channel>of(), List.of(first), List.of(first, first))) {
            ChannelUse use = sender(pair, partial, candidates, "Party", "SOURCE");
            assertThat(use.resolution()).isEqualTo("unknown");
            assertThat(use.candidates()).hasSize(candidates.size());
            assertThat(use.association()).isEqualTo("unknown");
            assertThat(use.direction()).isEqualTo("unknown");
        }
        for (DirRef empty : Arrays.asList(null, new DirRef(null, null, null, null), new DirRef(null, "", "", " "))) {
            ChannelUse use = sender(pair, empty, List.of(first), "Party", "SOURCE");
            assertThat(use.resolution()).isEqualTo("unknown");
            assertThat(use.candidates()).isEmpty();
        }
    }

    @Test
    void emptyPartyIsACompleteKeyWhileNullPartyIsPartial() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Channel source = pair.icos().channels().getFirst();
        Channel emptyParty = channel(source, new DirRef("", "SOURCE", "SND", null), "sender");
        ChannelUse complete = sender(pair, emptyParty.id(), List.of(emptyParty), "", "SOURCE");
        assertThat(complete.resolution()).isEqualTo("unique");
        assertThat(complete.association()).isEqualTo("compatible");
        assertThat(complete.direction()).isEqualTo("compatible");
        assertThat(sender(pair, new DirRef(null, "SOURCE", "SND", null), List.of(emptyParty), "", "SOURCE").resolution()).isEqualTo("unknown");
        assertThat(sender(pair, new DirRef("Party", "SOURCE", "SND", null), List.of(emptyParty), "", "SOURCE").resolution()).isEqualTo("missing");
        assertThat(sender(pair, emptyParty.id(), List.of(emptyParty, emptyParty), "", "SOURCE").resolution()).isEqualTo("ambiguous");
        assertThat(sender(pair, emptyParty.id(), List.of(channel(source, null, "sender"), emptyParty), "", "SOURCE").candidates()).containsExactly(2);
    }

    @Test
    void unavailableAndObservedEmptyChannelCataloguesRemainDistinct() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        BehaviorReport unavailable = analyze(pair.sca(), withConfiguration(pair.icos(), pair.icos().icos(), null));
        assertThat(unavailable.integration().reasons()).contains("channels-unavailable");
        assertThat(unavailable.integration().channelUses()).isNotEmpty().allSatisfy(use -> {
            assertThat(use.resolution()).isEqualTo("unavailable");
            assertThat(use.candidates()).isEmpty();
            assertThat(use.association()).isEqualTo("parameter".equals(use.role()) ? "not-transport" : "unknown");
            assertThat(use.direction()).isEqualTo("parameter".equals(use.role()) ? "not-applicable" : "unknown");
        });
        BehaviorReport empty = analyze(pair.sca(), withConfiguration(pair.icos(), pair.icos().icos(), List.of()));
        assertThat(empty.integration().reasons()).contains("channels-observed-empty");
        assertThat(empty.integration().channelUses().getFirst().resolution()).isEqualTo("missing");
        ChannelUse absentRef = empty.integration().channelUses().stream().filter(u -> u.ico() == 2 && "sender".equals(u.role())).findFirst().orElseThrow();
        assertThat(absentRef.resolution()).isEqualTo("unknown");
        assertThat(absentRef.reasons()).contains("observed-empty-catalogue");
    }

    @Test
    void receiverOwnerProjectionNeverProvesCompatibilityOrConflict() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Ico ico = pair.icos().icos().getFirst();
        Receiver raw = ico.receivers().getFirst();
        for (String owner : List.of("BPM", "XP:/dynamic", "MISMATCH")) {
            Receiver projected = new Receiver("BPM".equals(owner) ? raw.party() : "DifferentParty", owner,
                    raw.condition(), raw.qualityOfService(), raw.interfaces(), raw.channels());
            Ico changed = new Ico(ico.oid(), ico.description(), ico.generatedFrom(), ico.sender(), List.of(projected), ico.dynamicRouting(),
                    ico.noReceiverBehaviour(), ico.modifiedBy(), ico.modifiedAt());
            BehaviorReport report = analyze(pair.sca(), withConfiguration(pair.icos(), List.of(changed), pair.icos().channels()));
            assertThat(report.integration().channelUses()).filteredOn(ChannelUse::role, "receiver").singleElement().satisfies(use -> {
                assertThat(use.source()).isEqualTo(raw.channels().getFirst());
                assertThat(use.resolution()).isEqualTo("unique");
                assertThat(use.association()).isEqualTo("unknown");
                assertThat(use.reasons()).contains("receiver-identity-projection");
                assertThat(use.direction()).isEqualTo("compatible");
            });
            assertThat(report.integration().icos().getFirst().configuration().receivers().getFirst().component()).isEqualTo(owner);
        }
        List<Channel> contrary = pair.icos().channels().stream().map(c -> "receiver-channel".equals(c.id().oid())
                ? channel(c, c.id(), "sender") : c).toList();
        BehaviorReport report = analyze(pair.sca(), withConfiguration(pair.icos(), pair.icos().icos(), contrary));
        assertThat(report.integration().channelUses()).filteredOn(u -> "receiver".equals(u.role()) && u.receiver() == 1 && u.ico() == 1)
                .singleElement().satisfies(use -> {
                    assertThat(use.direction()).isEqualTo("conflict");
                    assertThat(use.association()).isEqualTo("unknown");
                });
    }

    @Test
    void senderAssociationAndDirectionAreIndependentOfIdentity() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        Channel source = pair.icos().channels().getFirst();
        ChannelUse mismatch = sender(pair, source.id(), List.of(source), "Party", "DIFFERENT");
        assertThat(mismatch.resolution()).isEqualTo("unique");
        assertThat(mismatch.association()).isEqualTo("conflict");
        assertThat(mismatch.direction()).isEqualTo("compatible");
        assertThat(sender(pair, source.id(), List.of(source), null, "SOURCE").association()).isEqualTo("unknown");
        for (String direction : Arrays.asList(null, "future-direction", "receiver")) {
            ChannelUse use = sender(pair, source.id(), List.of(channel(source, source.id(), direction)), "Party", "SOURCE");
            assertThat(use.resolution()).isEqualTo("unique");
            assertThat(use.association()).isEqualTo("compatible");
            assertThat(use.direction()).isEqualTo("receiver".equals(direction) ? "conflict" : "unknown");
        }
        Channel unknown = channel(source, source.id(), null);
        ChannelUse multiple = sender(pair, source.id(), List.of(source, unknown), "Party", "SOURCE");
        assertThat(multiple.direction()).isEqualTo("unknown");
        assertThat(multiple.reasons()).contains("candidate-direction-unknown");
    }

    @Test
    void parameterChannelsAreNotDeliveryTransportsAndKeepMapOrder() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        BehaviorReport report = analyze(pair.sca(), pair.icos());
        List<ChannelUse> uses = report.integration().channelUses();
        assertThat(uses).extracting(ChannelUse::ordinal).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(uses).extracting(ChannelUse::role).containsExactly("sender", "receiver", "parameter", "parameter", "receiver", "sender", "receiver");
        assertThat(uses).filteredOn(ChannelUse::role, "parameter").extracting(ChannelUse::parameter).containsExactly("LOOKUP", "SECOND");
        assertThat(uses).filteredOn(ChannelUse::role, "parameter").extracting(ChannelUse::reference).containsExactly(1, 2);
        assertThat(uses).filteredOn(ChannelUse::role, "parameter").allSatisfy(use -> {
            assertThat(use.ico()).isEqualTo(1); assertThat(use.receiver()).isEqualTo(1); assertThat(use.assignment()).isEqualTo(1);
            assertThat(use.association()).isEqualTo("not-transport");
            assertThat(use.direction()).isEqualTo("not-applicable");
        });
    }

    @Test
    void originalConfigRetainsRoutingModulesMappingsAndHoles() throws IOException {
        Pair pair = new SyntheticBehavior().write(dir);
        List<Channel> holes = Arrays.asList(null, pair.icos().channels().getFirst(), null);
        BehaviorReport report = analyze(pair.sca(), withConfiguration(pair.icos(), pair.icos().icos(), holes));
        assertThat(report.integration().channels()).extracting(ChannelSource::ordinal).containsExactly(1, 2, 3);
        assertThat(report.integration().channels()).extracting(ChannelSource::configuration).containsExactlyElementsOf(holes);
        assertThat(report.integration().channels().getFirst().reasons()).contains("missing-channel");
        assertThat(report.integration().channelUses().getFirst().candidates()).containsExactly(2);
        Channel retained = report.integration().channels().get(1).configuration();
        assertThat(retained.attributes()).containsEntry("syntheticValue", "configured-value");
        assertThat(retained.modules()).singleElement().satisfies(module -> {
            assertThat(module.position()).isEqualTo(7);
            assertThat(module.name()).isEqualTo("SyntheticModule");
            assertThat(module.parameters()).containsEntry("option", "configured");
        });
    }

    static Channel channel(Channel c, DirRef id, String direction) {
        return new Channel(id, direction, c.adapter(), c.transportProtocol(), c.messageProtocol(), c.engine(), c.attributes(), c.modules(), c.description());
    }
    private static ChannelUse sender(Pair pair, DirRef ref, List<Channel> channels, String party, String component) {
        Ico original = pair.icos().icos().getFirst();
        Endpoint old = original.sender();
        Ico ico = new Ico(original.oid(), original.description(), original.generatedFrom(),
                new Endpoint(party, component, old.iface(), old.operations(), ref), List.of(), original.dynamicRouting(),
                original.noReceiverBehaviour(), original.modifiedBy(), original.modifiedAt());
        return analyze(pair.sca(), withConfiguration(pair.icos(), List.of(ico), channels)).integration().channelUses().getFirst();
    }
}
