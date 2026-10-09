package io.poxray.xi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class XiObjectParserTest {

    /** Synthetic message type with an absolute and a relative link, texts and an inner object. */
    static final String XIOBJ = """
            <?xml version="1.0" encoding="UTF-8"?>
            <p1:xiObj xmlns:p1="urn:sap-com:xi">
              <p1:idInfo VID="vid1">
                <p1:vc vcType="S" swcGuid="AAAA-BBBB" sp="-1" caption="ACME, 1.0 of acme.com"><p1:clCxt consider="A"/></p1:vc>
                <p1:key typeID="ifmmessage" oid="oid1"><p1:elem>OrderCreate</p1:elem><p1:elem>urn:acme</p1:elem></p1:key>
              </p1:idInfo>
              <p1:generic>
                <p1:admInf><p1:modifBy>alice</p1:modifBy><p1:modifAt>2021-08-24 15:33:09.811</p1:modifAt></p1:admInf>
                <p1:lnks>
                  <p1:lnkRole role="TYPE" kpos="0">
                    <p1:lnk rMode="R"><p1:key typeID="ifmtypedef" oid="oid2"><p1:elem>Order</p1:elem><p1:elem>urn:acme</p1:elem></p1:key></p1:lnk>
                  </p1:lnkRole>
                  <p1:lnkRole role="BASE" kpos="1">
                    <p1:lnk rMode="A">
                      <p1:vc vcType="S" swcGuid="cccc" caption="BASIS"/>
                      <p1:key typeID="ifmtypedef"><p1:elem>Base</p1:elem><p1:elem>urn:base</p1:elem></p1:key>
                    </p1:lnk>
                  </p1:lnkRole>
                </p1:lnks>
                <p1:textInfo loadedL="EN">
                  <p1:textObj id="vid1" type="0" masterL="EN">
                    <p1:texts lang="EN"><p1:text label="">Objednávka</p1:text><p1:text label="f1">Pole</p1:text></p1:texts>
                    <p1:texts lang="DE"><p1:text label="">Bestellung</p1:text></p1:texts>
                  </p1:textObj>
                </p1:textInfo>
              </p1:generic>
              <p1:content><messageType><xmlPayloadNs>urn:acme</xmlPayloadNs></messageType></p1:content>
              <p1:inner>
                <p1:xiObj><p1:idInfo><p1:key typeID="ifmoper"><p1:elem>Op</p1:elem></p1:key></p1:idInfo></p1:xiObj>
              </p1:inner>
            </p1:xiObj>
            """;

    @Test
    void parsesIdentityLinksTextsContentAndInner() {
        XiObject o = XiObjectParser.parse(XIOBJ, "test");

        assertThat(o.id()).isEqualTo(new XiRef("S", "aaaabbbb", "ACME, 1.0 of acme.com", "ifmmessage", "oid1",
                java.util.List.of("OrderCreate", "urn:acme")));
        assertThat(o.id().keyString()).isEqualTo("OrderCreate|urn:acme");
        assertThat(o.vid()).isEqualTo("vid1");
        assertThat(o.modifiedBy()).isEqualTo("alice");
        assertThat(o.description()).isEqualTo("Objednávka"); // master language only
        assertThat(o.texts()).containsExactly(java.util.Map.entry("f1", "Pole"));
        assertThat(o.content().getLocalName()).isEqualTo("messageType");

        XiLink type = o.link("TYPE").orElseThrow();
        assertThat(type.mode()).isEqualTo("R");
        assertThat(type.target().swcGuid()).isEqualTo("aaaabbbb"); // relative: SWCV inherited
        assertThat(type.target().oid()).isEqualTo("oid2");
        XiLink base = o.link("BASE").orElseThrow();
        assertThat(base.kpos()).isEqualTo(1);
        assertThat(base.target().swcGuid()).isEqualTo("cccc");

        assertThat(o.inner()).singleElement().satisfies(i -> {
            assertThat(i.typeId()).isEqualTo("ifmoper");
            assertThat(i.id().swcGuid()).isEqualTo("aaaabbbb");
        });
    }

    @Test
    void rejectsOtherXml() {
        assertThatThrownBy(() -> XiObjectParser.parse("<x/>", "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
