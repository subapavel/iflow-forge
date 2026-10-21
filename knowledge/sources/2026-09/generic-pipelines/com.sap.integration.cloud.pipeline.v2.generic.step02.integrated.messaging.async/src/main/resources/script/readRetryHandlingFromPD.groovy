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

    // Read scenario-level retry skip profile from PD
    // SkipRetriesProfile : PID name — if set, exception classes are looked up against that PID in step06 if not set, global step06 config applies
    //def skipRetriesProfile = service.getParameter("SkipRetriesProfile", Pid, String.class)
    //def logInfo = []

    //if (skipRetriesProfile != null) {
    //    message.setHeader("CustomXSkipRetriesProfile", skipRetriesProfile.trim())
    //    logInfo << "SkipRetriesProfile=${skipRetriesProfile.trim()}"
    //} else {
    //    logInfo << "SkipRetriesProfile=not configured (global default applies)"
    //}

    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
    logger.addEntry("readRetryHandlingFromPD", "Partner ID to read the retry handling: " + (Pid ?: "n/a") + ".")
    logger.addEntry("readRetryHandlingFromPD", "Maximum number of retries from the Partner Directory: header maxJMSRetries=" + (message.headers.maxJMSRetries.toString() ?: "n/a") + ".")
    //logger.addEntry("readRetryHandlingFromPD", "Scenario retry profile config: ${logInfo.join(', ')}.")
    if (logger.getLog()) logger.saveToMessage(message)

    return message;
}
