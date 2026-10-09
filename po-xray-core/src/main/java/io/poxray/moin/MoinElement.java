package io.poxray.moin;

import java.util.List;
import java.util.Map;

/**
 * One element of a MOIN partition (a top-level child carrying {@code m:i}).
 *
 * @param id          MOF ID ({@code m:i})
 * @param metaclass   qualified metaclass, e.g. {@code Galaxy.Workflow.AutomatedActivity}
 * @param attributes  plain XML attributes (MOIN-internal {@code m:*} attributes excluded)
 * @param texts       translatable texts: child tag to {@code originalText} (name, documentation, ...)
 * @param values      multi-valued attributes: child tag to list of {@code v} values, in file order
 * @param properties  {@code <properties key value/>} children (PropertyBag), in file order
 * @param partition   path of the partition inside its archive
 */
public record MoinElement(
        String id,
        String metaclass,
        Map<String, String> attributes,
        Map<String, String> texts,
        Map<String, List<String>> values,
        Map<String, String> properties,
        String partition) {

    public String attr(String name) {
        return attributes.get(name);
    }

    public String text(String name) {
        return texts.get(name);
    }

    /** Name from the translatable {@code name} text, falling back to the {@code name} attribute. */
    public String name() {
        String n = texts.get("name");
        return n != null ? n : attributes.get("name");
    }

    /** Metaclass without package, e.g. {@code AutomatedActivity}. */
    public String simpleType() {
        return metaclass.substring(metaclass.lastIndexOf('.') + 1);
    }

    public boolean is(String metaclass) {
        return this.metaclass.equals(metaclass);
    }
}
