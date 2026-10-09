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
}
