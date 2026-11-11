package io.poxray.cli;

import io.poxray.monitoring.MonitoringClient;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;

/**
 * The options of the commands that read one message ({@code log}, {@code payload}, {@code uds-attributes}): the
 * message ID or key, {@code --key}, {@code --direction}, {@code --archive}, {@code --save}.
 */
abstract class MessagesKeySubcommand extends MessagesSubcommand {

    @Argument(description = "Message ID or message key (a value with a backslash is a key)", paramLabel = "id-or-key")
    String idOrKey;

    @Option(name = "key", description = "Message key to read, e.g. one of the candidates listed for an ambiguous ID")
    String key;

    @Option(name = "direction", description = "OUTBOUND (sender side) or INBOUND (receiver side) for a message ID")
    String direction;

    @Option(name = "archive", hasValue = false, description = "Read from the archive")
    boolean archive;

    @Option(name = "save",
            description = "Save response.xml, model.json (and payload.bin) under <dir>/<message>/; inside a git work "
                    + "tree only below local/ or testdata/")
    String save;

    /** The key to read (see {@link MessagesService#key}). */
    String key(CommandInvocation invocation, MonitoringClient client) {
        return service.key(invocation, client, idOrKey, key, direction, archive);
    }

    /** The directory name of a saved message: the ID or key given. */
    String saveName(String messageKey) {
        return idOrKey != null && !idOrKey.isBlank() ? idOrKey.strip() : messageKey;
    }
}
