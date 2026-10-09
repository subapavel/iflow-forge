package io.poxray.model;

import java.util.List;

/** Service interface imported into the BPM DC ({@code rep/wsdl/*.services}). */
public record Interface(
        String id, String name, String namespace, String source, List<Operation> operations, List<Usage> usedBy) {

    /** {@code pattern}: in-out | in-only | ... (WSDL 2.0 message exchange pattern). */
    public record Operation(
            String id, String name, String pattern, QNameRef input, QNameRef output, List<Fault> faults) {
    }

    public record Fault(String id, String name, QNameRef element) {
    }

    /** {@code role}: outbound (automated activity) | start | intermediate (message trigger). */
    public record Usage(String process, Ref node, String role) {
    }
}
