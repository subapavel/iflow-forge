package io.iflowforge.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.poxray.json.Json;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.DirRef;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.InterfaceAssignment;
import io.poxray.model.IntegrationInventory.Module;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.IntegrationInventory.ServiceInterface;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;

/**
 * The benchmark fixture {@code knowledge/pipeline/benchmark/sample-ico.json}: a synthetic PO ICO in the
 * {@link IntegrationInventory} shape (the output of {@code po-xray ico}) that the migration procedure is run on. It
 * must parse strictly into the model and describe one XI-to-IDoc, Exactly-Once ICO with a routing condition, a
 * mapping and a MessageTransformBean module, with synthetic names only.
 */
class BenchmarkFixtureTest {

    private static final String FIXTURE = "benchmark/sample-ico.json";

    private static final String SENDER_COMPONENT = "SND_SYS";
    private static final String RECEIVER_COMPONENT = "RCV_SYS";
    /** Exactly Once as the reader passes it through ({@code IntegrationReader} QualityOfService, model Receiver). */
    private static final String EXACTLY_ONCE = "EO";
    /** XI sender adapter as the reader renders transport/message protocol (model Channel, {@code SOAP/XI}). */
    private static final String XI_ADAPTER = "SOAP/XI";
    private static final String SENDER_DIRECTION = "sender";
    private static final String RECEIVER_DIRECTION = "receiver";
    private static final String ASYNC = "async";
    private static final String MESSAGE_TRANSFORM_BEAN = "AF_Modules/MessageTransformBean";
    private static final String SYNTHETIC_NAMESPACE_PREFIX = "urn:example:";
    private static final List<String> FORBIDDEN_FRAGMENTS = List.of("czint", ".corp", ".com", ".cz", "http");

    // --- fixture access -------------------------------------------------------------------------------------------

    private static Path fixture() {
        KnowledgeBase.skipIfRequested("benchmark");
        Path file = KnowledgeBase.pipeline().resolve(FIXTURE);
        assertThat(file).as("benchmark fixture knowledge/pipeline/%s", FIXTURE).isRegularFile();
        return file;
    }

