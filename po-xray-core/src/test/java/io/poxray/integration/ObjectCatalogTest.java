package io.poxray.integration;

import static io.poxray.po.FakePo.ref;
import static io.poxray.po.FakePo.result;
import static io.poxray.po.FakePo.xiObj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.IssueLog;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.ObjectDetail;
import io.poxray.model.IntegrationInventory.ObjectSummary;
import io.poxray.po.CachingObjectSource;
import io.poxray.po.FakePo;
import io.poxray.po.PoClient;
import io.poxray.xi.XiRef;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Search, read, where-used and classic configuration against a fake PO system. Classic configuration (receiver /
 * interface determination, receiver agreement) is synthetic: key names as reported by PO 7.50 SimpleQuery, no real
 * sample available yet.
 */
class ObjectCatalogTest {

    private static final List<String> RR_KEYS = IntegrationQuery.CLASSIC_KEYS.get("RoutingRelation");
    private static final List<String> MR_KEYS = IntegrationQuery.CLASSIC_KEYS.get("MappingRelation");
    private static final List<String> OB_KEYS = IntegrationQuery.CLASSIC_KEYS.get("OutboundBinding");

    private static FakePo fake() {
        FakePo po = new FakePo();
        po.query("rep", List.of("XI_TRAFO"), "NAME", result(Map.of("XI_TRAFO", List.of("NAME", "NAMESPACE")),
                List.<String[]>of(
                        new String[] {ref("S", "XI_TRAFO", "mm1", "MM_Order", "urn:a"), "Objednávka"},
                        new String[] {ref("S", "XI_TRAFO", "mm2", "MM_Invoice", "urn:b"), "Faktura"})));
        po.query("rep", List.of("MAPPING"), "NAME", result(Map.of("MAPPING", List.of("NAME", "NAMESPACE")),
                List.<String[]>of(new String[] {ref("S", "MAPPING", "om1", "OM_Order", "urn:a"), "",
                        ref("S", "XI_TRAFO", "mm1", "MM_Order", "urn:a") + ref("S", "ifmmessif", null, "Order_In",
                                "urn:a")})));
        po.query("dir", List.of("RoutingRelation"), null, result(Map.of("RoutingRelation", RR_KEYS),
                List.<String[]>of(new String[] {ref("D", "RoutingRelation", "rr1", "", "SND", "urn:a", "Order_Out", "",
                        "")})));
        po.query("dir", List.of("MappingRelation"), null, result(Map.of("MappingRelation", MR_KEYS),
                List.<String[]>of(new String[] {ref("D", "MappingRelation", "mr1", "urn:a", "Order_Out", "", "SND", "",
                        "RCV")})));
        po.query("dir", List.of("OutboundBinding"), null, result(Map.of("OutboundBinding", OB_KEYS),
                List.<String[]>of(new String[] {ref("D", "OutboundBinding", "ob1", "", "RCV", "urn:a", "Order_In", "",
                        "SND", "")})));
        po.object("RoutingRelation", "|SND|urn:a|Order_Out||", xiObj("D", "RoutingRelation", "rr1",
                List.of("", "SND", "urn:a", "Order_Out", "", ""), "<RoutingRelation><Condition>x</Condition></RoutingRelation>",
                List.<String[]>of(new String[] {"receiver", "D", "Service", "", "RCV"})));
        po.object("MappingRelation", "urn:a|Order_Out||SND||RCV", xiObj("D", "MappingRelation", "mr1",
                List.of("urn:a", "Order_Out", "", "SND", "", "RCV"), null, List.<String[]>of(
                        new String[] {"mapping", "S", "MAPPING", "OM_Order", "urn:a"})));
        po.object("OutboundBinding", "|RCV|urn:a|Order_In||SND|", xiObj("D", "OutboundBinding", "ob1",
                List.of("", "RCV", "urn:a", "Order_In", "", "SND", ""), null, List.<String[]>of(
                        new String[] {"channel", "D", "Channel", "", "RCV", "SOAP_RCV"})));
        po.object("MAPPING", "OM_Order|urn:a", xiObj("S", "MAPPING", "om1", List.of("OM_Order", "urn:a"), null,
                List.of()));
        po.object("Channel", "|RCV|SOAP_RCV", xiObj("D", "Channel", "ch1", List.of("", "RCV", "SOAP_RCV"),
                "<Channel><ChannelDirection>O</ChannelDirection><TransportProtocol>HTTP</TransportProtocol>"
                        + "<MessageProtocol>SOAP</MessageProtocol></Channel>", List.of()));
        return po;
    }

