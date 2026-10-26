package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.migration.DossierReport;
import io.poxray.migration.DossierValidator;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/** Read-only scoped approval and history check; no migration gate or runtime evidence claim. */
@CommandDefinition(name = "dossier-check", description = "Validate a versioned migration dossier and project scoped approvals",
        generateHelp = true)
public class DossierCheckCommand implements Command<CommandInvocation> {
    @Argument(description = "Versioned dossier JSON file", paramLabel = "dossier.json", required = true)
    String dossier;

    @Option(name = "baseline", description = "Previous dossier JSON snapshot for append-only history comparison")
    String baseline;

    @Option(name = "json", hasValue = false, description = "Print JSON instead of Markdown")
    boolean json;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            DossierReport report = DossierValidator.check(Path.of(dossier), baseline == null ? null : Path.of(baseline));
            invocation.println(json ? Json.write(report).stripTrailing() : DossierValidator.markdown(report));
            System.err.println("Dossier " + report.migrationId() + ": " + (report.valid() ? "valid" : "invalid")
                    + ", history " + report.historyStatus() + ", " + report.diagnostics().size() + " diagnostic(s)");
            return report.valid() ? CommandResult.SUCCESS : CommandResult.FAILURE;
        });
    }
}
