package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.monitoring.MessagePayload;
import io.poxray.monitoring.MonitoringClient;
import io.poxray.monitoring.PayloadPart;
import java.nio.charset.StandardCharsets;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;

/**
 * {@code po-xray messages payload <id-or-key> [--version <n> | --logged <v>] [--part <n>]}: the payload
 * (getMessageBytes..., getLoggedMessageBytes). The selected part goes to stdout, the part list to stderr.
 */
@CommandDefinition(name = "payload", description = "Payload of a message: one MIME part on stdout, the part list on "
        + "stderr", generateHelp = true)
public class MessagesPayloadCommand extends MessagesKeySubcommand {

    @Option(name = "version", description = "Version of the message (getMessageBytes with version)")
    Integer version;

    @Option(name = "logged", description = "Logged version, e.g. a log location such as BI or AM (getLoggedMessageBytes)")
    String logged;

    @Option(name = "part", description = "Index of the part printed, from 0 (default: the first part that is not an XI "
            + "header (SOAP envelope))")
    Integer part;

    @Override
    void run(CommandInvocation invocation) {
        if (version != null && logged != null) {
            throw new IllegalArgumentException("Give --version or --logged, not both");
        }
        MessagesService.checkSave(save);
        MonitoringClient client = service.client(system);
        String messageKey = key(invocation, client);
        MessagePayload p = version != null ? client.getMessageBytes(messageKey, version, archive)
                : logged != null ? client.getLoggedMessageBytes(messageKey, logged, archive)
                : client.getMessageBytes(messageKey, archive);
        MessagesService.save(save, saveName(messageKey), p, p.rawResponse(), p.bytes());
        if (json) {
            invocation.println(Json.write(p).stripTrailing());
            return;
        }
        if (p.parts().isEmpty()) {
            System.err.println(p.note() + " (" + (p.bytes() == null ? 0 : p.bytes().length) + " bytes)");
            if (p.bytes() != null) {
                invocation.println(new String(p.bytes(), StandardCharsets.UTF_8));
            }
            return;
        }
        int selected = part != null ? part : p.defaultPart();
        if (selected < 0 || selected >= p.parts().size()) {
            throw new IllegalArgumentException("No part " + selected + ": the payload has " + p.parts().size()
                    + " part(s), 0.." + (p.parts().size() - 1));
        }
        for (PayloadPart pp : p.parts()) {
            System.err.println((pp.index() == selected ? "* " : "  ") + "part " + pp.index() + "  "
                    + MessagesService.or(pp.contentId()) + "  " + MessagesService.or(pp.contentType()) + "  "
                    + pp.size() + " bytes" + (pp.text() == null ? " (binary, printed as base64)" : ""));
        }
        PayloadPart chosen = p.parts().get(selected);
        invocation.println(chosen.text() != null ? chosen.text() : chosen.base64());
    }
}
