# kb-author memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.

## Artifacts (generic pipeline iFlows, 2026-06-trial)
- Message-flow names are only the adapter type ("JMS", "ProcessDirect", "XI", "IDOC"); the participant name (dump `-> <endpoint>`) is what tells flows apart.
- JMS queue receivers in Step02+ are fed by message end events, not Send steps; find them via `<bpmn2:messageFlow sourceRef="EndEvent_...">`.
- Gateway conditions and end-event names are not in the dump: grep the `.iflw` for `sequenceFlow`, `conditionExpression`, `endEvent`.
- Scripts with the same file name in different generic iFlows were byte-identical (md5) in this label; line numbers carry over, check again per label.
- Queue prefix parameter has three names: PipelinePrefix (Step01 XI), DefaultJMSQueuePrefix (Step01 IDoc), PipelineJMSQueuePrefix (Step02+).
- The JSON dump's Content Modifier tables are `<row><cell id='Name'>...` strings; flatten with jq gsub into `ROW[...]` to read them.

## Budget
- BudgetTest counts whitespace tokens: table pipes and the `—` default cells count; `wc -w` undercounts by ~8. Measure with `tr -s ' \t\n\r\f\v' '\n' < page | grep -c .`.
- A step line with the full `[<id>, step "<name>"]` cite costs ~11 tokens; a 55-step iFlow leaves ~850 for the other nine sections. Write terse from the start; script-step cites (`[scripts/...#Ln]`) are 1 token.
