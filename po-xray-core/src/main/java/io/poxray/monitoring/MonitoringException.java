package io.poxray.monitoring;

import io.poxray.po.PoException;

/** Error from the AdapterMessageMonitoring service: connection, authentication, SOAP fault, unexpected answer. */
public class MonitoringException extends PoException {

    private static final long serialVersionUID = 1L;

    public MonitoringException(String message) {
        super(message);
    }

    public MonitoringException(String message, Throwable cause) {
        super(message, cause);
    }
}
