package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Caller-supplied provenance of one export (format version 1): explicit build and skill artifact files and attributed
 * version observations. The artifacts describe files the caller names, not a proven running binary or loaded skill;
 * the observations are retained verbatim and are never authenticated or inferred from one another.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExportProvenanceInput(int formatVersion, List<ArtifactInput> artifacts, List<VersionObservation> versions) {

    public ExportProvenanceInput {
        artifacts = copy(artifacts);
        versions = copy(versions);
    }

    /** {@code role} is {@code build} or {@code skill}; {@code path} is relative to the descriptor or absolute. */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ArtifactInput(String role, String label, String path) {
    }

    /**
     * One attributed version statement. {@code role}: export-label, package-release, bundle-version, target,
     * design-time, deployed, legacy-installed. {@code sourceKind}: export, documentation, derived, owner-confirmed,
     * tenant-observed. {@code locator} is inert text, never followed.
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record VersionObservation(String role, String subject, String value, String sourceKind, String locator,
            String sourceSha256, String observedAt, String scope, String limitations) {
    }

    static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
