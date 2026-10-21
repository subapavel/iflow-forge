import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {

    // fetch headers
    def headers = message.getHeaders()
    String source = headers.get("UniqueID") + headers.get("SAP_ReceiverInterface")
    String context = headers.get("SAP_ReceiverInterfaceNamespace")
    
    // remove special chars from context
    if (context) {
        context = 'C' + context.replaceAll(/[^a-zA-Z0-9]/, '')
    } else {
        context = 'IDMapper1'
    }
    // create properties
    message.setProperty("IdMapperSource", source.take(120))
    message.setProperty("IdMapperContext", context.take(40))

    return message;
}