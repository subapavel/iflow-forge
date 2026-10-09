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

/** {@code po-xray doc <sca> [-f markdown|jira|all] [-l cs|en] [-o dir]}: generated documentation. */
@CommandDefinition(name = "doc", description = "Read an SCA and generate documentation (Markdown, Jira wiki markup)",
        generateHelp = true)
public class DocCommand implements Command<CommandInvocation> {

    @Argument(description = "NWDI software component archive (.sca)", paramLabel = "sca", required = true)
    String sca;

    @Option(shortName = 'f', name = "format", description = "Output format", defaultValue = "all",
            allowedValues = {"markdown", "jira", "all"})
    String format;

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
            for (Path file : service.documentation(inv, sca, format, lang, Path.of(output))) {
                System.err.println("Written " + file);
            }
            return CommandResult.SUCCESS;
        });
    }
}
