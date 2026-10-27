package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.migration.BehaviorComparison;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/** Offline comparison of explicitly aligned source behavior evidence. */
@CommandDefinition(name = "compare-behavior",
        description = "Compare embedded source behavior reports offline using explicit alignments; not an approval gate",
        generateHelp = true)
public class CompareBehaviorCommand implements Command<CommandInvocation> {
    @Argument(description = "Local comparison JSON with embedded reports", paramLabel = "comparison.json", required = true)
    String path;
    @Option(name = "json", hasValue = false, description = "Print full JSON instead of Markdown")
    boolean json;
    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            var report = BehaviorComparison.read(Path.of(path));
            invocation.println(json ? Json.write(report).stripTrailing() : BehaviorComparison.markdown(report));
            return CommandResult.SUCCESS;
        });
    }
}
