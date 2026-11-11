package io.poxray.po;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP setup shared by the clients of a PO system ({@link PoClient}, the monitoring client): HTTP/1.1, 30 s connect
 * timeout, no redirects (a redirect means: logon page), the system's trust store and proxy, preemptive Basic
 * authentication; plus the helpers that turn an HTTP failure into a short message.
 */
public final class PoHttp {

    private static final Pattern CHARSET = Pattern.compile("(?i)charset=([^;]+)");
    /** First {@code some.package.SomeException: message} of a stack trace (up to the first frame). */
    private static final Pattern EXCEPTION =
            Pattern.compile("\\b(?:[a-z][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error)\\b:?[^\\n]*?(?=\\s+at\\s+[\\w$.]+\\(|$)");

    private PoHttp() {
    }

    /** The HTTP client for an online system; a TPZ export directory or a proxy without port is a {@link PoException}. */
    public static HttpClient client(PoSystem system) {
        if (system.url() == null) {
            throw new PoException("System '" + system.name() + "' is a directory of TPZ exports (tpz=...),"
                    + " it has no online connection");
        }
        HttpClient.Builder b = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // no h2c upgrade attempt: SAP AS Java answers it with 403
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NEVER); // a redirect means: logon page
        if ("https".equalsIgnoreCase(system.url().getScheme())) {
            b.sslContext(TrustStores.sslContext(system.trustStore(), system.trustStorePassword()));
        }
        if (system.proxy() != null) {
            String p = system.proxy();
            int colon = p.lastIndexOf(':');
            if (colon < 0) {
                throw new PoException("proxy must be host:port, not '" + p + "'");
            }
            b.proxy(ProxySelector.of(new InetSocketAddress(p.substring(0, colon),
                    Integer.parseInt(p.substring(colon + 1)))));
        }
        return b.build();
    }

    /** The preemptive {@code Authorization} header value: {@code Basic base64(user:password)}, UTF-8. */
    public static String authorization(PoSystem system) {
        return "Basic " + Base64.getEncoder().encodeToString(
                (system.user() + ":" + system.password()).getBytes(StandardCharsets.UTF_8));
    }

    /** First non-empty message in the cause chain with its exception type (some I/O errors have no message). */
    public static String describe(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && !t.getMessage().isBlank()) {
                return t.getClass().getSimpleName() + ": " + t.getMessage();
            }
        }
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == e || root.getClass() == e.getClass() ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + " / " + root.getClass().getSimpleName();
    }

    /** Charset of a response: the {@code charset} parameter of its content type, UTF-8 if absent or unknown. */
    public static Charset charset(String contentType) {
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1).replace("\"", "").strip());
            } catch (IllegalArgumentException e) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** True when an answer is the AS Java logon form instead of the requested content. */
    public static boolean isLogonPage(String html) {
        return html.contains("j_username") && html.contains("j_password");
    }

    /** A short plain-text excerpt of an HTML error page; for Java stack traces the first exception and message. */
    public static String errorText(String html) {
        String text = unescapeHtml(html.replaceAll("(?is)<(script|style)\\b.*?</\\1>", " ").replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ").strip();
        Matcher ex = EXCEPTION.matcher(text);
        if (ex.find()) {
            String message = ex.group(0).replaceAll("\\s+at\\s+[\\w$.]+\\(.*$", "").strip();
            return message.length() > 400 ? message.substring(0, 400) + "…" : message;
        }
        int i = text.toLowerCase(Locale.ROOT).indexOf("exception");
        String excerpt = i >= 0 ? text.substring(Math.max(0, i - 80)) : text;
        return excerpt.length() > 400 ? excerpt.substring(0, 400) + "…" : excerpt;
    }

    static String unescapeHtml(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&#034;", "\"").replace("&#039;", "'").replace("&amp;", "&");
    }
}
