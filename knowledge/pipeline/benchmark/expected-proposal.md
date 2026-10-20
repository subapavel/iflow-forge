# Proposal: `IF_OrderOut_to_RCV_SYS`

Steps 1–3 of migration/po-ico-to-pipeline.md on benchmark/sample-ico.json (D-027), fact base `2026-06-trial`.

## Scenario

| attribute | value | source |
|---|---|---|
| `runtime` | integrated-async | human decides (default integrated-async), question 1 |
| `qos` | EO | `icos[0].receivers[0].qualityOfService` |
| `sender_type` | XI | `channels[0].messageProtocol` (`icos[0].sender.channel`) |
| `receiver_type` | IDOC | `channels[1].messageProtocol` (`icos[0].receivers[0].channels[0]`) |
| `receiver_count` | 1 | `icos[0].receivers`, one entry, no `dynamicRouting` |
| `receiver_determination` | xpath | `icos[0].receivers[0].condition.lines` |
| `interface_determination` | static | `icos[0].receivers[0].interfaces[0]`, no `condition` |
| `mapping` | one | `icos[0].receivers[0].interfaces[0].mapping`, `.targets[0].multiplicity` 1 |
| `error_handling` | standard | human decides (default standard), question 2 |
| `custom_exit` | none | human decides (default none), question 2 |
| `inbound_conversion` | none | human decides (default none), question 3 |

Status: proposed

## Source summary

- Source: ICO benchmark/sample-ico.json (`query`: `SND_SYS`, out); ICO id: `icos[0].oid` `00000000000000000000000000000001`, `generatedFrom` `IF_OrderOut_to_RCV_SYS`.
- Sender: no party, `SND_SYS`, `OrderOut_Async`, `urn:example:orders` (mode async, `esr.serviceInterfaces[0]`); channel `CC_SND_XI`, adapter `SOAP/XI`; modules 1 `AF_Modules/MessageTransformBean` (`Transform.Class` `com.sap.aii.messaging.adapter.Conversion`, `Transform.ContentType` `text/xml`, `xml.documentName` `Order`), 2 `CallSapAdapter` (`ignoreDuplicate` true).
- Receiver: `RCV_SYS`, condition `/ns0:Order/Type = "STANDARD"`, interface `ORDERS.ORDERS05`, `urn:example:idoc`, multiplicity 1, mapping `OM_OrderOut_to_ORDERS05` (one request step, `XI_TRAFO` `MM_OrderOut_to_ORDERS05`); channel `CC_RCV_IDOC`, adapter `IDoc/IDoc XML`, attributes `rfcDestination`, `interfaceVersion`, no modules.
- Configuration, not attributes: `noReceiverBehaviour` error; test mode, retry profile, queue suffix: not in the ICO.

## Target artifacts

| condition | produce | read or bound at |
|---|---|---|
| always | entries under the partner ID (Partner directory entries) | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L342] |
| `sender_type` XI; `runtime` ≠ DefaultJMSQueueNumber | `InboundQueue` only if the tenant default is not X01 (question 1); without it "Which queue?" takes "Default queue" | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/readInboundQueueFromPD.groovy#L21] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 30 "Which queue?"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "DefaultInboundProcessingQueue"] |
| per receiver interface, by `mapping` | a p2p.to.idoc copy, iFlow id `IF_OrderOut_to_RCV_SYS` (package: question 5), at `{{tenant.endpoint.outbound.scenario}}`, the Service of its Interface entry | [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Sender"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 68 "Get interface"] |
| `receiver_type` IDOC | GenericIDocEndpoint = `{{tenant.endpoint.idoc.outbound}}` on that copy | [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, adapter "ProcessDirect" "Receiver"] [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, adapter "CC_ProcessDirect_Sender" "Pipeline"] |

## Partner directory entries

| key | value placeholder | set by |
|---|---|---|
| partner ID (header partnerID) [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L342] | from SAP_SenderInterface, SAP_SenderInterfaceNamespace [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 1 "Set SAP headers"]: alternative partner (agency, `OrderOut_Async`, `urn:example:orders`) [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L323], else (agency, `SenderInterface`, `OrderOut_Async`) [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L327], else tilde PID `<agency>~OrderOut_Async` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L338]; agency `<party>.<sender alias>` or `<sender alias>` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L318], the ICO has no party (question 4) | always |
| `InboundQueue` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/readInboundQueueFromPD.groovy#L21] | `{{tenant.pipeline.prefix}}X01` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "InboundProcessingQueueX"] | tenant default not X01 (question 1) |
| `receiverDetermination`, Binary [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 53 "Get XSLT mapping from Partner Directory"] | XSLT: on `/ns0:Order/Type = "STANDARD"` one `/ns0:Receivers/Receiver` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 83 "Split receivers"], Service `RCV_SYS` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 84 "Get receiver name"], with `Interfaces/Interface` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 67 "Split interfaces"] Name `ORDERS.ORDERS05`, Service `{{tenant.endpoint.outbound.scenario}}` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 68 "Get interface"]; no receiver: a ReceiverNotDetermined Type other than Default or Ignore takes "Error" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 60 "Not found type?"], status ReceiverNotFoundError [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 82 "Set custom status"] | `receiver_determination` xpath |

