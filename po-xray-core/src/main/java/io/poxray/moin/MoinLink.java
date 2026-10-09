package io.poxray.moin;

/**
 * One association link {@code <L f=.. s=../>}. IDs are normalised (the {@code local:} prefix is stripped).
 *
 * @param association qualified association name, e.g. {@code Galaxy.Workflow.SourceOfIncomingConnectors}
 * @param first       first end ({@code f})
 * @param second      second end ({@code s})
 */
public record MoinLink(String association, String first, String second) {
}
