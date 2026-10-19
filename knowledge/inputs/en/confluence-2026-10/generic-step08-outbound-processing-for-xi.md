<!-- Translated from Confluence page "Generic Step08 – Outbound Processing for XI" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Step08 – Outbound Processing for XI

Version: 1.0.2 (manifest) / contains functions of version 1.0.3 · Package: Cloud Integration Pipeline – Generic Outbound

## Purpose

A generic XI receiver step: the last link of the pipeline, which physically sends the message to the receiving ABAP system via the XI protocol. It is called via ProcessDirect from the scenario-specific outbound flow (Step07). It composes the connectivity (endpoint, proxy, authentication, credentials) from a three-layer precedence: SAP_* headers from the scenario → parameters from the Partner Directory → externalized parameters of the flow — and according to the resulting combination of proxy type and authentication it selects one of six preconfigured XI receiver channels.

> Common to both runtimes — the physical delivery is the same in the integrated and the fully decoupled pipeline.

## Input (ProcessDirect Sender)

Endpoint {{ProcessDirect_EndPoint}} = /generic/step08/xi. It lets through only the headers according to AllowedHeadersList (SAP_Sender*, SAP_Receiver*, SAP_MessageType, SAP_ApplicationID, customX*, dc*, partnerID, testMode, auditLogHeader, UniqueID, SapQualityOfService, SapQueueId, …).

## Main flow (Integration Process)

1. Create dynamic configurations – script createSapDynamicConfigurationFromHeaders.groovy: headers dc_SapDynamicConfiguration_* (passed from the scenario) → properties SapDynamicConfiguration_*, which the XI receiver adapter uses as dynamic XI headers of the outgoing message (since version 1.0.3; the mirror counterpart of the inbound script createHeadersFromSapDynamicConfiguration)
2. Set Default Properties (Content Modifier) – externalized parameters → properties default_*: XI_Address → default_Receiver_XIEndpoint, XI_ProxyType, XI_AuthenticationType, CredentialName, XI_PrivateKeyAlias, LocationID, XI_Sender(Party), XI_Receiver(Party); into the MPL: UniqueID, SapQualityOfService, SapQueueId
3. Check if XML body – script checkWellFormedXML.groovy → router "XML?": XML → Set XPath Properties (default_ReceiverInterface = local-name of the root, default_ReceiverInterfaceNamespace = its namespace — the last fallback for the interface); non-XML → skipped
4. Read PD values – script readPDValuesForXI.groovy (detail below) — overwrites the defaults with values from the Partner Directory for the specific receiver
5. Set values for XI as properties – script setValuesForXI.groovy (detail below) — final values according to the precedence SAP_* header → default_* property → empty
6. Add Custom Headers → Backup Log headers (auditLogHeader → property auditLogProperty, so that it survives the cleanup) → Remove headers (deletes dc_*, customX*, SAP_*, customHeaderProperties, auditLogHeader — pipeline headers must not leak to the receiver)
7. Router "Location & Auth?" by Receiver_XIProxyType + Receiver_XIAuthenticationType → six XI receiver channels:

| Channel | Proxy | Authentication | Note |
|---|---|---|---|
| OnPrem-Basic | On-Premise (Cloud Connector) | Basic | default according to parameters |
| OnPrem-None | On-Premise | None | |
| OnPrem-PP | On-Premise | Principal Propagation | only for SapQualityOfService = BestEffort (sync) |
| Internet-Basic | Internet | Basic | |
| Internet-None | Internet | None | |
| Internet-Cert | Internet | Client Certificate | |
| (other combination) | – | – | status NotExpectedConfigurationOption → throwException |

## Error handling (Exception Subprocess)

Without retry logic — retry is handled by the calling Step06. Here it only logs and the error is propagated: router "Audit Log Header?" → optionally Recover Audit Log (from the property auditLogProperty) → Add Exception to the Log (MPL attachment ExceptionSubProcess_*, scope according to the parameter Exception_LoggingConfiguration = AuditLog,Payload,Property,Header) → auditLogHandler.groovy → logMessage.groovy → Error End (the error is returned via ProcessDirect to Step07/Step06, where the JMS retry takes place).

## Scripts – where and why they are called

