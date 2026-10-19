import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {

    //get body
    def body = message.getBody();
    
    // get headers
    def headers = message.getHeaders();
    def isWellFormedXml = headers.get("isWellFormedXml");

    if (isWellFormedXml == "true"){
        message.setProperty("payload", body);
    } else {
        message.setProperty("payload", body.bytes.encodeBase64().toString());
    }

    return message
}