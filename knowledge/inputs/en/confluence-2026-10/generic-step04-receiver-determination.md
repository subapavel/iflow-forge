<!-- Translated from Confluence page "Generic Step04 – Receiver Determination" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Step04 – Receiver Determination

Version: 1.0.4 · Technical name: PIP_04_Generic_ReceiverDetermination · Package: Cloud Integration Pipeline – Generic Pipelines

> [FULLY DECOUPLED PIPELINE ONLY] — this entire flow is not used in the integrated runtime. In integrated, receivers and interfaces are determined by the Integrated Messaging Runtime Async in one combined XSLT. Kept for completeness.

## Purpose

Third step of the fully decoupled pipeline (Recipient List pattern): from queue PIPQ02 it determines the list of receivers — by an XSLT mapping from the Partner Directory, a custom flow, or an XRD mapping reused from PO — splits the message per receiver (including possible duplication into multiple stages) and forwards it: by default to PIPQ03 (interface determination), or, with a combined mapping, directly to PIPQ04 / a receiver-specific queue.

## Queues and adapters

| Adapter | Queue / endpoint | Role |
|---|---|---|
| JMS Sender | {{PipelineJMSQueuePrefix}}Q02 = PIPQ02 | input (from Step02 Inbound Processing) |
| JMS Receiver | PIPQ03 | default → Step05 Interface Determination |
| JMS Receiver | PIPQ04 / ${receiverSpecificQueueName} | bypass interface determination |
| JMS Receiver | PIPQ02_DLQ | dead letter queue |
| ProcessDirect | ${property.customXRDEndpoint} | custom RD flow / reuse XRD |
| ProcessDirect | custom exits, custom error flow | shared patterns |

## Main flow

1. Add custom header properties → Set custom extensions → Check well formed XML → Store payload → router "Max # of retries reached?" (SAPJMSRetries > maxJMSRetries → MaxRetriesExceeded → DLQ)
2. Initialize pre-processing exit → Call custom exit
3. Read custom receiver determination – readCustomReceiverDeterminationFromPD.groovy → router "Custom RD?":
   - No (default) → local process Determine receivers based on XSLT from Partner Directory: binary parameter receiverDetermination of the partner partnerID (the XSLT returns a Receivers/Receiver/Service list)
   - Yes (customXRDBoolean or reuseXRDBoolean) → local process Determine receivers either via custom flow or based on XRD reused from PO: Request Reply to ${property.customXRDEndpoint}; for reuse XRD additionally readReceiverNotDeterminedFromPD.groovy (PD ReceiverNotDeterminedType default Error + ReceiverNotDeterminedDefault) and the mapping Map to Receivers (conversion of the PO XRD output to the pipeline Receivers XML format)
