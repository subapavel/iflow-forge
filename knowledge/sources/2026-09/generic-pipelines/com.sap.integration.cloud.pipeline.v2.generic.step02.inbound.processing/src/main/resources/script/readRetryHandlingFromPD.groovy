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

    // read retry handling from header if already defined as header
    def maxJMSRetries = headers.get("maxJMSRetries");

    // otherwise, read retry handling from the Partner Directory
    if (!maxJMSRetries){
        maxJMSRetries = service.getParameter("MaxJMSRetries", Pid , String.class);
        // if the value exists in the Partner Directory, create a new header holding the max number of retries
        // otherwise, set the header to default which is 5
        message.setHeader("maxJMSRetries", maxJMSRetries ? maxJMSRetries.toInteger() : 5);
    }

    // adding information to audit log using helper PipelineLogger class
    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
    logger.addEntry("readRetryHandlingFromPD", "Partner ID to read the retry handling: " + (Pid ?: "n/a") + ".")
    logger.addEntry("readRetryHandlingFromPD", "Maximum number of retries from the Partner Directory: header maxJMSRetries=" + (message.headers.maxJMSRetries.toString() ?: "n/a") + ".")

    if (logger.getLog()) {
        logger.saveToMessage(message)
    }

    return message;
}
