package io.poxray.integration;

import static io.poxray.po.FakePo.ref;
import static io.poxray.po.FakePo.result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.poxray.integration.BpmIntegration.InterfaceIntegration;
import io.poxray.integration.BpmIntegration.ProcessIntegration;
import io.poxray.model.BpmComponent;
import io.poxray.model.EsrOperationMapping;
import io.poxray.model.Interface;
import io.poxray.model.Process;
import io.poxray.model.QNameRef;
import io.poxray.model.Ref;
import io.poxray.model.ServiceReference;
import io.poxray.po.CachingObjectSource;
import io.poxray.po.FakePo;
import io.poxray.po.PoClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Cross-reference of BPM processes (built by hand, no SCA needed) with a fake PO system: inbound and outbound
 * interface usages, the ESR operation mapping of a service reference, and the Directory / ESR objects using them.
 */
class BpmIntegrationTest {

    private static final List<String> SI_KEYS = List.of("NAME", "NAMESPACE");
    private static final List<String> ICO_KEYS = List.of("SNDPRT", "SNDCMP", "NS", "NAME", "RCVPRT", "RCVCMP");

    private static List<BpmComponent> bpm() {
        Interface outbound = new Interface("if1", "CreateEnvelope", "urn:a", "rep/wsdl/a.services",
                List.of(), List.of(new Interface.Usage("DigiSign Envelope", new Ref("n5", "Send Envelope"),
                        "outbound")));
        Interface inbound = new Interface("if2", "Start_In", "urn:a", "rep/wsdl/a.services",
                List.of(), List.of(new Interface.Usage("DigiSign Envelope", new Ref("n1", "Start"), "start"),
                        new Interface.Usage("Download Completed", new Ref("n1", "Start"), "start")));
        Interface local = new Interface("if3", "LocalOnly", "urn:local", "rep/wsdl/b.services",
                List.of(), List.of(new Interface.Usage("DigiSign Envelope", new Ref("n9", "Notify"),
                        "notification")));
        ServiceReference sr = new ServiceReference("sr1", "uuid1", "WS", "XI", new QNameRef("CreateEnvelope", "urn:a"),
                null, null, "sappo_om", Map.of(),
                new EsrOperationMapping("OM_Create", "urn:a", "om1", "aaaa", null, null));
        BpmComponent.Catalog catalog = new BpmComponent.Catalog(List.of(outbound, inbound, local), List.of(sr),
                List.of(), List.of(), List.of(), List.of(), List.of());
        return List.of(new BpmComponent("eon/digisign", List.of(process("p1", "DigiSign Envelope"),
                process("p2", "Download Completed")), catalog));
    }

