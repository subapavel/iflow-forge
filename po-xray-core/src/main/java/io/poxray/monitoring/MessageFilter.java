package io.poxray.monitoring;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The search filter of the monitoring service ({@code AdapterFilter}, F-17 Appendix C), immutable, built with
 * {@link #builder()}. Components are in the WSDL order, which is also the order of the request. A null component is
 * an unset optional field and is not sent; the eight fields the WSDL declares required ({@code archive},
 * {@code dateType}, {@code nodeId}, {@code onlyFaultyMessages}, {@code retries}, {@code retryInterval},
 * {@code timesFailed}, {@code wasEdited}) are always sent, false or 0 unless set. The field {@code interface} is the
 * component {@code interfaceRef}.
 */
public record MessageFilter(
        String applicationComponent,
        boolean archive,
        String connectionName,
        String correlationID,
        int dateType,
        String direction,
        String errorCategory,
        String errorCode,
        OffsetDateTime fromTime,
        MessageInterface interfaceRef,
        String messageFormat,
        List<String> messageIDs,
        String messageType,
        int nodeId,
        boolean onlyFaultyMessages,
        OffsetDateTime persistUntil,
        String protocol,
        String qualityOfService,
        MessageInterface receiverInterface,
        String receiverName,
        Party receiverParty,
        List<String> referenceIDs,
        int retries,
        long retryInterval,
        OffsetDateTime scheduleTime,
        MessageInterface senderInterface,
        String senderName,
        Party senderParty,
        String sequenceID,
        String serializationContext,
        String serviceDefinition,
        String softwareComponent,
        String status,
        List<String> statuses,
        int timesFailed,
        OffsetDateTime toTime,
        String transport,
        OffsetDateTime validUntil,
        boolean wasEdited,
        List<String> scenarioIDs,
        String rootID,
        Boolean returnLogLocations,
        Boolean onlyLogLocationsWithPayload) {

    public MessageFilter {
        messageIDs = copy(messageIDs);
        referenceIDs = copy(referenceIDs);
        statuses = copy(statuses);
        scenarioIDs = copy(scenarioIDs);
    }

    private static List<String> copy(List<String> list) {
        return list == null ? null : List.copyOf(list);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Appends the children of {@code AdapterFilter} (namespace WS, prefix {@code ws}) in the WSDL order. */
    void writeTo(StringBuilder out) {
        Soap.element(out, "ws", "applicationComponent", applicationComponent);
        Soap.element(out, "ws", "archive", archive);
        Soap.element(out, "ws", "connectionName", connectionName);
        Soap.element(out, "ws", "correlationID", correlationID);
        Soap.element(out, "ws", "dateType", dateType);
        Soap.element(out, "ws", "direction", direction);
        Soap.element(out, "ws", "errorCategory", errorCategory);
        Soap.element(out, "ws", "errorCode", errorCode);
        Soap.element(out, "ws", "fromTime", fromTime);
        writeInterface(out, "interface", interfaceRef);
        Soap.element(out, "ws", "messageFormat", messageFormat);
        Soap.strings(out, "ws", "messageIDs", messageIDs);
        Soap.element(out, "ws", "messageType", messageType);
        Soap.element(out, "ws", "nodeId", nodeId);
        Soap.element(out, "ws", "onlyFaultyMessages", onlyFaultyMessages);
        Soap.element(out, "ws", "persistUntil", persistUntil);
        Soap.element(out, "ws", "protocol", protocol);
        Soap.element(out, "ws", "qualityOfService", qualityOfService);
        writeInterface(out, "receiverInterface", receiverInterface);
        Soap.element(out, "ws", "receiverName", receiverName);
        writeParty(out, "receiverParty", receiverParty);
        Soap.strings(out, "ws", "referenceIDs", referenceIDs);
        Soap.element(out, "ws", "retries", retries);
        Soap.element(out, "ws", "retryInterval", retryInterval);
        Soap.element(out, "ws", "scheduleTime", scheduleTime);
        writeInterface(out, "senderInterface", senderInterface);
        Soap.element(out, "ws", "senderName", senderName);
        writeParty(out, "senderParty", senderParty);
        Soap.element(out, "ws", "sequenceID", sequenceID);
        Soap.element(out, "ws", "serializationContext", serializationContext);
        Soap.element(out, "ws", "serviceDefinition", serviceDefinition);
        Soap.element(out, "ws", "softwareComponent", softwareComponent);
        Soap.element(out, "ws", "status", status);
        Soap.strings(out, "ws", "statuses", statuses);
        Soap.element(out, "ws", "timesFailed", timesFailed);
        Soap.element(out, "ws", "toTime", toTime);
        Soap.element(out, "ws", "transport", transport);
        Soap.element(out, "ws", "validUntil", validUntil);
        Soap.element(out, "ws", "wasEdited", wasEdited);
        Soap.strings(out, "ws", "scenarioIDs", scenarioIDs);
        Soap.element(out, "ws", "rootID", rootID);
        Soap.element(out, "ws", "returnLogLocations", returnLogLocations);
        Soap.element(out, "ws", "onlyLogLocationsWithPayload", onlyLogLocationsWithPayload);
    }

    private static void writeInterface(StringBuilder out, String name, MessageInterface value) {
        if (value != null) {
            out.append("<ws:").append(name).append('>');
            value.writeTo(out);
            out.append("</ws:").append(name).append('>');
        }
    }

    private static void writeParty(StringBuilder out, String name, Party value) {
        if (value != null) {
            out.append("<ws:").append(name).append('>');
            value.writeTo(out);
            out.append("</ws:").append(name).append('>');
        }
    }

    /** Builder with one method per {@code AdapterFilter} field; {@code interface} is {@link #interfaceRef}. */
    public static final class Builder {

        private String applicationComponent;
        private boolean archive;
        private String connectionName;
        private String correlationID;
        private int dateType;
        private String direction;
        private String errorCategory;
        private String errorCode;
        private OffsetDateTime fromTime;
        private MessageInterface interfaceRef;
        private String messageFormat;
        private List<String> messageIDs;
        private String messageType;
        private int nodeId;
        private boolean onlyFaultyMessages;
        private OffsetDateTime persistUntil;
        private String protocol;
        private String qualityOfService;
        private MessageInterface receiverInterface;
        private String receiverName;
        private Party receiverParty;
        private List<String> referenceIDs;
        private int retries;
        private long retryInterval;
        private OffsetDateTime scheduleTime;
        private MessageInterface senderInterface;
        private String senderName;
        private Party senderParty;
        private String sequenceID;
        private String serializationContext;
        private String serviceDefinition;
        private String softwareComponent;
        private String status;
        private List<String> statuses;
        private int timesFailed;
        private OffsetDateTime toTime;
        private String transport;
        private OffsetDateTime validUntil;
        private boolean wasEdited;
        private List<String> scenarioIDs;
        private String rootID;
        private Boolean returnLogLocations;
        private Boolean onlyLogLocationsWithPayload;

        private Builder() {
        }

        public Builder applicationComponent(String value) {
            applicationComponent = value;
            return this;
        }

        public Builder archive(boolean value) {
            archive = value;
            return this;
        }

        public Builder connectionName(String value) {
            connectionName = value;
            return this;
        }

        public Builder correlationID(String value) {
            correlationID = value;
            return this;
        }

        public Builder dateType(int value) {
            dateType = value;
            return this;
        }

        public Builder direction(String value) {
            direction = value;
            return this;
        }

        public Builder errorCategory(String value) {
            errorCategory = value;
            return this;
        }

        public Builder errorCode(String value) {
            errorCode = value;
            return this;
        }

        public Builder fromTime(OffsetDateTime value) {
            fromTime = value;
            return this;
        }

        /** The {@code AdapterFilter} field {@code interface}. */
        public Builder interfaceRef(MessageInterface value) {
            interfaceRef = value;
            return this;
        }

        public Builder messageFormat(String value) {
            messageFormat = value;
            return this;
        }

        public Builder messageIDs(List<String> value) {
            messageIDs = value;
            return this;
        }

        public Builder messageType(String value) {
            messageType = value;
            return this;
        }

        public Builder nodeId(int value) {
            nodeId = value;
            return this;
        }

        public Builder onlyFaultyMessages(boolean value) {
            onlyFaultyMessages = value;
            return this;
        }

        public Builder persistUntil(OffsetDateTime value) {
            persistUntil = value;
            return this;
        }

        public Builder protocol(String value) {
            protocol = value;
            return this;
        }

        public Builder qualityOfService(String value) {
            qualityOfService = value;
            return this;
        }

        public Builder receiverInterface(MessageInterface value) {
            receiverInterface = value;
            return this;
        }

        public Builder receiverName(String value) {
            receiverName = value;
            return this;
        }

        public Builder receiverParty(Party value) {
            receiverParty = value;
            return this;
        }

        public Builder referenceIDs(List<String> value) {
            referenceIDs = value;
            return this;
        }

        public Builder retries(int value) {
            retries = value;
            return this;
        }

        public Builder retryInterval(long value) {
            retryInterval = value;
            return this;
        }

        public Builder scheduleTime(OffsetDateTime value) {
            scheduleTime = value;
            return this;
        }

        public Builder senderInterface(MessageInterface value) {
            senderInterface = value;
            return this;
        }

        public Builder senderName(String value) {
            senderName = value;
            return this;
        }

        public Builder senderParty(Party value) {
            senderParty = value;
            return this;
        }

        public Builder sequenceID(String value) {
            sequenceID = value;
            return this;
        }

        public Builder serializationContext(String value) {
            serializationContext = value;
            return this;
        }

        public Builder serviceDefinition(String value) {
            serviceDefinition = value;
            return this;
        }

        public Builder softwareComponent(String value) {
            softwareComponent = value;
            return this;
        }

        public Builder status(String value) {
            status = value;
            return this;
        }

        public Builder statuses(List<String> value) {
            statuses = value;
            return this;
        }

        public Builder timesFailed(int value) {
            timesFailed = value;
            return this;
        }

        public Builder toTime(OffsetDateTime value) {
            toTime = value;
            return this;
        }

        public Builder transport(String value) {
            transport = value;
            return this;
        }

        public Builder validUntil(OffsetDateTime value) {
            validUntil = value;
            return this;
        }

        public Builder wasEdited(boolean value) {
            wasEdited = value;
            return this;
        }

        public Builder scenarioIDs(List<String> value) {
            scenarioIDs = value;
            return this;
        }

        public Builder rootID(String value) {
            rootID = value;
            return this;
        }

        public Builder returnLogLocations(Boolean value) {
            returnLogLocations = value;
            return this;
        }

        public Builder onlyLogLocationsWithPayload(Boolean value) {
            onlyLogLocationsWithPayload = value;
            return this;
        }

        public MessageFilter build() {
            return new MessageFilter(applicationComponent, archive, connectionName, correlationID, dateType, direction,
                    errorCategory, errorCode, fromTime, interfaceRef, messageFormat, messageIDs, messageType, nodeId,
                    onlyFaultyMessages, persistUntil, protocol, qualityOfService, receiverInterface, receiverName,
                    receiverParty, referenceIDs, retries, retryInterval, scheduleTime, senderInterface, senderName,
                    senderParty, sequenceID, serializationContext, serviceDefinition, softwareComponent, status,
                    statuses, timesFailed, toTime, transport, validUntil, wasEdited, scenarioIDs, rootID,
                    returnLogLocations, onlyLogLocationsWithPayload);
        }
    }
}
