import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {
    def headers = message.getHeaders()
    def applicationId = headers.get('SAP_ApplicationID')

    if (applicationId != null){
        message.setHeader('SAP_ApplicationID', applicationId.replaceFirst("^0+", ""))
    }
    
    return message
}