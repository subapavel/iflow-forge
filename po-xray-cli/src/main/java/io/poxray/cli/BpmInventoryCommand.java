package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.migration.BpmInventory;
import io.poxray.model.BpmInventoryReport;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/** {@code po-xray bpm-inventory <export-dir> [--json]}: processes, steps, human tasks and EJB roles of an export. */
@CommandDefinition(name = "bpm-inventory",
        description = "Size the NW BPM processes and EJB modules of a po-xray export directory (or one inventory "
                + "JSON file)",
        generateHelp = true)
public class BpmInventoryCommand implements Command<CommandInvocation> {

    @Argument(description = "Directory written by po-xray export -o, or one SCA inventory JSON file",
            paramLabel = "export-dir", required = true)
    String path;

    @Option(name = "json", hasValue = false, description = "Print JSON instead of Markdown")
    boolean json;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            Path p = Path.of(path);
            if (!Files.exists(p)) {
                throw new ScaService.CliException("Not found: " + path);
            }
            BpmInventoryReport report = BpmInventory.read(p);
            if (report.inputs().isEmpty()) {
                throw new ScaService.CliException("No SCA inventory with bpm or ejb in " + path);
            }
            invocation.println(json ? Json.write(report).stripTrailing() : BpmInventory.markdown(report));
            System.err.println("Read " + report.inputs().size() + " inventory file(s), "
                    + report.totals().processes() + " process(es), " + report.issues().size()
                    + " issue(s) from " + path);
            return CommandResult.SUCCESS;
        });
    }
}
