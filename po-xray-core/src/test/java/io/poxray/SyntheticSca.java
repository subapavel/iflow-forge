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

/**
 * Small synthetic (non-confidential) SCA for tests that must run everywhere, also on CI without {@code testdata/}:
 * EJB module DC {@code acme.com/calc/ejb} with one BPM function bean, wrapped by EAR DC {@code acme.com/calc/ear}
 * whose deployed {@code src.zip} carries the given bean source.
 */
public final class SyntheticSca {

    public static final String BEAN = """
            package com.acme;
            import javax.ejb.Stateless;
            @Stateless
            public class Calc implements CalcLocal {
                public Object invokeSdo(Object in, Object ctx) { return in; }
            }
            """;
    public static final String LOCAL = """
            package com.acme;
            import javax.ejb.Local;
            @Local
            public interface CalcLocal { }
            """;

    private SyntheticSca() {
    }

    /** Writes the SCA (deployed bean source identical to the DC source) to {@code file}. */
    public static Path write(Path file) {
        return write(file, BEAN);
    }

    /** Writes the SCA with the given deployed bean source to {@code file}. */
    public static Path write(Path file, String deployedBean) {
        try {
            Files.write(file, build(deployedBean));
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] build(String deployedBean) throws IOException {
        Map<String, byte[]> ejbDcsa = new LinkedHashMap<>();
        ejbDcsa.put(".dcdef", dcdef("calc/ejb", "J2EE", "EJBModule", null));
        ejbDcsa.put("ejbModule/com/acme/Calc.java", utf8(BEAN));
        ejbDcsa.put("ejbModule/com/acme/CalcLocal.java", utf8(LOCAL));

        Map<String, byte[]> earDcsa = Map.of(".dcdef", dcdef("calc/ear", "J2EE", "Enterprise Application", "calc/ejb"));

        Map<String, byte[]> srcArchive = new LinkedHashMap<>();
        srcArchive.put("com/acme/Calc.java", utf8(deployedBean));
        srcArchive.put("com/acme/CalcLocal.java", utf8(LOCAL));
        byte[] srcZip = zip(Map.of("acme.com~calc~ejb~ejbjar_srcarchive.zip", zip(srcArchive)));
        byte[] ear = zip(Map.of("src.zip", srcZip));

        Map<String, byte[]> sca = new LinkedHashMap<>();
        sca.put("META-INF/SAP_MANIFEST.MF", utf8("Manifest-Version: 1.0\r\nkeyname: ACME_CALC\r\nkeyvendor: acme.com\r\n\r\n"));
        sca.put("SOURCEARCHIVES/acme.com~calc~ejb~.dcsa", zip(ejbDcsa));
        sca.put("SOURCEARCHIVES/acme.com~calc~ear~.dcsa", zip(earDcsa));
        sca.put("DEPLOYARCHIVES/acme.com~calc~ear~acme.com~calc~ear.ear", ear);
        return zip(sca);
    }

    private static byte[] dcdef(String name, String type, String subType, String dependency) {
        String dep = dependency == null ? "" : """
                <dependencies><dependency><dc-ref><name>%s</name><vendor>acme.com</vendor></dc-ref></dependency></dependencies>
                """.formatted(dependency);
        return utf8("""
                <?xml version="1.0" encoding="UTF-8"?>
                <development-component xmlns="http://xml.sap.com/2002/11/DevelopmentComponent">
                  <name>%s</name><vendor>acme.com</vendor>
                  <component-type><type>%s</type><sub-type>%s</sub-type></component-type>
                  %s
                </development-component>
                """.formatted(name, type, subType, dep));
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

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
