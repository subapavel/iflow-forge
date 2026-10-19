import com.sap.gateway.ip.core.customdev.util.Message;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {
    Reader reader = message.getBody(Reader)
    try {
        def xml = new XmlSlurper().parse(reader)
        message.setHeader("isWellFormedXml", 'true')
    }
    catch (ex)
    {
        message.setHeader("isWellFormedXml", 'false')
    }

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("checkWellFormedXML", "Header isWellFormedXml=" + (message.headers.isWellFormedXml.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}  
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Header isWellFormedXml=").append(message.headers.isWellFormedXml.toString() ?: "n/a").toString();
	//logger.addEntry("checkWellFormedXML", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	//}

    return message
}