import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.gateway.ip.core.customdev.util.AttachmentWrapper
import javax.mail.util.ByteArrayDataSource
import src.main.resources.script.PipelineLogger

/**
 * Entry Point 1: Full Message Context Restoration.
 * Restores body, headers and attachments from backed-up exchange properties.
 */
def Message restoreMessage(Message message) {
    executeOriginalPayloadRestoration(message)
    executeHeadersRestoration(message)
    executeAttachmentsRestoration(message)
    return message
}

/**
 * Entry Point 2: Isolated Original Payload Restoration.
 * Restores body only from 'bckOriginalPayload' property.
 */
def Message restoreOriginalPayload(Message message) {
    executeOriginalPayloadRestoration(message)
    return message
}

/**
 * Entry Point 3: Isolated Converted Payload Restoration.
 * Checks 'isOriginalConvertedBodyMatch': if true, restores from 'bckOriginalPayload'.
 * Otherwise restores from 'bckConvertedPayload'.
 */
def Message restoreConvertedPayload(Message message) {
    executeConvertedPayloadRestoration(message)
    return message
}

/**
 * Default Entry Point: Defaults to full message restoration.
 */
def Message processData(Message message) {
    return restoreMessage(message)
}

// =========================================================================
// Private: Property-based Restoration
// =========================================================================

private void executeOriginalPayloadRestoration(Message message) {
    def properties     = message.getProperties()
    def currentHeaders = message.getHeaders()

    def savedPayload = properties.get("bckOriginalPayload")
    if (savedPayload != null) {
        def isXml = properties.containsKey("bckOriginalPayloadIsXml")
                    ? properties.get("bckOriginalPayloadIsXml") == "true"
                    : currentHeaders.get("isWellFormedXml") == "true"
        message.setBody(isXml ? savedPayload : savedPayload.decodeBase64())
    }
}

private void executeConvertedPayloadRestoration(Message message) {
    def properties     = message.getProperties()
    def currentHeaders = message.getHeaders()
    def isMatch        = properties.get("isOriginalConvertedBodyMatch") == "true"

    def targetProperty = isMatch ? "bckOriginalPayload" : "bckConvertedPayload"
    def isXmlPropName  = isMatch ? "bckOriginalPayloadIsXml" : "bckConvertedPayloadIsXml"
    def savedPayload   = properties.get(targetProperty)
    if (savedPayload != null) {
        def isXml = properties.containsKey(isXmlPropName)
                    ? properties.get(isXmlPropName) == "true"
                    : currentHeaders.get("isWellFormedXml") == "true"
        message.setBody(isXml ? savedPayload : savedPayload.decodeBase64())
    }
}

private void executeHeadersRestoration(Message message) {
    def properties     = message.getProperties()
    def currentHeaders = message.getHeaders()

    Map bckHeadersMap = properties.get("bckHeadersMap")
    if (bckHeadersMap != null) {
        String basePattern    = properties.get("excludedHeadersFromBackupList") ?: "SAP_MessageProcessingLogID|SAP_MonitoringStateProperties|MplMarkers|SAP_MessageProcessingLog|SAP_MplCorrelationId|SAP_PregeneratedMplId|auditLogHeader|SAP_AuthHeaderName|SAP_AuthHeaderValue|functionName|sapFunctionName|scriptFile|scriptFileType|JMS_Solace(.*)"
        String excludePattern = (properties.get("CustomXError_Enabled") == "true")
            ? basePattern + "|SAP_Sender(.*)|SAP_MessageType|SAP_Receiver(.*)|maxJMSRetries|SAPJMSRetries|incomingQueue|pipelineStepID|partnerID|exception(.*)|testMode"
            : basePattern
        def compiled = ~excludePattern

        currentHeaders.keySet().each { hName ->
            if (!hName.matches(compiled) && !bckHeadersMap.containsKey(hName)) {
                message.setHeader(hName, "")
            }
        }
        bckHeadersMap.each { k, v -> message.setHeader(k, v) }
    }
}

private void executeAttachmentsRestoration(Message message) {
    def properties  = message.getProperties()
    def location    = properties.get("location") ?: "Restore Message"
    Map bckMetadata = properties.get("bckAttachmentMetadata")

    if (bckMetadata != null) {
        Map attachments        = message.getAttachments()
        Map attachmentWrappers = message.getAttachmentWrapperObjects()
        Map attachmentObjects  = message.getAttachmentObjects()
        attachments.clear();        message.setAttachments(attachments)
        attachmentWrappers.clear(); message.setAttachmentWrapperObjects(attachmentWrappers)
        attachmentObjects.clear();  message.setAttachmentObjects(attachmentObjects)

        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        bckMetadata.each { key, meta ->
            try {
                String base64 = properties.get(meta.propertyReference)
                if (base64) {
                    String finalName = meta.attachmentName ?: key
                    message.addAttachmentObject(finalName, new AttachmentWrapper(new ByteArrayDataSource(base64.decodeBase64(), meta.contentType)))
                }
            } catch (Exception e) {
                logger.addEntry(location, "Failed to restore attachment '${key}': ${e.getMessage()}. Skipped.")
            }
        }
        if (logger.getLog()) { logger.saveToMessage(message) }
    }
}
