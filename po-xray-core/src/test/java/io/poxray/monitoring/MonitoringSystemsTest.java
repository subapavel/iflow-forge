package io.poxray.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** MonitoringSystems.resolve: --system, then POXRAY_SYSTEM, then system= in monitoring.properties (spec §5). */
class MonitoringSystemsTest {

    private static Path home(Path dir, String system) throws IOException {
        Path home = dir.resolve(".po-xray");
        Files.createDirectories(home);
        Files.writeString(home.resolve("monitoring.properties"), "# default system\nsystem=" + system + "\n",
                StandardCharsets.UTF_8);
        return home;
    }

    @Test
    void explicitWins(@TempDir Path dir) throws IOException {
        assertThat(MonitoringSystems.resolve("QA1", Map.of("POXRAY_SYSTEM", "QA2"), home(dir, "QA3")))
                .isEqualTo("QA1");
    }

    @Test
    void envNext(@TempDir Path dir) throws IOException {
        assertThat(MonitoringSystems.resolve(null, Map.of("POXRAY_SYSTEM", "QA2"), home(dir, "QA3")))
                .isEqualTo("QA2");
    }

    @Test
    void fileNext(@TempDir Path dir) throws IOException {
        assertThat(MonitoringSystems.resolve(null, Map.of(), home(dir, "QA3"))).isEqualTo("QA3");
    }

    @Test
    void blankExplicitAndEnvAreAbsent(@TempDir Path dir) throws IOException {
        assertThat(MonitoringSystems.resolve(" ", Map.of("POXRAY_SYSTEM", ""), home(dir, "QA3"))).isEqualTo("QA3");
    }

    @Test
    void noneNamesAllThree(@TempDir Path dir) {
        Path home = dir.resolve(".po-xray"); // does not exist

        assertThatThrownBy(() -> MonitoringSystems.resolve(null, Map.of(), home))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("--system")
                .hasMessageContaining("POXRAY_SYSTEM")
                .hasMessageContaining("monitoring.properties");
    }

    @Test
    void fileWithoutSystemNamesAllThree(@TempDir Path dir) throws IOException {
        Path home = dir.resolve(".po-xray");
        Files.createDirectories(home);
        Files.writeString(home.resolve("monitoring.properties"), "other=value\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> MonitoringSystems.resolve(null, Map.of(), home))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("--system")
                .hasMessageContaining("POXRAY_SYSTEM")
                .hasMessageContaining("monitoring.properties");
    }
}
