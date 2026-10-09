package io.poxray;

import io.poxray.bpm.BpmReader;
import io.poxray.model.BpmComponent;
import io.poxray.model.DevelopmentComponent;
import io.poxray.model.ScaInventory;
import io.poxray.model.UnmappedElement;
import io.poxray.sca.SapManifest;
import io.poxray.sca.ScaArchive;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Library entry point: reads an NWDI SCA into the {@link ScaInventory} output model. */
public final class PoXray {

    public static final String NAME = "po-xray";
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private PoXray() {
    }

    public static ScaInventory read(Path scaFile) {
        ScaArchive sca = ScaArchive.open(scaFile);
        IssueLog issues = new IssueLog();
        List<UnmappedElement> unmapped = new ArrayList<>();
        List<DevelopmentComponent> dcs = new ArrayList<>();
        List<BpmComponent> bpm = new ArrayList<>();

        for (ScaArchive.DcSource dc : sca.developmentComponents()) {
            var def = dc.definition();
            dcs.add(new DevelopmentComponent(def.name(), def.vendor(), def.caption(), def.type(),
                    new DevelopmentComponent.Archives(dc.sourceEntry(), dc.deployEntry()),
                    def.dependencies(),
                    dc.deployEntry() == null ? List.of() : sca.manifest().deployDependencies(dc.deployEntry())));
            if (BpmReader.isBpm(dc)) {
                bpm.add(BpmReader.read(sca, dc, issues, unmapped));
            }
        }

        SapManifest mf = sca.manifest();
        return new ScaInventory(
                ScaInventory.SCHEMA_VERSION,
                new ScaInventory.Generator(NAME, VERSION, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()),
                new ScaInventory.ScaSource(scaFile.getFileName().toString(), sha256(scaFile),
                        new ScaInventory.SoftwareComponent(mf.main("keyname"), mf.main("keyvendor"),
                                mf.main("pr_release"), mf.main("pr_servicelevel"), mf.main("pr_patchlevel"),
                                mf.main("pr_updateversion"), mf.main("keylocation"))),
                dcs, bpm, issues.issues(), unmapped);
    }

    static String sha256(Path file) {
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
