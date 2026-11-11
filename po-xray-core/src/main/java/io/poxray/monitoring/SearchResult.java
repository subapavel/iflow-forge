package io.poxray.monitoring;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.w3c.dom.Element;

/**
 * The answer of a message search ({@code MessageSearchReturnValue}, F-17 Appendix C) from the system named
 * {@code system}: the messages (never null), the service's {@code number}, {@code moreAvailable} (the service's
 * {@code warning}: more messages match than were returned), {@code displayPermissionWarning}, {@code date}, and the
 * SOAP answer as received.
 */
public record SearchResult(String system, List<Message> messages, Integer number, boolean moreAvailable,
        boolean displayPermissionWarning, OffsetDateTime date, String rawResponse) {

    public SearchResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    /** Reads the {@code OP:Response} element of a search answer. */
    static SearchResult read(String system, Element response, String rawResponse) {
        List<Message> messages = new ArrayList<>();
        Element list = Soap.child(response, Soap.WS, "list");
        for (Element data : Soap.children(list)) {
            if (Soap.is(data, Soap.WS, "AdapterFrameworkData") && !Soap.nil(data)) {
                messages.add(Message.read(data));
            }
        }
        return new SearchResult(system, messages, integer(Soap.childText(response, Soap.WS, "number")),
                bool(Soap.childText(response, Soap.WS, "warning")),
                bool(Soap.childText(response, Soap.WS, "displayPermissionWarning")),
                dateTime(Soap.childText(response, Soap.WS, "date")), rawResponse);
    }

    private static Integer integer(String text) {
        try {
            return text == null ? null : Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean bool(String text) {
        String t = text == null ? "" : text.strip();
        return t.equals("true") || t.equals("1");
    }

    private static OffsetDateTime dateTime(String text) {
        try {
            return text == null ? null : OffsetDateTime.parse(text.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
