package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Output of {@code po-xray attributes}: the eleven D-025 scenario attributes of every ICO of one or more
 * {@link IntegrationInventory} files. A resolved {@link Attribute} names the input path its value came from; an open
 * one has value {@code ?}, the still possible candidates and a question. {@code issues} is always written (an empty
 * array means none were found).
 */
public record AttributesReport(
        String schemaVersion,
        ScaInventory.Generator generator,
        List<String> inputs,
        List<Scenario> scenarios,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<Issue> issues) {

    /** One ICO: {@code file} is the input as given, {@code modules} the exit and conversion modules of its channels. */
    public record Scenario(String file, IcoRef ico, List<Attribute> attributes, List<ModuleRef> modules) {
    }

    /** The scenario key: ICO identity, sender and receiver components. */
    public record IcoRef(String oid, String generatedFrom, String description, String senderComponent,
            String senderInterface, String senderNamespace, List<String> receiverComponents) {
    }

    /**
     * One attribute row. Resolved: {@code value} from the D-025 set, {@code source} = path into the input file,
     * {@code candidates} and {@code question} null. Open: {@code value} = {@code ?}, {@code candidates} in D-025 order,
     * {@code question} non-blank, {@code source} = the path looked at or null.
     */
    public record Attribute(String name, String value, String source, List<String> candidates, String question,
            String note) {
    }

    /** A module of a scenario channel: {@code side} sender | receiver, {@code name} as configured. */
    public record ModuleRef(String side, String channel, int position, String name) {
    }
}
