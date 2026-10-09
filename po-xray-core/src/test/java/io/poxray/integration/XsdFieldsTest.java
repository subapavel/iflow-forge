package io.poxray.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.model.DataType;
import org.junit.jupiter.api.Test;

class XsdFieldsTest {

    private static final String XSD = """
            <xsd:schema xmlns:xsd="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:acme" xmlns="urn:acme">
              <xsd:complexType name="Other"><xsd:sequence><xsd:element name="X" type="xsd:string"/></xsd:sequence></xsd:complexType>
              <xsd:complexType name="Order">
                <xsd:annotation><xsd:documentation>Objednávka</xsd:documentation></xsd:annotation>
                <xsd:sequence>
                  <xsd:element name="Id" type="xsd:string"/>
                  <xsd:element name="Note" type="xsd:string" minOccurs="0"/>
                  <xsd:element name="Item" maxOccurs="unbounded">
                    <xsd:complexType><xsd:sequence>
                      <xsd:element name="Qty" type="xsd:decimal"/>
                      <xsd:element name="Code"><xsd:simpleType><xsd:restriction base="xsd:string"/></xsd:simpleType></xsd:element>
                    </xsd:sequence><xsd:attribute name="lineNo" type="xsd:int" use="required"/></xsd:complexType>
                  </xsd:element>
                  <xsd:element name="Attachment" type="Attachment" minOccurs="0" maxOccurs="unbounded"/>
                </xsd:sequence>
              </xsd:complexType>
            </xsd:schema>
            """;

    @Test
    void flattensNamedComplexTypeIncludingInlineTypes() {
        assertThat(XsdFields.fields(XSD, "Order")).containsExactly(
                new DataType.Field("Id", "xsd:string", null, null),
                new DataType.Field("Note", "xsd:string", 0, null),
                new DataType.Field("Item", null, null, "unbounded"),
                new DataType.Field("Item/Qty", "xsd:decimal", null, null),
                new DataType.Field("Item/Code", "xsd:string", null, null),
                new DataType.Field("Item/@lineNo", "xsd:int", 1, null),
                new DataType.Field("Attachment", "Attachment", 0, "unbounded"));
    }

    @Test
    void toleratesMissingOrInvalidXsd() {
        assertThat(XsdFields.fields(null, "X")).isEmpty();
        assertThat(XsdFields.fields("<not xml", "X")).isEmpty();
    }
}
