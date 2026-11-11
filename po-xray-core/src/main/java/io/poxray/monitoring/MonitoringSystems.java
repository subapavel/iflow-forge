package io.poxray.monitoring;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * The PO system the monitoring commands talk to when none is named on the command line (spec §5): the explicit name
 * ({@code --system} / {@code system}), else the environment variable {@code POXRAY_SYSTEM}, else {@code system=} in
 * {@code monitoring.properties} of the po-xray home ({@code ~/.po-xray}); a blank value counts as absent.
 */
public final class MonitoringSystems {

    /** The environment variable naming the default system. */
    public static final String ENV = "POXRAY_SYSTEM";
    /** The file in the po-xray home naming the default system ({@code system=<name>}). */
    public static final String FILE = "monitoring.properties";

    private MonitoringSystems() {
    }

    /**
     * The system name: {@code explicit}, {@code env.get("POXRAY_SYSTEM")} or {@code system=} in
     * {@code <poxrayHome>/monitoring.properties}, the first that is not blank, stripped; none is a
     * {@link MonitoringException} naming all three.
     */
    public static String resolve(String explicit, Map<String, String> env, Path poxrayHome) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.strip();
        }
        String fromEnv = env == null ? null : env.get(ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.strip();
        }
        Path file = poxrayHome.resolve(FILE);
        String fromFile = Files.isRegularFile(file) ? read(file).getProperty("system") : null;
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.strip();
        }
        throw new MonitoringException("No PO system for monitoring: give --system <name>, set the environment variable "
                + ENV + ", or write system=<name> into ~/.po-xray/" + FILE + " (" + file + ")");
    }

    private static Properties read(Path file) {
        Properties p = new Properties();
        try {
            byte[] bytes = Files.readAllBytes(file);
            // a UTF-8 BOM written by a Windows editor would otherwise become part of the first key
            int start = bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB
                    && bytes[2] == (byte) 0xBF ? 3 : 0;
            p.load(new ByteArrayInputStream(bytes, start, bytes.length - start));
        } catch (IOException e) {
            throw new MonitoringException("Cannot read " + file + ": " + e.getMessage(), e);
        }
        return p;
    }
}
