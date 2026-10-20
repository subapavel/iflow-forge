# Migration: point-to-point iFlow to pipeline

Procedure from one existing point-to-point Integration Suite iFlow to an approved pipeline scenario on the `2026-06-trial` fact base. Same shape as po-ico-to-pipeline.md; attribute names and values are D-025. The procedure reports what the iFlow contains and what the pipeline needs; it does not assess the iFlow.

## Inputs

- The dump `po-xray iflow <path>` (artifact folder or zip, package export, or a folder of these), Markdown; `--json` prints the records, one object per artifact. Line shapes of the Markdown (IFlowDump):
  - `## <id> (<name>, <version>)`;
  - `- participant: <name> <componentType> <direction> -> <endpoint>`, one per message flow;
  - steps `n. <activityType> "<name>"`, numbered continuously, indented two spaces per depth, ` [unreached]` when no flow leads to the step;
  - `- parameter: <name> = <default> [configured]`; `- resource: <path>`.
- Records: `IFlow` (`id`, `name`, `version`, `participants`, `steps`, `parameters`, `resources`), `Participant` (`name`, `componentType`, `direction`, `endpoint`, `properties`), `Step` (`id`, `name`, `activityType`, `depth`, `properties`, `reached`), `Param` (`name`, `defaultValue`, `configured`; a configured value itself is never read).
- The Markdown omits participant and step properties; `--json` shows them. Neither shows sequence flows: gateway conditions and which step feeds which participant are grepped from the `.iflw` (`sequenceFlow`, `conditionExpression`, `messageFlow` `sourceRef`), never loaded whole.
- This knowledge base and the tenant profile as in po-ico-to-pipeline.md (the profile is not yet available).

## Steps

1. Dump the iFlow: `<path>` → `po-xray iflow <path>` and `--json` output under target/, plus each gateway's outgoing flows and conditions from the `.iflw` → the Source summary of the proposal.
2. Derive the attributes: Source summary → one value per attribute; apply the table below; unresolved values go to Open questions.
3. Fill migration/proposal-template.md: attribute values, Source summary → every section of the proposal.
4. Obtain human approval: the proposal → Status approved, with name and date. Nothing is generated without it; an open question still open blocks approval.
5. Produce the artifacts: the approved proposal → what scenario-contribution.md "What a scenario contributes" lists for the approved values, checked against its "What a scenario must not touch".
6. Convert scripts and mappings per conversion/README.md: `Script` and `Mapping` steps with their `- resource:` files (Groovy, XSLT, message mappings), converter steps after the sender → one Conversions needed row each, with its recipe.
7. Append to the knowledge base (spec §8): what this migration taught → recipe, pitfall, page note, committed (section "Append to the knowledge base").

## Dump lines to scenario attributes

`componentType` is the adapter as the reader documents it (`JMS`, `ProcessDirect`, `XI`, `IDOC`, `SOAP`, …); `direction` is `Sender` or `Receiver`. The pipeline consequence of each value is the same as in po-ico-to-pipeline.md "ICO fields to scenario attributes".

| attribute | dump line(s) | rule | cite |
|---|---|---|---|
| `qos` | sender participant, its `properties` (`--json`) | not in the Markdown dump: default EO for an asynchronous sender adapter, BE for a synchronous one; human confirms from the `--json` adapter properties. An XI sender carries `QualityOfService_inbound`; Step01 XI's own is HandledByIflow and it routes on header SapQualityOfService | [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, adapter "XI" "Sender"] [com.sap.integration.cloud.pipeline.v2.generic.step01.inbound.processing.xi, step 23 "QoS?"] |
| `runtime` | `qos`; a receiver called by Request Reply with the reply returned to the sender | synchronous → integrated-sync; asynchronous → human decides; default integrated-async | — |
| `sender_type` | participant with `direction` Sender | `componentType` XI → XI, IDOC → IDOC, anything else → OTHER | — |
| `receiver_type` | participants with `direction` Receiver that the scenario's sends reach (`messageFlow` `sourceRef` = a step `id`) | same rule per receiver; participants of lookups or enrichment calls are not receivers, human confirms | — |
| `receiver_count` | those receiver participants | one → 1, more → n; a `Multicast` to several receiver participants also → n: async splits per receiver, and the Step07 copy is chosen per receiver alias | [com.sap.integration.cloud.pipeline.v2.generic.step02.integrated.messaging.async, step 83 "Split receivers"] [com.sap.integration.cloud.pipeline.v2.generic.step02.inbound.processing, scripts/readBypassOptionFromPD.groovy#L29] |
| `receiver_determination` | `ExclusiveGateway` steps whose routes lead to different receivers | no such gateway → static; conditions on the payload → xpath; a `Script` step or call that computes the receiver → custom candidate: default xpath (router with conditions) or static (no router), custom only on the human's request; reuse-xrd: never derived, same rule as po-ico-to-pipeline.md | — |
| `interface_determination` | `ExclusiveGateway` steps whose routes lead to different mappings for one receiver | none → static; conditions → xpath; computed → custom candidate: default static, custom only on the human's request | — |
| `mapping` | `Mapping` steps per route (`--json`: `mappingType` MessageMapping or `subActivityType` XSLTMapping), `Splitter`, `Multicast`, `SequentialMulticast` after them | none → none; one per receiver → one (sync: request and response mapping); one mapping then a `Splitter` → one-to-many-one-type; one mapping then a `Multicast` over several target interfaces of one receiver → one-to-many-many-types; `SequentialMulticast` → ordered. A `Script` step that maps: default one, with a conversion recipe (conversion/README.md); human classifies | [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step 4 "One-to-Many Message Mapping"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mo, step 5 "Iterating Splitter"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.o2mm, step 4 "Parallel Multicast"] [com.sap.integration.cloud.pipeline.v2.template.step07.outbound.processing.moar, step 1 "Sequential Multicast"] |
| `error_handling` | `ErrorEventSubProcessTemplate` steps and the steps under them | own logic there (notification, ticket) → custom candidate; human decides; default standard | — |
| `custom_exit` | none | human decides; default none | — |
| `inbound_conversion` | steps between the sender and the first gateway or mapping that change the format | conversion steps there → custom candidate; human decides; default none | — |

## Outputs

- The approved proposal (migration/proposal-template.md filled), the input of the generator.
- The artifacts of scenario-contribution.md "What a scenario contributes" for the approved values.
- The knowledge-base additions of the last step, committed.

## Append to the knowledge base

Spec §8, before the migration is called done:

- [ ] a new or amended recipe in conversion/ for every script, mapping or adapter pattern converted (conversion/README.md);
- [ ] a pitfall in pitfalls.md for every place where this knowledge base was wrong, silent or ambiguous;
- [ ] a note in the relevant iflows/ page or contracts.md for what the migration showed about the pipeline itself.

Each addition is a commit naming the scenario (anonymised if customer-specific). A migration is done only when this step is committed.
