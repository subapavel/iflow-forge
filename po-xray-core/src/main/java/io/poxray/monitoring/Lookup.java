package io.poxray.monitoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.w3c.dom.Element;

/**
 * The answer of a lookup operation (an array of strings, interfaces, parties, ...) from the system named
 * {@code system}: the items in the order of the answer (never null), the SOAP answer as received, and
 * {@code otherFields} (never null) with what the reader cannot map, in document order: an item that is nil
 * ({@code "(nil)"}), of another element name or complex where a string is expected, as {@code Response/<name>}
 * ({@code #2}, {@code #3}, ... for a repeated key); a child of the n-th item that is unknown, repeated, in another
 * namespace or complex, as {@code Response/<item>/<child>} ({@code Response/<item>#n/<child>} from the second item
 * on); and {@code Response} as {@code "(nil)"} when the whole answer is nil.
 */
public record Lookup<T>(String system, List<T> values, String rawResponse, Map<String, String> otherFields) {

    public Lookup {
        values = values == null ? List.of() : List.copyOf(values);
        otherFields = otherFields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(otherFields));
    }

    /**
     * How the items of one array type are read: the item element ({@code namespace}, {@code name}); for a structured
     * item the namespace of its children and their names in the WSDL order, and the constructor taking their texts
     * in that order (null for absent or nil); for a string item ({@code fields} empty) the constructor takes its text.
     */
    record Item<V>(String namespace, String name, String fieldNamespace, List<String> fields,
            Function<List<String>, V> make) {
    }

    static final Item<String> STRING = new Item<>(Soap.JL, "String", null, List.of(), v -> v.getFirst());
    static final Item<MessageInterface> INTERFACE = new Item<>(Soap.DATA, "MessageInterface", Soap.DATA,
            List.of("name", "namespace", "senderParty", "senderComponent", "receiverParty", "receiverComponent"),
            v -> new MessageInterface(v.get(0), v.get(1), v.get(2), v.get(3), v.get(4), v.get(5)));
    static final Item<Party> PARTY = new Item<>(Soap.DATA, "MessageParty", Soap.DATA,
            List.of("agency", "name", "schema"), v -> new Party(v.get(0), v.get(1), v.get(2)));
    static final Item<AttributeMetadata> ATTRIBUTE_METADATA = new Item<>(Soap.WS, "AttributeMetadata", Soap.WS,
            List.of("name", "description"), v -> new AttributeMetadata(v.get(0), v.get(1)));
    static final Item<BusinessAttribute> BUSINESS_ATTRIBUTE = new Item<>(Soap.WS, "BusinessAttribute", Soap.WS,
            List.of("name", "value"), v -> new BusinessAttribute(v.get(0), v.get(1)));
    static final Item<IntegrationFlow> INTEGRATION_FLOW = new Item<>(Soap.WS, "IntegrationFlow", Soap.WS,
            List.of("name", "description", "id"), v -> new IntegrationFlow(v.get(2), v.get(0), v.get(1)));
    static final Item<StatusDetail> STATUS_DETAIL = new Item<>(Soap.WS, "StatusDetail", Soap.WS,
            List.of("errorLabelID", "description", "text"), v -> new StatusDetail(v.get(0), v.get(1), v.get(2)));

    /** Reads the {@code OP:Response} element of a lookup answer whose items are {@code item}. */
    static <T> Lookup<T> read(String system, MonitoringClient.Reply reply, Item<T> item) {
        List<T> values = new ArrayList<>();
        Map<String, String> other = new LinkedHashMap<>();
        Element response = reply.response();
        if (Soap.nil(response)) {
            other.put("Response", Soap.NIL);
        }
        int count = 0;
        for (Element e : Soap.children(response)) {
            if (!Soap.is(e, item.namespace(), item.name()) || Soap.nil(e)) {
                Soap.keepAt(other, "Response/" + e.getLocalName(), e);
            } else if (item.fields().isEmpty()) {
                if (Soap.complex(e)) {
                    Soap.keepAt(other, "Response/" + e.getLocalName(), e);
                } else {
                    values.add(item.make().apply(List.of(e.getTextContent())));
                }
            } else {
                count++;
                String path = "Response/" + e.getLocalName() + (count == 1 ? "" : "#" + count) + "/";
                values.add(structured(e, item, path, other));
            }
        }
        return new Lookup<>(system, values, reply.raw(), other);
    }

    /** One structured item: the first simple child of each field name; every other child kept under {@code path}. */
    private static <T> T structured(Element e, Item<T> item, String path, Map<String, String> other) {
        String[] texts = new String[item.fields().size()];
        Map<String, Integer> seen = new HashMap<>();
        for (Element c : Soap.children(e)) {
            int index = item.fieldNamespace().equals(c.getNamespaceURI()) ? item.fields().indexOf(c.getLocalName())
                    : -1;
            int occurrence = seen.merge(c.getNamespaceURI() + " " + c.getLocalName(), 1, Integer::sum);
            if (index < 0 || occurrence > 1 || !Soap.nil(c) && Soap.complex(c)) {
                Soap.keepAt(other, path + c.getLocalName(), c);
            } else if (!Soap.nil(c)) {
                texts[index] = c.getTextContent();
            }
        }
        return item.make().apply(Arrays.asList(texts));
    }
}
