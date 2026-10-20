package io.poxray.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import io.poxray.json.Json;
import io.poxray.model.AttributesReport;
import io.poxray.model.AttributesReport.Attribute;
import io.poxray.model.AttributesReport.IcoRef;
import io.poxray.model.AttributesReport.ModuleRef;
import io.poxray.model.AttributesReport.Scenario;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.Condition;
import io.poxray.model.IntegrationInventory.ConditionLine;
import io.poxray.model.IntegrationInventory.DirRef;
import io.poxray.model.IntegrationInventory.DynamicRouting;
import io.poxray.model.IntegrationInventory.Endpoint;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.InterfaceAssignment;
import io.poxray.model.IntegrationInventory.Module;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.IntegrationInventory.Target;
import io.poxray.model.ScaInventory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

/**
 * {@link ScenarioAttributes} derives the eleven D-025 scenario attributes of every ICO of an
 * {@link IntegrationInventory}: a resolved value names the input path it came from, an undecidable value is
 * {@code ?} with the still possible candidates and a question.
 */
class ScenarioAttributesTest {

    /** D-025 attribute order and value sets (docs/DECISIONS.md, D-025). */
    private static final List<String> ORDER = List.of("runtime", "qos", "sender_type", "receiver_type",
            "receiver_count", "receiver_determination", "interface_determination", "mapping", "error_handling",
            "custom_exit", "inbound_conversion");
    private static final Map<String, List<String>> VALUES = Map.ofEntries(
            Map.entry("runtime", List.of("integrated-async", "integrated-sync", "decoupled")),
            Map.entry("qos", List.of("EO", "EOIO", "BE", "ALO")),
            Map.entry("sender_type", List.of("XI", "IDOC", "OTHER")),
            Map.entry("receiver_type", List.of("XI", "IDOC", "OTHER")),
            Map.entry("receiver_count", List.of("1", "n")),
            Map.entry("receiver_determination", List.of("static", "xpath", "custom", "reuse-xrd")),
            Map.entry("interface_determination", List.of("static", "xpath", "custom")),
            Map.entry("mapping", List.of("none", "one", "one-to-many-one-type", "one-to-many-many-types", "ordered")),
            Map.entry("error_handling", List.of("standard", "custom")),
            Map.entry("custom_exit", List.of("none", "pre", "post", "pre+post")),
            Map.entry("inbound_conversion", List.of("none", "custom")));

    private static final DirRef SND = ref("SND_SYS", "CC_SND");
    private static final DirRef RCV = ref("RCV_SYS", "CC_RCV");

    // ---------------------------------------------------------------- fixture builders (synthetic records)

    private static DirRef ref(String component, String name) {
        return new DirRef(null, component, name, null);
    }

    private static ObjRef iface(String name) {
        return new ObjRef("ifmmessif", name, "urn:example:orders", null, null, null);
    }

    private static ObjRef om(String name) {
        return new ObjRef("MAPPING", name, "urn:example:orders", null, null, null);
    }

    private static Module module(int position, String name) {
        return new Module(position, name, "L", "ns", Map.of());
    }

    private static Channel channel(DirRef id, String direction, String adapter, Module... modules) {
        return new Channel(id, direction, adapter, null, null, null, Map.of(), List.of(modules), null);
    }

    private static Channel xiSender(Module... modules) {
        return channel(SND, "sender", "XI/XI", modules);
    }

    private static Channel xiReceiver(Module... modules) {
        return channel(RCV, "receiver", "XI/XI", modules);
    }

    private static Target target(String name, String multiplicity) {
        return new Target(iface(name), multiplicity);
    }

    private static InterfaceAssignment assign(ObjRef mapping, Target... targets) {
        return new InterfaceAssignment("Synth_Out", null, mapping, List.of(targets), Map.of());
    }

    private static InterfaceAssignment oneToOne() {
        return assign(om("OM_Synth"), target("Synth_In", "1"));
    }

    private static Condition xpath() {
        return new Condition("/ns0:Order/Type = \"A\"",
                List.of(new ConditionLine(1, "/ns0:Order/Type", "EQ", "\"A\"")));
    }

    private static Receiver receiver(String component, String qos, Condition condition,
            List<InterfaceAssignment> interfaces, DirRef... channels) {
        return new Receiver(null, component, condition, qos, interfaces, List.of(channels));
    }

    private static Receiver plainReceiver(String qos) {
        return receiver("RCV_SYS", qos, null, List.of(oneToOne()), RCV);
    }

    private static Ico ico(DirRef senderChannel, DynamicRouting routing, Receiver... receivers) {
        return new Ico("00000000000000000000000000000001", "Synthetic ICO", "IF_Synth",
                new Endpoint(null, "SND_SYS", iface("Synth_Out"), List.of("Synth_Out"), senderChannel),
                List.of(receivers), routing, null, null, null);
    }

    private static Ico ico(Receiver... receivers) {
        return ico(SND, null, receivers);
    }

    private static IntegrationInventory inv(List<Ico> icos, Channel... channels) {
        return new IntegrationInventory("0.1", new ScaInventory.Generator("po-xray", "t", "2026-01-01T00:00:00Z"),
                null, null, List.of(), icos, List.of(channels), List.of(), null, List.of());
    }

