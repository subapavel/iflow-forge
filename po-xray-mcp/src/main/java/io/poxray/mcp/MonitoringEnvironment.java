package io.poxray.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;

/** The process environment as the message tools see it (POXRAY_SYSTEM, credentials); a seam replaced in tests. */
@ApplicationScoped
public class MonitoringEnvironment {

    /** The environment variables of the process. */
    public Map<String, String> env() {
        return System.getenv();
    }
}
