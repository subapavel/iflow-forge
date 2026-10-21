import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.pd.PartnerDirectoryService
import src.main.resources.script.PipelineLogger

def Message processData(Message message) {
    PartnerDirectoryService service = getPartnerDirectoryService()
    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")

    try {
		// 1. Read the state from the incoming message
		Map ctx = readRoutingContext(message, service, logger)
		logger.addEntry("mapSenderStageToReceiverStage", "Routing & Tenant context: " + ctx.toString())
		
		// 2. Identify what the Receiver Alias is capable of (which stages it exists in)
		Map receiverCaps = resolveReceiverCapabilities(service, ctx, logger)
		logger.addEntry("mapSenderStageToReceiverStage", "Receiver context: " + receiverCaps.toString())

		// 3. Match the Sender's context against the Receiver's capabilities
		Map routingResult = resolveReceiverStage(service, ctx, receiverCaps, logger)

		// 4. Update the message (Set Tenant Stage for Receiver or Multi-Receiver XML Body)
		applyRoutingResult(message, routingResult, logger, ctx)
    } catch (Exception e) {       
        // Save Log and Rethrow
        if (logger.getLog()) logger.saveToMessage(message)
        throw e
    }

	if (logger.getLog()) logger.saveToMessage(message) // If success, finalize log
    return message
}