    @Test
    void searchFiltersLocallyAndDecodesTheQueryPage(@TempDir Path dir) {
        try (FakePo po = fake()) {
            ObjectCatalog catalog = catalog(po, dir);

            ObjectCatalog.SearchResult r = catalog.search(List.of("messageMapping"), "order", null, 10);

            assertThat(r.total()).isEqualTo(1);
            ObjectSummary s = r.objects().getFirst();
            assertThat(s.key()).containsEntry("NAME", "MM_Order").containsEntry("NAMESPACE", "urn:a");
            assertThat(s.description()).isEqualTo("Objednávka"); // page in ISO-8859-1, declared in Content-Type
            assertThat(catalog.search(List.of("XI_TRAFO"), "", "urn:b", 10).objects())
                    .extracting(o -> o.key().get("NAME")).containsExactly("MM_Invoice");
        }
    }

    @Test
    void whereUsedMatchesLinkListsByObjectId(@TempDir Path dir) {
        try (FakePo po = fake()) {
            ObjectCatalog catalog = catalog(po, dir);

            List<ObjectSummary> users = catalog.whereUsed(catalog.find("messageMapping", "mm_order", null));

            assertThat(users).extracting(u -> u.key().get("NAME")).containsExactly("OM_Order");
            ObjectCatalog.Hit valueMapping = new ObjectCatalog.Hit(
                    new XiRef("D", null, null, "ValueMapping", "v", List.of("x")), null);
            assertThatThrownBy(() -> catalog.whereUsed(List.of(valueMapping))).hasMessageContaining("not supported");
        }
    }

    @Test
    void readsUnknownTypesGenerically(@TempDir Path dir) {
        try (FakePo po = fake()) {
            ObjectCatalog catalog = catalog(po, dir);
            ObjectCatalog.Hit hit = catalog.find("receiverDetermination", "SND", null).getFirst();

            Object o = catalog.read(hit, new IssueLog());

            assertThat(o).isInstanceOfSatisfying(ObjectDetail.class, d -> {
                assertThat(d.id().key()).containsEntry("FROMSRVC", "SND").containsEntry("FROMACTION", "Order_Out");
                assertThat(d.links()).extracting(l -> l.role() + ":" + l.type() + ":" + l.key())
                        .containsExactly("receiver:Service:|RCV");
                assertThat(d.content()).contains("<Condition>x</Condition>");
            });
        }
    }

    @Test
    void classicConfigurationOfReceiverAndSender(@TempDir Path dir) {
        try (FakePo po = fake()) {
            PoClient client = new PoClient(po.system);
            IntegrationQuery q = new IntegrationQuery(client, new CachingObjectSource(client, dir, false));

            IntegrationInventory in = q.icos("rcv", IntegrationQuery.Direction.IN);

            assertThat(in.classicConfiguration()).extracting(d -> d.id().type())
                    .containsExactly("RoutingRelation", "MappingRelation", "OutboundBinding");
            assertThat(in.components()).extracting(c -> c.name()).containsExactly("RCV");
            assertThat(in.esr().operationMappings()).extracting(m -> m.id().name()).containsExactly("OM_Order");
            assertThat(in.channels()).extracting(c -> c.id().name() + ":" + c.adapter())
                    .containsExactly("SOAP_RCV:HTTP/SOAP");
            assertThat(in.issues()).extracting(i -> i.code()).contains("CLASSIC_CONFIGURATION");

            IntegrationInventory out = new IntegrationQuery(client, new CachingObjectSource(client, dir, false))
                    .icos("SND", IntegrationQuery.Direction.OUT);
            assertThat(out.classicConfiguration()).hasSize(3);
            assertThat(new IntegrationQuery(client, new CachingObjectSource(client, dir, false))
                    .icos("OTHER", IntegrationQuery.Direction.BOTH).classicConfiguration()).isEmpty();
        }
    }

    @Test
    void referencesComparesObjectIdsAcrossSwcvs() {
        XiRef target = new XiRef("S", "common", null, "FUNC_LIB", "f1", List.of("Utils", "urn:c"));
        XiRef linkFromOtherSwc = new XiRef("S", "app", null, "FUNC_LIB", "f1", List.of("Utils", "urn:c"));
        XiRef sameNameOtherObject = new XiRef("S", "app", null, "FUNC_LIB", "f2", List.of("Utils", "urn:c"));
        XiRef operation = new XiRef("S", "app", null, "ifmoper", null, List.of("Order_In.Create", "urn:a"));

        assertThat(ObjectCatalog.references(linkFromOtherSwc, target)).isTrue();
        assertThat(ObjectCatalog.references(sameNameOtherObject, target)).isFalse();
        assertThat(ObjectCatalog.references(operation,
                new XiRef("S", "app", null, "ifmmessif", "i1", List.of("Order_In", "urn:a")))).isTrue();
        assertThat(ObjectCatalog.typeId("MessageMapping")).isEqualTo("XI_TRAFO");
        assertThat(ObjectCatalog.typeId("receiverAgreement")).isEqualTo("OutboundBinding");
    }

    private static ObjectCatalog catalog(FakePo po, Path dir) {
        PoClient client = new PoClient(po.system);
        return new ObjectCatalog(client, new CachingObjectSource(client, dir, false));
    }
}
