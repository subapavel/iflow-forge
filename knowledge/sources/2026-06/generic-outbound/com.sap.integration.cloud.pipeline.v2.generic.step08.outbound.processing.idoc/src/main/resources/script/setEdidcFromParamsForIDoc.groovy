import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper
import groovy.xml.StreamingMarkupBuilder
import src.main.resources.script.PipelineLogger

def Message processData(Message message) {
    def headers = message.getHeaders()
    def properties = message.getProperties()
	def body = message.getBody(java.io.Reader)
	def xml = new XmlSlurper().parse(body)
	def location = properties.get("location") ?: "Override EDIDC Parameters"
	def nsUri = "urn:sap-com:document:sap:idoc:soap:messages"
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")

    def keysConnectivity = [
        "Receiver_IDocEndpoint",
        "Receiver_IDocCCLocationId",
        "Receiver_IDocCredentialName",
        "Receiver_IDocPrivateKeyAlias",
		"Receiver_IDocProxyType",
		"Receiver_IDocAuthenticationType",
		"Receiver_IDocContentType"
    ]
	
	// 1. IDoc connectivity related parameters: set final Properties
	keysConnectivity.each { key ->
		String headerName = "SAP_${key}"
		String propertyName = "default_${key}"
		def finalValue
		
		// 1.1. Headers coming with prefix "SAP_" from interface-spepcific IFlow has precedence
        if (headers.containsKey(headerName)) {
            finalValue = headers.get(headerName)
        } 
        // 1.2. If no "SAP_" header - use "default_" property from externalized configuration
        else if (properties.containsKey(propertyName)) {
            finalValue = properties.get(propertyName)
			logger.addEntry(location, "No scenario-specific header ${headerName} found, property ${key} is assigned to value ${finalValue} of default property ${propertyName}.")
        } 
        // 1.3. Fallback is empty value
        else {
			finalValue = ""
			logger.addEntry(location, "No header ${headerName} and no property ${propertyName}, fallback empty value is assigned to property ${key}.")
        }
		message.setProperty(key, finalValue)

	}
	
	// 2. IDoc content related parameters: override only control record attributes maintained in the header
	String overrideEDIDCparams = headers.get("SAP_IDoc_EDIDC_overrideControlRecordParameters")
	def resultValuesList = []
	
	if (overrideEDIDCparams) {
        overrideEDIDCparams.split(",").each { key ->
            key = key.trim()
			String headerName = "SAP_IDoc_EDIDC_${key}"
			String propertyName = "default_${key}"
			def finalValue
			
			 // 2.1. Headers coming with prefix "SAP_IDoc_EDIDC_" from interface-spepcific IFlow has precedence
			if (headers.containsKey(headerName)) {
				finalValue = headers.get(headerName)
			}            
			// 2.2. If no "SAP_IDoc_EDIDC_" header - use "default_" property from externalized configuration
			else if (properties.containsKey(propertyName)) {
				finalValue = properties.get(propertyName)
				logger.addEntry(location, "No scenario-specific header ${headerName} found, property ${key} is assigned to value ${finalValue} of default property ${propertyName}.")
			} 
			// 2.3. Fallback is empty value
			else {
				finalValue = ""
				logger.addEntry(location, "No header ${headerName} and no property ${propertyName}, fallback empty value is assigned to property ${key}.")
			}	
			
			message.setProperty(key, finalValue)
			resultValuesList << finalValue
			
			// update XML for every IDOC/EDI_DC40
            xml.IDOC.EDI_DC40.each { edidc ->
                def existingNode = edidc."${key}"
                if (existingNode && existingNode.size() > 0) {
                    existingNode.replaceBody(finalValue)
                } else {
                    edidc.appendNode { "${key}"(finalValue) }
                }
            }
        }
		message.setProperty("customHeader_overrideResultValues", resultValuesList.join(","))
	
		// Rebuild XML with updated values
		def updatedXml = new StreamingMarkupBuilder().bind {
			mkp.declareNamespace("ns0": nsUri)
			mkp.yield xml
		}
		message.setBody(updatedXml.toString())
	}
 
	if (logger.getLog()) { logger.saveToMessage(message) }   
    return message
}