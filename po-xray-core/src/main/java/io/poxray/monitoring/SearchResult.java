package io.poxray.monitoring;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Element;

/**
 * The answer of a message search ({@code MessageSearchReturnValue}, F-17 Appendix C) from the system named
 * {@code system}: the messages (never null), the service's {@code number}, {@code moreAvailable} (the service's
 * {@code warning}: more messages match than were returned), {@code displayPermissionWarning}, {@code date}, the SOAP
 * answer as received, and {@code otherFields} (never null): by element name in document order, every value the reader
 * cannot read as its type (number, date-time, boolean; the component is then null or false), every unknown or
 * repeated child ({@code name#2}, ...; complex ones as XML, nil ones as {@code "(nil)"}) and every item of
 * {@code list} that is not an {@code AdapterFrameworkData} (as {@code list/<name>}).
 */
public record SearchResult(String system, List<Message> messages, Integer number, boolean moreAvailable,
        boolean displayPermissionWarning, OffsetDateTime date, String rawResponse, Map<String, String> otherFields) {

    public SearchResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
        otherFields = otherFields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(otherFields));
    }

    /** Reads the {@code OP:Response} element of a search answer. */
    static SearchResult read(String system, Element response, String rawResponse) {
        List<Message> messages = new ArrayList<>();
        Map<String, String> other = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        Integer number = null;
        boolean warning = false;
        boolean permission = false;
        OffsetDateTime date = null;
        for (Element e : Soap.children(response)) {
            int occurrence = seen.merge(e.getNamespaceURI() + " " + e.getLocalName(), 1, Integer::sum);
            if (!Soap.WS.equals(e.getNamespaceURI()) || occurrence > 1) {
                Soap.keep(other, e, occurrence);
                continue;
            }
            String text = Soap.nil(e) || Soap.complex(e) ? null : e.getTextContent().strip();
            switch (e.getLocalName()) {
                case "list" -> list(e, messages, other);
                case "number" -> number = Soap.nil(e) ? null : keepUnless(integer(text), e, other);
                case "warning" -> warning = !Soap.nil(e) && flag(bool(text), e, other);
                case "displayPermissionWarning" -> permission = !Soap.nil(e) && flag(bool(text), e, other);
                case "date" -> date = Soap.nil(e) ? null : keepUnless(dateTime(text), e, other);
                default -> Soap.keep(other, e, occurrence);
            }
        }
        return new SearchResult(system, messages, number, warning, permission, date, rawResponse, other);
    }

    private static void list(Element list, List<Message> messages, Map<String, String> other) {
        for (Element data : Soap.children(list)) {
            if (Soap.is(data, Soap.WS, "AdapterFrameworkData")) {
                if (!Soap.nil(data)) {
                    messages.add(Message.read(data));
                }
            } else {
                String key = "list/" + data.getLocalName();
                for (int n = 2; other.containsKey(key); n++) {
                    key = "list/" + data.getLocalName() + "#" + n;
                }
                other.put(key, Soap.nil(data) ? Soap.NIL : Soap.complex(data) ? Soap.xml(data) : data.getTextContent());
            }
        }
    }

    /** The parsed value; when it is null (the text does not parse), the element's text is kept in {@code other}. */
    private static <T> T keepUnless(T value, Element e, Map<String, String> other) {
        if (value == null) {
            Soap.keep(other, e, 1);
        }
        return value;
    }

    private static boolean flag(Boolean value, Element e, Map<String, String> other) {
        if (value == null) {
            Soap.keep(other, e, 1);
            return false;
        }
        return value;
    }

    private static Integer integer(String text) {
        try {
            return text == null ? null : Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(String text) {
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "true", "1" -> true;
            case "false", "0" -> false;
            default -> null;
        };
    }

    private static OffsetDateTime dateTime(String text) {
        try {
            return text == null ? null : OffsetDateTime.parse(text);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
