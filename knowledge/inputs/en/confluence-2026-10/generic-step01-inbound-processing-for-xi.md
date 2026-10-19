<!-- Translated from Confluence page "Generic Step01 – Inbound Processing for XI" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Step01 – Inbound Processing for XI

Version: 1.0.5 (Origin-Bundle) · Technical name: PIP_01_InboundProcessing4Scenario1 · Package: Cloud Integration Pipeline – Generic Inbound

## Purpose

Generic pipeline entry point for messages arriving through the XI adapter (ABAP proxy). The flow receives the message, sets the standard pipeline headers, determines the partner ID (configuration scenario in the Partner Directory), calls optional custom exits and, depending on the Quality of Service, either stores the message in a JMS queue (asynchronously, Exactly Once) or passes it on synchronously (Best Effort). It is deployed once and serves all XI inbound scenarios.

## Entry point (XI Sender adapter)

| Setting | Value |
|---|---|
| Endpoint | {{XIEntryPoint}} = /PIP/XI |
| Authentication | {{SenderAuthType}} = RoleBased, role {{UserRole}} = ESBMessaging.send |
| Max. body / attachment size | {{BodySize}} = 40 MB / {{AttachmentSize}} = 100 MB |

## Main flow (Integration Process)

1. Set SAP headers (Content Modifier) – mapping of XI headers to pipeline headers:
   - sapsenderservice → SAP_Sender
   - sapinterfacename → SAP_SenderInterface and SAP_MessageType
   - sapinterfacenamespace → SAP_SenderInterfaceNamespace
   - SAP_ApplicationID = ${header.SapMessageId.toUpperCase()}
   - properties from externalized parameters: CustomXPreGlobal_Enabled/_Endpoint, CustomXError_Enabled, CustomXPid_SwitchOffTilde, customHeader_QualityOfService
2. Fetch dynamic configuration – script createHeadersFromSapDynamicConfiguration.groovy
3. Add party – script addPartyForXIInbound.groovy
4. Determine partner ID – script determinePartnerID.groovy (key step: determining the configuration scenario / partner ID for reading from the Partner Directory)
5. Initialize pre-processing exit – property CustomXType = CustomXPre
6. Call custom exit – call of the local process Call custom exits (see below)
7. Router "QoS?" by ${header.SapQualityOfService}:

| Branch | Condition | Processing |
|---|---|---|
| EO | ExactlyOnce | readInboundQueueFromPD.groovy → addCustomHeaderProperties.groovy → Idempotent Process Call → router "Duplicate?" |
| BE | BestEffort | addCustomHeaderProperties.groovy → readBypassOptionFromPD.groovy → router "Bypass option?" |
| EOIO | ExactlyOnceInOrder | custom status ConfigurationOptionNotSupported ("EOIO not yet supported in the Pipeline") → throwException.groovy |
| Empty/unknown | default | custom status ConfigurationOptionNotSupported → throwException.groovy |

### Branch EO (asynchronous, Exactly Once)

- Idempotent Process Call: deduplication by ${header.SapMessageIdEx}, skipOnDuplicate=true, calls the local process Forward message to JMS queue.
- Local process – router "Which queue?":
  - ${property.inbQueue} != null (queue determined in the Partner Directory by the script readInboundQueueFromPD) → JMS Send to ${property.inbQueue}
  - default → JMS Send to {{PipelinePrefix}}{{DefaultJMSQueueNumber}} = PIPX01
- Router "Duplicate?": ${property.CamelDuplicateMessage} = 'true' → custom status DuplicateDiscarded, end Completed (the duplicate is discarded without an error); otherwise normal end.

> Integrated runtime: the default target queue is PIPX01. The value PIPQ01 would mean routing to the fully decoupled pipeline (via PD InboundQueue).

### Branch BE (synchronous, Best Effort)

Router "Bypass option?" by ${property.bypassOption} (read by readBypassOptionFromPD.groovy from the Partner Directory):

| Value | Behavior |
|---|---|
| (default) CBR | Request Reply → ProcessDirect /{{PipelinePrefix}}/XS01 (synchronous runtime, receiver determination, etc.) |
| p2p | mapReceiverAliasToBusinessSystemName.groovy → Request Reply → ProcessDirect ${header.SAP_OutboundProcessingEndpoint} (directly to the scenario-specific outbound flow, the pipeline steps are skipped) |
| skipRcvDet | [FULLY DECOUPLED PIPELINE ONLY] — bypass only for the fully decoupled pipeline; not supported for XI sync → custom status ConfigurationOptionNotSupported → exception |

After the Request Reply, the pipeline headers (sap_sender*, sap_receiveralias, partnerid, tenantstage, auditlogheader, …) are removed from the response and the flow ends (the response is returned to the XI sender).

## Local process: Call custom exits (global and scenario-specific)

1. Initialize exit – from ${property.CustomXType} (here CustomXPre) it derives the parameter names …Global_Enabled / …Global_Endpoint and populates CustomXGlobalBoolean / CustomXGlobalEndpoint.
2. Router "Global exit?": ${property.CustomXGlobalBoolean} = 'true' → Request Reply to ProcessDirect ${property.CustomXGlobalEndpoint} (default /pip/custom/preProcessing/global).
3. Check scenario-specific exit – script readCustomXFromPD.groovy (reads from the Partner Directory whether the scenario has its own exit; sets CustomXBoolean / CustomXEndpoint).
4. Router "Scenario-specific exit?": ${property.CustomXBoolean} = 'true' → Request Reply to ProcessDirect ${property.CustomXEndpoint}.

## Error handling

All three processes (main, custom exits, JMS forward) have an Exception Subprocess with the same logic:

- Router "Custom Extension?": ${property.CustomXError_Enabled} = 'true' (default false)
  - Yes → Call custom error handling: a Content Modifier sets the headers exceptionSourceMplId, exceptionTimestamp, exceptionStacktrace, exceptionMessage, pipelineStepID → ProcessDirect {{CustomXError_Endpoint}} (= /pip/custom/errorHandling) → router by ${header.customXError_EndEvent}: Completed → End / Escalated → Escalation End / otherwise → Error End.
  - No → Standard error handling: router "QoS?" – BestEffort → response body ${exception.message}, custom status SyncCallFailed, the error is returned to the synchronous caller; asynchronous → Error End (retry is handled by JMS).

