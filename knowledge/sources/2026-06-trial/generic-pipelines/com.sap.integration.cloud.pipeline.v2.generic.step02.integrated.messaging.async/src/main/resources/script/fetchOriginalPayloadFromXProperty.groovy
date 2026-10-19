import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {

    // get headers
    def headers = message.getHeaders();
    def isWellFormedXml = headers.get("isWellFormedXml");
    
    // get properties
    def properties = message.getProperties();

    if (isWellFormedXml == "true"){
        message.setBody(properties.get("originalPayload"));
    } else {
        message.setBody(properties.get("originalPayload").decodeBase64());
    }

    return message
}