import com.sap.gateway.ip.core.customdev.util.Message;
 
def Message processData(Message message) {
    def headers = message.getHeaders()
    def SapMessageIdEx = headers.get("SapMessageIdEx")
    
    if (SapMessageIdEx) {
        def id = SapMessageIdEx.replaceAll("-", "").toUpperCase()
        message.setHeader("SAP_ApplicationID", id)    
    }

    return message
}
