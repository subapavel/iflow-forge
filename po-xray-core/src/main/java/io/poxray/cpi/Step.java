package io.poxray.cpi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A flow step of an iFlow: a {@code bpmn2:callActivity}, {@code serviceTask}, {@code exclusiveGateway},
 * {@code parallelGateway} or {@code subProcess}.
 *
 * @param id           the element id
 * @param name         the element's {@code name} attribute
 * @param activityType the {@code ifl:property} {@code activityType} ({@code Script}, {@code Enricher},
 *                     {@code ProcessCallElement}, ...); empty when the element has none
 * @param depth        0 in the main process; + 1 inside a subprocess and inside a local process inlined under its
 *                     process call
 * @param properties   all {@code ifl:property} key/value pairs of the element in document order (unmodifiable)
 * @param reached      false when no sequence flow from a start event leads to the step
 */
public record Step(String id, String name, String activityType, int depth, Map<String, String> properties,
        boolean reached) {

    public Step {
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