    private static IntegrationInventory inv(Ico ico, Channel... channels) {
        return inv(List.of(ico), channels);
    }

    /** XI sender, one XI receiver, one 1:1 mapping, no modules; qos as given. */
    private static IntegrationInventory baseline(String qos) {
        return inv(ico(plainReceiver(qos)), xiSender(), xiReceiver());
    }

    private static AttributesReport derive(IntegrationInventory inventory) {
        AttributesReport report = ScenarioAttributes.derive(inventory, "icos.json");
        assertContract(report);
        return report;
    }

    private static Attribute attr(AttributesReport report, int scenario, String name) {
        List<Attribute> rows = report.scenarios().get(scenario).attributes().stream()
                .filter(a -> a.name().equals(name)).toList();
        assertThat(rows).as("rows named %s", name).hasSize(1);
        return rows.get(0);
    }

    private static Attribute attr(AttributesReport report, String name) {
        return attr(report, 0, name);
    }

    /** Row contract: a resolved row has a D-025 value and a source; an open row has candidates and a question. */
    private static void assertContract(AttributesReport report) {
        for (Scenario s : report.scenarios()) {
            assertThat(s.attributes()).extracting(Attribute::name).containsExactlyElementsOf(ORDER);
            for (Attribute a : s.attributes()) {
                List<String> allowed = VALUES.get(a.name());
                if ("?".equals(a.value())) {
                    assertThat(a.candidates()).as("candidates of %s", a.name()).isNotEmpty();
                    assertThat(allowed).as("candidates of %s", a.name()).containsAll(a.candidates());
                    List<String> inD025Order = allowed.stream().filter(a.candidates()::contains).toList();
                    assertThat(a.candidates()).as("D-025 order of %s", a.name()).containsExactlyElementsOf(inD025Order);
                    assertThat(a.question()).as("question of %s", a.name()).isNotBlank();
                } else {
                    assertThat(allowed).as("value of %s", a.name()).contains(a.value());
                    assertThat(a.source()).as("source of %s", a.name()).isNotBlank();
                    assertThat(a.candidates()).as("candidates of %s", a.name()).isNull();
                    assertThat(a.question()).as("question of %s", a.name()).isNull();
                }
            }
        }
    }

    private static void assertOpen(Attribute a, String... candidates) {
        assertThat(a.value()).as(a.name()).isEqualTo("?");
        assertThat(a.candidates()).as(a.name()).containsExactly(candidates);
        assertThat(a.question()).as(a.name()).isNotBlank();
    }

    private static void assertResolved(Attribute a, String value, String source) {
        assertThat(a.value()).as(a.name()).isEqualTo(value);
        assertThat(a.source()).as(a.name()).isEqualTo(source);
    }

    // ---------------------------------------------------------------- shape

    @Test
    void everyIcoGivesOneScenarioWithElevenAttributesInD025Order() {
        Ico second = new Ico("00000000000000000000000000000002", "Second", "IF_Second", ico().sender(),
                List.of(plainReceiver("EO")), null, null, null, null);
        AttributesReport report = derive(inv(List.of(ico(plainReceiver("BE")), second), xiSender(), xiReceiver()));

        assertThat(report.schemaVersion()).isEqualTo("0.1");
        assertThat(report.generator().name()).isEqualTo("po-xray");
        assertThat(report.inputs()).containsExactly("icos.json");
        assertThat(report.scenarios()).extracting(s -> s.ico().generatedFrom())
                .containsExactly("IF_Synth", "IF_Second");
        assertThat(report.scenarios()).allSatisfy(s -> {
            assertThat(s.file()).isEqualTo("icos.json");
            assertThat(s.attributes()).hasSize(11).extracting(Attribute::name).containsExactlyElementsOf(ORDER);
        });
    }

    @Test
    void icoRefCarriesTheScenarioKey() {
        Receiver other = receiver("RCV2_SYS", "EO", null, List.of(oneToOne()), RCV);
        IcoRef ref = derive(inv(ico(plainReceiver("EO"), other), xiSender(), xiReceiver())).scenarios().get(0).ico();

        assertThat(ref.oid()).isEqualTo("00000000000000000000000000000001");
        assertThat(ref.generatedFrom()).isEqualTo("IF_Synth");
        assertThat(ref.description()).isEqualTo("Synthetic ICO");
        assertThat(ref.senderComponent()).isEqualTo("SND_SYS");
        assertThat(ref.senderInterface()).isEqualTo("Synth_Out");
        assertThat(ref.senderNamespace()).isEqualTo("urn:example:orders");
        assertThat(ref.receiverComponents()).containsExactly("RCV_SYS", "RCV2_SYS");
    }

    // ---------------------------------------------------------------- qos

    @Test
    void qosThatAgreesAfterNormalisationIsResolvedFromTheFirstReceiver() {
        AttributesReport report = derive(inv(List.of(
                ico(plainReceiver("ExactlyOnce"), plainReceiver("eo")),
                ico(plainReceiver("ExactlyOnceInOrder"), plainReceiver("EOIO")),
                ico(plainReceiver("BestEffort"), plainReceiver("be"))), xiSender(), xiReceiver()));

        assertResolved(attr(report, 0, "qos"), "EO", "icos[0].receivers[0].qualityOfService");
        assertResolved(attr(report, 1, "qos"), "EOIO", "icos[1].receivers[0].qualityOfService");
        assertResolved(attr(report, 2, "qos"), "BE", "icos[2].receivers[0].qualityOfService");
    }

