package io.poxray.po;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** PoHttp: the HTTP setup shared by PoClient and the monitoring client, moved out of PoClient unchanged. */
class PoHttpTest {

    private static final URI URL = URI.create("http://127.0.0.1:50000/");

    @Test
    void authorizationIsPreemptiveBasic() {
        PoSystem system = new PoSystem("QA1", URL, "alice", "s3cret");

        assertThat(PoHttp.authorization(system)).isEqualTo(
                "Basic " + Base64.getEncoder().encodeToString("alice:s3cret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void authorizationEncodesUserAndPasswordAsUtf8() {
        PoSystem system = new PoSystem("QA1", URL, "jürgen", "pässwort");

        assertThat(PoHttp.authorization(system)).isEqualTo(
                "Basic " + Base64.getEncoder().encodeToString("jürgen:pässwort".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void clientUsesHttp11NoRedirectsAndThirtySecondConnectTimeout() {
        HttpClient http = PoHttp.client(new PoSystem("QA1", URL, "alice", "s3cret"));

        assertThat(http.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(http.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        assertThat(http.connectTimeout()).contains(Duration.ofSeconds(30));
        assertThat(http.proxy()).isEmpty();
    }

    @Test
    void clientRoutesThroughTheConfiguredProxy() {
        HttpClient http = PoHttp.client(new PoSystem("QA1", URL, "alice", "s3cret", null, null, "127.0.0.1:3128"));

        assertThat(http.proxy()).hasValueSatisfying(selector -> assertThat(selector.select(URL))
                .extracting(Proxy::address)
                .containsExactly(new InetSocketAddress("127.0.0.1", 3128)));
    }

    @Test
    void clientRejectsAProxyWithoutPort() {
        PoSystem system = new PoSystem("QA1", URL, "alice", "s3cret", null, null, "proxyhost");

        assertThatThrownBy(() -> PoHttp.client(system))
                .isInstanceOf(PoException.class)
                .hasMessage("proxy must be host:port, not 'proxyhost'");
    }

    @Test
    void clientRejectsATpzSystemWithThePoClientMessage(@TempDir Path dir) {
        PoSystem system = PoSystem.tpz("exports", dir);

        assertThatThrownBy(() -> PoHttp.client(system))
                .isInstanceOf(PoException.class)
                .hasMessage("System 'exports' is a directory of TPZ exports (tpz=...), it has no online connection");
        assertThatThrownBy(() -> new PoClient(system))
                .isInstanceOf(PoException.class)
                .hasMessage("System 'exports' is a directory of TPZ exports (tpz=...), it has no online connection");
    }
}