## Scripts – where and why they are called

Call order in the main flow: createHeadersFromSapDynamicConfiguration → addPartyForXIInbound → determinePartnerID → (custom exits: readCustomXFromPD) → branch EO: readInboundQueueFromPD → addCustomHeaderProperties / branch BE: addCustomHeaderProperties → readBypassOptionFromPD → (p2p: mapReceiverAliasToBusinessSystemName).

| Script | Called in step | Purpose |
|---|---|---|
| createHeadersFromSapDynamicConfiguration.groovy | Fetch dynamic configuration | Conversion of the XI dynamic configuration to headers |
| addPartyForXIInbound.groovy | Add party | Adding party information for XI inbound |
| determinePartnerID.groovy | Determine partner ID | Determining the partner ID (configuration scenario) for the Partner Directory |
| readCustomXFromPD.groovy | Check scenario-specific exit | Reading the scenario-specific exit configuration from the PD |
| readInboundQueueFromPD.groovy | Read inbound queue (EO branch) | Determining the target JMS queue from the PD (inbQueue) |
| addCustomHeaderProperties.groovy | 2× (EO and BE branch) | Building custom header properties for MPL monitoring |
| readBypassOptionFromPD.groovy | Read bypass option (BE branch) | Reading the bypass option (p2p / skipRcvDet) from the PD |
| mapReceiverAliasToBusinessSystemName.groovy | Map to Business Name (p2p branch) | Mapping the receiver alias to the actual business system |
| throwException.groovy | 3× (error branches of the routers) | Throwing an exception with ${property.exceptionMessage} |
| PipelineLogger.groovy | – (library) | Logging helper (audit log), imported by 8 scripts |
| PipelineHelper.groovy | – (library) | Helper (Base64/JSON parsing), imported by addCustomHeaderProperties |
| auditLogHandler.groovy, logMessage.groovy | – | Helper/utility scripts (in this flow they are not bound to any step) |

Attach the source files of all scripts in Confluence as page attachments.

## Externalized parameters

| Parameter | Default | Meaning |
|---|---|---|
| XIEntryPoint | /PIP/XI | Endpoint of the XI sender adapter |
| SenderAuthType / UserRole | RoleBased / ESBMessaging.send | Sender authentication |
| PipelinePrefix / DefaultJMSQueueNumber | PIP / X01 | Default inbound queue (PIPX01) |
| JMS_RetentionThreshold / JMS_ExpirationPeriod | 2 / 30 | Retention and expiration of messages in JMS (days) |
| JMS_EncryptStoredMessage / JMS_CompressStoredMessage | 1 / 1 | Encryption and compression of stored messages |
| CustomXPreGlobal_Enabled / _Endpoint | false / /pip/custom/preProcessing/global | Global pre-processing exit |
| CustomXError_Enabled / _Endpoint | false / /pip/custom/errorHandling | Custom error handling |
| CustomXPid_SwitchOffTilde | true | Behavior when building the partner ID (tilde) |
| BodySize / AttachmentSize | 40 / 100 | Size limits (MB) |
| CommunicationParty, IssuerDN, SubjectDN | – | Optional (party / certificate authentication) |

## Script details

### createHeadersFromSapDynamicConfiguration.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message

 def Message processData(Message message) {
         // Constant: Property Prefix for Dynamic Configuration
         final String DC_PPREFIX = "SapDynamicConfiguration_"
         // Constant: Header Prefix for Dynamic Configuration
         final String DC_HPREFIX = "dc_" + DC_PPREFIX
         def properties = message.getProperties()
         def resultDynamicConfigurations = [:] // Start with empty map

     try {
         // read all properties matching the prefix
         properties?.findAll { k, v ->
             k instanceof String && k.startsWith(DC_PPREFIX)
         }?.each { k, v ->
             def cleanKey = k.replaceFirst("^${DC_PPREFIX}", "")
                         resultDynamicConfigurations[cleanKey] = v
         }
     } catch (Exception ignored) {
         // Ignore on failure
     }

     if (resultDynamicConfigurations && !resultDynamicConfigurations.isEmpty()) {
         resultDynamicConfigurations.each { key, value ->
                         message.setHeader("${DC_HPREFIX}${key}", value)
         }
         }

         return message
 }
```

**Called in:** Fetch dynamic configuration (main flow, step 2). **Logic:** Goes through the exchange properties with the prefix SapDynamicConfiguration_* (XI dynamic configuration) and creates a header dc_SapDynamicConfiguration_<key> for each. Errors are silently ignored. **Input:** properties SapDynamicConfiguration_* → **Output:** headers dc_SapDynamicConfiguration_*

### addPartyForXIInbound.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message;
 import src.main.resources.script.PipelineLogger;

 def Message processData(Message message) {

     // get headers
     def headers = message.getHeaders();

     // Fetch sender party and sender service
     String party = headers.get("SapSenderParty");
     String service = headers.get("SapSenderService");

     // Concatenate if party exists
     if (party != null && party != ''){
         message.setHeader("SAP_Sender",party + '.' + service);
         message.setHeader("SAP_SenderParty",party);
     }

         // adding information to audit log using helper PipelineLogger class
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         logger.addEntry("addPartyForXIInbound", "Header SAP_Sender=" + (message.headers.SAP_Sender.toString()
 ?: "n/a") + ".")
         logger.addEntry("addPartyForXIInbound", "Header SAP_SenderParty=" + (message.headers.SAP_SenderParty.
 toString() ?: "n/a") + ".")
         if (logger.getLog()) {
             logger.saveToMessage(message)
         }

     //PipelineLogger logger = PipelineLogger.newLogger(message)
     //def logEntry = new StringBuilder().append("Header SAP_Sender=").append(message.headers.SAP_Sender.
 toString() ?: "n/a").toString();
         //logger.addEntry("addPartyForXIInbound", logEntry);
         //logEntry = new StringBuilder().append("Header SAP_SenderParty=").append(message.headers.
 SAP_SenderParty.toString() ?: "n/a").toString();
         //logger.addEntry("addPartyForXIInbound", logEntry);
     //if (logger.getAuditLog()) {
         //    message.setHeader('auditLogHeader', logger.getAuditLog())
     //}

     return message;
 }
```

