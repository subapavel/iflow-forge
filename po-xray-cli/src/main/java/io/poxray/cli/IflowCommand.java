package io.poxray.cli;

import io.poxray.cpi.IFlow;
import io.poxray.cpi.IFlowDump;
import io.poxray.cpi.IFlowReader;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/** {@code po-xray iflow <path> [--json]}: channels, steps, parameters and resources of iFlows and collections. */
@CommandDefinition(name = "iflow",
        description = "Read SAP Integration Suite artifacts (iFlow folder or zip, package export, or a folder of "
                + "these) and print one dump per artifact",
        generateHelp = true)
public class IflowCommand implements Command<CommandInvocation> {

    @Argument(description = "Artifact folder or zip, package export (folder or zip with resources.cnt), or a folder of "
            + "these", paramLabel = "path", required = true)
    String path;

    @Option(name = "json", hasValue = false, description = "Print JSON (one object per artifact) instead of Markdown")
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
            List<IFlow> flows = IFlowReader.readAll(p);
            for (IFlow f : flows) {
                invocation.println(json ? IFlowDump.json(f).stripTrailing() : IFlowDump.markdown(f));
            }
            System.err.println("Read " + flows.size() + " iFlow(s) from " + path);
            return CommandResult.SUCCESS;
        });
    }
}
