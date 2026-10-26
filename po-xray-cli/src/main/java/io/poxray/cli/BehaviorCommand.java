package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.migration.BehaviorAnalysis;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/** Offline source behavior and configured integration candidates. */
@CommandDefinition(name = "behavior", description = "Analyze BPM source behavior and optional configured integration from local inventory JSON", generateHelp = true)
public class BehaviorCommand implements Command<CommandInvocation> {
    @Argument(description = "Export directory or one SCA inventory JSON file", paramLabel = "export-dir-or-file", required = true)
    String path;
    @Option(name = "icos", description = "Optional local ICO inventory JSON file")
    String icos;
    @Option(name = "json", hasValue = false, description = "Print full JSON instead of Markdown")
    boolean json;
    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            var report = BehaviorAnalysis.read(Path.of(path), icos == null || icos.isBlank() ? null : Path.of(icos));
            invocation.println(json ? Json.write(report).stripTrailing() : BehaviorAnalysis.markdown(report));
            return CommandResult.SUCCESS;
        });
    }
}
