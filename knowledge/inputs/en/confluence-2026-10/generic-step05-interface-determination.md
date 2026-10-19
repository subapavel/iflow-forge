<!-- Translated from Confluence page "Generic Step05 – Interface Determination" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Generic Step05 – Interface Determination

Version: 1.0.3 · Technical name: PIP_05_Generic_InterfaceDetermination · Package: Cloud Integration Pipeline – Generic Pipelines

> [FULLY DECOUPLED PIPELINE ONLY] — this entire flow is not used in the integrated runtime. In integrated, interfaces are returned directly from the combined determination in Integrated Messaging Runtime Async. Kept for completeness.

## Purpose

Fourth step of the fully decoupled pipeline: for each receiver (the message is already per receiver, from PIPQ03) it determines the list of receiver interfaces — by an XSLT mapping from the Partner Directory or by a custom flow — splits the message per interface and stores it in the outbound queue (PIPQ04 / receiver-specific). This is where the key header SAP_OutboundProcessingEndpoint is born, which Step06 then uses to route to the scenario-specific outbound flow.

## Queues and adapters

| Adapter | Queue / endpoint | Role |
|---|---|---|
| JMS Sender | {{PipelineJMSQueuePrefix}}Q03 = PIPQ03 | input (from Step02/Step04) |
| JMS Receiver | PIPQ04 | default output → Step06 |
| JMS Receiver | ${property.receiverSpecificQueueName} | receiver-specific output |
| JMS Receiver | PIPQ03_DLQ | dead letter queue |
| ProcessDirect | ${property.customXIDEndpoint} | custom interface determination flow |
| ProcessDirect | custom exits, custom error flow | shared patterns |

## Main flow

1. Add custom header properties → Set custom extensions (among others property partnerIDOld = ${SAP_Sender}~${SAP_SenderInterface…} for backward compatibility) → Check well formed XML → Store payload → router "Max # of retries reached?" → possibly MaxRetriesExceeded → DLQ
2. Initialize pre-processing exit → Call custom exit
3. Read custom interface determination – readCustomInterfaceDeterminationFromPD.groovy: PD CustomXIDEndpoint → customXIDBoolean/customXIDEndpoint → router "Custom ID?":
   - No (default) → local process Determine interfaces based on XSLT from Partner Directory: binary parameter interfaceDetermination_<SAP_ReceiverAlias> of partner partnerID (the XSLT returns Interfaces/Interface with Name, Index, Service)
   - Yes → local process Determine interfaces via custom flow: Request Reply to ${property.customXIDEndpoint}
4. Router "Router": count(//Interface) = 0 → status InterfaceNotFoundError → Error End; otherwise → Get Receiver specific queue from PD → Iterating splitter on /Interface
5. Per interface: Get interface — headers SAP_ReceiverInterface (= /Interface/Name), SAP_ReceiverInterfaceIndex, SAP_OutboundProcessingEndpoint (= /Interface/Service) → Log outbound processing endpoint → Get payload → router "Receiver specific?" → JMS ${receiverSpecificQueueName} or PIPQ04

## Error handling

Shared pattern: Standard Retry handling (test mode → ErrorInTestMode; max retries → DLQ PIPQ03_DLQ; DLQ_EndEvent default Escalated) / Custom error handling with the original payload (fix 1.0.2).

## Scripts (new compared to those documented earlier)

| Script | Purpose |
|---|---|
| readCustomInterfaceDeterminationFromPD.groovy | PD CustomXIDEndpoint → properties customXIDBoolean, customXIDEndpoint |

Others (readReceiverSpecificQueueFromPD, checkWellFormedXML, store/fetchPayloadInXProperty, logOutboundProcessingEndpoint, addCustomHeaderProperties, attachAdditionallnformation, readCustomXFromPD, libraries) — identical to the patterns in the other documents.

## Script details

### readCustomInterfaceDeterminationFromPD.groovy

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

    // check for custom interface determination by reading the custom interface determination end point from
the Partner Directory
    def customXIDEndpoint = service.getParameter("CustomXIDEndpoint", Pid , String.class);

    // create a new exchange property with value true or false depending on whether the end point exists
    message.setProperty("customXIDBoolean", customXIDEndpoint ? 'true' : 'false');

    // if the end point exists, create a new exchange property containing the end point
    // otherwise, the exchange property is not created
    message.setProperty("customXIDEndpoint", customXIDEndpoint ?: null);

        // adding information to audit log using helper PipelineLogger class
        PipelineLogger logger = PipelineLogger.newLogger(message)
        def logEntry = new StringBuilder().append("Partner ID to read the custom interface determination: ").
append(Pid ?: "n/a").toString();
        logger.addEntry("customInterfaceDetermination", logEntry);
        logEntry = new StringBuilder().append("Custom interface determination end point from the Partner
Directory: property customXIDEndpoint=").append(message.properties.customXIDEndpoint.toString() ?: "n/a").
toString();
        logger.addEntry("customInterfaceDetermination", logEntry);
        logEntry = new StringBuilder().append("Custom interface determination existence from the Partner
Directory: property customXIDBoolean=").append(message.properties.customXIDBoolean.toString() ?: "n/a").
toString();
        logger.addEntry("customInterfaceDetermination", logEntry);
        if (logger.getAuditLog()) {
                message.setHeader('auditLogHeader', logger.getAuditLog())
        }

    return message;
}
```

Logic: By partnerID reads PD CustomXIDEndpoint → property customXIDBoolean (true/ false) and customXIDEndpoint. If it exists, interface determination is not resolved by the XSLT from the PD, but by the custom flow at this endpoint. (Without partnerID or the PD service → exception.) Input: partnerID; PD CustomXIDEndpoint → Output: properties customXIDBoolean, customXIDEndpoint

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

Logic: Parses the body as XML (XmlSlurper) → header isWellFormedXml (true/ false); controls how the payload is backed up. Input: body → Output: header isWellFormedXml

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

Logic: Backs up the body to the property payload; non-XML is Base64-encoded. Input: body, header isWellFormedXml → Output: property payload

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

Logic: Restores the payload from the property payload back into the body; non-XML is Base64-decoded (custom error handling, fix 1.0.2). Input: property payload, header isWellFormedXml → Output: body

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

Logic: Writes to the audit log the selected bypassOption and the resulting value of the header SAP_OutboundProcessingEndpoint (which is born in this step from /Interface/Service).

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

Logic: Step number from camelId; reads CustomXPreEnabled/ CustomXPreEndpoint from the headers or from the PD by partnerID. Input: partnerID, property CustomXType → Output: properties CustomXBoolean, CustomXEndpoint

## Libraries

- PipelineLogger.groovy — structured audit log.
- PipelineHelper.groovy — Base64/JSON helpers.
