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

    // first check if a header exists for custom receiver determination
    def customXRDEndpoint = headers.get("customXRDEndpoint");
    
    // otherwise, check for custom receiver determination by reading the custom receiver determination end point from the Partner Directory
    if (customXRDEndpoint == null){
        customXRDEndpoint = service.getParameter("CustomXRDEndpoint", Pid , String.class);
    }
    
    // create a new exchange property with value true or false depending on whether the end point exists
    message.setProperty("customXRDBoolean", customXRDEndpoint ? 'true' : 'false');
    
    // do second check only if custom receiver determination is not used
    if (customXRDEndpoint == null){
    
    // check for extended receiver determination by reading the extended receiver determination end point from the Partner Directory
        customXRDEndpoint = service.getParameter("ReuseXRDEndpoint", Pid , String.class);    
    
    // create a new exchange property with value true or false depending on whether the end point exists
    message.setProperty("reuseXRDBoolean", customXRDEndpoint ? 'true' : 'false');    
    }
    
    // if either one of the end points exist, create a new exchange property containing the end point
    // otherwise, the exchange property is not created
    message.setProperty("customXRDEndpoint", customXRDEndpoint ?: null);

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("customReceiverDetermination", "Partner ID to read the custom receiver determination: " + (Pid ?: "n/a") + ".")
	logger.addEntry("customReceiverDetermination", "Custom or extended receiver determination end point from the Partner Directory: property customXRDEndpoint=" + (message.properties.customXRDEndpoint.toString() ?: "n/a") + ".")
	logger.addEntry("customReceiverDetermination", "Custom receiver determination existence from the Partner Directory: property customXRDBoolean=" + (message.properties.customXRDBoolean.toString() ?: "n/a") + ".")
	logger.addEntry("customReceiverDetermination", "Reuse extended receiver determination existence from the Partner Directory: property reuseXRDBoolean=" + (message.properties.reuseXRDBoolean.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Partner ID to read the custom receiver determination: ").append(Pid ?: "n/a").toString();
	//logger.addEntry("customReceiverDetermination", logEntry);
	//logEntry = new StringBuilder().append("Custom or extended receiver determination end point from the Partner Directory: property customXRDEndpoint=").append(message.properties.customXRDEndpoint.toString() ?: "n/a").toString();
	//logger.addEntry("customReceiverDetermination", logEntry);
	//logEntry = new StringBuilder().append("Custom receiver determination existence from the Partner Directory: property customXRDBoolean=").append(message.properties.customXRDBoolean.toString() ?: "n/a").toString();
	//logger.addEntry("customReceiverDetermination", logEntry);
	//logEntry = new StringBuilder().append("Reuse extended receiver determination existence from the Partner Directory: property reuseXRDBoolean=").append(message.properties.reuseXRDBoolean.toString() ?: "n/a").toString();
	//logger.addEntry("customReceiverDetermination", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	////}
    
    return message;
}