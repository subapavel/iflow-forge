package io.poxray.ejb;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.IssueLog;
import io.poxray.PoXray;
import io.poxray.SyntheticSca;
import io.poxray.doc.DocFormat;
import io.poxray.doc.Documentation;
import io.poxray.model.BpmComponent;
import io.poxray.model.EjbModule;
import io.poxray.model.Function;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test on a small synthetic (non-confidential) SCA ({@link SyntheticSca}): runs everywhere, also on CI
 * where {@code testdata/} is absent.
 */
class SyntheticScaTest {

    @Test
    void readsEjbModuleAndReportsDeployedSourceDifferences(@TempDir Path dir) {
        String deployedBean = SyntheticSca.BEAN.replace("return in;", "return null; // changed after the source archive");
        ScaInventory inv = PoXray.read(SyntheticSca.write(dir.resolve("ACME.sca"), deployedBean));

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
    void generatesDocumentation(@TempDir Path dir) {
        ScaInventory inv = PoXray.read(SyntheticSca.write(dir.resolve("ACME.sca")));

        for (DocFormat format : DocFormat.values()) {
            String doc = Documentation.render(inv, format);
            assertThat(doc).contains("CALC", "acme.com/calc/ejb", "Calc");
        }
    }

    @Test
    void identicalDeployedSourcesAreNotReported(@TempDir Path dir) {
        Path sca = SyntheticSca.write(dir.resolve("ACME.sca"), SyntheticSca.BEAN.replace("\n", "\r\n")); // EOL ignored
        assertThat(PoXray.read(sca).issues()).noneMatch(i -> i.code().equals("DEPLOYED_SOURCE_DIFFERS"));
    }

    @Test
    void reportsBpmFunctionsWithoutBean() {
        Function f = new Function("F1", "calc", "acme", "ejb", "acme.com/other~ear/LOCAL/Gone/com.acme.GoneLocal",
                new Function.EjbLink("acme.com/other~ear", "Gone", "com.acme.GoneLocal"), List.of(), null, "x.function", List.of());
        BpmComponent bpm = new BpmComponent("acme.com/bpm", List.of(),
                new BpmComponent.Catalog(List.of(), List.of(), List.of(f), List.of(), List.of(), List.of(), List.of()));
        IssueLog issues = new IssueLog();

        new BpmFunctionLinks(List.of(bpm)).reportMissing(issues);

        assertThat(issues.issues()).singleElement().satisfies(i -> {
            assertThat(i.code()).isEqualTo("MISSING_SOURCE");
            assertThat(i.severity()).isEqualTo(Issue.WARNING);
            assertThat(i.location().element().name()).isEqualTo("calc");
        });
    }
}
