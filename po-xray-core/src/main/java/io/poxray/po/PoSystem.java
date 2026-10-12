package io.poxray.po;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Connection settings of a PO system. Read from {@code ~/.po-xray/systems/<name>.properties} (outside any
 * repository): {@code url}, {@code user}, {@code password} — or {@code tpz=<directory>} for a system available only
 * as TPZ exports (no url or credentials needed; a relative directory is resolved against the systems directory).
 * Optional {@code truststore} (HTTPS trust: windows, jdk
 * or a trust store file with {@code truststorePassword}, see {@link TrustStores}) and {@code proxy}
 * ({@code host:port}). Environment variables override the file: {@code POXRAY_<SYSTEM>_USER} /
 * {@code POXRAY_<SYSTEM>_PASSWORD} for one system (name in upper case, other characters than letters and digits as
 * {@code _}, e.g. {@code POXRAY_EON_PROD_PASSWORD} for {@code eon-prod}), then {@code POXRAY_USER} /
 * {@code POXRAY_PASSWORD} for all systems. The password is never logged or included in {@link #toString()}.
 */
public final class PoSystem {

    public static final String USER_ENV = "POXRAY_USER";
    public static final String PASSWORD_ENV = "POXRAY_PASSWORD";

    private final String name;
    private final URI url;
    private final Path tpz;
    private final String user;
    private final String password;
    private final String trustStore;
    private final String trustStorePassword;
    private final String proxy;

    public PoSystem(String name, URI url, String user, String password) {
        this(name, url, null, user, password, null, null, null);
    }

    public PoSystem(String name, URI url, String user, String password, String trustStore,
            String trustStorePassword, String proxy) {
        this(name, url, null, user, password, trustStore, trustStorePassword, proxy);
    }

    /** A system backed by a directory of TPZ exports instead of an online connection. */
    public static PoSystem tpz(String name, Path directory) {
        return new PoSystem(name, null, directory, null, null, null, null, null);
    }

    private PoSystem(String name, URI url, Path tpz, String user, String password, String trustStore,
            String trustStorePassword, String proxy) {
        this.name = name;
        this.url = url == null ? null : URI.create(url.toString().replaceAll("/+$", ""));
        this.tpz = tpz;
        this.user = user;
        this.password = password;
        this.trustStore = blankToNull(trustStore);
        this.trustStorePassword = trustStorePassword;
        this.proxy = blankToNull(proxy);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Default location of system files: {@code ~/.po-xray/systems}. */
    public static Path defaultDirectory() {
        return Path.of(System.getProperty("user.home"), ".po-xray", "systems");
    }

    public static PoSystem load(String name) {
        return load(name, defaultDirectory(), System.getenv());
    }

    static PoSystem load(String name, Path directory, Map<String, String> env) {
        Path file = directory.resolve(name + ".properties");
        if (!Files.isRegularFile(file)) {
            throw new PoException("System '" + name + "' is not configured: create " + file
                    + " with url=..., user=..., password=...");
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
        String tpz = p.getProperty("tpz");
        if (tpz != null && !tpz.isBlank()) {
            // a relative directory is resolved against the systems directory
            return PoSystem.tpz(name, directory.resolve(tpz.strip()).normalize());
        }
        String url = p.getProperty("url");
        if (url == null || url.isBlank()) {
            throw new PoException("Missing url in " + file + " (url=... for an online system,"
                    + " or tpz=<directory of .tpz exports>)");
        }
        String prefix = envPrefix(name);
        String user = firstOf(env.get(prefix + "_USER"), env.get(USER_ENV), p.getProperty("user"));
        String password = firstOf(env.get(prefix + "_PASSWORD"), env.get(PASSWORD_ENV), p.getProperty("password"));
        if (user == null || user.isBlank() || password == null || password.isEmpty()) {
            throw new PoException("Missing user/password for system '" + name + "' (" + file + ", " + prefix
                    + "_USER/" + prefix + "_PASSWORD or " + USER_ENV + "/" + PASSWORD_ENV + ")");
        }
        return new PoSystem(name, URI.create(url.strip()), user.strip(), password, p.getProperty("truststore"),
                p.getProperty("truststorePassword"), p.getProperty("proxy"));
    }

    /** {@code POXRAY_<SYSTEM>}: system name in upper case, other characters than letters and digits as {@code _}. */
    static String envPrefix(String system) {
        return "POXRAY_" + system.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
    }

    private static String firstOf(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    public String name() {
        return name;
    }

    /** Base URL of the online system; null when the system is a TPZ export directory ({@link #tpz()}). */
    public URI url() {
        return url;
    }

    /** Directory of TPZ exports; null when the system is online ({@link #url()}). */
    public Path tpz() {
        return tpz;
    }

    /** Where the content comes from, for people: the URL or the TPZ directory. */
    public String location() {
        return tpz != null ? tpz.toString() : String.valueOf(url);
    }

    public String user() {
        return user;
    }

    String password() {
        return password;
    }

    /** HTTPS trust setting ({@code null} = default), see {@link TrustStores}. */
    public String trustStore() {
        return trustStore;
    }

    String trustStorePassword() {
        return trustStorePassword;
    }

    /** HTTP proxy {@code host:port}, null = direct connection. */
    public String proxy() {
        return proxy;
    }

    @Override
    public String toString() {
        return tpz != null ? name + " (tpz " + tpz + ")"
                : name + " (" + url + ", user " + user + (proxy == null ? "" : ", proxy " + proxy) + ")";
    }
}
