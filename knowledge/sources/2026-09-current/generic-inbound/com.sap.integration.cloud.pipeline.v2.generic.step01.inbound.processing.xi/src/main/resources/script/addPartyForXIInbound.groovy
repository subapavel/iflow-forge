import com.sap.gateway.ip.core.customdev.util.Message;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {
    
    // get headers
    def headers = message.getHeaders();

    // Fetch sender party and sender service
    String party = headers.get("SapSenderParty");
    String service = headers.get("SapSenderService");
        
    // Concatenate if party exists
    if (party != null && party != ''){
        message.setHeader("SAP_Sender",party + '.' + service);
        message.setHeader("SAP_SenderParty",party);
    }

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("addPartyForXIInbound", "Header SAP_Sender=" + (message.headers.SAP_Sender.toString() ?: "n/a") + ".")
	logger.addEntry("addPartyForXIInbound", "Header SAP_SenderParty=" + (message.headers.SAP_SenderParty.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
    //PipelineLogger logger = PipelineLogger.newLogger(message)
    //def logEntry = new StringBuilder().append("Header SAP_Sender=").append(message.headers.SAP_Sender.toString() ?: "n/a").toString();
	//logger.addEntry("addPartyForXIInbound", logEntry);
	//logEntry = new StringBuilder().append("Header SAP_SenderParty=").append(message.headers.SAP_SenderParty.toString() ?: "n/a").toString();
	//logger.addEntry("addPartyForXIInbound", logEntry);
    //if (logger.getAuditLog()) {
	//    message.setHeader('auditLogHeader', logger.getAuditLog())
    //}

    return message;
}