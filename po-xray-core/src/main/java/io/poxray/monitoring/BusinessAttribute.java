package io.poxray.monitoring;

import org.w3c.dom.Element;

/** A user-defined search attribute of a message (F-17 Appendix C: name, value; namespace WS). */
public record BusinessAttribute(String name, String value) {

    static BusinessAttribute read(Element e) {
        return new BusinessAttribute(Soap.childText(e, Soap.WS, "name"), Soap.childText(e, Soap.WS, "value"));
    }
}
