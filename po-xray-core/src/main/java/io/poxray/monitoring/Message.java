package io.poxray.monitoring;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.w3c.dom.Element;

/**
 * One message of a search ({@code AdapterFrameworkData}, F-17 Appendix C). Absent or nil children are null;
 * {@code businessAttributes}, {@code logLocations}, {@code flags} and {@code otherFields} are never null.
 * {@code flags} are the {@code BooleanAttribute} children by element name in document order;
 * {@code durationMillis} is {@code duration/duration}. {@code otherFields} holds every other child by element name in
 * document order: a simple child as its text, a complex child as its XML string, a nil unknown child as
 * {@code "(nil)"}, a mapped child whose text does not parse as its type (date-time, number, boolean) as its text, and
 * every repetition of a mapped child (the first one is mapped) as {@code name#2}, {@code name#3}, ..., so nothing is
 * dropped. A nil mapped child is null.
 */
public record Message(
        String messageId,
        String messageKey,
        String status,
        String errorCode,
        String errorCategory,
        Integer errorLabel,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        Party senderParty,
        String senderName,
        MessageInterface senderInterface,
        Party receiverParty,
        String receiverName,
        MessageInterface receiverInterface,
        MessageInterface interfaceRef,
        String direction,
        String qualityOfService,
        String connectionName,
        String sequenceId,
        String rootId,
        String parentId,
        Long size,
        List<BusinessAttribute> businessAttributes,
        List<String> logLocations,
        Map<String, Boolean> flags,
        Long durationMillis,
        Map<String, String> otherFields) {

    /** The {@code BooleanAttribute} children of {@code AdapterFrameworkData}. */
    private static final Set<String> FLAGS =
            Set.of("businessMessage", "cancelable", "editable", "restartable", "wasEdited", "payloadPermissionWarning");
    /** The children mapped to a component (the cases of {@code Reader.field} and {@link #FLAGS}). */
    private static final Set<String> MAPPED = union(FLAGS, Set.of("messageID", "messageKey", "status", "errorCode",
            "errorCategory", "errorLabel", "startTime", "endTime", "senderParty", "senderName", "senderInterface",
            "receiverParty", "receiverName", "receiverInterface", "interface", "direction", "qualityOfService",
            "connectionName", "sequenceID", "rootID", "parentID", "size", "businessAttributes", "logLocations",
            "duration"));

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }

    public Message {
        businessAttributes = businessAttributes == null ? List.of() : List.copyOf(businessAttributes);
        logLocations = logLocations == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(logLocations));
        flags = flags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(flags));
        otherFields = otherFields == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(otherFields));
    }

    /** Reads one {@code WS:AdapterFrameworkData} element. */
    static Message read(Element data) {
        return new Reader().read(data);
    }

    /** Collects the children of one {@code AdapterFrameworkData}. */
    private static final class Reader {
        private String messageId;
        private String messageKey;
        private String status;
        private String errorCode;
        private String errorCategory;
        private Integer errorLabel;
        private OffsetDateTime startTime;
        private OffsetDateTime endTime;
        private Party senderParty;
        private String senderName;
        private MessageInterface senderInterface;
        private Party receiverParty;
        private String receiverName;
        private MessageInterface receiverInterface;
        private MessageInterface interfaceRef;
        private String direction;
        private String qualityOfService;
        private String connectionName;
        private String sequenceId;
        private String rootId;
        private String parentId;
        private Long size;
        private final List<BusinessAttribute> businessAttributes = new ArrayList<>();
        private final List<String> logLocations = new ArrayList<>();
        private final Map<String, Boolean> flags = new LinkedHashMap<>();
        private Long durationMillis;
        private final Map<String, String> other = new LinkedHashMap<>();
        /** Occurrence (from 1) of the child being read among the children of its name. */
        private int occurrence;

        Message read(Element data) {
            Map<String, Integer> seen = new HashMap<>();
            for (Element e : Soap.children(data)) {
                occurrence = seen.merge(e.getNamespaceURI() + " " + e.getLocalName(), 1, Integer::sum);
                if (!Soap.WS.equals(e.getNamespaceURI()) || occurrence > 1) {
                    other(e); // foreign, or a repeated child: the first one is mapped, later ones are kept
                } else if (!Soap.nil(e)) {
                    field(e);
                } else if (!MAPPED.contains(e.getLocalName())) {
                    other(e); // a nil mapped child is null; a nil unknown child is kept as (nil)
                }
            }
            return new Message(messageId, messageKey, status, errorCode, errorCategory, errorLabel, startTime, endTime,
                    senderParty, senderName, senderInterface, receiverParty, receiverName, receiverInterface,
                    interfaceRef, direction, qualityOfService, connectionName, sequenceId, rootId, parentId, size,
                    businessAttributes, logLocations, flags, durationMillis, other);
        }

        private void field(Element e) {
            String name = e.getLocalName();
            switch (name) {
                case "messageID" -> messageId = text(e);
                case "messageKey" -> messageKey = text(e);
                case "status" -> status = text(e);
                case "errorCode" -> errorCode = text(e);
                case "errorCategory" -> errorCategory = text(e);
                case "errorLabel" -> errorLabel = integer(e);
                case "startTime" -> startTime = dateTime(e);
                case "endTime" -> endTime = dateTime(e);
                case "senderParty" -> senderParty = Party.read(e);
                case "senderName" -> senderName = text(e);
                case "senderInterface" -> senderInterface = MessageInterface.read(e);
                case "receiverParty" -> receiverParty = Party.read(e);
                case "receiverName" -> receiverName = text(e);
                case "receiverInterface" -> receiverInterface = MessageInterface.read(e);
                case "interface" -> interfaceRef = MessageInterface.read(e);
                case "direction" -> direction = text(e);
                case "qualityOfService" -> qualityOfService = text(e);
                case "connectionName" -> connectionName = text(e);
                case "sequenceID" -> sequenceId = text(e);
                case "rootID" -> rootId = text(e);
                case "parentID" -> parentId = text(e);
                case "size" -> size = longValue(e);
                case "businessAttributes" -> {
                    for (Element a : Soap.children(e)) {
                        businessAttributes.add(BusinessAttribute.read(a));
                    }
                }
                case "logLocations" -> {
                    for (Element s : Soap.children(e)) {
                        logLocations.add(Soap.nil(s) ? null : s.getTextContent());
                    }
                }
                case "duration" -> {
                    Element d = Soap.child(e, Soap.DATA, "duration");
                    durationMillis = d == null || Soap.nil(d) ? null : longValue(d, e);
                }
                default -> {
                    if (FLAGS.contains(name)) {
                        flag(e);
                    } else {
                        other(e);
                    }
                }
            }
        }

        private void flag(Element e) {
            Element v = Soap.child(e, Soap.DATA, "value");
            String t = v == null || Soap.nil(v) ? null : v.getTextContent().strip();
            switch (t == null ? "" : t) {
                case "true", "1" -> flags.put(e.getLocalName(), true);
                case "false", "0" -> flags.put(e.getLocalName(), false);
                default -> other(e); // no value or not a boolean: kept as XML
            }
        }

        /** Text of a simple child; a complex one goes to {@code otherFields} and the field stays null. */
        private String text(Element e) {
            if (Soap.complex(e)) {
                other(e);
                return null;
            }
            return e.getTextContent();
        }

        private Integer integer(Element e) {
            String t = text(e);
            try {
                return t == null ? null : Integer.valueOf(t.strip());
            } catch (NumberFormatException x) {
                other(e);
                return null;
            }
        }

        private Long longValue(Element e) {
            return longValue(e, e);
        }

        /** Long text of {@code value}; if it does not parse, {@code owner} goes to {@code otherFields}. */
        private Long longValue(Element value, Element owner) {
            String t = Soap.complex(value) ? null : value.getTextContent().strip();
            try {
                if (t == null) {
                    other(owner);
                    return null;
                }
                return Long.valueOf(t);
            } catch (NumberFormatException x) {
                other(owner);
                return null;
            }
        }

        private OffsetDateTime dateTime(Element e) {
            String t = text(e);
            try {
                return t == null ? null : OffsetDateTime.parse(t.strip());
            } catch (DateTimeParseException x) {
                other(e);
                return null;
            }
        }

        /** Keeps the current child by element name ({@link Soap#keep}): the n-th of a name is {@code name#n}. */
        private void other(Element e) {
            Soap.keep(other, e, occurrence);
        }
    }
}
