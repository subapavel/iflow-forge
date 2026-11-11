package io.poxray.mcp;

import static io.poxray.monitoring.FakeMonitoringPo.searchResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import io.poxray.monitoring.FakeMonitoringPo;
import io.poxray.monitoring.Message;
import io.poxray.monitoring.MessageFilter;
import io.poxray.monitoring.MonitoringClient;
import io.poxray.monitoring.SearchResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** McpBudget.fit keeps the longest prefix of the messages that fits the character budget, and says what it cut. */
class McpBudgetTest {

    private FakeMonitoringPo po;

    @BeforeEach
    void start() {
        po = new FakeMonitoringPo();
    }

    @AfterEach
    void stop() {
        po.close();
    }

    /** A search result with {@code count} messages, each carrying an error code of {@code padding} characters. */
    private SearchResult result(int count, int padding) {
        List<String> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add("<w:AdapterFrameworkData><w:errorCode>" + "E".repeat(padding) + "</w:errorCode>"
                    + "<w:messageID>msg-" + String.format("%04d", i) + "</w:messageID>"
                    + "<w:status>systemError</w:status></w:AdapterFrameworkData>");
        }
        po.respondSoap(searchResponse("getMessageList", count, false, items.toArray(String[]::new)));
        return new MonitoringClient(po.system).getMessageList(MessageFilter.builder().build(), count);
    }

    /** The same result with only its first {@code n} messages. */
    private static SearchResult firstN(SearchResult r, int n) {
        return new SearchResult(r.system(), r.messages().subList(0, n), r.number(), r.moreAvailable(),
                r.displayPermissionWarning(), r.date(), r.rawResponse(), r.otherFields());
    }

    @Test
    void fitsAll() {
        SearchResult r = result(3, 10);

        var fitted = McpBudget.fit(r, 80_000);

        assertThat(fitted.messages()).containsExactlyElementsOf(r.messages());
        assertThat(fitted.truncated()).isFalse();
        assertThat(fitted.dropped()).isZero();
    }

    @Test
    void cutsAtLastFittingMessage() {
        SearchResult r = result(10, 2_000);
        int budget = 9_000;

        var fitted = McpBudget.fit(r, budget);

        int kept = fitted.messages().size();
        assertThat(kept).isBetween(1, 9);
        assertThat(fitted.messages()).containsExactlyElementsOf(r.messages().subList(0, kept));
        assertThat(fitted.truncated()).isTrue();
        assertThat(fitted.dropped()).isEqualTo(10 - kept);
        assertThat(fitted.hint()).isNotBlank();
        assertThat(Json.write(fitted.messages()).length()).isLessThanOrEqualTo(budget);
        // the last fitting one: the first kept messages fit whole, one more does not
        var exactly = McpBudget.fit(firstN(r, kept), budget);
        assertThat(exactly.truncated()).isFalse();
        assertThat(exactly.messages()).hasSize(kept);
        var oneMore = McpBudget.fit(firstN(r, kept + 1), budget);
        assertThat(oneMore.truncated()).isTrue();
        assertThat(oneMore.messages()).extracting(Message::messageId)
                .containsExactlyElementsOf(fitted.messages().stream().map(Message::messageId).toList());
    }

    @Test
    void singleMessageLargerThanTheCapGivesNone() {
        SearchResult r = result(1, 5_000);

        var fitted = McpBudget.fit(r, 1_000);

        assertThat(fitted.messages()).isEmpty();
        assertThat(fitted.truncated()).isTrue();
        assertThat(fitted.dropped()).isEqualTo(1);
        assertThat(fitted.hint()).isNotBlank();
    }
}
