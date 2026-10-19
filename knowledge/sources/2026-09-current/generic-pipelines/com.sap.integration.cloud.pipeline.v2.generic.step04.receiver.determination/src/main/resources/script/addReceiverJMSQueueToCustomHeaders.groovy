import com.sap.gateway.ip.core.customdev.util.Message

Message processData(Message message) {

    def properties = message.getProperties()
    def messageLog = messageLogFactory.getMessageLog(message)

    def outgoingQueue = properties.get("outgoingQueue")

    if (outgoingQueue) {
        messageLog?.addCustomHeaderProperty("ReceiverJMSQueue", outgoingQueue)
    }

    return message
}