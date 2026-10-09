package io.poxray.sca;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An NWDI software component archive (SCA): {@code SOURCEARCHIVES/*.dcsa}, {@code DEPLOYARCHIVES/*.sda|*.ear},
 * {@code META-INF/SAP_MANIFEST.MF}.
 */
public final class ScaArchive {

    public static final String SOURCE_DIR = "SOURCEARCHIVES/";
    public static final String DEPLOY_DIR = "DEPLOYARCHIVES/";

    private final Path file;
    private final ZipArchive zip;
    private final SapManifest manifest;

    private ScaArchive(Path file, ZipArchive zip, SapManifest manifest) {
        this.file = file;
        this.zip = zip;
        this.manifest = manifest;
    }

    public static ScaArchive open(Path file) {
        ZipArchive zip = ZipArchive.open(file);
        byte[] mf = zip.get("META-INF/SAP_MANIFEST.MF")
                .orElseThrow(() -> new IllegalArgumentException("Not an SCA (no META-INF/SAP_MANIFEST.MF): " + file));
        return new ScaArchive(file, zip, SapManifest.parse(mf));
    }

    public Path file() {
        return file;
    }

    public ZipArchive zip() {
        return zip;
    }

    public SapManifest manifest() {
        return manifest;
    }

    /** One entry per DC source archive, in archive order. */
    public List<DcSource> developmentComponents() {
        List<DcSource> out = new ArrayList<>();
        for (var e : zip.entries().entrySet()) {
            if (e.getKey().startsWith(SOURCE_DIR) && e.getKey().endsWith(".dcsa")) {
                ZipArchive dcsa = ZipArchive.of(e.getValue(), e.getKey());
                byte[] dcdef = dcsa.get(".dcdef")
                        .orElseThrow(() -> new IllegalArgumentException("DC source archive without .dcdef: " + e.getKey()));
                DcDefinition def = DcDefinition.parse(dcdef, e.getKey() + "!/.dcdef");
                out.add(new DcSource(e.getKey(), dcsa, def, deployArchiveOf(e.getKey()).orElse(null)));
            }
        }
        return out;
    }

    /**
     * Deploy archive belonging to a source archive. Naming convention:
     * {@code SOURCEARCHIVES/<vendor>~<name~>~.dcsa} and {@code DEPLOYARCHIVES/<vendor>~<name~>~<...>.sda|.ear}.
     */
    private Optional<String> deployArchiveOf(String sourceEntry) {
        String prefix = DEPLOY_DIR + sourceEntry.substring(SOURCE_DIR.length(), sourceEntry.length() - ".dcsa".length());
        return zip.entries().keySet().stream().filter(k -> k.startsWith(prefix)).findFirst();
    }

    /**
     * A DC: its source archive (loaded), parsed .dcdef and the path of its deploy archive (may be null).
     */
    public record DcSource(String sourceEntry, ZipArchive sources, DcDefinition definition, String deployEntry) {
    }
}
