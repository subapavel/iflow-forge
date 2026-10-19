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

    // read keep bulk parameter from the Partner Directory
    def keepBulk = service.getParameter("KeepBulk", Pid , String.class);
    
    // if the value exists in the Partner Directory, create a new property with configured value
    // otherwise, set the property to default which is false
    message.setProperty("keepBulk", keepBulk ?: 'false');

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("readKeepBulkSettingFromPD", "Partner ID to read the bulk setting: " + (Pid ?: "n/a") + ".")
	logger.addEntry("readKeepBulkSettingFromPD", "Keep bulk property keepBulk=" + (message.properties.keepBulk.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Partner ID to read the bulk setting: ").append(Pid ?: "n/a").toString();
	//logger.addEntry("readKeepBulkSettingFromPD", logEntry);
	//logEntry = new StringBuilder().append("Keep bulk property keepBulk=").append(message.properties.keepBulk.toString() ?: "n/a").toString();
	//logger.addEntry("readKeepBulkSettingFromPD", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	//}
    
    return message;
}