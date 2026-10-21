import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger
import src.main.resources.script.PipelineHelper

def Message processData(Message message) {

    def body = message.getBody(String)
    def properties = message.getProperties()
    def headers = message.getHeaders()
    def messageLog = messageLogFactory.getMessageLog(message)

    def mplAttachmentConfiguration = properties.get("mplLoggingConfiguration") ?: "AuditLog" // AuditLog,Payload,Property,Header
    def mplAttachmentPrefix = properties.get("mplAttachmentName") ?: "Logging"
    def mediaType = properties.get("mediaType") ?: "text/plain"

    List excludedHeaders = ['SAP_MessageProcessingLogID', 'SAP_MonitoringStateProperties', 'MplMarkers', 'SAP_MessageProcessingLog', 'auditLogHeader', 'SAP_AuthHeaderName', 'SAP_AuthHeaderValue']
    List excludedProperties = ['CamelMessageHistory', 'SAP_MessageProcessingLog', 'auditLogProperty', 'SAP_AuthHeaderName', 'SAP_AuthHeaderValue']

    if (messageLog) {
        // normalize configuration into a lowercase set for easy contains()
		def configSet = mplAttachmentConfiguration
			.toLowerCase()
			.tokenize(',;|')  // tokenize accepts a string of delimiters
			.collect { it.trim() }
			.toSet()

        // --- Payload ---
        if (["payload","body","messagebody"].any { configSet.contains(it) }) {
            if (body?.trim()) { // non empty body
                messageLog.addAttachmentAsString("${mplAttachmentPrefix}MessageBody", body, mediaType)
            }
        }

        // --- Headers ---
        if (configSet.contains("header") || configSet.contains("headers")) {
            def headersSB = new StringBuilder()
            def filteredHeaders = headers.findAll { k, v -> !(k in excludedHeaders) }
            PipelineHelper.dumpKeyValueAsTEXT("Headers", filteredHeaders, headersSB)
            messageLog.addAttachmentAsString("${mplAttachmentPrefix}Headers", headersSB.toString(), mediaType)
        }

        // --- Properties ---
        if (configSet.contains("property") || configSet.contains("properties")) {
            def propsSB = new StringBuilder()
            def filteredProps = properties.findAll { k, v -> !(k in excludedProperties) }
            PipelineHelper.dumpKeyValueAsTEXT("Properties", filteredProps, propsSB)
            messageLog.addAttachmentAsString("${mplAttachmentPrefix}Properties", propsSB.toString(), mediaType)
        }

        // --- AuditLog ---
        if (configSet.contains("auditlog")) {
            PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
            if (logger.getLog()) {
                messageLog.addAttachmentAsString("${mplAttachmentPrefix}AuditLog", logger.getLog4Print(), mediaType)
            }
        }
    }

    return message
}