package io.poxray.cpi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A sender or receiver channel of an iFlow: one {@code bpmn2:messageFlow} and the endpoint participant it connects to.
 *
 * @param name          property {@code Name} (the channel name)
 * @param componentType property {@code ComponentType}: the adapter ({@code JMS}, {@code ProcessDirect}, {@code XI},
 *                      {@code IDOC}, {@code SOAP}, ...)
 * @param direction     property {@code direction}: {@code Sender} or {@code Receiver}
 * @param endpoint      {@code name} attribute of the {@code bpmn2:participant} the flow connects to (its
 *                      {@code sourceRef} for a sender, its {@code targetRef} for a receiver); empty when that does not
 *                      name a participant
 * @param properties    all {@code ifl:property} key/value pairs of the message flow (the adapter configuration,
 *                      including {@code Name}, {@code ComponentType}, {@code direction}) in document order
 *                      (unmodifiable)
 */
public record Participant(String name, String componentType, String direction, String endpoint,
        Map<String, String> properties) {

    public Participant {
        properties = properties == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }
}
