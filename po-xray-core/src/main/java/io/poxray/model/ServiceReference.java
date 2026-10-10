package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Service reference ({@code mc/META-INF/servicereferences.servicerefs}) enriched from the deployed composite;
 * {@code operationMapping}: the ESR operation mapping it calls (PI operation mapping used by the process), else null.
 */
public record ServiceReference(
        String id,
        String uuid,
        String type,
        String bindingType,
        @JsonProperty("interface") QNameRef iface,
        String authenticationProfile,
        String logicalSystem,
        String serviceGroup,
        Map<String, String> attributes,
        EsrOperationMapping operationMapping) {
}
