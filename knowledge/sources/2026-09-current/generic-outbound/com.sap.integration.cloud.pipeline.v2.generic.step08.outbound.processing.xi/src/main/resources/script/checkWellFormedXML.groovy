import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger

def Message processData(Message message) {
    Reader reader = message.getBody(Reader)
	def properties = message.getProperties()
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	def location = properties.get("location") ?: "Check If XML"
    try {
        def xml = new XmlSlurper().parse(reader)
        message.setProperty("isWellFormedXml", 'true')
		logger.addEntry(location, "Property isWellFormedXml=true; message body is well-formed XML.")
    }
    catch (ex)
    {
        message.setProperty("isWellFormedXml", 'false')
		logger.addEntry(location, "Property isWellFormedXml=false; message body is not well-formed XML.")
    }

	if (logger.getLog()) { logger.saveToMessage(message) } 
    return message
}