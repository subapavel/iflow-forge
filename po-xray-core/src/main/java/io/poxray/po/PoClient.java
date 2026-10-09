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

    private static final Pattern RESPONSE_TEXTAREA =
            Pattern.compile("<textarea[^>]*name=\"response\"[^>]*>(.*?)</textarea>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final PoSystem system;
    private final HttpClient http;
    private final String authorization;

    public PoClient(PoSystem system) {
        this(system, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NEVER) // a redirect means: logon page
                .build());
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
        return QueryResult.parse(xml);
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

    public XiObject read(XiRef ref) {
        return XiObjectParser.parse(readRaw(ref), ref.label());
    }

    private String send(HttpRequest req, String what) {
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PoException(what + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PoException(what + " interrupted", e);
        }
        int status = resp.statusCode();
        String body = resp.body();
        if (status == 401 || status == 403 || (status / 100 == 3) || isLogonPage(body)) {
            throw new PoException(what + ": authentication failed for " + system + " (HTTP " + status + ")");
        }
        if (status != 200) {
            throw new PoException(what + ": HTTP " + status + ": " + errorText(body));
        }
        return body;
    }

    private static boolean isLogonPage(String html) {
        return html.contains("j_username") && html.contains("j_password");
    }

    /** A short plain-text excerpt of an HTML error page. */
    static String errorText(String html) {
        String text = unescapeHtml(html.replaceAll("(?s)<script.*?</script>", " ").replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ").strip();
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
