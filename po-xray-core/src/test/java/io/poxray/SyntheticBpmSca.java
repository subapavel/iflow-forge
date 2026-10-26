package io.poxray;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Synthetic Process Composer archives. All identifiers and values are invented test data. */
public final class SyntheticBpmSca {
    public static final String ARCHIVE = "SOURCEARCHIVES/example.test~evidence~bpm~.dcsa";
    public static final String PROCESS = "src/bpmn/z-process.bpmn";
    public static final String METADATA = "src/model/mappings.model";
    public static final String CATALOG = "src/schema/catalog.xsd";

    private SyntheticBpmSca() {
    }

    public static Path write(Path file, Map<String, String> partitions) {
        Map<String, byte[]> dc = new LinkedHashMap<>();
        dc.put(".dcdef", utf8("""
                <development-component xmlns="http://xml.sap.com/2002/11/DevelopmentComponent">
                  <name>evidence/bpm</name><vendor>example.test</vendor>
                  <component-type><type>Process Composer</type></component-type>
                </development-component>
                """));
        partitions.forEach((path, xml) -> dc.put(path, utf8(xml)));
        try {
            Map<String, byte[]> sca = new LinkedHashMap<>();
            sca.put("META-INF/SAP_MANIFEST.MF", utf8(
                    "Manifest-Version: 1.0\r\nkeyname: SYNTH_BPM\r\nkeyvendor: example.test\r\n\r\n"));
            sca.put(ARCHIVE, zip(dc));
            Files.write(file, zip(sca));
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String partition(String body) {
        return "<?xml version='1.0' encoding='UTF-8'?>\n<Partition xmlns:m='http://xml.sap.com/moin'>\n"
                + body + "\n</Partition>";
    }

    public static String element(String type, String id, String attributes, String children) {
        return "<" + type + " m:i='" + id + "' " + attributes + ">" + children + "</" + type + ">\n";
    }

    /** Null endpoints deliberately produce absent f/s attributes. */
    public static String link(String association, String first, String second) {
        return "<" + association + "><L"
                + (first == null ? "" : " f='" + first + "'")
                + (second == null ? "" : " s='" + second + "'")
                + "/></" + association + ">\n";
    }

    public static String process(String prefix, String name, String elements, String links, String... objects) {
        StringBuilder body = new StringBuilder();
        body.append(element("Galaxy.Workflow.Collaboration", prefix + "collab", "",
                "<name originalText='" + name + "'/>"));
        body.append(element("Galaxy.Workflow.Pool", prefix + "pool", "active='true'", ""));
        body.append(elements);
        body.append(link("Galaxy.Workflow.CollaborationContainsPools", prefix + "pool", prefix + "collab"));
        for (String object : objects) {
            body.append(link("Galaxy.Workflow.ScopeContainsScopeObjects", object, prefix + "pool"));
        }
        return partition(body + links);
    }

    /** One interpretable process with an initial-value mapping and a disabled structural assignment. */
    public static Map<String, String> mappedProcess() {
        Map<String, String> partitions = new LinkedHashMap<>();
        String elements = element("Galaxy.Workflow.MappingActivity", "owner", "", "<name originalText='Map payload'/>")
                + element("Galaxy.Workflow.DataObject", "data", "", "<name originalText='payload'/>")
                + element("Galaxy.Workflow.MultiInstanceLoopCharacteristics", "loop", "sequential='false' flowCondition='all'", "");
        String links = link("Galaxy.Workflow.InputMappingOfMappableObject", "mapping", "owner")
                + link("Galaxy.Workflow.ViewContainsMapping", "initial", "data")
                + link("Galaxy.Workflow.SourceProbe", "owner", "missing-target");
        partitions.put(PROCESS, process("z-", "Zeta process", elements, links, "owner", "data"));
        partitions.put(METADATA, partition(
                element("Galaxy.Mapping.Mapping", "mapping", "", "")
                + element("Galaxy.Mapping.MappingPart", "root", "enabled='false' category='set'", "")
                + element("Galaxy.Mapping.MappingPart", "child", "enabled='true' category='set'", "")
                + element("Galaxy.Rule.Expression", "expr", "stringExpression='&quot;mapped|value&quot;'", "")
                + element("Galaxy.Mapping.Mapping", "initial", "", "")
                + element("Galaxy.Mapping.MappingPart", "initial-root", "category='set'", "")
                + element("Galaxy.Rule.Expression", "initial-expr", "stringExpression='&quot;seed|value&quot;'", "")
                + link("Galaxy.Mapping.MappingContainsMappingParts", "root", "mapping")
                + link("Galaxy.Mapping.MappingPartHasChildren", "child", "root")
                + link("Galaxy.Mapping.MappingPartHasQName", "root-qname", "root")
                + link("Galaxy.Mapping.MappingPartHasQName", "child-qname", "child")
                + link("Galaxy.Mapping.MappingPartContainsExpression", "expr", "child")
                + link("Galaxy.Mapping.MappingPartHasChildren", "missing-part", "root")
                + link("Galaxy.Mapping.MappingContainsMappingParts", "initial-root", "initial")
                + link("Galaxy.Mapping.MappingPartHasQName", "initial-qname", "initial-root")
                + link("Galaxy.Mapping.MappingPartContainsExpression", "initial-expr", "initial-root")));
        partitions.put(CATALOG, partition(
                element("XSD1.key.QName", "root-qname", "name='target'", "")
                + element("XSD1.key.QName", "child-qname", "name='leaf'", "")
                + element("XSD1.key.QName", "initial-qname", "name='seed'", "")));
        return partitions;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
