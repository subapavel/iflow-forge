package io.poxray.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.po.PoConnection;
import io.poxray.po.PoGateway;
import io.poxray.po.PoSystem;
import io.poxray.po.QueryRequest;
import io.poxray.po.QueryResult;
import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectSource;
import io.poxray.xi.XiRef;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One connection per system and session: TPZ exports and snapshots are not re-read on every tool call. */
class InventoriesTest {

    private int opened;

    private final Inventories inventories = new Inventories() {
        @Override
        PoConnection openConnection(String system, boolean refresh) {
            opened++;
            PoGateway gateway = new PoGateway() {
                @Override
                public PoSystem system() {
                    return new PoSystem(system, URI.create("http://fake"), "u", "p");
                }

                @Override
                public QueryResult query(QueryRequest request) {
                    return new QueryResult(List.of(), List.of(), Map.of());
                }

                @Override
                public String readRaw(XiRef ref) {
                    throw new UnsupportedOperationException();
                }
            };
            XiObjectSource source = new XiObjectSource() {
                @Override
                public Optional<XiObject> resolve(XiRef ref) {
                    return Optional.empty();
                }
            };
            return new PoConnection(gateway, source);
        }
    };

    @Test
    void oneConnectionPerSystemAndSession() {
        inventories.components("s", "");
        inventories.components("s", "x");
        inventories.icos("s", "a", "both", false);
        inventories.icos("s", "b", "in", false);
        inventories.catalog("s");

        assertThat(opened).isEqualTo(1);

        inventories.components("other", "");
        assertThat(opened).isEqualTo(2);
    }

    @Test
    void refreshReopensTheConnection() {
        inventories.icos("s", "a", "both", false);
        inventories.icos("s", "a", "both", true);
        assertThat(opened).isEqualTo(2);

        // the refreshing connection is not kept: the next call opens a fresh one over the updated snapshot
        inventories.components("s", "");
        assertThat(opened).isEqualTo(3);
        inventories.components("s", "");
        assertThat(opened).isEqualTo(3);
    }
}
