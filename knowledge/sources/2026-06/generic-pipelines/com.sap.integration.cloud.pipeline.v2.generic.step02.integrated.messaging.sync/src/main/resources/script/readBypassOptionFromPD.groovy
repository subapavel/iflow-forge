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
    String id;
    if (Pid == null){
        throw new IllegalStateException("Partner ID not found in sent message");   
    }

    // read receiverDetermination string parameter from the Partner Directory if any
    def receiverName = service.getParameter("receiverDetermination", Pid , String.class);

    // assign the value to the header SAP_ReceiverAlias
    message.setHeader("SAP_ReceiverAlias", receiverName ?: null);

    // read interfaceDetermination string parameter from the Partner Directory if any
    if (receiverName != null) {
        id = "interfaceDetermination_" + receiverName;
        def outboundEndpoint = service.getParameter(id, Pid , String.class);
        
        // assign the value to the header SAP_OutboundProcessingEndpoint
        message.setHeader("SAP_OutboundProcessingEndpoint", outboundEndpoint ?: null);
        
        // if the value exists, receiver and interface determination should be bypassed otherwise receiver determination only
        message.setProperty("bypassOption", outboundEndpoint ? 'p2p' : 'skipRcvDet');
        
    }

	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("readBypassOptionFromPD", "Partner ID to read the bypass option if any: " + (Pid ?: "n/a") + ".")
	logger.addEntry("readBypassOptionFromPD", "Bypass option: property bypassOption=" + (message.properties.bypassOption.toString() ?: "n/a") + ".")
	logger.addEntry("readBypassOptionFromPDe", "Header SAP_ReceiverAlias=" + (message.headers.SAP_ReceiverAlias.toString() ?: "n/a") + ".")
	logger.addEntry("readBypassOptionFromPDe", "Header SAP_OutboundProcessingEndpoint=" + (message.headers.SAP_OutboundProcessingEndpoint.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Partner ID to read the bypass option if any: ").append(Pid ?: "n/a").toString();
	//logger.addEntry("readBypassOptionFromPD", logEntry);
	//logEntry = new StringBuilder().append("Bypass option: property bypassOption=").append(message.properties.bypassOption.toString() ?: "n/a").toString();
	//logger.addEntry("readBypassOptionFromPD", logEntry);
	//logEntry = new StringBuilder().append("Header SAP_ReceiverAlias=").append(message.headers.SAP_ReceiverAlias.toString() ?: "n/a").toString();
	//logger.addEntry("readBypassOptionFromPD", logEntry);
	//logEntry = new StringBuilder().append("Header SAP_OutboundProcessingEndpoint=").append(message.headers.SAP_OutboundProcessingEndpoint.toString() ?: "n/a").toString();
	//logger.addEntry("readBypassOptionFromPD", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	//}

    return message;
}