    @Test
    void qosThatDiffersBetweenReceiversIsOpenWithTheDistinctValues() {
        Attribute qos = attr(derive(inv(ico(plainReceiver("BE"), plainReceiver("ExactlyOnce")), xiSender(),
                xiReceiver())), "qos");

        assertOpen(qos, "EO", "BE");
        assertThat(qos.question()).contains("EO", "BE");
    }

    @Test
    void qosWithoutReceiversOrValuesIsOpenWithAllFourAndAloIsNeverDerived() {
        AttributesReport report = derive(inv(List.of(ico(plainReceiver(null), plainReceiver(null)), ico()),
                xiSender(), xiReceiver()));

        assertOpen(attr(report, 0, "qos"), "EO", "EOIO", "BE", "ALO");
        assertOpen(attr(report, 1, "qos"), "EO", "EOIO", "BE", "ALO");
    }

    // ---------------------------------------------------------------- runtime

    @Test
    void runtimeIsIntegratedSyncForBestEffort() {
        AttributesReport report = derive(baseline("BE"));

        Attribute runtime = attr(report, "runtime");
        assertResolved(runtime, "integrated-sync", attr(report, "qos").source());
        assertThat(runtime.note()).isEqualTo("from qos BE");
    }

    @Test
    void runtimeIsAQuestionForEoAndEoio() {
        for (String qos : List.of("EO", "EOIO")) {
            Attribute runtime = attr(derive(baseline(qos)), "runtime");

            assertOpen(runtime, "integrated-async", "decoupled");
            assertThat(runtime.question()).as(qos).isEqualTo("tenant or scenario choice");
        }
    }

    @Test
    void runtimeIsOpenWithAllThreeWhenQosIsOpen() {
        assertOpen(attr(derive(baseline(null)), "runtime"), "integrated-async", "integrated-sync", "decoupled");
    }

    // ---------------------------------------------------------------- sender_type and channel lookup

    @Test
    void senderTypeIsClassifiedFromTheResolvedSenderChannelsAdapter() {
        DirRef idoc = ref("SND_SYS", "CC_SND_IDOC");
        DirRef idocLower = ref("SND_SYS", "CC_SND_IDOC2");
        DirRef soap = ref("SND_SYS", "CC_SND_SOAP");
        AttributesReport report = derive(inv(List.of(
                ico(SND, null, plainReceiver("EO")),
                ico(idoc, null, plainReceiver("EO")),
                ico(idocLower, null, plainReceiver("EO")),
                ico(soap, null, plainReceiver("EO"))),
                xiReceiver(), xiSender(), channel(idoc, "sender", "IDoc/IDoc XML"),
                channel(idocLower, "sender", "idoc/IDOC XML"), channel(soap, "sender", "SOAP/SOAP 1.1")));

        assertResolved(attr(report, 0, "sender_type"), "XI", "channels[1].adapter");
        assertResolved(attr(report, 1, "sender_type"), "IDOC", "channels[2].adapter");
        assertResolved(attr(report, 2, "sender_type"), "IDOC", "channels[3].adapter");
        assertResolved(attr(report, 3, "sender_type"), "OTHER", "channels[4].adapter");
    }

    @Test
    void senderTypeOfAMissingOrUnresolvableChannelIsOpen() {
        AttributesReport report = derive(inv(List.of(
                ico(ref("SND_SYS", "CC_NOT_IN_INVENTORY"), null, plainReceiver("EO")),
                ico((DirRef) null, null, plainReceiver("EO"))), xiSender(), xiReceiver()));

        for (int i = 0; i < 2; i++) {
            Attribute sender = attr(report, i, "sender_type");
            assertOpen(sender, "XI", "IDOC", "OTHER");
            assertThat(sender.source()).isEqualTo("icos[" + i + "].sender.channel");
        }
    }

    @Test
    void channelLookupMatchesByOidBeforeName() {
        DirRef byName = new DirRef(null, "SND_SYS", "CC_SND", "0000000000000000000000000000000A");
        DirRef byOid = new DirRef(null, "SND_SYS", "CC_SND_RENAMED", "0000000000000000000000000000000B");
        DirRef reference = new DirRef(null, "SND_SYS", "CC_SND", "0000000000000000000000000000000B");
        AttributesReport report = derive(inv(ico(reference, null, plainReceiver("EO")),
                channel(byName, "sender", "IDoc/IDoc XML"), channel(byOid, "sender", "XI/XI"), xiReceiver()));

        assertResolved(attr(report, "sender_type"), "XI", "channels[1].adapter");
    }

    @Test
    void channelLookupFallsBackToPartyComponentAndNameWhenAnOidIsMissing() {
        DirRef stored = new DirRef(null, "SND_SYS", "CC_SND", "0000000000000000000000000000000A");
        AttributesReport report = derive(inv(ico(SND, null, plainReceiver("EO")),
                channel(ref("OTHER_SYS", "CC_SND"), "sender", "IDoc/IDoc XML"), channel(stored, "sender", "XI/XI"),
                xiReceiver()));

        assertResolved(attr(report, "sender_type"), "XI", "channels[1].adapter");
    }