4. Router "Any Receiver?": none → "Not found type?" (Default → fallback receiver, status ReceiverNotFoundDefault / Ignore → end Completed / Error → Error End); otherwise → Iterating splitter over /Receiver
5. Per receiver – stage routing (1.0.4): "Virtual Stage Routing?" → with virtual stages Map to Receiver Stage (mapSenderStageToReceiverStage.groovy) → "Multiple Stages?" → possibly Split Receiver Stages (duplication per stage, tenantStage per copy) → Map to Business Name
6. Router "Bypass interface determination?": count(//Interface) > 0 (the mapping has already returned interfaces too) → Get Receiver specific queue from PD → splitter over interfaces → Get interface (SAP_ReceiverInterface/Index, SAP_OutboundProcessingEndpoint, bypassOption = skipIfDet) → "Receiver specific?" → JMS ${receiverSpecificQueueName} / PIPQ04; otherwise → Get payload → JMS PIPQ03

## Error handling

Shared pattern: Standard Retry handling (test mode, max retries → DLQ PIPQ02_DLQ, DLQ_EndEvent) / Custom error handling with the original payload (fetchPayloadFromXProperty, fix 1.0.2).

## Scripts (new compared to those documented earlier)

| Script | Purpose |
|---|---|
| readReceiverNotDeterminedFromPD.groovy | PD ReceiverNotDeterminedType (Default/Ignore/Error; default Error) + ReceiverNotDeterminedDefault → properties for the reuse-XRD branch (in the XSLT branch this information is directly in the mapping output) |

The others are identical to the async runtime / Step02 documentation.

## Script details

### readCustomReceiverDeterminationFromPD.groovy

```groovy
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

     // first check if a header exists for custom receiver determination
     def customXRDEndpoint = headers.get("customXRDEndpoint");

     // otherwise, check for custom receiver determination by reading the custom receiver determination end
 point from the Partner Directory
     if (customXRDEndpoint == null){
         customXRDEndpoint = service.getParameter("CustomXRDEndpoint", Pid , String.class);
     }

     // create a new exchange property with value true or false depending on whether the end point exists
     message.setProperty("customXRDBoolean", customXRDEndpoint ? 'true' : 'false');

     // do second check only if custom receiver determination is not used
     if (customXRDEndpoint == null){

     // check for extended receiver determination by reading the extended receiver determination end point from
 the Partner Directory
         customXRDEndpoint = service.getParameter("ReuseXRDEndpoint", Pid , String.class);

     // create a new exchange property with value true or false depending on whether the end point exists
     message.setProperty("reuseXRDBoolean", customXRDEndpoint ? 'true' : 'false');
     }

     // if either one of the end points exist, create a new exchange property containing the end point
     // otherwise, the exchange property is not created
     message.setProperty("customXRDEndpoint", customXRDEndpoint ?: null);

         // adding information to audit log using helper PipelineLogger class
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         logger.addEntry("customReceiverDetermination", "Partner ID to read the custom receiver determination: "
 + (Pid ?: "n/a") + ".")
         logger.addEntry("customReceiverDetermination", "Custom or extended receiver determination end point
 from the Partner Directory: property customXRDEndpoint=" + (message.properties.customXRDEndpoint.toString() ?:
 "n/a") + ".")
         logger.addEntry("customReceiverDetermination", "Custom receiver determination existence from the
 Partner Directory: property customXRDBoolean=" + (message.properties.customXRDBoolean.toString() ?: "n/a") +
 ".")
         logger.addEntry("customReceiverDetermination", "Reuse extended receiver determination existence from
 the Partner Directory: property reuseXRDBoolean=" + (message.properties.reuseXRDBoolean.toString() ?: "n/a") +
 ".")
         if (logger.getLog()) {
             logger.saveToMessage(message)
         }

         //PipelineLogger logger = PipelineLogger.newLogger(message)
         //def logEntry = new StringBuilder().append("Partner ID to read the custom receiver determination: ").
 append(Pid ?: "n/a").toString();
         //logger.addEntry("customReceiverDetermination", logEntry);
         //logEntry = new StringBuilder().append("Custom or extended receiver determination end point from the
 Partner Directory: property customXRDEndpoint=").append(message.properties.customXRDEndpoint.toString() ?: "n
 /a").toString();
         //logger.addEntry("customReceiverDetermination", logEntry);
         //logEntry = new StringBuilder().append("Custom receiver determination existence from the Partner
 Directory: property customXRDBoolean=").append(message.properties.customXRDBoolean.toString() ?: "n/a").
 toString();
         //logger.addEntry("customReceiverDetermination", logEntry);
         //logEntry = new StringBuilder().append("Reuse extended receiver determination existence from the
 Partner Directory: property reuseXRDBoolean=").append(message.properties.reuseXRDBoolean.toString() ?: "n/a").
 toString();
         //logger.addEntry("customReceiverDetermination", logEntry);
         //if (logger.getAuditLog()) {
         //        message.setHeader('auditLogHeader', logger.getAuditLog())
         ////}

     return message;
 }
```

Logic: According to partnerID, determines custom/reuse receiver determination: first the header customXRDEndpoint, otherwise PD CustomXRDEndpoint → property customXRDBoolean. If there is no custom one, it reads PD ReuseXRDEndpoint → reuseXRDBoolean. The resulting endpoint goes to customXRDEndpoint. Input: partnerID; PD CustomXRDEndpoint, ReuseXRDEndpoint → Output: properties customXRDBoolean, reuseXRDBoolean, customXRDEndpoint

### readReceiverNotDeterminedFromPD.groovy

```groovy
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

     // read receiver not determined type from the Partner Directory
     def receiverNotDeterminedType = service.getParameter("ReceiverNotDeterminedType", Pid , String.class);

     // if the value exists in the Partner Directory, assign the value to a new exchange property
     // otherwise, set the property to default which is Error
     message.setProperty("receiverNotDeterminedType", receiverNotDeterminedType ? receiverNotDeterminedType :
 'Error');

     // depending on the type, read default receiver from the Partner Directory
     if (receiverNotDeterminedType == 'Default') {
         def receiverNotDeterminedDefault = service.getParameter("ReceiverNotDeterminedDefault", Pid , String.
 class);
         if (receiverNotDeterminedDefault == null){
             throw new IllegalStateException("Partner ID " + Pid + " not found or ReceiverNotDeterminedDefault
 parameter not found in the Partner Directory for the partner ID " + Pid);
         }
         message.setProperty("receiverNotDeterminedDefault", receiverNotDeterminedDefault);
     }

         // adding information to audit log using helper PipelineLogger class
         PipelineLogger logger = PipelineLogger.newLogger(message)
         def logEntry = new StringBuilder().append("Partner ID to read the receiver not determined: ").append
 (Pid ?: "n/a").toString();
         logger.addEntry("readReceiverNotDeterminedFromPD", logEntry);
         logEntry = new StringBuilder().append("Receiver not determined type from the Partner Directory:
 property receiverNotDeterminedType=").append(message.properties.receiverNotDeterminedType.toString() ?: "n/a").
 toString();
         logger.addEntry("readReceiverNotDeterminedFromPD", logEntry);
         logEntry = new StringBuilder().append("If type Default, default receiver from the Partner Directory:
 property receiverNotDeterminedDefault=").append(message.properties.receiverNotDeterminedDefault.toString() ?: "n
 /a").toString();
         logger.addEntry("readReceiverNotDeterminedFromPD", logEntry);
         if (logger.getAuditLog()) {
                 message.setHeader('auditLogHeader', logger.getAuditLog())
         }

     return message;
 }
```

Logic: According to partnerID, reads PD ReceiverNotDeterminedType → property receiverNotDeterminedType (default Error). For type Default it also reads ReceiverNotDeterminedDefault → receiverNotDeterminedDefault (missing → exception). It is used for the reuse-XRD branch; in the XSLT branch this information is directly in the mapping output. Input: partnerID; PD ReceiverNotDeterminedType, ReceiverNotDeterminedDefault → Output: properties receiverNotDeterminedType, optionally receiverNotDeterminedDefault

### mapSenderStageToReceiverStage.groovy

```groovy
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
    String vStagesRaw = service.getParameter(tenantName, "SAP_Integration_Suite_Landscape", String.class) ?:
"PRD"
    def listVirtual = vStagesRaw.split(",")*.trim()
    String tStage = headers.get("tenantStage")
        def tStageList = tStage.split(",")*.trim() ?: []
    boolean isKnown = true

    if (!tStage || tStageList.size() > 1) {
        isKnown = false
        tStage = ""
                message.setHeader("tenantStage", "") // Reset header to empty value
        logger.addEntry("mapSenderStageToReceiverStage", "Detected ambiguous tenant stage. isKnownStage set to
false.")
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
                qos                          : headers.get("SapQualityOfService")
    ]
}

// --- Capability Discovery ---
Map resolveReceiverCapabilities(PartnerDirectoryService service, Map ctx, PipelineLogger logger) {

    if (!ctx.receiverAlias) {
                throw new IllegalStateException("SAP_ReceiverAlias header does not exist after Receiver
Determination.")
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
        logger.addEntry("resolveReceiverCapabilities", "Receiver Alias '${ctx.receiverAlias}' not found in PD
for any configured landscape stages: '${ctx.virtualStages}'. Treating as Business Component/External.")
    }

    return [
        hasPdAlias     : hasPdAlias,
        possibleStages : possibleStages
    ]
}

// --- Core Routing Logic ---
Map resolveReceiverStage(PartnerDirectoryService service, Map ctx, Map caps, PipelineLogger logger) {
    if (!caps.hasPdAlias) return noReceiverStage() // if no Alias in Partner Directory, then target stage
determination is not required

        if (ctx.isKnownStage) { // 2 decision-making branches, which depend on whether Tenant Stage has been
determined
        return resolveKnownTenantStage(service, ctx, caps, logger)
    } else {
        return resolveUnknownTenantStage(service, ctx, caps, logger)
    }
}

Map resolveUnknownTenantStage(PartnerDirectoryService service, Map ctx, Map caps, PipelineLogger logger) {

    // 1.A Tenant Rule at Partner ID (Scenario ID) - Receiver-specific
    String rule = service.getParameter("stageRule_${ctx.tenantName}~${ctx.receiverAlias}", ctx.partnerId,
String.class)
    if (rule) {
                logger.addEntry("resolveUnknownTenantStage", "For tenant '${ctx.tenantName}' routing rule
'${rule}' found at Scenario ID '${ctx.partnerId}' defined for any Stage in Tenant for Receiver '${ctx.
receiverAlias}'.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }

    // 1.B Tenant Rule at Partner ID (Scenario ID) - All Receivers
    rule = service.getParameter("stageRule_${ctx.tenantName}", ctx.partnerId, String.class)
    if (rule) {
                logger.addEntry("resolveUnknownTenantStage", "For tenant '${ctx.tenantName}' routing rule
'${rule}' found at Scenario ID '${ctx.partnerId}' defined for any Stage in Tenant for any Receiver.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }

    // 2. Tenant Rule at Receiver Alias
    rule = service.getParameter("stageRule_${ctx.tenantName}", ctx.receiverAlias, String.class)
    if (rule) {
                logger.addEntry("resolveUnknownTenantStage", "For tenant '${ctx.tenantName}' routing rule
'${rule}' found at Receiver Alias '${ctx.receiverAlias}'.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }

    // 3. Only one possible stage
    if (caps.possibleStages.size() == 1) {
                logger.addEntry("resolveUnknownTenantStage", "Receiver Alias '${ctx.receiverAlias}' has only 1
possible stage '${caps.possibleStages[0]}'.")
                return singleReceiverStage(caps.possibleStages[0])
        }

    // 4. Routing rule is required - throw stage routing exception
        throw new IllegalStateException("Virtual Stage Routing Error: Tenant Stage unknown (possible: '${ctx.
virtualStages}') and multiple target Receiver Stages (possible: '${caps.possibleStages}') found for Receiver
Alias '${ctx.receiverAlias}'. Routing rule is required.")
}

Map resolveKnownTenantStage(PartnerDirectoryService service, Map ctx, Map caps, PipelineLogger logger) {

    // 1. Stage Rule at Partner ID (Scenario ID) - Receiver-specific or All Receivers
    String rule = service.getParameter("stageRule_${ctx.stage}~${ctx.receiverAlias}", ctx.partnerId, String.
class) // Receiver-specific at Specific Stage
        if(rule) {
                logger.addEntry("resolveKnownTenantStage", "Stage-Specific Rule: For tenant '${ctx.tenantName}'
stage '${ctx.stage}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined as stage-specific
for Receiver '${ctx.receiverAlias}'.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }
        rule = service.getParameter("stageRule_${ctx.stage}", ctx.partnerId, String.class) // Any Receiver at
Specific Stage
    if (rule) {
                logger.addEntry("resolveKnownTenantStage", "Stage-Specific Rule: For tenant '${ctx.tenantName}'
stage '${ctx.stage}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined as stage-specific
for any Receiver.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }

        // 2. Stage Rule at Receiver Alias
    rule = service.getParameter("stageRule_${ctx.stage}", ctx.receiverAlias, String.class)
    if (rule) {
                logger.addEntry("resolveKnownTenantStage", "Stage-Specific Rule: For tenant '${ctx.tenantName}'
stage '${ctx.stage}' routing rule '${rule}' found at Receiver Alias '${ctx.receiverAlias}' defined as stage-
specific.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }

    // 3. Stage Exact Match
    if (ctx.stage in caps.possibleStages) {
                logger.addEntry("resolveKnownTenantStage", "Receiver Alias '${ctx.receiverAlias}' has matching
Tenant Stage '${ctx.stage}'.")
                return singleReceiverStage(ctx.stage)
        }

    // 4 Tenant Rule (fallback if no Stage specific value) at Partner ID (Scenario ID) - Receiver-specific or
All Receivers
    rule = service.getParameter("stageRule_${ctx.tenantName}~${ctx.receiverAlias}", ctx.partnerId, String.
class) // Receiver-specific in Tenant at any Stage
    if (rule) {
                logger.addEntry("resolveKnownTenantStage", "Tenant-Fallback Rule: For tenant '${ctx.
tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined for
any Stage in Tenant for Receiver '${ctx.receiverAlias}'.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }
    rule = service.getParameter("stageRule_${ctx.tenantName}", ctx.partnerId, String.class) // Any Receivers in
Tenant at any Stage
    if (rule) {
                logger.addEntry("resolveKnownTenantStage", "Tenant-Fallback Rule: For tenant '${ctx.
tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Scenario ID '${ctx.partnerId}' defined for
any Stage in Tenant for any Receiver.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }

    // 5. Tenant Rule (fallback if no Stage specific value) at Receiver Alias
    rule = service.getParameter("stageRule_${ctx.tenantName}", ctx.receiverAlias, String.class)
    if (rule) {

                logger.addEntry("resolveKnownTenantStage", "Tenant-Fallback Rule: For tenant '${ctx.
tenantName}' stage '${ctx.stage}' routing rule '${rule}' found at Receiver Alias '${ctx.receiverAlias}' defined
for any Stage in Tenant.")
                return parseRouteStage(service, rule, caps.possibleStages, ctx.receiverAlias, logger)
        }

        // 6. Routing rule is required - throw stage routing exception
        throw new IllegalStateException("Virtual Stage Routing Error: Tenant stage '${ctx.stage}' is known but
no matching stage found at Receiver Alias '${ctx.receiverAlias}' that has possible target stages '${caps.
possibleStages}'. Routing rule is required.")
}

// --- Helpers ---
Map noReceiverStage() {
        [ noReceiverStageBoolean: true ]
}

Map singleReceiverStage(String stage) {
        [ receiverStages: [ stage ] ]
}

Map parseRouteStage(PartnerDirectoryService service, String value, List<String> possibleStages, String
receiverAlias, PipelineLogger logger) {
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
            logger.addEntry("mapSenderStageToReceiverStage", "parseRouteStage: Stage '${s}' was not in global
virtual landscape but is explicitly mapped to Alias '${receiverAlias}'. Including in route.")
        } else {
            logger.addEntry("mapSenderStageToReceiverStage", "parseRouteStage WARNING: Stage '${s}' in Rule is
NOT mapped to Alias '${receiverAlias}' in PD. Skipping.")
        }
    }

    // 4. Final Validation
    valid = valid.unique()
    if (!valid) {
        throw new IllegalStateException("Virtual Stage Routing Error: No valid receiver stages remain. Rule
'${value}' provided targets that do not exist for Alias '${receiverAlias}' in the Partner Directory.")
    }

    return [ receiverStages: valid ]
}

void applyRoutingResult(Message message, Map result, PipelineLogger logger, Map ctx) {
    if (result.noReceiverStageBoolean) {
        if (!ctx.isKnownStage) {
                        message.setHeader("tenantStage", "PRD") // set Tenant Stage to default value if tenant
stage unknown
                        ctx.stage = "PRD"
                }
                message.setProperty("noReceiverStageBoolean", "true") // property for routing in IFlow
                message.setProperty("customHeader_tenantStage", ctx.stage)

         logger.addEntry("mapSenderStageToReceiverStage", "applyRoutingResult: No Receiver Alias in Partner
 Directory  -> noReceiverStageBoolean=true")
         return
     }

     if (result.receiverStages?.size() == 1) {
         message.setHeader("tenantStage", result.receiverStages[0])
                 message.setProperty("singleReceiverStageBoolean", "true") // property for routing in IFlow
                 message.setProperty("customHeader_tenantStage", result.receiverStages[0])
         logger.addEntry("mapSenderStageToReceiverStage", "applyRoutingResult: Single Stage '${result.
 receiverStages[0]}' determined and assigned to header 'tenantStage'.")
         return
     }

     // Multiple stages found: Build Multi-Receiver XML
         if(ctx.qos == "BestEffort") {
                 throw new IllegalStateException("Virtual Stage Routing Error: Multiple target stages '${result.
 receiverStages}' were determined for a synchronous interface with QoS 'Best Effort'. Message splitting is not
 permitted for synchronous interfaces.")
         }
     logger.addEntry("mapSenderStageToReceiverStage", "applyRoutingResult: Multiple stages '${result.
 receiverStages}' determined -> Building Receiver Stages XML.")
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
```

(1.0.4)

Logic: Stage rules engine. Loads the context (tenantStage, TENANT_NAME from env, virtual stages from PD SAP_Integration_Suite_Landscape, SAP_ReceiverAlias, partnerID, QoS), determines in which stages the receiver exists (getAlternativePartnerId(stage, 'BusinessSystemName', alias)) and according to the priority of the stageRule_* parameters determines the target stage. One stage → header tenantStage; multiple stages → multi-Receiver XML (duplication of the message per stage); no alias in the PD → noReceiverStageBoolean. Input: headers tenantStage, SAP_ReceiverAlias, partnerID; TENANT_NAME; PD SAP_Integration_Suite_Landscape, stageRule_* → Output: header tenantStage / multi-Receiver XML / property noReceiverStageBoolean, customHeader_tenantStage

### mapReceiverAliasToBusinessSystemName.groovy

```groovy
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
         logger.addEntry("mapReceiverToBusinessName", "Business System Name=" + (headers.get("SAP_Receiver") ?:
 "n/a") + ".")
         if (logger.getLog()) {
             logger.saveToMessage(message)
         }

     //PipelineLogger logger = PipelineLogger.newLogger(message)
     //def logEntry = new StringBuilder().append("Tenant Stage=").append(Agency ?: "n/a").toString();
         //logger.addEntry("mapReceiverToBusinessName", logEntry);
     //logEntry = new StringBuilder().append("Receiver Alias=").append(Pid ?: "n/a").toString();
         //logger.addEntry("mapReceiverToBusinessName", logEntry);
     //logEntry = new StringBuilder().append("Business System Name=").append(headers.get("SAP_Receiver") ?: "n
 /a").toString();
         //logger.addEntry("mapReceiverToBusinessName", logEntry);
     //if (logger.getAuditLog()) {
         //    message.setHeader('auditLogHeader', logger.getAuditLog())
     //}

     return message;
 }
```

Logic: Separates the party (party.system) from SAP_ReceiverAlias → SAP_ReceiverParty + SAP_ReceiverAlias (without party). Via getAlternativePartnerId(tenantStage ?: 'PRD', 'BusinessSystemName', alias) it translates the alias to the actual business system → SAP_Receiver. When the receiver differs from the alias / a party exists, it adds ReceiverAlias/ReceiverParty/ReceiverSystem to customHeaderProperties. Input: SAP_ReceiverAlias, tenantStage; PD alternative partner IDs → Output: SAP_Receiver, SAP_ReceiverParty, customHeaderProperties

### readReceiverSpecificQueueFromPD.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message;
import com.sap.it.api.pd.PartnerDirectoryService;
import com.sap.it.api.ITApiFactory;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {

    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null);
    if (service == null){
        throw new IllegalStateException("Partner Directory Service not found");
    }

    // get headers
    def headers = message.getHeaders();
    def ReceiverAlias = headers.get("SAP_ReceiverAlias");

    // First check if receiver-specific queue is maintained for a particular configuration scenario
    String Pid = headers.get("partnerID");
    def receiverSpecificQueue = service.getParameter("ReceiverSpecificQueue_" + ReceiverAlias, Pid , String.
class);

    // If not, check if receiver-specific queue is maintained for the particular receiver
    if (receiverSpecificQueue == null) {
        receiverSpecificQueue = service.getParameter("ReceiverSpecificQueue", ReceiverAlias , String.class);
    }

    // if the receiver-specific queue exists, create a new exchange property containing the queue name
    // otherwise, the exchange property is not created
    message.setProperty("receiverSpecificQueueName", receiverSpecificQueue ?: null);

    // create a new exchange property with value true or false depending on whether the queue exists
    message.setProperty("receiverSpecificQueueBoolean", receiverSpecificQueue ? 'true' : 'false');

        // adding information to audit log using helper PipelineLogger class
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        logger.addEntry("readReceiverSpecificQueueFromPD", "Header SAP_ReceiverAlias=" + (message.headers.
SAP_ReceiverAlias ?: "n/a") + ".")
        logger.addEntry("readReceiverSpecificQueueFromPD", "PID to read the receiver-specific JMS queue from
the Partner Directory: " + (Pid ?: "n/a") + ".")
        logger.addEntry("readReceiverSpecificQueueFromPD", "Receiver specific queue from the Partner Directory:
property receiverSpecificQueueName=" + (message.properties.receiverSpecificQueueName.toString() ?: "n/a") + ".")
        logger.addEntry("readReceiverSpecificQueueFromPD", "Receiver specific queue existence from the Partner
Directory: property receiverSpecificQueueBoolean=" + (message.properties.receiverSpecificQueueBoolean.toString()
?: "n/a") + ".")
        if (logger.getLog()) {
            logger.saveToMessage(message)
        }

        //PipelineLogger logger = PipelineLogger.newLogger(message)
        //def logEntry = new StringBuilder().append("Header SAP_ReceiverAlias=").append(message.headers.
SAP_ReceiverAlias ?: "n/a").toString();
        //logger.addEntry("readReceiverSpecificQueueFromPD", logEntry);
        //logEntry = new StringBuilder().append("PID to read the receiver-specific JMS queue from the Partner
Directory: ").append(Pid ?: "n/a").toString();
        //logger.addEntry("readReceiverSpecificQueueFromPD", logEntry);
        //logEntry = new StringBuilder().append("Receiver specific queue from the Partner Directory: property
receiverSpecificQueueName=").append(message.properties.receiverSpecificQueueName.toString() ?: "n/a").
toString();
        //logger.addEntry("readReceiverSpecificQueueFromPD", logEntry);
        //logEntry = new StringBuilder().append("Receiver specific queue existence from the Partner Directory:
property receiverSpecificQueueBoolean=").append(message.properties.receiverSpecificQueueBoolean.toString() ?: "n
/a").toString();
        //logger.addEntry("readReceiverSpecificQueueFromPD", logEntry);
        //if (logger.getAuditLog()) {
        //        message.setHeader('auditLogHeader', logger.getAuditLog())
        //}

    return message;
}
```

Logic: Determines the receiver-specific queue: PD ReceiverSpecificQueue_<SAP_ReceiverAlias> according to partnerID, otherwise ReceiverSpecificQueue according to SAP_ReceiverAlias. The result goes to receiverSpecificQueueName + receiverSpecificQueueBoolean. Input: partnerID, SAP_ReceiverAlias; PD parameters → Output: properties receiverSpecificQueueName, receiverSpecificQueueBoolean

### checkWellFormedXML.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message;
 import src.main.resources.script.PipelineLogger;

 def Message processData(Message message) {
     Reader reader = message.getBody(Reader)
     try {
         def xml = new XmlSlurper().parse(reader)
         message.setHeader("isWellFormedXml", 'true')
     }
     catch (ex)
     {
         message.setHeader("isWellFormedXml", 'false')
     }

         // adding information to audit log using helper PipelineLogger class
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         logger.addEntry("checkWellFormedXML", "Header isWellFormedXml=" + (message.headers.isWellFormedXml.
 toString() ?: "n/a") + ".")
         if (logger.getLog()) {
             logger.saveToMessage(message)
         }

         //PipelineLogger logger = PipelineLogger.newLogger(message)
         //def logEntry = new StringBuilder().append("Header isWellFormedXml=").append(message.headers.
 isWellFormedXml.toString() ?: "n/a").toString();
         //logger.addEntry("checkWellFormedXML", logEntry);
         //if (logger.getAuditLog()) {
         //        message.setHeader('auditLogHeader', logger.getAuditLog())
         //}

     return message
 }
```

