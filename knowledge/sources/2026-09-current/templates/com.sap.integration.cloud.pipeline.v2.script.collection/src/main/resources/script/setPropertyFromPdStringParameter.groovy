import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.pd.PartnerDirectoryService
import com.sap.it.api.ITApiFactory

def Message processData(Message message) {
	// Constant: Prefix for Properties
	final String SP_PREFIX = "pidStringParameter_"
	def headers = message.getHeaders()
	def properties = message.getProperties() //read all Exchange Properties with dummy values to extract actual ones from Partner Directory
	def setStringParametersProperties = [:] // Start with empty map

	def pid = (properties.get("pidToLookup")) ? properties.get("pidToLookup") : headers.get("partnerID")
	def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
    if (!service || !pid){
        return message   // exit early to avoid exception
    }
	
	// find all properties with prefix ${SP_PREFIX}
	properties?.findAll { k, v -> 
        k instanceof String && k.startsWith(SP_PREFIX)
    }?.each { k, v ->
        def cleanKey = k.replaceFirst("^${SP_PREFIX}", "")
        setStringParametersProperties[cleanKey] = v
    }
	
	// if properties found, then access Partner Directory to retrieve actual values and store as Properties
	if (setStringParametersProperties && !setStringParametersProperties.isEmpty()) {
		setStringParametersProperties.each { key, value ->
			def stringParameterValue = service.getParameter(key, pid, String)
			if (stringParameterValue != null) {
				message.setProperty("${SP_PREFIX}${key}", stringParameterValue)
			}
        }
	}
	
	return message
}