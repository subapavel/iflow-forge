package io.poxray.doc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * Localised document labels: {@code labels.properties} (English, base) overlaid by {@code labels_<lang>.properties}.
 * Loaded as plain UTF-8 resources (no {@code ResourceBundle}), so it works unchanged in native images.
 */
final class Labels {

    private static final String BASE = "labels";

    private final Properties labels = new Properties();
    private final Locale locale;

    Labels(Locale locale) {
        this.locale = locale;
        load(BASE + ".properties", true);
        if (!locale.getLanguage().isEmpty()) {
            load(BASE + "_" + locale.getLanguage() + ".properties", false);
        }
    }

    String get(String key, Object... args) {
        String pattern = labels.getProperty(key, key);
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }

    private void load(String resource, boolean required) {
        try (InputStream in = Labels.class.getResourceAsStream(resource)) {
            if (in == null) {
                if (required) {
                    throw new IllegalStateException("Missing resource io/poxray/doc/" + resource);
                }
                return;
            }
            labels.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + resource, e);
        }
    }
}