    // ---------------------------------------------------------------- receiver_type

    @Test
    void receiverTypeOfOneClassIsResolvedFromTheFirstReceiverChannel() {
        DirRef rcv2 = ref("RCV2_SYS", "CC_RCV2");
        AttributesReport report = derive(inv(ico(plainReceiver("EO"),
                receiver("RCV2_SYS", "EO", null, List.of(oneToOne()), rcv2)),
                xiSender(), channel(rcv2, "receiver", "XI/XI"), xiReceiver()));

        assertResolved(attr(report, "receiver_type"), "XI", "channels[2].adapter");
    }

    @Test
    void receiverTypeOverMixedChannelsIsOpenWithTheClassesSeen() {
        DirRef file = ref("RCV2_SYS", "CC_RCV2_FILE");
        AttributesReport report = derive(inv(ico(
                receiver("RCV2_SYS", "EO", null, List.of(oneToOne()), file), plainReceiver("EO")),
                xiSender(), channel(file, "receiver", "File/File"), xiReceiver()));

        assertOpen(attr(report, "receiver_type"), "XI", "OTHER");
    }

    @Test
    void receiverTypeWithoutAnyResolvedChannelIsOpenWithAllThree() {
        AttributesReport report = derive(inv(ico(receiver("RCV_SYS", "EO", null, List.of(oneToOne()))), xiSender()));

        assertOpen(attr(report, "receiver_type"), "XI", "IDOC", "OTHER");
    }

    // ---------------------------------------------------------------- receiver_count

    @Test
    void receiverCountFollowsReceiversAndDynamicRouting() {
        AttributesReport report = derive(inv(List.of(
                ico(plainReceiver("EO")),
                ico(plainReceiver("EO"), plainReceiver("EO")),
                ico(SND, new DynamicRouting(false, List.of(om("OM_Routing"))), plainReceiver("EO")),
                ico(SND, new DynamicRouting(false, List.of()), plainReceiver("EO"))), xiSender(), xiReceiver()));

        assertResolved(attr(report, 0, "receiver_count"), "1", "icos[0].receivers");
        assertResolved(attr(report, 1, "receiver_count"), "n", "icos[1].receivers");
        assertResolved(attr(report, 2, "receiver_count"), "n", "icos[2].dynamicRouting");
        assertResolved(attr(report, 3, "receiver_count"), "1", "icos[3].receivers");
    }

    @Test
    void receiverCountWithoutReceiversAndRoutingIsOpen() {
        assertOpen(attr(derive(inv(ico(), xiSender())), "receiver_count"), "1", "n");
    }

    // ---------------------------------------------------------------- receiver_determination

    @Test
    void receiverDeterminationIsStaticWithoutConditions() {
        Receiver blank = receiver("RCV2_SYS", "EO", new Condition(" ", List.of()), List.of(oneToOne()), RCV);
        AttributesReport report = derive(inv(ico(plainReceiver("EO"), blank), xiSender(), xiReceiver()));

        assertResolved(attr(report, "receiver_determination"), "static", "icos[0].receivers");
    }

    @Test
    void receiverDeterminationIsXpathWhenAReceiverHasACondition() {
        Receiver byText = receiver("RCV2_SYS", "EO", new Condition("/ns0:Order/Type = \"A\"", List.of()),
                List.of(oneToOne()), RCV);
        Receiver byLines = receiver("RCV2_SYS", "EO", new Condition(null,
                List.of(new ConditionLine(1, "/ns0:Order/Type", "EQ", "\"A\""))), List.of(oneToOne()), RCV);
        AttributesReport report = derive(inv(List.of(ico(plainReceiver("EO"), byText),
                ico(plainReceiver("EO"), byLines)), xiSender(), xiReceiver()));

        assertResolved(attr(report, 0, "receiver_determination"), "xpath", "icos[0].receivers[1].condition");
        assertResolved(attr(report, 1, "receiver_determination"), "xpath", "icos[1].receivers[1].condition");
    }

    @Test
    void receiverDeterminationIsReuseXrdForDynamicRoutingAndCustomIsNeverDerived() {
        AttributesReport report = derive(inv(ico(SND, new DynamicRouting(false, List.of(om("OM_Routing"))),
                receiver("RCV_SYS", "EO", xpath(), List.of(oneToOne()), RCV)), xiSender(), xiReceiver()));

        Attribute rd = attr(report, "receiver_determination");
        assertResolved(rd, "reuse-xrd", "icos[0].dynamicRouting.mappings[0]");
        assertThat(rd.note()).isEqualTo("custom if the mapping is rewritten");
    }

    // ---------------------------------------------------------------- interface_determination

    @Test
    void interfaceDeterminationIsStaticWithoutAssignmentConditions() {
        assertResolved(attr(derive(baseline("EO")), "interface_determination"), "static",
                "icos[0].receivers[0].interfaces");
    }

