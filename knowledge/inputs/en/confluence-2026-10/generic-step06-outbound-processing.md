<!-- Translated from Confluence page "Generic Step06 – Outbound Processing" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Step06 – Outbound Processing

Version: 1.0.3 · Technical name: PIP_02_Generic_InboundProcessing · Package: Cloud Integration Pipeline – Generic Outbound

## Purpose

A generic dispatcher on the output side of the pipeline. It reads messages from the outbound JMS queue (PIPQ04), performs preparatory steps (XML validation, payload backup for retry, custom exits) and passes the message via ProcessDirect to the scenario-specific outbound flow (Step07), whose endpoint arrives in the header SAP_OutboundProcessingEndpoint. It provides retry and dead-letter handling for the whole outbound processing — when the scenario-specific flow fails, the error is propagated here and this is where the decision is made about an automatic retry or a move to the DLQ.

> Common to both runtimes. PIPQ04 is filled both by the integrated runtime and (in the fully decoupled pipeline) by Step05 — Step06 is therefore the same in both cases.

## Input (JMS Sender adapter)

| Setting | Value |
|---|---|
| Queue | {{PipelineJMSQueuePrefix}}Q04{{PipelineJMSQueueSuffix}} = PIPQ04 (prefix/suffix allow receiver-specific queues) |
| Retry interval / max | {{JMS_RetryInterval}} = 1 min / {{JMS_MaxRetryInterval}} = 60 min, exponential backoff enabled |
| Concurrent processes | {{JMS_ConcurrentProcesses}} = 2 |
| Dead Letter Queue | {{JMS_DLQ}} = enabled |

## Main flow (Integration Process)

1. Add custom header properties – script addCustomHeaderProperties.groovy (MPL custom headers)
2. Check well formed XML – script checkWellFormedXML.groovy → header isWellFormedXml
3. Store payload – script storePayloadInXProperty.groovy: backup of the body into the property payload; a non-XML payload is Base64-encoded (fix from 1.0.2 against corruption of binary data)
4. Set custom extensions (Content Modifier) – parameters → properties: CustomXPreGlobal_Enabled/_Endpoint, PipelineJMSQueuePrefix, DLQ_EndEvent, CustomXError_Enabled
5. Initialize pre-processing exit – CustomXType = CustomXPre
6. Call custom exit – local process Call custom exits (the same logic as in the inbound flows: global exit → scenario-specific exit from the PD via readCustomXFromPD.groovy)
7. Set unique identifier – UniqueID = SAP_MessageProcessingLogID
8. Remove header customXPipelineStep → End → ProcessDirect ${header.SAP_OutboundProcessingEndpoint} (scenario-specific outbound flow)

## Retry and DLQ (Standard Retry handling)

On an error (its own as well as one propagated from the scenario-specific flow) the process Standard Retry handling runs:

1. Router "Test mode?": ${header.testMode} = 'true' → status ErrorInTestMode → Attach additional information → Escalation End (a test message is not recycled as an error)
2. Router "Max # of retries reached?": ${header.SAPJMSRetries} = ${header.maxJMSRetries} or maxJMSRetries = '0'
   - No → status AutomaticRetry → Attach additional information → Escalation End → the message returns to the queue, JMS delivers it again (interval 1 min, exponentially up to 60 min)
   - Yes → status MaxRetriesExceeded, property incomingQueue = <prefix>Q04 → Fetch original payload (script fetchPayloadFromXProperty.groovy — restores the backed-up payload, Base64-decodes non-XML) → Attach additional information (script attachAdditionallnformation.groovy — audit log as the MPL attachment "Additional Information") → Send to DLQ: JMS {{PipelineJMSQueuePrefix}}Q04{{PipelineJMSQueueSuffix}}_DLQ (= PIPQ04_DLQ) → router "End event?" by the parameter DLQ_EndEvent: Completed → End / Escalated (default) → Escalation End

The DLQ has its own retention/expiration: DLQ_RetentionThreshold = 2 days, DLQ_ExpirationPeriod = 30 days, encryption and compression enabled.

## Custom error handling

Router "Custom Extension?" (CustomXError_Enabled, default false) in the Exception Subprocesses of the main process and of the custom exits. Custom branch: Set headers (exceptionMessage/stacktrace/timestamp, pipelineStepID, incomingQueue) → Fetch original payload → ProcessDirect {{CustomXError_Endpoint}} → status from customXError_Status → router customXError_EndEvent (Completed / Escalated / Failed).

## Scripts – where and why they are called

| Script | Called in step | Purpose |
|---|---|---|
| addCustomHeaderProperties.groovy | Add custom header properties | MPL custom headers (identical to inbound) |
| checkWellFormedXML.groovy | Check well formed XML | XmlSlurper.parse → header isWellFormedXml true/false |
| storePayloadInXProperty.groovy | Store payload | Backup of the body into the property payload (non-XML → Base64) for later restoration |
| readCustomXFromPD.groovy | Check scenario-specific exit | Custom exit configuration from the PD (identical to inbound) |
| fetchPayloadFromXProperty.groovy | Fetch original payload (2×: DLQ branch, custom error) | Restoration of the backed-up payload (Base64 decoding of non-XML) |
| attachAdditionallnformation.groovy | Attach additional information (3×: test mode, retry, DLQ) | Audit log as an MPL attachment; for the DLQ it writes "Message removed from queue X and sent to dead letter queue X_DLQ" |
| throwException.groovy | – (available) | Standard exception |
| PipelineLogger.groovy / PipelineHelper.groovy | – (libraries) | Audit log / Base64+JSON helpers |
| auditLogHandler.groovy, logMessage.groovy | – | Utilities (in this flow not linked to a step) |

