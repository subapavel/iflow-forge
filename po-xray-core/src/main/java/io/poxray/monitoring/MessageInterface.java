package io.poxray.monitoring;

import org.w3c.dom.Element;

/**
 * A {@code MessageInterface} of the monitoring service (F-17 Appendix C, namespace DATA): the interface name and
 * namespace, optionally with the sender and receiver party and component.
 */
public record MessageInterface(String name, String namespace, String senderParty, String senderComponent,
        String receiverParty, String receiverComponent) {

    /** Reads a {@code MessageInterface} element; a child that is absent or nil is null. */
    static MessageInterface read(Element e) {
        return new MessageInterface(Soap.childText(e, Soap.DATA, "name"), Soap.childText(e, Soap.DATA, "namespace"),
                Soap.childText(e, Soap.DATA, "senderParty"), Soap.childText(e, Soap.DATA, "senderComponent"),
                Soap.childText(e, Soap.DATA, "receiverParty"), Soap.childText(e, Soap.DATA, "receiverComponent"));
    }

    /** Appends the children of a {@code MessageInterface} in the WSDL order, unset ones omitted. */
    void writeTo(StringBuilder out) {
        Soap.element(out, "d", "name", name);
        Soap.element(out, "d", "namespace", namespace);
        Soap.element(out, "d", "senderParty", senderParty);
        Soap.element(out, "d", "senderComponent", senderComponent);
        Soap.element(out, "d", "receiverParty", receiverParty);
        Soap.element(out, "d", "receiverComponent", receiverComponent);
    }
}
