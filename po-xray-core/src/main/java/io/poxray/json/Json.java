package io.poxray.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON serialisation of the output model: pretty printed, declaration order of record components
 * (Jackson 3 sorts alphabetically by default), null/empty values omitted (map entries are kept as is).
 */
public final class Json {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
            .build();

    private Json() {
    }

    public static JsonMapper mapper() {
        return MAPPER;
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static void write(Object value, Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, write(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
