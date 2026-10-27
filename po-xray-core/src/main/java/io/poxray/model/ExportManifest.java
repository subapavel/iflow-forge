package io.poxray.model;

import static io.poxray.model.ExportProvenanceInput.copy;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The export provenance sidecar (format version 1). {@code generator} and {@code source} are the inventory's own,
 * verbatim ({@code generator.readAt} stays the reading time); {@code outputCreatedAt} is the time the manifest was
 * generated, not a file system time. The descriptor digest is the snapshot taken when it was read; outputs and supplied
 * artifacts are measured when the manifest is created. Nothing here is authenticated: identities are caller-supplied.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ExportManifest(int formatVersion, ScaInventory.Generator generator, String outputCreatedAt,
        ScaInventory.ScaSource source, FileDigest descriptor, List<FileDigest> outputs,
        List<FileDigest> suppliedArtifacts, List<ExportProvenanceInput.VersionObservation> versions,
        List<Issue> issues) {

    public ExportManifest {
        outputs = copy(outputs);
        suppliedArtifacts = copy(suppliedArtifacts);
        versions = copy(versions);
        issues = copy(issues);
    }

    /**
     * One file: {@code path} resolved absolute; {@code state} {@code measured} (digest of the actual bytes),
     * {@code read-time-snapshot} (the descriptor) or {@code unavailable} ({@code sha256} null).
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record FileDigest(String role, String label, String path, String sha256, String state) {
    }
}
