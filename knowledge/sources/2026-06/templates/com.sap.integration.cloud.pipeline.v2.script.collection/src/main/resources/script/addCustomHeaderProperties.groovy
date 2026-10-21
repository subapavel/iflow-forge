import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineHelper
import groovy.json.JsonOutput
import java.util.Base64
import org.w3c.dom.NodeList

def Message processData(Message message) {
	// Constant: Property Prefix for Custom Header Property
	final String CH_PREFIX = "customHeader_"
	def headers = message.getHeaders()
    def custHeaderProps = headers.get('customHeaderProperties') //custom headers are either stored in the standard pipeline header
	def properties = message.getProperties() //or custom headers can be defined as IFlow-step specific Exchange Propeties
	def resultCustomHeaderProperties = [:] // Start with empty map
	def messageLog = messageLogFactory.getMessageLog(message)

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

    try {
        // Merge Exchange properties with prefix
        properties?.findAll { k, v -> 
            k instanceof String && k.startsWith(CH_PREFIX)
        }?.each { k, v ->
            def cleanKey = k.replaceFirst("^${CH_PREFIX}", "")
			if (v instanceof org.w3c.dom.NodeList) {
				// Handle NodeList: create multiple headers with same base name + counter
				for (int i = 0; i < v.getLength(); i++) {
					def nodeValue = v.item(i)?.getTextContent()?.trim()
					if (nodeValue) {
						def indexedKey = "${cleanKey}_NdLstElmnt${i + 1}" // unique in internal MAP with Node List Element index
						resultCustomHeaderProperties[indexedKey] = nodeValue
					}
				}
			} else {
				// Normal case (String or other type)
				resultCustomHeaderProperties[cleanKey] = v
			}
        }
    } catch (Exception ignored) {
        // Ignore on failure
    }

    try { //if final Customer Headers Map exists and not empty - attach to the MPL log and store the header with potentially new or same values
        if (messageLog && resultCustomHeaderProperties && !resultCustomHeaderProperties.isEmpty()) {
            resultCustomHeaderProperties.each { key, value ->
                def logKey = key.replaceFirst(/_NdLstElmnt\d+$/, "") // IF Node List Element index - remove suffix to simplify the search by custom value
				messageLog.addCustomHeaderProperty(logKey, value)
            }
			// Convert map to JSON
            def jsonString = JsonOutput.toJson(resultCustomHeaderProperties)
			// Encode JSON to Base64 and set standard header
            def base64Encoded = Base64.encoder.encodeToString(jsonString.getBytes("UTF-8"))
            message.setHeader('customHeaderProperties', base64Encoded)
        }
    } catch (Exception ignored) {
        // Log writing failed, continue
    }
	
	return message
}