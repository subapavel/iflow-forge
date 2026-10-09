package io.poxray.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.model.IntegrationInventory.FunctionLibrary;
import io.poxray.model.IntegrationInventory.MappingLogic;
import io.poxray.model.IntegrationInventory.MappingParameter;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.sca.XmlDocs;
import io.poxray.xi.XiBlob;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/** Synthetic mapping metadata in the format verified on xipod (PO 7.50). */
class MappingLogicReaderTest {

    private static final String LIBRARY = """
            <functionstorage version="XI7.1"><key><key typeID="FUNC_LIB" oid="lib1"><elem>Utils</elem><elem>urn:u</elem></key></key>
            <classname>Utils</classname><package>urn_u</package>
            <imports><![CDATA[import com.sap.aii.mapping.api.*;
            import java.util.*;
            import javax.xml.stream.XMLInputFactory;
            ]]></imports>
            <globals><javaText><![CDATA[	private String call(Channel c) { return LookupService.getSystemAccessor(c).toString(); }]]></javaText></globals>
            <init><functionmodel><signature cacheType="2"/><name>init</name><key>%init%</key><implementation type="udf"><javaText><![CDATA[]]></javaText></implementation></functionmodel></init>
            <cleanup><javaText><![CDATA[]]></javaText></cleanup>
            <functionmodel><signature cacheType="2">
              <argument tp="0" nm="value" jtp="String"><uiTitle>Input</uiTitle></argument>
              <argument tp="2" nm="channel" jtp="Channel"/>
              <argument tp="1" nm="valid" jtp="ResultList"/>
              <argument tp="1" nm="text" jtp="ResultList"/></signature>
              <name>check</name><key>guid-check</key><title>Check</title><uiTitle>Checks the value</uiTitle>
              <implementation type="udf"><javaText><![CDATA[		valid.addValue("X");
            		DynamicConfiguration conf = null;]]></javaText></implementation></functionmodel>
            <usedjars><ref role="USED_JARS" pos="1"><key><key typeID="TRAFO_JAR" oid="jar1"><elem>Archive</elem><elem>urn:u</elem></key></key></ref></usedjars>
            </functionstorage>
            """;

    private static final String MAPPING = """
            <mappingtool version="XI7.1"><project version="XI7.1"><libstorage>
              <entry name="usernamespace"><functionstorage version="XI7.1"><key><key typeID="FUNC_LIB"><elem>LocalUserFunctions</elem><elem>http://com.sap.xi</elem></key></key>
                <imports><![CDATA[import java.util.*;]]></imports><globals><javaText><![CDATA[]]></javaText></globals>
                <cleanup><javaText><![CDATA[]]></javaText></cleanup>
                <functionmodel><signature cacheType="0"><argument tp="0" nm="xml" jtp="String"/></signature>
                  <name>setNode</name><key>calculate1</key><title>SetNode</title>
                  <implementation type="udf"><javaText><![CDATA[	StructureNode node = (StructureNode)(container.getParameter("STRUCTURE_NODE"));
            	return "";]]></javaText></implementation></functionmodel><usedjars/></functionstorage></entry>
              <entry name="Utils"><libref><ref role="UsedFuncLib" pos="1"><key><key typeID="FUNC_LIB" oid="lib1"><elem>Utils</elem><elem>urn:u</elem></key></key></ref></libref></entry>
              <entry name="Missing"><libref><ref role="UsedFuncLib" pos="2"><key><key typeID="FUNC_LIB"><elem>Missing</elem><elem>urn:m</elem></key></key></ref></libref></entry>
            </libstorage><transformation>
              <brick type="Dst" gid="0" path="/ns0:T/A"><viewData x="1" y="1"/><arg>
                <brick type="Src" gid="1" path="/ns1:S/A"/></arg><group/></brick>
              <brick type="Dst" gid="0" path="/ns0:T/B"><arg>
                <brick type="Func" gid="2" fname="iF" fns="dflt">
                  <arg><brick type="Src" gid="3" path="/ns1:S/B" object_uid="r0"/></arg>
                  <arg pin="1"><brick type="Func" gid="4" fname="stringEquals" fns="dflt">
                    <arg><brick type="Func" gid="5" fname="guid-check" fns="Utils" object_uid="r1">
                      <arg><brick object_uid="r0"/></arg>
                      <bindings><param name="channel"><value ext_bind="rfc"/></param></bindings></brick></arg>
                    <arg pin="1"><brick type="Func" gid="6" fname="const" fns="dflt"><bindings><param name="value"><value>X</value></param></bindings></brick></arg>
                  </brick></arg>
                  <arg pin="2"><brick type="Func" gid="7" fname="const" fns="dflt"><bindings><param name="value"><value/></param></bindings></brick></arg>
                </brick></arg><group/></brick>
              <brick type="Dst" gid="0" path="/ns0:T/C"><arg>
                <brick type="Func" gid="8" fname="calculate1" fns="usernamespace"><arg from="1"><brick object_uid="r1"/></arg></brick></arg><group/></brick>
              <brick type="Dst" gid="0" path="/ns0:T/D"><arg>
                <brick type="Func" gid="9" fname="FixValues" fns="dflt"><arg><brick type="Src" gid="10" path="/ns1:S/D" context="x"/></arg>
                  <bindings><param name="table"><value><properties><property name="1">one</property><property name="2">two</property></properties></value></param>
                  <param name="vmdefault"><value>?</value></param></bindings></brick></arg><group/></brick>
              <brick type="Dst" gid="0" path="/ns0:T/E"><arg>
                <brick type="Func" gid="11" fname="fn" fns="Missing"/></arg><group/></brick>
              <brick type="Dst" gid="0" path="/ns0:T/F"><group/><properties><property name="switchedOff">true</property></properties></brick>
              <namespaces><properties><property name="urn:t">ns0</property><property name="urn:s">ns1</property></properties></namespaces>
            </transformation></project></mappingtool>
            """;

