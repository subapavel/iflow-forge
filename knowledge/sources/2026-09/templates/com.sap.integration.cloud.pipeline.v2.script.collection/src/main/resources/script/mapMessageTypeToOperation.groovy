import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {

    // Get headers
    def headers = message.getHeaders();
    
    // Get message type
    def prefix = "SAP-MT2OP-";
    def messageType = message.getProperty("messageTypeFromXMLRoot");
    def propsKey = message.getProperties().keySet().findAll{ it.contains(prefix) };
    def operationTarget;
    
    propsKey.each{ key ->
        def operationID = key.substring(10);
        def messageTypeSource = message.getProperty(key);
        
        if (messageType == messageTypeSource){
            operationTarget = operationID;
        }
    }
    
    // Set new header SAP_SenderOperation
    message.setHeader("SAP_SenderOperation", operationTarget);

    // Append header customHeaderProperties
    String CustomHeaders = headers.get("customHeaderProperties");
    CustomHeaders = CustomHeaders ? CustomHeaders + '|' : '';
    CustomHeaders = CustomHeaders + 'SAP_SenderOperation' + ':' + operationTarget;
    message.setHeader("customHeaderProperties", CustomHeaders);
    
    return message;
}