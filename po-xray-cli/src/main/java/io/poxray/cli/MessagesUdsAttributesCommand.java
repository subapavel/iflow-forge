package io.poxray.cli;

import io.poxray.json.Json;
import io.poxray.monitoring.BusinessAttribute;
import io.poxray.monitoring.Lookup;
import io.poxray.monitoring.MonitoringClient;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;

/** {@code po-xray messages uds-attributes <id-or-key>}: the user-defined search attributes of a message. */
@CommandDefinition(name = "uds-attributes",
        description = "User-defined search attributes of a message (getUserDefinedSearchAttributes)", generateHelp = true)
public class MessagesUdsAttributesCommand extends MessagesKeySubcommand {

    @Override
    void run(CommandInvocation invocation) {
        MessagesService.checkSave(save);
        MonitoringClient client = service.client(system);
        String messageKey = key(invocation, client);
        Lookup<BusinessAttribute> r = client.getUserDefinedSearchAttributes(messageKey, archive);
        MessagesService.save(save, saveName(messageKey), r, r.rawResponse(), null);
        if (json) {
            invocation.println(Json.write(r).stripTrailing());
        } else {
            r.values().forEach(a -> invocation.println(MessagesService.or(a.name()) + " = "
                    + (a.value() == null ? "-" : a.value())));
        }
        MessagesService.otherFields(r.otherFields().size());
    }
}
