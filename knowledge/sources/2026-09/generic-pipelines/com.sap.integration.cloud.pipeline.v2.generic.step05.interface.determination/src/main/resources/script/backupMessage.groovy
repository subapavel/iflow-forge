import com.sap.gateway.ip.core.customdev.util.Message
import javax.activation.DataHandler
import src.main.resources.script.PipelineLogger

/**
 * Default Entry Point: Routes automatically to backupMessage.
 */
def Message processData(Message message) {
    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
    return backupMessage(message, logger)
}

/**
 * Method 1: Backup of Original Message.
 * Full Backup includes original message body, headers and attachments.
 * Size measurement is performed only when originalPayloadSizeMeasurementEnabled=true.
 */
def Message backupMessage(Message message, PipelineLogger logger = null) {
    if (logger == null) {
        logger = PipelineLogger.newLogger(message, "auditLog")
    }

    def headers    = message.getHeaders()
    def properties = message.getProperties()
    def location   = properties.get("location") ?: "Backup Message"

    String measuredBody = null
    if (properties.get("originalPayloadSizeMeasurementEnabled") == "true") {
        measuredBody = measureBodySizeAndGet(message, headers, logger, location, "originalPayloadSizeBytes")
    }

    executePayloadBackup(message, "bckOriginalPayload", measuredBody)
    executeHeadersBackup(message)
    executeAttachmentsBackup(message, logger)

    if (logger.getLog()) { logger.saveToMessage(message) }
    return message
}

/**
 * Method 2: Original Payload Backup.
 * Backups the message body only into 'bckOriginalPayload'.
 * Size measurement is performed only when originalPayloadSizeMeasurementEnabled=true.
 */
def Message backupOriginalPayload(Message message) {
    def headers    = message.getHeaders()
    def properties = message.getProperties()
    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
    def location   = properties.get("location") ?: "Backup Original Payload"

    if (properties.get("originalPayloadSizeMeasurementEnabled") == "true") {
        String measuredBody = measureBodySizeAndGet(message, headers, logger, location, "originalPayloadSizeBytes")
        executePayloadBackup(message, "bckOriginalPayload", measuredBody)
    } else {
        executePayloadBackup(message, "bckOriginalPayload")
    }

    if (logger.getLog()) { logger.saveToMessage(message) }
    return message
}

/**
 * Method 3: Converted Payload Backup.
 * Backups the message body into 'bckConvertedPayload' if 'bckOriginalPayload' does not exist or differs from converted payload.
 * Body is read exactly once — size is derived from the same read.
 * Size measurement is performed only when convertedPayloadSizeMeasurementEnabled=true.
 */
def Message backupConvertedPayload(Message message) {
    def properties = message.getProperties()
    def headers    = message.getHeaders()
    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
    def location   = properties.get("location") ?: "Converted Payload Backup"
    def isXml      = headers.get("isWellFormedXml") == "true"

    // Read body once — derive both size and the value to store
    String currentBody
    long bodySize = 0
    if (isXml) {
        currentBody = message.getBody(java.lang.String)
        if (currentBody != null) bodySize = currentBody.getBytes("UTF-8").length
    } else {
        byte[] bodyBytes = message.getBody(byte[].class)
        if (bodyBytes != null) {
            bodySize    = bodyBytes.length
            currentBody = bodyBytes.encodeBase64().toString()
            bodyBytes   = null
        }
    }

    if (properties.get("convertedPayloadSizeMeasurementEnabled") == "true" && bodySize > 0) {
        message.setHeader("convertedPayloadSizeBytes", bodySize)
        logger.addEntry(location, "Message Payload size measured for Custom Header [convertedPayloadSizeBytes]: ${bodySize} Bytes.")
        messageLogFactory.getMessageLog(message)?.addCustomHeaderProperty("convertedPayloadSizeBytes", String.valueOf(bodySize))
    }

    def originalBck = properties.get("bckOriginalPayload")

    // Base Case: If no original backup exists yet, backup converted payload directly
    if (!properties.containsKey("bckOriginalPayload")) {
        if (currentBody != null) {
            message.setProperty("bckConvertedPayload", currentBody)
            message.setProperty("bckConvertedPayloadIsXml", String.valueOf(isXml))
        } else {
            logger.addEntry(location, "Message body is null. Converted payload backup skipped.")
        }
        if (logger.getLog()) { logger.saveToMessage(message) }
        return message
    }

    // Dynamic Comparison Evaluation
    if (currentBody != null) {
        if (originalBck != currentBody) {
            // Payloads differ! Create new dedicated backup property
            originalBck = null
            message.setProperty("bckConvertedPayload", currentBody)
            message.setProperty("bckConvertedPayloadIsXml", String.valueOf(isXml))
            logger.addEntry(location, "Converted payload differs from original payload. Saved to 'bckConvertedPayload'.")
        } else {
            message.setProperty("isOriginalConvertedBodyMatch", "true")
            logger.addEntry(location, "Converted payload matches original backup perfectly. Skipping property backup creation.")
        }
    } else {
        logger.addEntry(location, "Message body is null. Converted payload backup skipped.")
    }
    originalBck = null
    currentBody = null

    if (logger.getLog()) { logger.saveToMessage(message) }
    return message
}


