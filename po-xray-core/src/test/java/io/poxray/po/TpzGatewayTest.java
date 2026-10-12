package io.poxray.po;

import static io.poxray.po.FakePo.xiObj;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.IssueLog;
import io.poxray.SyntheticTpz;
import io.poxray.TestData;
import io.poxray.integration.IntegrationQuery;
import io.poxray.integration.ObjectCatalog;
import io.poxray.model.IntegrationInventory.ObjectDetail;
import io.poxray.model.IntegrationInventory.ObjectSummary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A directory of TPZ exports as a PO system: discovery, read and where-used without an online connection. */
class TpzGatewayTest {

    private static final String ESR_META = "swcv=aaaa\nsystemtype=IBD 7.1\nsourcesystem=EONPROD\n";
    private static final String DIR_META = "systemtype=IBC 7.1\nsourcesystem=EONPROD\n";

    private static Path exports(Path dir) {
        SyntheticTpz.write(dir.resolve("esr.tpz"), ESR_META, List.of(
                xiObj("S", "XI_TRAFO", "mm1", List.of("MM_Order", "urn:a"), null, List.of()),
                xiObj("S", "MAPPING", "om1", List.of("OM_Order", "urn:a"), null, List.<String[]>of(
                        new String[] {"REQUEST_TRAFO", "S", "XI_TRAFO", "MM_Order", "urn:a"}))));
        SyntheticTpz.write(dir.resolve("directory.tpz"), DIR_META, List.of(
                xiObj("D", "Service", "sv1", List.of("", "BC_DIGISIGN"), null, List.of()),
                xiObj("D", "RoutingRelation", "rr1", List.of("", "SND", "urn:a", "Order_Out", "", ""),
                        "<RoutingRelation><Condition>x</Condition></RoutingRelation>", List.<String[]>of(
                                new String[] {"receiver", "D", "Service", "", "RCV"}))));
        return dir;
    }

    @Test
    void searchWhereUsedAndReadOverExports(@TempDir Path dir) {
        TpzGateway gateway = new TpzGateway(PoSystem.tpz("eon", exports(dir)));
        ObjectCatalog catalog = new ObjectCatalog(gateway, gateway);

        ObjectCatalog.SearchResult search = catalog.search(List.of("messageMapping"), "order", null, 10);
        assertThat(search.total()).isEqualTo(1);
        assertThat(search.objects().getFirst().key())
                .containsEntry("NAME", "MM_Order").containsEntry("NAMESPACE", "urn:a");

        List<ObjectSummary> users = catalog.whereUsed(catalog.find("messageMapping", "MM_Order", null));
        assertThat(users).extracting(u -> u.key().get("NAME")).containsExactly("OM_Order");

        ObjectCatalog.Hit rd = catalog.find("receiverDetermination", "SND", null).getFirst();
        Object read = catalog.read(rd, new IssueLog());
        assertThat(read).isInstanceOfSatisfying(ObjectDetail.class, d -> {
            assertThat(d.id().key()).containsEntry("FROMSRVC", "SND");
            assertThat(d.links()).extracting(l -> l.role() + ":" + l.type()).containsExactly("receiver:Service");
            assertThat(d.content()).contains("<Condition>x</Condition>");
        });

        IntegrationQuery query = new IntegrationQuery(gateway, gateway);
        assertThat(query.components("digi")).extracting(c -> c.name()).containsExactly("BC_DIGISIGN");
    }

    @Test
    void systemConfiguredWithTpzDirectoryNeedsNoUrlOrCredentials(@TempDir Path dir) throws IOException {
        Path systems = Files.createDirectories(dir.resolve("systems"));
        Path exports = exports(Files.createDirectories(dir.resolve("exports")));
        Files.writeString(systems.resolve("eon.properties"), "tpz=../exports\n");

        PoSystem system = PoSystem.load("eon", systems, Map.of());

        assertThat(system.tpz().toAbsolutePath().normalize()).isEqualTo(exports.toAbsolutePath().normalize());
        assertThat(system.location()).contains("exports");
        PoConnection connection = PoConnection.open(system, null, false);
        assertThat(connection.gateway()).isInstanceOf(TpzGateway.class);
        assertThat(connection.source()).isSameAs(connection.gateway());
        assertThatThrownBy(() -> new PoClient(system)).isInstanceOf(PoException.class).hasMessageContaining("tpz");
    }

    @Test
    void notesDuplicatesAndMixedSourceSystems(@TempDir Path dir) {
        SyntheticTpz.write(dir.resolve("a-esr.tpz"), ESR_META, List.of(
                xiObj("S", "XI_TRAFO", "mm1", List.of("MM_Order", "urn:a"), null, List.of())));
        SyntheticTpz.write(dir.resolve("b-esr.tpz"), "systemtype=IBD 7.1\nsourcesystem=OTHER\n", List.of(
                xiObj("S", "XI_TRAFO", "mm2", List.of("MM_Order", "urn:a"), null, List.of())));

        TpzGateway gateway = new TpzGateway(PoSystem.tpz("eon", dir));

        // the file loaded last (name order) wins
        ObjectCatalog catalog = new ObjectCatalog(gateway, gateway);
        assertThat(catalog.find("messageMapping", "MM_Order", null))
                .singleElement().satisfies(h -> assertThat(h.ref().oid()).isEqualTo("mm2"));
        assertThat(gateway.offlineNotes()).anySatisfy(n -> assertThat(n).contains("TPZ"))
                .anySatisfy(n -> assertThat(n).contains("several TPZ files"))
                .anySatisfy(n -> assertThat(n).contains("EONPROD").contains("OTHER"));
    }

    @Test
    void refusesMissingOrEmptyDirectory(@TempDir Path dir) {
        assertThatThrownBy(() -> new TpzGateway(PoSystem.tpz("eon", dir.resolve("missing"))))
                .isInstanceOf(PoException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> new TpzGateway(PoSystem.tpz("eon", dir)))
                .isInstanceOf(PoException.class).hasMessageContaining("No .tpz");
    }

    @Test
    void readsRealExportsFromTestdata() {
        Path dir = TestData.require("XI7_1_PPAS_AEM_1.0_of_ppas.cz.tpz").getParent();
        TpzGateway gateway = new TpzGateway(PoSystem.tpz("ppas", dir));
        ObjectCatalog catalog = new ObjectCatalog(gateway, gateway);

        ObjectCatalog.SearchResult mappings = catalog.search(List.of("operationMapping"), "", "", 50);
        assertThat(mappings.total()).isGreaterThanOrEqualTo(2);
        String name = mappings.objects().getFirst().key().get("NAME");
        List<ObjectSummary> users = catalog.whereUsed(catalog.find("messageMapping",
                catalog.search(List.of("messageMapping"), "", "", 1).objects().getFirst().key().get("NAME"), null));
        assertThat(users).isNotEmpty();
        assertThat(name).isNotBlank();
    }
}
