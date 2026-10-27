package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.migration.ExportMetadata;
import io.poxray.model.ScaInventory;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.aesh.command.Command;
import org.aesh.command.CommandDefinition;
import org.aesh.command.CommandResult;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/**
 * {@code po-xray export <sca> [-l cs|en] [-o dir] [--provenance-input descriptor.json]}: JSON + Markdown + Jira
 * documentation in one go; with a provenance descriptor also {@code <base>.provenance.json} (not atomic: a failure
 * after the documentation leaves the files already written in place).
 */
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

    @Option(name = "provenance-input", description = "Optional local provenance descriptor JSON (caller-supplied "
            + "build/skill artifacts and version observations); also writes <base>.provenance.json")
    String provenanceInput;

    @Inject
    ScaService service;

    @Override
    public CommandResult execute(CommandInvocation invocation) {
        return service.run(() -> provenanceInput == null ? legacy() : withProvenance());
    }

    private CommandResult legacy() {
        ScaInventory inv = service.read(sca);
        Path dir = Path.of(output);
        System.err.println("Written " + service.write(dir.resolve(ScaService.baseName(sca) + ".json"),
                service.json(inv)));
        for (Path file : service.documentation(inv, sca, "all", lang, dir)) {
            System.err.println("Written " + file);
        }
        return CommandResult.SUCCESS;
    }

    /**
     * Descriptor read and preflight before the SCA is read or anything written; the sidecar last. The SCA path, the
     * output directory and the four destinations are made absolute and normalized once, and exactly those paths are
     * checked, read, written, printed and measured (a raw {@code ..} could otherwise resolve through a link the check
     * never saw).
     */
    private CommandResult withProvenance() throws Exception {
        ExportMetadata.Loaded descriptor = ExportMetadata.read(Path.of(provenanceInput));
        Path scaFile = Path.of(sca).toAbsolutePath().normalize();
        String scaName = scaFile.toString();
        Path dir = Path.of(output).toAbsolutePath().normalize();
        String base = ScaService.baseName(scaName);
        Path json = dir.resolve(base + ".json");
        List<Path> docs = service.documentationPaths(scaName, "all", dir);
        Path sidecar = dir.resolve(base + ".provenance.json");
        List<Path> planned = new ArrayList<>();
        planned.add(json);
        planned.addAll(docs);
        planned.add(sidecar);
        ExportMetadata.preflight(scaFile, descriptor, List.copyOf(planned));

        ScaInventory inv = service.read(scaName);
        List<Path> written = new ArrayList<>();
        written.add(service.write(json, service.json(inv)));
        System.err.println("Written " + json);
        List<Path> documentation = service.documentation(inv, scaName, "all", lang, dir);
        if (!documentation.equals(docs)) {
            throw new IllegalStateException("Documentation was written to " + documentation + ", not to the checked "
                    + docs);
        }
        for (Path file : docs) {
            written.add(file);
            System.err.println("Written " + file);
        }
        String manifest = Json.write(ExportMetadata.create(inv, descriptor, written));
        service.write(sidecar, manifest);
        System.err.println("Written " + sidecar);
        return CommandResult.SUCCESS;
    }
}
