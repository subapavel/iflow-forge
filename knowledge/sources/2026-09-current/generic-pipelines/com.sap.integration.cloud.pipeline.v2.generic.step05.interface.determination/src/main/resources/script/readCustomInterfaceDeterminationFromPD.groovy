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

    // check for custom interface determination by reading the custom interface determination end point from the Partner Directory
    def customXIDEndpoint = service.getParameter("CustomXIDEndpoint", Pid , String.class);
    
    // create a new exchange property with value true or false depending on whether the end point exists
    message.setProperty("customXIDBoolean", customXIDEndpoint ? 'true' : 'false');
    
    // if the end point exists, create a new exchange property containing the end point
    // otherwise, the exchange property is not created
    message.setProperty("customXIDEndpoint", customXIDEndpoint ?: null);

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message)
	def logEntry = new StringBuilder().append("Partner ID to read the custom interface determination: ").append(Pid ?: "n/a").toString();
	logger.addEntry("customInterfaceDetermination", logEntry);
	logEntry = new StringBuilder().append("Custom interface determination end point from the Partner Directory: property customXIDEndpoint=").append(message.properties.customXIDEndpoint.toString() ?: "n/a").toString();
	logger.addEntry("customInterfaceDetermination", logEntry);
	logEntry = new StringBuilder().append("Custom interface determination existence from the Partner Directory: property customXIDBoolean=").append(message.properties.customXIDBoolean.toString() ?: "n/a").toString();
	logger.addEntry("customInterfaceDetermination", logEntry);
	if (logger.getAuditLog()) {
		message.setHeader('auditLogHeader', logger.getAuditLog())
	}
    
    return message;
}