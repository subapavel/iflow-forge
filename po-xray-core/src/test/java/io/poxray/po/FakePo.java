package io.poxray.po;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local fake PO system for tests: SimpleQuery answers registered per area and type set (HTML page in ISO-8859-1 like
 * the real one, result XML in the response textarea), {@code read/ext} answers per type and key.
 */
public final class FakePo implements AutoCloseable {

    private static final Pattern TYPE = Pattern.compile("<type id=\"([^\"]+)\"/>");
    private static final Pattern KEY = Pattern.compile("<key>([^<]+)</key>");

    public final PoSystem system;
    private final HttpServer server;
    private final Map<String, String> queries = new ConcurrentHashMap<>();
    private final Map<String, String> objects = new ConcurrentHashMap<>();
    public final List<String> requests = new ArrayList<>();

    public FakePo() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", this::handle);
        server.start();
        system = new PoSystem("fake", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), "u", "p");
    }

    /** Answer for a SimpleQuery of {@code area} (rep | dir) over exactly these types (and condition key if given). */
    public FakePo query(String area, List<String> types, String conditionKey, String queryResultXml) {
        queries.put(area + "|" + new TreeSet<>(types) + "|" + (conditionKey == null ? "" : conditionKey),
                queryResultXml);
        return this;
    }

    /** Answer of {@code read/ext} for a type and key ({@code a|b|c}). */
    public FakePo object(String type, String key, String xiObj) {
        objects.put(type + "|" + key, xiObj);
        return this;
    }

    private synchronized void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String body = URLDecoder.decode(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
        requests.add(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + body);
        String response;
        Charset charset = StandardCharsets.UTF_8;
        if (path.endsWith("/support/SimpleQuery")) {
            String area = path.startsWith("/rep") ? "rep" : "dir";
            TreeSet<String> types = new TreeSet<>();
            Matcher m = TYPE.matcher(body);
            while (m.find()) {
                types.add(m.group(1));
            }
            Matcher k = KEY.matcher(body);
            String key = k.find() ? k.group(1) : "";
            String xml = queries.getOrDefault(area + "|" + types + "|" + key, queries.getOrDefault(area + "|" + types
                    + "|", "<queryResult><headerInfo><rows count=\"0\"/></headerInfo><matrix/></queryResult>"));
            String escaped = xml.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
            response = "<html><form><textarea name=\"response\">" + escaped + "</textarea></form></html>";
            charset = StandardCharsets.ISO_8859_1;
        } else if (path.endsWith("/read/ext")) {
            String query = URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            Matcher type = Pattern.compile("TYPE=([^&]+)").matcher(query);
            Matcher key = Pattern.compile("KEY=([^&]*)").matcher(query);
            String id = (type.find() ? type.group(1) : "") + "|" + (key.find() ? key.group(1) : "");
            response = objects.getOrDefault(id, "<html>An exception occured: object " + id + " not found</html>");
        } else {
            response = "<html>An exception occured: unknown</html>";
        }
        byte[] bytes = response.getBytes(charset);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=" + charset.name());
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ------------------------------------------------------------------------------------------ fixtures

    /** Result row cell with an object reference ({@code vcType} S with SWCV aaaa, or D). */
    public static String ref(String vcType, String type, String oid, String... key) {
        StringBuilder elems = new StringBuilder();
        for (String k : key) {
            elems.append(k.isEmpty() ? "<elem/>" : "<elem>" + k + "</elem>");
        }
        String vc = "S".equals(vcType) ? "<vc vcType=\"S\" swcGuid=\"aaaa\" caption=\"ACME, 1.0\"/>" : "<vc vcType=\"D\"/>";
        return "<ref>" + vc + "<key typeID=\"" + type + "\"" + (oid == null ? "" : " oid=\"" + oid + "\"") + ">" + elems
                + "</key></ref>";
    }

    /** A query result: rows of (object ref, optional TEXT, optional link list) with typeInfo key names. */
    public static String result(Map<String, List<String>> keyNames, List<String[]> rows) {
        StringBuilder sb = new StringBuilder("<queryResult><headerInfo><rows count=\"" + rows.size() + "\"/><colDef>"
                + "<def type=\"RA_XILINK\" pos=\"0\"/><def type=\"TEXT\" pos=\"1\"/><def type=\"RA_LINK_LIST\" pos=\"2\"/>"
                + "</colDef></headerInfo><typeInfo>");
        keyNames.forEach((type, names) -> {
            sb.append("<type id=\"").append(type).append("\">");
            for (int i = 0; i < names.size(); i++) {
                sb.append("<keyElem name=\"").append(names.get(i)).append("\" pos=\"").append(i).append("\"/>");
            }
            sb.append("</type>");
        });
        sb.append("</typeInfo><matrix>");
        for (String[] row : rows) {
            sb.append("<r><c><qref>").append(row[0]).append("</qref></c><c><simple><strg>")
                    .append(row.length > 1 ? row[1] : "").append("</strg></simple></c><c><array>")
                    .append(row.length > 2 ? row[2] : "").append("</array></c></r>");
        }
        return sb.append("</matrix></queryResult>").toString();
    }

    /** An {@code xiObj} with links ({@code role, vcType, type, key...} each) and optional content XML. */
    public static String xiObj(String vcType, String type, String oid, List<String> key, String content,
            List<String[]> links) {
        StringBuilder elems = new StringBuilder();
        key.forEach(k -> elems.append(k.isEmpty() ? "<p1:elem/>" : "<p1:elem>" + k + "</p1:elem>"));
        StringBuilder lnks = new StringBuilder();
        for (String[] l : links) {
            StringBuilder le = new StringBuilder();
            for (int i = 3; i < l.length; i++) {
                le.append(l[i].isEmpty() ? "<p1:elem/>" : "<p1:elem>" + l[i] + "</p1:elem>");
            }
            String lvc = "S".equals(l[1]) ? "<p1:vc vcType=\"S\" swcGuid=\"aaaa\"/>" : "<p1:vc vcType=\"D\"/>";
            lnks.append("<p1:lnkRole role=\"").append(l[0]).append("\" kpos=\"0\"><p1:lnk rMode=\"A\">").append(lvc)
                    .append("<p1:key typeID=\"").append(l[2]).append("\">").append(le).append("</p1:key></p1:lnk>")
                    .append("</p1:lnkRole>");
        }
        String vc = "S".equals(vcType) ? "<p1:vc vcType=\"S\" swcGuid=\"aaaa\" caption=\"ACME, 1.0\"/>"
                : "<p1:vc vcType=\"D\"/>";
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><p1:xiObj xmlns:p1=\"urn:sap-com:xi\"><p1:idInfo VID=\"v1\">"
                + vc + "<p1:key typeID=\"" + type + "\" oid=\"" + oid + "\">" + elems + "</p1:key></p1:idInfo>"
                + "<p1:generic><p1:lnks>" + lnks + "</p1:lnks></p1:generic>"
                + "<p1:content>" + (content == null ? "" : content) + "</p1:content></p1:xiObj>";
    }
}
