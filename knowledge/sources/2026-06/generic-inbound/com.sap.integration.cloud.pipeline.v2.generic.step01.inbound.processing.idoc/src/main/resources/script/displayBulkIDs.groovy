import com.sap.gateway.ip.core.customdev.util.Message
import org.w3c.dom.NodeList
import groovy.json.JsonOutput

Message processData(Message message) {

    def nodes = message.getProperty('nodelistToHeader') as NodeList
    def headerName = message.getProperty('nodelistName') as String
    def messageLog = messageLogFactory.getMessageLog(message)

    // --- bulk flag with default = false (case-insensitive) ---
    def individualHeaders4BulkFlag = (message.getProperty('idocBulkIndividualHeader') ?: "false").toString().equalsIgnoreCase("true")

    // 1: Handle SAP-specific headers (SapIDocDbId / SapIDocTransferId)
	def headers = message.getHeaders()
	String sapDbId = headers.get('SapIDocDbId')
	String sapTransferId = headers.get('SapIDocTransferId')

    if (sapDbId) {
        def cleaned = sapDbId.toString().replaceFirst("^0+", "")
        messageLog?.addCustomHeaderProperty("SapIDocDbId", cleaned)
    }

    if (sapTransferId) {
        def cleaned = sapTransferId.toString().replaceFirst("^0+", "")
        messageLog?.addCustomHeaderProperty("SapIDocTransferId", cleaned)
    }

    // 2: Handle NodeList-based logic
	
	// --- If no nodes or only one node → nothing to do ---
	if (!nodes || nodes.getLength() <= 1) {
		return message
	}

    // ----- CUSTOM HEADERS: INDIVIDUAL MODE -----
    if (individualHeaders4BulkFlag) {
        for (int i = 0; i < nodes.getLength(); i++) {
            def value = nodes.item(i).getTextContent().replaceFirst("^0+", "")
            messageLog?.addCustomHeaderProperty(headerName, value)
        }
        return message
    }

    // ----- CUSTOM HEADERS: BULK MODE -----
	def list = []
	for (int i = 0; i < nodes.getLength(); i++) {
		def cleaned = (nodes.item(i)?.getTextContent() ?: "")
				.toString()
				.replaceFirst("^0+", "")
		if (cleaned != "") {
			if (cleaned.isInteger()) {
				list << cleaned.toInteger()
			} else {
				list << cleaned
			}
		}
	}

	if (!list.isEmpty()) {
		def jsonString = JsonOutput.toJson(list)
		messageLog?.addCustomHeaderProperty("${headerName}_bulk", jsonString)
	}

    return message
}