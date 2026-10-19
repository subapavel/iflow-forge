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

    // read receiver not determined type from the Partner Directory
    def receiverNotDeterminedType = service.getParameter("ReceiverNotDeterminedType", Pid , String.class);
    
    // if the value exists in the Partner Directory, assign the value to a new exchange property
    // otherwise, set the property to default which is Error
    message.setProperty("receiverNotDeterminedType", receiverNotDeterminedType ? receiverNotDeterminedType : 'Error');
    
    // depending on the type, read default receiver from the Partner Directory
    if (receiverNotDeterminedType == 'Default') {
        def receiverNotDeterminedDefault = service.getParameter("ReceiverNotDeterminedDefault", Pid , String.class);
        if (receiverNotDeterminedDefault == null){
            throw new IllegalStateException("Partner ID " + Pid + " not found or ReceiverNotDeterminedDefault parameter not found in the Partner Directory for the partner ID " + Pid);      
        }
        message.setProperty("receiverNotDeterminedDefault", receiverNotDeterminedDefault);
    }

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message)
	def logEntry = new StringBuilder().append("Partner ID to read the receiver not determined: ").append(Pid ?: "n/a").toString();
	logger.addEntry("readReceiverNotDeterminedFromPD", logEntry);
	logEntry = new StringBuilder().append("Receiver not determined type from the Partner Directory: property receiverNotDeterminedType=").append(message.properties.receiverNotDeterminedType.toString() ?: "n/a").toString();
	logger.addEntry("readReceiverNotDeterminedFromPD", logEntry);
	logEntry = new StringBuilder().append("If type Default, default receiver from the Partner Directory: property receiverNotDeterminedDefault=").append(message.properties.receiverNotDeterminedDefault.toString() ?: "n/a").toString();
	logger.addEntry("readReceiverNotDeterminedFromPD", logEntry);
	if (logger.getAuditLog()) {
		message.setHeader('auditLogHeader', logger.getAuditLog())
	}
    
    return message;
}