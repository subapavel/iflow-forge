import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.it.api.pd.PartnerDirectoryService;
import com.sap.it.api.ITApiFactory;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {
    
    // get headers
    def headers = message.getHeaders();
    String CustomHeaders = headers.get("customHeaderProperties");
    
    // Fetch receiver alias
    String ReceiverAlias = headers.get("SAP_ReceiverAlias");
    String BusinessService = ReceiverAlias;
    String Party;
    
    // check if receiver alias contains party
	def kv = ReceiverAlias.split(/\./, 2)
	if (kv?.size() == 2) {
	    Party = kv[0]?.trim()
	    BusinessService = kv[1]?.trim()
	    message.setHeader("SAP_ReceiverParty", Party);
	    message.setHeader("SAP_ReceiverAlias", BusinessService);
	}

    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null); 
        if (service == null){
            throw new IllegalStateException("Partner Directory Service not found");
    }

    // Map receiver alias to business name if maintained otherwise keep alias    
    String Agency = headers.get("tenantStage") ?: "PRD";
    String Scheme = 'BusinessSystemName';
    String Pid = BusinessService;

    String Receiver = service.getAlternativePartnerId(Agency, Scheme, Pid) ?: Pid;
    
    // enhance custom header properties if receiver alias differs from actual receiver
    if (Receiver != ReceiverAlias) {
        CustomHeaders = CustomHeaders ? CustomHeaders + '|' : '';
        CustomHeaders = CustomHeaders + 'ReceiverAlias' + ':' + BusinessService;
    //    message.setProperty("customHeader_ReceiverAlias", BusinessService);
    }

    // add party if existing
    if (Party != null && Party != ''){
        CustomHeaders = CustomHeaders ? CustomHeaders + '|' : '';
        CustomHeaders = CustomHeaders + 'ReceiverParty' + ':' + Party;
        CustomHeaders = CustomHeaders + '|' + 'ReceiverSystem' + ':' + Receiver;
    //    message.setProperty("customHeader_ReceiverParty", Party);
	//    message.setProperty("customHeader_ReceiverSystem", Receiver);
        Receiver = Party + '.' + Receiver;
    }
    
    message.setHeader("SAP_Receiver", Receiver);
    message.setHeader("customHeaderProperties", CustomHeaders);
    
	// adding information to audit log using helper PipelineLogger class
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("mapReceiverToBusinessName", "Tenant Stage=" + (Agency ?: "n/a") + ".")
	logger.addEntry("mapReceiverToBusinessName", "Receiver Alias=" + (Pid ?: "n/a") + ".")
	logger.addEntry("mapReceiverToBusinessName", "Business System Name=" + (headers.get("SAP_Receiver") ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
    //PipelineLogger logger = PipelineLogger.newLogger(message)
    //def logEntry = new StringBuilder().append("Tenant Stage=").append(Agency ?: "n/a").toString();
	//logger.addEntry("mapReceiverToBusinessName", logEntry);
    //logEntry = new StringBuilder().append("Receiver Alias=").append(Pid ?: "n/a").toString();
	//logger.addEntry("mapReceiverToBusinessName", logEntry);
    //logEntry = new StringBuilder().append("Business System Name=").append(headers.get("SAP_Receiver") ?: "n/a").toString();
	//logger.addEntry("mapReceiverToBusinessName", logEntry);
    //if (logger.getAuditLog()) {
	//    message.setHeader('auditLogHeader', logger.getAuditLog())
    //}

    return message;
}