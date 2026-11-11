package io.poxray.monitoring;

/**
 * The answer of an operation whose {@code Response} is one string ({@code getPredecessorMessageId}) from the system
 * named {@code system}: the value as received, null when the service answers nil, and the SOAP answer as received.
 */
public record ValueResult(String system, String value, String rawResponse) {

    /** Reads the {@code OP:Response} element of a string answer. */
    static ValueResult read(String system, MonitoringClient.Reply reply) {
        return new ValueResult(system, Soap.nil(reply.response()) ? null : reply.response().getTextContent(),
                reply.raw());
    }
}