    @Test
    void interfaceDeterminationIsXpathWhenAnAssignmentHasACondition() {
        InterfaceAssignment conditional = new InterfaceAssignment("Synth_Out", xpath(), om("OM_Synth2"),
                List.of(target("Synth2_In", "1")), Map.of());
        AttributesReport report = derive(inv(ico(plainReceiver("EO"),
                receiver("RCV_SYS", "EO", null, List.of(oneToOne(), conditional), RCV)), xiSender(), xiReceiver()));

        assertResolved(attr(report, "interface_determination"), "xpath",
                "icos[0].receivers[1].interfaces[1].condition");
    }

    // ---------------------------------------------------------------- mapping

    @Test
    void mappingIsNoneWhenNoAssignmentHasAMapping() {
        Receiver noMapping = receiver("RCV_SYS", "EO", null, List.of(assign(null, target("Synth_In", "1"))), RCV);
        AttributesReport report = derive(inv(ico(noMapping), xiSender(), xiReceiver()));

        assertThat(attr(report, "mapping").value()).isEqualTo("none");
    }

    @Test
    void mappingIsOneForASingleTargetAndNamesTheFirstAssignmentWithAMapping() {
        Receiver r = receiver("RCV_SYS", "EO", null,
                List.of(assign(null, target("Synth_In", "1")), oneToOne()), RCV);
        AttributesReport report = derive(inv(ico(r), xiSender(), xiReceiver()));

        assertResolved(attr(report, "mapping"), "one", "icos[0].receivers[0].interfaces[1].mapping");
    }

    @Test
    void mappingIsOneToManyOneTypeForSeveralTargetsOfOneInterfaceOrAnUnboundedMultiplicity() {
        String note = "ordered if PO keeps order at runtime (not in the dump)";
        AttributesReport report = derive(inv(List.of(
                ico(receiver("RCV_SYS", "EO", null, List.of(assign(om("OM_Split"), target("Order_In", "1"),
                        target("Order_In", "1"))), RCV)),
                ico(receiver("RCV_SYS", "EO", null, List.of(assign(om("OM_Split"), target("Order_In",
                        "0..unbounded"))), RCV)),
                ico(receiver("RCV_SYS", "EO", null, List.of(assign(om("OM_Split"), target("Order_In", "1..n"))),
                        RCV))), xiSender(), xiReceiver()));

        for (int i = 0; i < 3; i++) {
            Attribute mapping = attr(report, i, "mapping");
            assertResolved(mapping, "one-to-many-one-type", "icos[" + i + "].receivers[0].interfaces[0].mapping");
            assertThat(mapping.note()).isEqualTo(note);
        }
    }

    @Test
    void mappingIsOneToManyManyTypesForTargetsOfDifferentInterfaces() {
        Receiver r = receiver("RCV_SYS", "EO", null,
                List.of(assign(om("OM_Split"), target("Order_In", "1"), target("Invoice_In", "1"))), RCV);
        Attribute mapping = attr(derive(inv(ico(r), xiSender(), xiReceiver())), "mapping");

        assertResolved(mapping, "one-to-many-many-types", "icos[0].receivers[0].interfaces[0].mapping");
        assertThat(mapping.note()).isEqualTo("ordered if PO keeps order at runtime (not in the dump)");
    }

    @Test
    void mappingIsOneTypeOnlyWhenTargetsShareNameAndNamespaceSoSameNameInAnotherNamespaceIsManyTypes() {
        Target orders = new Target(new ObjRef("ifmmessif", "Order_In", "urn:example:orders", null, null, null), "1");
        Target ordersAgain = new Target(new ObjRef("ifmmessif", "Order_In", "urn:example:orders", null, null, null),
                "1");
        Target ordersV2 = new Target(new ObjRef("ifmmessif", "Order_In", "urn:example:orders:v2", null, null, null),
                "1");
        AttributesReport report = derive(inv(List.of(
                ico(receiver("RCV_SYS", "EO", null, List.of(assign(om("OM_Split"), orders, ordersV2)), RCV)),
                ico(receiver("RCV_SYS", "EO", null, List.of(assign(om("OM_Split"), orders, ordersAgain)), RCV))),
                xiSender(), xiReceiver()));

        assertResolved(attr(report, 0, "mapping"), "one-to-many-many-types",
                "icos[0].receivers[0].interfaces[0].mapping");
        assertResolved(attr(report, 1, "mapping"), "one-to-many-one-type",
                "icos[1].receivers[0].interfaces[0].mapping");
    }

    @Test
    void mappingOfMixedClassesIsOpenWithTheClassesAndOrderedIsNeverDerived() {
        Receiver split = receiver("RCV2_SYS", "EO", null,
                List.of(assign(om("OM_Split"), target("Order_In", "1"), target("Order_In", "1"))), RCV);
        AttributesReport report = derive(inv(ico(split, plainReceiver("EO")), xiSender(), xiReceiver()));

        assertOpen(attr(report, "mapping"), "one", "one-to-many-one-type");
    }

    // ---------------------------------------------------------------- error_handling

    @Test
    void errorHandlingIsAlwaysAQuestionAboutTheTenantSwitch() {
        Attribute eh = attr(derive(baseline("BE")), "error_handling");

        assertOpen(eh, "standard", "custom");
        assertThat(eh.question()).isEqualTo("tenant-level switch CustomXError_Enabled (D-025); record the request");
        assertThat(eh.source()).isNull();
    }

    // ---------------------------------------------------------------- custom_exit

