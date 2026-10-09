package io.poxray.bpm;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.PoXray;
import io.poxray.TestData;
import io.poxray.json.Json;
import io.poxray.model.EjbModule;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Second, unrelated SCA (PPAS_OTE, confidential, testdata/): BPM robustness (no BPMN 2.0 oracle) and the richer
 * EJB patterns (scheduler job, JCo, XI proxies, JPA). Output in {@code target/testoutput/} for review.
 */
class PpasOteTest {

    private static ScaInventory inventory;

    @BeforeAll
    static void read() {
        inventory = PoXray.read(TestData.require("PPASOTE00_0.sca"));
        Json.write(inventory, Path.of("target/testoutput/PPASOTE00_0.json"));
    }

    @Test
    void readsWithoutErrors() {
        assertThat(inventory.developmentComponents()).isNotEmpty();
        assertThat(inventory.bpm()).allSatisfy(b -> assertThat(b.processes()).isNotEmpty());
        assertThat(inventory.issues()).noneMatch(i -> i.severity().equals(Issue.ERROR));
        // getPrincipals (role assignments) resolves via the catalog; its editor display form is accepted
        assertThat(inventory.issues()).extracting(Issue::code)
                .doesNotContain("UNKNOWN_BUILTIN_FUNCTION", "EXPRESSION_TEXT_MISMATCH");
        assertThat(inventory.bpm().getFirst().catalog().builtinFunctions())
                .filteredOn(f -> f.name().equals("getPrincipals")).singleElement()
                .satisfies(f -> assertThat(f.result()).isEqualTo("sap:Principal"));
        assertThat(inventory.unmapped()).isEmpty();
        assertThat(inventory.bpm()).flatExtracting(b -> b.processes()).flatExtracting(p -> p.nodes())
                .noneMatch(n -> n.kind().equals("unknown"));
    }

    @Test
    void classifiesEjbPatterns() {
        EjbModule m = inventory.ejb().getFirst();
        assertThat(m.ear()).isEqualTo("ppas.cz/dc/ear/dc_ide_ote_routing");

        assertThat(bean(m, "ZideOteMsgRefDeleteOldJobBean")).satisfies(b -> {
            assertThat(b.type()).isEqualTo("messageDriven");
            assertThat(b.roles()).containsExactly("schedulerJob");
            assertThat(b.job().name()).isEqualTo("ZideOteMsgRefDeleteOldJob");
            assertThat(b.job().description()).isNotBlank();
            assertThat(b.properties()).containsEntry("destination-name", "JobQueue");
        });
        assertThat(bean(m, "ZIsuIntUiDetermineRfcBean")).satisfies(b -> {
            assertThat(b.technologies()).contains("jco");
            assertThat(b.rfcFunctions()).containsExactly("Z_ISU_INT_UI_DETERMINE_RFC");
            assertThat(b.jcoDestinations()).containsExactly("SAPISU"); // resolved from a constant
        });
        assertThat(m.beans()).filteredOn(b -> b.roles().contains("xiProxyProvider")).hasSize(3)
                .allSatisfy(b -> assertThat(b.webService().endpointInterface()).isNotBlank());
        assertThat(bean(m, "OteRoutingEvaluatorBean").injects()).contains("ZideOteRoutingBean", "ZIsuIntUiDetermineRfcBean");

        assertThat(m.jpaEntities()).extracting(EjbModule.JpaEntity::table)
                .containsExactlyInAnyOrder("ZIDE_EXT_UI_FUZE", "ZIDE_OTE_MSG_REF", "ZIDE_OTE_ROUTING");
        assertThat(m.persistenceUnits()).singleElement()
                .satisfies(u -> assertThat(u.dataSource()).isEqualTo("PODB"));
        assertThat(inventory.developmentComponents()).anySatisfy(dc ->
                assertThat(dc.libraries()).contains("libraries/mvel2-2.5.2.Final.jar"));
        assertThat(inventory.issues()).noneMatch(i -> i.code().equals("DEPLOYED_SOURCE_DIFFERS"));
    }

    private static EjbModule.Bean bean(EjbModule m, String name) {
        return m.beans().stream().filter(b -> b.name().equals(name)).findFirst().orElseThrow();
    }
}
