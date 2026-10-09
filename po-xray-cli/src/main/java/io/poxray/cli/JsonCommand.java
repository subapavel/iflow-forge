package io.poxray.cli;

import io.poxray.model.ScaInventory;
import jakarta.inject.Inject;
import java.nio.file.Path;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/** {@code po-xray json <sca> [-o file]}: the inventory as JSON (docs/OUTPUT-MODEL.md). */
@CommandDefinition(name = "json", description = "Read an SCA and write the inventory as JSON", generateHelp = true)
public class JsonCommand implements Command<CommandInvocation> {

    @Argument(description = "NWDI software component archive (.sca)", paramLabel = "sca", required = true)
    String sca;

    @Option(shortName = 'o', name = "output", description = "Output file (default: standard output)")
    String output;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            ScaInventory inv = service.read(sca);
            String json = service.json(inv);
            if (output == null) {
                invocation.println(json.stripTrailing());
            } else {
                System.err.println("Written " + service.write(Path.of(output), json));
            }
            return CommandResult.SUCCESS;
        });
    }
}