// =========================================================================
// Private Reusable Helper Methods
// =========================================================================

/**
 * Reads the message body once, measures its size, sets the custom header and MPL property,
 * and returns the body value ready for use as a backup property (XML String or base64 String).
 * If the size header is already set, the body read is skipped and null is returned
 * (caller should then let executePayloadBackup perform its own read).
 */
private String measureBodySizeAndGet(Message message, Map headers, PipelineLogger logger, String location, String customHeaderName) {
    def rawSizeHeader = headers.get(customHeaderName)
    long bodySizeBytes = 0
    try {
        bodySizeBytes = rawSizeHeader ? rawSizeHeader.toLong() : 0
    } catch (Exception ignored) {
        bodySizeBytes = 0
    }

    if (bodySizeBytes > 0) {
        // Size already known — register MPL custom header only; body read not needed
        messageLogFactory.getMessageLog(message)?.addCustomHeaderProperty(customHeaderName, String.valueOf(bodySizeBytes))
        return null
    }

    // Read body once — derive both size and the value to store
    boolean isXml = headers.get("isWellFormedXml") == "true"
    String bodyValue
    if (isXml) {
        String bodyStr = message.getBody(java.lang.String)
        if (bodyStr != null) {
            bodySizeBytes = bodyStr.getBytes("UTF-8").length
            bodyValue     = bodyStr
        }
    } else {
        byte[] bodyBytes = message.getBody(byte[].class)
        if (bodyBytes != null) {
            bodySizeBytes = bodyBytes.length
            bodyValue     = bodyBytes.encodeBase64().toString()
            bodyBytes     = null
        }
    }

    if (bodySizeBytes > 0) {
        message.setHeader(customHeaderName, bodySizeBytes)
        logger.addEntry(location, "Message Payload size measured for Custom Header [${customHeaderName}]: ${bodySizeBytes} Bytes.")
        messageLogFactory.getMessageLog(message)?.addCustomHeaderProperty(customHeaderName, String.valueOf(bodySizeBytes))
    }
    return bodyValue
}

private void executePayloadBackup(Message message, String targetPropertyName, String preReadBody = null) {
    def headers = message.getHeaders()
    def isXml   = headers.get("isWellFormedXml") == "true"
    String body
    if (preReadBody != null) {
        body = preReadBody
    } else {
        body = isXml ? message.getBody(java.lang.String) : message.getBody(byte[].class)?.encodeBase64()?.toString()
    }
    message.setProperty(targetPropertyName, body ?: "")
    message.setProperty(targetPropertyName + "IsXml", String.valueOf(isXml))
}

private void executeHeadersBackup(Message message) {
    def headers    = message.getHeaders()
    def properties = message.getProperties()
    String excludePattern = properties.get("excludedHeadersFromBackupList") ?: "SAP_MessageProcessingLogID|SAP_MonitoringStateProperties|MplMarkers|SAP_MessageProcessingLog|SAP_MplCorrelationId|SAP_PregeneratedMplId|auditLogHeader|SAP_AuthHeaderName|SAP_AuthHeaderValue|functionName|sapFunctionName|scriptFile|scriptFileType|JMS_Solace(.*)"

    Map bckHeadersMap = headers.findAll { k, v ->
        !(k.matches(excludePattern))
    }
    message.setProperty("bckHeadersMap", bckHeadersMap)
}

private void executeAttachmentsBackup(Message message, PipelineLogger logger) {
    def properties = message.getProperties()
    def location   = properties.get("location") ?: "Backup Message"

    Map<String, DataHandler> attachments = message.getAttachments()
    Map bckMetadata = [:]

    attachments.each { key, handler ->
        try {
            String actualName = handler?.getName()?.trim() ?: key
            String propName   = "bckAtt_" + key.replaceAll("[^a-zA-Z0-9]", "_")
            message.setProperty(propName, handler.getInputStream().bytes.encodeBase64().toString())
            bckMetadata.put(key, [
                attachmentName   : actualName,
                contentType      : handler.getContentType(),
                propertyReference: propName
            ])
        } catch (Exception e) {
            logger.addEntry(location, "Failed to backup attachment '${key}': ${e.getMessage()}. Skipped.")
        }
    }
    if (bckMetadata) {
        message.setProperty("bckAttachmentMetadata", bckMetadata)
        List attachmentNames = bckMetadata.values().collect { it.attachmentName }
        logger.addEntry(location, "Message contains ${attachmentNames.size()} attachments: ${attachmentNames.join(', ')}. Attachments backup done.")
    }
}
