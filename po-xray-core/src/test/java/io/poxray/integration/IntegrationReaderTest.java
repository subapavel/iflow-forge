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
        assertThat(ico.sender().operations()).containsExactly("UpdateRequestConfirmation");
        assertThat(ico.noReceiverBehaviour()).isEqualTo("error");
        assertThat(ico.receivers()).singleElement().satisfies(r -> {
            assertThat(r.component()).isEqualTo("V3S_D");
            assertThat(r.interfaces()).singleElement().satisfies(a -> {
                assertThat(a.operation()).isEqualTo("UpdateRequestConfirmation");
                assertThat(a.mapping().name())
                        .isEqualTo("ApplicationStatus_UpdateRequestConfirmation_to_StateUpdate_RequestConfirmation_SetEportalId");
                assertThat(a.targets()).singleElement().satisfies(t -> {
                    assertThat(t.iface().name()).isEqualTo("StateUpdate_In");
                    assertThat(t.multiplicity()).isEqualTo("1");
                });
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

        IntegrationInventory inv = new IntegrationInventory("0.1", null,
                new IntegrationInventory.Source(null, null, java.util.List.of("XI7_1_directory-objs.tpz")),
                new IntegrationInventory.Query("V3S_D", "both"), java.util.List.of(), java.util.List.of(ico),
                reader.channels(), reader.esr(), issues.issues());
        inv = new IntegrationInventory(inv.schemaVersion(), new io.poxray.model.ScaInventory.Generator("po-xray", "t", "now"),
                inv.source(), inv.query(), inv.components(), inv.icos(), inv.channels(), inv.esr(), inv.issues());
        String md = io.poxray.doc.Documentation.render(inv, io.poxray.doc.DocFormat.MARKDOWN, java.util.Locale.ENGLISH);
        String jira = io.poxray.doc.Documentation.render(inv, io.poxray.doc.DocFormat.JIRA, java.util.Locale.forLanguageTag("cs"));
        assertThat(md).contains("# Integrations of V3S\\_D", "### CD1CLNT300 \\| ApplicationStatus\\_Out",
                "`StateUpdate_In [1]`", "`V3S_D | REST_RCV_StateUpdate`");
        assertThat(jira).contains("h1. Integrace komponenty V3S\\_D", "||Operace||Podmínka||Mapping||Cílová rozhraní||Parametry||");
    }

    /** Link structure of an ICO with two sender operations and a 1:n mapping, as found on xipod (neutral names). */
    private static final String MULTI_ICO = """
            <p1:xiObj xmlns:p1="urn:sap-com:xi"><p1:idInfo VID="v"><p1:vc vcType="D"/>
            <p1:key typeID="AllInOne" oid="ico1"><p1:elem/><p1:elem>SND</p1:elem><p1:elem>urn:a</p1:elem><p1:elem>Order_Out</p1:elem><p1:elem/><p1:elem/></p1:key></p1:idInfo>
            <p1:generic><p1:lnks>
            %s
            </p1:lnks></p1:generic>
            <p1:content><AllInOne><ReceiverConfigurations><ReceiverConfiguration><ReceiverId>R1</ReceiverId>
              <Receiver><ServiceExtractor><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>RCV</VALUE></TRD_EXTRACTOR></ServiceExtractor></Receiver>
              <QualityOfService>EO</QualityOfService>
              <InterfaceDeterminations>
                <InterfaceDetermination><Counter>0</Counter><Operation>Change</Operation><SplitItems>
                  <SplitItem><SplitCounter>0</SplitCounter><Multiplicity>1</Multiplicity></SplitItem>
                  <SplitItem><SplitCounter>1</SplitCounter><Multiplicity>0..1</Multiplicity></SplitItem></SplitItems></InterfaceDetermination>
                <InterfaceDetermination><Counter>1</Counter><Operation>Create</Operation><InterfaceConditionId>C2</InterfaceConditionId><SplitItems>
                  <SplitItem><SplitCounter>0</SplitCounter><Multiplicity>1</Multiplicity></SplitItem></SplitItems></InterfaceDetermination>
              </InterfaceDeterminations></ReceiverConfiguration></ReceiverConfigurations>
              <ReceiverAssignmentList><ReceiverAssignment><ReceiverConditionId>C1</ReceiverConditionId>
                <ReceiverIds><ReceiverId>R1</ReceiverId></ReceiverIds></ReceiverAssignment></ReceiverAssignmentList>
              <Conditions>
                <RDS_CONDSHORT><CONDITIONID>C1</CONDITIONID>
                  <CONDLINE><COMPGROUP>1</COMPGROUP><COMPOP>EQ</COMPOP>
                    <LEXTRACTOR><TRD_EXTRACTOR><TYPE>CT</TYPE><VALUE/><COBJNS>http://sap.com/xi/XI/System/SOAP</COBJNS><COBJNAME>XHeaderName1</COBJNAME></TRD_EXTRACTOR></LEXTRACTOR>
                    <REXTRACTOR><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>301</VALUE></TRD_EXTRACTOR></REXTRACTOR></CONDLINE>
                  <CONDLINE><COMPGROUP>2</COMPGROUP><COMPOP>CP</COMPOP>
                    <LEXTRACTOR><TRD_EXTRACTOR><TYPE>XP</TYPE><VALUE>/Order/Type</VALUE></TRD_EXTRACTOR></LEXTRACTOR>
                    <REXTRACTOR><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>Z*</VALUE></TRD_EXTRACTOR></REXTRACTOR></CONDLINE>
                  <CONDLINE><COMPGROUP>2</COMPGROUP><COMPOP>EX</COMPOP>
                    <LEXTRACTOR><TRD_EXTRACTOR><TYPE>XP</TYPE><VALUE>/Order/Id</VALUE></TRD_EXTRACTOR></LEXTRACTOR></CONDLINE>
                </RDS_CONDSHORT>
                <RDS_CONDSHORT><CONDITIONID>C2</CONDITIONID>
                  <CONDLINE><COMPGROUP>1</COMPGROUP><COMPOP>NE</COMPOP>
                    <LEXTRACTOR><TRD_EXTRACTOR><TYPE>XP</TYPE><VALUE>/Order/Status</VALUE></TRD_EXTRACTOR></LEXTRACTOR>
                    <REXTRACTOR><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>X</VALUE></TRD_EXTRACTOR></REXTRACTOR></CONDLINE>
                </RDS_CONDSHORT>
              </Conditions></AllInOne></p1:content></p1:xiObj>
            """.formatted(String.join("\n",
            link("senderOperation", 0, "S", "ifmoper", "Order_Out.Change", "urn:a"),
            link("senderOperation", 1, "S", "ifmoper", "Order_Out.Create", "urn:a"),
            link("MAP0", 0, "S", "MAPPING", "OM_Change", "urn:a"),
            link("MAP0", 1, "S", "MAPPING", "OM_Create", "urn:a"),
            link("IIF0$0", 0, "S", "ifmmessif", "Confirmation_In", "urn:a"),
            link("IIF0$0", 1, "S", "ifmmessif", "Order_In", "urn:a"),
            link("IIF0$1", 0, "S", "ifmmessif", "Order_In", "urn:a"),
            link("PAR0_0_LOOKUP", 0, "D", "Channel", "", "RCV", "RFC_LOOKUP"),
            link("receiverChannel", 12345, "D", "Channel", "", "RCV", "SOAP_RCV")));

    private static String link(String role, int kpos, String vcType, String type, String... key) {
        String vc = "S".equals(vcType) ? "<p1:vc vcType=\"S\" swcGuid=\"aaaa\"/>" : "<p1:vc vcType=\"D\"/>";
        StringBuilder elems = new StringBuilder();
        for (String k : key) {
            elems.append("<p1:elem>").append(k).append("</p1:elem>");
        }
        return "<p1:lnkRole role=\"" + role + "\" kpos=\"" + kpos + "\"><p1:lnk rMode=\"A\">" + vc
                + "<p1:key typeID=\"" + type + "\">" + elems + "</p1:key></p1:lnk></p1:lnkRole>";
    }

    @Test
    void readsMultiOperationIcoWithOneToManyMapping() {
        IssueLog issues = new IssueLog();
        IntegrationReader reader = new IntegrationReader(new InMemoryObjectSource(), issues);

        Ico ico = reader.icos(java.util.List.of(io.poxray.xi.XiObjectParser.parse(MULTI_ICO, "multi"))).getFirst();

        assertThat(ico.sender().operations()).containsExactly("Change", "Create");
        assertThat(ico.receivers()).singleElement().satisfies(r -> {
            assertThat(r.component()).isEqualTo("RCV");
            assertThat(r.qualityOfService()).isEqualTo("EO");
            // lines of a group: AND, groups: OR
            assertThat(r.condition().text()).isEqualTo(
                    "context:XHeaderName1 (http://sap.com/xi/XI/System/SOAP) = \"301\" OR (/Order/Type like \"Z*\" AND exists /Order/Id)");
            assertThat(r.condition().lines()).hasSize(3);
            assertThat(r.channels()).extracting(IntegrationInventory.DirRef::name).containsExactly("SOAP_RCV");
            assertThat(r.interfaces()).hasSize(2);
            assertThat(r.interfaces().get(0)).satisfies(a -> {
                assertThat(a.operation()).isEqualTo("Change");
                assertThat(a.mapping().name()).isEqualTo("OM_Change");
                assertThat(a.targets()).extracting(t -> t.iface().name() + ":" + t.multiplicity())
                        .containsExactly("Confirmation_In:1", "Order_In:0..1");
                assertThat(a.parameters()).containsOnlyKeys("LOOKUP");
            });
            assertThat(r.interfaces().get(1)).satisfies(a -> {
                assertThat(a.mapping().name()).isEqualTo("OM_Create");
                assertThat(a.condition().text()).isEqualTo("/Order/Status != \"X\"");
                assertThat(a.targets()).extracting(t -> t.iface().name()).containsExactly("Order_In");
            });
        });
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
