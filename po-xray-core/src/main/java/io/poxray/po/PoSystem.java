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
 * repository): {@code url}, {@code user}, {@code password}. Environment variables {@code POXRAY_USER} /
 * {@code POXRAY_PASSWORD} override the file. The password is never logged or included in {@link #toString()}.
 */
public final class PoSystem {

    public static final String USER_ENV = "POXRAY_USER";
    public static final String PASSWORD_ENV = "POXRAY_PASSWORD";

    private final String name;
    private final URI url;
    private final String user;
    private final String password;

    public PoSystem(String name, URI url, String user, String password) {
        this.name = name;
        this.url = URI.create(url.toString().replaceAll("/+$", ""));
        this.user = user;
        this.password = password;
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
        String url = p.getProperty("url");
        if (url == null || url.isBlank()) {
            throw new PoException("Missing url in " + file);
        }
        String user = env.getOrDefault(USER_ENV, p.getProperty("user"));
        String password = env.getOrDefault(PASSWORD_ENV, p.getProperty("password"));
        if (user == null || user.isBlank() || password == null || password.isEmpty()) {
            throw new PoException("Missing user/password for system '" + name + "' (" + file + " or "
                    + USER_ENV + "/" + PASSWORD_ENV + ")");
        }
        return new PoSystem(name, URI.create(url.strip()), user.strip(), password);
    }

    public String name() {
        return name;
    }

    public URI url() {
        return url;
    }

    public String user() {
        return user;
    }

    String password() {
        return password;
    }

    @Override
    public String toString() {
        return name + " (" + url + ", user " + user + ")";
    }
}