Logic: Parses the body as XML (XmlSlurper) → header isWellFormedXml (true/false); controls how the payload is backed up. Input: body → Output: header isWellFormedXml

### storePayloadInXProperty.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message;

 def Message processData(Message message) {

     //get body
     def body = message.getBody();

     // get headers
     def headers = message.getHeaders();
     def isWellFormedXml = headers.get("isWellFormedXml");

     if (isWellFormedXml == "true"){
         message.setProperty("payload", body);
     } else {
         message.setProperty("payload", body.bytes.encodeBase64().toString());
     }

     return message
 }
```

Logic: Backs up the body into property payload; non-XML is Base64-encoded. Input: body, header isWellFormedXml → Output: property payload

### fetchPayloadFromXProperty.groovy

```groovy
 def Message processData(Message message) {

     // get headers
     def headers = message.getHeaders();
     def isWellFormedXml = headers.get("isWellFormedXml");

     // get properties
     def properties = message.getProperties();

     if (isWellFormedXml == "true"){
         message.setBody(properties.get("payload"));
     } else {
         message.setBody(properties.get("payload").decodeBase64());
     }

     return message
 }
```

Logic: Restores the payload from property payload back into the body; non-XML is Base64-decoded (custom error handling, fix 1.0.2). Input: property payload, header isWellFormedXml → Output: body

### addCustomHeaderProperties.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message
 import src.main.resources.script.PipelineHelper
 import groovy.json.JsonOutput
 import java.util.Base64
 import org.w3c.dom.NodeList

 def Message processData(Message message) {
         // Constant: Property Prefix for Custom Header Property
         final String CH_PREFIX = "customHeader_"
         def headers = message.getHeaders()
     def custHeaderProps = headers.get('customHeaderProperties') //custom headers are either stored in the
 standard pipeline header
         def properties = message.getProperties() //or custom headers can be defined as IFlow-step specific
 Exchange Propeties
         def resultCustomHeaderProperties = [:] // Start with empty map
         def messageLog = messageLogFactory.getMessageLog(message)

     try {
          // first check if header customHeaderProperties exists and already defined (only String input is
 expected)
                  if (custHeaderProps && custHeaderProps instanceof String && custHeaderProps?.trim()) {
             // Split by '|' and handle each part independently (backward compatibility to avoid failure of
 handling input like jsonInBase64|key:value
             custHeaderProps.split(/\|/).each { part ->
                 def decodedPart = PipelineHelper.decodeIfBase64(part) //if base64 and JSON - decode to Map,
 otherwise return String
                 if (decodedPart instanceof Map) {
                     resultCustomHeaderProperties.putAll(decodedPart)
                 } else if (decodedPart instanceof String && PipelineHelper.isValidKeyValueString(decodedPart)) {
                                         //if String - check if it is delimited as expected and if yes - split
 and convert to result Map
                                         decodedPart.split(/\|/).each { pair ->
                                                 def kv = pair.split(":", 2)
                                                 if (kv?.size() == 2) {
                                                         resultCustomHeaderProperties[kv[0]?.trim()] = kv[1]?.
 trim()
                                                 }
                                         }
                 }
                 // else ignore

             }
         }
     } catch (Exception ignored) {
         // Fallback to empty map on any failure
         resultCustomHeaderProperties = [:]
     }

     try {
         // Merge Exchange properties with prefix
         properties?.findAll { k, v ->
             k instanceof String && k.startsWith(CH_PREFIX)
         }?.each { k, v ->
             def cleanKey = k.replaceFirst("^${CH_PREFIX}", "")
                         if (v instanceof org.w3c.dom.NodeList) {
                                 // Handle NodeList: create multiple headers with same base name + counter
                                 for (int i = 0; i < v.getLength(); i++) {
                                         def nodeValue = v.item(i)?.getTextContent()?.trim()
                                         if (nodeValue) {
                                                 def indexedKey = "${cleanKey}_NdLstElmnt${i + 1}" // unique in
 internal MAP with Node List Element index
                                                 resultCustomHeaderProperties[indexedKey] = nodeValue
                                         }
                                 }
                         } else {
                                 // Normal case (String or other type)
                                 resultCustomHeaderProperties[cleanKey] = v
                         }
         }
     } catch (Exception ignored) {
         // Ignore on failure
     }

     try { //if final Customer Headers Map exists and not empty - attach to the MPL log and store the header
 with potentially new or same values
         if (messageLog && resultCustomHeaderProperties && !resultCustomHeaderProperties.isEmpty()) {
             resultCustomHeaderProperties.each { key, value ->
                 def logKey = key.replaceFirst(/_NdLstElmnt\d+$/, "") // IF Node List Element index - remove
 suffix to simplify the search by custom value
                                 messageLog.addCustomHeaderProperty(logKey, value)
             }
                         // Convert map to JSON
             def jsonString = JsonOutput.toJson(resultCustomHeaderProperties)
                         // Encode JSON to Base64 and set standard header
             def base64Encoded = Base64.encoder.encodeToString(jsonString.getBytes("UTF-8"))
             message.setHeader('customHeaderProperties', base64Encoded)
         }
     } catch (Exception ignored) {
         // Log writing failed, continue
     }

         return message
 }
```