**Called in:** Add party (main flow, step 3). **Logic:** If the header SapSenderParty exists, it overwrites SAP_Sender with <party>.<service> and sets SAP_SenderParty. Writes an audit log via PipelineLogger. **Input:** headers SapSenderParty, SapSenderService → **Output:** headers SAP_Sender, SAP_SenderParty

### determinePartnerID.groovy — key script

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message
 import com.sap.it.api.ITApiFactory
 import com.sap.it.api.pd.PartnerDirectoryService
 import src.main.resources.script.PipelineLogger

 def Message processData(Message message) {
     def service = getPartnerDirectoryService()
     PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         def tracker = new HeaderStateTracker(message, logger)
         def headers = message.getHeaders()
         String stage = headers.get("tenantStage")
         boolean stageVirtual = headers.get("tenantStageVirtual") == "true"
         String partnerID = headers.get("partnerID")

         if (stageVirtual) logger.addEntry("determinePartnerID", "Virtual Stages are already enabled with header
 'tenantStageVirtual'.")

        // Partner ID and Tenant Stage can be known from Generic XI / IDoc Inbound Step01
        if(stage && partnerID) { // skip execution - required headers are known
                logger.addEntry("determinePartnerID", "Skipped execution of determinePartnerID script because
'tenantStage' and 'partnerID' headers are already known.")
                if(!stageVirtual) {
                        def listCurStage = stage.split(",")*.trim() ?: []
                        String landscapeValue = service.getParameter(System.getenv("TENANT_NAME"),
"SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
                        def listVirtual = landscapeValue.split(",")*.trim() ?: []
                        def merged = (listCurStage + listVirtual).unique()
                        if (merged.size() > 1) { // if multiple Virtual Stages - enable Virtual Stages logic
for Receiver
                                tracker.setHeader("tenantStageVirtual", "true")
                                String mergedValue = merged.join(",")
                                logger.addEntry("determinePartnerID", "Virtual Stages are enabled for the
tenant: '${mergedValue}'.")
                        }
                }
        } else { // execute script logic
                try {
                        // 1. Read Stage configuration
                        def tenantContext = determineTenantStage(message, service, logger, tracker)

                        // 2. Set the base for Sender Context
                        def senderContext = determineSenderContext(message, service, tenantContext, logger,
tracker)

                        // 3. Stage & System resolution (Verification): only for Virtual Stages
                        determineSenderStage(message, service, tenantContext, senderContext, logger, tracker)

                        // 4. Partner ID determination
                        determinePartnerId(message, service, senderContext, logger, tracker)

                } catch (Exception e) {
                        // ROLLBACK: Restore headers to original state before the script started
                        tracker.restoreOriginalState()

                        // Save Log and Rethrow
                        if (logger.getLog()) logger.saveToMessage(message)
                        throw e
                }
        }

    // 5. Finalize Log
    finalizeAuditLog(message, logger)

    return message
}

PartnerDirectoryService getPartnerDirectoryService() {
    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
    if (!service) throw new IllegalStateException("Partner Directory Service not found")
    return service
}

// --- 1. TENANT STAGE ---
Map determineTenantStage(Message message, PartnerDirectoryService service, PipelineLogger logger,
HeaderStateTracker tracker) {
    def headers = message.getHeaders()
    String stage = headers.get("tenantStage")
        String headerSender = headers.get("SAP_Sender")
        headerSender = determineSenderParty(headerSender) ? determineSenderParty(headerSender).sender :
headerSender
        String authPotSender = null
        boolean authSenderWithStage = false

    // 0) Check SAP_Sender header for landscapeStage if not yet found via Auth User
    if (!stage && headerSender) {
        stage = service.getParameter("landscapeStage", headerSender, String.class)
        if (stage) {
            logger.addEntry("determineTenantStage", "Stage '${stage}' assigned from Landscape Stage of Sender

System '${headerSender}' supplied with header.")
        }
    } else if (stage) {
        logger.addEntry("determineTenantStage", "Tenant Stage provided via incoming header: '${stage}'.")
    }

    // 1) Identity Determination via Auth User - it can be at Tenant Level or at Business System Level
    def auth = authenticatedUserDetermination(message, service)
    if (auth) {
        if (auth.location == "tenant") { // Tenant Stage by User (example: Landscape_Stage~DEV~PRJ)
            logger.addEntry("determineTenantStage", "Tenant Stage resolved via Authenticated User: '${auth.
pid}'.")
                        if (!stage) { // Tenant Stage header supplied from Step01 has priority
                stage = auth.pid
            } else {
                                if(stage != auth.pid) logger.addEntry("determineTenantStage", "WARNING: Tenant
Stage Mismatch: Known tenant stage value '${stage}', while Tenant Stage resolved via Auth User is '${auth.
pid}'.")
                                logger.addEntry("determineTenantStage", "Tenant Stage overridden with
'${stage}'.")
                        }
        } else { // BS does not have specific pattern/prefix in PD, so it can be differentiated only if
landscapeStage is configured
            if(!headerSender) {
                                authPotSender = auth.pid // It's a potential Business System PID
                                logger.addEntry("determineTenantStage", "Authenticated User is mapped to PID:
'${auth.pid}'.")

                                // If (optional) Stage is defined, then it is definitely BS
                                String confStage = service.getParameter("landscapeStage", auth.pid, String.
class)
                                if (confStage) {
                                        authSenderWithStage = true // if Stage assigned, then it is Business
System
                                        logger.addEntry("determineTenantStage", "Stage '${confStage}' assigned
from Landscape Stage of Business System '${authPotSender}' that is found using Authenticated User.")
                                        if(confStage.split(",")*.trim().size() > 1) { // validation that only
single stage is assigned for BS
                                                throw new IllegalStateException("Partner Directory
Configuration Error: Single Landscape Stage can be assigned to Business System: '${auth.pid}' have multiple
configured '${confStage}'.")
                                        }
                                        if (!stage) { // Known Tenant Stage header has priority (like supplied
from Step01)
                                                stage = confStage
                                        } else {
                                                if(stage != confStage) logger.addEntry("determineTenantStage",
"WARNING: Tenant Stage Mismatch: Known tenant stage value '${stage}', while Tenant Stage resolved via Landscape
Stage of Business System resolved via Auth User is '${auth.pid}'.")
                                                logger.addEntry("determineTenantStage", "WARNING: Sender System
Tenant Stage overridden with '${stage}'.")
                                        }
                                }
                        } else {
                                logger.addEntry("determineTenantStage", "Sender System from header
'${headerSender}'; Authenticated User is mapped to PID: '${auth.pid}' but this PID is ignored due to sender is
already known.")
                        }
        }
    } else {
                logger.addEntry("determineTenantStage", "Used Authenticated User is not mapped to any PID in
PD.")
    }

        // 2) Landscape Configuration
        String landscapeValue = service.getParameter(System.getenv("TENANT_NAME"),
"SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
    def listVirtual = landscapeValue.split(",")*.trim()
    boolean isVirtual = listVirtual.size() > 1
        String virtualStages = isVirtual ? landscapeValue : null
        String defaultStage = isVirtual ? null : listVirtual[0]

        if (isVirtual) logger.addEntry("determineTenantStage", "Virtual Stages are enabled for the tenant:
'${virtualStages}'.")

        // 3) Stage assignment for Non-Virtual case
        if(!stage && defaultStage) {
                stage = defaultStage
                logger.addEntry("determineTenantStage", "Single stage tenant configuration and set to value:
'${stage}'.")
        }

        // 4) Stage Promotion Logic
    if (stage && !(stage in listVirtual)) {
        isVirtual = true
        virtualStages = "${landscapeValue},${stage}" // Promotion to Virtual Context
        logger.addEntry("determineTenantStage", "Resolved Stage '${stage}' promoted to Virtual Context as it
was not in the list of tenant's virtual stages '${landscapeValue}'. Active Virtual Stages: '${virtualStages}'.")
    }

    if (isVirtual) tracker.setHeader("tenantStageVirtual", "true")
    if (stage) tracker.setHeader("tenantStage", stage)

    return [stage: stage, isVirtual: isVirtual, viStages: virtualStages, authSender: [hasStage :
authSenderWithStage, value : authPotSender]]
}

// --- 2. SENDER CONTEXT ---
Map determineSenderContext(Message message, PartnerDirectoryService service, Map ctxTenant, PipelineLogger
logger, HeaderStateTracker tracker) {
    def headers = message.getHeaders()
    String alias = headers.get("SAP_SenderAlias")
    String idocSndPrn = headers.get("SAP_IDoc_EDIDC_SNDPRN")  // Detect IDoc
    boolean isIdoc = idocSndPrn != null
    String party = null

        // 1) Resolve Sender
        def authInfo = ctxTenant.authSender
        String potSender = authInfo?.value
        boolean hasStage = authInfo?.hasStage ?: false
        // priority is header; then if Sender was found with User at determineTenantStage with Landscape Stage
- then Sender is known:
        String sender = headers.get("SAP_Sender") ?: (hasStage ? potSender : null)

    // 2) Party Determination (only for non-IDoc scenarios)
    if (!isIdoc && sender) {
        def partyResult = determineSenderParty(sender)
        sender = partyResult.sender
        party = partyResult.party
        if (party) {
                        tracker.setHeader("SAP_SenderParty", party)
                        message.setProperty("customHeader_SenderParty", party)
                        message.setProperty("customHeader_SenderSystem", sender)
                        logger.addEntry("determineSenderContext", "Party detected in SAP_Sender header:
'${party}' and '${sender}'.")
        }
    }

    // 3) Sender Business System Determination via Auth User
    if (!sender && potSender && !hasStage) { // Partner ID was found with User at determineTenantStage but
without Landscape Stage
        // Fetch ALL possible Aliases for the Business System across all stages/tracks
                def aliasMatches = findInPD(service, ctxTenant, "BusinessSystemName", potSender, false, true,
false) ?:
                       findInPD(service, ctxTenant, "LogicalSystemName", potSender, false, true, false)

                if (aliasMatches) {
            def uniqueAliases = aliasMatches.collect { it.value }.unique()
                        if (uniqueAliases.size() > 1) {
                                throw new IllegalStateException("Partner Directory Configuration Error:
Business System '${potSender}' is linked to multiple System Aliases ${uniqueAliases}. A Business System must
map to exactly one System Alias.")
            }

                        String potAlias = uniqueAliases[0]
                        sender = potSender
                        if (alias && alias != potAlias) {
                                throw new IllegalStateException("Sender Alias Determination Error: Alias
'${alias}' supplied in 'SAP_SenderAlias' header does not match Alias '${potAlias}' associated with Sender
System '${potSender}' resolved via Authenticated User Alias Determination. Authenticated User mapped to a
specific Business System cannot be used to authenticate other sender systems.")
                        } else {
                                alias = potAlias
                                logger.addEntry("determineSenderContext", "Sender Alias '${alias}' resolved via
Authenticated User associated with Business System.")
                        }
            logger.addEntry("determineSenderContext", "Sender '${sender}' resolved via Authenticated User
associated with Business System.")
        }
        }

        // 4) Sender Determination if Alias is present (optional - overall known alias is sufficient)
    if (alias && !sender) {
        def matches = findInPD(service, ctxTenant, "BusinessSystemName", alias, true, true, false) ?:
                      findInPD(service, ctxTenant, "LogicalSystemName", alias, true, true, false)
        if (matches) {
            sender = resolveSenderFromAliasMatches(matches, alias, ctxTenant, logger)
        }
    }

        // 5) Standard Alias Lookup (main logic if Auth User is not used)
    if (!alias) {
        String scheme = isIdoc ? "LogicalSystemName" : "BusinessSystemName"
        String pid = isIdoc ? idocSndPrn : sender
                if (!pid) { // At this time if no Alias - either Sender Business System or IDoc SNDPRN must be
known
                        throw new IllegalStateException("Mandatory sender identification (SAP_Sender or IDoc
SNDPRN) is missing.")
                }
                def aliasList = findInPD(service, ctxTenant, scheme, pid, false, true, false)
                if(aliasList) {
                        def uniqueList = aliasList.collect { it.value }.unique()
                        if (uniqueList.size() > 1) {
                                throw new IllegalStateException("Partner Directory Configuration Error:
Multiple System Aliases ${uniqueList} found for Scheme '${scheme}' and PID '${pid}'.")
                        }
                        alias = uniqueList[0]
                }
                if (alias) logger.addEntry("determineSenderContext", "Alias '${alias}' resolved via standard PD
lookup for Scheme '${scheme}' and PID '${pid}'.")
    }

    boolean isAliasFound = (alias != null) // set flag that Alias is found in Partner Directory (not a
fallback: alias = sender)

    // IDoc Mapping: set header SAP_Sender (map alias name to business system name otherwise keep logical
system name)
    if (isIdoc) {
        String idocBS = isAliasFound ? findInPD(service, ctxTenant, "BusinessSystemName", alias, true) : null
        String finalIdocSender = idocBS ?: idocSndPrn
                tracker.setHeader("SAP_Sender", finalIdocSender)
                logger.addEntry("determineSenderContext", "IDoc specific: Header SAP_Sender set to
'${finalIdocSender}'. Internal sender context remains '${sender}'.")
    }

    // Final Sync & Fallbacks
    alias = alias ?: (isIdoc ? idocSndPrn : sender)
    tracker.setHeader("SAP_SenderAlias", alias)
    message.setProperty("customHeader_SenderAlias", alias)
        if(!headers.get("SAP_Sender")) { // set SAP_Sender if missing
                if (sender) {
                        tracker.setHeader("SAP_Sender", sender)
                } else {
                        tracker.setHeader("SAP_Sender", alias)
                }

        }

    return [alias: alias, sender: sender, party: party, isAlias: isAliasFound]
}

// --- 3. SENDER STAGE VERIFICATION ---
void determineSenderStage(Message message, PartnerDirectoryService service, Map ctxTenant, Map ctxSender,
PipelineLogger logger, HeaderStateTracker tracker) {
    if (!ctxTenant.isVirtual) return // not relevant if Tenant does not have virtual stages

    String currentStage = ctxTenant.stage
    String alias = ctxSender.alias
    String sender = ctxSender.sender
    boolean isAliasFromPD = ctxSender.isAlias

        // --- Scenario 1: Tenant Stage is KNOWN ---
    if (currentStage) {
                // 1.a If No Alias from PD, then keep As-Is (no change of header tenantStage is required)
                // 1.b if Alias exists, then validate Alias against the known Stage:
        if (isAliasFromPD) { // search for BS by Alias and Stage
            String foundSender = findInPD(service, ctxTenant, "BusinessSystemName", alias, true, false, true)

            if (!foundSender) {
                                throw new IllegalStateException("Virtual Stage Routing Error: Sender System for
Alias '${alias}' not maintained for stage '${currentStage}'")
            }
            if (sender && foundSender != sender) {
                                throw new IllegalStateException("Virtual Stage Routing Error: Mismatch.
Configured Sender System '${foundSender}' in System Alias '${alias}' does not match actual sender '${sender}'
for tenant stage '${currentStage}'")
            } // else if(foundSender) && foundSender == sender, then expected normal path with correct and
verfied tenantStage
        }
    } else { // --- Scenario 2: Tenant Stage is UNKNOWN ---
        if (!isAliasFromPD) { // 2.a If No Alias from PD (like sender business component without System Alias):
keep stage ambiguous with possible virtual stages
            tracker.setHeader("tenantStage", ctxTenant.viStages)
            logger.addEntry("determineSenderStage", "Tenant Stage is unknown and no Sender System Alias found
for sender '${sender}. Set header for tenant stage to ambiguous value with possible virtual stages:
'${ctxTenant.viStages}'.")
        } else { // 2.b if Alias exists, then resolve Stage via Alias + Sender
            if (!sender) { // if no Sender, proceed with unknown Stage & Sender
                                 tracker.setHeader("tenantStage", ctxTenant.viStages)
                                 logger.addEntry("determineSenderStage", "For Alias '${alias}' sender system
'SAP_Sender' is unknown: Tenant Stage cannot be determined and header set to ambiguous value with possible
virtual stages: '${ctxTenant.viStages}'.")
                        } else { // Retrieve ALL matches across ALL virtual stages
                                def matches = findInPD(service, ctxTenant, "BusinessSystemName", alias, true,
true, false)
                                // Filter list to find matches where the found value equals the actual sender
                                def validMatches = matches?.findAll { it.value == sender }

                                if (!validMatches) {
                                        throw new IllegalStateException("Virtual Stage Routing Error: Sender
'${sender}' with System Alias '${alias}' not found in any virtual stage '${ctxTenant.viStages}'.")
                                }
                                if (validMatches.size() == 1) { // One unique occurrence - set tenant stage
from Sender System stage (expected path)
                                        tracker.setHeader("tenantStage", validMatches[0].stage)
                                        logger.addEntry("determineSenderStage", "Tenant Stage match found:
'${validMatches[0].stage}'")
                                } else { // Multiple matches found
                                        String foundInStages = validMatches.collect { "[${it.stage}]" }.join
(",")
                                        tracker.setHeader("tenantStage", foundInStages)
                                        logger.addEntry("determineSenderStage", "Ambiguity detected: Sender
'${sender}' with Alias '${alias}' matches multiple virtual stages: '${foundInStages}'. Tenant Stage is unknown
and allowed landscape: '${ctxTenant.viStages}'. Set tenant stage header to ambiguous value with matching
virtual stages: '${foundInStages}'.")
                                }
                        }

        }
    }
}

// --- 4. PARTNER ID ---
void determinePartnerId(Message message, PartnerDirectoryService service, Map ctxSender, PipelineLogger logger,
HeaderStateTracker tracker) {
    def headers = message.getHeaders()
        def properties = message.getProperties()

    if (headers.get("partnerID")) return

    def messageLog = messageLogFactory.getMessageLog(message)
    String Stage = headers.get("tenantStage")
    if (Stage && Stage.split(",")*.trim().size() == 1) messageLog?.addCustomHeaderProperty("tenantStage", Stage)

    String agency = ctxSender.party ? "${ctxSender.party}.${ctxSender.alias}" : ctxSender.alias
    String iface = headers.get("SAP_SenderInterface")
    String ns = headers.get("SAP_SenderInterfaceNamespace")
    String pid = null
        // Zero attempt: Determine partner ID via alternative partner based on sender service, sender interface
and interface namespace
    if (iface && ns) pid = service.getPartnerId(agency, iface, ns)
        if (pid) logger.addEntry("determinePartnerId", "Partner ID '${pid}' determined from: agency
'${agency}', interface '${iface}', namespace '${ns}'.")
        // First attempt: Determine partner ID via alternative partner if maintained
    if (!pid && iface) {
                pid = service.getPartnerId(agency, "SenderInterface", iface)
                if (pid) logger.addEntry("determinePartnerId", "Partner ID '${pid}' determined from: agency
'${agency}' and sender interface '${iface}'.")
        }
    // Second attempt: Otherwise use a combination of sender component and sender interface unless you switched
off the tilde option
    if (!pid) {
        if (properties.get("CustomXPid_SwitchOffTilde") == "true") {
                        throw new IllegalStateException("Partner ID Missing Error: No matching Partner ID found
in Partner Directory: " +
                                        "agency = '${agency ?: 'n/a'}', " +
                                        "interface = '${iface ?: 'n/a'}', " +
                                        "namespace = '${ns ?: 'n/a'}'")
                }
        pid = agency + "~" + (iface ?: "Unknown").replaceAll("[&%/]", "_")
        logger.addEntry("determinePartnerId", "Partner ID '${pid}' determined with Tilde PID determination
option.")
    }

    tracker.setHeader("partnerID", pid)
    message.setProperty("customHeader_partnerID", pid)
}

// --- 5. PIPELINE AUDIT LOG ---
void finalizeAuditLog(Message message, PipelineLogger logger) {
    def h = message.getHeaders()
        def p = message.getProperties()
    logger.addEntry("determinePartnerID_FinalHeaders", "tenantStage=${h.tenantStage}, tenantStageVirtual=${h.
tenantStageVirtual}, SAP_Sender=${h.SAP_Sender}, SAP_SenderAlias=${h.SAP_SenderAlias}, partnerID=${h.
partnerID}")
        logger.addEntry("determinePartnerID_Additional", "Headers: SAP_SenderParty=${h.SAP_SenderParty ?: 'n
/a'}, SAP_SenderInterface=${h.SAP_SenderInterface ?: 'n/a'}, SAP_ApplicationID=${h.SAP_ApplicationID ?: 'n/a'},
testMode=${h.testMode ?: 'n/a'}, Property CustomXError_Enabled=${p.CustomXError_Enabled ?: 'n/a'}")
    if (logger.getLog()) logger.saveToMessage(message)
}
/**
* --- HELPERS ---
* 5 helpers are used: determineSenderParty, authenticatedUserDetermination, findInPD,
resolveSenderFromAliasMatches, HeaderStateTracker
*/
// Check and Exract Sender Party
Map determineSenderParty(String senderHeader) {
    String party = null
    String sender = senderHeader

    if (senderHeader?.contains(".")) {
        def kv = senderHeader.split(/\./, 2)
        party = kv[0]?.trim()
        sender = kv[1]?.trim()
    }
    return [sender: sender, party: party]
}

// Check if AuthenticatedUser (header SapAuthenticatedUserName) is maintained in Partner Directory
Map authenticatedUserDetermination(Message message, PartnerDirectoryService service) {
    String user = message.getHeaders().get("SapAuthenticatedUserName")
    if (!user) return null
    String partnerId = service.getPartnerIdOfAuthorizedUser(user)
    if (!partnerId) return null

    String prefTenant = "Landscape_Stage~"
        String prefTpm = "SAP_TPM"

    if (partnerId.startsWith(prefTenant)) {
        return [pid: partnerId.substring(prefTenant.length()), location: "tenant"]
    }
    if (partnerId.startsWith(prefTpm)) return null // Ignore TPM

    return [pid: partnerId, location: "other"]
}

/**
 * Helper to find a value in PD by checking all valid virtual stages.
 * isAlt = true (getAlternativePartnerId: Scheme + Alias -> Bus.System)
 * isAlt = false (getPartnerId: Scheme + Bus.System -> Alias)
 * fetchAll = true (search through all possible combinations and return a list [value, stage])
 * fetchAll = false (exit fucntion at first found entry in PD)
 * perStage = true (search only by ctxTenant.stage)
 * perStage = false (search by all virtual stages)
 */
def findInPD(PartnerDirectoryService service, Map ctxTenant, String scheme, String value, boolean isAlt =
false, boolean fetchAll = false, boolean perStage = false) {
    // 1) Determine the exact stages to search from the landscape
        def searchStages = perStage ? [ctxTenant.stage] : (ctxTenant.isVirtual ? (ctxTenant.viStages?.split(",")
*.trim() ?: []) : [ctxTenant.stage])
    def results = []
    for (stage in searchStages.unique()) { // 2) Iterate through unique stages only
        if (!stage) continue
        String found = isAlt ? service.getAlternativePartnerId(stage, scheme, value) : service.getPartnerId
(stage, scheme, value)
        if (found) {
            if (!fetchAll) return found // Exit early for single match
            results << [value: found, stage: stage]
        }
    }
    return fetchAll ? results : null
}

/**
 * Helper to resolve a technical Sender System from a list of Alias matches in PD.
 * Handles stage-based resolution, and ambiguous logging.
 */
String resolveSenderFromAliasMatches(List matches, String alias, Map ctxTenant, PipelineLogger logger) {
    def uniqueSenders = matches.collect { it.value }.unique()
    String senderList = uniqueSenders.join(", ")
    String resolvedSender = null

    if (uniqueSenders.size() == 1) { // 1. Unique Match Case
        String potSender = uniqueSenders[0]
        resolvedSender = potSender
        logger.addEntry("determineSenderContext", "Sender '${resolvedSender}' resolved as unique match for
Alias '${alias}'.")
    } else {
        // 2. Try Stage-based resolution
        if (!resolvedSender && ctxTenant.stage) {
            def match = matches.find { it.stage == ctxTenant.stage }

             if (match) {
                 resolvedSender = match.value
                 logger.addEntry("determineSenderContext", "Sender '${resolvedSender}' resolved via Stage Match
 (${match.stage}) for Alias '${alias}'.")
             }
         }
         // 3. If still not resolved, keep sender unknown
         if (!resolvedSender) {
             logger.addEntry("determineSenderContext", "System Alias '${alias}' has multiple possible Sender
 Systems: '${senderList}'. Specific system could not be determined for the current landscape context as Tenant
 Stage is unknown '${ctxTenant.viStages}'.")
         }
     }
     return resolvedSender
 }
 /**
  * Helper to restore original state of headers before throwing an exception.
  * This prevents "distortion" of message headers in case of moving to DLQ due to configuration mistakes.
  */
 class HeaderStateTracker {
     private Map originalState = [:]
     private Set modifiedKeys = []
     private def message
     private def logger

     HeaderStateTracker(def message, def logger) {
         this.message = message
         this.logger = logger
     }

     // Sets a header and tracks the original value if not already tracked.
     void setHeader(String name, Object value) {
         if (!originalState.containsKey(name)) {
             // Store the true original value (even if null)
             originalState[name] = message.getHeaders().get(name) ?: ""
         }
         modifiedKeys.add(name)
         message.setHeader(name, value)
     }

     // Reverts all modified headers to their state prior to this script's execution.
     void restoreOriginalState() {
         originalState.each { name, oldValue ->
             message.setHeader(name, oldValue)
         }
         logger.addEntry("HeaderStateTracker", "Rollback performed: Reverted headers ${modifiedKeys} to original
 values.")
     }
 }
```

**Called in:** Determine partner ID (main flow, step 4). **Logic (simplified):** Determines the partner ID = identifier of the configuration scenario in the Partner Directory. Procedure:

1. If the headers tenantStage and partnerID are already known, the script is skipped (it only possibly activates the virtual-stages logic for the receiver).
2. Tenant stage: determined from the authorized user (PD partner Landscape_Stage~…), from the landscapeStage parameter of the business system, from the header, or from the tenant → stages mapping (partner SAP_Integration_Suite_Landscape). Supports virtual stages (multiple stages per tenant), including the "promotion" of a stage.
3. Sender context: distinguishes IDoc from XI (header SAP_IDoc_EDIDC_SNDPRN), determines the sender party and the business system, and possibly looks up aliases via getAlternativePartnerId (scheme BusinessSystemName).
4. Partner ID: builds/looks up the partner ID; the behavior is influenced by the parameter CustomXPid_SwitchOffTilde. On error it returns the headers to their original state (rollback) and rethrows the exception.

**Input:** headers SAP_Sender, SAP_SenderInterface, tenantStage, authorized user; PD partners Landscape_Stage~*, SAP_Integration_Suite_Landscape → **Output:** headers partnerID, tenantStage, possibly tenantStageVirtual, SAP_SenderParty; properties customHeader_SenderParty/_SenderSystem. **Note:** Contains the logic of flexible landscape stages (from version 1.0.6) — when documenting further versions of the package, watch for changes right here.

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

**Called in:** Check scenario-specific exit (local process Call custom exits). **Logic:** Determines the pipeline step number from camelId (substring after generic.step, here 01) if it is not in the header customXPipelineStep. From the prefix ${property.CustomXType} (here CustomXPre) it reads the settings …Enabled and …Endpoint — first from the headers, then from the Partner Directory by partnerID. Enabled is a list of steps separated by | (e.g. 01|03); if it contains the current step, it sets CustomXBoolean=true. **Input:** headers partnerID, customXPipelineStep; property CustomXType; PD parameters CustomXPreEnabled, CustomXPreEndpoint → **Output:** properties CustomXBoolean, CustomXEndpoint

### readInboundQueueFromPD.groovy

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

     // read the inbound queue from the Partner Directory
     def inbQueue = service.getParameter("InboundQueue", Pid , String.class);

     // if the inbound queue exists, create a new exchange property containing the queue name
     // otherwise, the exchange property is not created
     message.setProperty("inbQueue", inbQueue ?: null);

         // adding information to audit log using helper PipelineLogger class
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         logger.addEntry("readInboundQueueFromPD", "Partner ID to read the inbound queue:" + (Pid ?: "n/a") +
 ".")
         logger.addEntry("readInboundQueueFromPD", "Inbound queue from the Partner Directory: property
 inbQueue=" + (message.properties.inbQueue.toString() ?: "n/a") + ".")
         if (logger.getLog()) {
             logger.saveToMessage(message)
         }

         //PipelineLogger logger = PipelineLogger.newLogger(message)
         //def logEntry = new StringBuilder().append("Partner ID to read the inbound queue: ").append(Pid ?: "n
 /a").toString();
         //logger.addEntry("readInboundQueueFromPD", logEntry);
         //logEntry = new StringBuilder().append("Inbound queue from the Partner Directory: property inbQueue=").
 append(message.properties.inbQueue.toString() ?: "n/a").toString();
         //logger.addEntry("readInboundQueueFromPD", logEntry);
         //if (logger.getAuditLog()) {
         //        message.setHeader('auditLogHeader', logger.getAuditLog())
         //}

     return message;
 }
```

**Called in:** Read inbound queue (EO branch). **Logic:** By partnerID it reads the string parameter InboundQueue from the Partner Directory. If it exists, it sets the property inbQueue (the router "Which queue?" then sends the message to this queue instead of the default PIPX01). Without partnerID it throws IllegalStateException. **Input:** header partnerID; PD parameter InboundQueue → **Output:** property inbQueue

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

**Called in:** Add custom header properties (2×: EO branch before JMS, BE branch before the bypass router). **Logic:** Builds custom header properties for MPL monitoring from two sources: (a) the header customHeaderProperties — a string key:value|key:value or Base64-encoded JSON (decoded by PipelineHelper), (b) exchange properties with the prefix customHeader_* (including unpacking of NodeLists from XPath). The result is written to the MPL custom headers, so that e.g. QualityOfService, SenderParty, ReceiverAlias are visible in monitoring. **Input:** header customHeaderProperties, properties customHeader_* → **Output:** MPL custom header properties

### readBypassOptionFromPD.groovy

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
     String id;
     if (Pid == null){
         throw new IllegalStateException("Partner ID not found in sent message");
     }

     // read receiverDetermination string parameter from the Partner Directory if any
     def receiverName = service.getParameter("receiverDetermination", Pid , String.class);

     // assign the value to the header SAP_ReceiverAlias
     message.setHeader("SAP_ReceiverAlias", receiverName ?: null);

     // read interfaceDetermination string parameter from the Partner Directory if any
     if (receiverName != null) {
         id = "interfaceDetermination_" + receiverName;
         def outboundEndpoint = service.getParameter(id, Pid , String.class);

         // assign the value to the header SAP_OutboundProcessingEndpoint
         message.setHeader("SAP_OutboundProcessingEndpoint", outboundEndpoint ?: null);

         // if the value exists, receiver and interface determination should be bypassed otherwise receiver
 determination only
         message.setProperty("bypassOption", outboundEndpoint ? 'p2p' : 'skipRcvDet');

     }

         // adding information to audit log using helper PipelineLogger class
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         logger.addEntry("readBypassOptionFromPD", "Partner ID to read the bypass option if any: " + (Pid ?: "n
 /a") + ".")
         logger.addEntry("readBypassOptionFromPD", "Bypass option: property bypassOption=" + (message.properties.
 bypassOption.toString() ?: "n/a") + ".")
         logger.addEntry("readBypassOptionFromPDe", "Header SAP_ReceiverAlias=" + (message.headers.
 SAP_ReceiverAlias.toString() ?: "n/a") + ".")

         logger.addEntry("readBypassOptionFromPDe", "Header SAP_OutboundProcessingEndpoint=" + (message.headers.
 SAP_OutboundProcessingEndpoint.toString() ?: "n/a") + ".")
         if (logger.getLog()) {
             logger.saveToMessage(message)
         }

         //PipelineLogger logger = PipelineLogger.newLogger(message)
         //def logEntry = new StringBuilder().append("Partner ID to read the bypass option if any: ").append(Pid
 ?: "n/a").toString();
         //logger.addEntry("readBypassOptionFromPD", logEntry);
         //logEntry = new StringBuilder().append("Bypass option: property bypassOption=").append(message.
 properties.bypassOption.toString() ?: "n/a").toString();
         //logger.addEntry("readBypassOptionFromPD", logEntry);
         //logEntry = new StringBuilder().append("Header SAP_ReceiverAlias=").append(message.headers.
 SAP_ReceiverAlias.toString() ?: "n/a").toString();
         //logger.addEntry("readBypassOptionFromPD", logEntry);
         //logEntry = new StringBuilder().append("Header SAP_OutboundProcessingEndpoint=").append(message.
 headers.SAP_OutboundProcessingEndpoint.toString() ?: "n/a").toString();
         //logger.addEntry("readBypassOptionFromPD", logEntry);
         //if (logger.getAuditLog()) {
         //        message.setHeader('auditLogHeader', logger.getAuditLog())
         //}

     return message;
 }
```

**Called in:** Read bypass option (BE branch). **Logic:** By partnerID it reads the parameter receiverDetermination from the Partner Directory → header SAP_ReceiverAlias. If it exists, it further reads interfaceDetermination_<receiver> → header SAP_OutboundProcessingEndpoint. It then sets the property bypassOption to p2p (the endpoint exists → receiver and interface determination are skipped) or skipRcvDet (receiver determination only — not supported for XI sync, leads to an exception). If receiverDetermination is not in the PD, bypassOption is not set → default branch CBR (full sync runtime). **Input:** header partnerID; PD parameters receiverDetermination, interfaceDetermination_<receiver> → **Output:** headers SAP_ReceiverAlias, SAP_OutboundProcessingEndpoint; property bypassOption

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

**Called in:** Map to Business Name (BE branch → p2p). **Logic:** From SAP_ReceiverAlias it separates a possible party (party.service). Via getAlternativePartnerId(stage, 'BusinessSystemName', alias) it translates the alias to the actual business system for the given tenantStage (default PRD). The result goes to SAP_Receiver; if it differs from the alias or a party exists, it adds custom header properties (ReceiverAlias, ReceiverParty, ReceiverSystem). **Input:** headers SAP_ReceiverAlias, tenantStage; PD alternative partner IDs → **Output:** headers SAP_Receiver, SAP_ReceiverParty, customHeaderProperties

### throwException.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message
 import java.io.IOException

 def Message processData(Message message) {
     def properties = message.getProperties()
     String exceptionMessage = properties.get("exceptionMessage")
     String exceptionType = properties.get("exceptionType") ?: "Exception"
         String location = properties.get("location") ?: "Script Throwing an Exception"

     if (!exceptionMessage) {
         // default: take body as message
                 exceptionMessage = "Throw an exception with message body: ${message.getBody(String) ?:
 "Unexpected error"}"
     }
         exceptionMessage = "${exceptionMessage} at ${location}."

     def ex
     switch (exceptionType) {
         case "IllegalArgumentException":
             ex = new IllegalArgumentException(exceptionMessage)
             break
         case "RuntimeException":
             ex = new RuntimeException(exceptionMessage)
             break
         case "IOException":
             ex = new IOException(exceptionMessage)
             break
         case "NullPointerException":
             ex = new NullPointerException(exceptionMessage)
             break
         default:
             ex = new Exception(exceptionMessage)
     }

     throw ex
 }
```

**Called in:** Throw exception (3×: EOIO, empty/unknown QoS, skipRcvDet). **Logic:** Throws an exception of the type given by the property exceptionType (default Exception; supports IllegalArgument/Runtime/IO/NullPointer) with the message from the property exceptionMessage (set by the preceding Content Modifier "Set custom status"), extended with location. This activates the Exception Subprocess. **Input:** properties exceptionMessage, exceptionType, location → **Output:** exception

## Libraries and utilities

- PipelineLogger.groovy (74 lines) — class for the structured audit log; scripts use it to add entries (addEntry) and save them to the message (saveToMessage, header auditLogHeader). Imported by 8 scripts.
- PipelineHelper.groovy (59 lines) — static helpers: detection/decoding of Base64+JSON (decodeIfBase64), validation of key:value strings (isValidKeyValueString). Used by addCustomHeaderProperties.
- auditLogHandler.groovy (42 lines), logMessage.groovy (67 lines) — utilities, in this flow not bound to any step.
