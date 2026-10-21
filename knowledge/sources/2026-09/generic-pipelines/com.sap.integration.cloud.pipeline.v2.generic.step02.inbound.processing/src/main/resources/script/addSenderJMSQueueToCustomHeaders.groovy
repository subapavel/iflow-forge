import com.sap.gateway.ip.core.customdev.util.Message

Message processData(Message message) {

    def properties = message.getProperties()
    def messageLog = messageLogFactory.getMessageLog(message)

    def incomingQueue = properties.get("incomingQueue")

    if (incomingQueue) {
        messageLog?.addCustomHeaderProperty("SenderJMSQueue", incomingQueue)
    }

    return message
}