| Script | Called in step | Purpose |
|---|---|---|
| createSapDynamicConfigurationFromHeaders.groovy | Create dynamic configurations | dc_SapDynamicConfiguration_* headers → properties for dynamic XI headers (1.0.3) |
| checkWellFormedXML.groovy | Check if XML body | Header isWellFormedXML |
| readPDValuesForXI.groovy | Read PD values | (1) PD parameters XIEndpoint_<Receiver>, XICCLocationId_…, XICredentialName_…, XIPrivateKeyAlias_…, XIProxyType_…, XIAuthenticationType_… according to SAP_ReceiverAlias → overwrite of default_Receiver_*; (2) fallback SAP_Sender/SAP_Receiver from parameters if the scenario sent them empty (1.0.3); (3) receiver interface: header → PD InterfaceNameDetermination_<Receiver> according to partnerID → SAP_MessageType; namespace: header → PD InterfaceNamespaceDetermination_<interface> |
| setValuesForXI.groovy | Set values for XI as properties | For 12 keys (endpoint, proxy, auth, credentials, sender/receiver + parties, interface + namespace) it applies the precedence SAP_<key> header → default_<key> property → empty; separates the party (party.system); fills customHeader_* for monitoring |
| addCustomHeaderProperties.groovy | Add Custom Headers | MPL custom headers |
| throwException.groovy | Throw Exception | Unsupported proxy/auth combination |
| auditLogHandler.groovy + logMessage.groovy | Exception Subprocess | Saving of the audit log and logging according to Exception_LoggingConfiguration |
| PipelineLogger.groovy / PipelineHelper.groovy | – (libraries) | Audit log / helpers |

## Externalized parameters (selection)

| Parameter | Default | Meaning |
|---|---|---|
| ProcessDirect_EndPoint | /generic/step08/xi | Input endpoint (must be unique when there are multiple instances of the flow) |
| XI_Address | http://<abap_virtual_host>:<abap_virtual_port>/sap/xi/engine?type=entry&sap-client=<client> | Default XI endpoint of the receiver |
| XI_ProxyType / XI_AuthenticationType | OnPremise / Basic | Default connectivity |
| CredentialName | <ABAP_XI_Credential> | Credential for Basic auth |
| XI_Sender / XI_SenderParty | IntegrationServerCloudIntegration / – | Default XI sender identity |
| XI_Receiver / XI_ReceiverParty | – | Default receiver (fallback, 1.0.3) |
| XI_Timeout | 60000 ms | Timeout of the XI call |
| Exception_LoggingConfiguration | AuditLog,Payload,Property,Header | Logging scope on error |
| AllowedHeadersList | see above | Whitelist of headers of the ProcessDirect sender |
| LocationID, XI_PrivateKeyAlias, HTTPSessionReuse, XI_KeepAlive, XI_CompressMessage, … | – | Fine-tuning of connectivity |

## Script details

### createSapDynamicConfigurationFromHeaders.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    // Constant: Property Prefix for Dynamic Configuration
    final String DC_PPREFIX = "SapDynamicConfiguration_"
    // Constant: Header Prefix for Dynamic Configuration
    final String DC_HPREFIX = "dc_" + DC_PPREFIX

    def headers = message.getHeaders()
    def resultDynamicConfigurations = [:] // Start with empty map

    try {
        // read all headers matching the prefix
        headers?.findAll { k, v ->
            k instanceof String && k.startsWith(DC_HPREFIX)
        }?.each { k, v ->
            def cleanKey = k.replaceFirst("^${DC_HPREFIX}", "")
            resultDynamicConfigurations[cleanKey] = v
        }
    } catch (Exception ignored) {
        // Ignore on failure
    }

    if (resultDynamicConfigurations && !resultDynamicConfigurations.isEmpty()) {
        resultDynamicConfigurations.each { key, value ->
            message.setProperty("${DC_PPREFIX}${key}", value)
        }
    }

    return message
}
```

Logic: Finds the headers with the prefix dc_SapDynamicConfiguration_ (passed from the scenario), removes the prefix and stores the values as properties SapDynamicConfiguration_<key>, which the XI receiver adapter uses as dynamic XI headers of the outgoing message. The mirror counterpart of the inbound script createHeadersFromSapDynamicConfiguration. Errors are silently ignored. Input: headers dc_SapDynamicConfiguration_* → Output: properties SapDynamicConfiguration_*

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
        logger.addEntry(location, "Property isWellFormedXml=false; message body is not well-formed XML.")
    }

    if (logger.getLog()) { logger.saveToMessage(message) }
    return message
}
```

Logic: Parses the body as XML and sets the header isWellFormedXml (true/false) — it controls whether the XPath fallback for determining the interface from the root element is applied. Input: body → Output: header isWellFormedXml

### readPDValuesForXI.groovy

The script code is not present in the source; only the script name is repeated there. [unclear in source]

Logic: From the Partner Directory it overwrites the connectivity defaults with the actual values for the specific receiver. (1) It fills a missing SAP_Receiver/SAP_Sender from the properties default_*; it cuts off the party (party.system). (2) According to SAP_ReceiverAlias and SAP_Receiver it reads the string parameters XIEndpoint_<Receiver>, XICCLocationId_…, XICredentialName_…, XIPrivateKeyAlias_…, XIProxyType_…, XIAuthenticationType_… → overwrite of the properties default_Receiver_*. (3) Receiver interface: the header SAP_ReceiverInterface, otherwise PD InterfaceNameDetermination_<Receiver> according to partnerID, otherwise SAP_MessageType. (4) Namespace: the header, otherwise PD InterfaceNamespaceDetermination_<interface>. When the PD service is not available, it silently ends (the parameters are optional). Input: headers SAP_ReceiverAlias/Receiver/Sender/ReceiverInterface/MessageType, partnerID; PD parameters → Output: properties default_Receiver_*; headers SAP_ReceiverInterface, SAP_ReceiverInterfaceNamespace

