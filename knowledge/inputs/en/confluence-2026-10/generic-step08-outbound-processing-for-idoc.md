<!-- Translated from Confluence page "Generic Step08 – Outbound Processing for IDoc" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Step08 – Outbound Processing for IDoc

Version: 1.0.2 (manifest) / contains features of version 1.0.3 · Package: Cloud Integration Pipeline – Generic Outbound

## Purpose

Generic IDoc receiver step: sends an IDoc (single and bulk) to the receiving ABAP system. It is called via ProcessDirect from the scenario-specific outbound flow (Step07). Besides connectivity (the same three-layer precedence as the XI variant: SAP_* headers → Partner Directory → externalized parameters) it additionally handles the completion and validation of the EDI_DC40 control record — adding a missing node, overwriting fields from the configuration, checking mandatory fields — and the choice of the content type (x-sap.idoc for a single IDoc vs. Text/XML for bulk).

> Common to both runtimes — the physical delivery is the same in the integrated and in the fully decoupled pipeline.

## Entry (ProcessDirect Sender)

Endpoint {{ProcessDirect_Address}} = /generic/step08/idoc. Header whitelist according to AllowedHeadersList (SAP_Sender*, SAP_Receiver*, SAP_IDoc_EDIDC*, SAP_MessageType, SAP_ApplicationID, customX*, dc*, partnerID, testMode, SapPlainSoapQoS, SapPlainSoapQueueId, …).

## Main flow (Integration Process)

