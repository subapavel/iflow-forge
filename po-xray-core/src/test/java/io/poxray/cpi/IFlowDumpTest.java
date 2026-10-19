package io.poxray.cpi;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.json.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** {@link IFlowDump} on an {@link IFlow} built directly from the records (no file I/O). */
class IFlowDumpTest {

    private static Map<String, String> inProps() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("ComponentType", "JMS");
        props.put("Name", "In");
        props.put("direction", "Sender");
        props.put("QueueName_inbound", "{{Q_IN}}");
        return props;
    }

    private static IFlow sample() {
        return new IFlow("my.id", "My Flow", "1.0.0",
                List.of(new Participant("In", "JMS", "Sender", "InQueue", inProps())),
                List.of(new Step("s1", "A", "Script", 0, Map.of("activityType", "Script"), true),
                        new Step("s2", "H", "Script", 1, Map.of("activityType", "Script"), true),
                        new Step("s3", "Z", "Enricher", 0, Map.of("activityType", "Enricher"), false)),
                List.of(new Param("p1", "d1", true), new Param("p2", "", false)),
                List.of("script/x.groovy"));
    }

    @Test
    void markdownLayout() {
        assertThat(IFlowDump.markdown(sample())).isEqualTo("""
                ## my.id (My Flow, 1.0.0)
                - participant: In JMS Sender -> InQueue
                1. Script "A"
                  2. Script "H"
                3. Enricher "Z" [unreached]
                - parameter: p1 = d1 [configured]
                - parameter: p2 =
                - resource: script/x.groovy
                """);
    }

    @Test
    void jsonKeysInRecordOrder() {
        JsonNode root = Json.mapper().readTree(IFlowDump.json(sample()));

        assertThat(root.propertyNames())
                .containsExactly("id", "name", "version", "participants", "steps", "parameters", "resources");
        assertThat(root.get("participants").get(0).propertyNames())
                .containsExactly("name", "componentType", "direction", "endpoint", "properties");
        assertThat(root.get("participants").get(0).get("properties").get("ComponentType").stringValue()).isEqualTo("JMS");
        assertThat(root.get("steps").get(0).propertyNames())
                .containsExactly("id", "name", "activityType", "depth", "properties", "reached");
        assertThat(root.get("steps").get(2).get("reached").booleanValue()).isFalse();
        assertThat(root.get("parameters").get(0).propertyNames()).containsExactly("name", "defaultValue", "configured");
        assertThat(root.get("parameters").get(0).get("configured").booleanValue()).isTrue();
    }
}