    @Test
    void customExitIsNoneWhenOnlyAdapterBeansRunWithOrWithoutLocalejbsPrefix() {
        AttributesReport report = derive(inv(ico(plainReceiver("EO")),
                xiSender(module(1, "localejbs/CallSapAdapter"), module(2, "CallXIAdapter")),
                xiReceiver(module(1, "XISOAPAdapterBean"), module(2, "localejbs/CallAdapterWithMessageAsXMB"))));

        assertResolved(attr(report, "custom_exit"), "none", "icos[0].sender.channel");
        assertThat(report.scenarios().get(0).modules()).isNullOrEmpty();
    }

    @Test
    void customExitIsPreForAModuleOfTheSenderChannel() {
        AttributesReport report = derive(inv(ico(plainReceiver("EO")), xiReceiver(),
                xiSender(module(1, "localejbs/MyPreModule"), module(2, "localejbs/CallSapAdapter"))));

        assertResolved(attr(report, "custom_exit"), "pre", "channels[1].modules[0].name");
        assertThat(report.scenarios().get(0).modules())
                .containsExactly(new ModuleRef("sender", "CC_SND", 1, "localejbs/MyPreModule"));
    }

    @Test
    void customExitIsPostForAModuleOfAReceiverChannel() {
        AttributesReport report = derive(inv(ico(plainReceiver("EO")), xiSender(),
                xiReceiver(module(1, "CallSapAdapter"), module(2, "MyPostModule"))));

        assertResolved(attr(report, "custom_exit"), "post", "channels[1].modules[1].name");
        assertThat(report.scenarios().get(0).modules())
                .containsExactly(new ModuleRef("receiver", "CC_RCV", 2, "MyPostModule"));
    }

    @Test
    void customExitIsPrePlusPostWhenBothSidesHaveOneAndNamesTheSenderModule() {
        AttributesReport report = derive(inv(ico(plainReceiver("EO")),
                xiReceiver(module(1, "CallSapAdapter"), module(2, "MyPostModule")),
                xiSender(module(1, "MyPreModule"), module(2, "CallSapAdapter"))));

        assertResolved(attr(report, "custom_exit"), "pre+post", "channels[1].modules[0].name");
        assertThat(report.scenarios().get(0).modules()).containsExactly(
                new ModuleRef("sender", "CC_SND", 1, "MyPreModule"),
                new ModuleRef("receiver", "CC_RCV", 2, "MyPostModule"));
    }

    // ---------------------------------------------------------------- inbound_conversion

    @Test
    void inboundConversionIsNoneForXiAndIdocSenders() {
        DirRef idoc = ref("SND_SYS", "CC_SND_IDOC");
        AttributesReport report = derive(inv(List.of(ico(SND, null, plainReceiver("EO")),
                ico(idoc, null, plainReceiver("EO"))),
                xiReceiver(), xiSender(), channel(idoc, "sender", "IDoc/IDoc XML")));

        assertResolved(attr(report, 0, "inbound_conversion"), "none", "channels[1].adapter");
        assertResolved(attr(report, 1, "inbound_conversion"), "none", "channels[2].adapter");
    }

    @Test
    void inboundConversionIsAQuestionForOtherSenders() {
        DirRef soap = ref("SND_SYS", "CC_SND_SOAP");
        Attribute ic = attr(derive(inv(ico(soap, null, plainReceiver("EO")), xiReceiver(),
                channel(soap, "sender", "SOAP/SOAP 1.1"))), "inbound_conversion");

        assertOpen(ic, "none", "custom");
        assertThat(ic.question()).isEqualTo("does the sender payload need conversion to XML (Step03)");
    }

    @Test
    void inboundConversionIsOpenWhenTheSenderTypeIsOpen() {
        Attribute ic = attr(derive(inv(ico(ref("SND_SYS", "CC_MISSING"), null, plainReceiver("EO")),
                xiReceiver())), "inbound_conversion");

        assertOpen(ic, "none", "custom");
    }

    // ---------------------------------------------------------------- acceptance spec §8 decisions

    @Test
    void adapterClassificationReadsEitherPartSoSoapXiIsXiOnBothSidesIgnoringCase() {
        DirRef sndUpper = ref("SND_SYS", "CC_SND_SOAPXI");
        DirRef sndLower = ref("SND_SYS", "CC_SND_SOAPXI_LOWER");
        DirRef rcvUpper = ref("RCV_SYS", "CC_RCV_SOAPXI");
        DirRef rcvLower = ref("RCV_SYS", "CC_RCV_SOAPXI_LOWER");
        AttributesReport report = derive(inv(List.of(
                ico(sndUpper, null, receiver("RCV_SYS", "EO", null, List.of(oneToOne()), rcvUpper)),
                ico(sndLower, null, receiver("RCV_SYS", "EO", null, List.of(oneToOne()), rcvLower))),
                channel(sndUpper, "sender", "SOAP/XI"), channel(rcvUpper, "receiver", "SOAP/XI"),
                channel(sndLower, "sender", "soap/xi"), channel(rcvLower, "receiver", "soap/xi")));

        assertResolved(attr(report, 0, "sender_type"), "XI", "channels[0].adapter");
        assertResolved(attr(report, 0, "receiver_type"), "XI", "channels[1].adapter");
        assertResolved(attr(report, 1, "sender_type"), "XI", "channels[2].adapter");
        assertResolved(attr(report, 1, "receiver_type"), "XI", "channels[3].adapter");
        assertResolved(attr(report, 0, "inbound_conversion"), "none", "channels[0].adapter");
    }

