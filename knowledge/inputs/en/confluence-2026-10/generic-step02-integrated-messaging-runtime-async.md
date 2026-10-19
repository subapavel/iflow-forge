<!-- Translated from Confluence page "Generic Step02 – Integrated Messaging Runtime Async" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Step02 – Integrated Messaging Runtime Async

Version: 1.0.5 · Technical name: PIP_02_Generic_IntegratedMessagingAsync · Package: Cloud Integration Pipeline – Generic Pipelines

> Integrated runtime — this is what we use. This flow is the core of the asynchronous integrated runtime; it replaces the separate Step02 + Step04 + Step05 from the fully decoupled pipeline.

## Purpose

The core of the asynchronous integrated messaging runtime — it combines into a single step what the fully decoupled pipeline does in three (Step02 + Step04 + Step05): inbound conversion, combined determination of receivers and interfaces by a single XSLT mapping, and distribution to outbound queues. This reduces the number of JMS queues required (PIPX01 input, PIPQ04 output). Since version 1.0.4 it supports stage rules and virtual stages, including duplicating the message to multiple receiver stages.

## Queues and adapters

| Adapter | Queue / endpoint | Role |
|---|---|---|
| JMS Sender | {{PipelineJMSQueuePrefix}}X01 = PIPX01 | input (from Step01) |
| JMS Receiver | PIPQ04 | output → Step06 |
| JMS Receiver | ${property.receiverSpecificQueueName} | receiver-specific output |
| JMS Receiver | PIPX01_DLQ | dead letter queue |
| ProcessDirect | ${property.inbConvEndpoint} | scenario-specific inbound conversion |
| ProcessDirect | ${property.customXRDEndpoint} | custom receiver determination flow |
| ProcessDirect | custom exits, custom error flow | shared patterns |

## Main flow

1. Set custom extensions (SapQualityOfService = ExactlyOnce, SAP_MessageType = SAP_SenderInterface) → Check well formed XML → Store original payload (storeOriginalPayloadInXProperty.groovy → property originalPayload — passed to the custom error flow, fix 1.0.2)
2. Determine partner ID → Read retry handling → router "Max # of retries reached?": SAPJMSRetries > maxJMSRetries → MaxRetriesExceeded → error (retry handling then sends it to the DLQ)
3. Initialize pre-processing exit → Call custom exit → Add custom header properties
4. Call inbound conversion (local process): readInboundConversionFromPD.groovy → optionally Request Reply to ${property.inbConvEndpoint} → Check well formed XML → Store converted payload (property payload)
5. Read bypass option → router "Bypass option?":
   - Default Route → Determine receivers & interfaces (see below) → router "Any Receiver?":
     - no receiver → router "Not found type?" by ns0:Receivers/ReceiverNotDetermined/Type:
       - Default → substitute receiver from the mapping (status ReceiverNotFoundDefault)
       - Ignore → status ReceiverNotFoundIgnored, end
       - Completed / Error → status ReceiverNotFoundError → Error End
     - otherwise → Split receivers (iterating splitter on /Receiver)
   - p2p → straight to stage routing (receiver from PD, skipping the determination)
   - skipRcvDet → [FULLY DECOUPLED PIPELINE ONLY] — not supported in integrated → ConfigurationOptionNotSupported → exception
