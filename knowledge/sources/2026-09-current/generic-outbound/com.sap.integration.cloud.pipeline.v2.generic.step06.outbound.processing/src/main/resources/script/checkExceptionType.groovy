import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger
import com.sap.it.api.ITApiFactory
import com.sap.it.api.mapping.ValueMappingApi
import com.sap.it.api.pd.PartnerDirectoryService

// Used in step06.outbound.processing — evaluates caught exception against retry skip config
// Reads:
//   CustomXSkipRetries         : if false → always retry, unless CustomXSkipRetryProfile header is set
//   CustomXSkipRetriesSource   : Default | ValueMapping | PartnerDirectory | PartnerDirectoryGlobal | PartnerDirectoryScenario
//   CustomXSkipRetriesProfile  : PID for PartnerDirectory / PartnerDirectoryGlobal lookup (property, global)
//   CustomXSkipRetryProfile    : scenario-level override header — PID for PD lookup, takes priority over global false
// Sets:
//   skipRetry = true  if exception matched and skip is warranted
//   skipRetry = false if exception not found in source (no skip)
def Message processData(Message message) {

    def props   = message.getProperties()
    def headers = message.getHeaders()
    def isTrue  = { val -> val?.toString()?.trim()?.equalsIgnoreCase("true") }

    // Scenario-level override: header CustomXSkipRetryProfile overrides global CustomXSkipRetries=false
    def scenarioPid = headers.get("CustomXSkipRetriesProfile")?.toString()?.trim()

    // If globally disabled AND no scenario-level override — always retry, nothing to do
    if (props.get("CustomXSkipRetries")?.toString()?.trim()?.equalsIgnoreCase("false") && !scenarioPid) {
        //message.setProperty("DBG_checkExceptionType", "EarlyExit_SkipRetriesFalse")
        return message
    }

    // Get exception and walk cause chain
    def exception = message.getProperty("CamelExceptionCaught") as Exception
    if (!exception) {
        //message.setProperty("DBG_checkExceptionType", "EarlyExit_NoExceptionFound")
        return message
    }

    def exceptionClasses = []
    def current = exception
    while (current != null) {
        exceptionClasses << current.getClass().getName()
        current = current.getCause()
    }
    //message.setProperty("DBG_checkExceptionType_ExceptionClass", exceptionClasses.join(" -> "))

    // Scenario-level PD lookup — takes priority over global source/profile config
    if (scenarioPid) {
        def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
        def skipRetries = false
        def matchedClass = null
        for (cls in exceptionClasses) {
            def val = service?.getParameter(cls, scenarioPid, String.class)
            if (val) {
                matchedClass = cls
                if (isTrue(val)) { skipRetries = true; break }
            }
        }
        if (skipRetries) {
            message.setProperty("skipRetry", "true")
            //message.setProperty("DBG_checkExceptionType", "Matched_ScenarioProfile_${scenarioPid}_${matchedClass}")
            PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
            logger.addEntry("checkExceptionType", "Exception '${exceptionClasses.join(' -> ')}' matched in scenario profile PID '${scenarioPid}' (class: ${matchedClass}). Skipping retries.")
            if (logger.getLog()) logger.saveToMessage(message)
        } else {
            message.setProperty("skipRetry", "false")
            if (matchedClass) {
                //message.setProperty("DBG_checkExceptionType", "NoSkip_ExplicitlyDisabled_ScenarioProfile_${scenarioPid}_${matchedClass}")
                PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
                logger.addEntry("checkExceptionType", "Exception '${exceptionClasses.join(' -> ')}' found in scenario profile PID '${scenarioPid}' but retry skip is disabled (value=false). Retrying.")
                if (logger.getLog()) logger.saveToMessage(message)
            } else {
                //message.setProperty("DBG_checkExceptionType", "NoSkip_NotFound_ScenarioProfile_${scenarioPid}")
                PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
                logger.addEntry("checkExceptionType", "Exception '${exceptionClasses.join(' -> ')}' not found in scenario profile PID '${scenarioPid}'. Retrying.")
                if (logger.getLog()) logger.saveToMessage(message)
            }
        }
        return message
    }

    def source = props.get("CustomXSkipRetriesSource")?.toString()?.trim() ?: "Default"
    def skipRetries = false
    def matchedClass = null
    def matchedValue = null

    switch (source) {

        case "Default":
            // Hardcoded — only XiMappingException and IllegalInstanceException
            def defaultClasses = [
                "com.sap.xi.mapping.camel.XiMappingException",
                "com.sap.aii.mappingtool.tf7.IllegalInstanceException"
            ] as Set
            matchedClass = exceptionClasses.find { defaultClasses.contains(it) }
            skipRetries  = matchedClass != null
            break

        case "ValueMapping":
            // Check all schemas in VM — iterate schemas until first match
            // true wins over false; not found = no skip
            def vmApi   = ITApiFactory.getApi(ValueMappingApi.class, null)
            if (!vmApi) {
                PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
                logger.addEntry("checkExceptionType", "ValueMappingApi is null — cannot perform VM lookup. Forcing retry.")
                if (logger.getLog()) logger.saveToMessage(message)
                //message.setProperty("DBG_checkExceptionType", "EarlyExit_ValueMappingApi_Null")
                skipRetries = false
                break
            }
            def schemas = ["Connection", "Generic", "Mapping"]
            def resolved = null
            def resolvedClass = null

            outer:
            for (cls in exceptionClasses) {
                for (schema in schemas) {
                    def val = vmApi?.getMappedValue("IFlow", "OutboundProcessing", cls, "Error", schema)
                    if (val != null) {
                        if (isTrue(val)) {
                            resolved      = val
                            resolvedClass = cls
                            break outer   // true wins immediately
                        } else if (resolved == null) {
                            resolved      = val
                            resolvedClass = cls
                            // keep looking for a true
                        }
                    }
                }
            }

            matchedClass = resolvedClass
            matchedValue = resolved
            skipRetries  = isTrue(resolved)
            break

        case "PartnerDirectory":
        case "PartnerDirectoryGlobal":
            // Look up global PD using CustomXSkipRetriesProfile
            def pid     = props.get("CustomXSkipRetriesProfile")?.toString()
            def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
            matchedClass = null
            for (cls in exceptionClasses) {
                def val = service?.getParameter(cls, pid, String.class)
                if (val) {
                    matchedClass = cls
                    matchedValue = val
                    if (isTrue(val)) { skipRetries = true; break }
                }
            }
            break

        case "PartnerDirectoryScenario":
            // Look up scenario PD using partnerID header
            def pid     = headers.get("partnerID")?.toString()
            def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
            matchedClass = null
            for (cls in exceptionClasses) {
                def val = service?.getParameter(cls, pid, String.class)
                if (val) {
                    matchedClass = cls
                    matchedValue = val
                    if (isTrue(val)) { skipRetries = true; break }
                }
            }
            break

        default:
            //message.setProperty("DBG_checkExceptionType", "EarlyExit_UnknownSource_${source}")
            return message
    }

    // Set result
    if (skipRetries) {
        message.setProperty("skipRetry", "true")
        //message.setProperty("DBG_checkExceptionType", "Matched_${source}_${matchedClass}")
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        logger.addEntry("checkExceptionType", "Exception '${exceptionClasses.join(' -> ')}' matched in ${source} (class: ${matchedClass}). Skipping retries.")
        if (logger.getLog()) logger.saveToMessage(message)
    } else {
        message.setProperty("skipRetry", "false")
        if (matchedClass) {
            //message.setProperty("DBG_checkExceptionType", "NoSkip_ExplicitlyDisabled_${source}_${matchedClass}")
            PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
            logger.addEntry("checkExceptionType", "Exception '${exceptionClasses.join(' -> ')}' found in ${source} but retry skip is disabled (value=false). Retrying.")
            if (logger.getLog()) logger.saveToMessage(message)
        } else {
            //message.setProperty("DBG_checkExceptionType", "NoSkip_NotFound_${source}")
            PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
            logger.addEntry("checkExceptionType", "Exception '${exceptionClasses.join(' -> ')}' not found in ${source}. Retrying.")
            if (logger.getLog()) logger.saveToMessage(message)
        }
    }

    return message
}