    @Test
    void messageTransformBeanAloneOnTheSenderIsACustomConversionNotAnExitWithOrWithoutLocalejbsPrefix() {
        DirRef soap = ref("SND_SYS", "CC_SND_SOAP");
        AttributesReport report = derive(inv(List.of(
                ico(SND, null, plainReceiver("EO")),
                ico(soap, null, plainReceiver("EO"))),
                xiReceiver(),
                xiSender(module(1, "localejbs/AF_Modules/MessageTransformBean"),
                        module(2, "localejbs/CallSapAdapter")),
                channel(soap, "sender", "SOAP/SOAP 1.1", module(1, "AF_Modules/MessageTransformBean"),
                        module(2, "CallSapAdapter"))));

        assertResolved(attr(report, 0, "inbound_conversion"), "custom", "channels[1].modules[0].name");
        assertResolved(attr(report, 0, "custom_exit"), "none", "icos[0].sender.channel");
        assertThat(report.scenarios().get(0).modules())
                .containsExactly(new ModuleRef("sender", "CC_SND", 1, "localejbs/AF_Modules/MessageTransformBean"));

        assertResolved(attr(report, 1, "inbound_conversion"), "custom", "channels[2].modules[0].name");
        assertResolved(attr(report, 1, "custom_exit"), "none", "icos[1].sender.channel");
        assertThat(report.scenarios().get(1).modules())
                .containsExactly(new ModuleRef("sender", "CC_SND_SOAP", 1, "AF_Modules/MessageTransformBean"));
    }

    @Test
    void messageTransformBeanBesideAnotherSenderModuleKeepsTheConversionCustomAndTheExitPre() {
        AttributesReport report = derive(inv(ico(plainReceiver("EO")), xiReceiver(),
                xiSender(module(1, "localejbs/AF_Modules/MessageTransformBean"), module(2, "localejbs/MyPreModule"),
                        module(3, "localejbs/CallSapAdapter"))));

        assertResolved(attr(report, "inbound_conversion"), "custom", "channels[1].modules[0].name");
        assertResolved(attr(report, "custom_exit"), "pre", "channels[1].modules[1].name");
        assertThat(report.scenarios().get(0).modules()).containsExactlyInAnyOrder(
                new ModuleRef("sender", "CC_SND", 1, "localejbs/AF_Modules/MessageTransformBean"),
                new ModuleRef("sender", "CC_SND", 2, "localejbs/MyPreModule"));
    }

    @Test
    void messageTransformBeanOnAReceiverChannelIsAPostExitAndLeavesAnXiSendersConversionNone() {
        AttributesReport report = derive(inv(ico(plainReceiver("EO")), xiSender(),
                xiReceiver(module(1, "localejbs/AF_Modules/MessageTransformBean"),
                        module(2, "localejbs/CallSapAdapter"))));

        assertResolved(attr(report, "custom_exit"), "post", "channels[1].modules[0].name");
        assertResolved(attr(report, "inbound_conversion"), "none", "channels[0].adapter");
        assertThat(report.scenarios().get(0).modules())
                .containsExactly(new ModuleRef("receiver", "CC_RCV", 1, "localejbs/AF_Modules/MessageTransformBean"));
    }

    @Test
    void receiverTypeOfAReceiverWhoseChannelIsNotInTheInventoryIsOpenWithAllThree() {
        AttributesReport report = derive(inv(ico(
                receiver("RCV_SYS", "EO", null, List.of(oneToOne()), ref("RCV_SYS", "CC_RCV_NOT_IN_INVENTORY"))),
                xiSender(), xiReceiver()));

        assertOpen(attr(report, "receiver_type"), "XI", "IDOC", "OTHER");
    }

    // ---------------------------------------------------------------- files, Markdown, JSON

    @Test
    void deriveReadsInventoryFilesAndKeepsThePathsAsGiven(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("icos-A.json");
        Path b = dir.resolve("icos-B.json");
        Json.write(baseline("BE"), a);
        Json.write(inv(List.of(ico(plainReceiver("EO")), ico(plainReceiver("EO"), plainReceiver("EO"))),
                xiSender(), xiReceiver()), b);

        AttributesReport report = ScenarioAttributes.derive(List.of(a, b));
        assertContract(report);

        assertThat(report.inputs()).containsExactly(a.toString(), b.toString());
        assertThat(report.scenarios()).extracting(Scenario::file)
                .containsExactly(a.toString(), b.toString(), b.toString());
        assertThat(attr(report, 0, "runtime").value()).isEqualTo("integrated-sync");
        assertResolved(attr(report, 2, "receiver_count"), "n", "icos[1].receivers");
    }

    @Test
    void deriveOnAMissingFileFailsWithAnIoException(@TempDir Path dir) {
        Path missing = dir.resolve("absent.json");

        assertThatThrownBy(() -> ScenarioAttributes.derive(List.of(missing))).isInstanceOf(IOException.class);
    }

