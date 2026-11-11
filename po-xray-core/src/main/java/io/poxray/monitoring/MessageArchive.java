package io.poxray.monitoring;

import io.poxray.json.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Saves what a monitoring command read about one message (spec §7), only on request ({@code --save} /
 * {@code saveDir}): {@code <dir>/<message>/response.xml} (the SOAP answer as received), {@code model.json} (the
 * parsed result, {@link Json#write}) and {@code payload.bin} (the payload bytes, when given). The target is checked by
 * {@link SaveTarget#check} before anything is written; nothing is written outside {@code <dir>}.
 */
public final class MessageArchive {

    private MessageArchive() {
    }

    /**
     * Writes the files and returns the message directory. Its name is {@code messageId} with every character outside
     * {@code [A-Za-z0-9._-]} replaced by {@code _}; an empty ID or one of only dots is a {@link MonitoringException}.
     * Existing files of the same message are replaced.
     */
    public static Path save(Path dir, String messageId, Object model, String rawResponse, byte[] payload) {
        Path target = SaveTarget.check(dir);
        String name = messageId == null ? "" : messageId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            throw new MonitoringException("Cannot save message '" + messageId + "': the ID is not usable as a"
                    + " directory name");
        }
        Path messageDir = target.resolve(name);
        if (Files.isSymbolicLink(messageDir)) {
            throw new MonitoringException("Cannot save message '" + messageId + "': " + messageDir
                    + " is a symbolic link");
        }
        try {
            Files.createDirectories(messageDir);
            Files.writeString(messageDir.resolve("response.xml"), rawResponse == null ? "" : rawResponse,
                    StandardCharsets.UTF_8);
            Files.writeString(messageDir.resolve("model.json"), Json.write(model), StandardCharsets.UTF_8);
            if (payload != null) {
                Files.write(messageDir.resolve("payload.bin"), payload);
            }
        } catch (IOException e) {
            throw new MonitoringException("Cannot save message '" + messageId + "' to " + messageDir + ": "
                    + e.getMessage(), e);
        }
        return messageDir;
    }
}
