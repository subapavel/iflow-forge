package io.poxray.moin;

public class MoinException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MoinException(String message, Throwable cause) {
        super(message, cause);
    }
}
