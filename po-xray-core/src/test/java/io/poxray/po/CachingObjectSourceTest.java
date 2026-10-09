package io.poxray.po;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.xi.XiRef;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CachingObjectSourceTest {

    private static final XiRef ICO = new XiRef("D", null, null, "AllInOne", "o1", List.of("", "SND", "urn:a", "If_Out", "", ""));

    @Test
    void listingFallsBackToSnapshotWhenSystemIsUnreachable(@TempDir Path dir) {
        CachingObjectSource source = new CachingObjectSource(null, dir, false);
        assertThat(source.list("AllInOne", () -> List.of(ICO))).containsExactly(ICO);
        assertThat(source.offlineNotes()).isEmpty();

        List<XiRef> offline = source.list("AllInOne", () -> {
            throw new PoException("SimpleQuery dir failed: cannot connect to po.example (ConnectException)");
        });

        assertThat(offline).containsExactly(ICO);
        assertThat(source.offlineNotes()).singleElement().asString()
                .contains("cannot connect to po.example", "snapshot of AllInOne");
    }

    @Test
    void authenticationErrorsAndMissingSnapshotsAreNotHidden(@TempDir Path dir) {
        CachingObjectSource source = new CachingObjectSource(null, dir, false);

        assertThatThrownBy(() -> source.list("Service", () -> {
            throw new PoException("SimpleQuery dir failed: cannot connect");
        })).isInstanceOf(PoException.class);
        source.list("Service", () -> List.of(ICO));
        assertThatThrownBy(() -> source.list("Service", () -> {
            throw new PoException("SimpleQuery dir: authentication failed for xipod (HTTP 401)");
        })).hasMessageContaining("authentication failed");
    }

    @Test
    void connectionErrorsWithoutMessageAreDescribed() {
        assertThat(PoClient.describe(new ConnectException())).isEqualTo("ConnectException");
        assertThat(PoClient.describe(new java.io.IOException(new IllegalStateException("boom"))))
                .isEqualTo("IOException: java.lang.IllegalStateException: boom");
    }

    @Test
    void changedObjectsAreReadAgain(@TempDir Path dir) throws java.io.IOException {
        String om = FakePo.xiObj("S", "MAPPING", "om1", List.of("OM_Order", "urn:a"), null, List.of());
        try (FakePo po = new FakePo()) {
            po.object("MAPPING", "OM_Order|urn:a", om);
            XiRef ref = new XiRef("S", "aaaa", null, "MAPPING", "om1", List.of("OM_Order", "urn:a"));
            new CachingObjectSource(new PoClient(po.system), dir, false).resolve(ref); // snapshot with VID v1
            po.query("rep", List.of("MAPPING"), "MODIFYDATE", "<queryResult><headerInfo><colDef>"
                    + "<def type=\"RA_XILINK\" pos=\"0\"/><def type=\"VERSIONID\" pos=\"1\"/></colDef></headerInfo>"
                    + "<matrix><r><c><qref>" + FakePo.ref("S", "MAPPING", "om1", "OM_Order", "urn:a") + "</qref></c>"
                    + "<c><simple><strg>v2</strg></simple></c></r></matrix></queryResult>");
            po.object("MAPPING", "OM_Order|urn:a", om.replace("VID=\"v1\"", "VID=\"v2\""));

            CachingObjectSource source = new CachingObjectSource(new PoClient(po.system), dir, false);
            assertThat(source.resolve(ref)).hasValueSatisfying(o -> assertThat(o.vid()).isEqualTo("v2"));
            assertThat(source.changedObjects()).isEqualTo(1);
            assertThat(java.nio.file.Files.readString(dir.resolve("sync.properties"))).contains("rep=");
            assertThat(po.requests).anySatisfy(r -> assertThat(r).contains("<key>MODIFYDATE</key>", "<date>"));
        }
    }
}
