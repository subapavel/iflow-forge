package io.poxray.migration;

import io.poxray.model.BehaviorReport.*;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static io.poxray.migration.Reports.list;

/** Identity, association and direction are deliberately independent evidence dimensions. */
final class BehaviorChannels {
    static Integration integration(IntegrationInventory raw, String label, boolean unavailable) {
        if (raw == null) return new Integration(label, null, unavailable ? "unavailable" : "absent", "unknown",
                null, null, List.of(), List.of(), List.of(), List.of(), List.of(unavailable ? "ico-unavailable" : "ico-absent"));
        String availability = !BehaviorInputs.supported(raw.schemaVersion()) ? "unsupported-schema"
                : raw.icos() == null ? "missing-icos" : "available";
        String selection = raw.query() != null && (nonblank(raw.query().component()) || nonblank(raw.query().direction()))
                ? "query-limited" : list(raw.issues()).stream().anyMatch(Objects::nonNull) ? "source-issues" : "unknown";
        List<String> reasons = new ArrayList<>();
        if (raw.schemaVersion() == null) reasons.add("legacy-schema");
        if (!"available".equals(availability)) reasons.add(availability);
        if (raw.channels() == null) reasons.add("channels-unavailable");
        else if (raw.channels().isEmpty()) reasons.add("channels-observed-empty");
        List<IcoSource> icos = new ArrayList<>();
        for (Ico i : list(raw.icos())) icos.add(new IcoSource(icos.size() + 1, i, i == null ? List.of("missing-ico") : List.of()));
        List<ChannelSource> channels = new ArrayList<>();
        for (Channel c : list(raw.channels())) channels.add(new ChannelSource(channels.size() + 1, c, c == null ? List.of("missing-channel") : List.of()));
        List<ChannelUse> uses = new ArrayList<>();
        if ("available".equals(availability)) for (IcoSource source : icos) {
            Ico ico = source.configuration();
            Endpoint sender = ico == null ? null : ico.sender();
            add(uses, raw.channels(), source.ordinal(), null, null, "sender", null, null,
                    sender == null ? null : sender.channel(), sender == null ? null : sender.party(), sender == null ? null : sender.component());
            List<Receiver> receivers = ico == null ? List.of() : list(ico.receivers());
            for (int r = 0; r < receivers.size(); r++) {
                Receiver receiver = receivers.get(r);
                List<DirRef> refs = receiver == null ? List.of() : list(receiver.channels());
                if (refs.isEmpty()) add(uses, raw.channels(), source.ordinal(), r + 1, null, "receiver", null, null, null, null, null);
                else for (int c = 0; c < refs.size(); c++) add(uses, raw.channels(), source.ordinal(), r + 1, null, "receiver", null, c + 1,
                        refs.get(c), receiver.party(), receiver.component());
                List<InterfaceAssignment> assignments = receiver == null ? List.of() : list(receiver.interfaces());
                for (int a = 0; a < assignments.size(); a++) {
                    InterfaceAssignment assignment = assignments.get(a);
                    if (assignment == null || assignment.parameters() == null) continue;
                    int ref = 0;
                    for (var p : assignment.parameters().entrySet()) add(uses, raw.channels(), source.ordinal(), r + 1, a + 1,
                            "parameter", p.getKey(), ++ref, p.getValue(), null, null);
                }
            }
        }
        return new Integration(label, raw.schemaVersion(), availability, selection, raw.source(), raw.query(), icos, channels,
                uses, list(raw.issues()), reasons);
    }

    private static void add(List<ChannelUse> uses, List<Channel> channels, int ico, Integer receiver, Integer assignment,
            String role, String parameter, Integer reference, DirRef ref, String ownerParty, String ownerComponent) {
        List<Integer> candidates = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        boolean oid = ref != null && nonblank(ref.oid());
        boolean selector = ref != null && (oid || ref.party() != null || nonblank(ref.component()) || nonblank(ref.name()));
        boolean complete = ref != null && ref.party() != null && nonblank(ref.component()) && nonblank(ref.name());
        if (channels != null && selector) for (int c = 0; c < channels.size(); c++) {
            Channel channel = channels.get(c);
            if (channel == null || channel.id() == null) continue;
            DirRef id = channel.id();
            if (oid ? Objects.equals(ref.oid(), id.oid()) : compatible(ref.party(), id.party()) &&
                    compatible(ref.component(), id.component()) && compatible(ref.name(), id.name())) candidates.add(c + 1);
        }
        boolean conflict = oid && candidates.stream().anyMatch(c -> identityConflict(ref, channels.get(c - 1).id()));
        if (conflict) reasons.add("reference-identity-conflict");
        String resolution = channels == null ? "unavailable" : !selector || !oid && !complete ? "unknown"
                : candidates.isEmpty() ? "missing" : candidates.size() > 1 ? "ambiguous" : conflict ? "conflict" : "unique";
        if (channels == null) reasons.add("channels-unavailable");
        if (channels != null && channels.isEmpty()) reasons.add("observed-empty-catalogue");
        if (!selector) reasons.add("missing-channel-selector");
        else if (!oid && !complete) reasons.add("partial-channel-key");
        String association = "unknown", direction = "unknown";
        if ("parameter".equals(role)) { association = "not-transport"; direction = "not-applicable"; }
        else {
            if ("receiver".equals(role)) reasons.add("receiver-identity-projection");
            for (int candidate : candidates) {
                Channel c = channels.get(candidate - 1);
                if ("sender".equals(role) && ownerConflict(ownerParty, ownerComponent, c.id())) addReason(reasons, "candidate-owner-conflict");
                if (!"sender".equals(c.direction()) && !"receiver".equals(c.direction())) addReason(reasons, "candidate-direction-unknown");
                else if (!role.equals(c.direction())) addReason(reasons, "candidate-direction-conflict");
            }
            if ("unique".equals(resolution)) {
                Channel c = channels.get(candidates.getFirst() - 1);
                if ("sender".equals(role)) association = ownerConflict(ownerParty, ownerComponent, c.id()) ? "conflict"
                        : ownerParty != null && ownerComponent != null && c.id().party() != null && c.id().component() != null ? "compatible" : "unknown";
                if ("sender".equals(c.direction()) || "receiver".equals(c.direction())) direction = role.equals(c.direction()) ? "compatible" : "conflict";
            }
        }
        uses.add(new ChannelUse(uses.size() + 1, ico, receiver, assignment, role, parameter, reference, ref, candidates,
                resolution, association, direction, reasons));
    }
    private static boolean compatible(String provided, String value) { return provided == null || Objects.equals(provided, value); }
    private static boolean identityConflict(DirRef ref, DirRef id) {
        return !compatible(ref.party(), id.party()) || !compatible(ref.component(), id.component()) || !compatible(ref.name(), id.name());
    }
    private static boolean ownerConflict(String party, String component, DirRef id) {
        return party != null && id.party() != null && !party.equals(id.party())
                || component != null && id.component() != null && !component.equals(id.component());
    }
    static boolean nonblank(String value) { return value != null && !value.isBlank(); }
    static void addReason(List<String> reasons, String reason) { if (!reasons.contains(reason)) reasons.add(reason); }
    private BehaviorChannels() { }
}
