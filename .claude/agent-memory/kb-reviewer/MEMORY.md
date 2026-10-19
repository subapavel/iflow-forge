# kb-reviewer memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.
- KB-04 B1 (2026-10-19): recurring findings on iFlow pages — (1) duplicate-named Send steps: check each step's
  message flow and participant in the .iflw (grep the ServiceTask id, then the messageFlow sourceRef), the page often
  swaps them; (2) "library, unused" claims for helper scripts: grep the step scripts for `import` of the helper first.
- KB-04 B2: (3) a lookup-precedence summary on a page is often compressed out of order against the script (stage rules →
  exact stage → tenant rules → exception in mapSenderStageToReceiverStage.groovy); check the if-order line by line;
  (4) a cross-iFlow "X calls Y" claim must name the route (gateway branch) on which the call happens.
- KB-04 B3: (5) a claim about several iFlows needs a cite per iFlow, one shared cite covers only its own artifact;
  (6) same-named Content Modifiers on different paths (custom-error vs DLQ): check which path the cited step is on.
- (7) Templates (KB-05): a "only the payload comes from outside the flow" claim needs `[<id>, property "allowedHeaderList"]` (empty); a cross-iFlow Partner Directory lookup claim covering several Step02 iFlows needs a script-line cite and the gateway/route cite per iFlow.
- (8) A copy-condition sentence cites the realising element; a manifest header that does not exist is never cited "(absent)".
- (9) Open-question bullets cite the wrong script line more often than pages do: check that the cited line is the assignment/read, not the comparison above it. "The Step01 templates" usually means eo + alo only (sync has no script step).
- (10) Count claims ("four exception subprocesses", "nine headers") against the .iflw element count; XPath paths differ between Step02 sync "Get interface" (/Interfaces/Interface/Service) and Step04/Step05 (/Interface/Service); default gateway flows are named "No (Default)".
