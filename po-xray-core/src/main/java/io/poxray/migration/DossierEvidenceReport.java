package io.poxray.migration;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Local byte integrity and attributed review, never proof of source authenticity or execution. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DossierEvidenceReport(List<Packet> packets, List<Use> uses) {
    public DossierEvidenceReport { packets = copy(packets); uses = copy(uses); }
    private static <T> List<T> copy(List<T> values) { return values == null ? List.of() : List.copyOf(values); }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Packet(String eventId, String packetPath, String packetSha256, String reviewPath,
            String reviewSha256, PacketDocument packet, ReviewDocument review, String state, List<String> reasons) {
        public Packet { reasons = copy(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PacketDocument(int formatVersion, String id, String author, String sourceKind, String sourceLocator,
            String sourceSha256, String observedAt, Versions versions, String limitations, String kbDifference, List<Claim> claims) {
        public PacketDocument { claims = copy(claims); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Versions(String exportLabel, String packageRelease, String bundleVersion, String targetVersion,
            String designTimeVersion, String deployedVersion) { }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Claim(String id, String text, List<DossierReport.Dependency> scope, String limitations) {
        public Claim { scope = copy(scope); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReviewDocument(int formatVersion, String packetId, String packetSha256, String reviewer,
            String reviewedAt, List<ReviewClaim> claims) {
        public ReviewDocument { claims = copy(claims); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ReviewClaim(String claimId, List<DossierReport.Dependency> scope, String verdict, String limitations) {
        public ReviewClaim { scope = copy(scope); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Use(String eventId, DossierReport.Dependency scope, String evidenceEventId, String claimId,
            String text, String replaces, String state, List<String> reasons) {
        public Use { reasons = copy(reasons); }
    }
}
