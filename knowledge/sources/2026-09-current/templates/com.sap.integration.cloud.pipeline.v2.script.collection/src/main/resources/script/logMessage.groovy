import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger
import src.main.resources.script.PipelineHelper

def Message processData(Message message) {
    def body = message.getBody(String)
    def properties = message.getProperties()
    def headers = message.getHeaders()
    def messageLog = messageLogFactory.getMessageLog(message)

    def mplAttachmentConfiguration = properties.get("mplLoggingConfiguration") ?: "AuditLog" // AuditLog,Payload,Property,Header,Exception
    def mplAttachmentPrefix = properties.get("mplAttachmentName") ?: "Logging"
    def mediaType = properties.get("mediaType") ?: "text/plain"

    List excludedHeaders = ['SAP_MessageProcessingLogID', 'SAP_MonitoringStateProperties', 'MplMarkers', 'SAP_MessageProcessingLog', 'auditLogHeader', 'SAP_AuthHeaderName', 'SAP_AuthHeaderValue']
    List excludedProperties = ['CamelMessageHistory', 'SAP_MessageProcessingLog', 'auditLogProperty', 'SAP_AuthHeaderName', 'SAP_AuthHeaderValue', 'CamelExceptionCaught']

    if (messageLog) {
        // normalize configuration into a lowercase set for easy contains()
		def configSet = mplAttachmentConfiguration
			.toLowerCase()
			.tokenize(',;|')  // tokenize accepts a string of delimiters
			.collect { it.trim() }
			.toSet()
			
			
		// Auto-Promotion Logic: If an exception exists, ensure 'exception' is in the config
        def ex = properties.get("CamelExceptionCaught")
        if (ex != null) configSet.add("exception")

        // --- 1. Payload ---
        if (["payload","body","messagebody"].any { configSet.contains(it) }) {
            if (body?.trim()) { // non empty body
                messageLog.addAttachmentAsString("${mplAttachmentPrefix}MessageBody", body, mediaType)
            }
        }

        // --- 2. Headers ---
        if (configSet.contains("header") || configSet.contains("headers")) {
            def headersSB = new StringBuilder()
            def filteredHeaders = headers.findAll { k, v -> !(k in excludedHeaders) }
            PipelineHelper.dumpKeyValueAsTEXT("Headers", filteredHeaders, headersSB)
            messageLog.addAttachmentAsString("${mplAttachmentPrefix}Headers", headersSB.toString(), mediaType)
        }

        // --- 3. Properties ---
        if (configSet.contains("property") || configSet.contains("properties")) {
            def propsSB = new StringBuilder()
            def filteredProps = properties.findAll { k, v -> !(k in excludedProperties) }
            PipelineHelper.dumpKeyValueAsTEXT("Properties", filteredProps, propsSB)
            messageLog.addAttachmentAsString("${mplAttachmentPrefix}Properties", propsSB.toString(), mediaType)
        }

        // --- 4. AuditLog ---
        if (configSet.contains("auditlog")) {
            PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
            if (logger.getLog()) {
                messageLog.addAttachmentAsString("${mplAttachmentPrefix}AuditLog", logger.getLog4Print(), mediaType)
            }
        }

        // --- 5. Exception ---
        if (configSet.contains("exception") || configSet.contains("error")) {
			if (ex) {
                processExceptionLogging(messageLog, ex, mplAttachmentPrefix)
            }
        }
    }
    return message
}

/**
 * Generically captures exception details and handles specific adapter error responses
 */
def void processExceptionLogging(def messageLog, def ex, String prefix) {
    def exMap = [:]
    StringBuilder sb = new StringBuilder()
    
    try {
        // --- 1. Basic Info (Standard java.lang.Throwable methods) ---
        exMap.put("ExceptionClass", ex.getClass().getCanonicalName() ?: ex.getClass().getName())
        exMap.put("Message", ex.getMessage() ?: "No message provided")

        // Capture StackTrace
        try {
            StringWriter sw = new StringWriter()
            ex.printStackTrace(new PrintWriter(sw))
            exMap.put("StackTrace", sw.toString())
        } catch (Exception e) {
            exMap.put("StackTrace", "Could not extract stacktrace: " + e.getMessage())
        }

        // --- 2. Adapter-Specific Logic (Wrapped in individual try-blocks) ---
        
        // HTTP Adapter Deep Dive (AHC/HTTP4)
        try {
            if (ex.respondsTo("getResponseBody") && ex.getResponseBody() != null) {
                String httpResponseBody = ex.getResponseBody()
                if (httpResponseBody) {
                    messageLog.addAttachmentAsString("${prefix}ErrorResponseBody", httpResponseBody, "text/plain")
                }
                exMap.put("HTTP_StatusCode", ex.respondsTo("getStatusCode") ? ex.getStatusCode() : "N/A")
                exMap.put("HTTP_StatusText", ex.respondsTo("getStatusText") ? ex.getStatusText() : "N/A")
            }
        } catch (Exception e) {
            exMap.put("Debug_HTTP_Resolution", "Failed: " + e.getMessage())
        }

        // SOAP/CXF Deep Dive
        try {
            if (ex.getClass().getCanonicalName()?.contains("org.apache.cxf.interceptor.Fault")) {
                exMap.put("SOAP_FaultCode", ex.respondsTo("getFaultCode") ? ex.getFaultCode() : "N/A")
                if (ex.respondsTo("getDetail") && ex.getDetail()) {
                    exMap.put("SOAP_Detail", ex.getDetail().toString())
                }
            }
        } catch (Exception e) {
            exMap.put("Debug_SOAP_Resolution", "Failed: " + e.getMessage())
        }

        // Nested Causes
        if (ex.getCause()) {
            exMap.put("CausedBy", ex.getCause().toString())
            exMap.put("CauseMessage", ex.getCause().getMessage())
        }

    } catch (Exception fatalEx) {
        // --- 3. Fatal Script Fallback ---
        // If everything else fails, we log the bare minimum directly to a custom header or log
        exMap.put("LOGGING_SCRIPT_ERROR", "The logger script failed to process the exception deep-dive.")
        exMap.put("FATAL_ERROR_MSG", fatalEx.getMessage())
    } finally {
        // Finalize the "ExceptionInfo" attachment regardless of partial failures
        try {
            PipelineHelper.dumpKeyValueAsTEXT("ExceptionDetails", exMap, sb)
            messageLog.addAttachmentAsString("${prefix}ExceptionInfo", sb.toString(), "text/plain")
        } catch (Exception e) {
            // Last resort: add a custom header if even the attachment fails
            messageLog.addCustomHeaderProperty("Logging_Script_Fatal", "Critical failure in processExceptionLogging")
        }
    }
}