import com.sap.gateway.ip.core.customdev.util.Message;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("logOutboundProcessingEndpoint", "Bypass option: property bypassOption=" + (message.properties.bypassOption.toString() ?: "n/a") + ".")
	logger.addEntry("logOutboundProcessingEndpoint", "Header SAP_OutboundProcessingEndpoint=" + (message.headers.SAP_OutboundProcessingEndpoint.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Bypass option: property bypassOption=").append(message.properties.bypassOption.toString() ?: "n/a").toString();
	//logger.addEntry("logOutboundProcessingEndpoint", logEntry);
	//logEntry = new StringBuilder().append("Header SAP_OutboundProcessingEndpoint=").append(message.headers.SAP_OutboundProcessingEndpoint.toString() ?: "n/a").toString();
	//logger.addEntry("logOutboundProcessingEndpoint", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	//}

    return message;
}