    @Test
    void markdownShowsScenarioTableQuestionsAndModules() {
        DirRef soap = ref("SND_SYS", "CC_SND_SOAP");
        AttributesReport report = derive(inv(ico(soap, null, plainReceiver("EO")), xiReceiver(),
                channel(soap, "sender", "SOAP/SOAP 1.1", module(1, "MyPreModule"), module(2, "CallSapAdapter"))));

        String md = ScenarioAttributes.markdown(report);
        List<String> lines = Arrays.asList(md.split("\n"));

        assertThat(lines).contains("# Scenario attributes", "## IF_Synth", "Synthetic ICO",
                "sender SND_SYS Synth_Out → receivers RCV_SYS", "| attribute | value | source |", "### Questions",
                "### Modules");
        assertThat(lines).filteredOn(l -> l.startsWith("|")).hasSize(13);
        for (String name : ORDER) {
            Pattern row = Pattern.compile("^\\|\\s*`?" + Pattern.quote(name) + "`?\\s*\\|.*");
            assertThat(lines).as("table row %s", name).filteredOn(l -> row.matcher(l).matches()).hasSize(1);
        }
        String runtimeRow = lines.stream().filter(l -> l.matches("^\\|\\s*`?runtime`?\\s*\\|.*")).findFirst()
                .orElseThrow();
        assertThat(runtimeRow).contains("? (", "integrated-async", "decoupled");
        String exitRow = lines.stream().filter(l -> l.matches("^\\|\\s*`?custom_exit`?\\s*\\|.*")).findFirst()
                .orElseThrow();
        assertThat(exitRow).matches("^\\|\\s*`?custom_exit`?\\s*\\|\\s*`?pre`?\\s*\\|.*")
                .contains("channels[1].modules[0].name");

        long open = report.scenarios().get(0).attributes().stream().filter(a -> "?".equals(a.value())).count();
        List<String> questionBullets = new ArrayList<>(lines.stream()
                .filter(l -> l.matches("^[-*] `[a-z_]+` — .+")).toList());
        assertThat(questionBullets).hasSize((int) open);
        assertThat(questionBullets).anySatisfy(l -> assertThat(l).endsWith("`runtime` — tenant or scenario choice"));
        assertThat(questionBullets).anySatisfy(l -> assertThat(l).endsWith(
                "`error_handling` — tenant-level switch CustomXError_Enabled (D-025); record the request"));
        assertThat(questionBullets).anySatisfy(l -> assertThat(l).endsWith(
                "`inbound_conversion` — does the sender payload need conversion to XML (Step03)"));
        assertThat(lines).anySatisfy(l -> assertThat(l).matches("^[-*] sender CC_SND_SOAP #1 MyPreModule$"));
        assertThat(md.indexOf("## IF_Synth")).isLessThan(md.indexOf("### Questions"));
        assertThat(md.indexOf("### Questions")).isLessThan(md.indexOf("### Modules"));
    }

    @Test
    void markdownOmitsModulesWithoutExitModulesAndHeadsWithTheOidWithoutGeneratedFrom() {
        Ico noIflow = new Ico("00000000000000000000000000000009", "Hand-made ICO", null, ico().sender(),
                List.of(plainReceiver("BE")), null, null, null, null);
        String md = ScenarioAttributes.markdown(derive(inv(noIflow, xiSender(), xiReceiver())));

        assertThat(md.split("\n")).contains("## 00000000000000000000000000000009", "### Questions")
                .doesNotContain("### Modules");
    }

    @Test
    void jsonRoundTripsThroughTheMapper() {
        DirRef soap = ref("SND_SYS", "CC_SND_SOAP");
        AttributesReport report = derive(inv(List.of(baseline("BE").icos().get(0),
                ico(soap, new DynamicRouting(false, List.of(om("OM_Routing"))), plainReceiver("EO"))),
                xiReceiver(), xiSender(), channel(soap, "sender", "SOAP/SOAP 1.1", module(1, "MyPreModule"))));

        String json = Json.write(report);
        AttributesReport back = Json.mapper().readValue(json, AttributesReport.class);

        assertThat(Json.write(back)).isEqualTo(json);
        assertThat(back.scenarios()).hasSize(2);
        assertThat(back.scenarios().get(1).attributes()).extracting(Attribute::name, Attribute::value)
                .contains(tuple("receiver_determination", "reuse-xrd"),
                        tuple("custom_exit", "pre"));
        assertThat(back.scenarios().get(1).modules())
                .containsExactly(new ModuleRef("sender", "CC_SND_SOAP", 1, "MyPreModule"));
    }

    @Test
    void jsonAlwaysCarriesAnEmptyIssuesListThatRoundTripsAsEmptyNotNull() {
        AttributesReport report = derive(baseline("BE"));
        assertThat(report.issues()).isNotNull().isEmpty();

        String json = Json.write(report);
        assertThat(json).contains("\"issues\" : [ ]");
        JsonNode issues = Json.mapper().readTree(json).get("issues");
        assertThat(issues).isNotNull();
        assertThat(issues.isArray()).isTrue();
        assertThat(issues.size()).isZero();

        AttributesReport back = Json.mapper().readValue(json, AttributesReport.class);
        assertThat(back.issues()).isNotNull().isEmpty();
    }
}
