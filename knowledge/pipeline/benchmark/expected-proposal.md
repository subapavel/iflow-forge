# Proposal: `IF_OrderOut_to_RCV_SYS`

Steps 1–3 of migration/po-ico-to-pipeline.md on benchmark/sample-ico.json (D-027), fact base `2026-06-trial`.

## Scenario

| attribute | value | source |
|---|---|---|
| `runtime` | integrated-async | human decides (default integrated-async), question 1 |
| `qos` | EO | `icos[0].receivers[0].qualityOfService`; human confirms, question 4 |
| `sender_type` | XI | `channels[0].messageProtocol` (`icos[0].sender.channel`) |
| `receiver_type` | IDOC | `channels[1].transportProtocol` `IDoc` (`icos[0].receivers[0].channels[0]`) |
| `receiver_count` | 1 | `icos[0].receivers`, one entry, no `dynamicRouting` |
| `receiver_determination` | xpath | `icos[0].receivers[0].condition.lines` |
| `interface_determination` | static | `icos[0].receivers[0].interfaces[0]`, no `condition` |
| `mapping` | one | `icos[0].receivers[0].interfaces[0].mapping`, `.targets[0].multiplicity` 1, confirmed by `esr.messageMappings[0].multiplicity` 1:1 |
| `error_handling` | standard | human decides (default standard), question 2 |
| `custom_exit` | none | human decides (default none), question 2 |
| `inbound_conversion` | custom | human decides (default custom: sender module `MessageTransformBean`), question 3 |

Status: proposed

## Source summary

- Source: ICO benchmark/sample-ico.json (`query`: `SND_SYS`, out); ICO id: `icos[0].oid` `00000000000000000000000000000001`, `generatedFrom` `IF_OrderOut_to_RCV_SYS`.
- Sender: no party, `SND_SYS`, `OrderOut_Async`, `urn:example:orders` (mode async, `esr.serviceInterfaces[0]`, message type `Order_MT`); channel `CC_SND_XI`, adapter `SOAP/XI`; modules 1 `AF_Modules/MessageTransformBean` (`Transform.Class` `com.sap.aii.messaging.adapter.Conversion`, `Transform.ContentType` `text/xml`, `xml.documentName` `Order`), 2 `CallSapAdapter` (`ignoreDuplicate` true).
- Receiver: `RCV_SYS`, condition `/ns0:Order/Type = "STANDARD"`, interface `ORDERS.ORDERS05`, `urn:example:idoc`, multiplicity 1, mapping `OM_OrderOut_to_ORDERS05` (one request step, `XI_TRAFO` `MM_OrderOut_to_ORDERS05`); channel `CC_RCV_IDOC`, adapter `IDoc/IDoc XML`, attributes `rfcDestination`, `interfaceVersion`, no modules.
- Configuration, not attributes: `noReceiverBehaviour` error; test mode, retry profile, queue suffix: not in the ICO.

## Target artifacts

| condition | produce | read or bound at |
|---|---|---|
| always | entries under the partner ID (Partner directory entries) | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L342] |
| `inbound_conversion` custom | a Step03 copy at `{{tenant.endpoint.inbound.conversion}}` | [com.sap.integration.cloud.pipeline.v2.template.step03.inbound.conversion, adapter "ProcessDirect" "Sender"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "ProcessDirect" "ReceiverConvert"] |
| `receiver_determination` xpath | Binary `receiverDetermination`; String `receiverDetermination`, `CustomXRDEndpoint`, `ReuseXRDEndpoint` absent | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L36] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 44 "Bypass option?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L35] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 47 "Custom RD?"] |
| per receiver interface, by `mapping` | a p2p.to.idoc copy (iFlow id and package: question 5) at `{{tenant.endpoint.outbound.scenario}}`, the Service of its Interface entry | [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Sender"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 68 "Get interface"] |
| `receiver_type` IDOC | GenericIDocEndpoint = `{{tenant.endpoint.idoc.outbound}}` on that copy | [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Receiver"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_ProcessDirect_Sender" "Pipeline"] |
| `receiver_type` IDOC, connection; control record | optional (question 6); no control-record field in the ICO | [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L40] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L126] |

