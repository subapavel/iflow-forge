package io.poxray.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.IssueLog;
import io.poxray.TestData;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.xi.InMemoryObjectSource;
import io.poxray.xi.TpzArchive;
import io.poxray.xi.XiObject;
import org.junit.jupiter.api.Test;

/** ICO and ESR expansion on the TPZ exports of xipod (testdata/, skipped when absent). */
class IntegrationReaderTest {

    @Test
    void expandsIcoWithChannelsFromIflowExport() {
        InMemoryObjectSource source = new InMemoryObjectSource()
                .addAll(TpzArchive.open(TestData.require("XI7_1_directory-objs.tpz")).objects())
                .addAll(TpzArchive.open(TestData.require("SAPCRM_ApplicationStatus.tpz")).objects());
        IssueLog issues = new IssueLog();
        IntegrationReader reader = new IntegrationReader(source, issues);

        Ico ico = reader.icos(source.ofType("AllInOne")).getFirst();

        assertThat(ico.generatedFrom()).isEqualTo("SAPCRM_ApplicationStatus");
        assertThat(ico.sender().component()).isEqualTo("CD1CLNT300");
        assertThat(ico.sender().iface().name()).isEqualTo("ApplicationStatus_Out");
        assertThat(ico.sender().operation()).isEqualTo("UpdateRequestConfirmation");
        assertThat(ico.noReceiverBehaviour()).isEqualTo("error");
        assertThat(ico.receivers()).singleElement().satisfies(r -> {
            assertThat(r.component()).isEqualTo("V3S_D");
            assertThat(r.interfaces()).singleElement().satisfies(a -> {
                assertThat(a.iface().name()).isEqualTo("StateUpdate_In");
                assertThat(a.mapping().name())
                        .isEqualTo("ApplicationStatus_UpdateRequestConfirmation_to_StateUpdate_RequestConfirmation_SetEportalId");
                assertThat(a.multiplicity()).isEqualTo("1");
            });
            assertThat(r.channels()).extracting(IntegrationInventory.DirRef::name).containsExactly("REST_RCV_StateUpdate");
        });

        assertThat(reader.channels()).extracting(c -> c.id().name() + ":" + c.direction() + ":" + c.adapter())
                .containsExactlyInAnyOrder("SOAP_SND_XI:sender:SOAP/XI", "REST_RCV_StateUpdate:receiver:HTTP/REST");
        assertThat(reader.channels()).filteredOn(c -> c.id().name().equals("SOAP_SND_XI")).singleElement()
                .satisfies(c -> assertThat(c.modules()).singleElement().satisfies(m -> {
                    assertThat(m.name()).isEqualTo("CallSapAdapter");
                    assertThat(m.parameters()).containsEntry("ignoreDuplicate", "true");
                }));
        // ESR objects of PPAS_V3S / PPAS_SAPCRM are not in the exports: reported, not guessed
        assertThat(issues.issues()).extracting(i -> i.code()).containsOnly("UNRESOLVED_OBJECT");
    }

    @Test
    void expandsOperationMappingChainInEsrExport() {
        InMemoryObjectSource source = new InMemoryObjectSource()
                .addAll(TpzArchive.open(TestData.require("XI7_1_PPAS_AEM_1.0_of_ppas.cz.tpz")).objects());
        IntegrationReader reader = new IntegrationReader(source, new IssueLog());

        for (XiObject om : source.ofType("MAPPING")) {
            reader.expand(om.id());
        }
        source.ofType("ifmmessif").forEach(si -> reader.expand(si.id()));
        // Email_Out refers to a message type of another namespace (not exported): expand the exported one directly
        source.ofType("ifmmessage").forEach(mt -> reader.expand(mt.id()));
        IntegrationInventory.Esr esr = reader.esr();

        assertThat(esr.operationMappings()).hasSize(2).allSatisfy(om -> {
            assertThat(om.steps()).extracting(IntegrationInventory.MappingStep::kind)
                    .containsExactly("request", "response", "fault");
            assertThat(om.sourceOperation()).isNotNull();
        });
        // request: message mapping from the sender's message type to the WSDL message of the receiver
        assertThat(esr.operationMappings().getFirst().steps().getFirst()).satisfies(step -> {
            assertThat(step.program().type()).isEqualTo("XI_TRAFO");
            assertThat(step.sourceMessage().type()).isEqualTo("ifmmessage");
            assertThat(step.targetMessage().type()).isEqualTo("ifmextmes");
        });
        assertThat(esr.mappingPrograms()).isNotEmpty()
                .allSatisfy(p -> assertThat(p.type()).isEqualTo("MAP_ARCHIVE_PRG")); // Java fault mappings
        assertThat(esr.messageMappings()).isNotEmpty();
        assertThat(esr.externalDefinitions()).extracting(d -> d.id().name())
                .contains("WSDL_zmenseniSouboru", "WSDL_pdfPodleSablony");
        assertThat(esr.serviceInterfaces()).filteredOn(si -> si.id().name().equals("Email_Out")).singleElement()
                .satisfies(si -> {
                    assertThat(si.category()).isEqualTo("O");
                    assertThat(si.operations()).singleElement().satisfies(op -> {
                        assertThat(op.name()).isEqualTo("CreateRequest");
                        assertThat(op.mode()).isEqualTo("async");
                    });
                });
        assertThat(esr.dataTypes()).filteredOn(dt -> dt.id().name().equals("EmailCreateRequestMessage")).singleElement()
                .satisfies(dt -> {
                    assertThat(dt.uses()).extracting(IntegrationInventory.ObjRef::name).containsExactly("EmailAttachment");
                    assertThat(dt.xsd()).contains("complexType", "EmailCreateRequestMessage");
                });
    }
}
