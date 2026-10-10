package io.poxray.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.PoXray;
import io.poxray.SyntheticSca;
import io.poxray.TestData;
import io.poxray.json.Json;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.DirRef;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ViewsTest {

    private static final String XSD = """
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:a">
              <xsd:complexType name="Order"><xsd:sequence>
                <xsd:element name="Id" type="xsd:string"/>
                <xsd:element name="Item" type="xsd:string" minOccurs="0" maxOccurs="unbounded"/>
              </xsd:sequence></xsd:complexType></xsd:schema>""";

    private static IntegrationInventory inventory() {
        ObjRef dt = new ObjRef("ifmtypedef", "Order", "urn:a", "SWC 1.0", "g", "o1");
        Channel channel = new Channel(new DirRef(null, "RCV", "SOAP_RCV", "c1"), "receiver", "SOAP/SOAP", "HTTP",
                "SOAP", "AAE", Map.of("url", "http://x"), List.of(new IntegrationInventory.Module(0,
                        "CallSapAdapter", "L", null, Map.of())), null);
        var esr = new IntegrationInventory.Esr(List.of(), List.of(), List.of(), List.of(),
                List.of(new IntegrationInventory.DataType(dt, List.of(), XSD, null)), List.of(), List.of(), List.of(),
                List.of());
        return new IntegrationInventory("0.1", null, null, new IntegrationInventory.Query("RCV", "both"),
                List.of(new IntegrationInventory.Component(null, "RCV", null)), List.of(), List.of(channel), List.of(), esr,
                List.of());
    }

    @Test
    void overviewListsObjectsByNameWithoutDetails() {
        Views.IcoOverview o = Views.overview(inventory());

        assertThat(o.channels()).singleElement().satisfies(c -> {
            assertThat(c.adapter()).isEqualTo("SOAP/SOAP");
            assertThat(c.modules()).containsExactly("CallSapAdapter");
        });
        assertThat(o.esrObjects()).containsOnlyKeys("dataType").containsEntry("dataType", List.of("Order | urn:a"));
        assertThat(o.next()).contains("get_object");
    }

    @Test
    void findsObjectsCaseInsensitivelyWithDerivedFields() {
        IntegrationInventory inv = inventory();

        assertThat(Views.find(inv, "channel", "rcv|soap_rcv")).singleElement().isInstanceOf(Channel.class);
        assertThat(Views.find(inv, "dataType", "order")).singleElement()
                .isInstanceOfSatisfying(Views.DataTypeDetail.class, d -> assertThat(d.fields())
                        .extracting(f -> f.path()).contains("Id", "Item"));
        assertThat(Views.find(inv, "messageMapping", "Order")).isEmpty();
        assertThatThrownBy(() -> Views.find(inv, "bogus", "x")).hasMessageContaining("Unknown kind 'bogus'");
    }

    @Test
    void scaOverviewAndModuleDetail(@TempDir Path dir) {
        ScaInventory inv = PoXray.read(SyntheticSca.write(dir.resolve("calc.sca")));

        Views.ScaOverview o = Views.overview(inv);

        assertThat(o.ejbModules()).singleElement().satisfies(m -> assertThat(m.beans()).isEqualTo(1));
        String dc = o.ejbModules().getFirst().dc();
        assertThat(Views.find(inv, "ejbModule", dc)).hasSize(1);
        assertThat(Views.find(inv, "process", "none")).isEmpty();
    }

    @Test
    void processOutlineLeavesMappingsToSteps() {
        ScaInventory inv = PoXray.read(TestData.require("EONBPMDIGI00_0.sca"));

        Object outline = Views.find(inv, "process", "digisign_envelope").getFirst();
        String json = Json.mapper().writer().without(tools.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                .writeValueAsString(outline);

        assertThat(json).doesNotContain("\"inputMapping\":{").contains("\"mappings\":[\"inputMapping\"");
        assertThat(json.length()).isLessThan(60_000);
        assertThat(Views.find(inv, "step", "digisign_envelope/Create envelope recipient")).singleElement()
                .isInstanceOfSatisfying(Views.StepDetail.class, d -> assertThat(d.step().inputMapping()).isNotNull());
        assertThat(Views.find(inv, "processData", "digisign_envelope")).hasSize(1);
    }
}
