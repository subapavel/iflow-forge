package io.poxray.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.model.IntegrationInventory.ArchiveProgram;
import io.poxray.model.IntegrationInventory.ImportedArchive;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.ValueMappingGroup;
import io.poxray.sca.XmlDocs;
import io.poxray.xi.XiObject;
import io.poxray.xi.XiObjectParser;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** Imported archives (synthetic jar with generated class files) and value mapping groups, format as on xipod. */
class ArchiveReaderTest {

    @Test
    void recognisesMappingsApisAndTexts() throws IOException {
        Map<String, byte[]> jar = new LinkedHashMap<>();
        jar.put("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
        jar.put("cz/acme/Base.class", classFile("cz/acme/Base", "com/sap/aii/mapping/api/AbstractTransformation",
                List.of(), List.of("com/sap/aii/mapping/api/AbstractTrace")));
        jar.put("cz/acme/OrderMapping.class", classFile("cz/acme/OrderMapping", "cz/acme/Base", List.of(),
                List.of("(Lcom/sap/aii/mapping/api/DynamicConfiguration;)V", "com/sap/aii/mapping/lookup/LookupService")));
        jar.put("cz/acme/Legacy.class", classFile("cz/acme/Legacy", "java/lang/Object",
                List.of("com/sap/aii/mapping/api/StreamTransformation"), List.of()));
        jar.put("cz/acme/util/Helper.class", classFile("cz/acme/util/Helper", "java/lang/Object", List.of(),
                List.of()));
        jar.put("cz/acme/OrderMapping$1.class", classFile("cz/acme/OrderMapping$1", "java/lang/Object", List.of(),
                List.of("java/sql/Connection")));
        jar.put("xsl/Strip.xsl", "<xsl:stylesheet version=\"1.0\"/>".getBytes(StandardCharsets.UTF_8));
        jar.put("META-INF/maven/cz.acme/orders/pom.properties",
                "groupId=cz.acme\nartifactId=orders\nversion=1.2\n".getBytes(StandardCharsets.UTF_8));
        jar.put("config.properties", "a=b".getBytes(StandardCharsets.UTF_8));

        ImportedArchive a = ArchiveReader.archive(archiveObject(zip(jar)), new ObjRef("TRAFO_JAR", "JAVA_Orders",
                "urn:acme", null, null, null));

        assertThat(a.fileName()).isEqualTo("orders.jar");
        assertThat(a.maven()).isEqualTo("cz.acme:orders:1.2");
        assertThat(a.packages()).containsEntry("cz.acme", 4).containsEntry("cz.acme.util", 1);
        assertThat(a.otherFiles()).containsExactly("config.properties");
        assertThat(a.sapApis()).containsExactly("LookupService", "DynamicConfiguration", "Trace", "JDBC");
        assertThat(a.programs()).extracting(p -> p.path() + ":" + p.kind() + ":" + p.mappingApi()).containsExactly(
                "cz/acme/Base.class:javaMapping:AbstractTransformation",
                "cz/acme/Legacy.class:javaMapping:StreamTransformation",
                "cz/acme/OrderMapping.class:javaMapping:AbstractTransformation", // inherited from Base
                "xsl/Strip.xsl:xslt:null");
        ArchiveProgram order = a.programs().get(2);
        assertThat(order.className()).isEqualTo("cz.acme.OrderMapping");
        assertThat(order.sapApis()).containsExactly("LookupService", "DynamicConfiguration");
        assertThat(a.programs().get(3).text()).isEqualTo("<xsl:stylesheet version=\"1.0\"/>");
    }

    @Test
    void valueMappingGroupWithMaskedSecrets() {
        XiObject o = XiObjectParser.parse("""
                <p1:xiObj xmlns:p1="urn:sap-com:xi"><p1:idInfo VID="v"><p1:vc vcType="D"/>
                <p1:key typeID="ValueMapping" oid="o"><p1:elem>g1</p1:elem></p1:key></p1:idInfo>
                <p1:content><Valuemapping groupName="Params" groupid="g1">
                  <Representation><system>Api</system><objectTypeName>GrantType</objectTypeName><value>code</value></Representation>
                  <Representation><system>Api</system><objectTypeName>ClientSecret</objectTypeName><value>s3cret</value></Representation>
                </Valuemapping></p1:content></p1:xiObj>""", "vm");

        ValueMappingGroup g = ArchiveReader.valueMapping(o);

        assertThat(g.groupName()).isEqualTo("Params");
        assertThat(g.entries()).extracting(e -> e.objectTypeName() + "=" + e.value())
                .containsExactly("GrantType=code", "ClientSecret=<hidden>");
    }

    @Test
    void genericContentMasksSecrets() {
        Element e = XmlDocs.parse("""
                <Cfg password="x"><Attribute><Name>clientSecret</Name><Value>s3cret</Value></Attribute>
                <ApiKey>k</ApiKey><Url>http://h</Url></Cfg>""".getBytes(StandardCharsets.UTF_8), "c").getDocumentElement();

        Secrets.mask(e);

        String xml = ObjectCatalog.serialize(e);
        assertThat(xml).doesNotContain("s3cret", ">k<", "\"x\"").contains("http://h", "<Name>clientSecret</Name>");
    }

    // ------------------------------------------------------------------------------------------ fixtures

    private static XiObject archiveObject(byte[] jar) {
        return XiObjectParser.parse("""
                <p1:xiObj xmlns:p1="urn:sap-com:xi"><p1:idInfo VID="v"><p1:vc vcType="S" swcGuid="aaaa"/>
                <p1:key typeID="TRAFO_JAR" oid="o"><p1:elem>JAVA_Orders</p1:elem><p1:elem>urn:acme</p1:elem></p1:key></p1:idInfo>
                <p1:generic><p1:lnks><p1:lnkRole role="FILE_NAME" kpos="0"><p1:lnk rMode="R"><p1:vc vcType="S"/>
                <p1:key typeID="MAP_FILE_NAME"><p1:elem>jar</p1:elem><p1:elem>orders.jar</p1:elem></p1:key></p1:lnk></p1:lnkRole>
                </p1:lnks></p1:generic>
                <p1:content><ar:Archive xmlns:ar="urn:sap-com:xi:mapping:archive"><ar:JdkVersion>18</ar:JdkVersion>
                <ar:blob type="jar" zipped="false">!jar!%s</ar:blob><ar:Encoding>Cp1250</ar:Encoding></ar:Archive></p1:content>
                </p1:xiObj>""".formatted(Base64.getEncoder().encodeToString(jar)), "archive");
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }

    /** A minimal valid class file: constant pool with the given UTF-8 constants, this, super and interfaces. */
    static byte[] classFile(String name, String superName, List<String> interfaces, List<String> constants)
            throws IOException {
        List<String> utf8 = new ArrayList<>();
        utf8.add(name);
        utf8.add(superName);
        utf8.addAll(interfaces);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(52);
        int classCount = utf8.size();
        out.writeShort(2 + 2 * classCount + constants.size()); // count = number of entries + 1
        for (int i = 0; i < classCount; i++) {
            out.writeByte(1);
            out.writeUTF(utf8.get(i)); // index 1 + 2i
            out.writeByte(7);
            out.writeShort(1 + 2 * i); // index 2 + 2i
        }
        out.writeByte(3);
        out.writeInt(42); // an integer constant between, to exercise skipping
        for (String c : constants) {
            out.writeByte(1);
            out.writeUTF(c);
        }
        out.writeShort(0x21);
        out.writeShort(2); // this
        out.writeShort(4); // super
        out.writeShort(interfaces.size());
        for (int i = 0; i < interfaces.size(); i++) {
            out.writeShort(2 + 2 * (2 + i));
        }
        return bytes.toByteArray();
    }
}
