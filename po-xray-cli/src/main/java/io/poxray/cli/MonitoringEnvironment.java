package io.poxray.cli;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/** The process environment as the monitoring commands see it (POXRAY_SYSTEM); a seam replaced in tests. */
@ApplicationScoped
public class MonitoringEnvironment {

    /** The environment variables of the process. */
    public Map<String, String> env() {
        return System.getenv();
    }
}
