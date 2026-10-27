package io.poxray.migration;

import io.poxray.migration.ExportMetadataPaths.State;
import io.poxray.migration.ExportMetadataPaths.Walk;
import io.poxray.model.ExportManifest;
import io.poxray.model.ExportManifest.FileDigest;
import io.poxray.model.ExportProvenanceInput;
import io.poxray.model.ExportProvenanceInput.ArtifactInput;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The optional export provenance sidecar: a strict descriptor read ({@link #read}), an overwrite-safety preflight of
 * the planned destinations ({@link #preflight}) and the manifest of an export ({@link #create}). Supplied artifacts
 * are caller-named files measured as they are, never a proven running binary or loaded skill; nothing is scanned
 * automatically. Protects existing input files against the planned outputs, not against concurrent file system changes.
 */
public final class ExportMetadata {

    static final String ARTIFACT_UNAVAILABLE = "EXPORT_ARTIFACT_UNAVAILABLE";
    static final String ROLE_MISSING = "EXPORT_ROLE_MISSING";

    /**
     * A descriptor as read once: its absolute normalized path, the SHA256 of the exact bytes read and the typed input
     * parsed from those bytes. Its identity is attributed to the reader or caller; constructing one authenticates nothing.
     */
    public record Loaded(String descriptorPath, String descriptorSha256, ExportProvenanceInput input) {
        public Loaded {
            Objects.requireNonNull(descriptorPath, "descriptorPath");
            Objects.requireNonNull(input, "input");
        }
    }

    private ExportMetadata() {
    }

    /**
     * Reads {@code descriptor} strictly. The path is walked without following links before any byte is read; a link
     * or reparse component, a missing or non-regular file, a UNC path, malformed JSON or content outside format
     * version 1 is an IOException.
     */
    public static Loaded read(Path descriptor) throws IOException {
        Path file = ExportMetadataPaths.local(descriptor, "Provenance descriptor");
        ExportMetadataPaths.requireRegular(file, "Provenance descriptor");
        byte[] bytes = Files.readAllBytes(file);
        ExportProvenanceInput input = ExportMetadataDescriptor.parse(bytes, file);
        return new Loaded(file.toString(), DossierReader.sha256(bytes), input);
    }

    /**
     * Checks, before the SCA is read or anything is written, that the planned {@code destinations} cannot overwrite
     * the source SCA, the descriptor or a supplied artifact, nor each other: lexically (absolute normalized paths) and,
     * for existing link-free regular files, by {@link Files#isSameFile}. The source and the descriptor must be
     * link-free regular files; destination directories and existing destinations must be link-free and an existing
     * destination a regular file. When a supplied artifact is reached through a link (alias unresolved), no
     * destination may exist. Inputs may alias each other. Creates nothing.
     */
    public static void preflight(Path source, Loaded descriptor, List<Path> destinations) throws IOException {
        Objects.requireNonNull(descriptor, "descriptor");
        Path sca = ExportMetadataPaths.local(source, "SCA source");
        ExportMetadataPaths.requireRegular(sca, "SCA source");
        Path descriptorFile = descriptorFile(descriptor);
        ExportMetadataPaths.requireRegular(descriptorFile, "Provenance descriptor");

        List<Path> inputs = new ArrayList<>(List.of(sca, descriptorFile));
        List<Path> regularInputs = new ArrayList<>(List.of(sca, descriptorFile));
        boolean aliasUnresolved = false;
        for (ArtifactInput artifact : descriptor.input().artifacts()) {
            Path path = ExportMetadataPaths.resolve(descriptorFile, artifact.path());
            inputs.add(path);
            State state = ExportMetadataPaths.walk(path).state();
            if (state == State.REGULAR) {
                regularInputs.add(path);
            } else if (state == State.LINKED || state == State.FAILED) {
                aliasUnresolved = true;
            }
        }

        List<Path> planned = new ArrayList<>();
        List<Path> existing = new ArrayList<>();
        for (Path destination : destinations) {
            Path path = ExportMetadataPaths.local(destination, "Output destination");
            if (planned.contains(path)) {
                throw new IOException("Two planned outputs share the path " + path);
            }
            if (inputs.contains(path)) {
                throw new IOException("Planned output " + path + " is an input of the export (source, descriptor or "
                        + "supplied artifact)");
            }
            planned.add(path);
            Walk walk = ExportMetadataPaths.walk(path);
            switch (walk.state()) {
                case REGULAR -> existing.add(path);
                case MISSING -> {
                }
                default -> throw new IOException("Output destination " + path + " " + walk.cause());
            }
        }
        if (aliasUnresolved && !existing.isEmpty()) {
            throw new IOException("A supplied artifact is reached through a link or cannot be inspected, so its "
                    + "identity cannot be compared; the existing output " + existing.get(0) + " is not overwritten");
        }
        for (int i = 0; i < existing.size(); i++) {
            Path output = existing.get(i);
            for (Path input : regularInputs) {
                if (Files.isSameFile(output, input)) {
                    throw new IOException("Existing output " + output + " is the same file as the input " + input);
                }
            }
            for (int j = i + 1; j < existing.size(); j++) {
                if (Files.isSameFile(output, existing.get(j))) {
                    throw new IOException("Existing outputs " + output + " and " + existing.get(j)
                            + " are the same file");
                }
            }
        }
    }

    /**
     * The manifest of an export whose {@code writtenOutputs} exist now: inventory generator and source verbatim, the
     * creation time sampled here, the descriptor's read-time digest (never re-read), the outputs and supplied artifacts
     * measured now. A missing or non-regular output is an IOException; an unavailable supplied artifact or a missing
     * build/skill role is a warning in the manifest. Writes nothing.
     */
    public static ExportManifest create(ScaInventory inventory, Loaded descriptor, List<Path> writtenOutputs)
            throws IOException {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(descriptor, "descriptor");
        String createdAt = Reports.timestamp();
        Path descriptorFile = descriptorFile(descriptor);
        FileDigest descriptorDigest = new FileDigest("descriptor", descriptorFile.getFileName().toString(),
                descriptorFile.toString(), descriptor.descriptorSha256(), "read-time-snapshot");

        List<FileDigest> outputs = new ArrayList<>();
        for (Path output : List.copyOf(writtenOutputs)) {
            Path path = output.toAbsolutePath().normalize();
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Written output " + path + " is missing or not a regular file");
            }
            outputs.add(new FileDigest("output", path.getFileName().toString(), path.toString(),
                    DossierReader.sha256(Files.readAllBytes(path)), "measured"));
        }

        List<FileDigest> supplied = new ArrayList<>();
        List<Issue> issues = new ArrayList<>();
        List<ArtifactInput> artifacts = descriptor.input().artifacts();
        for (ArtifactInput artifact : artifacts) {
            Path path = ExportMetadataPaths.resolve(descriptorFile, artifact.path());
            Walk walk = ExportMetadataPaths.walk(path);
            String cause = walk.cause();
            if (walk.state() == State.REGULAR) {
                try {
                    supplied.add(new FileDigest(artifact.role(), artifact.label(), path.toString(),
                            DossierReader.sha256(Files.readAllBytes(path)), "measured"));
                    continue;
                } catch (IOException e) {
                    cause = "cannot be read (" + e.getClass().getSimpleName() + ")";
                }
            }
            supplied.add(new FileDigest(artifact.role(), artifact.label(), path.toString(), null, "unavailable"));
            issues.add(new Issue(Issue.WARNING, ARTIFACT_UNAVAILABLE, "Supplied " + artifact.role() + " artifact \""
                    + artifact.label() + "\" " + cause + "; its digest is unknown", null, path.toString()));
        }
        for (String role : ExportMetadataDescriptor.ARTIFACT_ROLES) {
            if (artifacts.stream().noneMatch(a -> a.role().equals(role))) {
                issues.add(new Issue(Issue.WARNING, ROLE_MISSING, "No " + role + " artifact was supplied; its "
                        + "identity is not recorded", null, null));
            }
        }
        return new ExportManifest(1, inventory.generator(), createdAt, inventory.source(), descriptorDigest, outputs,
                supplied, descriptor.input().versions(), issues);
    }

    private static Path descriptorFile(Loaded descriptor) throws IOException {
        try {
            return ExportMetadataPaths.local(Path.of(descriptor.descriptorPath()), "Provenance descriptor");
        } catch (java.nio.file.InvalidPathException e) {
            throw new IOException("Provenance descriptor path is malformed", e);
        }
    }
}