// --- Context Reading ---
Map readRoutingContext(Message message, PartnerDirectoryService service, PipelineLogger logger) {
    def headers = message.getHeaders()
    String tenantName = System.getenv("TENANT_NAME")
    
    // Read Global Configuration
    String vStagesRaw = service.getParameter(tenantName, "SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
    def listVirtual = vStagesRaw.split(",")*.trim()
    String tStage = headers.get("tenantStage")
	def tStageList = tStage.split(",")*.trim() ?: []
    boolean isKnown = true

    if (!tStage || tStageList.size() > 1) {
        isKnown = false
        tStage = "" 
		message.setHeader("tenantStage", "") // Reset header to empty value
        logger.addEntry("mapSenderStageToReceiverStage", "Detected ambiguous tenant stage. isKnownStage set to false.")
    }

	if(tStage && !(tStage in listVirtual)) {
		vStagesRaw = "${vStagesRaw},${tStage}" // Promotion to Virtual Context
	}

    return [
        stage         : tStage,
        tenantName    : tenantName,
        virtualStages : vStagesRaw,
        receiverAlias : determineParty(headers.get("SAP_ReceiverAlias")).system,
        partnerId     : headers.get("partnerID"),
        isKnownStage  : isKnown,
		qos			  : headers.get("SapQualityOfService")
    ]
}

// --- Capability Discovery ---
Map resolveReceiverCapabilities(PartnerDirectoryService service, Map ctx, PipelineLogger logger) {
    if (!ctx.receiverAlias) {
		throw new IllegalStateException("SAP_ReceiverAlias header does not exist after Receiver Determination.")
    }

    // Identify all stages where this Receiver exists
    List<String> allTenantStages = ctx.virtualStages?.split(",")*.trim() ?: []
    List<String> possibleStages = []

    allTenantStages.each { s ->
        // Check exact stage (e.g., QA~MNT)
        if (service.getAlternativePartnerId(s, "BusinessSystemName", ctx.receiverAlias)) {
            possibleStages << s
        }
    }
    
    possibleStages = possibleStages.unique()
	boolean hasPdAlias = !possibleStages.isEmpty()

    if (!hasPdAlias) {
        logger.addEntry("resolveReceiverCapabilities", "Receiver Alias '${ctx.receiverAlias}' not found in PD for any configured landscape stages: '${ctx.virtualStages}'. Treating as Business Component/External.")
    }

    return [
        hasPdAlias     : hasPdAlias,
        possibleStages : possibleStages
    ]
}

// --- Core Routing Logic ---
Map resolveReceiverStage(PartnerDirectoryService service, Map ctx, Map caps, PipelineLogger logger) {
    if (!caps.hasPdAlias) return noReceiverStage() // if no Alias in Partner Directory, then target stage determination is not required
	
	if (ctx.isKnownStage) { // 2 decision-making branches, which depend on whether Tenant Stage has been determined
        return resolveKnownTenantStage(service, ctx, caps, logger)
    } else {
        return resolveUnknownTenantStage(service, ctx, caps, logger)
    }
}

Map resolveUnknownTenantStage(PartnerDirectoryService service, Map ctx, Map caps, PipelineLogger logger) {

    // 1.A Tenant Rule at Partner ID (Scenario ID) - Receiver-specific 
    String rule = service.getParameter("stageRule_${ctx.tenantName}~${ctx.receiverAlias}", ctx.partnerId, String.class)
    if (rule) {
		logger.addEntry("resolveUnknownTenantStage", "For tenant '${ctx.tenantName}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined for any Stage in Tenant for Receiver '${ctx.receiverAlias}'.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}

    // 1.B Tenant Rule at Partner ID (Scenario ID) - All Receivers
    rule = service.getParameter("stageRule_${ctx.tenantName}", ctx.partnerId, String.class)
    if (rule) {
		logger.addEntry("resolveUnknownTenantStage", "For tenant '${ctx.tenantName}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined for any Stage in Tenant for any Receiver.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}
	
    // 2. Tenant Rule at Receiver Alias
    rule = service.getParameter("stageRule_${ctx.tenantName}", ctx.receiverAlias, String.class)
    if (rule) {
		logger.addEntry("resolveUnknownTenantStage", "For tenant '${ctx.tenantName}' routing rule '${rule}' found at Receiver Alias '${ctx.receiverAlias}'.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}

    // 3. Only one possible stage
    if (caps.possibleStages.size() == 1) {
		logger.addEntry("resolveUnknownTenantStage", "Receiver Alias '${ctx.receiverAlias}' has only 1 possible stage '${caps.possibleStages[0]}'.")
		return singleReceiverStage(caps.possibleStages[0])
	}

    // 4. Routing rule is required - throw stage routing exception
	throw new IllegalStateException("Virtual Stage Routing Error: Tenant Stage unknown (possible: '${ctx.virtualStages}') and multiple target Receiver Stages (possible: '${caps.possibleStages}') found for Receiver Alias '${ctx.receiverAlias}'. Routing rule is required.")
}

Map resolveKnownTenantStage(PartnerDirectoryService service, Map ctx, Map caps, PipelineLogger logger) {

    // 1. Stage Rule at Partner ID (Scenario ID) - Receiver-specific or All Receivers 
    String rule = service.getParameter("stageRule_${ctx.stage}~${ctx.receiverAlias}", ctx.partnerId, String.class) // Receiver-specific at Specific Stage
	if(rule) {
		logger.addEntry("resolveKnownTenantStage", "Stage-Specific Rule: For tenant '${ctx.tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined as stage-specific for Receiver '${ctx.receiverAlias}'.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}	
	rule = service.getParameter("stageRule_${ctx.stage}", ctx.partnerId, String.class) // Any Receiver at Specific Stage
    if (rule) {
		logger.addEntry("resolveKnownTenantStage", "Stage-Specific Rule: For tenant '${ctx.tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined as stage-specific for any Receiver.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}
	
	// 2. Stage Rule at Receiver Alias
    rule = service.getParameter("stageRule_${ctx.stage}", ctx.receiverAlias, String.class) 
    if (rule) {
		logger.addEntry("resolveKnownTenantStage", "Stage-Specific Rule: For tenant '${ctx.tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Receiver Alias '${ctx.receiverAlias}' defined as stage-specific.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}

    // 3. Stage Exact Match
    if (ctx.stage in caps.possibleStages) {
		logger.addEntry("resolveKnownTenantStage", "Receiver Alias '${ctx.receiverAlias}' has matching Tenant Stage '${ctx.stage}'.")
		return singleReceiverStage(ctx.stage)
	}	

    // 4 Tenant Rule (fallback if no Stage specific value) at Partner ID (Scenario ID) - Receiver-specific or All Receivers
    rule = service.getParameter("stageRule_${ctx.tenantName}~${ctx.receiverAlias}", ctx.partnerId, String.class) // Receiver-specific in Tenant at any Stage
    if (rule) {
		logger.addEntry("resolveKnownTenantStage", "Tenant-Fallback Rule: For tenant '${ctx.tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined for any Stage in Tenant for Receiver '${ctx.receiverAlias}'.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}	
    rule = service.getParameter("stageRule_${ctx.tenantName}", ctx.partnerId, String.class) // Any Receivers in Tenant at any Stage
    if (rule) {
		logger.addEntry("resolveKnownTenantStage", "Tenant-Fallback Rule: For tenant '${ctx.tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined for any Stage in Tenant for any Receiver.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}

    // 5. Tenant Rule (fallback if no Stage specific value) at Receiver Alias
    rule = service.getParameter("stageRule_${ctx.tenantName}", ctx.receiverAlias, String.class)
    if (rule) {
		logger.addEntry("resolveKnownTenantStage", "Tenant-Fallback Rule: For tenant '${ctx.tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Receiver Alias '${ctx.receiverAlias}' defined for any Stage in Tenant.")
		return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
	}

	// 6. Routing rule is required - throw stage routing exception
	throw new IllegalStateException("Virtual Stage Routing Error: Tenant stage '${ctx.stage}' is known but no matching stage found at Receiver Alias '${ctx.receiverAlias}' that has possible target stages '${caps.possibleStages}'. Routing rule is required.")
}

// --- Helpers ---
Map noReceiverStage() { 
	[ noReceiverStageBoolean: true ] 
}

Map singleReceiverStage(String stage) {
	[ receiverStages: [ stage ] ]
}

Map parseRouteStage(PartnerDirectoryService service, String value, List<String> possibleStages, String receiverAlias, PipelineLogger logger) {
    // 1. Expected format: ">Stage1,Stage2"
    String cleanValue = value.startsWith(">") ? value.substring(1) : value
    List<String> requested = cleanValue.split(",")*.trim().findAll { it }
    
    List<String> valid = []
    List<String> checkInPD = []
    
    // 2. Separate stages: what we know vs. what we need to verify
    requested.each { r ->
        if (possibleStages.contains(r)) {
            valid << r
        } else {
            checkInPD << r
        }
    }
    
    // 3. Dynamic Discovery: Check the "Unknown" stages directly in the PD for this Alias
    checkInPD.each { s ->
        // We bypass the cached 'possibleStages' and ask the PD directly
        if (service.getAlternativePartnerId(s, "BusinessSystemName", receiverAlias)) {
            valid << s
            logger.addEntry("mapSenderStageToReceiverStage", "parseRouteStage: Stage '${s}' was not in global virtual landscape but is explicitly mapped to Alias '${receiverAlias}'. Including in route.")
        } else {
            logger.addEntry("mapSenderStageToReceiverStage", "parseRouteStage WARNING: Stage '${s}' in Rule is NOT mapped to Alias '${receiverAlias}' in PD. Skipping.")
        }
    }

    // 4. Final Validation
    valid = valid.unique()
    if (!valid) {
        throw new IllegalStateException("Virtual Stage Routing Error: No valid receiver stages remain. Rule '${value}' provided targets that do not exist for Alias '${receiverAlias}' in the Partner Directory.")
    }

    return [ receiverStages: valid ]
}

void applyRoutingResult(Message message, Map result, PipelineLogger logger, Map ctx) {
    if (result.noReceiverStageBoolean) {
        if (!ctx.isKnownStage) {
			message.setHeader("tenantStage", "PRD") // set Tenant Stage to default value if tenant stage unknown
			ctx.stage = "PRD"
		}
		message.setProperty("noReceiverStageBoolean", "true") // property for routing in IFlow
		message.setProperty("customHeader_tenantStage", ctx.stage)
        logger.addEntry("mapSenderStageToReceiverStage", "applyRoutingResult: No Receiver Alias in Partner Directory  -> noReceiverStageBoolean=true")
        return
    }

    if (result.receiverStages?.size() == 1) {
        message.setHeader("tenantStage", result.receiverStages[0])
		message.setProperty("singleReceiverStageBoolean", "true") // property for routing in IFlow
		message.setProperty("customHeader_tenantStage", result.receiverStages[0])
        logger.addEntry("mapSenderStageToReceiverStage", "applyRoutingResult: Single Stage '${result.receiverStages[0]}' determined and assigned to header 'tenantStage'.")
        return
    }

    // Multiple stages found: Build Multi-Receiver XML
	if(ctx.qos == "BestEffort") {
		throw new IllegalStateException("Virtual Stage Routing Error: Multiple target stages '${result.receiverStages}' were determined for a synchronous interface with QoS 'Best Effort'. Message splitting is not permitted for synchronous interfaces.")
	}
    logger.addEntry("mapSenderStageToReceiverStage", "applyRoutingResult: Multiple stages '${result.receiverStages}' determined -> Building Receiver Stages XML.")

    // Check if payload backup exists/required before changing message body
    def properties = message.getProperties()
    boolean bypassBackup = (properties.get("receiversXmlPayload") != null) || 
                           (properties.get("isOriginalConvertedBodyMatch") == "true") || 
                           (properties.get("bckConvertedPayload") != null)

    if (bypassBackup) {
        logger.addEntry("mapSenderStageToReceiverStage", "applyRoutingResult: Converted payload backup skipped. Reason: Target property exists, or current body perfectly matches original baseline.")
    } else {
        backupConvertedPayload(message, logger)
    }

    message.setBody(buildReceiverXml(result.receiverStages))
}

String buildReceiverXml(List<String> stages) {
    StringBuilder xml = new StringBuilder()
    xml.append('<?xml version="1.0" encoding="UTF-8"?>\n')
    xml.append('<ns0:Receivers xmlns:ns0="http://sap.com/xi/XI/System">\n')
    stages.each { s ->
        xml.append("  <Receiver>\n    <Service>${s}</Service>\n  </Receiver>\n")
    }
    xml.append('</ns0:Receivers>')
    return xml.toString()
}

// Partner Directory
PartnerDirectoryService getPartnerDirectoryService() {
    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
    if (!service) {
        throw new IllegalStateException("Partner Directory Service not available")
    }
    return service
}

// Check and Extract Party
Map determineParty(String systemHeader) {
    String party = null
    String system = systemHeader
    
    if (!systemHeader) {
        return [system: null, party: null]
    }

    if (systemHeader.contains(".")) {
        def kv = systemHeader.split(/\./, 2)
        party = kv[0]?.trim()
        system = kv[1]?.trim()
    }
    return [system: system, party: party]
}

// Reusable Helper to back up payload before overwriting with XML
private void backupConvertedPayload(Message message, PipelineLogger logger) {
    def properties = message.getProperties()
    def headers    = message.getHeaders()
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
        logger.addEntry("mapSenderStageToReceiverStage", "Message Payload size measured for Custom Header [convertedPayloadSizeBytes]: ${bodySize} Bytes.")
        messageLogFactory.getMessageLog(message)?.addCustomHeaderProperty("convertedPayloadSizeBytes", String.valueOf(bodySize))
    }

    def originalBck = properties.get("bckOriginalPayload")

    // Base Case: If no original backup exists yet, then backup directly converted payload
    if (originalBck == null && currentBody != null) {
        message.setProperty("bckConvertedPayload", currentBody)
        message.setProperty("bckConvertedPayloadIsXml", String.valueOf(isXml))
        logger.addEntry("mapSenderStageToReceiverStage", "backupConvertedPayload: Original payload backup missing. Store converted payload into 'bckConvertedPayload'.")
        return
    }

    // Comparison Evaluation: Check if a transformation or script modified the payload since step 1
    if (currentBody != null) {
        if (originalBck != currentBody) {
            originalBck = null
            message.setProperty("bckConvertedPayload", currentBody)
            message.setProperty("bckConvertedPayloadIsXml", String.valueOf(isXml))
            logger.addEntry("mapSenderStageToReceiverStage", "backupConvertedPayload: Converted payload differs from original payload baseline. Saved to 'bckConvertedPayload'.")
        } else {
            message.setProperty("isOriginalConvertedBodyMatch", "true")
            logger.addEntry("mapSenderStageToReceiverStage", "backupConvertedPayload: Converted payload matches original backup perfectly. Skipping property backup creation.")
        }
    }
}