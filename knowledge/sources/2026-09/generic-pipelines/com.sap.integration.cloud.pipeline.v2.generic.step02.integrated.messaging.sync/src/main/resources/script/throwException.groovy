import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {

    // get exception message
    def propertyMap = message.getProperties()
    String exceptionMessage = propertyMap.get("exceptionMessage");

    throw new Exception(exceptionMessage);

    return message;
}