package io.poxray.cli;

import io.quarkus.test.Mock;
import java.util.Map;
import java.util.TreeMap;

/**
 * In CLI tests the monitoring commands see no machine environment variables, so the machine's POXRAY_SYSTEM or
 * credentials never count. A test may make variables visible with {@link #override(Map)} and must {@link #reset()}
 * afterwards. The override travels as JVM system properties ({@value #PREFIX}NAME): under {@code @QuarkusMainTest}
 * the application loads this class in its own class loader, so a static field set by the test is not the one the
 * bean reads.
 */
@Mock
public class EmptyMonitoringEnvironment extends MonitoringEnvironment {

    static final String PREFIX = "poxray.test.monitoring.env.";

    /** Makes exactly these variables visible to the commands. */
    static void override(Map<String, String> variables) {
        reset();
        variables.forEach((k, v) -> System.setProperty(PREFIX + k, v));
    }

    /** No variable visible. */
    static void reset() {
        System.getProperties().stringPropertyNames().stream().filter(n -> n.startsWith(PREFIX))
                .forEach(System::clearProperty);
    }

    @Override
    public Map<String, String> env() {
        Map<String, String> env = new TreeMap<>();
        System.getProperties().stringPropertyNames().stream().filter(n -> n.startsWith(PREFIX))
                .forEach(n -> env.put(n.substring(PREFIX.length()), System.getProperty(n)));
        return Map.copyOf(env);
    }
}
