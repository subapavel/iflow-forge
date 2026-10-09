package io.poxray.po;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Which certificates a PO system's HTTPS certificate is checked against. A native executable only knows the JDK
 * certificates baked in at build time; company PO systems usually have certificates of a company CA that is in the
 * Windows certificate store (what the browser on the same machine trusts).
 * <ul>
 * <li>{@code default} (no setting): JDK certificates, on Windows also the Windows store (either one suffices)</li>
 * <li>{@code windows}: Windows certificate store only</li>
 * <li>{@code jdk}: JDK certificates only</li>
 * <li>a file path: a JKS / PKCS12 trust store ({@code truststorePassword} if needed)</li>
 * </ul>
 */
final class TrustStores {

    private TrustStores() {
    }

    static SSLContext sslContext(String trustStore, String password) {
        try {
            String mode = trustStore == null || trustStore.isBlank() ? "default" : trustStore.strip();
            List<X509TrustManager> managers = new ArrayList<>();
            switch (mode.toLowerCase(Locale.ROOT)) {
                case "default" -> {
                    managers.add(manager(null));
                    if (isWindows()) {
                        X509TrustManager windows = windowsOrNull();
                        if (windows != null) {
                            managers.add(windows);
                        }
                    }
                }
                case "windows" -> {
                    X509TrustManager windows = windowsOrNull();
                    if (windows == null) {
                        throw new PoException("truststore=windows: the Windows certificate store is not available"
                                + (isWindows() ? "" : " (not Windows)"));
                    }
                    managers.add(windows);
                }
                case "jdk" -> managers.add(manager(null));
                default -> {
                    Path file = Path.of(mode);
                    if (!Files.isRegularFile(file)) {
                        throw new PoException("truststore file not found: " + file);
                    }
                    managers.add(manager(KeyStore.getInstance(file.toFile(),
                            password == null ? null : password.toCharArray())));
                }
            }
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[] {new AnyOf(List.copyOf(managers))}, null);
            return ctx;
        } catch (GeneralSecurityException | IOException e) {
            throw new PoException("Cannot set up HTTPS trust (" + trustStore + "): " + e.getMessage(), e);
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** The Windows ROOT store (SunMSCAPI), null if the provider is not available. */
    private static X509TrustManager windowsOrNull() {
        try {
            KeyStore ks = KeyStore.getInstance("Windows-ROOT");
            ks.load(null, null);
            return manager(ks);
        } catch (GeneralSecurityException | IOException e) {
            return null;
        }
    }

    /** Trust manager of a key store; {@code null} = the JDK default certificates. */
    private static X509TrustManager manager(KeyStore ks) throws GeneralSecurityException {
        TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        f.init(ks);
        for (TrustManager m : f.getTrustManagers()) {
            if (m instanceof X509TrustManager x) {
                return x;
            }
        }
        throw new GeneralSecurityException("no X509TrustManager");
    }

    /** Trusts a server certificate if any of the managers does. */
    private record AnyOf(List<X509TrustManager> managers) implements X509TrustManager {

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            CertificateException last = null;
            for (X509TrustManager m : managers) {
                try {
                    m.checkServerTrusted(chain, authType);
                    return;
                } catch (CertificateException e) {
                    last = e;
                }
            }
            throw last == null ? new CertificateException("no trust manager") : last;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new CertificateException("client certificates are not checked");
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return managers.stream().flatMap(m -> java.util.Arrays.stream(m.getAcceptedIssuers()))
                    .toArray(X509Certificate[]::new);
        }
    }
}