    private static Process process(String id, String name) {
        return new Process(id, name, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), null);
    }

    private static FakePo fake() {
        FakePo po = new FakePo();
        // service interfaces in the ESR (direct find query and listing share the NAME condition)
        po.query("rep", List.of("ifmmessif"), "NAME", result(Map.of("ifmmessif", SI_KEYS), List.<String[]>of(
                new String[] {ref("S", "ifmmessif", "si1", "CreateEnvelope", "urn:a"), "Envelope out"},
                new String[] {ref("S", "ifmmessif", "si2", "Start_In", "urn:a"), "Start"})));
        // operation mappings: referrer listing of the interfaces and direct find of OM_Create
        po.query("rep", List.of("MAPPING"), "NAME", result(Map.of("MAPPING", SI_KEYS), List.<String[]>of(
                new String[] {ref("S", "MAPPING", "om1", "OM_Create", "urn:a"), "",
                        ref("S", "ifmmessif", "si1", "CreateEnvelope", "urn:a")})));
        // Directory referrers of a service interface
        po.query("dir", List.of("AllInOne", "RoutingRelation", "MappingRelation", "InboundBinding", "OutboundBinding"),
                "MODIFYUSER", result(Map.of("AllInOne", ICO_KEYS), List.<String[]>of(
                        new String[] {ref("D", "AllInOne", "ico_out", "", "BC_DIGISIGN", "urn:a", "CreateEnvelope",
                                "", "RCV"), "",
                                ref("S", "ifmmessif", "si1", "CreateEnvelope", "urn:a")},
                        new String[] {ref("D", "AllInOne", "ico_in", "", "SND", "urn:a", "Start_In", "",
                                "BC_DIGISIGN"), "",
                                ref("S", "ifmmessif", "si2", "Start_In", "urn:a")})));
        // Directory referrers of an operation mapping
        po.query("dir", List.of("AllInOne", "MappingRelation"), "MODIFYUSER",
                result(Map.of("AllInOne", ICO_KEYS), List.<String[]>of(
                        new String[] {ref("D", "AllInOne", "ico_out", "", "BC_DIGISIGN", "urn:a", "CreateEnvelope",
                                "", "RCV"), "",
                                ref("S", "MAPPING", "om1", "OM_Create", "urn:a")})));
        return po;
    }

    @Test
    void crossReferencesOneProcess(@TempDir Path dir) {
        try (FakePo po = fake()) {
            List<ProcessIntegration> report = BpmIntegration.report(bpm(), "digisign envelope", catalog(po, dir));

            assertThat(report).hasSize(1);
            ProcessIntegration p = report.getFirst();
            assertThat(p.process()).isEqualTo("DigiSign Envelope");
            assertThat(p.dc()).isEqualTo("eon/digisign");
            assertThat(p.interfaces()).extracting(InterfaceIntegration::name)
                    .containsExactly("CreateEnvelope", "Start_In", "LocalOnly");

            InterfaceIntegration out = p.interfaces().get(0);
            assertThat(out.uses()).extracting(u -> u.node() + ":" + u.role()).containsExactly("Send Envelope:outbound");
            assertThat(out.bindingType()).isEqualTo("XI");
            assertThat(out.serviceGroup()).isEqualTo("sappo_om");
            assertThat(out.operationMapping().name()).isEqualTo("OM_Create");
            assertThat(out.esr()).singleElement().satisfies(s -> {
                assertThat(s.key()).containsEntry("NAME", "CreateEnvelope");
                assertThat(s.swc()).isEqualTo("ACME, 1.0");
            });
            assertThat(out.usedBy()).extracting(s -> s.type() + ":" + s.key().get("NAME"))
                    .containsExactlyInAnyOrder("MAPPING:OM_Create", "AllInOne:CreateEnvelope");
            assertThat(out.operationMappingUsedBy()).extracting(s -> s.type() + ":" + s.key().get("RCVCMP"))
                    .containsExactly("AllInOne:RCV");

            InterfaceIntegration in = p.interfaces().get(1);
            assertThat(in.uses()).extracting(u -> u.node() + ":" + u.role()).containsExactly("Start:start");
            assertThat(in.bindingType()).isNull();
            assertThat(in.operationMapping()).isNull();
            assertThat(in.usedBy()).singleElement().satisfies(s -> {
                assertThat(s.type()).isEqualTo("AllInOne");
                assertThat(s.key()).containsEntry("SNDCMP", "SND").containsEntry("RCVCMP", "BC_DIGISIGN");
            });
            assertThat(in.operationMappingUsedBy()).isEmpty();

            InterfaceIntegration local = p.interfaces().get(2);
            assertThat(local.esr()).isEmpty();
            assertThat(local.usedBy()).isEmpty();
        }
    }

    @Test
    void reportsEveryProcessWithItsOwnUsages(@TempDir Path dir) {
        try (FakePo po = fake()) {
            List<ProcessIntegration> report = BpmIntegration.report(bpm(), "", catalog(po, dir));

            assertThat(report).extracting(ProcessIntegration::process)
                    .containsExactly("DigiSign Envelope", "Download Completed");
            ProcessIntegration download = report.get(1);
            assertThat(download.interfaces()).hasSize(1);
            assertThat(download.interfaces().getFirst().name()).isEqualTo("Start_In");
            assertThat(download.interfaces().getFirst().uses()).hasSize(1);
        }
    }

    @Test
    void unknownProcessListsTheNames(@TempDir Path dir) {
        try (FakePo po = fake()) {
            ObjectCatalog catalog = catalog(po, dir);
            assertThatThrownBy(() -> BpmIntegration.report(bpm(), "nope", catalog))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DigiSign Envelope").hasMessageContaining("Download Completed");
            assertThatThrownBy(() -> BpmIntegration.report(List.of(), "", catalog))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No BPM process");
        }
    }

    private static ObjectCatalog catalog(FakePo po, Path dir) {
        PoClient client = new PoClient(po.system);
        return new ObjectCatalog(client, new CachingObjectSource(client, dir, false));
    }
}
