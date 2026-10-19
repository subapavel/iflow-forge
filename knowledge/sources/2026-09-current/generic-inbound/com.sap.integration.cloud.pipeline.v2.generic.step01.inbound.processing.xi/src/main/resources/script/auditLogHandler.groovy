import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger

def Message attachAuditLog(Message message) {
    def properties = message.getProperties()
	PipelineLogger logger = PipelineLogger.newLogger(message, 'auditLog')
	
	def attachmentName = properties.get('mplAttachmentName') ?: 'Audit Log Information'
	def mediaType = properties.get('mplAttachmentMediaType') ?: 'text/plain'
	
	// optional
	// if log entry to be added before MPL attachment (to avoid calling addAuditLog)
	def logText = properties.get('messageText') ?: ''
	def location = properties.get('location') ?: 'Attach Audit Log Script'
	if (logText) {
		logger.addEntry(location, logText)
	}

    // attach Audit Log Header to the MPL
    def messageLog = messageLogFactory.getMessageLog(message);
    if (messageLog) {
		messageLog.addAttachmentAsString(attachmentName, logger.getLog4Print(), mediaType)
	}

    return message
}

def Message addAuditLog(Message message) {
    def properties = message.getProperties()
	PipelineLogger logger = PipelineLogger.newLogger(message, 'auditLog')
	
	def logText = properties.get('messageText') ?: ''
	def location = properties.get('location') ?: 'Add Audit Log Script'
	
	if (logText) { // if log entry is supplied in property messageText - then add it to the Audit Log
		logger.addEntry(location, logText)
	}

	if (logger.getLog()) { // save audit log
		logger.saveToMessage(message)
	}
    return message
}