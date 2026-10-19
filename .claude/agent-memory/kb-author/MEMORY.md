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
