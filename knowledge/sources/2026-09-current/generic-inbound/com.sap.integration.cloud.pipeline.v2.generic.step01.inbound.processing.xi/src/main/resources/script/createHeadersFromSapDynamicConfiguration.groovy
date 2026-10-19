import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
	// Constant: Property Prefix for Dynamic Configuration
	final String DC_PPREFIX = "SapDynamicConfiguration_"
	// Constant: Header Prefix for Dynamic Configuration
	final String DC_HPREFIX = "dc_" + DC_PPREFIX
	def properties = message.getProperties()
	def resultDynamicConfigurations = [:] // Start with empty map

    try {
        // read all properties matching the prefix
        properties?.findAll { k, v -> 
            k instanceof String && k.startsWith(DC_PPREFIX)
        }?.each { k, v ->
            def cleanKey = k.replaceFirst("^${DC_PPREFIX}", "")
			resultDynamicConfigurations[cleanKey] = v
        }
    } catch (Exception ignored) {
        // Ignore on failure
    }
    
    if (resultDynamicConfigurations && !resultDynamicConfigurations.isEmpty()) {
        resultDynamicConfigurations.each { key, value ->
			message.setHeader("${DC_HPREFIX}${key}", value)
        }
	}
	
	return message
}