import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.it.api.pd.PartnerDirectoryService;
import com.sap.it.api.ITApiFactory;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {

    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
    if (service == null){
        throw new IllegalStateException("Partner Directory Service not found")
    }

    // get pid
    def headers = message.getHeaders()
    def Pid = headers.get("partnerID")
    if (Pid == null){
        throw new IllegalStateException("Partner ID not found in sent message")
    }

    def properties = message.getProperties()
    //def headers    = message.getHeaders()

    // Step02 scenario-level profile arrives as header — takes priority over global config
    // def skipRetryProfileHeader = headers.get("CustomXSkipRetriesProfile")
    
    // Read scenario-level retry skip profile from PD
    def skipRetryProfileHeader = service.getParameter("SkipRetriesProfile", Pid, String.class)
    def logInfo = []

    if (skipRetryProfileHeader != null) {
        message.setHeader("CustomXSkipRetriesProfile", skipRetryProfileHeader.trim())
        logInfo << "SkipRetriesProfile=${skipRetryProfileHeader.trim()}"
        message.setProperty("CustomXSkipRetryProfile", skipRetryProfileHeader.toString().trim())
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        logger.addEntry("readRetryHandlingFromPDOutbound", "OB stage retry skip config overridden by scenario (step02): CustomXSkipRetryProfile=${skipRetryProfileHeader.toString().trim()}.")
        if (logger.getLog()) logger.saveToMessage(message)
        return message
    }

    // No scenario override — validate global CM values
    def skipRetries       = properties.get("CustomXSkipRetries")?.toString()?.trim()
    def skipRetriesSource = properties.get("CustomXSkipRetriesSource")?.toString()?.trim()

    def validSkipRetries       = ["true", "false"]
    def validSkipRetriesSources = ["Default", "ValueMapping", "PartnerDirectory"] as Set

    //def logInfo = []
    def invalid = false

    if (!validSkipRetries.contains(skipRetries)) {
        logInfo << "CustomXSkipRetries='${skipRetries}' is invalid — must be true or false."
        invalid = true
    }
    if (!validSkipRetriesSources.contains(skipRetriesSource)) {
        logInfo << "CustomXSkipRetriesSource='${skipRetriesSource}' is invalid — must be one of: ${validSkipRetriesSources.join(', ')}."
        invalid = true
    }

    if (invalid) {
        message.setProperty("CustomXSkipRetries", "false")
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        logger.addEntry("readRetryHandlingFromPDOutbound", "Invalid CM configuration — forcing retry. ${logInfo.join(' ')}")
        if (logger.getLog()) logger.saveToMessage(message)
        return message
    }

    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
    logger.addEntry("readRetryHandlingFromPDOutbound", "Retry skip config: CustomXSkipRetries=${skipRetries}, CustomXSkipRetriesSource=${skipRetriesSource} (global).")
    if (logger.getLog()) logger.saveToMessage(message)

    return message
}
