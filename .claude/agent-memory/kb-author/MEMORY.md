# kb-author memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.

## Artifacts (generic pipeline iFlows, 2026-06-trial)
- Message-flow names are only the adapter type ("JMS", "ProcessDirect", "XI", "IDOC"); the participant name (dump `-> <endpoint>`) is what tells flows apart.
- JMS queue receivers in Step02+ are fed by message end events, not Send steps; find them via `<bpmn2:messageFlow sourceRef="EndEvent_...">`.
- Gateway conditions and end-event names are not in the dump: grep the `.iflw` for `sequenceFlow`, `conditionExpression`, `endEvent`.
- Scripts with the same file name in different generic iFlows were byte-identical (md5) in this label; line numbers carry over, check again per label.
- Queue prefix parameter has three names: PipelinePrefix (Step01 XI, Step02 sync address), DefaultJMSQueuePrefix (Step01 IDoc), PipelineJMSQueuePrefix (Step02+).
- The JSON dump's Content Modifier tables are `<row><cell id='Name'>...` strings; flatten with jq gsub into `ROW[...]` to read them.

## Budget
- BudgetTest counts whitespace tokens: table pipes and the `—` default cells count; `wc -w` undercounts by ~8. Measure with `tr -s ' \t\n\r\f\v' '\n' < page | grep -c .`.
- A step line with the full `[<id>, step "<name>"]` cite costs ~11 tokens; a 55-step iFlow leaves ~850 for the other nine sections. Write terse from the start; script-step cites (`[scripts/...#Ln]`) are 1 token.

## Reading the routing (learned on Step02 async/sync, Step04)
- The JSON dump's step `id` is the BPMN element id: grep the `.iflw` `sequenceFlow` lines (sourceRef/targetRef/name + conditionExpression) and map ids to dump step numbers; messageFlow sourceRef=ServiceTask_/EndEvent_ gives the adapter per step.
- Dump order is not flow order: in Step02 sync the two "Request Reply" exit calls are #9 = CustomXScenario, #10 = CustomXGlobal (reverse of async). Always map ServiceTask id -> messageFlow -> participant.
- Backoff parameter spelling: JMS_ExpotentialBackoff (Step02 async, Step04) vs JMS_ExponentialBackoff (Step02 inbound). Message-flow names: JMS_OuboundProcessing, JMS_InterfaceDetermination (typo "Oubound" is SAP's).
- Step04 has no retry-read script; its retry gateway uses maxJMSRetries from upstream (open question in pitfalls).

## Budget (cont.)
- Bare step lines cost ~5 tokens each (99 steps = 495); a table row costs 5 pipe tokens plus cells. 1,350 is out of reach above ~90 steps; group script rows and point to "Partner directory" instead of repeating cites.

## Generic outbound (learned on Step05, Step06, Step08 IDoc/XI, 2026-06-trial)
- Step08 message-flow names are the channel names (CC_IDOC_Receiver_OnPremBasic, CC_XI_Receiver_InternetNone, CC_ProcessDirect_Sender); endpoints repeat (OnPremise, Internet), so cite by flow name. Step08 XI and Step05/06 receivers hang on end events, Step08 IDoc on ExternalCall steps.
- `checkWellFormedXML.groovy` sets a property in Step08 but a header in Step04-06 (different md5); verify per flow.
- Step06's .iflw is named PIP_02_Generic_InboundProcessing.iflw; its retry gateway is `SAPJMSRetries = maxJMSRetries or maxJMSRetries = '0'` (Step04/05 use `>` in the main flow).
- Step08 entry parameters: ProcessDirect_Address (IDoc) vs ProcessDirect_EndPoint (XI); templates call {{GenericIDocEndpoint}} / {{GenericXIEndpoint}}.
- BudgetTest applies to every *.md. Since F-3, open questions go to open-questions/<task>.md (one page per content task, <= 1,500 words), not pitfalls.md; kb-04.md was 1,460 words.
- Fast routing read: a scratchpad awk script over the .iflw (sequenceFlow sourceRef/targetRef/name + conditionExpression, messageFlow source -> participant) with dump step ids mapped to numbers (`jq '.steps|to_entries[]|"\(.value.id) \(.key+1)"'`).

## Templates (learned on KB-05 B1, 2026-06-trial)
- Templates have no script folder; script steps carry scriptBundleId = the script collection; cite `[<collection id>, scripts/<file>#L<n>]`. MANIFEST Require-Capability (resolution optional) names the collection; Step01 sync has none. No Bundle-Description in B1 manifests (join continuation lines before grepping).
- Step01 templates have an unconnected participant "Sender" (no message flow): cite `[<id>, resource "<iflw path>"]`.
- Dump quirk: an IdempotentProcessCall's local process is not inlined (Step01 eo: Send listed as step 1, depth 0); a local process called three times is inlined only under the first call (Step01 sync).
- The template id folders are `...step01.inbound.processing.<qos>`, not `...step01.<qos>`; take ids from `ls templates/`.
- Coverage log repeats each `missing page:` line ~4 times; count distinct with `sort -u`.

## Determination templates (learned on KB-05 B2, 2026-06-trial)
- All step04/step05 templates keep the Step03 file name PIP_03_InboundConversion4Scenario1.iflw; one ProcessDirect sender `{{ProcessDirect_EndPoint}}`, allowedHeaderList set (step05 adds SAP_Receiver(.*)).
- Callers: Step04 step 71 / async step 48 / sync step 34 Request Reply "ReceiverXRD" (`${property.customXRDEndpoint}`); Step05 step 53 "ReceiverXID". Integrated runtimes reject reuseXRD (async 49, sync 35: ConfigurationOptionNotSupported).
- Template Content Modifiers carry SAP's angle-bracket placeholder text as values; Mapping steps have empty mappinguri. Empty skeletons have Start->End only: cite the iflw resource.
- SAP-Operation / SAP-OperationSpecific are read by the collection's custom*ViaXProperties scripts but set nowhere in the label.
- open-questions/kb-05.md reached ~1,020 tokens after B2: later batches must write short bullets.
- Review B2 lessons: a claim about "the Step04 templates" needs one cite per template; check BPMN process names (`[<id>, process "<name>"]`, D-019) before writing "not found in the artifact"; mapMessageTypeToOperation.groovy sets SAP_SenderOperation at L24 (L18 is the comparison); a Read-table row covering several names needs the step per name.