## Externalized parameters

| Parameter | Default | Meaning |
|---|---|---|
| PipelineJMSQueuePrefix / PipelineJMSQueueSuffix | PIP / – | Queue name <prefix>Q04<suffix>; the suffix is for receiver-specific outbound queues (since 1.0.1) |
| JMS_RetryInterval / JMS_MaxRetryInterval / JMS_ExponentialBackoff | 1 / 60 / 1 | Retry policy (minutes) |
| JMS_ConcurrentProcesses | 2 | Parallelism of reading from the queue |
| JMS_DLQ | 1 | Enabling of dead letter handling |
| DLQ_EndEvent | Escalated | MPL status after the move to the DLQ (Escalated/Completed) |
| DLQ_RetentionThreshold / DLQ_ExpirationPeriod | 2 / 30 | DLQ retention/expiration (days) |
| DLQ_EncryptStoredMessage / DLQ_CompressStoredMessage | 1 / 1 | DLQ encryption/compression |
| CustomXPreGlobal_Enabled / _Endpoint | false / /pip/custom/preProcessing/global | Global pre-processing exit |
| CustomXError_Enabled / _Endpoint | false / /pip/custom/errorHandling | Custom error handling |

## Script details

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

Logic: Builds the MPL custom header properties from two sources: the header customHeaderProperties (a string klí:hodnota|klí:hodnota or Base64-encoded JSON) and exchange properties with the prefix customHeader_* (including unpacking of NodeLists from XPath). It writes the result into the MPL custom headers (business keys searchable in monitoring). Input: header customHeaderProperties, properties customHeader_* → Output: MPL custom header properties

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
        logger.addEntry("checkWellFormedXML", "Header isWellFormedXml=" + (message.headers.isWellFormedXml.toString() ?: "n/a") + ".")
        if (logger.getLog()) {
            logger.saveToMessage(message)
        }

        //PipelineLogger logger = PipelineLogger.newLogger(message)
        //def logEntry = new StringBuilder().append("Header isWellFormedXml=").append(message.headers.isWellFormedXml.toString() ?: "n/a").toString();
        //logger.addEntry("checkWellFormedXML", logEntry);
        //if (logger.getAuditLog()) {
        //        message.setHeader('auditLogHeader', logger.getAuditLog())
        //}

    return message
}
```

Logic: Tries to parse the body as XML (XmlSlurper) and, depending on the result, sets the header isWellFormedXml to true/false. It serves to decide whether the payload is backed up as text or as Base64. Input: body → Output: header isWellFormedXml

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

Logic: Backs up the body into the property payload. With isWellFormedXml = true it stores the body as it is; otherwise it Base64-encodes it (body.bytes.encodeBase64()) — protection of binary/non-XML data for later restoration. Input: body, header isWellFormedXml → Output: property payload

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

Logic: Identical to the pattern from inbound — determines the step number from camelId, reads CustomXPreEnabled/CustomXPreEndpoint from the headers or from the PD by partnerID; Enabled is a |-separated list of steps. Input: headers partnerID, customXPipelineStep; property CustomXType → Output: properties CustomXBoolean, CustomXEndpoint

### fetchPayloadFromXProperty.groovy

```groovy
import com.sap.gateway.ip.core.customdev.util.Message;

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

Logic: The counterpart of storePayloadInXProperty — restores the backed-up payload from the property payload back into the body; if isWellFormedXml != true, it Base64-decodes the value. Input: property payload, header isWellFormedXml → Output: body

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
            logger.addEntry("attachAdditionallnformation", "Message removed from queue " + incomingQueue + " and sent to dead letter queue " + deadLetterQueue + ".")
            //def logEntry = new StringBuilder().append("Message removed from queue ").append(incomingQueue).append(" and sent to dead letter queue ").append(deadLetterQueue).toString();
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

Logic: Attaches the audit log as the MPL attachment "Additional Information". If the message is not in test mode, the custom status does not contain OptionNotSupported and incomingQueue is known, it adds the records "Maximum number of retries exceeded" and "Message removed from queue X and sent to dead letter queue X_DLQ" to the log. Input: properties incomingQueue, SAP_MessageProcessingLogCustomStatus, header testMode → Output: MPL attachment "Additional Information"

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

Logic: Throws an exception of the type given by the property exceptionType (default Exception) with the message from the property exceptionMessage. Input: properties exceptionMessage, exceptionType → Output: exception

## Libraries and utilities

- PipelineLogger.groovy — structured audit log (addEntry/saveToMessage).
- PipelineHelper.groovy — Base64/JSON helpers.
- auditLogHandler.groovy, logMessage.groovy — utilities, in this flow not linked to a step.
