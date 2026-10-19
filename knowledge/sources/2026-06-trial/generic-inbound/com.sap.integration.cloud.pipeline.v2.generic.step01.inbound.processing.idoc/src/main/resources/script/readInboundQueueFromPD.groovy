import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.it.api.pd.PartnerDirectoryService;
import com.sap.it.api.ITApiFactory;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {
    
    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null); 
    if (service == null){
        throw new IllegalStateException("Partner Directory Service not found");
    }
    
    // get pid
    def headers = message.getHeaders();
    def Pid = headers.get("partnerID");
    if (Pid == null){
        throw new IllegalStateException("Partner ID not found in sent message");   
    }

    // read the inbound queue from the Partner Directory
    def inbQueue = service.getParameter("InboundQueue", Pid , String.class);
    
    // if the inbound queue exists, create a new exchange property containing the queue name
    // otherwise, the exchange property is not created
    message.setProperty("inbQueue", inbQueue ?: null);
    
	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("readInboundQueueFromPD", "Partner ID to read the inbound queue:" + (Pid ?: "n/a") + ".")
	logger.addEntry("readInboundQueueFromPD", "Inbound queue from the Partner Directory: property inbQueue=" + (message.properties.inbQueue.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Partner ID to read the inbound queue: ").append(Pid ?: "n/a").toString();
	//logger.addEntry("readInboundQueueFromPD", logEntry);
	//logEntry = new StringBuilder().append("Inbound queue from the Partner Directory: property inbQueue=").append(message.properties.inbQueue.toString() ?: "n/a").toString();
	//logger.addEntry("readInboundQueueFromPD", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	//}

    return message;
}