    /** The fixture as the model; a key that is not a record component fails the read. */
    private static IntegrationInventory inventory() {
        return Json.mapper().readerFor(IntegrationInventory.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(fixture());
    }

    private static Ico onlyIco(IntegrationInventory inventory) {
        assertThat(inventory.icos()).as("icos").hasSize(1);
        return inventory.icos().getFirst();
    }

    private static Receiver onlyReceiver(Ico ico) {
        assertThat(ico.receivers()).as("receivers").hasSize(1);
        return ico.receivers().getFirst();
    }

    private static InterfaceAssignment onlyInterface(Receiver receiver) {
        assertThat(receiver.interfaces()).as("receiver interfaces").hasSize(1);
        return receiver.interfaces().getFirst();
    }

    /** The one entry of {@code channels} named by {@code ref}. */
    private static Channel channel(IntegrationInventory inventory, DirRef ref) {
        assertThat(ref).as("channel reference").isNotNull();
        assertThat(ref.name()).as("channel reference name").isNotBlank();
        assertThat(inventory.channels()).as("channels").isNotNull();
        List<Channel> named = inventory.channels().stream()
                .filter(c -> c.id() != null && ref.name().equals(c.id().name()))
                .toList();
        assertThat(named).as("channels named %s", ref.name()).hasSize(1);
        return named.getFirst();
    }

    private static void assertNamed(ObjRef ref, String what) {
        assertThat(ref).as(what).isNotNull();
        assertThat(ref.name()).as(what + " name").isNotBlank();
        assertThat(ref.namespace()).as(what + " namespace").isNotBlank();
    }

    // --- model ----------------------------------------------------------------------------------------------------

    @Test
    void fixtureParsesStrictlyIntoTheInventoryModelWithHeaderAndSenderComponent() {
        IntegrationInventory inventory = inventory();

        assertThat(inventory.schemaVersion()).as("schemaVersion").isNotBlank();
        assertThat(inventory.generator()).as("generator").isNotNull();
        assertThat(inventory.source()).as("source").isNotNull();
        assertThat(inventory.query()).as("query").isNotNull();
        assertThat(inventory.components()).as("components")
                .extracting(IntegrationInventory.Component::name)
                .containsExactly(SENDER_COMPONENT);
        Ico ico = onlyIco(inventory);
        assertThat(ico.description()).as("ico description").isNotBlank();
        assertThat(ico.modifiedBy()).as("ico modifiedBy").isNotBlank();
    }

    @Test
    void theOnlyIcoHasASyntheticSenderInterfaceAndAListedSenderChannel() {
        IntegrationInventory inventory = inventory();
        Ico ico = onlyIco(inventory);

        assertThat(ico.sender()).as("sender").isNotNull();
        assertThat(ico.sender().component()).as("sender component").isEqualTo(SENDER_COMPONENT);
        assertNamed(ico.sender().iface(), "sender iface");
        assertThat(ico.sender().iface().namespace()).as("sender iface namespace")
                .startsWith(SYNTHETIC_NAMESPACE_PREFIX);
        assertThat(ico.sender().channel()).as("sender channel").isNotNull();
        assertThat(ico.sender().channel().name()).as("sender channel name").isNotBlank();
        assertThat(channel(inventory, ico.sender().channel()).id().name())
                .isEqualTo(ico.sender().channel().name());
    }

    @Test
    void theOnlyReceiverIsExactlyOnceWithAConditionAndOneMappedInterface() {
        Receiver receiver = onlyReceiver(onlyIco(inventory()));

        assertThat(receiver.component()).as("receiver component").isEqualTo(RECEIVER_COMPONENT);
        assertThat(receiver.qualityOfService()).as("receiver qualityOfService").isEqualTo(EXACTLY_ONCE);
        assertThat(receiver.condition()).as("receiver condition").isNotNull();
        assertThat(receiver.condition().text()).as("receiver condition text").isNotBlank();
        assertThat(receiver.condition().lines()).as("receiver condition lines")
                .isNotEmpty()
                .allSatisfy(line -> {
                    assertThat(line.left()).as("condition line left").isNotBlank();
                    assertThat(line.operator()).as("condition line operator").isNotBlank();
                    assertThat(line.right()).as("condition line right").isNotBlank();
                });
        assertNamed(onlyInterface(receiver).mapping(), "receiver interface mapping");
    }

    @Test
    void senderChannelIsXiWithMessageTransformBeanAndReceiverChannelIsIdoc() {
        IntegrationInventory inventory = inventory();
        Ico ico = onlyIco(inventory);
        Receiver receiver = onlyReceiver(ico);
        assertThat(ico.sender()).as("sender").isNotNull();

        Channel sender = channel(inventory, ico.sender().channel());
        assertThat(sender.direction()).as("sender channel direction").isEqualTo(SENDER_DIRECTION);
        assertThat(sender.adapter()).as("sender channel adapter").isEqualTo(XI_ADAPTER);
        assertThat(sender.adapter()).as("sender adapter = transportProtocol/messageProtocol")
                .isEqualTo(sender.transportProtocol() + "/" + sender.messageProtocol());
        assertThat(sender.modules()).as("sender channel modules").isNotNull();
        List<Module> transform = sender.modules().stream()
                .filter(m -> MESSAGE_TRANSFORM_BEAN.equals(m.name()))
                .toList();
        assertThat(transform).as("sender modules named %s", MESSAGE_TRANSFORM_BEAN).hasSize(1);
        assertThat(transform.getFirst().parameters()).as("%s parameters", MESSAGE_TRANSFORM_BEAN).isNotEmpty();

        assertThat(receiver.channels()).as("receiver channels").hasSize(1);
        Channel target = channel(inventory, receiver.channels().getFirst());
        assertThat(target.direction()).as("receiver channel direction").isEqualTo(RECEIVER_DIRECTION);
        assertThat(target.adapter()).as("receiver adapter = transportProtocol/messageProtocol")
                .isEqualTo(target.transportProtocol() + "/" + target.messageProtocol());
        assertThat(target.transportProtocol()).as("receiver channel transportProtocol").startsWithIgnoringCase("IDoc");
    }

    @Test
    void esrHoldsTheOperationMappingAndBothInterfacesWithOneAsyncOperationEach() {
        IntegrationInventory inventory = inventory();
        Ico ico = onlyIco(inventory);
        InterfaceAssignment assignment = onlyInterface(onlyReceiver(ico));
        ObjRef mapping = assignment.mapping();
        assertNamed(mapping, "receiver interface mapping");
        assertThat(assignment.targets()).as("receiver interface targets").hasSize(1);
        ObjRef receiverIface = assignment.targets().getFirst().iface();
        assertNamed(receiverIface, "receiver target iface");
        assertThat(ico.sender()).as("sender").isNotNull();
        ObjRef senderIface = ico.sender().iface();
        assertNamed(senderIface, "sender iface");

        assertThat(inventory.esr()).as("esr").isNotNull();
        assertThat(inventory.esr().operationMappings()).as("esr operationMappings").hasSize(1);
        ObjRef om = inventory.esr().operationMappings().getFirst().id();
        assertThat(om).as("operation mapping id").isNotNull();
        assertThat(om.name()).as("operation mapping name").isEqualTo(mapping.name());
        assertThat(om.namespace()).as("operation mapping namespace").isEqualTo(mapping.namespace());

        List<ServiceInterface> interfaces = inventory.esr().serviceInterfaces();
        assertThat(interfaces).as("esr serviceInterfaces")
                .extracting(si -> si.id().name(), si -> si.id().namespace())
                .containsExactlyInAnyOrder(tuple(senderIface.name(), senderIface.namespace()),
                        tuple(receiverIface.name(), receiverIface.namespace()));
        assertThat(interfaces).allSatisfy(si -> {
            assertThat(si.operations()).as("operations of %s", si.id().name()).hasSize(1);
            assertThat(si.operations().getFirst().mode()).as("mode of %s", si.id().name()).isEqualTo(ASYNC);
        });
    }

    // --- synthetic data only --------------------------------------------------------------------------------------

    @Test
    void noStringInTheFixtureCarriesACustomerNameOrHostname() {
        JsonNode tree = Json.mapper().readTree(fixture());
        Map<String, String> strings = new LinkedHashMap<>();
        collect(tree, "", strings, new ArrayList<>());

        assertThat(strings).as("string values of the fixture").isNotEmpty();
        List<String> offenders = strings.entrySet().stream()
                .filter(e -> FORBIDDEN_FRAGMENTS.stream()
                        .anyMatch(f -> e.getValue().toLowerCase(Locale.ROOT).contains(f)
                                || e.getKey().toLowerCase(Locale.ROOT).contains(f)))
                .map(e -> e.getKey() + " = " + e.getValue())
                .toList();
        assertThat(offenders).as("strings containing one of %s", FORBIDDEN_FRAGMENTS).isEmpty();
    }

    @Test
    void theOnlyComponentNamesAreSndSysAndRcvSys() {
        JsonNode tree = Json.mapper().readTree(fixture());
        List<String> components = new ArrayList<>();
        collect(tree, "", new LinkedHashMap<>(), components);
        for (JsonNode c : tree.path("components")) {
            components.add(c.path("name").asString());
        }

        assertThat(components).as("component names in the fixture")
                .containsOnly(SENDER_COMPONENT, RECEIVER_COMPONENT)
                .contains(SENDER_COMPONENT, RECEIVER_COMPONENT);
    }

    /**
     * Walks {@code node}: every string value by its JSON pointer into {@code strings} (property names are part of
     * the pointer), every value of a {@code component} property into {@code components}.
     */
    private static void collect(JsonNode node, String pointer, Map<String, String> strings, List<String> components) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if ("component".equals(e.getKey()) && e.getValue().isString()) {
                    components.add(e.getValue().stringValue());
                }
                collect(e.getValue(), pointer + "/" + e.getKey(), strings, components);
            }
        } else if (node.isArray()) {
            int i = 0;
            for (JsonNode element : node) {
                collect(element, pointer + "/" + i++, strings, components);
            }
        } else if (node.isString()) {
            strings.put(pointer, node.stringValue());
        }
    }
}
