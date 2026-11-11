package io.poxray.monitoring;

import static io.poxray.monitoring.FakeMonitoringPo.JL;
import static io.poxray.monitoring.FakeMonitoringPo.OP;
import static io.poxray.monitoring.FakeMonitoringPo.child;
import static io.poxray.monitoring.FakeMonitoringPo.childNames;
import static io.poxray.monitoring.FakeMonitoringPo.children;
import static io.poxray.monitoring.FakeMonitoringPo.message;
import static io.poxray.monitoring.FakeMonitoringPo.operation;
import static io.poxray.monitoring.FakeMonitoringPo.searchResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.monitoring.KeyResolver.Resolution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** KeyResolver against a MonitoringClient whose getMessagesByIDs answers come from the local fake service. */
class KeyResolverTest {

    private static final String ID = "msg-0001";
    /** Synthetic keys of the sender and the receiver side of message {@link #ID}. */
    private static final String SENDER_KEY = "aaaaaaaa-1111-2222-3333-000000000001\\OUTBOUND\\0\\EO\\0\\";
    private static final String RECEIVER_KEY = "aaaaaaaa-1111-2222-3333-000000000001\\INBOUND\\0\\EO\\0\\";
    private static final String BOTH_SIDES = searchResponse("getMessagesByIDs", 2, false,
            message(ID, SENDER_KEY, "OUTBOUND", "success"), message(ID, RECEIVER_KEY, "INBOUND", "systemError"));

    private FakeMonitoringPo po;
    private MonitoringClient client;

    @BeforeEach
    void start() {
        po = new FakeMonitoringPo();
        client = new MonitoringClient(po.system);
    }

    @AfterEach
    void stop() {
        po.close();
    }

    /** The getMessagesByIDs request sent for one ID, with the expected archive flag. */
    private void assertLookedUp(String id, boolean archive) {
        assertThat(po.requests()).hasSize(1);
        Element op = operation(po.lastRequest(), "getMessagesByIDs");
        assertThat(childNames(op)).containsExactly(
                OP + " messageIds", OP + " referenceIds", OP + " correlationIds", OP + " archive");
        Element ids = child(op, OP, "messageIds");
        assertThat(childNames(ids)).containsExactly(JL + " String");
        assertThat(children(ids).getFirst().getTextContent()).isEqualTo(id);
        assertThat(children(child(op, OP, "referenceIds"))).isEmpty();
        assertThat(children(child(op, OP, "correlationIds"))).isEmpty();
        assertThat(child(op, OP, "archive").getTextContent()).isEqualTo(String.valueOf(archive));
    }

    @Test
    void keyIsPassedThrough() {
        po.respondSoap(BOTH_SIDES);

        Resolution r = KeyResolver.resolve(client, SENDER_KEY, null, false);

        assertThat(r).isEqualTo(new Resolution.Key(SENDER_KEY));
        assertThat(po.requests()).isEmpty(); // a value with a backslash is a key: no lookup
    }

    @Test
    void singleKeyResolved() {
        po.respondSoap(searchResponse("getMessagesByIDs", 1, false, message(ID, SENDER_KEY, "OUTBOUND", "success")));

        Resolution r = KeyResolver.resolve(client, ID, null, true);

        assertThat(r).isEqualTo(new Resolution.Key(SENDER_KEY));
        assertLookedUp(ID, true);
    }

    @Test
    void twoKeysAreAmbiguousWithBothCandidates() {
        po.respondSoap(BOTH_SIDES);

        Resolution r = KeyResolver.resolve(client, ID, null, false);

        assertThat(r).isInstanceOfSatisfying(Resolution.Ambiguous.class, a -> {
            assertThat(a.candidates()).extracting(Message::messageKey).containsExactly(SENDER_KEY, RECEIVER_KEY);
            assertThat(a.candidates()).extracting(Message::direction).containsExactly("OUTBOUND", "INBOUND");
            assertThat(a.candidates()).extracting(Message::messageId).containsOnly(ID);
        });
        assertLookedUp(ID, false);
    }

    @Test
    void directionNarrowsToOne() {
        po.respondSoap(BOTH_SIDES);

        Resolution inbound = KeyResolver.resolve(client, ID, "INBOUND", false);
        Resolution outbound = KeyResolver.resolve(client, ID, "OUTBOUND", false);

        assertThat(inbound).isEqualTo(new Resolution.Key(RECEIVER_KEY));
        assertThat(outbound).isEqualTo(new Resolution.Key(SENDER_KEY));
    }

    @Test
    void unknownIdIsNotFound() {
        po.respondSoap(searchResponse("getMessagesByIDs", 0, false));

        Resolution r = KeyResolver.resolve(client, "msg-9999", null, false);

        assertThat(r).isEqualTo(new Resolution.NotFound("msg-9999"));
        assertLookedUp("msg-9999", false);
    }

    @Test
    void directionWithoutMatchIsNotFound() {
        po.respondSoap(searchResponse("getMessagesByIDs", 1, false, message(ID, SENDER_KEY, "OUTBOUND", "success")));

        Resolution r = KeyResolver.resolve(client, ID, "INBOUND", false);

        assertThat(r).isEqualTo(new Resolution.NotFound(ID));
    }

    @Test
    void sameKeyTwiceCountsOnce() {
        po.respondSoap(searchResponse("getMessagesByIDs", 2, false,
                message(ID, SENDER_KEY, "OUTBOUND", "success"), message(ID, SENDER_KEY, "OUTBOUND", "success")));

        Resolution r = KeyResolver.resolve(client, ID, null, false);

        assertThat(r).isEqualTo(new Resolution.Key(SENDER_KEY));
    }

    @Test
    void directionMatchIgnoresCase() {
        po.respondSoap(BOTH_SIDES);

        Resolution r = KeyResolver.resolve(client, ID, "inbound", false);

        assertThat(r).isEqualTo(new Resolution.Key(RECEIVER_KEY));
    }
}
