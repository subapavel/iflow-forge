package io.poxray.cli;

import io.poxray.PoXray;
import io.poxray.doc.DocFormat;
import io.poxray.doc.Documentation;
import io.poxray.json.Json;
import io.poxray.model.Issue;
import io.poxray.model.ScaInventory;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import org.aesh.command.CommandResult;

/** Shared logic of the commands: reading an SCA, writing outputs, uniform error handling. */
@ApplicationScoped
public class ScaService {

    /** Thrown for user errors (missing file, ...): printed without stack trace. */
    static final class CliException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CliException(String message) {
            super(message);
        }
    }

    /** Runs a command body; errors are printed to stderr and mapped to exit code 1. */
    CommandResult run(Callable<CommandResult> body) {
        try {
            return body.call();
        } catch (CliException e) {
            System.err.println("Error: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return CommandResult.FAILURE;
    }

    /** Reads the SCA and prints a one-line summary to stderr. */
    ScaInventory read(String sca) {
        Path file = Path.of(sca);
        if (!Files.isRegularFile(file)) {
            throw new CliException("SCA file not found: " + sca);
        }
        ScaInventory inv = PoXray.read(file);
        long processes = inv.bpm().stream().mapToLong(b -> b.processes().size()).sum();
        System.err.printf("%s: %d DCs, %d processes, %d EJB modules; issues: %d errors, %d warnings, %d info%n",
                file.getFileName(), inv.developmentComponents().size(), processes, inv.ejb().size(),
                count(inv, Issue.ERROR), count(inv, Issue.WARNING), count(inv, Issue.INFO));
        return inv;
    }

    String json(ScaInventory inv) {
        return Json.write(inv);
    }

    /** Renders the documentation in the requested formats into {@code dir}; returns the written files. */
    List<Path> documentation(ScaInventory inv, String sca, String format, String lang, Path dir) {
        List<DocFormat> formats = switch (format) {
            case "markdown" -> List.of(DocFormat.MARKDOWN);
            case "jira" -> List.of(DocFormat.JIRA);
            default -> List.of(DocFormat.MARKDOWN, DocFormat.JIRA);
        };
        Locale locale = Locale.forLanguageTag(lang);
        return formats.stream()
                .map(f -> write(dir.resolve(baseName(sca) + f.extension()), Documentation.render(inv, f, locale)))
                .toList();
    }

    Path write(Path file, String content) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }

    /** SCA file name without extension, e.g. {@code EONBPMDIGI00_0}. */
    static String baseName(String sca) {
        String name = Path.of(sca).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static long count(ScaInventory inv, String severity) {
        return inv.issues().stream().filter(i -> i.severity().equals(severity)).count();
    }
}
