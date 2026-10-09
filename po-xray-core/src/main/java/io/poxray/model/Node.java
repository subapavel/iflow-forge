package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * A flow node. Common fields first, kind-specific fields are null when not applicable (omitted in JSON).
 * Kinds: startEvent, endEvent, boundaryEvent, automatedActivity, mappingActivity, notificationActivity,
 * exclusiveGateway, joinGateway, embeddedSubprocess, unknown.
 */
public record Node(
        String id,
        String kind,
        String metaclass,
        String name,
        String documentation,
        Ref scope,
        Ref lane,
        String subject,
        String body,
        OperationRef operation,
        Ref serviceReference,
        Trigger trigger,
        Ref attachedTo,
        Expression condition,
        Boolean terminate,
        @JsonProperty("throws") Ref throwsEvent,
        Mapping inputMapping,
        Mapping outputMapping,
        List<ErrorEvent> errorEvents,
        List<Ref> boundaryEvents,
        String scopeId,
        Map<String, String> attributes) {

    public record OperationRef(String ref, String name, @JsonProperty("interface") InterfaceRef iface) {
    }

    public record InterfaceRef(String ref, String name, String namespace) {
    }

    /** {@code kind}: message | error; {@code builtin} for SAP triggers not contained in the DC. */
    public record Trigger(String kind, Ref eventDefinition, String builtin) {
    }

    public record ErrorEvent(String ref, String name, Ref fault) {
    }

    /** Mutable builder; the record has too many optional fields for a readable constructor call. */
    public static final class Builder {
        String id;
        String kind;
        String metaclass;
        String name;
        String documentation;
        Ref scope;
        Ref lane;
        String subject;
        String body;
        OperationRef operation;
        Ref serviceReference;
        Trigger trigger;
        Ref attachedTo;
        Expression condition;
        Boolean terminate;
        Ref throwsEvent;
        Mapping inputMapping;
        Mapping outputMapping;
        List<ErrorEvent> errorEvents;
        List<Ref> boundaryEvents;
        String scopeId;
        Map<String, String> attributes;

        public Builder(String id, String kind) {
            this.id = id;
            this.kind = kind;
        }

        public Builder metaclass(String v) { metaclass = v; return this; }
        public Builder name(String v) { name = v; return this; }
        public Builder documentation(String v) { documentation = v; return this; }
        public Builder scope(Ref v) { scope = v; return this; }
        public Builder lane(Ref v) { lane = v; return this; }
        public Builder subject(String v) { subject = v; return this; }
        public Builder body(String v) { body = v; return this; }
        public Builder operation(OperationRef v) { operation = v; return this; }
        public Builder serviceReference(Ref v) { serviceReference = v; return this; }
        public Builder trigger(Trigger v) { trigger = v; return this; }
        public Builder attachedTo(Ref v) { attachedTo = v; return this; }
        public Builder condition(Expression v) { condition = v; return this; }
        public Builder terminate(Boolean v) { terminate = v; return this; }
        public Builder throwsEvent(Ref v) { throwsEvent = v; return this; }
        public Builder inputMapping(Mapping v) { inputMapping = v; return this; }
        public Builder outputMapping(Mapping v) { outputMapping = v; return this; }
        public Builder errorEvents(List<ErrorEvent> v) { errorEvents = v; return this; }
        public Builder boundaryEvents(List<Ref> v) { boundaryEvents = v; return this; }
        public Builder scopeId(String v) { scopeId = v; return this; }
        public Builder attributes(Map<String, String> v) { attributes = v; return this; }

        public Node build() {
            return new Node(id, kind, metaclass, name, documentation, scope, lane, subject, body, operation,
                    serviceReference, trigger, attachedTo, condition, terminate, throwsEvent, inputMapping, outputMapping,
                    errorEvents, boundaryEvents, scopeId, attributes);
        }
    }
}