Logic: MPL custom headers from the header customHeaderProperties and the properties customHeader_*. Input: customHeaderProperties, properties customHeader_* → Output: MPL custom headers

### attachAdditionallnformation.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message
 import src.main.resources.script.PipelineLogger;

 def Message processData(Message message) {

     // get queue names
     def propertyMap = message.getProperties()
     def incomingQueue = propertyMap.get("incomingQueue");
     def deadLetterQueue;
     if (incomingQueue != null) {
         deadLetterQueue = new StringBuilder(incomingQueue).append('_DLQ').toString();
     }
     def customStatus = propertyMap.get("SAP_MessageProcessingLogCustomStatus")?:'NONE';
     // get test mode
     def headers = message.getHeaders();
     def testMode = headers.get("testMode");

     // adding information to audit log using helper PipelineLogger class
     def messageLog = messageLogFactory.getMessageLog(message);
     if (messageLog != null) {
     //    PipelineLogger logger = PipelineLogger.newLogger(message)
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         if (testMode != 'true' && !customStatus.contains("OptionNotSupported") && incomingQueue != null){
                     logger.addEntry("attachAdditionallnformation", "Maximum number of retries exceeded.")
                     logger.addEntry("attachAdditionallnformation", "Message removed from queue " +
 incomingQueue + " and sent to dead letter queue " + deadLetterQueue + ".")
             //def logEntry = new StringBuilder().append("Message removed from queue ").append(incomingQueue).
 append(" and sent to dead letter queue ").append(deadLetterQueue).toString();
             if (logger.getLog()) {
                     logger.saveToMessage(message)
                 }
             //logger.addEntry("attachAdditionallnformation", logEntry);
         }
         messageLog.addAttachmentAsString('Additional Information', logger.getAuditLog4Print(), 'text/plain')
     }
     return message;
 }
