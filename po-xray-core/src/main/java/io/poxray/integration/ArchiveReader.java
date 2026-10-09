package io.poxray.integration;

import io.poxray.model.IntegrationInventory.ArchiveProgram;
import io.poxray.model.IntegrationInventory.ImportedArchive;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.ValueMappingEntry;
import io.poxray.model.IntegrationInventory.ValueMappingGroup;
import io.poxray.sca.XmlDocs;
import io.poxray.xi.XiBlob;
import io.poxray.xi.XiObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.w3c.dom.Element;

/**
 * Content of imported archives ({@code TRAFO_JAR}: {@code Archive/blob} = the uploaded jar / zip, verified on xipod)
 * and value mapping groups ({@code Valuemapping/Representation}).
 */
final class ArchiveReader {

    private static final String ABSTRACT_TRANSFORMATION = "com/sap/aii/mapping/api/AbstractTransformation";
    private static final String STREAM_TRANSFORMATION = "com/sap/aii/mapping/api/StreamTransformation";
    /** API label → prefixes of internal class names (matched in class names and descriptors of the constant pool). */
    private static final Map<String, List<String>> APIS = new LinkedHashMap<>();
    private static final int MAX_TEXT = 200_000;
    private static final int MAX_OTHER_FILES = 200;
    private static final long MAX_ARCHIVE = 256L * 1024 * 1024;

    static {
        APIS.put("LookupService", List.of("com/sap/aii/mapping/lookup/"));
        APIS.put("DynamicConfiguration", List.of("com/sap/aii/mapping/api/DynamicConfiguration"));
        APIS.put("Trace", List.of("com/sap/aii/mapping/api/AbstractTrace", "com/sap/aii/mapping/api/MappingTrace"));
        APIS.put("ValueMapping", List.of("com/sap/aii/mapping/value/api/"));
        APIS.put("MessageHeader", List.of("com/sap/aii/mapping/api/InputHeader", "com/sap/aii/mapping/api/OutputHeader"));
        APIS.put("MappingParameters", List.of("com/sap/aii/mapping/api/InputParameters",
                "com/sap/aii/mapping/api/OutputParameters"));
        APIS.put("Attachments", List.of("com/sap/aii/mapping/api/InputAttachments",
                "com/sap/aii/mapping/api/OutputAttachments"));
        APIS.put("AdapterFramework", List.of("com/sap/aii/af/"));
        APIS.put("JDBC", List.of("java/sql/", "javax/sql/"));
        APIS.put("JNDI", List.of("javax/naming/"));
        APIS.put("HTTP", List.of("java/net/HttpURLConnection", "java/net/http/", "org/apache/http/"));
        APIS.put("FileSystem", List.of("java/io/File", "java/nio/file/"));
    }

    private ArchiveReader() {
    }