## Partner directory entries

| key | value placeholder | set by |
|---|---|---|
| partner ID (header partnerID) [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L342] | from SAP_SenderInterface, SAP_SenderInterfaceNamespace [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 1 "Set SAP headers"]: alternative partner (`SND_SYS`, `OrderOut_Async`, `urn:example:orders`) [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L323], else (`SND_SYS`, `SenderInterface`, `OrderOut_Async`) [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L327], else `SND_SYS~OrderOut_Async` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L338]; agency = alias `SND_SYS`, the sender component (question 4) [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L318] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L241] | always |
| `InboundConversionEndpoint_SND_SYS` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readInboundConversionFromPD.groovy#L26] | `{{tenant.endpoint.inbound.conversion}}` | `inbound_conversion` custom |
| `receiverDetermination`, Binary [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 53 "Get XSLT mapping from Partner Directory"] | XSLT: on the ICO condition `/ns0:Order/Type = "STANDARD"` (its `ns0` = `urn:example:orders`, sender message type) one `Receivers/Receiver`, `Receivers` in namespace `http://sap.com/xi/XI/System` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 59 "Any Receiver?"] [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, resource "xsd/Receivers.xsd"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 83 "Split receivers"], Service `RCV_SYS` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 84 "Get receiver name"], with `Interfaces/Interface` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 67 "Split interfaces"] Name `ORDERS.ORDERS05`, Service `{{tenant.endpoint.outbound.scenario}}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 68 "Get interface"]; no receiver (`error`): no `ReceiverNotDetermined`, route "Error" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 60 "Not found type?"], status ReceiverNotFoundError [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 82 "Set custom status"] | `receiver_determination` xpath |

## Conversions needed

| source (module / mapping) | target construct | recipe |
|---|---|---|
| `AF_Modules/MessageTransformBean`, `CC_SND_XI` position 1 | CSV to XML Converter for the plain/XML conversion, Content Modifier header for the content type, in the Step03 copy (conversion/README.md, Rules); proposal — confirm on first migration | none yet |
| `CallSapAdapter`, `CC_SND_XI` position 2 | nothing to convert (conversion/README.md); proposal — confirm on first migration | none yet |
| `OM_OrderOut_to_ORDERS05` request step, `XI_TRAFO` `MM_OrderOut_to_ORDERS05` | Message Mapping step, ESR mapping imported or rebuilt, in place of the copy's "Dummy Content Modifier" under "Call Mappings" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step 1 "Call Mappings"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step 2 "Dummy Content Modifier"]; proposal — confirm on first migration | none yet |

## Open questions

| n | question | attribute | default |
|---|---|---|---|
| 1 | Integrated-async on the tenant default queue, or decoupled or a queue of its own? | `runtime` | integrated-async, no `InboundQueue` |
| 2 | Custom error handling, pre or post exit (migration/variants.md)? | `error_handling`, `custom_exit` | standard, none |
| 3 | MessageTransformBean in a Step03 copy or a pre-processing exit? Step02 async calls inbound conversion before receiver determination [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 33 "Call inbound conversion"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 45 "Determine receivers & interfaces"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, resource "scenarioflows/integrationflow/PIP_02_Generic_IntegratedMessagingAsync.iflw"] | `inbound_conversion` | Step03 copy |
| 4 | Confirm `qos` EO, sender alias and partner ID (migration/variants.md) | `qos`, — | EO, `SND_SYS`, `SND_SYS~OrderOut_Async` |
| 5 | iFlow id and package of the copy: the human names them until the tenant profile (proposal-template.md) | — | id `IF_OrderOut_to_RCV_SYS` (`generatedFrom`) |
| 6 | `rfcDestination`, `interfaceVersion`: Step08 IDoc keys under the receiver alias (scenario-contribution.md), or its defaults? The copy sets empty connection headers [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step 13 "Set IDoc Connection"] | `receiver_type` | Step08 defaults |

## Approval

- Name: …
- Date: …
- approved for generation
