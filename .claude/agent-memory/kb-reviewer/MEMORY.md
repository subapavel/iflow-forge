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
- (11) A copy condition with an idempotency clause tends to cite the Idempotent Process Call (sourceMessageID `${property.SplitMessageID}`) but not the Content Modifier that builds the key (`${header.UniqueID}_...`); both are needed.
- (12) "Local process X" vs the ProcessCallElement step that calls it: grep `ifl:type="IntegrationProcess"`/`bpmn2:process ... name=` before accepting a local-process name; the step and the process usually differ ("Call custom exit" vs "Call custom exits (global and scenario-specific)"). An open question that only agrees with the draft is not an open question: it goes.
- (13) Script pages (KB-06): "before the router X" in a Purpose covering n iFlows needs the router step cite for each of the n iFlows (the Used by bullets are the natural place); a header name built by concatenation (PipelineLogger L25) is cited at the building line, not at a later use.
14. (KB-06 B2) Rollback/restore claims: check which headers a tracker recorded earlier in the flow, not only the later untracked write; a numbered precedence list on a page must be cross-referenced by the page's own numbering, not the code's comment numbering; "which later step does X is not in the script" is not an open question when the .iflw's flow order and a later script answer it.
15. (KB-06 B3) Before accepting an open question, grep the label's `*/CHANGELOG.txt` (cite form `[<package-folder>/CHANGELOG.txt#L<n>]`, D-019) and the step-1 Content Modifiers of the iFlows named: they often answer a "version"/"who sets this" question. A negative claim ("no step of the label sets X") needs a grep over every script and .iflw of the label and must name what was searched.
