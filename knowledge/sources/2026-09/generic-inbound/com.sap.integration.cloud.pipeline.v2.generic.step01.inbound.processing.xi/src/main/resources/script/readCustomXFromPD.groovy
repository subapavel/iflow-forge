import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.it.api.pd.PartnerDirectoryService;
import com.sap.it.api.ITApiFactory;
import org.apache.camel.Exchange;
import org.apache.camel.builder.SimpleBuilder;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {
    
    def custXBoolean = "false";
    
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
    
    def pipStep = headers.get("customXPipelineStep");
    if (pipStep == null){
        Exchange ex = message.exchange;
		String flowID = SimpleBuilder.simple('${camelId}').evaluate(ex, String);
        int index = flowID.indexOf("generic.step") + 12;
        pipStep = flowID.substring(index, index + 2);
        message.setHeader("customXPipelineStep", pipStep);
    }
    
    // get custom exit type CustomXType
    def properties = message.getProperties();
    String paramPrefix = properties.get("CustomXType");
    
    // read the CustomX Enabled setting from the header if applicable
    def custXEnabled = headers.get(paramPrefix + "Enabled")
    // otherwise, read the CustomX Enabled string parameter from the Partner Directory
    if (custXEnabled == null){
        custXEnabled = service.getParameter(paramPrefix + "Enabled", Pid , String.class);
    }
    
    // read the CustomX Endpoint setting from the header if applicable
    def custXEndpoint = headers.get(paramPrefix + "Endpoint")
    // otherwise, read the CustomX Endpoint string parameter from the Partner Directory
    if (custXEndpoint == null){
        custXEndpoint = service.getParameter(paramPrefix + "Endpoint", Pid , String.class);
    }

    // proceed if both parameters exist, otherwise do nothing
    if (custXEnabled && custXEndpoint) {
        custXEnabled.split(/\|/).each { part ->
            if (part == pipStep) {
                custXBoolean = "true";
            }
        }
        message.setProperty("CustomXBoolean", custXBoolean)
        message.setProperty("CustomXEndpoint", custXEndpoint)

    }
    
    // adding information to audit log using helper PipelineLogger class
    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	logger.addEntry("readCustomXFromPD", "Partner ID to read the custom exit settings: " + (Pid ?: "n/a") + ".")
	logger.addEntry("readCustomXFromPD", "Custom exit type: property CustomXType=" + (message.properties.CustomXType.toString() ?: "n/a") + ".")
	logger.addEntry("readCustomXFromPD", "Custom exit end point from the Partner Directory: property CustomXEndpoint=" + (message.properties.CustomXEndpoint.toString() ?: "n/a") + ".")
	logger.addEntry("readCustomXFromPD", "Custom exit enabled: property CustomXBoolean=" + (message.properties.CustomXBoolean.toString() ?: "n/a") + ".")
	if (logger.getLog()) { 
	    logger.saveToMessage(message)
	}
	
	//PipelineLogger logger = PipelineLogger.newLogger(message)
	//def logEntry = new StringBuilder().append("Partner ID to read the custom exit settings: ").append(Pid ?: "n/a").toString();
	//logger.addEntry("readCustomXFromPD", logEntry);
	//logEntry = new StringBuilder().append("Custom exit type: property CustomXType=").append(message.properties.CustomXType.toString() ?: "n/a").toString();
	//logger.addEntry("readCustomXFromPD", logEntry);
	//logEntry = new StringBuilder().append("Custom exit end point from the Partner Directory: property CustomXEndpoint=").append(message.properties.CustomXEndpoint.toString() ?: "n/a").toString();
	//logger.addEntry("readCustomXFromPD", logEntry);
	//logEntry = new StringBuilder().append("Custom exit enabled: property CustomXBoolean=").append(message.properties.CustomXBoolean.toString() ?: "n/a").toString();
	//logger.addEntry("readCustomXFromPD", logEntry);
	//if (logger.getAuditLog()) {
	//	message.setHeader('auditLogHeader', logger.getAuditLog())
	//}
    
    return message;
}