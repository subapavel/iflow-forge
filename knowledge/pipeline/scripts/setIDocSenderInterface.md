# setIDocSenderInterface

## Purpose

Builds the sender interface of an incoming IDoc as `<MESTYP>.<IDOCTYP>[.<CIMTYP>]` with the fixed IDoc namespace, and sets the quality of service from the IDoc adapter's SapPlainSoapQoS, default ExactlyOnce [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L8] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L10] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L12].
Script step "Set sender interface" in the main process of Step01 IDoc, right after the Content Modifier "Read partner headers" that fills the SAP_IDoc_EDIDC_* headers from the control record [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 1 "Read partner headers"]; "Determine partner ID" follows [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 3 "Determine partner ID"].

## Inputs

- Headers SAP_IDoc_EDIDC_MESTYP, SAP_IDoc_EDIDC_IDOCTYP, SAP_IDoc_EDIDC_CIMTYP [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L8].
- Header SapPlainSoapQoS [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L12].
- Header SapQualityOfService, read for the property [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L13].

## Outputs

- Header SAP_SenderInterface = MESTYP + "." + IDOCTYP, plus "." + CIMTYP when CIMTYP is not empty [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L8].
- Header SAP_SenderInterfaceNamespace = "urn:sap-com:document:sap:idoc:messages" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L10].
- Header SapQualityOfService = SapPlainSoapQoS, or "ExactlyOnce" when it is missing or empty [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L12]; the router "QoS?" sends 'ExactlyOnceInOrder' to the EOIO branch and everything else to "Read inbound queue" [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 23 "QoS?"].
- Property customHeader_QualityOfService = `map.get("SapQualityOfService")` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L13].
- No audit log entry is written: the script imports only Message [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L1].

## Gotchas

- `map` is the header map read at the start [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L5]; L13 reads SapQualityOfService from it after L12 has set the header, so whether the property holds the new or the incoming value depends on whether the map is live, which the script does not show [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L12] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L13].
- L8 concatenates the MESTYP and IDOCTYP header values with "." without a null check [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L8].
- The header SapQualityOfService set upstream is overwritten in every case [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/setIDocSenderInterface.groovy#L12].

## Used by

- `com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc`: [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, step 2 "Set sender interface"].

Copies: one copy (`com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc`).
