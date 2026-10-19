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
	
    return message
}