package io.poxray.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import io.poxray.model.IntegrationInventory.*;

/** Defensive copies of legacy projections, preserving their null/empty distinctions. */
public final class BehaviorSnapshots {
    private BehaviorSnapshots() { }
    static <T> List<T> required(List<T> value) { return Collections.unmodifiableList(new ArrayList<>(value)); }
    private static <T> List<T> list(List<T> value) { return value == null ? null : required(value); }
    private static <T> List<T> list(List<T> value, Function<T, T> copy) {
        if (value == null) return null;
        List<T> result = new ArrayList<>();
        for (T item : value) result.add(item == null ? null : copy.apply(item));
        return Collections.unmodifiableList(result);
    }
    private static <T> Map<String, T> map(Map<String, T> value) {
        if (value == null) return null;
        if (value.keySet().stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("Null map key in behavior configuration");
        return Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }
    static Source source(Source s) { return s == null ? null : new Source(s.system(), s.url(), list(s.files())); }
    static Ico ico(Ico i) {
        if (i == null) return null;
        Endpoint s = i.sender();
        return new Ico(i.oid(), i.description(), i.generatedFrom(), s == null ? null :
                new Endpoint(s.party(), s.component(), s.iface(), list(s.operations()), s.channel()),
                list(i.receivers(), r -> new Receiver(r.party(), r.component(), condition(r.condition()), r.qualityOfService(),
                        list(r.interfaces(), a -> new InterfaceAssignment(a.operation(), condition(a.condition()), a.mapping(),
                                list(a.targets()), map(a.parameters()))), list(r.channels()))),
                i.dynamicRouting() == null ? null : new DynamicRouting(i.dynamicRouting().operationSpecific(), list(i.dynamicRouting().mappings())),
                i.noReceiverBehaviour(), i.modifiedBy(), i.modifiedAt());
    }
    private static Condition condition(Condition c) { return c == null ? null : new Condition(c.text(), list(c.lines())); }
    static Channel channel(Channel c) {
        return c == null ? null : new Channel(c.id(), c.direction(), c.adapter(), c.transportProtocol(), c.messageProtocol(),
                c.engine(), map(c.attributes()), list(c.modules(), m -> new IntegrationInventory.Module(m.position(), m.name(),
                m.type(), m.namespace(), map(m.parameters()))), c.description());
    }
    static Interface iface(Interface i) {
        return i == null ? null : new Interface(i.id(), i.name(), i.namespace(), i.source(),
                list(i.operations(), o -> new Interface.Operation(o.id(), o.name(), o.pattern(), o.input(), o.output(), list(o.faults()))),
                list(i.usedBy()));
    }
    static ServiceReference service(ServiceReference s) {
        return s == null ? null : new ServiceReference(s.id(), s.uuid(), s.type(), s.bindingType(), s.iface(),
                s.authenticationProfile(), s.logicalSystem(), s.serviceGroup(), map(s.attributes()), s.operationMapping());
    }
    static DiscoveryReport.InterfaceRow interfaceRow(DiscoveryReport.InterfaceRow r) {
        return r == null ? null : new DiscoveryReport.InterfaceRow(r.name(), r.namespace(), r.role(),
                list(r.icos(), i -> new DiscoveryReport.IcoRow(i.oid(), i.senderParty(), i.senderComponent(),
                        i.senderInterface(), i.senderNamespace(), list(i.receiverComponents()))));
    }
    static DiscoveryReport discovery(DiscoveryReport d) {
        return d == null ? null : new DiscoveryReport(d.schemaVersion(), d.generator(), list(d.inputs()), d.icosInput(),
                list(d.processes(), p -> new DiscoveryReport.ProcessRow(p.file(), p.dc(), p.id(), p.name(), list(p.processComponents()),
                        list(p.inbound(), BehaviorSnapshots::interfaceRow), list(p.outbound(), BehaviorSnapshots::interfaceRow))),
                list(d.components(), c -> new DiscoveryReport.ComponentRow(c.name(), list(c.roles()))), list(d.issues()));
    }
}