### setValuesForXI.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message
import src.main.resources.script.PipelineLogger

def Message processData(Message message) {
    def headers = message.getHeaders()
    def properties = message.getProperties()
    def location = properties.get("location") ?: "Set XI Values"
    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")

    def keys = [
        "Receiver_XIEndpoint",
        "Receiver_XICCLocationId",
        "Receiver_XICredentialName",
        "Receiver_XIPrivateKeyAlias",
        "Receiver_XIProxyType",
        "Receiver_XIAuthenticationType",
        "SenderParty",
        "Sender",
        "ReceiverParty",
        "Receiver",
        "ReceiverInterface", // ReceiverInterface: precedence ReceiverInterface, then SAP_MessageType, last fallback is XML Root stored in property default_ReceiverInterface

        "ReceiverInterfaceNamespace"
    ]

    def customHeaders = [
        "SenderParty",
        "ReceiverParty",
        "ReceiverInterface",
        "ReceiverInterfaceNamespace"
    ]

    def businessComponents = [
        "Sender",
        "Receiver",
    ]

    keys.each { key ->
        def finalValue = null
        def kv

        // 1. Headers coming with prefix "SAP_" from interface-spepcific IFlow has precedence
        if (headers.containsKey("SAP_${key}".toString())) {
            finalValue = headers.get("SAP_${key}".toString())
        }
        // 2. If no "SAP_" header - use "default_" property from externalized configuration
        else if (properties.containsKey("default_${key}".toString())) {
            finalValue = properties.get("default_${key}".toString())
            logger.addEntry(location, "No scenario-specific header SAP_${key} found, property ${key} is assigned to value ${finalValue} of default property default_${key}.")
        }
        // 3. Fallback is empty value
        else {
            finalValue = ""
            logger.addEntry(location, "No header SAP_${key} and no property default_${key}, fallback empty value ${finalValue} is assigned to property ${key}.")
        }

        // If key belongs to businessComponents list, remove party if any
        if (key in businessComponents) {
            kv = finalValue.split(/\./, 2)
            if (kv?.size() == 2) {
                finalValue = kv[1]?.trim()
                message.setProperty("customHeader_${key}System", finalValue)
            }
        }

        message.setProperty(key, finalValue)

        // If key belongs to customHeaders list
        if (key in customHeaders) {
            message.setProperty("customHeader_${key}", finalValue)
        }
    }

    if (logger.getLog()) { logger.saveToMessage(message) }
    return message
}
```

Logic: For 12 keys (endpoint, CC location, credential, private key, proxy, auth, sender/receiver + their parties, interface + namespace) it determines the final value according to the precedence: (1) header SAP_<key> → (2) property default_<key> → (3) empty. For business components (Sender/Receiver) it cuts off the party (party.system) and stores customHeader_<key>System. The selected keys (SenderParty, ReceiverParty, ReceiverInterface, ReceiverInterfaceNamespace) are additionally written to customHeader_* for monitoring. Input: headers SAP_*, properties default_* → Output: properties (final values for the adapter) + customHeader_*

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

Logic: MPL custom headers from the header customHeaderProperties (string k:v|k:v or Base64 JSON) and the properties customHeader_*. Input: header customHeaderProperties, properties customHeader_* → Output: MPL custom headers

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

Logic: Throws an exception of the type according to exceptionType with the message from exceptionMessage — here for an unsupported proxy/auth combination. Input: properties exceptionMessage, exceptionType → Output: exception

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

    def mplAttachmentConfiguration = properties.get("mplLoggingConfiguration") ?: "AuditLog" // AuditLog, Payload,Property,Header
    def mplAttachmentPrefix = properties.get("mplAttachmentName") ?: "Logging"
    def mediaType = properties.get("mediaType") ?: "text/plain"

    List excludedHeaders = ['SAP_MessageProcessingLogID', 'SAP_MonitoringStateProperties', 'MplMarkers', 'SAP_MessageProcessingLog', 'auditLogHeader', 'SAP_AuthHeaderName', 'SAP_AuthHeaderValue']
    List excludedProperties = ['CamelMessageHistory', 'SAP_MessageProcessingLog', 'auditLogProperty', 'SAP_AuthHeaderName', 'SAP_AuthHeaderValue']

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
                messageLog.addAttachmentAsString("${mplAttachmentPrefix}AuditLog", logger.getLog4Print(), mediaType)
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

Logic: In the Exception Subprocess it saves the audit log as an MPL attachment and logs according to the parameter Exception_LoggingConfiguration (scope AuditLog/Payload/Property/Header).

## Libraries

PipelineLogger.groovy — structured audit log. PipelineHelper.groovy — Base64/JSON helpers.
