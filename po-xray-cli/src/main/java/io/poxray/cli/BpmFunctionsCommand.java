package io.poxray.cli;

import io.poxray.bpm.BuiltinFunctionGenerator;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;

/**
 * {@code po-xray bpm-functions <nwds-plugins-dir> <output.json>}: the NW BPM built-in function and standard trigger
 * catalog ({@code bpm-builtin-functions.json}) generated from an NWDS plugins folder. The output is derived from SAP
 * software: keep it in private repositories only.
 */
@CommandDefinition(name = "bpm-functions",
        description = "Generate the NW BPM built-in function catalog (bpm-builtin-functions.json) from an NWDS "
                + "plugins folder (com.sap.tc.ap.servicecomp_*.jar, com.sap.tc.ap.bpemcontent_*.jar)",
        generateHelp = true)
public class BpmFunctionsCommand implements Command<CommandInvocation> {

    @Argument(description = "NWDS plugins folder", paramLabel = "nwds-plugins-dir", index = "0", required = true)
    String plugins;

    @Argument(description = "Output JSON file", paramLabel = "output.json", index = "1", required = true)
    String out;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            BuiltinFunctionGenerator.Result result;
            try {
                result = BuiltinFunctionGenerator.generate(Path.of(plugins), Path.of(out));
            } catch (IllegalArgumentException e) {
                throw new ScaService.CliException(e.getMessage());
            }
            invocation.println(result.summary() + " -> " + out);
            return CommandResult.SUCCESS;
        });
    }
}
