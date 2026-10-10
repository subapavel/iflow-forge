package io.poxray.bpm;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.model.EsrOperationMapping;
import io.poxray.sca.ZipArchive;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** Composite of a BPM DC calling a PI operation mapping (structure as in the PPAS_OTE archive BPM). */
class CompositeTest {

    private static final String COMPOSITE = """
            <composite xmlns="http://www.osoa.org/xmlns/sca/1.0"
                xmlns:sapconfig="http://www.sap.com/webas/2007/03/esoa/config/system"
                xmlns:sapimplbpm="http://www.sap.com/xmlns/sapimplbpm"
                xmlns:sapinterfacewsdlloc="http://www.sap.com/xmlns/sapinterfacewsdlloc">
              <component name="acme~bpm~Process">
                <reference name="r-1" sapconfig:authenticationProfile="technicalUser" sapconfig:serviceGroup="sappo_om">
                  <interface.wsdl interface="urn:acme#wsdl.interface(acme_OrderToIdocPortType)"/>
                </reference>
              </component>
              <component name="acme~bpm~OperationMappingComponent">
                <sapimplbpm:implementation.piom/>
                <service name="acme_OrderToIdoc_Service">
                  <interface.wsdl interface="urn:acme#wsdl.interface(acme_OrderToIdocPortType)"
                      sapinterfacewsdlloc:location="acme.bpem#wsdls/acme/OM_OrderToIdoc.wsdl"/>
                  <sapinterfacewsdlloc:sapextensions omguid="f443996c" svguid="499c5b00">
                    <sapinterfacewsdlloc:input><sapinterfacewsdlloc:type name="Order" namespace="urn:acme"/></sapinterfacewsdlloc:input>
                    <sapinterfacewsdlloc:output><sapinterfacewsdlloc:type name="ORDERS05" namespace="urn:sap-com:document:sap:idoc"/></sapinterfacewsdlloc:output>
                  </sapinterfacewsdlloc:sapextensions>
                </service>
              </component>
            </composite>""";

    @Test
    void readsReferencesAndPiOperationMappings() throws IOException {
        Composite.Content c = Composite.read(ZipArchive.of(sda(), "test.sda"));

        assertThat(c.references().get("r-1")).isEqualTo(new Composite.Reference("sappo_om", "technicalUser"));
        EsrOperationMapping om = c.operationMappings().get("{urn:acme}acme_OrderToIdocPortType");
        assertThat(om).isNotNull();
        assertThat(om.name()).isEqualTo("OM_OrderToIdoc");
        assertThat(om.namespace()).isEqualTo("urn:acme");
        assertThat(om.oid()).isEqualTo("f443996c");
        assertThat(om.swcGuid()).isEqualTo("499c5b00");
        assertThat(om.input().name()).isEqualTo("Order");
        assertThat(om.output().name()).isEqualTo("ORDERS05");
    }

    private static byte[] sda() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("META-INF/acme.composite"));
            z.write(COMPOSITE.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return out.toByteArray();
    }
}
