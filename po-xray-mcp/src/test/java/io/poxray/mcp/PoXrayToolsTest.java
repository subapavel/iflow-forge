package io.poxray.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/** Tool results stay compact inline JSON; a result above the client limit goes to a file as indented JSON. */
class PoXrayToolsTest {

    @Test
    void smallResultStaysInline(@TempDir Path dir) {
        assertThat(PoXrayTools.json("find_components", List.of("a"), dir)).isEqualTo("[\"a\"]");

        assertThat(dir).isEmptyDirectory();
    }

    @Test
    void oversizedResultIsWrittenToAFile(@TempDir Path dir) throws IOException {
        List<String> big = Collections.nCopies(10_000, "x".repeat(20));

        String out = PoXrayTools.json("search_objects", big, dir);

        assertThat(out.length()).isLessThan(2_000);
        JsonNode envelope = Json.mapper().readTree(out);
        assertThat(envelope.path("message").asString("")).contains("characters").contains("file");
        Path file = Path.of(envelope.path("file").asString(""));
        assertThat(file.getFileName().toString()).startsWith("search_objects-").endsWith(".json");
        assertThat(file).exists().hasParent(dir);
        JsonNode content = Json.mapper().readTree(Files.readString(file));
        assertThat(content.isArray()).isTrue();
        assertThat(content).hasSize(10_000);
    }
}
