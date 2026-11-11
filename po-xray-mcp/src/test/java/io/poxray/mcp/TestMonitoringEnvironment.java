package io.poxray.mcp;

import java.util.Map;

/**
 * The environment the monitoring tools see in tests: exactly the given variables, never the machine's
 * (POXRAY_SYSTEM, POXRAY_*_USER/PASSWORD). The MCP tests instantiate the tools directly (no Quarkus test runtime on
 * this module's test class path), so {@link MessageToolsTest} injects this instance into every
 * {@code MonitoringEnvironment} field.
 */
class TestMonitoringEnvironment extends MonitoringEnvironment {

    private final Map<String, String> variables;

    TestMonitoringEnvironment(Map<String, String> variables) {
        this.variables = Map.copyOf(variables);
    }

    @Override
    public Map<String, String> env() {
        return variables;
    }
}