1. Set Default Properties (Content Modifier) – parameters → properties default_*: EDIDC defaults (Default_SNDPRN = ISuite, Default_SNDPRT = LS, Default_SNDPOR = SAPBTP, Default_RCVPRN = SIDCLNTXYZ, Default_RCVPRT = LS, Default_RCVPOR = SAPSID), fixed EDIDC items (Default_EDIDC_List_Names/Values: TABNAM=EDI_DC40, MANDT=100, DIRECT=2), connectivity (IDoc_Address → default_Receiver_IDocEndpoint, IDoc_ProxyType/AuthenticationType/ContentType/CredentialName/PrivateKeyAlias, LocationID), pidParameterReadSuffixPattern = MESTYP.IDOCTYP.CIMTYP
2. IDoc Basic Validation (local process, details below)
3. Read and Set PD values – script setAllDefaultAndScenarioValuesFromPDForIDoc.groovy (details below)
4. Set Properties & Adjust EDIDC – script setEdidcFromParamsForIDoc.groovy (details below) — overwrites EDI_DC40 directly in the XML payload
5. Set Monitoring Properties (Content Modifier) – XPath from the resulting XML → properties EDIDC_* (TABNAM, DIRECT, MESTYP, IDOCTYP, CIMTYP, SNDPOR/PRT/PFC/PRN, RCVPOR/PRT/PFC/PRN, MESCOD, MESFCT, ARCKEY)
6. Add Combined IDoc Properties – script setIDocMonitoringProperties.groovy (details below) — fails on an empty mandatory field (1.0.3)
7. Backup Log headers → Remove headers (customX*, SAP_*, customHeaderProperties, auditLogHeader)
8. Router "Content Type?" by Receiver_IDocContentType:
   - Text/XML → local process Send IDoc TextXML (bulk; after sending, Set IDocs from Bulk Response: XPath //*:DbId from the response → Set IDoc Numbers by the script displayBulkIDs.groovy — all IDoc numbers from the response into the MPL, new in 1.0.2)
   - x-sap.idoc (default) → local process Send IDoc X-SAP.IDOC (single IDoc) → Set IDoc Numbers
9. Add Custom Headers → End

## Local process: IDoc Validation

1. Check if XML body → non-XML → status IDocValidationException ("Not supported message body format") → exception
2. Add missing EDI_DC40 – script addEdidcNodeIfMissing.groovy: if the IDOC node lacks EDI_DC40, it inserts it (with TABNAM and DIRECT) in the first position, preserving attributes (BEGIN="1")
3. Set XPath Properties – isPackage = count(//*:IDOC) > 1, isARCKEY (non-empty ARCKEY), MESTYP/IDOCTYP/CIMTYP
4. Router "Check ContentType":
   - Bulk (isPackage) → Force Text/XML (header SAP_Receiver_IDocContentType = Text/XML — bulk supports only this content type)
   - Single + ARCKEY → Force X-SAP.IDoc (Application/x-sap.idoc)
   - Single without ARCKEY → unchanged
5. Router "Check if EOIO": SapPlainSoapQoS = ExactlyOnceInOrder without ARCKEY → status IDocValidationException ("Invalid IDoc structure for QoS EOIO…") → exception; otherwise OK

## Sending processes (Send IDoc TextXML / X-SAP.IDOC)

Both have a router "Location & Auth?" by Receiver_IDocProxyType + Receiver_IDocAuthenticationType with six channels — i.e. 12 IDoc receiver channels in total:

| Channel | Proxy | Authentication |
|---|---|---|
| OnPrem-Basic / OnPrem-None / OnPrem-PP | On-Premise (Cloud Connector) | Basic / None / Principal Propagation |
| Internet-Basic / Internet-None / Internet-Cert | Internet | Basic / None / Client Certificate |

All of them point to ${property.Receiver_IDocEndpoint}; the Basic channels use ${property.Receiver_IDocCredentialName}. An unsupported combination → status NotExpectedConfigurationOption → exception.

## Error handling (local process Exception Handling)

A shared process called from all Exception Subprocesses: Add Custom Headers → router "Audit Log Header?" → Recover Audit Log → Add Exception to the Log (MPL attachment, scope according to Exception_LoggingConfiguration = AuditLog,Payload) → auditLogHandler.groovy → logMessage.groovy → End. The error is then propagated via ProcessDirect back to Step07/Step06, where the JMS retry takes place.

## Scripts – where and why they are called

| Script | Called in step | Purpose |
|---|---|---|
| checkWellFormedXML.groovy | Validation: Check if XML body | Header isWellFormedXml |
| addEdidcNodeIfMissing.groovy | Validation: Add missing EDI_DC40 | Adding the missing control record to each IDOC node (TABNAM, DIRECT; receiver interface from the header) |
| setAllDefaultAndScenarioValuesFromPDForIDoc.groovy | Read and Set PD values | (1) PD parameters <key>_<Receiver> by SAP_ReceiverAlias → connectivity default_Receiver_IDoc*; (2) LS_<Receiver>/ LS_<Sender> → logical systems default_RCVPRN/ default_SNDPRN; (3) receiver interface; (4) EDIDC overrides from the PD by partnerID and the suffix from pidParameterReadSuffixPattern (<key>_MESTYP.IDOCTYP.CIMTYP — the values are read from the EDI_DC40 payload); (5) fixed EDIDC items from Default_EDIDC_List_* |
| setEdidcFromParamsForIDoc.groovy | Set Properties & Adjust EDIDC | Final values: connectivity with the precedence SAP_* header → default_* → empty; EDIDC fields with the precedence SAP_IDoc_EDIDC_* header → default_* property and overwriting in the XML of each IDOC/EDI_DC40 (rebuild of the payload); overwritten fields to customHeader_overrideResultValues |
| setIDocMonitoringProperties.groovy | Add Combined IDoc Properties | Validation of mandatory fields (TABNAM, DIRECT, MESTYP, IDOCTYP, SNDPOR, SNDPRT, SNDPRN, RCVPOR, RCVPRT, RCVPRN) — empty → IllegalStateException (1.0.3); MPL custom headers IDocSenderPartner (SNDPRT.SNDPFC.SNDPRN.SNDPOR — partner function from 1.0.3), IDocReceiverPartner, IDocTypeDetails (MESTYP[-MESCOD-MESFCT].IDOCTYP[.CIMTYP]), ARCKEY |
| displayBulkIDs.groovy | Set IDoc Numbers | IDoc numbers from the response (//DbId, SapIDocDbId/ SapIDocTransferId) into the MPL — individually or as JSON according to IDocIndividualHeaderInBulk |
| addCustomHeaderProperties.groovy | Add Custom Headers (main flow + exception handling) | MPL custom headers |
| throwException.groovy | Throw Exception (validation, unsupported connectivity) | Exception with exceptionMessage |
| auditLogHandler.groovy + logMessage.groovy | Exception handling (local process) | Audit log + logging according to the configuration |
| PipelineLogger.groovy / PipelineHelper.groovy | – (libraries) | Audit log / helpers |

## Externalized parameters (selection)

| Parameter | Default | Meaning |
|---|---|---|
| ProcessDirect_Address | /generic/step08/idoc | Entry endpoint (unique when there are multiple instances) |
| IDoc_Address | http://<abap_virtual_host>:<abap_virtual_port>/sap/bc/srt/IDoc?sap-client=<client> | Default IDoc endpoint |
| IDoc_ProxyType / IDoc_AuthenticationType | OnPremise / Basic | Default connectivity |
| IDoc_ContentType | Application/x-sap.idoc | Default content type (bulk enforces Text/XML) |
| Default_SNDPOR/SNDPRT/SNDPRN | SAPBTP / LS / ISuite | Default sender in EDIDC |
| Default_RCVPOR/RCVPRT/RCVPRN | SAPSID / LS / SIDCLNTXYZ | Default receiver in EDIDC |
| Default_EDIDC_List_Names/Values | TABNAM,MANDT,DIRECT = EDI_DC40,100,2 | Fixed EDIDC items that are added |
| pidParameterReadSuffixPattern | MESTYP.IDOCTYP.CIMTYP | Suffix of PD parameters for interface-specific EDIDC overrides |
| IDocIndividualHeaderInBulk | true | IDoc numbers in the MPL individually vs. as a JSON list |
| IDoc_CompressMessage / IDoc_AllowChunking / IDoc_KeepAlive | true / true / 1 | HTTP tuning |
| Exception_LoggingConfiguration | AuditLog,Payload | Scope of logging on error |

## Script details

### checkWellFormedXML.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message
 import src.main.resources.script.PipelineLogger

 def Message processData(Message message) {
     Reader reader = message.getBody(Reader)
         def properties = message.getProperties()
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         def location = properties.get("location") ?: "Check If XML"
     try {
         def xml = new XmlSlurper().parse(reader)
         message.setProperty("isWellFormedXml", 'true')
                 logger.addEntry(location, "Property isWellFormedXml=true; message body is well-formed XML.")
     }
     catch (ex)
     {
         message.setProperty("isWellFormedXml", 'false')
                 logger.addEntry(location, "Property isWellFormedXml=false; message body is not well-formed
 XML.")
     }

         if (logger.getLog()) { logger.saveToMessage(message) }
     return message
 }
```

**Logic:** Parses the body as XML, sets the header isWellFormedXml. A non-XML body → the validation ends with the exception IDocValidationException. **Input:** body → **Output:** header isWellFormedXml

### addEdidcNodeIfMissing.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper
import groovy.xml.StreamingMarkupBuilder

def Message processData(Message message) {
    // Parse the incoming XML body
    def headers = message.getHeaders()
    def body = message.getBody(java.io.Reader)
    def xml = new XmlSlurper().parse(body)
    boolean rebuildXml = false

    // fetch receiver interface name
    def receiverInterface = headers.get("SAP_ReceiverInterface") ?: headers.get("SAP_MessageType")

    // Find each IDOC node
    xml.IDOC.each { idocNode ->
        // Check if the EDI_DC40 child node is missing.
        if (idocNode.EDI_DC40.size() == 0) {

            rebuildXml = true
            // Get all other existing children from the original IDOC node
            def existingChildren = idocNode.children()

            // Use 'replaceNode' to rebuild the IDOC node completely.
            // This gives us full control over the order of child elements.
            idocNode.replaceNode {
                // Recreate the IDOC node, preserving its original attributes (like BEGIN="1")
                IDOC(idocNode.attributes()) {
                    // Add the new EDI_DC40 node and its children TABNAM and DIRECT first.
                    EDI_DC40(SEGMENT: '1', '')
                    {
                        TABNAM('EDI_DC40')
                        DIRECT('2')
                        MESTYP(determineIdocMessageType(receiverInterface).MESTYP)
                        IDOCTYP(determineIdocMessageType(receiverInterface).IDOCTYP)
                        CIMTYP(determineIdocMessageType(receiverInterface).CIMTYP)
                    }
                    // 'mkp.yield' inserts the collection of original children after the new node.
                    mkp.yield(existingChildren)
                }
            }
        }
    }

    if (rebuildXml) {

        // Rebuild the final XML from the modified structure
        def updatedXml = new StreamingMarkupBuilder().bind {
            // 'mkp.yield' renders the entire modified 'xml' object.
            mkp.yield(xml)
        }

        // Set the modified XML as the new message body
        message.setBody(updatedXml.toString())

    }

    return message

}

// Determine MESTYP, IDOCTYP, and CIMTYP
Map determineIdocMessageType(String input) {
    String CIMTYP = null
        String MESTYP = null
        String IDOCTYP = null

    if (input?.contains(".")) {
        def kv = input.split(/\./, 2)
        MESTYP  = kv[0]?.trim()
        IDOCTYP = kv[1]?.trim()

         if (IDOCTYP?.contains(".")) {
             kv = IDOCTYP.split(/\./, 2)
             IDOCTYP  = kv[0]?.trim()
             CIMTYP = kv[1]?.trim()
         }
     }

     return [ MESTYP: MESTYP, IDOCTYP: IDOCTYP, CIMTYP: CIMTYP ]
 }
```

**Logic:** For each IDOC node that lacks EDI_DC40, it inserts it in the first position (preserves the node's attributes, e.g. BEGIN="1") and fills TABNAM = EDI_DC40, DIRECT = 2 and MESTYP/IDOCTYP/CIMTYP parsed from the header SAP_ReceiverInterface (otherwise SAP_MessageType) in the format MESTYP.IDOCTYP[.CIMTYP]. It keeps the original children of IDOC after the new node. If no node is missing, it does not rewrite the payload. **Input:** body (IDoc XML), headers SAP_ReceiverInterface/ SAP_MessageType → **Output:** modified body

### setAllDefaultAndScenarioValuesFromPDForIDoc.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message
 import com.sap.it.api.pd.PartnerDirectoryService
 import com.sap.it.api.ITApiFactory
 import src.main.resources.script.PipelineLogger
 import groovy.util.XmlSlurper

 def Message processData(Message message) {

     def headers = message.getHeaders()
         def properties = message.getProperties()
     String ReceiverAlias = headers.get("SAP_ReceiverAlias")
         String Receiver = headers.get("SAP_Receiver")
         String SenderAlias = headers.get("SAP_SenderAlias")
         String Sender = headers.get("SAP_Sender")
         def location = properties.get("location") ?: "Set IDoc Parameters"
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
         def aliasKeys = [
         "IDocEndpoint",
         "IDocCCLocationId",
         "IDocCredentialName",
         "IDocPrivateKeyAlias",
                 "IDocProxyType",
                 "IDocAuthenticationType",
                 "IDocContentType"
     ]
         String prefixLS = 'IDocLogicalSystem'

     def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
     if (service == null){
                 // simply exit as parameters that are checked in this script are optional
         return message
     }

         //--------------- 1. Set Default Properties ---------------------
         // (i.e. parameters that can override Default values in IFlow Externalized Configuration)

         // 1.1 Read String Parameters linked to System Alias in Partner Directory (connectivity settings)
         if (ReceiverAlias && Receiver) {
                 aliasKeys.each { key ->
                         String stringParameterValue = service.getParameter("${key}_${Receiver}", ReceiverAlias,
 String)
                         // if any parameter found - it will replace default value from Externalized Parameters
                         if (stringParameterValue != null) {
                                 logger.addEntry(location, "Parameter ${key}_${Receiver} for receiver alias
 ${ReceiverAlias} is found in Partner Directory and stored as Property default_Receiver_${key} with value
 ${stringParameterValue}.")
                                 message.setProperty("default_Receiver_${key}", stringParameterValue?.trim())
                         }
                 }
         }

         // 1.2 Determine default Logical System name for Sender and Receiver names (expected to be used when

SNDPRN and RCVPRN are of type LS)
        if (ReceiverAlias && Receiver) {
                String receiverLogicalSystem = service.getParameter("${prefixLS}_${Receiver}", ReceiverAlias,
String)
                if (receiverLogicalSystem != null) { // override default_RCVPRN if parameter exists in PD
                        message.setProperty("default_RCVPRN", receiverLogicalSystem.trim())
                        logger.addEntry(location, "Default RCVPRN is set to value ${receiverLogicalSystem} from
String Parameter ${prefixLS}_${Receiver} of Receiver system alias ${ReceiverAlias} in Partner Directory.")
                }
        }
        if (SenderAlias && Sender) {
                String senderLogicalSystem = service.getParameter("${prefixLS}_${Sender}", SenderAlias, String)
                if (senderLogicalSystem != null) { // override default_SNDPRN if parameter exists in PD
                        message.setProperty("default_SNDPRN", senderLogicalSystem.trim())
                        logger.addEntry(location, "Default SNDPRN is set to value ${senderLogicalSystem} from
String Parameter ${prefixLS}_${Sender} of Sender system alias ${SenderAlias} in Partner Directory.")
                }
        }

        // 1.3 Determine Receiver Interface Name
        String receiverInterface = headers.get("SAP_ReceiverInterface")
        if (!receiverInterface) {
                receiverInterface = "${properties.get('SAP_IDoc_EDIDC_MESTYP')}.${properties.get
('SAP_IDoc_EDIDC_IDOCTYP')}${properties.get('SAP_IDoc_EDIDC_CIMTYP') ? '.' + properties.get
('SAP_IDoc_EDIDC_CIMTYP') : ''}"
                message.setHeader("SAP_ReceiverInterface",
receiverInterface)

        logger.addEntry(location, "Derived SAP_ReceiverInterface = ${receiverInterface}.")
    } else {
        logger.addEntry(location, "Header SAP_ReceiverInterface already provided = ${receiverInterface}.")
    }

        // 1.4 Determine suffix to be used to extract Interface-Specific values from Partner Directory per
Partner ID
        def interfaceSuffix = headers.get("SAP_IDoc_EDIDC_pidParameterReadSuffixPattern") ?: properties.get
("pidParameterReadSuffixPattern")
        if (!interfaceSuffix || interfaceSuffix in ['MESTYP.IDOCTYP.CIMTYP', 'SAP_ReceiverInterface']) { //
default setting, read parameters with ReceiverInterface suffix
                interfaceSuffix = receiverInterface
                logger.addEntry(location, "Using default suffix = ReceiverInterface (${interfaceSuffix}).")
        } else { // suffix to be built dynamically using the supplied EDIDC segments concatenated with a dot if
not empty value
        def values = []
        try {
            def body = message.getBody(java.io.Reader)
            def xml = new XmlSlurper().parse(body) // works with or without namespaces
            def params = interfaceSuffix.split("\\.")
            def ediDc40 = xml.IDOC.EDI_DC40
            if (ediDc40) {
                values = params.collect { p -> ediDc40."$p".text()?.trim() ?: "" }.findAll { it }
            }
        } catch (Exception e) {
            logger.addEntry(location, "Failed to parse IDoc XML body for interfaceSuffix building: ${e.
message}")
        }
        interfaceSuffix = values ? values.join(".") : receiverInterface
        logger.addEntry(location, "Built dynamic suffix = ${interfaceSuffix}.")
        }

        // 1.5 Determine default values of EDIDC values provided in the Externalized Parameters and set them as
individual properties
        def controlHeaderNames = properties.get("default_EDIDC_List_Names")
        def controlHeaderValues = properties.get("default_EDIDC_List_Values")
        if (controlHeaderNames && controlHeaderValues) {
                def names  = controlHeaderNames.split(",", -1) as List
                def values = controlHeaderValues.split(",", -1) as List
                if (names.size() == values.size()) {
                        names.eachWithIndex { name, i ->
                                def value = values[i]
                                if (name) {

                                         message.setProperty("default_${name.trim()}", value?.trim())
                                 }
                         }
                 } else {
                         logger.addEntry(location, "Externalized Parameters default_EDIDC_List_Names has value
 ${controlHeaderNames} and default_EDIDC_List_Values has value ${controlHeaderValues} - the sizes between names
 and values are not matching after splitting with supported comma delimiter.")
                 }
         }

         //--------------- 2. Set Scenario-Specific Properties ---------------------
         // (precedence for incoming headers with prefix  SAP_IDoc_EDIDC_, if no such header - check Partner
 Directory by Partner ID)

         // For each parameter of Header SAP_IDoc_EDIDC_overrideControlRecordParameters - check if
 SAP_IDoc_EDIDC_ exists, if not - read partner directoty with PID searching for parameter with previously
 defined interfaceSuffix
         String Pid = headers.get("partnerID")
         String overrideEDIDCparams = headers.get("SAP_IDoc_EDIDC_overrideControlRecordParameters")
         if (overrideEDIDCparams && Pid && interfaceSuffix) {
         overrideEDIDCparams.split(",").each { key ->
             key = key.trim()
             String headerName = "SAP_IDoc_EDIDC_${key}"
             if (!headers.containsKey(headerName)) {
                 String pdValue = service.getParameter("${key}_${interfaceSuffix}", Pid, String)
                 if (pdValue != null) {
                     message.setHeader(headerName, pdValue?.trim())
                                         logger.addEntry(location, "Control Record segment ${key} is part of
 header SAP_IDoc_EDIDC_overrideControlRecordParameters and there is no ${headerName} from scenario-specific
 IFlow, that's why header value ${pdValue} is set from Partner Directory parameter ${key}_${interfaceSuffix} of
 Partner ID ${Pid}.")
                 }
             }
         }
         } else if (!Pid) {
                 logger.addEntry(location, "No PartnerID found in headers, cannot resolve
 SAP_IDoc_EDIDC_overrideControlRecordParameters from PD.")
         }

         if (logger.getLog()) { logger.saveToMessage(message) }
     return message
 }
```

**Logic:** Populates defaults and scenario values from the Partner Directory (when the PD service is not available, it silently ends — the parameters are optional). (1) By SAP_ReceiverAlias + SAP_Receiver it reads the connectivity IDocEndpoint/IDocCCLocationId/IDocCredentialName/IDocPrivateKeyAlias/IDocProxyType/IDocAuthenticationType/IDocContentType → properties default_Receiver_*. (2) Logical systems: IDocLogicalSystem_<Receiver>/ _<Sender> → default_RCVPRN/ default_SNDPRN. (3) Receiver interface: the header, otherwise derived from MESTYP.IDOCTYP[.CIMTYP]. (4) Builds the suffix for reading interface-specific PD parameters (pidParameterReadSuffixPattern; default = ReceiverInterface, otherwise composed from the EDIDC segments of the payload). (5) Fixed EDIDC items from default_EDIDC_List_Names/Values → properties default_<field>. (6) Scenario EDIDC overrides: for each field from SAP_IDoc_EDIDC_overrideControlRecordParameters that did not arrive as a header, it looks up <field>_<suffix> in the PD by partnerID. **Input:** headers SAP_ReceiverAlias/SenderAlias/Receiver/Sender, partnerID, SAP_IDoc_EDIDC_*; PD parameters → **Output:** properties default_*; headers SAP_IDoc_EDIDC_*, SAP_ReceiverInterface

### setEdidcFromParamsForIDoc.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message
 import groovy.util.XmlSlurper
 import groovy.xml.StreamingMarkupBuilder
 import src.main.resources.script.PipelineLogger

 def Message processData(Message message) {
     def headers = message.getHeaders()
     def properties = message.getProperties()
         def body = message.getBody(java.io.Reader)
         def xml = new XmlSlurper().parse(body)
         def location = properties.get("location") ?: "Override EDIDC Parameters"
         def nsUri = "urn:sap-com:document:sap:idoc:soap:messages"
         PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")

    def keysConnectivity = [
        "Receiver_IDocEndpoint",
        "Receiver_IDocCCLocationId",
        "Receiver_IDocCredentialName",
        "Receiver_IDocPrivateKeyAlias",
                "Receiver_IDocProxyType",
                "Receiver_IDocAuthenticationType",
                "Receiver_IDocContentType"
    ]

        // 1. IDoc connectivity related parameters: set final Properties
        keysConnectivity.each { key ->
                String headerName = "SAP_${key}"
                String propertyName = "default_${key}"
                def finalValue

                // 1.1. Headers coming with prefix "SAP_" from interface-spepcific IFlow has precedence
        if (headers.containsKey(headerName)) {
            finalValue = headers.get(headerName)
        }
        // 1.2. If no "SAP_" header - use "default_" property from externalized configuration
        else if (properties.containsKey(propertyName)) {
            finalValue = properties.get(propertyName)
                        logger.addEntry(location, "No scenario-specific header ${headerName} found, property
${key} is assigned to value ${finalValue} of default property ${propertyName}.")
        }
        // 1.3. Fallback is empty value
        else {
                        finalValue = ""
                        logger.addEntry(location, "No header ${headerName} and no property ${propertyName},
fallback empty value is assigned to property ${key}.")
        }
                message.setProperty(key, finalValue)

        }

        // 2. IDoc content related parameters: override only control record attributes maintained in the header
        String overrideEDIDCparams = headers.get("SAP_IDoc_EDIDC_overrideControlRecordParameters")
        def resultValuesList = []

        if (overrideEDIDCparams) {
        overrideEDIDCparams.split(",").each { key ->
            key = key.trim()
                        String headerName = "SAP_IDoc_EDIDC_${key}"
                        String propertyName = "default_${key}"
                        def finalValue

                         // 2.1. Headers coming with prefix "SAP_IDoc_EDIDC_" from interface-spepcific IFlow
has precedence
                        if (headers.containsKey(headerName)) {
                                finalValue = headers.get(headerName)
                        }
                        // 2.2. If no "SAP_IDoc_EDIDC_" header - use "default_" property from externalized
configuration
                        else if (properties.containsKey(propertyName)) {
                                finalValue = properties.get(propertyName)
                                logger.addEntry(location, "No scenario-specific header ${headerName} found,
property ${key} is assigned to value ${finalValue} of default property ${propertyName}.")
                        }
                        // 2.3. Fallback is empty value
                        else {
                                finalValue = ""
                                logger.addEntry(location, "No header ${headerName} and no property
${propertyName}, fallback empty value is assigned to property ${key}.")
                        }

                        message.setProperty(key, finalValue)
                        resultValuesList << finalValue

                        // update XML for every IDOC/EDI_DC40
            xml.IDOC.EDI_DC40.each { edidc ->

                 def existingNode = edidc."${key}"
                 if (existingNode && existingNode.size() > 0) {
                     existingNode.replaceBody(finalValue)
                 } else {
                     edidc.appendNode { "${key}"(finalValue) }
                 }
             }
         }
                 message.setProperty("customHeader_overrideResultValues", resultValuesList.join(","))

                 // Rebuild XML with updated values
                 def updatedXml = new StreamingMarkupBuilder().bind {
                         mkp.declareNamespace("ns0": nsUri)
                         mkp.yield xml
                 }
                 message.setBody(updatedXml.toString())
         }

         if (logger.getLog()) { logger.saveToMessage(message) }
     return message
 }
```

**Logic:** Builds the final values. (1) Connectivity (Receiver_IDocEndpoint/…/ContentType): precedence header SAP_<key> → property default_<key> → empty, the result goes to properties. (2) EDIDC fields from SAP_IDoc_EDIDC_overrideControlRecordParameters: the same precedence (SAP_IDoc_EDIDC_<field> → default_<field> → empty) and the value is overwritten directly in the XML of each IDOC/EDI_DC40 (an existing element is overwritten, a missing one is added), then the payload is rebuilt (namespace urn:sap-com:document:sap:idoc:soap:messages). The overwritten fields are written to customHeader_overrideResultValues. **Input:** headers SAP_*/ SAP_IDoc_EDIDC_*, properties default_*, body → **Output:** connectivity properties, modified body, customHeader_overrideResultValues

### setIDocMonitoringProperties.groovy

```groovy
 import com.sap.gateway.ip.core.customdev.util.Message

 def Message processData(Message message) {
     def properties = message.getProperties()
     def edidcKeys = [
         "EDIDC_MESCOD",
         "EDIDC_MESFCT",
         "EDIDC_ARCKEY",
         "EDIDC_RCVPRT",
         "EDIDC_RCVPFC",
         "EDIDC_RCVPRN",
         "EDIDC_RCVPOR",
         "EDIDC_SNDPRT",
         "EDIDC_SNDPFC",
         "EDIDC_SNDPRN",
         "EDIDC_SNDPOR",
         "EDIDC_CIMTYP",
         "EDIDC_IDOCTYP",
         "EDIDC_MESTYP",
         "EDIDC_TABNAM",
         "EDIDC_DIRECT"
     ]

     // Read EDIDC properties into a map, removing prefix
     def edidcValues = edidcKeys.collectEntries { key ->
         def val = properties.get(key)
         [(key.replace("EDIDC_", "")) : val?.trim()] // trim whitespace
     }

     // Collect all mandatory keys where the value is empty
     def emptyMandatoryValueKeys = ["TABNAM","DIRECT","MESTYP","IDOCTYP","SNDPOR","SNDPRT","SNDPRN","RCVPOR","
 RCVPRT","RCVPRN"].findAll { !edidcValues[it] }
     //if (emptyMandatoryValueKeys) message.setProperty("customHeader_EmptyMandatoryControlRecords",
 emptyMandatoryValueKeys.join(","))
     // Raise an error in case of an empty mandatory IDoc control record field
     if (emptyMandatoryValueKeys) throw new IllegalStateException("The following mandatory IDoc control record
 fields are empty which would result into an error in the receiving system: " + emptyMandatoryValueKeys.join
 (","))

     // Build new monitoring properties, skipping empty segments
     def senderParts = ["SNDPRT","SNDPFC","SNDPRN","SNDPOR"].collect { edidcValues[it] }.findAll { it }
     if (senderParts) message.setProperty("customHeader_IDocSenderPartner", senderParts.join("."))

     def receiverParts = ["RCVPRT","RCVPFC","RCVPRN","RCVPOR"].collect { edidcValues[it] }.findAll { it }
     if (receiverParts) message.setProperty("customHeader_IDocReceiverPartner", receiverParts.join("."))

     def typeParts = ["MESTYP","MESCOD","MESFCT"].collect { edidcValues[it] }.findAll { it }
     def idocTypeParts = ["IDOCTYP","CIMTYP"].collect { edidcValues[it] }.findAll { it }
     def allTypeParts = []
     if (typeParts) allTypeParts << typeParts.join("-")
     if (idocTypeParts) allTypeParts << idocTypeParts.join(".")
     if (allTypeParts) message.setProperty("customHeader_IDocTypeDetails", allTypeParts.join("."))

     if (edidcValues.ARCKEY) message.setProperty("customHeader_ARCKEY", edidcValues.ARCKEY)

     return message
 }
```

**Logic:** From the properties EDIDC_* it verifies the mandatory fields (TABNAM, DIRECT, MESTYP, IDOCTYP, SNDPOR, SNDPRT, SNDPRN, RCVPOR, RCVPRT, RCVPRN) — if any is empty, it throws IllegalStateException (the message would end with an error in the receiving system). It then builds the MPL custom headers: IDocSenderPartner (SNDPRT.SNDPFC.SNDPRN.SNDPOR), IDocReceiverPartner (RCVPRT.RCVPFC.RCVPRN.RCVPOR), IDocTypeDetails (MESTYP[-MESCOD-MESFCT].IDOCTYP[.CIMTYP]) and ARCKEY (empty segments are omitted). **Input:** properties EDIDC_* → **Output:** exception on a missing mandatory field / MPL custom headers

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
    def individualHeaders4BulkFlag = (message.getProperty('idocBulkIndividualHeader') ?: "false").toString().
equalsIgnoreCase("true")

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

**Logic:** Writes to the MPL the IDoc numbers from the receiver's response: SapIDocDbId/ SapIDocTransferId (without leading zeros) and //DbId from the response — individually as the custom header idoc, or as a JSON list, according to the parameter IDocIndividualHeaderInBulk. **Input:** XPath //DbId from the response, headers SapIDocDbId/ SapIDocTransferId → **Output:** MPL custom headers

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

**Logic:** MPL custom headers from customHeaderProperties and the properties customHeader_* (identical to the pattern elsewhere).

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

**Logic:** An exception of the type according to exceptionType with the message from exceptionMessage — here on IDoc validation or unsupported connectivity.

### auditLogHandler.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger

def Message attachAuditLog(Message message) {
    def properties = message.getProperties()
        PipelineLogger logger = PipelineLogger.newLogger(message, 'auditLog')

        def attachmentName = properties.get('mplAttachmentName') ?: 'Audit Log Information'
        def mediaType = properties.get('mplAttachmentMediaType') ?: 'text/plain'

        // optional
        // if log entry to be added before MPL attachment (to avoid calling addAuditLog)
        def logText = properties.get('messageText') ?: ''
        def location = properties.get('location') ?: 'Attach Audit Log Script'
        if (logText) {
                logger.addEntry(location, logText)
        }

    // attach Audit Log Header to the MPL
    def messageLog = messageLogFactory.getMessageLog(message);
    if (messageLog) {
                messageLog.addAttachmentAsString(attachmentName, logger.getLog4Print(), mediaType)
        }

    return message
}

def Message addAuditLog(Message message) {
    def properties = message.getProperties()
        PipelineLogger logger = PipelineLogger.newLogger(message, 'auditLog')

        def logText = properties.get('messageText') ?: ''
        def location = properties.get('location') ?: 'Add Audit Log Script'

        if (logText) { // if log entry is supplied in property messageText - then add it to the Audit Log
                logger.addEntry(location, logText)
        }

        if (logger.getLog()) { // save audit log
                logger.saveToMessage(message)
        }
    return message
}
```

### logMessage.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger

def Message processData(Message message) {

    def body = message.getBody(String)
    def properties = message.getProperties()
    def headers = message.getHeaders()
    def messageLog = messageLogFactory.getMessageLog(message)

    def mplAttachmentConfiguration = properties.get("mplLoggingConfiguration") ?: "AuditLog" // AuditLog,
Payload,Property,Header
    def mplAttachmentPrefix = properties.get("mplAttachmentName") ?: "Logging"
    def mediaType = properties.get("mediaType") ?: "text/plain"

    List excludedHeaders = ['SAP_MessageProcessingLogID', 'SAP_MonitoringStateProperties', 'MplMarkers',
'SAP_MessageProcessingLog', 'auditLogHeader', 'SAP_AuthHeaderName', 'SAP_AuthHeaderValue']
    List excludedProperties = ['CamelMessageHistory', 'SAP_MessageProcessingLog', 'auditLogProperty',
'SAP_AuthHeaderName', 'SAP_AuthHeaderValue']

     if (messageLog) {
         // normalize configuration into a lowercase set for easy contains()
                 def configSet = mplAttachmentConfiguration
                         .toLowerCase()
                         .tokenize(',;|')  // tokenize accepts a string of delimiters
                         .collect { it.trim() }
                         .toSet()

         // --- Payload ---
         if (["payload","body","messagebody"].any { configSet.contains(it) }) {
             if (body?.trim()) { // non empty body
                 messageLog.addAttachmentAsString("${mplAttachmentPrefix}MessageBody", body, mediaType)
             }
         }

         // --- Headers ---
         if (configSet.contains("header") || configSet.contains("headers")) {
             def headersSB = new StringBuilder()
             def filteredHeaders = headers.findAll { k, v -> !(k in excludedHeaders) }
             dumpKeyValueAsTEXT("Headers", filteredHeaders, headersSB)
             messageLog.addAttachmentAsString("${mplAttachmentPrefix}Headers", headersSB.toString(), mediaType)
         }

         // --- Properties ---
         if (configSet.contains("property") || configSet.contains("properties")) {
             def propsSB = new StringBuilder()
             def filteredProps = properties.findAll { k, v -> !(k in excludedProperties) }
             dumpKeyValueAsTEXT("Properties", filteredProps, propsSB)
             messageLog.addAttachmentAsString("${mplAttachmentPrefix}Properties", propsSB.toString(), mediaType)
         }

         // --- AuditLog ---
         if (configSet.contains("auditlog")) {
             PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
             if (logger.getLog()) {
                 messageLog.addAttachmentAsString("${mplAttachmentPrefix}AuditLog", logger.getLog4Print(),
 mediaType)
             }
         }
     }

     return message
 }

 static void dumpKeyValueAsTEXT(String title, Map<String, Object> map, StringBuilder sb) {
     sb.append(title).append('\n')
     map.each { key, value ->
         sb.append(String.format(" %-40s: %-40s\n", key, value))
     }
     sb.append('\n')
 }
```

**Logic:** In the local process Exception handling it saves the audit log and logs according to Exception_LoggingConfiguration.

## Libraries

PipelineLogger.groovy — audit log. PipelineHelper.groovy — Base64/JSON helpers.
