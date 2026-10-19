<!-- Translated from Confluence page "Generic Step01 – Inbound Processing for IDoc" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Step01 – Inbound Processing for IDoc

Version: 1.0.8 (Origin-Bundle) · Technical name: PIP_01_InboundProcessing4Scenario1 · Package: Cloud Integration Pipeline – Generic Inbound

## Purpose

Generic entry point of the pipeline for IDoc messages (IDoc SOAP over HTTP). The flow receives the IDoc (single or bulk), reads the EDIDC control record into headers, determines the sender interface and the partner ID, calls the optional custom exits, resolves bulk handling (split vs. keep), performs deduplication and stores the message in a JMS queue. Unlike the XI variant, it is purely asynchronous — there is no synchronous Best Effort branch and no bypass options.

## Entry point (IDoc Sender adapter)

| Setting | Value |
|---|---|
| Adapter | IDOC (IDoc SOAP / HTTP, component 1.7.2) |
| Endpoint | {{IDocEntryPoint}} = /PIP/IDoc |
| Authentication | {{SenderAuthType}} = RoleBased, role {{UserRole}} = ESBMessaging.send |
| Invalid XML characters | {{InvalidXMLCharHandling}} = throwException |
| Max. body / attachment size | {{BodySize}} = 40 MB / {{AttachmentSize}} = 100 MB |

## Main flow (Integration Process)

1. Read partner headers (Content Modifier) – XPath from the EDIDC control record into headers: SAP_IDoc_EDIDC_SNDPOR/SNDPRT/SNDPFC/SNDPRN (sender), RCVPOR/RCVPRT/RCVPFC/RCVPRN (receiver), MANDT, MESTYP, IDOCTYP, CIMTYP; header testMode = //*:TEST != '' (boolean). Properties from parameters: CustomXPid_ResolveVirtualReceiver, Default_LogicalSystemName, CustomXPreGlobal_*, CustomXPid_SwitchOffTilde, CustomXError_Enabled.
2. Set sender interface – script setIDocSenderInterface.groovy
3. Determine partner ID – script determinePartnerIDForIDocInbound.groovy (key step)
4. Add custom header properties – script addCustomHeaderProperties.groovy
5. Initialize pre-processing exit – properties CustomXType = CustomXPre, CustomXTest_SkipDuplicateCheck (from the parameter)
6. Call custom exit – local process Call custom exits (identical to the XI variant)
7. Router "QoS?" by ${header.SapQualityOfService}:

| Branch | Condition | Processing |
|---|---|---|
| EOIO | ExactlyOnceInOrder | custom status ConfigurationOptionNotSupported → throwException.groovy |
| EO (Default) | everything else | processing continues below (an IDoc is always processed Exactly Once; QoS is set by setIDocSenderInterface from SapPlainSoapQoS, default ExactlyOnce) |

