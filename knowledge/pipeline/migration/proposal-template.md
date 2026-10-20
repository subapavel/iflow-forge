# Proposal: `<scenario>`

The document a human approves before anything is generated (po-ico-to-pipeline.md, is-iflow-to-pipeline.md step 4). Copy it per scenario; replace every `…`; delete rows that do not apply. Example names are synthetic (`SND_SYS`, `RCV_SYS`, `urn:example:orders`, `Z_ORDER_IN`).

## Scenario

| attribute | value | source |
|---|---|---|
| `runtime` | … | … |
| `qos` | … | … |
| `sender_type` | … | … |
| `receiver_type` | … | … |
| `receiver_count` | … | … |
| `receiver_determination` | … | … |
| `interface_determination` | … | … |
| `mapping` | … | … |
| `error_handling` | … | … |
| `custom_exit` | … | … |
| `inbound_conversion` | … | … |

Value: one of the D-025 values. Source: the ICO field or dump line the value was derived from, or "human decides (default …)" with the Open questions number.

Status: proposed | approved

## Source summary

- Source: ICO `icos-<component>.json` | iFlow dump of `<path>`; ICO or iFlow id: …
- Sender: party, component, interface name and namespace (e.g. `SND_SYS`, `Z_ORDER_IN`, `urn:example:orders`), channel and adapter: …
- Receivers, one line each: party, component (e.g. `RCV_SYS`), condition, interfaces, mapping, channel and adapter: …
- Configuration, not attributes (no-receiver behaviour, test mode, retry profile, queue suffix): …

## Target artifacts

| condition | produce | read or bound at |
|---|---|---|
| … | … | … |

Fill it from scenario-contribution.md "What a scenario contributes": copy every row whose condition the Scenario values meet ("always" included), one row per copy or entry, each copy with its own ProcessDirect address, unique per copy. The copy's iFlow id and package follow the tenant profile (part 2); until then the human names them.

## Partner directory entries

The keys below hang under the scenario's partner ID (partner-directory.md "Key scheme"); keep the rows the Scenario values need.

| key | value placeholder | set by |
|---|---|---|
| partner ID (header partnerID) [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/determinePartnerID.groovy#L342] | per partner-directory.md "Key scheme", alias per migration/variants.md: … | always |
| `InboundQueue` [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, scripts/readInboundQueueFromPD.groovy#L21] | queue name [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "JMS" "InboundProcessingQueue"], shape `<prefix>X01` [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, adapter "JMS" "InboundProcessingQueueX"] or `<prefix>Q01` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, adapter "JMS" "InboundProcessingQueue"]: … | XI, IDOC sender the human assigns its own queue (migration/variants.md) [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.idoc, scripts/readInboundQueueFromPD.groovy#L21] |
| `InboundConversionEndpoint_<SAP_SenderAlias>` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L26] | `{{tenant.endpoint.inbound.conversion}}` | `inbound_conversion` custom |
| `InboundConversionEndpoint` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readInboundConversionFromPD.groovy#L31] | `{{tenant.endpoint.inbound.conversion}}` | `inbound_conversion` custom, no alias key |
| `CustomXPreEnabled` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, step 5 "Initialize pre-processing exit"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readCustomXFromPD.groovy#L41] | step numbers, pipe-separated [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readCustomXFromPD.groovy#L53] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readCustomXFromPD.groovy#L54]: … | `custom_exit` pre, pre+post |
| `CustomXPreEndpoint` [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readCustomXFromPD.groovy#L48] | `{{tenant.endpoint.custom.preprocessing.scenario}}` | `custom_exit` pre, pre+post |
| `receiverDetermination`, String [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L22] | receiver alias, e.g. `RCV_SYS`: … | `receiver_determination` static |
| `interfaceDetermination_<receiver alias>`, String [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L29] | `{{tenant.endpoint.outbound.scenario}}` | `receiver_determination` static, one interface |
| `receiverDetermination`, Binary [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 53 "Get XSLT mapping from Partner Directory"] | XSLT: … | `receiver_determination` xpath |
| `interfaceDetermination_<SAP_ReceiverAlias>`, Binary [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, step 36 "Get XSLT mapping from Partner Directory"] | XSLT: … | `interface_determination` static, xpath; decoupled |
| `CustomXRDEndpoint` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L25] | `{{tenant.endpoint.receiver.determination.custom}}` | `receiver_determination` custom |
| `ReuseXRDEndpoint` [com.sap.integration.cloud.pipeline.v2.generic.step04.receiver.determination, scripts/readCustomReceiverDeterminationFromPD.groovy#L35] | `{{tenant.endpoint.receiver.determination.reuse}}` | `receiver_determination` reuse-xrd |
| `CustomXIDEndpoint` [com.sap.integration.cloud.pipeline.v2.generic.step05.interface.determination, scripts/readCustomInterfaceDeterminationFromPD.groovy#L21] | `{{tenant.endpoint.interface.determination.custom}}` | `interface_determination` custom; decoupled |
| … | … | configuration rows of scenario-contribution.md |

## Conversions needed

| source (module / mapping) | target construct | recipe |
|---|---|---|
| … | … | conversion/… or "none yet": … |

One row per channel module and mapping program of the Source summary (conversion/README.md); a row without a recipe becomes a recipe at the procedure's last step.

## Open questions

| n | question | attribute | default |
|---|---|---|---|
| 1 | … | … | … |

Every "human decides" or "human confirms" of the procedure is a row here, the sender alias and partner ID always (migration/variants.md "Partner ID and sender alias", default the sender component name); approval needs each answered.

## Approval

- Name: …
- Date: …
- approved for generation
