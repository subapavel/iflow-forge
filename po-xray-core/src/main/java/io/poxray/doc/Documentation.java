package io.poxray.doc;

import io.poxray.model.IntegrationInventory;
import io.poxray.model.ScaInventory;
import java.util.Locale;

/** Generates the documentation of an SCA inventory in Markdown or Jira wiki markup. */
public final class Documentation {

    /** Default document language: Czech (client documentation). */
    public static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("cs");

    private Documentation() {
    }

    public static String render(ScaInventory inventory, DocFormat format) {
        return render(inventory, format, DEFAULT_LOCALE);
    }

    public static String render(ScaInventory inventory, DocFormat format, Locale locale) {
        return format.writer().write(DocumentBuilder.build(inventory, locale));
    }

    public static String render(IntegrationInventory inventory, DocFormat format, Locale locale) {
        return format.writer().write(IntegrationDocumentBuilder.build(inventory, locale));
    }
}