    private static Element xml(String s) {
        return XmlDocs.parse(s.getBytes(StandardCharsets.UTF_8), "test").getDocumentElement();
    }

    @Test
    void functionLibraryWithCodeArchivesAndApis() {
        FunctionLibrary lib = MappingLogicReader.functionStorage(xml(LIBRARY),
                new ObjRef("FUNC_LIB", "Utils", "urn:u", null, null, "lib1"), "d");

        assertThat(lib.imports()).containsExactly("import javax.xml.stream.XMLInputFactory;");
        assertThat(lib.globals()).startsWith("private String call(Channel c)");
        assertThat(lib.init()).isNull();
        assertThat(lib.cleanup()).isNull();
        assertThat(lib.sapApis()).containsExactly("LookupService");
        assertThat(lib.archives()).extracting(ObjRef::name).containsExactly("Archive");
        var fn = lib.functions().getFirst();
        assertThat(fn.name()).isEqualTo("check");
        assertThat(fn.key()).isEqualTo("guid-check");
        assertThat(fn.execution()).isEqualTo("queueValues");
        assertThat(fn.arguments()).extracting(a -> a.name() + ":" + a.kind())
                .containsExactly("value:input", "channel:parameter", "valid:result", "text:result");
        assertThat(fn.code()).isEqualTo("valid.addValue(\"X\");\nDynamicConfiguration conf = null;");
        assertThat(fn.sapApis()).containsExactly("DynamicConfiguration");
    }

    @Test
    void fieldTermsWithFunctionsReferencesAndParameters() {
        FunctionLibrary utils = MappingLogicReader.functionStorage(xml(LIBRARY),
                new ObjRef("FUNC_LIB", "Utils", "urn:u", null, null, "lib1"), null);
        List<String> warnings = new ArrayList<>();

        MappingLogic logic = MappingLogicReader.mapping(xml(MAPPING),
                List.of(new MappingParameter("rfc", "channel:RFC", "import", true)),
                r -> "Utils".equals(r.name()) ? utils : null, warnings::add);

        assertThat(logic.namespaces()).containsEntry("ns0", "urn:t").containsEntry("ns1", "urn:s");
        assertThat(logic.disabledFields()).containsExactly("/ns0:T/F");
        assertThat(logic.libraries()).extracting(ObjRef::name).containsExactly("Utils", "Missing");
        assertThat(logic.localFunctions().functions()).extracting(f -> f.name()).containsExactly("setNode");
        assertThat(logic.localFunctions().functions().getFirst().sapApis()).containsExactly("StructureNode");
        assertThat(logic.fields()).extracting(f -> f.target() + " = " + f.expression()).containsExactly(
                "/ns0:T/A = /ns1:S/A",
                "/ns0:T/B = iF(if: stringEquals(Utils.check(/ns1:S/B, channel=$rfc).valid, \"X\"), then: /ns1:S/B, else: \"\")",
                "/ns0:T/C = setNode(Utils.check(/ns1:S/B, channel=$rfc).text)",
                "/ns0:T/D = FixValues(/ns1:S/D{context=x}, table={\"1\": \"one\", \"2\": \"two\"}, vmdefault=\"?\")",
                "/ns0:T/E = Missing.fn()");
        assertThat(logic.fields().get(1).sources()).containsExactly("/ns1:S/B");
        assertThat(warnings).containsExactly("brick attribute 'context' not interpreted",
                "function fn of library Missing | urn:m not resolved");
    }

    @Test
    void nestedZipBlobIsUnpacked() throws IOException {
        byte[] inner = zip("metadata", MAPPING.getBytes(StandardCharsets.UTF_8));
        String text = "!zip!" + Base64.getEncoder().encodeToString(zip("value", inner));
        Element blob = xml("<blob type=\"zip\" zipped=\"true\">" + text + "</blob>");

        assertThat(new String(XiBlob.payload(blob), StandardCharsets.UTF_8)).isEqualTo(MAPPING);
    }

    private static byte[] zip(String name, byte[] content) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry(name));
            z.write(content);
            z.closeEntry();
        }
        return out.toByteArray();
    }
}
