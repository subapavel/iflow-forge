import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger

def Message processData(Message message) {
    def headers = message.getHeaders()
    def properties = message.getProperties()
	def location = properties.get("location") ?: "Set XI Values"
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")

    def keys = [
        "Receiver_XIEndpoint",
        "Receiver_XICCLocationId",
        "Receiver_XICredentialName",
        "Receiver_XIPrivateKeyAlias",
		"Receiver_XIProxyType",
		"Receiver_XIAuthenticationType",
        "SenderParty",
        "Sender",
        "ReceiverParty",
        "Receiver",
        "ReceiverInterface", // ReceiverInterface: precedence ReceiverInterface, then SAP_MessageType, last fallback is XML Root stored in property default_ReceiverInterface
        "ReceiverInterfaceNamespace"
    ]
	
    def customHeaders = [
        "SenderParty",
        "ReceiverParty",
        "ReceiverInterface",
        "ReceiverInterfaceNamespace"
    ]
    
    def businessComponents = [
        "Sender",
        "Receiver",
    ]
    
    keys.each { key ->
        def finalValue = null
        def kv

        // 1. Headers coming with prefix "SAP_" from interface-spepcific IFlow has precedence
        if (headers.containsKey("SAP_${key}".toString())) {
            finalValue = headers.get("SAP_${key}".toString())
        } 
        // 2. If no "SAP_" header - use "default_" property from externalized configuration
        else if (properties.containsKey("default_${key}".toString())) {
            finalValue = properties.get("default_${key}".toString())
			logger.addEntry(location, "No scenario-specific header SAP_${key} found, property ${key} is assigned to value ${finalValue} of default property default_${key}.")
        } 
        // 3. Fallback is empty value
        else {
			finalValue = ""
			logger.addEntry(location, "No header SAP_${key} and no property default_${key}, fallback empty value ${finalValue} is assigned to property ${key}.")
        }
		
        // If key belongs to businessComponents list, remove party if any
        if (key in businessComponents) {
            kv = finalValue.split(/\./, 2)
            if (kv?.size() == 2) {
                finalValue = kv[1]?.trim()
                message.setProperty("customHeader_${key}System", finalValue)
	        }
        }
        
        message.setProperty(key, finalValue)
        
        // If key belongs to customHeaders list
        if (key in customHeaders) {
            message.setProperty("customHeader_${key}", finalValue)
        }
    }
    
	if (logger.getLog()) { logger.saveToMessage(message) }  
    return message
}