package io.poxray.model;

/** A problem found in the source (never fixed by the reader) or a reader limitation. */
public record Issue(String severity, String code, String message, Location location, String source) {

    public static final String ERROR = "error";
    public static final String WARNING = "warning";
    public static final String INFO = "info";

    public record Location(String dc, String process, Ref element, String path) {
    }
}
