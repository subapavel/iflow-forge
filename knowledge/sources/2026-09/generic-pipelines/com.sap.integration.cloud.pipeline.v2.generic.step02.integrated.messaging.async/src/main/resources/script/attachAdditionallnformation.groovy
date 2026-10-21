import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {

    // get queue names
    def propertyMap = message.getProperties()
    def incomingQueue = propertyMap.get("incomingQueue")
    def skipRetry = propertyMap.get("CamelExceptionCaught").toString().contains("determinePartnerID.groovy") ? 'true' : propertyMap.get("skipRetry")
    def deadLetterQueue
    if (incomingQueue != null) {
        deadLetterQueue = new StringBuilder(incomingQueue).append('_DLQ').toString()
    }
    def customStatus = propertyMap.get("SAP_MessageProcessingLogCustomStatus")?:'NONE'
    // get test mode
    def headers = message.getHeaders()
    def testMode = headers.get("testMode")

    // adding information to audit log using helper PipelineLogger class
    def messageLog = messageLogFactory.getMessageLog(message)
    if (messageLog != null) {
    //    PipelineLogger logger = PipelineLogger.newLogger(message)
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        
        if (testMode != 'true' && skipRetry == 'true' && incomingQueue != null)  {
            logger.addEntry("attachAdditionallnformation", "Message retry skipped")
            logger.addEntry("attachAdditionallnformation", "Message removed from queue " + incomingQueue + " and sent to dead letter queue " + deadLetterQueue + ".")
            if (logger.getLog()) { 
                logger.saveToMessage(message)
            }
        }
        else if (testMode != 'true' && !customStatus.contains("OptionNotSupported") && incomingQueue != null){
		    logger.addEntry("attachAdditionallnformation", "Maximum number of retries exceeded.")
		    logger.addEntry("attachAdditionallnformation", "Message removed from queue " + incomingQueue + " and sent to dead letter queue " + deadLetterQueue + ".")
            //def logEntry = new StringBuilder().append("Message removed from queue ").append(incomingQueue).append(" and sent to dead letter queue ").append(deadLetterQueue).toString();
            if (logger.getLog()) { 
	            logger.saveToMessage(message)
	        }
            //logger.addEntry("attachAdditionallnformation", logEntry);            
        }
        messageLog.addAttachmentAsString('Additional Information', logger.getAuditLog4Print(), 'text/plain')
    }
    return message
}