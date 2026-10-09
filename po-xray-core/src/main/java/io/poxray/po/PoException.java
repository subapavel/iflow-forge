package io.poxray.po;

/** Error talking to a PO system (configuration, authentication, query error reported by the server). */
public class PoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PoException(String message) {
        super(message);
    }

    public PoException(String message, Throwable cause) {
        super(message, cause);
    }
}
