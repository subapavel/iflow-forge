package io.poxray.mcp;

import io.poxray.json.Json;
import io.poxray.monitoring.Message;
import io.poxray.monitoring.SearchResult;
import java.util.List;

/**
 * Fits a list into a character budget of an MCP tool result (spec §7): the longest prefix whose JSON
 * ({@link Json#write}, indented, so at least as long as the compact form inside a tool result) is at most the budget;
 * the rest is dropped and said so, never spilled to a file.
 */
final class McpBudget {

    private McpBudget() {
    }

    /** The messages of a search that fit, whether some were cut, how many, and a hint for the agent. */
    record FittedSearch(List<Message> messages, boolean truncated, int dropped, String hint) {

        FittedSearch {
            messages = List.copyOf(messages);
        }
    }

    /** The longest prefix of {@code result.messages()} whose JSON has at most {@code maxChars} characters. */
    static FittedSearch fit(SearchResult result, int maxChars) {
        List<Message> all = result.messages();
        int kept = fitting(all, maxChars);
        int dropped = all.size() - kept;
        return new FittedSearch(all.subList(0, kept), dropped > 0, dropped, dropped == 0 ? null
                : dropped + " of " + all.size() + " messages dropped to keep the result under " + maxChars
                        + " characters: narrow the filter (time window, status, interface, sender, receiver) or lower"
                        + " limit");
    }

    /** How many items from the start fit: the largest n with {@code Json.write(items.subList(0, n))} within budget. */
    static <T> int fitting(List<T> items, int maxChars) {
        if (Json.write(items).length() <= maxChars) {
            return items.size();
        }
        int lo = 0; // fits (the empty list is "[ ]")
        int hi = items.size(); // does not fit
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (Json.write(items.subList(0, mid)).length() <= maxChars) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
