package io.poxray.model;

/** Message trigger or error (escalation) event definition ({@code src/triggers/*.eventdef}). */
public record EventDefinition(
        String id,
        String name,
        String kind,
        String documentation,
        Boolean isCritical,
        Boolean isTechnical,
        QNameRef parameter,
        Node.OperationRef operation,
        String source) {
}
