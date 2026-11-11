package io.poxray.monitoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.json.Json;
import io.poxray.migration.ExportMetadataFixtures;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** MessageArchive.save: response.xml, model.json and payload.bin per message, behind SaveTarget.check. */
class MessageArchiveTest {

    private static final String RAW = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><S:Envelope "
            + "xmlns:S=\"http://schemas.xmlsoap.org/soap/envelope/\"><S:Body><greeting>Grüße</greeting></S:Body>"
            + "</S:Envelope>";
    private static final byte[] PAYLOAD = {0, 1, 2, (byte) 0xFF, '<', 'a', '/', '>'};

    private static ValueResult model() {
        return new ValueResult("QA1", "msg-0000", RAW);
    }

    @Test
    void writesThreeFiles(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("archive");

        Path dir = MessageArchive.save(out, "msg-0001", model(), RAW, PAYLOAD);

        assertThat(dir).isEqualTo(out.resolve("msg-0001").toAbsolutePath().normalize());
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("response.xml", "model.json", "payload.bin");
        }
        assertThat(Files.readString(dir.resolve("response.xml"), StandardCharsets.UTF_8)).isEqualTo(RAW);
        assertThat(Files.readString(dir.resolve("model.json"), StandardCharsets.UTF_8).strip())
                .isEqualTo(Json.write(model()).strip());
        assertThat(Files.readAllBytes(dir.resolve("payload.bin"))).isEqualTo(PAYLOAD);
    }

    @Test
    void withoutPayloadWritesNoPayloadFile(@TempDir Path tmp) throws IOException {
        Path dir = MessageArchive.save(tmp.resolve("archive"), "msg-0002", model(), RAW, null);

        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("response.xml", "model.json");
        }
    }

    @Test
    void refusedTargetWritesNothing(@TempDir Path tmp) throws IOException {
        Path repo = tmp.resolve("repo");
        Files.createDirectories(repo.resolve(".git"));
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src").resolve("Existing.java"), "class Existing {}");
        Map<String, String> before = ExportMetadataFixtures.snapshot(repo);

        assertThatThrownBy(() -> MessageArchive.save(repo.resolve("src").resolve("out"), "msg-0001", model(), RAW,
                PAYLOAD))
                .isInstanceOf(MonitoringException.class)
                .hasMessageContaining("refusing to save customer data");

        assertThat(ExportMetadataFixtures.snapshot(repo)).isEqualTo(before);
        assertThat(repo.resolve("src").resolve("out")).doesNotExist();
    }

    @Test
    void messageIdWithPathSeparatorsStaysInsideTheTarget(@TempDir Path tmp) throws IOException {
        Path out = Files.createDirectories(tmp.resolve("archive"));
        Map<String, String> outsideBefore = ExportMetadataFixtures.snapshot(tmp);

        Path dir = MessageArchive.save(out, "../a/b\\c", model(), RAW, PAYLOAD);

        assertThat(dir).isEqualTo(out.resolve(".._a_b_c").toAbsolutePath().normalize());
        try (var entries = Files.list(out)) {
            assertThat(entries.map(p -> p.getFileName().toString()).toList()).containsExactly(".._a_b_c");
        }
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("response.xml", "model.json", "payload.bin");
        }
        // nothing outside <dir>: the snapshot of the temp dir differs only below archive/.._a_b_c
        Map<String, String> outsideAfter = new java.util.TreeMap<>(ExportMetadataFixtures.snapshot(tmp));
        outsideAfter.keySet().removeIf(k -> k.replace('\\', '/').startsWith("archive/.._a_b_c"));
        assertThat(outsideAfter).isEqualTo(new java.util.TreeMap<>(outsideBefore));
    }

    @Test
    void messageIdOfOnlyDotsIsRefused(@TempDir Path tmp) throws IOException {
        Path out = Files.createDirectories(tmp.resolve("archive"));
        Map<String, String> before = ExportMetadataFixtures.snapshot(tmp);

        assertThatThrownBy(() -> MessageArchive.save(out, "..", model(), RAW, PAYLOAD))
                .isInstanceOf(MonitoringException.class);

        assertThat(ExportMetadataFixtures.snapshot(tmp)).isEqualTo(before);
    }
}