```

Logic: Attaches the audit log as the MPL attachment "Additional Information"; outside test mode and with a known incomingQueue it adds a record about the move to _DLQ. Input: properties incomingQueue, SAP_MessageProcessingLogCustomStatus, header testMode → Output: MPL attachment

### readCustomXFromPD.groovy

```groovy
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
        logger.addEntry("readCustomXFromPD", "Partner ID to read the custom exit settings: " + (Pid ?: "n/a") +
".")
        logger.addEntry("readCustomXFromPD", "Custom exit type: property CustomXType=" + (message.properties.
CustomXType.toString() ?: "n/a") + ".")
        logger.addEntry("readCustomXFromPD", "Custom exit end point from the Partner Directory: property
CustomXEndpoint=" + (message.properties.CustomXEndpoint.toString() ?: "n/a") + ".")
        logger.addEntry("readCustomXFromPD", "Custom exit enabled: property CustomXBoolean=" + (message.
properties.CustomXBoolean.toString() ?: "n/a") + ".")
        if (logger.getLog()) {
            logger.saveToMessage(message)
        }

        //PipelineLogger logger = PipelineLogger.newLogger(message)
        //def logEntry = new StringBuilder().append("Partner ID to read the custom exit settings: ").append(Pid
?: "n/a").toString();
        //logger.addEntry("readCustomXFromPD", logEntry);
        //logEntry = new StringBuilder().append("Custom exit type: property CustomXType=").append(message.
properties.CustomXType.toString() ?: "n/a").toString();
        //logger.addEntry("readCustomXFromPD", logEntry);
        //logEntry = new StringBuilder().append("Custom exit end point from the Partner Directory: property
CustomXEndpoint=").append(message.properties.CustomXEndpoint.toString() ?: "n/a").toString();
        //logger.addEntry("readCustomXFromPD", logEntry);
        //logEntry = new StringBuilder().append("Custom exit enabled: property CustomXBoolean=").append(message.
properties.CustomXBoolean.toString() ?: "n/a").toString();
        //logger.addEntry("readCustomXFromPD", logEntry);
        //if (logger.getAuditLog()) {
        //        message.setHeader('auditLogHeader', logger.getAuditLog())
        //}

     return message;
 }
```

Logic: Step number from camelId; reads CustomXPreEnabled/CustomXPreEndpoint from the headers or from the PD according to partnerID. Input: partnerID, property CustomXType → Output: properties CustomXBoolean, CustomXEndpoint

## Libraries

- PipelineLogger.groovy — structured audit log.
- PipelineHelper.groovy — Base64/JSON helpers.