String `receiverDetermination`, `CustomXRDEndpoint` and `ReuseXRDEndpoint` stay absent for xpath: the first sets bypassOption [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readBypassOptionFromPD.groovy#L36], off the XSLT route of "Bypass option?" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 44 "Bypass option?"]; the others [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomReceiverDeterminationFromPD.groovy#L35] route "Custom RD?" to Yes or "Not supported route" [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 47 "Custom RD?"].

## Conversions needed

| source (module / mapping) | target construct | recipe |
|---|---|---|
| `AF_Modules/MessageTransformBean`, `CC_SND_XI` position 1 | CSV to XML Converter for the plain/XML conversion, Content Modifier header for the content type, in a Step03 copy or a pre-processing exit (conversion/README.md, Rules; question 3); proposal — confirm on first migration | none yet |
| `OM_OrderOut_to_ORDERS05` request step, `XI_TRAFO` `MM_OrderOut_to_ORDERS05` | Message Mapping step, ESR mapping imported or rebuilt, in place of the copy's "Dummy Content Modifier" under "Call Mappings" [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step 1 "Call Mappings"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step 2 "Dummy Content Modifier"]; proposal — confirm on first migration | none yet |

## Open questions

| n | question | attribute | default |
|---|---|---|---|
| 1 | Integrated-async or decoupled; is the tenant's DefaultJMSQueueNumber X01? No rule without the tenant profile (po-ico-to-pipeline.md:9, :31) | `runtime` | integrated-async, X01 as SAP recommends [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, resource "parameters.propdef"] |
| 2 | Custom error handling only on CustomXError_Enabled 'true' [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 56 "Custom Extension?"]; pre exit needs both entries [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, scripts/readCustomXFromPD.groovy#L52], post the copy's CustomXPost_Enabled 'true' [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step 8 "Exit?"] | `error_handling`, `custom_exit` | standard, none |
| 3 | Must MessageTransformBean run before the condition? Step02 async calls inbound conversion before receiver determination [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 33 "Call inbound conversion"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 45 "Determine receivers & interfaces"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, resource "scenarioflows/integrationflow/PIP_02_Generic_IntegratedMessagingAsync.iflw"] and maps `<dummy></dummy>` when isWellFormedXml is 'false' [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 51 "XML?"] [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 52 "Define dummy xml"]. po-ico-to-pipeline.md:43 hints custom for non-standard modules only, conversion/README.md:24 for every sender-side module | `inbound_conversion` | none |
| 4 | Which partner ID and sender alias? No procedure row derives them (po-ico-to-pipeline.md:13-19; proposal-template.md:46) | — | tilde PID, alias SAP_Sender [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/determinePartnerID.groovy#L241]: `SND_SYS~OrderOut_Async` |
| 5 | Package of the copy: no rule (proposal-template.md:38; scenario-contribution.md:80) | — | — |
| 6 | Namespace of prefix `ns0`: `condition.lines` carry none (po-ico-to-pipeline.md:15); candidate `esr.serviceInterfaces[0].xmlNamespace` | `receiver_determination` | — |
| 7 | IDoc connection and control record (`rfcDestination`, `interfaceVersion`): no contributes row (scenario-contribution.md:50-69); Step08 IDoc reads `<key>_<SAP_Receiver>` under the receiver alias [com.sap.integration.cloud.pipeline.v2.generic.step08.outbound.processing.idoc, scripts/setAllDefaultAndScenarioValuesFromPDForIDoc.groovy#L40], the copy sets empty connection headers [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.p2p.to.idoc, step 13 "Set IDoc Connection"] | `receiver_type` | Step08 defaults |
| 8 | `CallSapAdapter`: no conversion/README.md row, though po-ico-to-pipeline.md:23 asks one per module; left out | — | — |
| 9 | Gaps: `adapter` sub-fields are siblings (po-ico-to-pipeline.md:17); "e.g. `SOAP/XI`" is the `adapter` value, IDoc protocols unlisted (:32); "human confirms" scope (:30); `noReceiverBehaviour` target (:16); no xpath contributes row (scenario-contribution.md:50-69); Service cited from Step05 only (:60); mapping cites p2p numbers (po-ico-to-pipeline.md:39); `esr.messageMappings[].multiplicity` unused (:39-40) | — | — |

## Approval

- Name: …
- Date: …
- approved for generation
