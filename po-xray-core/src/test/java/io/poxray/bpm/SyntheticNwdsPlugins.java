package io.poxray.bpm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Small synthetic (non-confidential) NWDS plugins folder for the BPM built-in function generator: the two plugin jars
 * it reads, each carrying its DC archive with MOIN partitions shaped like the ones the generator parses (namespace
 * {@code http://xml.sap.com/moin}, attribute {@code m:i}, links as {@code <assoc><l f= s=/></assoc>}).
 *
 * <p>Content: functions {@code plus} (operators, infix, parameters ordered by ParticlesForModelGroup, one typed by an XSD
 * type, one by an unknown id), {@code blank} (text, no parameters, no result, documentation with characters JSON must
 * escape) and {@code minus} (operators, default DC, links written in the other direction, result typed by a SAP type);
 * a function outside the {@code src/functions/} prefix of the default DC (ignored); triggers {@code Deadline} (timer),
 * {@code MessageTrigger} (message, critical, with parameter) and {@code MessageTriggerFault} (error, technical) — the
 * last two in one {@code .eventdef}; XSD types {@code int}, {@code string} (plus a duplicate id and a non-type element),
 * SAP type {@link #SAP_PRINCIPAL_NAME} (plus the XSD string id under another name and a nameless type); an older,
 * empty servicecomp jar that must not be chosen.
 */
public final class SyntheticNwdsPlugins {

    public static final String SERVICECOMP_JAR = "com.sap.tc.ap.servicecomp_1.0.0.jar";
    public static final String OLD_SERVICECOMP_JAR = "com.sap.tc.ap.servicecomp_0.9.0.jar";
    public static final String BPEMCONTENT_JAR = "com.sap.tc.ap.bpemcontent_1.0.0.jar";
    public static final String TYPES_DC_ZIP = "sap.com~tc~bpem~content~predefs~types~def_cmp.zip";
    public static final String DEFAULT_DC_ZIP = "sap.com~tc~bpem~content~predefs~default~def_cmp.zip";

    public static final String XSD_STRING = id('B', 1);
    public static final String XSD_INT = id('B', 2);
    public static final String NOT_A_TYPE = id('B', 9);
    public static final String SAP_PRINCIPAL = id('C', 1);
    public static final String SAP_NAMELESS = id('C', 2);
    public static final String UNKNOWN_TYPE = id('D', 1);
    public static final String F_PLUS = id('F', 1);
    public static final String F_BLANK = id('F', 2);
    public static final String F_MINUS = id('F', 3);
    public static final String F_GHOST = id('F', 9);
    public static final String T_MESSAGE = id('E', 1);
    public static final String T_FAULT = id('E', 2);
    public static final String T_DEADLINE = id('E', 3);

    /** Raw SAP type name with control characters (read by regex from the .types text, never XML-parsed). */
    public static final String SAP_PRINCIPAL_NAME = "Principal\b\f" + (char) 0x01 + (char) 0x1b;

    public static final String PLUS_FILE = "rep/functions/sap.com/bpem/glx/built-in/operators/plus.function";
    public static final String BLANK_FILE = "rep/functions/sap.com/bpem/glx/built-in/text/blank.function";
    public static final String MINUS_FILE = "src/functions/com/example/minus.function";
    public static final String GHOST_FILE = "rep/functions/sap.com/ghost/ghost.function";
    public static final String MESSAGE_TRIGGER_FILE = "src/triggers/MessageTrigger.eventdef";
    public static final String DEADLINE_FILE = "src/triggers/Deadline.eventdef";

    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<m:Partition xmlns:m=\"http://xml.sap.com/moin\">\n";
    private static final String FOOTER = "</m:Partition>\n";

    private SyntheticNwdsPlugins() {
    }

    /** 40 upper-case hex characters: {@code letter} + 38 zeros + {@code digit}. */
    private static String id(char letter, int digit) {
        return letter + "0".repeat(38) + digit;
    }

    /** Writes the three plugin jars into {@code dir} (created if absent) and returns {@code dir}. */
    public static Path write(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.write(dir.resolve(OLD_SERVICECOMP_JAR), jar(Map.of()));
        Files.write(dir.resolve(SERVICECOMP_JAR), jar(Map.of("dcs/" + TYPES_DC_ZIP, zip(typesDc()))));
        Files.write(dir.resolve(BPEMCONTENT_JAR), jar(Map.of(DEFAULT_DC_ZIP, zip(defaultDc()))));
        return dir;
    }

    private static Map<String, byte[]> typesDc() {
        Map<String, byte[]> dc = new LinkedHashMap<>();
        dc.put("_comp/.dcdef", utf8("<development-component/>\n"));
        // written in reverse of the sorted order
        dc.put(BLANK_FILE, utf8(blankFunction()));
        dc.put(PLUS_FILE, utf8(plusFunction()));
        dc.put("rep/wsdl/xml_schema.types", utf8(xmlSchemaTypes()));
        dc.put("rep/wsdl/sap.com.glx.types", utf8(sapTypes()));
        return dc;
    }

    private static Map<String, byte[]> defaultDc() {
        Map<String, byte[]> dc = new LinkedHashMap<>();
        dc.put(MESSAGE_TRIGGER_FILE, utf8(messageTrigger()));
        dc.put(DEADLINE_FILE, utf8(deadlineTrigger()));
        dc.put(MINUS_FILE, utf8(minusFunction()));
        dc.put(GHOST_FILE, utf8(HEADER
                + " <gfi.FunctionInterface m:i=\"" + F_GHOST + "\" namespace=\"sap.com/ghost\" symbol=\"ghost\">\n"
                + "  <name originalText=\"ghost\"/>\n"
                + " </gfi.FunctionInterface>\n"
                + FOOTER));
        return dc;
    }

    private static String plusFunction() {
        String f = "local:" + F_PLUS;
        return HEADER
                + " <gfi.FunctionInterface m:i=\"" + F_PLUS + "\" namespace=\"sap.com/bpem/glx/built-in/operators\""
                + " symbol=\"+\" infix=\"true\">\n"
                + "  <name originalText=\"plus\"/>\n"
                + "  <documentation originalText=\"Adds two numbers.\"/>\n"
                + " </gfi.FunctionInterface>\n"
                + " <gfi.JavaFunction m:i=\"plusJava\" className=\"com.example.bpm.PlusFunction\"/>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"plusIn\" name=\"plusRequest\"/>\n"
                + " <XSD1.XsdComplexTypeDefinition m:i=\"plusInType\"/>\n"
                + " <XSD1.XsdModelGroup m:i=\"plusInGroup\"/>\n"
                + " <XSD1.XsdParticle m:i=\"plusInP1\"/>\n"
                + " <XSD1.XsdParticle m:i=\"plusInP2\"/>\n"
                // document order addend before summand; particle order summand (0), addend (1)
                + " <XSD1.XsdElementDeclaration m:i=\"plusAddend\" name=\"addend\"/>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"plusSummand\" name=\"summand\"/>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"plusOut\" name=\"plusResponse\"/>\n"
                + " <XSD1.XsdComplexTypeDefinition m:i=\"plusOutType\"/>\n"
                + " <XSD1.XsdModelGroup m:i=\"plusOutGroup\"/>\n"
                + " <XSD1.XsdParticle m:i=\"plusOutP1\"/>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"plusResult\" name=\"result\"/>\n"
                + " <gfi.FunctionInterfaceHasFormalParameter><l f=\"local:plusIn\" s=\"" + f + "\"/>"
                + "</gfi.FunctionInterfaceHasFormalParameter>\n"
                + " <gfi.FunctionInterfaceHasResult><l f=\"local:plusOut\" s=\"" + f + "\"/>"
                + "</gfi.FunctionInterfaceHasResult>\n"
                + " <gfi.FunctionInterfaceHasFunctions><l f=\"local:plusJava\" s=\"" + f + "\"/>"
                + "</gfi.FunctionInterfaceHasFunctions>\n"
                + " <XSD1.TypeDefinitionForElementDeclaration>\n"
                + "  <l f=\"local:plusIn\" s=\"local:plusInType\"/>\n"
                + "  <l f=\"local:plusOut\" s=\"local:plusOutType\"/>\n"
                + "  <l f=\"local:plusAddend\" s=\"" + UNKNOWN_TYPE + "\"/>\n"
                + "  <l f=\"local:plusSummand\" s=\"" + XSD_INT + "\"/>\n"
                + "  <l f=\"local:plusResult\" s=\"" + XSD_INT + "\"/>\n"
                + " </XSD1.TypeDefinitionForElementDeclaration>\n"
                + " <XSD1.ContentTypeForComplexTypeDefinition>\n"
                + "  <l f=\"local:plusInType\" s=\"local:plusInGroup\"/>\n"
                + "  <l f=\"local:plusOutType\" s=\"local:plusOutGroup\"/>\n"
                + " </XSD1.ContentTypeForComplexTypeDefinition>\n"
                + " <XSD1.ParticlesForModelGroup>\n"
                + "  <l f=\"local:plusInGroup\" s=\"local:plusInP1\"/>\n"
                + "  <l f=\"local:plusInGroup\" s=\"local:plusInP2\"/>\n"
                + "  <l f=\"local:plusOutGroup\" s=\"local:plusOutP1\"/>\n"
                + " </XSD1.ParticlesForModelGroup>\n"
                + " <XSD1.ContentForParticle>\n"
                + "  <l f=\"local:plusAddend\" s=\"local:plusInP2\"/>\n"
                + "  <l f=\"local:plusInP1\" s=\"local:plusSummand\"/>\n"
                + "  <l f=\"local:plusOutP1\" s=\"local:plusResult\"/>\n"
                + " </XSD1.ContentForParticle>\n"
                + FOOTER;
    }

    private static String blankFunction() {
        // documentation parses to: Say "hi" \ C:\temp<LF>next line<TAB>tab<CR>cr ü/ok
        return HEADER
                + " <gfi.FunctionInterface m:i=\"" + F_BLANK + "\" namespace=\"sap.com/bpem/glx/built-in/text\""
                + " symbol=\"blank\">\n"
                + "  <name originalText=\"blank\"/>\n"
                + "  <documentation originalText=\"Say &quot;hi&quot; \\ C:\\temp&#10;next line&#9;tab&#13;cr"
                + " \u00fc/ok\"/>\n"
                + " </gfi.FunctionInterface>\n"
                + FOOTER;
    }

    private static String minusFunction() {
        String f = "local:" + F_MINUS;
        return HEADER
                + " <gfi.FunctionInterface m:i=\"" + F_MINUS + "\" namespace=\"sap.com/bpem/glx/built-in/operators\""
                + " symbol=\"-\" infix=\"false\">\n"
                + "  <name originalText=\"minus\"/>\n"
                + " </gfi.FunctionInterface>\n"
                + " <gfi.JavaFunction m:i=\"minusJava\" className=\"com.example.bpm.MinusFunction\"/>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"minusIn\" name=\"minusRequest\"/>\n"
                + " <XSD1.XsdComplexTypeDefinition m:i=\"minusInType\"/>\n"
                + " <XSD1.XsdModelGroup m:i=\"minusInGroup\"/>\n"
                + " <XSD1.XsdParticle m:i=\"minusInP1\"/>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"minusValue\" name=\"value\"/>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"minusOut\" name=\"minusResponse\"/>\n"
                + " <XSD1.XsdComplexTypeDefinition m:i=\"minusOutType\"/>\n"
                + " <XSD1.XsdModelGroup m:i=\"minusOutGroup\"/>\n"
                + " <XSD1.XsdParticle m:i=\"minusOutP1\"/>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"minusResult\" name=\"result\"/>\n"
                // function on the f side of its links (the plus partition has it on the s side)
                + " <gfi.FunctionInterfaceHasFormalParameter><l f=\"" + f + "\" s=\"local:minusIn\"/>"
                + "</gfi.FunctionInterfaceHasFormalParameter>\n"
                + " <gfi.FunctionInterfaceHasResult><l f=\"" + f + "\" s=\"local:minusOut\"/>"
                + "</gfi.FunctionInterfaceHasResult>\n"
                + " <gfi.FunctionInterfaceHasFunctions><l f=\"" + f + "\" s=\"local:minusJava\"/>"
                + "</gfi.FunctionInterfaceHasFunctions>\n"
                + " <XSD1.TypeDefinitionForElementDeclaration>\n"
                + "  <l f=\"local:minusIn\" s=\"local:minusInType\"/>\n"
                + "  <l f=\"local:minusOut\" s=\"local:minusOutType\"/>\n"
                + "  <l f=\"local:minusValue\" s=\"" + XSD_STRING + "\"/>\n"
                + "  <l f=\"local:minusResult\" s=\"" + SAP_PRINCIPAL + "\"/>\n"
                + " </XSD1.TypeDefinitionForElementDeclaration>\n"
                + " <XSD1.ContentTypeForComplexTypeDefinition>\n"
                + "  <l f=\"local:minusInType\" s=\"local:minusInGroup\"/>\n"
                + "  <l f=\"local:minusOutType\" s=\"local:minusOutGroup\"/>\n"
                + " </XSD1.ContentTypeForComplexTypeDefinition>\n"
                + " <XSD1.ParticlesForModelGroup>\n"
                + "  <l f=\"local:minusInGroup\" s=\"local:minusInP1\"/>\n"
                + "  <l f=\"local:minusOutGroup\" s=\"local:minusOutP1\"/>\n"
                + " </XSD1.ParticlesForModelGroup>\n"
                + " <XSD1.ContentForParticle>\n"
                + "  <l f=\"local:minusInP1\" s=\"local:minusValue\"/>\n"
                + "  <l f=\"local:minusOutP1\" s=\"local:minusResult\"/>\n"
                + " </XSD1.ContentForParticle>\n"
                + FOOTER;
    }

    private static String messageTrigger() {
        return HEADER
                + " <ev.MessageEventDefinition m:i=\"" + T_MESSAGE + "\" isCritical=\"true\" isTechnical=\"false\">\n"
                + "  <name originalText=\"MessageTrigger\"/>\n"
                + "  <documentation originalText=\"Default message trigger\"/>\n"
                + " </ev.MessageEventDefinition>\n"
                + " <ev.ErrorEventDefinition m:i=\"" + T_FAULT + "\" isTechnical=\"true\">\n"
                + "  <name originalText=\"MessageTriggerFault\"/>\n"
                + "  <documentation originalText=\"Fault of the message trigger\"/>\n"
                + " </ev.ErrorEventDefinition>\n"
                + " <XSD1.XsdElementDeclaration m:i=\"msgParam\" name=\"MessageTrigger\""
                + " namespace=\"urn:example:bpm:trigger\"/>\n"
                + " <ev.EventDefinitionHasParameter><l f=\"local:msgParam\" s=\"local:" + T_MESSAGE + "\"/>"
                + "</ev.EventDefinitionHasParameter>\n"
                + " <XSD1.TypeDefinitionForElementDeclaration><l f=\"local:msgParam\" s=\"" + XSD_STRING + "\"/>"
                + "</XSD1.TypeDefinitionForElementDeclaration>\n"
                + FOOTER;
    }

    private static String deadlineTrigger() {
        return HEADER
                + " <ev.TimerEventDefinition m:i=\"" + T_DEADLINE + "\">\n"
                + "  <name originalText=\"Deadline\"/>\n"
                + " </ev.TimerEventDefinition>\n"
                + FOOTER;
    }

    /** XSD built-in types: int before string (sorted by id: string, int), a duplicate id, a non-type element. */
    private static String xmlSchemaTypes() {
        return "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<m:Partition xmlns:m='http://xml.sap.com/moin'>\n"
                + " <XSD1.XsdSimpleTypeDefinition m:i='" + XSD_INT + "' name='int'/>\n"
                + " <XSD1.XsdSimpleTypeDefinition m:i='" + XSD_STRING + "' name='string'/>\n"
                + " <XSD1.XsdSimpleTypeDefinition m:i='" + XSD_INT + "' name='integer'/>\n"
                + " <XSD1.XsdElementDeclaration m:i='" + NOT_A_TYPE + "' name='notAType'/>\n"
                + "</m:Partition>\n";
    }

    /** SAP types: the XSD string id again (dropped), the principal type (name before m:i), a nameless type. */
    private static String sapTypes() {
        return "<?xml version='1.0' encoding='UTF-8'?>\n"
                + "<m:Partition xmlns:m='http://xml.sap.com/moin'>\n"
                + " <XSD1.XsdSimpleTypeDefinition m:i='" + XSD_STRING + "' name='SapString'/>\n"
                + " <XSD1.XsdComplexTypeDefinition name='" + SAP_PRINCIPAL_NAME + "' m:i='" + SAP_PRINCIPAL + "'>\n"
                + " </XSD1.XsdComplexTypeDefinition>\n"
                + " <XSD1.XsdComplexTypeDefinition m:i='" + SAP_NAMELESS + "'/>\n"
                + "</m:Partition>\n";
    }

    private static byte[] jar(Map<String, byte[]> entries) throws IOException {
        Map<String, byte[]> jar = new LinkedHashMap<>();
        jar.put("META-INF/MANIFEST.MF", utf8("Manifest-Version: 1.0\r\nBundle-SymbolicName: synthetic\r\n\r\n"));
        jar.putAll(entries);
        return zip(jar);
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
