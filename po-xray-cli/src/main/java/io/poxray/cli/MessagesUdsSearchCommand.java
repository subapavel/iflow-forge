package io.poxray.cli;

import io.poxray.monitoring.BusinessAttribute;
import io.poxray.monitoring.SearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.aesh.command.CommandDefinition;
import org.aesh.command.invocation.CommandInvocation;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;

/** {@code po-xray messages uds-search --attr name=value ... [--operator AND|OR] [filter options]}. */
@CommandDefinition(name = "uds-search",
        description = "Search messages by user-defined search attributes (getUserDefinedSearchMessages)",
        generateHelp = true)
public class MessagesUdsSearchCommand extends MessagesFilterSubcommand {

    @OptionList(name = "attr", required = true, description = "Search attribute name=value (several: a=1,b=2)")
    List<String> attributes;

    @Option(name = "operator", defaultValue = "AND", description = "How the attributes combine: AND or OR")
    String operator;

    @Override
    void run(CommandInvocation invocation) {
        String op = operator.strip().toUpperCase(Locale.ROOT);
        if (!op.equals("AND") && !op.equals("OR")) {
            throw new IllegalArgumentException("--operator must be AND or OR, not '" + operator + "'");
        }
        List<BusinessAttribute> attrs = new ArrayList<>();
        for (String a : attributes) {
            int eq = a.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("--attr needs name=value, not '" + a + "'");
            }
            attrs.add(new BusinessAttribute(a.substring(0, eq).strip(), a.substring(eq + 1)));
        }
        SearchResult result = service.client(system).getUserDefinedSearchMessages(filter(), limit, attrs, op);
        service.print(invocation, result, json);
    }
}