    static ImportedArchive archive(XiObject o, ObjRef id) {
        Element c = o.content();
        Element blob = XmlDocs.child(c, "blob");
        String encoding = XmlDocs.childText(c, "Encoding");
        String fileName = o.link("FILE_NAME").map(l -> l.target().key(1)).orElse(null);
        byte[] jar = XiBlob.file(blob);
        if (jar == null) {
            throw new IllegalArgumentException("archive without content");
        }
        Charset sourceCharset = charset(encoding);
        Map<String, ClassFileInfo> classes = new LinkedHashMap<>();
        Map<String, String> classPaths = new HashMap<>();
        List<ArchiveProgram> texts = new ArrayList<>();
        Map<String, Integer> packages = new TreeMap<>();
        List<String> other = new ArrayList<>();
        String maven = null;
        long total = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(jar))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                if (e.isDirectory()) {
                    continue;
                }
                byte[] data = in.readAllBytes();
                total += data.length;
                if (total > MAX_ARCHIVE) {
                    throw new IllegalArgumentException("archive larger than " + MAX_ARCHIVE + " bytes");
                }
                String path = e.getName();
                String lower = path.toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".class")) {
                    ClassFileInfo info = ClassFileInfo.parse(data);
                    classes.put(info.className(), info);
                    classPaths.put(info.className(), path);
                    int slash = info.className().lastIndexOf('/');
                    packages.merge(slash < 0 ? "" : info.className().substring(0, slash).replace('/', '.'), 1,
                            Integer::sum);
                } else if (lower.endsWith(".xsl") || lower.endsWith(".xslt")) {
                    texts.add(new ArchiveProgram(path, "xslt", null, null, List.of(), null, List.of(),
                            text(data, StandardCharsets.UTF_8, sourceCharset)));
                } else if (lower.endsWith(".java")) {
                    texts.add(new ArchiveProgram(path, "javaSource", null, null, List.of(), null, List.of(),
                            text(data, sourceCharset, StandardCharsets.UTF_8)));
                } else if (lower.startsWith("meta-inf/maven/") && lower.endsWith("/pom.properties")) {
                    Properties p = new Properties();
                    p.load(new ByteArrayInputStream(data));
                    maven = p.getProperty("groupId") + ":" + p.getProperty("artifactId") + ":" + p.getProperty("version");
                } else if (other.size() < MAX_OTHER_FILES && !lower.equals("meta-inf/manifest.mf")) {
                    other.add(path);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot unpack archive: " + e.getMessage(), e);
        }
        List<ArchiveProgram> programs = new ArrayList<>();
        Set<String> allApis = new LinkedHashSet<>();
        for (ClassFileInfo info : classes.values()) {
            List<String> apis = apis(info.utf8());
            allApis.addAll(apis);
            String api = mappingApi(info, classes);
            boolean inner = info.className().contains("$");
            if (api != null || (!apis.isEmpty() && !inner)) {
                programs.add(new ArchiveProgram(classPaths.get(info.className()), api != null ? "javaMapping" : "class",
                        info.className().replace('/', '.'),
                        info.superclass() == null ? null : info.superclass().replace('/', '.'),
                        info.interfaces().stream().map(i -> i.replace('/', '.')).toList(), api, apis, null));
            }
        }
        programs.addAll(texts);
        programs.sort((a, b) -> a.path().compareToIgnoreCase(b.path()));
        return new ImportedArchive(id, fileName, maven, List.copyOf(programs), packages, List.copyOf(other),
                APIS.keySet().stream().filter(allApis::contains).toList(), o.description());
    }

    /** AbstractTransformation / StreamTransformation, directly or through superclasses / interfaces in the archive. */
    static String mappingApi(ClassFileInfo info, Map<String, ClassFileInfo> classes) {
        Set<String> seen = new LinkedHashSet<>();
        for (ClassFileInfo c = info; c != null && seen.add(c.className()); c = classes.get(c.superclass())) {
            if (ABSTRACT_TRANSFORMATION.equals(c.superclass())) {
                return "AbstractTransformation";
            }
            if (implementsStream(c, classes, new LinkedHashSet<>())) {
                return "StreamTransformation";
            }
        }
        return null;
    }

    private static boolean implementsStream(ClassFileInfo c, Map<String, ClassFileInfo> classes, Set<String> seen) {
        for (String i : c.interfaces()) {
            if (STREAM_TRANSFORMATION.equals(i)) {
                return true;
            }
            ClassFileInfo inArchive = classes.get(i);
            if (inArchive != null && seen.add(i) && implementsStream(inArchive, classes, seen)) {
                return true;
            }
        }
        return false;
    }

    static List<String> apis(List<String> utf8) {
        List<String> out = new ArrayList<>();
        APIS.forEach((label, prefixes) -> {
            if (utf8.stream().anyMatch(s -> prefixes.stream().anyMatch(s::contains))) {
                out.add(label);
            }
        });
        return List.copyOf(out);
    }

    /** Text in the first charset that decodes without errors (XSLT usually UTF-8, Java sources in the archive's). */
    private static String text(byte[] data, Charset first, Charset second) {
        byte[] cut = data.length > MAX_TEXT ? java.util.Arrays.copyOf(data, MAX_TEXT) : data;
        String suffix = data.length > MAX_TEXT ? "\n… (" + data.length + " bytes, truncated)" : "";
        for (Charset cs : List.of(first, second)) {
            try {
                return cs.newDecoder().decode(ByteBuffer.wrap(cut)).toString() + suffix;
            } catch (CharacterCodingException e) {
                // next
            }
        }
        return new String(cut, StandardCharsets.ISO_8859_1) + suffix;
    }

    private static Charset charset(String name) {
        try {
            return name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }

    // ------------------------------------------------------------------------------------------ value mappings

    static ValueMappingGroup valueMapping(XiObject o) {
        Element c = o.content();
        List<ValueMappingEntry> entries = new ArrayList<>();
        for (Element r : XmlDocs.children(c, "Representation")) {
            String system = XmlDocs.childText(r, "system");
            String name = XmlDocs.childText(r, "objectTypeName");
            String value = XmlDocs.childText(r, "value");
            entries.add(new ValueMappingEntry(system, name,
                    Secrets.isSecret(system) || Secrets.isSecret(name) ? Secrets.mask("secret", value) : value));
        }
        return new ValueMappingGroup(c == null ? o.id().key(0) : c.getAttribute("groupid"),
                c == null ? null : c.getAttribute("groupName"), List.copyOf(entries));
    }
}