1. Read inbound queue – script readInboundQueueFromPD.groovy (PD parameter InboundQueue → property inbQueue)
2. Set nodelist (Content Modifier) – SAP_MessageType = SAP_SenderInterface; property nodelistToHeader = XPath //*:DOCNUM (NodeList of all document numbers), nodelistName = idoc, idocBulkIndividualHeader from the parameter {{IDocIndividualHeaderInBulk}}
3. Set bulk IDs – script displayBulkIDs.groovy (IDoc numbers into MPL monitoring)
4. Router "Bulk?": count(//*:IDOC) > 1

### Single IDoc branch

1. Read Idoc control header (Content Modifier) – complete EDIDC into the SAP_IDoc_EDIDC_* headers (TABNAM, DOCNUM, DOCREL, STATUS, DIRECT, OUTMOD, EXPRSS, TEST, IDOCTYP, CIMTYP, MESTYP, MESCOD, MESFCT, STD, STDVRS, STDMES, SNDSAD, SNDLAD, RCVSAD, RCVLAD, CREDAT, CRETIM, REFINT, REFGRP, REFMES, ARCKEY, SERIAL)
2. Set unique ID – SAP_ApplicationID = SAP_IDoc_EDIDC_DOCNUM; property uniqueID = ${SNDPRN}_${DOCNUM}
3. Remove leading zeros – script removeLeadingZerosFromApplicationID.groovy (DOCNUM without leading zeros)
4. Router "Skip duplicate check?": ${property.CustomXTest_SkipDuplicateCheck} = 'true' (test parameter, default false)
   - No (default) → Idempotent Process Call: deduplication by ${property.uniqueID} (= SNDPRN_DOCNUM), skipOnDuplicate, calls the local process Forward to JMS → router "Duplicate?": duplicate → status DuplicateDiscarded, ends Completed; otherwise normal end
   - Yes → direct Process Call (without deduplication) → status DuplicateCheckSkipped

### Bulk IDoc branch

1. Read keep bulk parameter – script readKeepBulkSettingFromPD.groovy (PD parameter KeepBulk → property keepBulk, default false)
2. Router "Keep bulk?":
   - No (default) → IDoc Splitter (splitType = IDoc): the bulk is split into individual IDocs and each passes through the Single IDoc branch (its own EDIDC, its own deduplication, its own JMS write)
   - Yes (keepBulk = true) → the bulk stays together: Set unique ID – SAP_ApplicationID = sapidoctransferid; uniqueID = ${SNDPRN}_${sapidoctransferid} → Remove leading zeros → then as for single (deduplication of the whole bulk by transfer ID)

## Writing to JMS (local process, identical to XI)

Router "Which queue?": ${property.inbQueue} != null → JMS ${property.inbQueue} (from Partner Directory); otherwise the default {{DefaultJMSQueuePrefix}}{{DefaultJMSQueueNumber}} = PIPX01.

> Integrated runtime: the default target queue is PIPX01. The value PIPQ01 would mean routing to the fully decoupled pipeline (via PD InboundQueue).

## Local process: Call custom exits

Identical to the XI variant: Initialize exit (derives …Global_Enabled/_Endpoint from CustomXType) → router "Global exit?" (CustomXGlobalBoolean) → ProcessDirect ${property.CustomXGlobalEndpoint} → readCustomXFromPD.groovy → router "Scenario-specific exit?" (CustomXBoolean) → ProcessDirect ${property.CustomXEndpoint}.

## Error handling

Exception Subprocess in all processes: router "Custom Extension?" (CustomXError_Enabled, default false):

- Custom error handling: Set headers (exceptionSourceMplId, exceptionTimestamp, exceptionStacktrace, exceptionMessage, pipelineStepID) → ProcessDirect {{CustomXError_Endpoint}} → custom status from the header customXError_Status → router by customXError_EndEvent: Completed / Escalated / Failed.
- Standard error handling – differs from XI! Router "Test mode?": ${header.testMode} = 'true' (the IDoc has the TEST flag in EDIDC) → custom status ErrorInTestMode, ends Escalated (a test IDoc does not end as Failed); otherwise → Error End (retry is handled by JMS). Unlike XI, there is no synchronous branch here — IDoc inbound is always asynchronous.

## Scripts – where and why they are called

Order of calls in the main flow: setIDocSenderInterface → determinePartnerIDForIDocInbound → addCustomHeaderProperties → (custom exits: readCustomXFromPD) → readInboundQueueFromPD → displayBulkIDs → (bulk: readKeepBulkSettingFromPD) → removeLeadingZerosFromApplicationID.

| Script | Called in step | Purpose |
|---|---|---|
| setIDocSenderInterface.groovy | Set sender interface | Building SAP_SenderInterface from MESTYP/IDOCTYP/CIMTYP, setting the QoS |
| determinePartnerIDForIDocInbound.groovy | Determine partner ID | Determining the partner ID from EDIDC (logical systems, stages, virtual receiver) |
| addCustomHeaderProperties.groovy | Add custom header properties | Custom header properties for MPL monitoring |
| readCustomXFromPD.groovy | Check scenario-specific exit | Configuration of the scenario-specific exit from PD |
| readInboundQueueFromPD.groovy | Read inbound queue | Target JMS queue from PD (inbQueue) |
| displayBulkIDs.groovy | Set bulk IDs | IDoc numbers (DOCNUM) into MPL custom headers |
| readKeepBulkSettingFromPD.groovy | Read keep bulk parameter (bulk branch) | PD parameter KeepBulk → split vs. keep the bulk |
| removeLeadingZerosFromApplicationID.groovy | Remove leading zeros | Removing leading zeros from SAP_ApplicationID |
| throwException.groovy | Throw exception (EOIO branch) | Throwing an exception with ${property.exceptionMessage} |
| PipelineLogger.groovy, PipelineHelper.groovy | – (libraries) | Audit log / Base64+JSON helpers |

Attach the source files of the scripts in Confluence as attachments of the page.

## Externalized parameters (parameters.prop)

| Parameter | Default | Meaning |
|---|---|---|
| IDocEntryPoint | /PIP/IDoc | Endpoint of the IDoc sender adapter |
| SenderAuthType / UserRole | RoleBased / ESBMessaging.send | Authentication of the sender |
| InvalidXMLCharHandling | throwException | Handling of invalid XML characters in the IDoc |
| DefaultJMSQueuePrefix / DefaultJMSQueueNumber | PIP / X01 | Default inbound queue (PIPX01) |
| JMS_RetentionThreshold / JMS_ExpirationPeriod | 2 / 30 | Retention and expiration of messages in JMS (days) |
| JMS_EncryptStoredMessage / JMS_CompressStoredMessage | 1 / 1 | Encryption and compression of stored messages |
| IDocIndividualHeaderInBulk | true | Bulk: IDoc numbers into MPL as individual custom headers (true) vs. one JSON list (false) |
| CustomXTest_SkipDuplicateCheck | false | Test switch: skip deduplication |
| Default_LogicalSystemName | – | Fallback logical system name of the sender |
| CustomXPid_ResolveVirtualReceiver | false | Take the virtual receiver into account when determining the partner ID (since 1.0.6; bug fixed in 1.0.7) |
| CustomXPid_SwitchOffTilde | true | Behaviour of the tilde when building the partner ID |
| CustomXPreGlobal_Enabled / _Endpoint | false / /pip/custom/preProcessing/global | Global pre-processing exit |
| CustomXError_Enabled / _Endpoint | false / /pip/custom/errorHandling | Custom error handling |
| BodySize / AttachmentSize | 40 / 100 | Size limits (MB) |
| CommunicationParty, IssuerDN, SubjectDN | – | Optional (certificate authentication) |

## Main differences compared to the XI variant

The XI vs. IDoc comparison table is on the parent page Cloud Integration Pipeline – Generic Inbound. In short: the IDoc flow is purely asynchronous (no Best Effort / sync branch, no bypass options), it supports bulk handling (Splitter + KeepBulk), it deduplicates by SNDPRN_DOCNUM, and it has a test mode from the EDIDC field TEST.

(The diagrams of the flow and of the error handling are omitted — Confluence does not support mermaid; the flow is described in text above.)

## Script details

### setIDocSenderInterface.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {
    // headers
    def map = message.getHeaders()

    // Set sender interface name
    message.setHeader("SAP_SenderInterface", map.get("SAP_IDoc_EDIDC_MESTYP") + "." + map.get("SAP_IDoc_EDIDC_IDOCTYP") + (map.get("SAP_IDoc_EDIDC_CIMTYP") ? "." +map.get("SAP_IDoc_EDIDC_CIMTYP") : ""))
    // Set sender interface namespace
    message.setHeader("SAP_SenderInterfaceNamespace", "urn:sap-com:document:sap:idoc:messages")
    // Set QoS
    message.setHeader("SapQualityOfService", map.get("SapPlainSoapQoS") ?: "ExactlyOnce")
    message.setProperty("customHeader_QualityOfService", map.get("SapQualityOfService"))

    return message;
}
```

**Called in:** Set sender interface (main flow, step 2) **Logic:** SAP_SenderInterface = MESTYP.IDOCTYP[.CIMTYP] (CIMTYP only if it exists — extended IDocs); SAP_SenderInterfaceNamespace = urn:sap-com:document:sap:idoc:messages; SapQualityOfService = SapPlainSoapQoS ?: 'ExactlyOnce'. **Input:** headers SAP_IDoc_EDIDC_MESTYP/IDOCTYP/CIMTYP, SapPlainSoapQoS → **Output:** headers SAP_SenderInterface, SAP_SenderInterfaceNamespace, SapQualityOfService; property customHeader_QualityOfService

### determinePartnerIDForIDocInbound.groovy — key script

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

        if (stageVirtual) logger.addEntry("determinePartnerID", "Virtual Stages are already enabled with header 'tenantStageVirtual'.")
        // Partner ID and Tenant Stage can be known from Generic XI / IDoc Inbound Step01
        if(stage && partnerID) { // skip execution - required headers are known
                logger.addEntry("determinePartnerID", "Skipped execution of determinePartnerID script because 'tenantStage' and 'partnerID' headers are already known.")
                def listCurStage = stage.split(",")*.trim() ?: []
                String landscapeValue = service.getParameter(System.getenv("TENANT_NAME"), "SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
                def listVirtual = landscapeValue.split(",")*.trim() ?: []
                def merged = (listCurStage + listVirtual).unique()
                if (merged.size() > 1) { // if multiple Virtual Stages - enable Virtual Stages logic for Receiver
                        tracker.setHeader("tenantStageVirtual", "true")
                        String mergedValue = merged.join(",")
                        logger.addEntry("determinePartnerID", "Virtual Stages are enabled for the tenant: '${mergedValue}'.")
                }
        } else { // execute script logic
                try {
                        // 0. Read IDoc control record & determine logical system of sender
                        def idocControlHeader = readEDIDC(message)
                        def logicalSystemSender = determineSenderLogicalSystem(idocControlHeader, logger)

                        // 1. Read Stage configuration
                        def tenantContext = determineTenantStage(message, service, logger, tracker)

                       // 2. Set the base for Sender Context
                       def senderContext = determineSenderContext(message, service, logicalSystemSender, tenantContext, logger, tracker)

                       // 3. Stage & System resolution (Verification): only for Virtual Stages
                       determineSenderStage(service, logicalSystemSender, tenantContext, senderContext, logger, tracker)

                       // 4. Partner ID determination
                       determinePartnerId(message, service, idocControlHeader, logicalSystemSender, logger, tracker)

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

// --- 0. READ IDOC CONTROL RECORD ---
Map readEDIDC(Message message) {
   // get headers
   def headers = message.getHeaders()

   // define EDIDC keys
   def edidcKeys = [
       "SAP_IDoc_EDIDC_MANDT",
       "SAP_IDoc_EDIDC_SNDPRT",
       "SAP_IDoc_EDIDC_SNDPFC",
       "SAP_IDoc_EDIDC_SNDPRN",
       "SAP_IDoc_EDIDC_SNDPOR",
       "SAP_IDoc_EDIDC_RCVPRT",
       "SAP_IDoc_EDIDC_RCVPFC",
       "SAP_IDoc_EDIDC_RCVPRN",
       "SAP_IDoc_EDIDC_RCVPOR"
   ]

   // Read EDIDC headers into a map, removing prefix
   def edidcValues = edidcKeys.collectEntries { key ->
       def val = headers.get(key)
       [(key.replace("SAP_IDoc_EDIDC_", "")) : val?.trim()] // trim whitespace
   }

   return edidcValues
}

// --- 0. DETERMINE LOGICAL SYSTEM NAME OF SENDER ---
String determineSenderLogicalSystem(Map edidcValues, PipelineLogger logger) {

   String LogicalSystemSender = null
   // In case of LS, use logical system name
   if (edidcValues.SNDPRT == 'LS') {
       LogicalSystemSender = edidcValues.SNDPRN
       logger.addEntry("determineSenderLogicalSystem", "Logical system name for sender determined via partner number: '${LogicalSystemSender}'.")

   // Otherwise (in case of LI, KU, etc.), use port and client to calculate logical system name as <SID>CLNT<MANDT>
   } else{
       if (edidcValues.SNDPOR && edidcValues.MANDT && edidcValues.SNDPOR.startsWith('SAP') && edidcValues.SNDPOR?.length() == 6) {
           LogicalSystemSender = edidcValues.SNDPOR.replace('SAP','') + 'CLNT' + edidcValues.MANDT
           logger.addEntry("determineSenderLogicalSystem", "Logical system name for sender determined via port and client: '${LogicalSystemSender}'.")
       } else {
           if (!edidcValues.SNDPOR && !edidcValues.MANDT) throw new IllegalStateException("Logical system name for sender can't be determined because mandatory IDoc control records SNDPOR and MANDT are empty.")
           if (!edidcValues.SNDPOR) throw new IllegalStateException("Logical system name for sender can't be determined because mandatory IDoc control record SNDPOR is empty.")
           if (!edidcValues.MANDT) throw new IllegalStateException("Logical system name for sender can't be determined because mandatory IDoc control record MANDT is empty.")
           if (!edidcValues.SNDPOR.startsWith('SAP') || edidcValues.SNDPOR?.length() != 6) throw new IllegalStateException("Logical system name for sender can't be determined because IDoc control record SNDPOR with value '${edidcValues.SNDPOR}' doesn't comply with standard port naming convention. For IDocs of sender partner type not equal LS, the generic IDoc inbound flow only supports the format SAP<SID> with <SID> the sender system ID of length 3.")
       }
   }

   return LogicalSystemSender
}

// --- 1. TENANT STAGE ---
Map determineTenantStage(Message message, PartnerDirectoryService service, PipelineLogger logger, HeaderStateTracker tracker) {
   def headers = message.getHeaders()
   String stage = headers.get("tenantStage")
       String headerSender = headers.get("SAP_Sender")
       headerSender = determineSenderParty(headerSender) ? determineSenderParty(headerSender).sender : headerSender
       String authPotSender = null
       boolean authSenderWithStage = false

   // 0) Check SAP_Sender header for landscapeStage if not yet found via Auth User
   if (!stage && headerSender) {
       stage = service.getParameter("landscapeStage", headerSender, String.class)
       if (stage) {
           logger.addEntry("determineTenantStage", "Stage '${stage}' assigned from Landscape Stage of Sender System '${headerSender}' supplied with header.")
       }
   } else if (stage) {
       logger.addEntry("determineTenantStage", "Tenant Stage provided via incoming header: '${stage}'.")
   }

   // 1) Identity Determination via Auth User - it can be at Tenant Level or at Business System Level
   def auth = authenticatedUserDetermination(message, service)
   if (auth) {
       if (auth.location == "tenant") { // Tenant Stage by User (example: Landscape_Stage~DEV~PRJ)
           logger.addEntry("determineTenantStage", "Tenant Stage resolved via Authenticated User: '${auth.pid}'.")
                       if (!stage) { // Tenant Stage header supplied from Step01 has priority
               stage = auth.pid
           } else {
                               if(stage != auth.pid) logger.addEntry("determineTenantStage", "WARNING: Tenant Stage Mismatch: Known tenant stage value '${stage}', while Tenant Stage resolved via Auth User is '${auth.pid}'.")
                               logger.addEntry("determineTenantStage", "Tenant Stage overridden with '${stage}'.")
                       }
       } else { // BS does not have specific pattern/prefix in PD, so it can be differentiated only if landscapeStage is configured
           if(!headerSender) {
                               authPotSender = auth.pid // It's a potential Business System PID
                               logger.addEntry("determineTenantStage", "Authenticated User is mapped to PID: '${auth.pid}'.")

                               // If (optional) Stage is defined, then it is definitely BS

                               String confStage = service.getParameter("landscapeStage", auth.pid, String.class)
                               if (confStage) {
                                       authSenderWithStage = true // if Stage assigned, then it is Business System
                                       logger.addEntry("determineTenantStage", "Stage '${confStage}' assigned from Landscape Stage of Business System '${authPotSender}' that is found using Authenticated User.")
                                       if(confStage.split(",")*.trim().size() > 1) { // validation that only single stage is assigned for BS
                                               throw new IllegalStateException("Partner Directory Configuration Error: Single Landscape Stage can be assigned to Business System: '${auth.pid}' have multiple configured '${confStage}'.")
                                       }
                                       if (!stage) { // Known Tenant Stage header has priority (like supplied from Step01)
                                               stage = confStage
                                       } else {
                                               if(stage != confStage) logger.addEntry("determineTenantStage", "WARNING: Tenant Stage Mismatch: Known tenant stage value '${stage}', while Tenant Stage resolved via Landscape Stage of Business System resolved via Auth User is '${auth.pid}'.")
                                               logger.addEntry("determineTenantStage", "WARNING: Sender System Tenant Stage overridden with '${stage}'.")
                                       }
                               }
                       } else {
                               logger.addEntry("determineTenantStage", "Sender System from header '${headerSender}'; Authenticated User is mapped to PID: '${auth.pid}' but this PID is ignored due to sender is already known.")
                       }
       }
   } else {
               logger.addEntry("determineTenantStage", "Used Authenticated User is not mapped to any PID in PD.")
   }

       // 2) Landscape Configuration
       String landscapeValue = service.getParameter(System.getenv("TENANT_NAME"), "SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
   def listVirtual = landscapeValue.split(",")*.trim()
   boolean isVirtual = listVirtual.size() > 1
       String virtualStages = isVirtual ? landscapeValue : null
       String defaultStage = isVirtual ? null : listVirtual[0]
       if (isVirtual) logger.addEntry("determineTenantStage", "Virtual Stages are enabled for the tenant: '${virtualStages}'.")

       // 3) Stage assignment for Non-Virtual case
       if(!stage && defaultStage) {
               stage = defaultStage
               logger.addEntry("determineTenantStage", "Single stage tenant configuration and set to value: '${stage}'.")
       }

       // 4) Stage Promotion Logic
   if (stage && !(stage in listVirtual)) {
       isVirtual = true
       virtualStages = "${landscapeValue},${stage}" // Promotion to Virtual Context
       logger.addEntry("determineTenantStage", "Resolved Stage '${stage}' promoted to Virtual Context as it was not in the list of tenant's virtual stages '${landscapeValue}'. Active Virtual Stages: '${virtualStages}'.")
   }

   if (isVirtual) tracker.setHeader("tenantStageVirtual", "true")
   if (stage) tracker.setHeader("tenantStage", stage)

   return [stage: stage, isVirtual: isVirtual, viStages: virtualStages, authSender: [hasStage : authSenderWithStage, value : authPotSender]]
}

// --- 2. SENDER CONTEXT ---
Map determineSenderContext(Message message, PartnerDirectoryService service, String LogicalSystemSender, Map ctxTenant, PipelineLogger logger, HeaderStateTracker tracker) {
   def headers = message.getHeaders()

   String alias = headers.get("SAP_SenderAlias")
   String idocSndPrn = LogicalSystemSender
   //String idocSndPrn = headers.get("SAP_IDoc_EDIDC_SNDPRN")  // Detect IDoc
   boolean isIdoc = idocSndPrn != null
   String party = null

       // 1) Resolve Sender
       def authInfo = ctxTenant.authSender
       String potSender = authInfo?.value
       boolean hasStage = authInfo?.hasStage ?: false
       // priority is header; then if Sender was found with User at determineTenantStage with Landscape Stage - then Sender is known:
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
                       logger.addEntry("determineSenderContext", "Party detected in SAP_Sender header: '${party}' and '${sender}'.")
       }
   }

   // 3) Sender Business System Determination via Auth User
   if (!sender && potSender && !hasStage) { // Partner ID was found with User at determineTenantStage but without Landscape Stage
       // Fetch ALL possible Aliases for the Business System across all stages/tracks
               def aliasMatches = findInPD(service, ctxTenant, "BusinessSystemName", potSender, false, true, false) ?:
                      findInPD(service, ctxTenant, "LogicalSystemName", potSender, false, true, false)

               if (aliasMatches) {
           def uniqueAliases = aliasMatches.collect { it.value }.unique()
                       if (uniqueAliases.size() > 1) {
                               throw new IllegalStateException("Partner Directory Configuration Error: Business System '${potSender}' is linked to multiple System Aliases ${uniqueAliases}. A Business System must map to exactly one System Alias.")
           }
                       String potAlias = uniqueAliases[0]
                       sender = potSender
                       if (alias && alias != potAlias) {
                               throw new IllegalStateException("Sender Alias Determination Error: Alias '${alias}' supplied in 'SAP_SenderAlias' header does not match Alias '${potAlias}' associated with Sender System '${potSender}' resolved via Authenticated User Alias Determination. Authenticated User mapped to a specific Business System cannot be used to authenticate other sender systems.")
                       } else {
                               alias = potAlias
                               logger.addEntry("determineSenderContext", "Sender Alias '${alias}' resolved via Authenticated User associated with Business System.")
                       }
           logger.addEntry("determineSenderContext", "Sender '${sender}' resolved via Authenticated User associated with Business System.")
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
               if (!pid) { // At this time if no Alias - either Sender Business System or IDoc SNDPRN must be known
                       throw new IllegalStateException("Mandatory sender identification (SAP_Sender or IDoc SNDPRN) is missing.")
               }
               def aliasList = findInPD(service, ctxTenant, scheme, pid, false, true, false)
               if(aliasList) {
                       def uniqueList = aliasList.collect { it.value }.unique()
                       if (uniqueList.size() > 1) {
                               throw new IllegalStateException("Partner Directory Configuration Error: Multiple System Aliases ${uniqueList} found for Scheme '${scheme}' and PID '${pid}'.")
                       }
                       alias = uniqueList[0]
               }
               if (alias) logger.addEntry("determineSenderContext", "Alias '${alias}' resolved via standard PD lookup for Scheme '${scheme}' and PID '${pid}'.")
   }

   boolean isAliasFound = (alias != null) // set flag that Alias is found in Partner Directory (not a fallback: alias = sender)

   // IDoc Mapping: set header SAP_Sender (map alias name to business system name otherwise keep logical system name)
   if (isIdoc) {
       String idocBS = isAliasFound ? findInPD(service, ctxTenant, "BusinessSystemName", alias, true) : null
       String finalIdocSender = idocBS ?: idocSndPrn
               tracker.setHeader("SAP_Sender", finalIdocSender)
               logger.addEntry("determineSenderContext", "IDoc specific: Header SAP_Sender set to '${finalIdocSender}'. Internal sender context remains '${sender}'.")
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
void determineSenderStage(PartnerDirectoryService service, String LogicalSystemSender, Map ctxTenant, Map ctxSender, PipelineLogger logger, HeaderStateTracker tracker) {
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
                               throw new IllegalStateException("Virtual Stage Routing Error: Sender System for Alias '${alias}' not maintained for stage '${currentStage}'")
           }
           if (sender && foundSender != sender) {
                               throw new IllegalStateException("Virtual Stage Routing Error: Mismatch. Configured Sender System '${foundSender}' in System Alias '${alias}' does not match actual sender '${sender}' for tenant stage '${currentStage}'")
           } // else if(foundSender) && foundSender == sender, then expected normal path with correct and verfied tenantStage
       }
   } else { // --- Scenario 2: Tenant Stage is UNKNOWN ---
       if (!isAliasFromPD) { // 2.a If No Alias from PD (like sender business component without System Alias): keep stage ambiguous with possible virtual stages
           tracker.setHeader("tenantStage", ctxTenant.viStages)
           logger.addEntry("determineSenderStage", "Tenant Stage is unknown and no Sender System Alias found for sender '${sender}. Set header for tenant stage to ambiguous value with possible virtual stages: '${ctxTenant.viStages}'.")
       } else { // 2.b if Alias exists, then resolve Stage either via Alias + Sender or Alias + LogicalSystemSender
           def matches
           def validMatches
           if (!sender) { // if no Sender, resolve via Alias + LogicalSystemSender by retrieving ALL matches across ALL virtual stages
               matches = findInPD(service, ctxTenant, "LogicalSystemName", alias, true, true, false)
               // Filter list to find matches where the found value equals the actual logical system
                               validMatches = matches?.findAll { it.value == LogicalSystemSender }

                               if (!validMatches) {
                                       throw new IllegalStateException("Virtual Stage Routing Error: Logical system name '${LogicalSystemSender}' with System Alias '${alias}' not found in any virtual stage '${ctxTenant.viStages}'.")
                               }
                               if (validMatches.size() == 1) { // One unique occurrence - set tenant stage from Sender System stage (expected path)
                                       tracker.setHeader("tenantStage", validMatches[0].stage)
                                       logger.addEntry("determineSenderStage", "Tenant Stage match found: '${validMatches[0].stage}'")
                               } else { // Multiple matches found
                                       String foundInStages = validMatches.collect { "[${it.stage}]" }.join(",")
                                       tracker.setHeader("tenantStage", foundInStages)
                                       logger.addEntry("determineSenderStage", "Ambiguity detected: Logical system name '${LogicalSystemSender}' with Alias '${alias}' matches multiple virtual stages: '${foundInStages}'.Tenant Stage is unknown and allowed landscape: '${ctxTenant.viStages}'. Set tenant stage header to ambiguous value with matching virtual stages: '${foundInStages}'.")
                               }
//                                 tracker.setHeader("tenantStage", ctxTenant.viStages)
//                                 logger.addEntry("determineSenderStage", "For Alias '${alias}' sender system 'SAP_Sender' is unknown: Tenant Stage cannot be determined and header set to ambiguous value with possible virtual stages: '${ctxTenant.viStages}'.")
                       } else { // else resolve via Alias + Sender by retrieving ALL matches across ALL virtual stages
                               matches = findInPD(service, ctxTenant, "BusinessSystemName", alias, true, true, false)
                               // Filter list to find matches where the found value equals the actual sender
                               validMatches = matches?.findAll { it.value == sender }

                               if (!validMatches) {
                                       throw new IllegalStateException("Virtual Stage Routing Error: Sender '${sender}' with System Alias '${alias}' not found in any virtual stage '${ctxTenant.viStages}'.")
                               }
                               if (validMatches.size() == 1) { // One unique occurrence - set tenant stage from Sender System stage (expected path)
                                       tracker.setHeader("tenantStage", validMatches[0].stage)
                                       logger.addEntry("determineSenderStage", "Tenant Stage match found: '${validMatches[0].stage}'")
                               } else { // Multiple matches found
                                       String foundInStages = validMatches.collect { "[${it.stage}]" }.join(",")
                                       tracker.setHeader("tenantStage", foundInStages)
                                       logger.addEntry("determineSenderStage", "Ambiguity detected: Sender '${sender}' with Alias '${alias}' matches multiple virtual stages: '${foundInStages}'. Tenant Stage is unknown and allowed landscape: '${ctxTenant.viStages}'. Set tenant stage header to ambiguous value with matching virtual stages: '${foundInStages}'.")
                               }
                       }
       }

   }
}

// --- 4. PARTNER ID ---
void determinePartnerId(Message message, PartnerDirectoryService service, Map edidcValues, String LogicalSystemSender, PipelineLogger logger, HeaderStateTracker tracker) {
   def headers = message.getHeaders()
       def properties = message.getProperties()

   if (headers.get("partnerID")) return

   def messageLog = messageLogFactory.getMessageLog(message)
   String Stage = headers.get("tenantStage")
   if (Stage && Stage.split(",")*.trim().size() == 1) messageLog?.addCustomHeaderProperty("tenantStage", Stage)

   String IdocSenderInterface = headers.get("SAP_SenderInterface")
   String IdocSenderInterfaceNamespace = headers.get("SAP_SenderInterfaceNamespace")

   // check tilde option
   String SwitchOffTilde = properties.get("CustomXPid_SwitchOffTilde").toLowerCase()
   // check usage of virtual receiver
   String ResolveVirtualReceiver = properties.get("CustomXPid_ResolveVirtualReceiver").toLowerCase()
       def pidMap

   // Fetch headers and initialize variables
   String SenderAlias = null
   String SenderPartyAlias = null
   String ALESenderScheme = null
   String ALESenderParty = null
   String ALEReceiverParty = null
   String ALEReceiverScheme = null
   String LogicalSystemReceiver = null
   String ReceiverAlias = null
   String ReceiverPartyAlias = null
   String Pid = null
   def senderParts
   def receiverParts

   // Define ALE sender scheme as ALE#<SNDPRT>[#<SNDPFC>], and ALE sender party as ALE#<SNDPRT>[#<SNDPFC>] #<SNDPRN>
   // Add sender partner to custom header properties as <SNDPRT>[.<SNDPFC>].<SNDPRN>.<SNDPOR>
   senderParts = ["SNDPRT","SNDPFC"].collect { edidcValues[it] }.findAll { it }
   ALESenderScheme ='ALE#' + senderParts.join("#")
   ALESenderParty = ALESenderScheme + '#' + edidcValues.SNDPRN
   senderParts = senderParts + ["SNDPRN","SNDPOR"].collect { edidcValues[it] }.findAll { it }
   messageLog?.addCustomHeaderProperty("IDocSenderPartner", senderParts.join("."))

   // IN CASE OF VIRTUAL RECEIVER: DETERMINE LOGICAL SYSTEM NAME OF RECEIVER
   if (ResolveVirtualReceiver == 'true') {
       logger.addEntry("determinePartnerId", "Virtual receiver to determine the partner ID is taken into account")
   // In case of LS, use logical system name
       if (edidcValues.RCVPRT == 'LS') {
           LogicalSystemReceiver = edidcValues.RCVPRN
       }
   // Define ALE receiver scheme as ALE#<RCVPRT>[#<RCVPFC>], and ALE virtual receiver partner as ALE#<RCVPRT> [#<RCVPFC>]#<RCVPRN>
       receiverParts = ["RCVPRT","RCVPFC"].collect { edidcValues[it] }.findAll { it }
       ALEReceiverScheme ='ALE#' + receiverParts.join("#")
       ALEReceiverParty = ALEReceiverScheme + '#' + edidcValues.RCVPRN
       receiverParts =  receiverParts + ["RCVPRN","RCVPOR"].collect { edidcValues[it] }.findAll { it }
   }

   // DETERMINE SENDER ALIAS
   // Check if a business system alias exists as partner ID in the Partner Directory and if so map logical system name to sender alias
   String Agency = Stage
   String Scheme = 'LogicalSystemName'
   String AlternativePid = LogicalSystemSender
   SenderAlias = service.getPartnerId(Agency, Scheme, AlternativePid)

   // For compatibility reasons, check if the logical system name scheme has been misused to map any partner number to an alias independent of the partner type
   if (SenderAlias == null){
       AlternativePid = edidcValues.SNDPRN
       SenderAlias = service.getPartnerId(Agency, Scheme, AlternativePid)
   }

   // Map alias name to business system name otherwise use logical system name
   String Sender = null;
   if (SenderAlias != null){
       Scheme = 'BusinessSystemName'
       Sender = service.getAlternativePartnerId(Agency, Scheme, SenderAlias) ?: LogicalSystemSender
   } else{
       Sender = LogicalSystemSender
   }

   // Set header SAP_Sender in any case
   message.setHeader("SAP_Sender", Sender)

   // Add logical system to custom header properties if sender differs from logical system
   if (Sender != LogicalSystemSender) {
       messageLog?.addCustomHeaderProperty("SenderLogicalSystem", LogicalSystemSender)
   }

   // Add sender alias to custom header properties if sender alias differs from logical system
   if (SenderAlias != LogicalSystemSender) {
       message.setProperty("customHeader_SenderAlias", SenderAlias)
   }

   // DETERMINE SENDER PARTY ALIAS
   // Only makes sense if sender alias is not empty
   if (edidcValues.SNDPRT != 'LS' && SenderAlias) {
       Agency = SenderAlias
       Scheme = ALESenderScheme
       AlternativePid = edidcValues.SNDPRN
       SenderPartyAlias = service.getPartnerId(Agency, Scheme, AlternativePid)
       if (SenderPartyAlias) {
           message.setProperty("customHeader_SenderParty", SenderPartyAlias)
           message.setHeader("SAP_Sender", SenderPartyAlias + '.' + Sender)
           message.setProperty("customHeader_SenderSystem", Sender)
       }
   }

   // IN CASE OF VIRTUAL RECEIVER: DETERMINE RECEIVER ALIAS
   if (ResolveVirtualReceiver == 'true' && LogicalSystemReceiver) {
       Agency = Stage
       Scheme = 'LogicalSystemName'
       AlternativePid = LogicalSystemReceiver
       ReceiverAlias = service.getPartnerId(Agency, Scheme, AlternativePid)
   }

   // IN CASE OF VIRTUAL RECEIVER: DETERMINE RECEIVER PARTY ALIAS
   // Only makes sense if sender alias is not empty
   if (ResolveVirtualReceiver == 'true' && edidcValues.RCVPRT != 'LS' && SenderAlias) {
       Agency = SenderAlias
       Scheme = ALEReceiverScheme
       AlternativePid = edidcValues.RCVPRN
       ReceiverPartyAlias = service.getPartnerId(Agency, Scheme, AlternativePid)
   }

   // DETERMINE PARTNER ID
   // Multiple options are supported, start with the most specific
   // If virtual receiver should be resolved, check if sender and virtual receiver can be mapped to a configuration scenario
   // In case of no hit, check if sender only can be mapped to a configuration scenario
   // In case of no hit, sort of sender wildcard option, i.e., depending on logical system name or business system alias independent of actual partner number or partner type
   // In case of no hit, backward compatibility options to support scenario configurations before we introduced the support for partner type NOT equal LS

   String AgencySenderParty

   String AgencySenderService
   String AgencyReceiver
   // First option: In case of virtual receiver
   if (ResolveVirtualReceiver == 'true') {
   // In case of sender partner type equals LS: Agency is either sender alias or sender logical system
       if (edidcValues.SNDPRT == 'LS') {
           Agency = SenderAlias ?: LogicalSystemSender
   // Otherwise, in case of sender partner type NOT equals LS
   // With landscape staging: Agency is a combination of sender party and sender alias
   // Without landscape staging: Agency is a combination of ALE sender party (ALE#<SNDPRT>[#<SNDPFC>] #<SNDPRN>) and sender logical system
       } else {
           AgencySenderParty = SenderPartyAlias ?: ALESenderParty
           AgencySenderService = SenderAlias ?: LogicalSystemSender
           Agency = AgencySenderParty + '.' + AgencySenderService
       }
   // In case of receiver partner type equals LS: Agency is either receiver alias or receiver logical system
       if (edidcValues.RCVPRT == 'LS') {
           AgencyReceiver = ReceiverAlias ?: LogicalSystemReceiver
   // Otherwise, in case of receiver partner type NOT equals LS: Agency is either receiver party or ALE receiver party (ALE#<RCVPRT>[#<RCVPFC>]#<RCVPRN>)
       } else {
           AgencyReceiver = ReceiverPartyAlias ?: ALEReceiverParty
       }
       Agency = Agency + ':' + AgencyReceiver
       pidMap = determinePartnerIDAttempts(service, Agency, IdocSenderInterface, IdocSenderInterfaceNamespace)
       Pid = pidMap.pid
   // Add ALE virtual receiver partner to custom header properties as <RCVPRT>[.<RCVPFC>].<RCVPRN>.<RCVPOR>
       if (Pid) {
           receiverParts = ["RCVPRT","RCVPFC","RCVPRN","RCVPOR"].collect { edidcValues[it] }.findAll { it }
           messageLog?.addCustomHeaderProperty("IDocVirtualReceiverPartner", receiverParts.join("."))
       }
   // Add receiver alias to custom header properties
       if (Pid && ReceiverAlias) {
           messageLog?.addCustomHeaderProperty("VirtualReceiverAlias", ReceiverAlias)
       }
   // Add receiver party alias to custom header properties
       if (Pid && ReceiverPartyAlias) {
           messageLog?.addCustomHeaderProperty("VirtualReceiverParty", ReceiverPartyAlias)
       }
   }

   // Second option: Without virtual receiver
   if (Pid == null) {
   // In case of sender partner type equals LS: Agency is either sender alias or sender logical system
       if (edidcValues.SNDPRT == 'LS') {
           Agency = SenderAlias ?: LogicalSystemSender
           message.setHeader("SAP_SenderAlias", Agency)
   // Otherwise, in case of sender partner type NOT equals LS
   // With landscape staging: Agency is a combination of sender party and sender alias
   // Without landscape staging: Agency is a combination of ALE sender party (ALE#<SNDPRT>[#<SNDPFC>] #<SNDPRN>) and sender logical system
       } else {
           AgencySenderParty = SenderPartyAlias ?: ALESenderParty
           AgencySenderService = SenderAlias ?: LogicalSystemSender
           Agency = AgencySenderParty + '.' + AgencySenderService
           message.setHeader("SAP_SenderAlias", AgencySenderService)
       }
       pidMap = determinePartnerIDAttempts(service, Agency, IdocSenderInterface, IdocSenderInterfaceNamespace)
       Pid = pidMap.pid
   }

   // Third option: For partner type NOT equal LS based on sender system only
   if (Pid == null && edidcValues.SNDPRT != 'LS') {
   // In case of sender partner type equals LS: Agency is either sender alias or sender logical system
       Agency = SenderAlias ?: LogicalSystemSender
       pidMap = determinePartnerIDAttempts(service, Agency, IdocSenderInterface, IdocSenderInterfaceNamespace)
       Pid = pidMap.pid
   }

   // Fourth option: Compatibility option, i.e., in case that a sender partner number of partner type different to LS is directly mapped to a configuration scenario
   if (Pid == null && edidcValues.SNDPRT != 'LS') {
       Agency = edidcValues.SNDPRN
       message.setHeader("SAP_SenderAlias", Agency)
       pidMap = determinePartnerIDAttempts(service, Agency, IdocSenderInterface, IdocSenderInterfaceNamespace)
       Pid = pidMap.pid
   }

   // Fifth option: Further compatibility option, i.e., use a combination of sender component and sender interface unless you switched off the tilde option
   // Note: not allowed chars need to be replaced
   if (Pid == null && SwitchOffTilde != 'true') {
       logger.addEntry("determinePartnerId", "Tilde option to determine the partner ID is active")
       Agency = SenderAlias ?: LogicalSystemSender
       Pid = Agency + '~' + IdocSenderInterface.toString().replaceAll("[&%/]","_")
   }

   // Finally, raise an error if we still haven't determined any partner ID
   if (Pid == null) {
       def exceptionText = "No matching Partner ID found in Partner Directory for sender logical system " + LogicalSystemSender
       if (edidcValues.SNDPRT != 'LS') {
           exceptionText = exceptionText + ", sender partner " + ALESenderParty
       }
       if (ResolveVirtualReceiver == 'true') {
           exceptionText = exceptionText + ", potentially virtual receiver partner " + ALEReceiverParty
       }
       exceptionText = exceptionText + ", and sender interface " + IdocSenderInterface + " with namespace " + IdocSenderInterfaceNamespace
       throw new IllegalStateException(exceptionText)
   }

   // create new header holding the Partner ID and custom header for the alternative partner
   tracker.setHeader("partnerID", Pid)
   message.setProperty("customHeader_PartnerID", Pid)
   if (SwitchOffTilde == 'true') {
       messageLog?.addCustomHeaderProperty("AlternativePartner", Agency + '|' + pidMap.scheme + '|' + pidMap.id)
   }

}

// --- 5. PIPELINE AUDIT LOG ---
void finalizeAuditLog(Message message, PipelineLogger logger) {
   def h = message.getHeaders()
       def p = message.getProperties()
   logger.addEntry("determinePartnerID_FinalHeaders", "tenantStage=${h.tenantStage}, tenantStageVirtual=${h.tenantStageVirtual}, SAP_Sender=${h.SAP_Sender}, SAP_SenderAlias=${h.SAP_SenderAlias}, partnerID=${h.partnerID}")
       logger.addEntry("determinePartnerID_Additional", "Headers: SAP_SenderParty=${h.SAP_SenderParty ?: 'n/a'}, SAP_SenderInterface=${h.SAP_SenderInterface ?: 'n/a'}, SAP_ApplicationID=${h.SAP_ApplicationID ?: 'n/a'}, testMode=${h.testMode ?: 'n/a'}, Property CustomXError_Enabled=${p.CustomXError_Enabled ?: 'n/a'}")
   if (logger.getLog()) logger.saveToMessage(message)
}

/**
* --- HELPERS ---
* 6 helpers are used: determineSenderParty, authenticatedUserDetermination, findInPD, resolveSenderFromAliasMatches, HeaderStateTracker, determinePartnerIDAttempts
*/
// Check and Extract Sender Party
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
def findInPD(PartnerDirectoryService service, Map ctxTenant, String scheme, String value, boolean isAlt = false, boolean fetchAll = false, boolean perStage = false) {
   // 1) Determine the exact stages to search from the landscape
       def searchStages = perStage ? [ctxTenant.stage] : (ctxTenant.isVirtual ? (ctxTenant.viStages?.split(",")
*.trim() ?: []) : [ctxTenant.stage])
   def results = []
   for (stage in searchStages.unique()) { // 2) Iterate through unique stages only
       if (!stage) continue
       String found = isAlt ? service.getAlternativePartnerId(stage, scheme, value) : service.getPartnerId(stage, scheme, value)
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
       logger.addEntry("determineSenderContext", "Sender '${resolvedSender}' resolved as unique match for Alias '${alias}'.")
   } else {
       // 2. Try Stage-based resolution
       if (!resolvedSender && ctxTenant.stage) {
           def match = matches.find { it.stage == ctxTenant.stage }
           if (match) {
               resolvedSender = match.value
               logger.addEntry("determineSenderContext", "Sender '${resolvedSender}' resolved via Stage Match(${match.stage}) for Alias '${alias}'.")
           }
       }
       // 3. If still not resolved, keep sender unknown

       if (!resolvedSender) {
           logger.addEntry("determineSenderContext", "System Alias '${alias}' has multiple possible Sender Systems: '${senderList}'. Specific system could not be determined for the current landscape context as Tenant Stage is unknown '${ctxTenant.viStages}'.")
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
       logger.addEntry("HeaderStateTracker", "Rollback performed: Reverted headers ${modifiedKeys} to original values.")
   }
}

/**
* Helper performing all attempts to determine partner ID
*/
Map determinePartnerIDAttempts(PartnerDirectoryService service, String agency, String senderInterface, String senderInterfaceNamespace) {
   // First attempt: Determine partner ID via alternative partner based on sender service, sender interface and interface namespace
   // Note: not allowed chars need to be replaced
   String scheme = senderInterface.toString().replaceAll("[&%/]","_")
   String id = senderInterfaceNamespace
   String pid = service.getPartnerId(agency, scheme, id)

   // Second attempt: Determine partner ID via alternative partner based on sender service and sender interface
   if (pid == null){
       scheme = 'SenderInterface'
       id = senderInterface
       pid = service.getPartnerId(agency, scheme, id)
   }

   // Third attempt: Otherwise use a combination of sender component and sender interface unless you switched off the tilde option
   // Note: not allowed chars need to be replaced
   //if (pid == null && additionalAttempt){
   //    pid = agency + '~' + senderInterface.toString().replaceAll("[&%/]","_")
   //    agency = 'n/a'
   //}

    return [ pid: pid, scheme: scheme, id: id ]
}
```

