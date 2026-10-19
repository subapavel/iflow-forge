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
    def SenderAlias = headers.get("SAP_SenderAlias");
    def Pid = headers.get("partnerID");
    if (Pid == null){
        throw new IllegalStateException("Partner ID not found in sent message");   
    }

    // First, read the inbound conversion end point from the corresponding custom header if applicable
    def inbConvEndpoint = headers.get("customXInboundConversionEndpoint");
    
    // Secondly, check if sender-specific inbound conversion is maintained in the Partner Directory
    if (inbConvEndpoint == null){
        inbConvEndpoint = service.getParameter("InboundConversionEndpoint_" + SenderAlias, Pid , String.class);
    }   
    
    // Otherwise, read the inbound conversion end point InboundConversionEndpoint from the Partner Directory
    if (inbConvEndpoint == null){
        inbConvEndpoint = service.getParameter("InboundConversionEndpoint", Pid , String.class);
    }
    
    // if the inbound conversion end point exists, create a new exchange property containing the end point
    // otherwise, the exchange property is not created
    message.setProperty("inbConvEndpoint", inbConvEndpoint ?: null);
    
    // create a new exchange property with value true or false depending on whether the end point exists
    message.setProperty("inbConvBoolean", inbConvEndpoint ? 'true' : 'false');

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("readInboundConversionFromPD", "Partner ID to read the inbound conversion: " + (Pid ?: "n/a") + ".")
	logger.addEntry("readInboundConversionFromPD", "Inbound conversion end point from the Partner Directory: property inbConvEndpoint=" + (message.properties.inbConvEndpoint.toString() ?: "n/a") + ".")
	logger.addEntry("readInboundConversionFromPD", "Inbound conversion existence from the Partner Directory: property inbConvBoolean=" + (message.properties.inbConvBoolean.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Partner ID to read the inbound conversion: ").append(Pid ?: "n/a").toString();
	//logger.addEntry("readInboundConversionFromPD", logEntry);
	//logEntry = new StringBuilder().append("Inbound conversion end point from the Partner Directory: property inbConvEndpoint=").append(message.properties.inbConvEndpoint.toString() ?: "n/a").toString();
	//logger.addEntry("readInboundConversionFromPD", logEntry);
	//logEntry = new StringBuilder().append("Inbound conversion existence from the Partner Directory: property inbConvBoolean=").append(message.properties.inbConvBoolean.toString() ?: "n/a").toString();
	//logger.addEntry("readInboundConversionFromPD", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	//}

    return message;
}