package io.poxray.migration;

import io.poxray.model.BpmDataReport;
import io.poxray.model.ContractCatalogInput;
import io.poxray.model.ContractCatalogReport;
import io.poxray.model.IntegrationInventory;

/** Deterministic, offline projection of retained contracts and attributed design claims. */
public final class ContractCatalog {
    private ContractCatalog() { }

    public static ContractCatalogReport of(IntegrationInventory inventory, BpmDataReport data,
            ContractCatalogInput supplied) {
        var catalog = new ContractCatalogBuilder(data);
        catalog.source(inventory);
        new ContractCatalogClaims(catalog).add(supplied);
        catalog.imports();
        return new ContractCatalogReport(1, "contract-catalog-v1", catalog.artifacts, catalog.contracts,
                catalog.dependencies, data, catalog.issues);
    }

    public static String markdown(ContractCatalogReport report) {
        return ContractCatalogMarkdown.render(report);
    }
}
