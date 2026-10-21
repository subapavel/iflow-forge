import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
	// Constant: Property Prefix for Dynamic Configuration
	final String DC_PPREFIX = "SapDynamicConfiguration_"
	// Constant: Header Prefix for Dynamic Configuration
	final String DC_HPREFIX = "dc_" + DC_PPREFIX

	def headers = message.getHeaders()
	def resultDynamicConfigurations = [:] // Start with empty map

    try {
        // read all headers matching the prefix
        headers?.findAll { k, v -> 
            k instanceof String && k.startsWith(DC_HPREFIX)
        }?.each { k, v ->
            def cleanKey = k.replaceFirst("^${DC_HPREFIX}", "")
			resultDynamicConfigurations[cleanKey] = v
        }
    } catch (Exception ignored) {
        // Ignore on failure
    }
    
    if (resultDynamicConfigurations && !resultDynamicConfigurations.isEmpty()) {
        resultDynamicConfigurations.each { key, value ->
			message.setProperty("${DC_PPREFIX}${key}", value)
        }
	}
	
	return message
}