package io.poxray.po;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.poxray.xi.XiRef;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PoClient against a local fake PO server (no real system needed). */
class PoClientTest {

    private static final String QUERY_RESULT = """
            <queryResult><headerInfo><rows count="1"/><cols count="2"/>
            <colDef><def type="RA_XILINK" pos="0"/><def type="NAME" pos="1"/></colDef></headerInfo>
            <matrix><r>
              <c><qref isMod="false"><ref><vc vcType="S" swcGuid="aaaa" sp="-1" caption="ACME, 1.0"/>
                 <key typeID="MAPPING" oid="o1"><elem>OM_Order</elem><elem>urn:acme</elem></key>
                 <vspec type="4" id="v1" deleted="false"/></ref></qref></c>
              <c><simple><strg>OM_Order</strg></simple></c>
            </r></matrix></queryResult>
            """;
    private static final String XIOBJ = """
            <?xml version="1.0" encoding="UTF-8"?><p1:xiObj xmlns:p1="urn:sap-com:xi"><p1:idInfo VID="v1">
            <p1:vc vcType="S" swcGuid="aaaa" caption="ACME, 1.0"/><p1:key typeID="MAPPING" oid="o1">
            <p1:elem>OM_Order</p1:elem><p1:elem>urn:acme</p1:elem></p1:key></p1:idInfo><p1:content><mp:Mapping xmlns:mp="urn:sap-com:xi:mapping"/></p1:content></p1:xiObj>
            """;

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private final AtomicInteger reads = new AtomicInteger();
    private volatile boolean logonPage;
    private PoSystem system;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        system = new PoSystem("test", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                "alice", "s3cret");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String expected = "Basic " + Base64.getEncoder().encodeToString("alice:s3cret".getBytes(StandardCharsets.UTF_8));
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String path = ex.getRequestURI().getPath();
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + URLDecoder.decode(body, StandardCharsets.UTF_8));
        String response;
        if (logonPage || !expected.equals(auth)) {
            response = "<html><title>User Management, SAP AG</title><input name=\"j_username\"><input name=\"j_password\"></html>";
        } else if (path.equals("/rep/support/SimpleQuery")) {
            String escaped = QUERY_RESULT.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
            response = "<html><form><textarea name=\"request\">x</textarea><textarea name=\"response\" rows=\"20\">"
                    + escaped + "</textarea></form></html>";
        } else if (path.equals("/rep/read/ext")) {
            reads.incrementAndGet();
            response = XIOBJ;
        } else {
            response = "<html>An exception occured: unknown</html>";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    void queriesWithPreemptiveAuthAndParsesResult() {
        QueryResult r = new PoClient(system).query(QueryRequest.like(Area.REPOSITORY, List.of("MAPPING"), "NAME", "OM_*"));

        assertThat(r.columns()).containsExactly("RA_XILINK", "NAME");
        assertThat(r.rows()).singleElement().satisfies(row -> {
            assertThat(row.text("NAME")).isEqualTo("OM_Order");
            assertThat(row.xiLink()).isEqualTo(new XiRef("S", "aaaa", "ACME, 1.0", "MAPPING", "o1", List.of("OM_Order", "urn:acme")));
        });
        assertThat(requests).singleElement().satisfies(req -> assertThat(req)
                .startsWith("POST /rep/support/SimpleQuery")
                .contains("useXMLL=true", "<type id=\"MAPPING\"/>", "<key>NAME</key>", "<strg>OM_*</strg>",
                        "isCaseIns=\"true\"", "action=Start query"));
    }

    @Test
    void readsObjectsAndCachesThem(@TempDir Path cache) {
        CachingObjectSource source = new CachingObjectSource(new PoClient(system), cache, false);
        XiRef ref = new XiRef("S", "aaaa", null, "MAPPING", "o1", List.of("OM_Order", "urn:acme"));

        assertThat(source.resolve(ref)).hasValueSatisfying(o -> assertThat(o.id().key(0)).isEqualTo("OM_Order"));
        assertThat(source.resolve(ref)).isPresent();
        assertThat(reads.get()).isEqualTo(1); // second time from the snapshot
        assertThat(requests.getFirst()).contains("TYPE=MAPPING", "KEY=OM_Order%7Curn%3Aacme", "VC=SWC", "SWCGUID=aaaa");
        assertThat(Files.exists(source.fileOf(ref))).isTrue();

        new CachingObjectSource(new PoClient(system), cache, true).resolve(ref);
        assertThat(reads.get()).isEqualTo(2); // refresh re-reads
    }

    @Test
    void reportsAuthenticationFailureWithoutPassword() {
        logonPage = true;
        PoClient client = new PoClient(system);

        assertThatThrownBy(() -> client.query(QueryRequest.like(Area.DIRECTORY, List.of("Service"), "NAME", "*")))
                .isInstanceOf(PoException.class)
                .hasMessageContaining("authentication failed")
                .hasMessageNotContaining("s3cret");
        assertThat(system.toString()).doesNotContain("s3cret");
    }

    @Test
    void loadsSystemFromFileWithEnvOverride(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("xipod.properties"), "url=http://host:54200/\nuser=bob\npassword=pw\n");

        PoSystem s = PoSystem.load("xipod", dir, Map.of());
        assertThat(s.url()).hasToString("http://host:54200");
        assertThat(s.user()).isEqualTo("bob");
        assertThat(PoSystem.load("xipod", dir, Map.of(PoSystem.PASSWORD_ENV, "envpw")).password()).isEqualTo("envpw");

        assertThatThrownBy(() -> PoSystem.load("missing", dir, Map.of()))
                .isInstanceOf(PoException.class).hasMessageContaining("missing.properties");
    }
}
