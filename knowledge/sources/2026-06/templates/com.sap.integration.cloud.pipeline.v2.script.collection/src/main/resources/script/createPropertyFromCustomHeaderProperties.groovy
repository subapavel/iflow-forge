import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineHelper

def Message processData(Message message) {
	// Constant: Property Prefix for Custom Header Property
	final String CH_PREFIX = "customHeader_"
	def headers = message.getHeaders()
    def custHeaderProps = headers.get('customHeaderProperties') //custom headers are either stored in the standard pipeline header
	def resultCustomHeaderProperties = [:] // Start with empty map

    try {
         // first check if header customHeaderProperties exists and already defined (only String input is expected)
		 if (custHeaderProps && custHeaderProps instanceof String && custHeaderProps?.trim()) {
            // Split by '|' and handle each part independently (backward compatibility to avoid failure of handling input like jsonInBase64|key:value 
            custHeaderProps.split(/\|/).each { part ->
                def decodedPart = PipelineHelper.decodeIfBase64(part) //if base64 and JSON - decode to Map, otherwise return String
                if (decodedPart instanceof Map) {
                    resultCustomHeaderProperties.putAll(decodedPart)
                } else if (decodedPart instanceof String && PipelineHelper.isValidKeyValueString(decodedPart)) {
					//if String - check if it is delimited as expected and if yes - split and convert to result Map
					decodedPart.split(/\|/).each { pair ->
						def kv = pair.split(":", 2)
						if (kv?.size() == 2) {
							resultCustomHeaderProperties[kv[0]?.trim()] = kv[1]?.trim()
						}
					}
                }
                // else ignore
            }
        }       
    } catch (Exception ignored) {
        // Fallback to empty map on any failure
        resultCustomHeaderProperties = [:]
    }		

    if (resultCustomHeaderProperties && !resultCustomHeaderProperties.isEmpty()) {
        resultCustomHeaderProperties.each { key, value ->
			message.setProperty("${CH_PREFIX}${key}", value)
        }
	}
	
	return message
}