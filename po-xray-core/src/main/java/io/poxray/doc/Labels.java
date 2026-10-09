package io.poxray.doc;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/** Localised document labels ({@code labels.properties} = English, {@code labels_cs.properties} = Czech). */
final class Labels {

    private final ResourceBundle bundle;
    private final Locale locale;

    Labels(Locale locale) {
        this.locale = locale;
        this.bundle = ResourceBundle.getBundle("io.poxray.doc.labels", locale,
                ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    String get(String key, Object... args) {
        String pattern;
        try {
            pattern = bundle.getString(key);
        } catch (MissingResourceException e) {
            pattern = key;
        }
        return args.length == 0 ? pattern : new MessageFormat(pattern, locale).format(args);
    }
}
