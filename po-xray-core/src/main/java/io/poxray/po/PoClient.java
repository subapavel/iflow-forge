package io.poxray.po;

import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectParser;
import io.poxray.xi.XiRef;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Read-only access to a PO system: SimpleQuery (discovery) and {@code read/ext} (object content), for both the
 * ESR ({@code /rep}) and the Integration Directory ({@code /dir}). Unofficial support endpoints, see
 * docs/SOURCES-ESR-DIRECTORY.md. Basic authentication is sent preemptively (POST endpoints challenge separately).
 */
public final class PoClient {

    private static final Pattern CHARSET = Pattern.compile("(?i)charset=([^;]+)");
    private static final Pattern RESPONSE_TEXTAREA =
            Pattern.compile("<textarea[^>]*name=\"response\"[^>]*>(.*?)</textarea>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Duration TIMEOUT = Duration.ofMinutes(2);
    /** First {@code some.package.SomeException: message} of a stack trace (up to the first frame). */
    private static final Pattern EXCEPTION =
            Pattern.compile("\\b(?:[a-z][\\w$]*\\.)+[A-Z][\\w$]*(?:Exception|Error)\\b:?[^\\n]*?(?=\\s+at\\s+[\\w$.]+\\(|$)");

    private final PoSystem system;
    private final HttpClient http;
    private final String authorization;

    public PoClient(PoSystem system) {
        this(system, httpClient(system));
    }

    private static HttpClient httpClient(PoSystem system) {
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
            b.proxy(java.net.ProxySelector.of(new java.net.InetSocketAddress(p.substring(0, colon),
                    Integer.parseInt(p.substring(colon + 1)))));
        }
        return b.build();
    }

    PoClient(PoSystem system, HttpClient http) {
        this.system = system;
        this.http = http;
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                (system.user() + ":" + system.password()).getBytes(StandardCharsets.UTF_8));
    }

    public PoSystem system() {
        return system;
    }

    /** Runs a SimpleQuery. */
    public QueryResult query(QueryRequest request) {
        return QueryResult.parse(queryXml(request));
    }

    /** Runs a SimpleQuery and returns the raw {@code queryResult} XML. */
    public String queryXml(QueryRequest request) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("useXMLL", "true");
        form.put("queryRequestXMLL", request.toXml(system.user()));
        form.put("responseXMLL", "true");
        if (request.area() == Area.DIRECTORY) {
            form.put("qc", "Default (for directory objects)");
            form.put("deletedL", "N");
        }
        form.put("action", "Start query");
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        URI uri = system.url().resolve("/" + request.area().path() + "/support/SimpleQuery");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Authorization", authorization)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        String html = send(req, "SimpleQuery " + request.area().path());
        Matcher m = RESPONSE_TEXTAREA.matcher(html);
        String xml = m.find() ? unescapeHtml(m.group(1)).strip() : "";
        if (xml.isEmpty()) {
            throw new PoException("SimpleQuery returned no result: " + errorText(html));
        }
        return xml;
    }

    /** The SimpleQuery form page (HTML) of an area: lists the object types this system can query. */
    public String queryForm(Area area) {
        URI uri = system.url().resolve("/" + area.path() + "/support/SimpleQuery");
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Authorization", authorization)
                .GET()
                .build();
        return send(req, "SimpleQuery form " + area.path());
    }

    /** The raw {@code p1:xiObj} XML of the current version of an object. */
    public String readRaw(XiRef ref) {
        if (ref.typeId() == null) {
            throw new IllegalArgumentException("Reference without type: " + ref);
        }
        Area area = Area.of(ref);
        StringBuilder q = new StringBuilder("method=PLAIN")
                .append("&TYPE=").append(encode(ref.typeId()))
                .append("&KEY=").append(encode(ref.keyString()));
        if (area == Area.DIRECTORY) {
            q.append("&VC=DIR");
        } else {
            if (ref.swcGuid() == null) {
                throw new IllegalArgumentException("ESR reference without SWCV: " + ref.label());
            }
            q.append("&VC=SWC&SWCGUID=").append(encode(ref.swcGuid())).append("&SP=-1");
        }
        q.append("&UC=true&release=7.0");
        URI uri = system.url().resolve("/" + area.path() + "/read/ext?" + q);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(TIMEOUT)
                .header("Authorization", authorization)
                .GET()
                .build();
        String body = send(req, "read/ext " + ref.label());
        if (!body.contains("xiObj")) {
            throw new PoException("Object not readable: " + ref.label() + ": " + errorText(body));
        }
        return body;
    }

    /** First non-empty message in the cause chain with its exception type (some I/O errors have no message). */
    static String describe(Throwable e) {
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
    static java.nio.charset.Charset charset(String contentType) {
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            try {
                return java.nio.charset.Charset.forName(m.group(1).replace("\"", "").strip());
            } catch (IllegalArgumentException e) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    public XiObject read(XiRef ref) {
        return XiObjectParser.parse(readRaw(ref), ref.label());
    }

    private String send(HttpRequest req, String what) {
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
            throw new PoException(what + " failed: cannot connect to " + req.uri().getHost() + " (" + describe(e)
                    + "; network / VPN?)", e);
        } catch (IOException e) {
            throw new PoException(what + " failed: " + describe(e), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PoException(what + " interrupted", e);
        }
        int status = resp.statusCode();
        String body = new String(resp.body(), charset(resp.headers().firstValue("Content-Type").orElse("")));
        if (status == 401 || (status / 100 == 3) || isLogonPage(body)) {
            throw new PoException(what + ": authentication failed for " + system + " (HTTP " + status + ")");
        }
        if (status == 403) {
            throw new PoException(what + ": authentication failed or not authorized for " + system
                    + " (HTTP 403; roles SAP_XI_DISPLAY_USER_J2EE / SAP_XI_API_DISPLAY_J2EE needed): " + errorText(body));
        }
        if (status != 200) {
            throw new PoException(what + ": HTTP " + status + ": " + errorText(body));
        }
        return body;
    }

    private static boolean isLogonPage(String html) {
        return html.contains("j_username") && html.contains("j_password");
    }

    /** A short plain-text excerpt of an HTML error page; for Java stack traces the first exception and message. */
    static String errorText(String html) {
        String text = unescapeHtml(html.replaceAll("(?is)<(script|style)\\b.*?</\\1>", " ").replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ").strip();
        Matcher ex = EXCEPTION.matcher(text);
        if (ex.find()) {
            String message = ex.group(0).replaceAll("\\s+at\\s+[\\w$.]+\\(.*$", "").strip();
            return message.length() > 400 ? message.substring(0, 400) + "…" : message;
        }
        int i = text.toLowerCase(java.util.Locale.ROOT).indexOf("exception");
        String excerpt = i >= 0 ? text.substring(Math.max(0, i - 80)) : text;
        return excerpt.length() > 400 ? excerpt.substring(0, 400) + "…" : excerpt;
    }

    static String unescapeHtml(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&#034;", "\"").replace("&#039;", "'").replace("&amp;", "&");
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
