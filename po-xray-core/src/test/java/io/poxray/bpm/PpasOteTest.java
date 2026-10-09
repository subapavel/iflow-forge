package io.poxray.bpm;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.PoXray;
import io.poxray.TestData;
import io.poxray.json.Json;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Robustness check on a second, unrelated SCA (PPAS_OTE, confidential, testdata/): no BPMN 2.0 oracle,
 * so it only verifies that the reader copes and writes the result to {@code target/testoutput/} for review.
 */
class PpasOteTest {

    @Test
    void readsWithoutErrors() {
        ScaInventory inventory = PoXray.read(TestData.require("PPASOTE00_0.sca"));
        Json.write(inventory, Path.of("target/testoutput/PPASOTE00_0.json"));

        assertThat(inventory.developmentComponents()).isNotEmpty();
        assertThat(inventory.bpm()).allSatisfy(b -> assertThat(b.processes()).isNotEmpty());
        assertThat(inventory.issues()).noneMatch(i -> i.severity().equals(Issue.ERROR));
    }
}
