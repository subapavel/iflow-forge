package io.poxray.po;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The public PoSystem.load(name, directory, env): credentials from the given environment override the file. */
class PoSystemPublicLoadTest {

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static Path systems(Path dir) throws IOException {
        Files.writeString(dir.resolve("QA1.properties"),
                "url=http://127.0.0.1:1/\nuser=file-user\npassword=file-secret\n", StandardCharsets.UTF_8);
        return dir;
    }

    @Test
    void threeArgumentLoadIsPublic() throws NoSuchMethodException {
        Method load = PoSystem.class.getDeclaredMethod("load", String.class, Path.class, Map.class);

        assertThat(Modifier.isPublic(load.getModifiers())).as("PoSystem.load(String, Path, Map) is public").isTrue();
        assertThat(Modifier.isStatic(load.getModifiers())).isTrue();
    }

    @Test
    void systemSpecificEnvironmentOverridesTheFile(@TempDir Path dir) throws IOException {
        PoSystem s = PoSystem.load("QA1", systems(dir),
                Map.of("POXRAY_QA1_USER", "env-user", "POXRAY_QA1_PASSWORD", "env-secret"));

        assertThat(s.user()).isEqualTo("env-user");
        assertThat(PoHttp.authorization(s)).isEqualTo(basic("env-user", "env-secret"));
    }

    @Test
    void emptyEnvironmentLeavesTheFileValues(@TempDir Path dir) throws IOException {
        PoSystem s = PoSystem.load("QA1", systems(dir), Map.of());

        assertThat(s.user()).isEqualTo("file-user");
        assertThat(PoHttp.authorization(s)).isEqualTo(basic("file-user", "file-secret"));
    }
}