**Called in:** Determine partner ID (main flow, step 3) **Logic (simplified):** Determining the partner ID from the IDoc control record. Phases: reading the EDIDC values and determining the logical system of the sender (SNDPRN, partner type LS and others; fallback Default_LogicalSystemName) → tenant stage (authorized user / landscapeStage in PD / tenant mapping, incl. virtual stages) → sender context (translation of logical system → business system via alternative partner IDs, scheme BusinessSystemName) → verification of the sender's stage → partner ID; with CustomXPid_ResolveVirtualReceiver=true the virtual receiver (RCVPRN/RCVPRT, also for partner type ≠ LS — extension from 1.0.6, fix of No such property: Pid in 1.0.7) is additionally taken into account → audit log. **Input:** headers SAP_IDoc_EDIDC_*, tenantStage; properties CustomXPid_ResolveVirtualReceiver, CustomXPid_SwitchOffTilde, Default_LogicalSystemName; PD partners Landscape_Stage~*, SAP_Integration_Suite_Landscape, alternative partner IDs → **Output:** headers partnerID, tenantStage, and possibly the related SAP_Sender, SAP_Receiver

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
    def custHeaderProps = headers.get('customHeaderProperties') //custom headers are either stored in the standard pipeline header
        def properties = message.getProperties() //or custom headers can be defined as IFlow-step specific Exchange Propeties
        def resultCustomHeaderProperties = [:] // Start with empty map
        def messageLog = messageLogFactory.getMessageLog(message)

    try {
         // first check if header customHeaderProperties exists and already defined (only String input is expected)
                 if (custHeaderProps && custHeaderProps instanceof String && custHeaderProps?.trim()) {
            // Split by '|' and handle each part independently (backward compatibility to avoid failure of handling input like jsonInBase64|key:value
            custHeaderProps.split(/\|/).each { part ->
                def decodedPart = PipelineHelper.decodeIfBase64(part) //if base64 and JSON - decode to Map, otherwise return String
                if (decodedPart instanceof Map) {
                    resultCustomHeaderProperties.putAll(decodedPart)
                } else if (decodedPart instanceof String && PipelineHelper.isValidKeyValueString(decodedPart)) {
                                        //if String - check if it is delimited as expected and if yes - split and convert to result Map
                                        decodedPart.split(/\|/).each { pair ->
                                                def kv = pair.split(":", 2)
                                                if (kv?.size() == 2) {
                                                        resultCustomHeaderProperties[kv[0]?.trim()] = kv[1]?.trim()
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
                                                def indexedKey = "${cleanKey}_NdLstElmnt${i + 1}" // unique in internal MAP with Node List Element index
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

    try { //if final Customer Headers Map exists and not empty - attach to the MPL log and store the header with potentially new or same values
        if (messageLog && resultCustomHeaderProperties && !resultCustomHeaderProperties.isEmpty()) {
            resultCustomHeaderProperties.each { key, value ->
                def logKey = key.replaceFirst(/_NdLstElmnt\d+$/, "") // IF Node List Element index - remove suffix to simplify the search by custom value
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

**Called in:** Add custom header properties (main flow, step 4) **Logic:** Identical to the XI variant — MPL custom headers from the header customHeaderProperties (string k:v|k:v or Base64 JSON) and from the properties customHeader_* (incl. NodeLists).

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
       logger.addEntry("readCustomXFromPD", "Partner ID to read the custom exit settings: " + (Pid ?: "n/a") + ".")
       logger.addEntry("readCustomXFromPD", "Custom exit type: property CustomXType=" + (message.properties.CustomXType.toString() ?: "n/a") + ".")
       logger.addEntry("readCustomXFromPD", "Custom exit end point from the Partner Directory: property CustomXEndpoint=" + (message.properties.CustomXEndpoint.toString() ?: "n/a") + ".")
       logger.addEntry("readCustomXFromPD", "Custom exit enabled: property CustomXBoolean=" + (message.properties.CustomXBoolean.toString() ?: "n/a") + ".")
       if (logger.getLog()) {
           logger.saveToMessage(message)
       }

       //PipelineLogger logger = PipelineLogger.newLogger(message)
       //def logEntry = new StringBuilder().append("Partner ID to read the custom exit settings: ").append(Pid ?: "n/a").toString();
       //logger.addEntry("readCustomXFromPD", logEntry);
       //logEntry = new StringBuilder().append("Custom exit type: property CustomXType=").append(message.properties.CustomXType.toString() ?: "n/a").toString();
       //logger.addEntry("readCustomXFromPD", logEntry);
       //logEntry = new StringBuilder().append("Custom exit end point from the Partner Directory: property CustomXEndpoint=").append(message.properties.CustomXEndpoint.toString() ?: "n/a").toString();
       //logger.addEntry("readCustomXFromPD", logEntry);
       //logEntry = new StringBuilder().append("Custom exit enabled: property CustomXBoolean=").append(message.properties.CustomXBoolean.toString() ?: "n/a").toString();
       //logger.addEntry("readCustomXFromPD", logEntry);
       //if (logger.getAuditLog()) {
       //        message.setHeader('auditLogHeader', logger.getAuditLog())
       //}

   return message;
}
```

**Called in:** Check scenario-specific exit (local process Call custom exits) **Logic:** Identical to the XI variant — the step number from camelId, reading CustomXPreEnabled/Endpoint from headers or PD by partnerID; Enabled is a |-separated list of steps.

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
        logger.addEntry("readInboundQueueFromPD", "Partner ID to read the inbound queue:" + (Pid ?: "n/a") + ".")
        logger.addEntry("readInboundQueueFromPD", "Inbound queue from the Partner Directory: property inbQueue=" + (message.properties.inbQueue.toString() ?: "n/a") + ".")
        if (logger.getLog()) {
            logger.saveToMessage(message)
        }

        //PipelineLogger logger = PipelineLogger.newLogger(message)
        //def logEntry = new StringBuilder().append("Partner ID to read the inbound queue: ").append(Pid ?: "n/a").toString();
        //logger.addEntry("readInboundQueueFromPD", logEntry);
        //logEntry = new StringBuilder().append("Inbound queue from the Partner Directory: property inbQueue=").append(message.properties.inbQueue.toString() ?: "n/a").toString();
        //logger.addEntry("readInboundQueueFromPD", logEntry);
        //if (logger.getAuditLog()) {
        //        message.setHeader('auditLogHeader', logger.getAuditLog())
        //}

    return message;
}
```

**Called in:** Read inbound queue **Logic:** Identical to the XI variant — PD parameter InboundQueue by partnerID → property inbQueue.

### displayBulkIDs.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import org.w3c.dom.NodeList
import groovy.json.JsonOutput

Message processData(Message message) {

    def nodes = message.getProperty('nodelistToHeader') as NodeList
    def headerName = message.getProperty('nodelistName') as String
    def messageLog = messageLogFactory.getMessageLog(message)

    // --- bulk flag with default = false (case-insensitive) ---
    def individualHeaders4BulkFlag = (message.getProperty('idocBulkIndividualHeader') ?: "false").toString().equalsIgnoreCase("true")

    // 1: Handle SAP-specific headers (SapIDocDbId / SapIDocTransferId)
        def headers = message.getHeaders()
        String sapDbId = headers.get('SapIDocDbId')
        String sapTransferId = headers.get('SapIDocTransferId')

    if (sapDbId) {
        def cleaned = sapDbId.toString().replaceFirst("^0+", "")
        messageLog?.addCustomHeaderProperty("SapIDocDbId", cleaned)
    }

    if (sapTransferId) {
        def cleaned = sapTransferId.toString().replaceFirst("^0+", "")
        messageLog?.addCustomHeaderProperty("SapIDocTransferId", cleaned)
    }

    // 2: Handle NodeList-based logic

        // --- If no nodes or only one node  nothing to do ---
        if (!nodes || nodes.getLength() <= 1) {
                return message
        }

    // ----- CUSTOM HEADERS: INDIVIDUAL MODE -----
    if (individualHeaders4BulkFlag) {
        for (int i = 0; i < nodes.getLength(); i++) {
            def value = nodes.item(i).getTextContent().replaceFirst("^0+", "")
            messageLog?.addCustomHeaderProperty(headerName, value)
        }
        return message
    }

    // ----- CUSTOM HEADERS: BULK MODE -----
        def list = []
        for (int i = 0; i < nodes.getLength(); i++) {
                def cleaned = (nodes.item(i)?.getTextContent() ?: "")
                                .toString()
                                .replaceFirst("^0+", "")
                if (cleaned != "") {
                        if (cleaned.isInteger()) {
                                list << cleaned.toInteger()
                        } else {
                                list << cleaned
                        }
                }
        }

        if (!list.isEmpty()) {
                def jsonString = JsonOutput.toJson(list)
                messageLog?.addCustomHeaderProperty("${headerName}_bulk", jsonString)
        }

    return message
}
```

**Called in:** Set bulk IDs (after Set nodelist) **Logic:** Writes the IDoc identifiers to MPL monitoring: SapIDocDbId / SapIDocTransferId (without leading zeros) and the document numbers from the property nodelistToHeader (NodeList //DOCNUM). According to idocBulkIndividualHeader (parameter IDocIndividualHeaderInBulk): true → each DOCNUM as a separate custom header idoc; false → one header idoc_bulk with a JSON list. For a single IDoc (≤1 node) it does nothing with the NodeList. **Input:** properties nodelistToHeader, nodelistName, idocBulkIndividualHeader; headers SapIDocDbId, SapIDocTransferId → **Output:** MPL custom header properties

### readKeepBulkSettingFromPD.groovy

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

    // read keep bulk parameter from the Partner Directory
    def keepBulk = service.getParameter("KeepBulk", Pid , String.class);

    // if the value exists in the Partner Directory, create a new property with configured value
    // otherwise, set the property to default which is false
    message.setProperty("keepBulk", keepBulk ?: 'false');

        // adding information to audit log using helper PipelineLogger class
        PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
        logger.addEntry("readKeepBulkSettingFromPD", "Partner ID to read the bulk setting: " + (Pid ?: "n/a") + ".")
        logger.addEntry("readKeepBulkSettingFromPD", "Keep bulk property keepBulk=" + (message.properties.keepBulk.toString() ?: "n/a") + ".")
        if (logger.getLog()) {
            logger.saveToMessage(message)
        }

        //PipelineLogger logger = PipelineLogger.newLogger(message)
        //def logEntry = new StringBuilder().append("Partner ID to read the bulk setting: ").append(Pid ?: "n/a").toString();
        //logger.addEntry("readKeepBulkSettingFromPD", logEntry);
        //logEntry = new StringBuilder().append("Keep bulk property keepBulk=").append(message.properties.keepBulk.toString() ?: "n/a").toString();
        //logger.addEntry("readKeepBulkSettingFromPD", logEntry);
        //if (logger.getAuditLog()) {
        //        message.setHeader('auditLogHeader', logger.getAuditLog())
        //}

    return message;
}
```

**Called in:** Read keep bulk parameter (bulk branch) **Logic:** PD parameter KeepBulk by partnerID → property keepBulk (default false = the bulk is split by the splitter). Without partnerID it throws IllegalStateException. **Input:** header partnerID; PD parameter KeepBulk → **Output:** property keepBulk

### removeLeadingZerosFromApplicationID.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {
    def headers = message.getHeaders()
    def applicationId = headers.get('SAP_ApplicationID')

    if (applicationId != null){
        message.setHeader('SAP_ApplicationID', applicationId.replaceFirst("^0+", ""))
    }

    return message
}
```

**Called in:** Remove leading zeros (after Set unique ID, both branches) **Logic:** Removes leading zeros from the header SAP_ApplicationID (DOCNUM or transfer ID) (replaceFirst("^0+","")), so that it is possible to search in monitoring by the IDoc number without zeros. **Input/Output:** header SAP_ApplicationID

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
                exceptionMessage = "Throw an exception with message body: ${message.getBody(String) ?: "Unexpected error"}"
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

**Called in:** Throw exception (EOIO branch) **Logic:** Identical to the XI variant — an exception of the type given by exceptionType with the message from exceptionMessage.

## Libraries

- PipelineLogger.groovy — structured audit log (addEntry/saveToMessage), imported by the PD scripts.
- PipelineHelper.groovy — Base64/JSON helpers for addCustomHeaderProperties.

Note: Bundle version 1.0.8 corresponds to the state after the changes of version 1.0.7 (fix of No such property: Pid when CustomXPid_ResolveVirtualReceiver=false).
