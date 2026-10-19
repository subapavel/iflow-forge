package io.poxray.cli;

import io.poxray.cpi.IFlowReader;
import io.poxray.cpi.PackageUnpacker;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/**
 * {@code po-xray unpack <package> <out-dir> [--strip a,b]}: a package export into one folder per artifact (named by
 * its symbolic name) plus {@code CHANGELOG.txt}; configured values and custom tags are left out.
 */
@CommandDefinition(name = "unpack",
        description = "Unpack an SAP Integration Suite package export into <out-dir>/<symbolic-name>/ (without "
                + "parameters.prop and Configured_Custom_Tags.txt) plus CHANGELOG.txt",
        generateHelp = true)
public class UnpackCommand implements Command<CommandInvocation> {

    @Argument(description = "Package export (zip, or folder with resources.cnt)", paramLabel = "package", index = "0",
            required = true)
    String pkg;

    @Argument(description = "Output folder", paramLabel = "out-dir", index = "1", required = true)
    String out;

    @Option(name = "strip",
            description = "Comma-separated tokens removed from symbolic names, MANIFEST.MF and .project "
                    + "(e.g. a tenant suffix)")
    String strip;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            Path p = Path.of(pkg);
            if (!Files.exists(p)) {
                throw new ScaService.CliException("Package export not found: " + pkg);
            }
            List<String> tokens = strip == null ? List.of()
                    : Arrays.stream(strip.split(",")).map(String::strip).filter(t -> !t.isEmpty()).toList();
            PackageUnpacker.unpack(p, Path.of(out), tokens);
            int artifacts = IFlowReader.readAll(p).size();
            System.err.println("Unpacked " + artifacts + " artifact(s) into " + out);
            return CommandResult.SUCCESS;
        });
    }
}