6. Stage routing (per receiver) – router "Virtual Stage Routing?" (tenantStageVirtual = true from determinePartnerID):
   - virtual stages → Map to Receiver Stage (mapSenderStageToReceiverStage.groovy: determines the receiver's target stage(s) according to the stage rules in the PD) → router "Multiple Stages?": multiple stages → Split Receiver Stages (the message is duplicated for each stage; tenantStage per copy) → Restore payload
   - without virtual stages → straight to Map to Business Name
7. Map to Business Name → Get Receiver specific queue from PD → router "Any Interface?": no interface → InterfaceNotFoundError → Error; otherwise Split interfaces (on /Interface)
8. Per interface: Get interface (SAP_ReceiverInterface, SAP_ReceiverInterfaceIndex, SAP_OutboundProcessingEndpoint = /Interface/Service; bypassOption = skipIfDet) → Log outbound processing endpoint → Get payload → Add Custom Header → router "Receiver specific?" → JMS ${receiverSpecificQueueName} or PIPQ04

## Local process: Determine receivers & interfaces

1. Read custom receiver determination – readCustomReceiverDeterminationFromPD.groovy: PD CustomXRDEndpoint → customXRDBoolean; PD ReuseXRDEndpoint → reuseXRDBoolean
2. Router "Custom RD?": custom → Request Reply to ${property.customXRDEndpoint} (the custom determination flow returns Receivers+Interfaces XML); reuse XRD → not supported in the integrated runtime (ConfigurationOptionNotSupported) — [FULLY DECOUPLED PIPELINE ONLY]; default → XSLT from Partner Directory: binary parameter receiverDetermination of partner ${header.partnerID} (combined mapping: returns receivers and their interfaces at once). Non-XML payload → the mapping runs over <dummy/>.

## Error handling

Shared pattern (Standard Retry handling with DLQ PIPX01_DLQ / custom error handling with the original payload via fetchOriginalPayloadFromXProperty.groovy). Deviations: errors from determinePartnerID.groovy, MaxRetriesExceeded and *OptionNotSupported go directly to the DLQ; since version 1.0.4 an invalid configuration ends with an Error End (previously Escalation).

## Scripts (new compared to those documented earlier)

| Script | Purpose |
|---|---|
| mapSenderStageToReceiverStage.groovy (1.0.4) | Stage rules: determines the receiver's target stage(s) from the PD (SAP_Integration_Suite_Landscape, parameters stageRule*); handles 1→1 and 1→N (duplication) |
| readCustomReceiverDeterminationFromPD.groovy | PD CustomXRDEndpoint / ReuseXRDEndpoint → properties customXRD*, reuseXRD* |
| storeOriginalPayloadInXProperty.groovy / fetchOriginalPayloadFromXProperty.groovy | Backup/restore of the original (non-converted) payload for the custom error flow |
| logOutboundProcessingEndpoint.groovy | Audit log: bypassOption + resulting SAP_OutboundProcessingEndpoint |

## Script details

### storeOriginalPayloadInXProperty.groovy

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

Logic: Backs up the original (non-converted) payload to the property originalPayload; non-XML is Base64-encoded. It is passed to the custom error flow (fix 1.0.2) so that the flow has the input message available before conversion. Input: body, header isWellFormedXml → Output: property originalPayload

### determinePartnerID.groovy

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

Logic: Determines the partner ID (identifier of the configuration scenario in the PD): tenant stage (authenticated user / landscapeStage / tenant mapping via SAP_Integration_Suite_Landscape, incl. virtual stages) → sender context (business system via getAlternativePartnerId, scheme BusinessSystemName) → assembly of the partner ID (behaviour controlled by CustomXPid_SwitchOffTilde). On error it returns the headers to their original state and rethrows the exception. Input: headers SAP_Sender, SAP_SenderInterface, tenantStage; PD partners Landscape_Stage~*, SAP_Integration_Suite_Landscape → Output: headers partnerID, tenantStage

### readRetryHandlingFromPD.groovy

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

    // read retry handling from header if already defined as header
    def maxJMSRetries = headers.get("maxJMSRetries");

    // otherwise, read retry handling from the Partner Directory
    if (maxJMSRetries == null){
        maxJMSRetries = service.getParameter("MaxJMSRetries", Pid , String.class);
    // if the value exists in the Partner Directory, create a new header holding the max number of retries
    // otherwise, set the header to default which is 5
        message.setHeader("maxJMSRetries", maxJMSRetries ? maxJMSRetries.toInteger() : 5);
    }

        // adding information to audit log using helper PipelineLogger class
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        logger.addEntry("readRetryHandlingFromPD", "Partner ID to read the retry handling: " + (Pid ?: "n/a") +
".")
        logger.addEntry("readRetryHandlingFromPD", "Maximum number of retries from the Partner Directory:
header maxJMSRetries=" + (message.headers.maxJMSRetries.toString() ?: "n/a") + ".")
        if (logger.getLog()) {
            logger.saveToMessage(message)
        }

        //PipelineLogger logger = PipelineLogger.newLogger(message)
        //def logEntry = new StringBuilder().append("Partner ID to read the retry handling: ").append(Pid ?: "n
/a").toString();
        //logger.addEntry("readRetryHandlingFromPD", logEntry);
        //logEntry = new StringBuilder().append("Maximum number of retries from the Partner Directory: header
maxJMSRetries=").append(message.headers.maxJMSRetries.toString() ?: "n/a").toString();
        //logger.addEntry("readRetryHandlingFromPD", logEntry);
        //if (logger.getAuditLog()) {
        //        message.setHeader('auditLogHeader', logger.getAuditLog())
        //}

    return message;
}
```

Logic: If the header maxJMSRetries is not present, reads PD MaxJMSRetries by partnerID → header maxJMSRetries (default 5). Input: partnerID; PD MaxJMSRetries → Output: header maxJMSRetries

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

Logic: Determines the step number from camelId; reads CustomXPreEnabled/ CustomXPreEndpoint from the headers or from the PD by partnerID (Enabled = |-separated list of steps). Input: partnerID, property CustomXType → Output: properties CustomXBoolean, CustomXEndpoint

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

Logic: MPL custom headers from two sources: the header customHeaderProperties (k:v|k:v or Base64 JSON) and the properties customHeader_* (incl. NodeLists). Input: customHeaderProperties, properties customHeader_* → Output: MPL custom headers

### readInboundConversionFromPD.groovy

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
    def SenderAlias = headers.get("SAP_SenderAlias");
    def Pid = headers.get("partnerID");
    if (Pid == null){
        throw new IllegalStateException("Partner ID not found in sent message");
    }

    // First, read the inbound conversion end point from the corresponding custom header if applicable
    def inbConvEndpoint = headers.get("customXInboundConversionEndpoint");

    // Secondly, check if sender-specific inbound conversion is maintained in the Partner Directory
    if (inbConvEndpoint == null){
        inbConvEndpoint = service.getParameter("InboundConversionEndpoint_" + SenderAlias, Pid , String.class);
    }

    // Otherwise, read the inbound conversion end point InboundConversionEndpoint from the Partner Directory
    if (inbConvEndpoint == null){
        inbConvEndpoint = service.getParameter("InboundConversionEndpoint", Pid , String.class);
    }

    // if the inbound conversion end point exists, create a new exchange property containing the end point
    // otherwise, the exchange property is not created
    message.setProperty("inbConvEndpoint", inbConvEndpoint ?: null);

    // create a new exchange property with value true or false depending on whether the end point exists
    message.setProperty("inbConvBoolean", inbConvEndpoint ? 'true' : 'false');

        // adding information to audit log using helper PipelineLogger class
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        logger.addEntry("readInboundConversionFromPD", "Partner ID to read the inbound conversion: " + (Pid ?:
"n/a") + ".")
        logger.addEntry("readInboundConversionFromPD", "Inbound conversion end point from the Partner
Directory: property inbConvEndpoint=" + (message.properties.inbConvEndpoint.toString() ?: "n/a") + ".")
        logger.addEntry("readInboundConversionFromPD", "Inbound conversion existence from the Partner
Directory: property inbConvBoolean=" + (message.properties.inbConvBoolean.toString() ?: "n/a") + ".")
        if (logger.getLog()) {
            logger.saveToMessage(message)
        }

        //PipelineLogger logger = PipelineLogger.newLogger(message)
        //def logEntry = new StringBuilder().append("Partner ID to read the inbound conversion: ").append(Pid
?: "n/a").toString();
        //logger.addEntry("readInboundConversionFromPD", logEntry);
        //logEntry = new StringBuilder().append("Inbound conversion end point from the Partner Directory:
property inbConvEndpoint=").append(message.properties.inbConvEndpoint.toString() ?: "n/a").toString();
        //logger.addEntry("readInboundConversionFromPD", logEntry);
        //logEntry = new StringBuilder().append("Inbound conversion existence from the Partner Directory:
property inbConvBoolean=").append(message.properties.inbConvBoolean.toString() ?: "n/a").toString();
        //logger.addEntry("readInboundConversionFromPD", logEntry);
        //if (logger.getAuditLog()) {
        //        message.setHeader('auditLogHeader', logger.getAuditLog())
        //}

    return message;
}
```

Logic: Determines the endpoint of the scenario-specific inbound conversion in this order: header customXInboundConversionEndpoint → PD InboundConversionEndpoint_<SAP_SenderAlias> (sender-specific) by partnerID → PD InboundConversionEndpoint. The result goes to the property inbConvEndpoint + the flag inbConvBoolean. Input: partnerID, SAP_SenderAlias; PD parameters → Output: properties inbConvEndpoint, inbConvBoolean

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

Logic: Parses the body as XML (XmlSlurper) → header isWellFormedXml (true/ false); controls how the payload is backed up (text/Base64) and whether the mapping runs over <dummy/>. Input: body → Output: header isWellFormedXml

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

Logic: By partnerID reads the PD string receiverDetermination → SAP_ReceiverAlias. If it exists, reads interfaceDetermination_<receiver> → SAP_OutboundProcessingEndpoint and sets bypassOption = p2p (endpoint exists) or skipRcvDet (receiver only). When receiverDetermination is not in the PD, bypassOption is not set → default (full determination). Input: partnerID; PD receiverDetermination, interfaceDetermination_<receiver> → Output: headers SAP_ReceiverAlias, SAP_OutboundProcessingEndpoint; property bypassOption

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

Logic: By partnerID determines custom/reuse receiver determination: first the header customXRDEndpoint, otherwise PD CustomXRDEndpoint → property customXRDBoolean. If custom is not present, reads PD ReuseXRDEndpoint → reuseXRDBoolean. The resulting endpoint goes to customXRDEndpoint. (Without partnerID or the PD service → exception.) Input: partnerID; PD CustomXRDEndpoint, ReuseXRDEndpoint → Output: properties customXRDBoolean, reuseXRDBoolean, customXRDEndpoint

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

Logic: Stage rules engine. (1) Loads the context: tenantStage (multiple values/empty → "unknown stage"), TENANT_NAME (env), virtual stages from PD SAP_Integration_Suite_Landscape, SAP_ReceiverAlias, partnerID, QoS. (2) Determines in which stages the receiver exists (getAlternativePartnerId(stage, 'BusinessSystemName', alias)). (3) Determines the target stage by the priority of the stageRule_* parameters (scenario ID receiver-specific → scenario ID for all → receiver alias; for known and unknown tenant stage alike, plus exact match and tenant fallback). The rule >Stage1,Stage2 can target multiple stages. (4) One stage → header tenantStage; multiple stages → builds a multi-Receiver XML (message duplication, async only); for Best Effort (sync), multiple stages → exception. Without an alias in the PD → noReceiverStageBoolean. Input: headers tenantStage, SAP_ReceiverAlias, partnerID; TENANT_NAME; PD SAP_Integration_Suite_Landscape, stageRule_* → Output: header tenantStage / multi-Receiver XML / properties noReceiverStageBoolean, customHeader_tenantStage

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

Logic: Separates the party from SAP_ReceiverAlias (party.system) → SAP_ReceiverParty + SAP_ReceiverAlias (without the party). Via getAlternativePartnerId(tenantStage ?: 'PRD', 'BusinessSystemName', alias) translates the alias into the actual business system → SAP_Receiver (if there is no mapping, the alias remains). When the receiver differs from the alias / a party exists, it adds ReceiverAlias/ ReceiverParty/ ReceiverSystem to customHeaderProperties. Input: SAP_ReceiverAlias, tenantStage; PD alternative partner IDs → Output: SAP_Receiver, SAP_ReceiverParty, customHeaderProperties

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

Logic: Determines the receiver-specific queue: PD ReceiverSpecificQueue_<SAP_ReceiverAlias> by partnerID, otherwise ReceiverSpecificQueue by SAP_ReceiverAlias. The result goes to receiverSpecificQueueName + receiverSpecificQueueBoolean. Input: partnerID, SAP_ReceiverAlias; PD parameters → Output: properties receiverSpecificQueueName, receiverSpecificQueueBoolean

### logOutboundProcessingEndpoint.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message;
import src.main.resources.script.PipelineLogger;

def Message processData(Message message) {

        // adding information to audit log using helper PipelineLogger class
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        logger.addEntry("logOutboundProcessingEndpoint", "Bypass option: property bypassOption=" + (message.
properties.bypassOption.toString() ?: "n/a") + ".")
        logger.addEntry("logOutboundProcessingEndpoint", "Header SAP_OutboundProcessingEndpoint=" + (message.
headers.SAP_OutboundProcessingEndpoint.toString() ?: "n/a") + ".")
        if (logger.getLog()) {
            logger.saveToMessage(message)
        }

        //PipelineLogger logger = PipelineLogger.newLogger(message)
        //def logEntry = new StringBuilder().append("Bypass option: property bypassOption=").append(message.
properties.bypassOption.toString() ?: "n/a").toString();
        //logger.addEntry("logOutboundProcessingEndpoint", logEntry);
        //logEntry = new StringBuilder().append("Header SAP_OutboundProcessingEndpoint=").append(message.
headers.SAP_OutboundProcessingEndpoint.toString() ?: "n/a").toString();
        //logger.addEntry("logOutboundProcessingEndpoint", logEntry);
        //if (logger.getAuditLog()) {
        //        message.setHeader('auditLogHeader', logger.getAuditLog())
        //}

    return message;
}
```

Logic: Writes to the audit log the selected bypassOption and the resulting value of the header SAP_OutboundProcessingEndpoint.

### fetchOriginalPayloadFromXProperty.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {

    // get headers
    def headers = message.getHeaders();
    def isWellFormedXml = headers.get("isWellFormedXml");

    // get properties
    def properties = message.getProperties();

    if (isWellFormedXml == "true"){
        message.setBody(properties.get("originalPayload"));
    } else {
        message.setBody(properties.get("originalPayload").decodeBase64());
    }

    return message
}
```

Logic: Restores the original payload from the property originalPayload back into the body (non-XML is Base64-decoded) — for the custom error flow. Input: property originalPayload, header isWellFormedXml → Output: body

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

Logic: Throws an exception of the type given by exceptionType with the message from exceptionMessage.

## Libraries

- PipelineLogger.groovy — structured audit log (addEntry/ saveToMessage).
- PipelineHelper.groovy — Base64/JSON helpers.
