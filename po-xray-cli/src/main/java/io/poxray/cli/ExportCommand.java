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

/** {@code po-xray export <sca> [-l cs|en] [-o dir]}: JSON + Markdown + Jira documentation in one go. */
@CommandDefinition(name = "export", description = "Read an SCA and write JSON and documentation (both formats)",
        generateHelp = true)
public class ExportCommand implements Command<CommandInvocation> {

    @Argument(description = "NWDI software component archive (.sca)", paramLabel = "sca", required = true)
    String sca;

    @Option(shortName = 'l', name = "lang", description = "Document language", defaultValue = "cs",
            allowedValues = {"cs", "en"})
    String lang;

    @Option(shortName = 'o', name = "output", description = "Output directory", defaultValue = ".")
    String output;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> {
            ScaInventory inv = service.read(sca);
            Path dir = Path.of(output);
            System.err.println("Written " + service.write(dir.resolve(ScaService.baseName(sca) + ".json"),
                    service.json(inv)));
            for (Path file : service.documentation(inv, sca, "all", lang, dir)) {
                System.err.println("Written " + file);
            }
            return CommandResult.SUCCESS;
        });
    }
}
