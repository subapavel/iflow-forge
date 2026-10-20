package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.migration.ScenarioAttributes;
import io.poxray.model.AttributesReport;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Arguments;
import org.aesh.command.option.Option;

/** {@code po-xray attributes <ico.json>... [--json]}: the D-025 scenario attributes of every ICO of the files. */
@CommandDefinition(name = "attributes",
        description = "Derive the D-025 scenario attributes of every ICO of po-xray ico JSON files; undecidable "
                + "values are ? with a question",
        generateHelp = true)
public class AttributesCommand implements Command<CommandInvocation> {

    @Arguments(description = "Integration inventory JSON files written by po-xray ico", paramLabel = "ico.json",
            required = true)
    List<String> files;

    @Option(name = "json", hasValue = false, description = "Print JSON instead of Markdown")
    boolean json;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            List<Path> paths = new ArrayList<>();
            for (String f : files) {
                Path p = Path.of(f);
                if (!Files.isRegularFile(p)) {
                    throw new ScaService.CliException("Not found: " + f);
                }
                paths.add(p);
            }
            AttributesReport report = ScenarioAttributes.derive(paths);
            invocation.println(json ? Json.write(report).stripTrailing() : ScenarioAttributes.markdown(report));
            System.err.println("Derived attributes of " + report.scenarios().size() + " scenario(s) from "
                    + paths.size() + " file(s)");
            return CommandResult.SUCCESS;
        });
    }
}
