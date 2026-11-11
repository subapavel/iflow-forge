package io.poxray.monitoring;

/** An integration flow known to the monitoring service (F-17 Appendix C: name, description, id; namespace WS). */
public record IntegrationFlow(String id, String name, String description) {
}
