package io.poxray.ejb;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.IssueLog;
import io.poxray.PoXray;
import io.poxray.model.BpmComponent;
import io.poxray.model.EjbModule;
import io.poxray.model.Function;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test on a small synthetic (non-confidential) SCA built in the test: runs everywhere, also on CI
 * where {@code testdata/} is absent.
 */
class SyntheticScaTest {

    private static final String BEAN_V1 = """
            package com.acme;
            import javax.ejb.Stateless;
            @Stateless
            public class Calc implements CalcLocal {
                public Object invokeSdo(Object in, Object ctx) { return in; }
            }
            """;
    private static final String LOCAL = """
            package com.acme;
            import javax.ejb.Local;
            @Local
            public interface CalcLocal { }
            """;

    @Test
    void readsEjbModuleAndReportsDeployedSourceDifferences(@TempDir Path dir) throws IOException {
        Path sca = dir.resolve("ACME.sca");
        String deployedBean = BEAN_V1.replace("return in;", "return null; // changed after the source archive");
        Files.write(sca, sca(deployedBean));

        ScaInventory inv = PoXray.read(sca);

        assertThat(inv.developmentComponents()).extracting(dc -> dc.name() + ":" + dc.type())
                .containsExactlyInAnyOrder("calc/ejb:EJBModule", "calc/ear:Enterprise Application");
        EjbModule m = inv.ejb().getFirst();
        assertThat(m.ear()).isEqualTo("acme.com/calc/ear");
        assertThat(m.application()).isEqualTo("acme.com/calc~ear");
        assertThat(m.beans()).singleElement().satisfies(b -> {
            assertThat(b.name()).isEqualTo("Calc");
            assertThat(b.roles()).containsExactly("bpmFunction");
            assertThat(b.interfaces().local()).containsExactly("com.acme.CalcLocal");
            assertThat(b.source().sha256()).hasSize(64);
        });
        assertThat(inv.issues()).filteredOn(i -> i.code().equals("DEPLOYED_SOURCE_DIFFERS")).singleElement()
                .satisfies(i -> assertThat(i.message()).contains("com/acme/Calc.java").doesNotContain("CalcLocal"));
    }

    @Test
    void identicalDeployedSourcesAreNotReported(@TempDir Path dir) throws IOException {
        Path sca = dir.resolve("ACME.sca");
        Files.write(sca, sca(BEAN_V1.replace("\n", "\r\n"))); // line endings do not matter
        assertThat(PoXray.read(sca).issues()).noneMatch(i -> i.code().equals("DEPLOYED_SOURCE_DIFFERS"));
    }

    @Test
    void reportsBpmFunctionsWithoutBean() {
        Function f = new Function("F1", "calc", "acme", "ejb", "acme.com/other~ear/LOCAL/Gone/com.acme.GoneLocal",
                new Function.EjbLink("acme.com/other~ear", "Gone", "com.acme.GoneLocal"), List.of(), null, "x.function");
        BpmComponent bpm = new BpmComponent("acme.com/bpm",
                List.of(), new BpmComponent.Catalog(List.of(), List.of(), List.of(f), List.of(), List.of(), List.of(), List.of()));
        IssueLog issues = new IssueLog();

        new BpmFunctionLinks(List.of(bpm)).reportMissing(issues);

        assertThat(issues.issues()).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("MISSING_SOURCE");
            assertThat(i.severity()).isEqualTo(Issue.WARNING);
            assertThat(i.location().element().name()).isEqualTo("calc");
        });
    }

    // ------------------------------------------------------------------------------------------ fixture

    private static byte[] sca(String deployedBean) throws IOException {
        Map<String, byte[]> ejbDcsa = new LinkedHashMap<>();
        ejbDcsa.put(".dcdef", dcdef("calc/ejb", "J2EE", "EJBModule", null));
        ejbDcsa.put("ejbModule/com/acme/Calc.java", utf8(BEAN_V1));
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
