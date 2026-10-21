import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.it.api.pd.PartnerDirectoryService;
import com.sap.it.api.ITApiFactory;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {
    
    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null); 
    if (service == null){
        throw new IllegalStateException("Partner Directory Service not found");
    }
    
    // get headers
    def headers = message.getHeaders();
    def ReceiverAlias = headers.get("SAP_ReceiverAlias");
    
    // First check if receiver-specific queue is maintained for a particular configuration scenario
    String Pid = headers.get("partnerID");
    def receiverSpecificQueue = service.getParameter("ReceiverSpecificQueue_" + ReceiverAlias, Pid , String.class);

    // If not, check if receiver-specific queue is maintained for the particular receiver    
    if (receiverSpecificQueue == null) {
        receiverSpecificQueue = service.getParameter("ReceiverSpecificQueue", ReceiverAlias , String.class);
    }
    
    // if the receiver-specific queue exists, create a new exchange property containing the queue name
    // otherwise, the exchange property is not created
    message.setProperty("receiverSpecificQueueName", receiverSpecificQueue ?: null);
    
    // create a new exchange property with value true or false depending on whether the queue exists
    message.setProperty("receiverSpecificQueueBoolean", receiverSpecificQueue ? 'true' : 'false');

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("readReceiverSpecificQueueFromPD", "Header SAP_ReceiverAlias=" + (message.headers.SAP_ReceiverAlias ?: "n/a") + ".")
	logger.addEntry("readReceiverSpecificQueueFromPD", "PID to read the receiver-specific JMS queue from the Partner Directory: " + (Pid ?: "n/a") + ".")
	logger.addEntry("readReceiverSpecificQueueFromPD", "Receiver specific queue from the Partner Directory: property receiverSpecificQueueName=" + (message.properties.receiverSpecificQueueName.toString() ?: "n/a") + ".")
	logger.addEntry("readReceiverSpecificQueueFromPD", "Receiver specific queue existence from the Partner Directory: property receiverSpecificQueueBoolean=" + (message.properties.receiverSpecificQueueBoolean.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Header SAP_ReceiverAlias=").append(message.headers.SAP_ReceiverAlias ?: "n/a").toString();
	//logger.addEntry("readReceiverSpecificQueueFromPD", logEntry);
	//logEntry = new StringBuilder().append("PID to read the receiver-specific JMS queue from the Partner Directory: ").append(Pid ?: "n/a").toString();
	//logger.addEntry("readReceiverSpecificQueueFromPD", logEntry);
	//logEntry = new StringBuilder().append("Receiver specific queue from the Partner Directory: property receiverSpecificQueueName=").append(message.properties.receiverSpecificQueueName.toString() ?: "n/a").toString();
	//logger.addEntry("readReceiverSpecificQueueFromPD", logEntry);
	//logEntry = new StringBuilder().append("Receiver specific queue existence from the Partner Directory: property receiverSpecificQueueBoolean=").append(message.properties.receiverSpecificQueueBoolean.toString() ?: "n/a").toString();
	//logger.addEntry("readReceiverSpecificQueueFromPD", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	//}

    return message;
}