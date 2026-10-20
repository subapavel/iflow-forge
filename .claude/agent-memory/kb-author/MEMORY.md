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

## Step07 templates (learned on KB-05 B3, 2026-06-trial)
- File names are reused: p2p, p2p.to.idoc, p2p.to.xi keep P2P_ASYNC_0001.iflw; sync keeps PIP_01_InboundProcessing4Scenario1.iflw; custom.exit keeps PIP_03_InboundConversion4Scenario1.iflw.
- The IdempotentProcessCall of Step07 p2p IS inlined (17 -> 18): the Step01 eo non-inlining is not general; check per flow.
- All Step07 templates share the post-processing exit (Initialize: customXPipelineStep 07; "Exit?" Yes on CustomXPost_Enabled = 'true'; CustomExit `${property.CustomXPost_Endpoint}`); it runs BEFORE the receiver call; custom exit routes '07' to "Content Modifier 7".
- p2p and sync leave participant "Receiver" unconnected; p2p.to.idoc/xi call {{GenericIDocEndpoint}}/{{GenericXIEndpoint}} (placeholders tenant.endpoint.idoc/xi.outbound).
- Callers: Step06 end event "End" -> `${header.SAP_OutboundProcessingEndpoint}`; Step02 sync step 59 "ScenarioSpecificOutbound"; Step01 XI step 49 "ActualReceiver" on bypass p2p (gateway 41).
- Step08 takes a present SAP_<key> header by containsKey (setEdidcFromParamsForIDoc.groovy#L32, setValuesForXI.groovy#L42); EDIDC field headers only when listed in SAP_IDoc_EDIDC_overrideControlRecordParameters (#L50, #L54, #L61).
- Open questions of B3-B5 go to open-questions/kb-05-outbound.md (kb-05.md is full, ~1,150 tokens).

## One-to-many templates (learned on KB-05 B4, 2026-06-trial)
- o2mo, o2mm, moar keep P2P_ASYNC_0001.iflw, same 3 params and allowedHeaderList as p2p; no Bundle-Description; no mapping file (Mapping step mappinguri empty: cite the iflw resource).
- o2mm/moar: two branches ("Branch 1"/"Branch 2" sequenceFlow names) share message end event "End", no Join/Gather; receivers are participants Receiver1/Receiver2. moar SequentialMulticast routingSequenceTable gives the order.
- moar step 6 "Call Exception Handling" has an empty processId (SAP bug): the dump then inlines the exception process under the next call with a processId (step 11).
- Idempotency key there is property SplitMessageID (`${header.UniqueID}_..`), not header UniqueID.
- Verify step cites in bulk: awk the dump section per id and grep `^ *[n.] Type "name"$` (count must be 1 when no step number).

## Custom exit, error handling, collection (learned on KB-05 B5, 2026-06-trial)
- The dump JSON is a stream of objects: `jq 'select(.id==...)'`, never `.[]`.
- Exit callers: the 8 generic iFlows except Step08 ("Call custom exit": CustomXScenario `${property.CustomXEndpoint}`, CustomXGlobal; customXPipelineStep from readCustomXFromPD.groovy#L29, byte-identical in all 8) and the 7 Step07 templates ('07', CustomExit). No caller sends '03'.
- Error-flow callers: the same 8, Request Reply "Send to custom error flow" at `{{CustomXError_Endpoint}}`; only Step02 inbound/async, Step04, Step05, Step06 set incomingQueue; caller "End event?" tests only 'Completed'/'Escalated'.
- Generic .iflw script steps carry an empty scriptBundleId; 8 templates Require-Capability the collection (p2p.to.idoc without a Script step).
- Fast authoring: write with @XX@ shorthand ids and expand with a scratchpad sed file; verify step/adapter/parameter cites in bulk against TSVs built from the dump JSON.

## Script pages (learned on KB-06 B1, 2026-06-trial)
- Script steps per artifact: `jq -r '.id as $a | .steps | to_entries[] | select(.value.activityType=="Script") | "\($a)\t\(.key+1)\t\(.value.name)\t\(.value.properties.script)"'` over target/kb-04/*.json; the md dumps only list `- resource:` lines.
- Enclosing local process of a script step: awk the .iflw (`<bpmn2:process name=` then `<value>X.groovy</value>`); names are XML-escaped (`&amp;`).
- PD readers: all but readReceiverSpecificQueueFromPD throw IllegalStateException on missing service/partnerID; most write `?: null` (property set to null though comments say "not created"); readCustomInterfaceDetermination/readReceiverNotDetermined use the legacy logger (getAuditLog + setHeader auditLogHeader), same header as saveToMessage (PipelineLogger#L25/#L69/#L73).
- All 8 readCustomXFromPD callers set CustomXType = CustomXPre in Enricher "Initialize pre-processing exit" (step 5; 2a 7; 2s 3; 4/5 6).
- Header overrides customXInboundConversionEndpoint, customXRDEndpoint, CustomXPreEnabled/Endpoint are set nowhere in the label.
- The collection has no .iflw: cite "not referenced" with `[collection, manifest "SAP-BundleType"]`.
- Write pages with `~L<n>`/`~P<n>` + @id@ shorthand, expand per stem with sed; verify cites in bulk (line non-blank, step TSV, process name in .iflw).

## IDoc scripts (learned on KB-06 B2, 2026-06-trial)
- Canonical copy for stems in neither collection nor Step02 inbound: first id of the alphabetically sorted *Used by* list (step01.idoc < step01.xi < step08).
- Many IDoc scripts lack a trailing newline: `cat` of several files glues lines; number each file separately (`awk '{print NR": "$0}'`, tab-indented lines break `cut -f`).
- Step08 IDoc: setEdidcFromParamsForIDoc sets properties Receiver_IDoc* (router "Content Type?" step 29 reads them); validation steps 8/12 force header SAP_Receiver_IDocContentType. Text/XML path 30 -> 42 -> 43, X-SAP.IDOC 45 -> 43 (no node list).
- determinePartnerIDForIDocInbound: HeaderStateTracker rollback covers only tracker.setHeader; step-4 writes use message.setHeader. 1,065 tokens with precedence as nested ordered list.
- Adapter cite form for JMS receivers: `[<id>, adapter "JMS" "<participant>"]`.
- Review B2 lessons: check that a rollback/restore claim matches what the tracker actually stores (for an IDoc sender, SAP_Sender and SAP_SenderAlias are tracked); cross-reference the page's own list numbers, not the code-comment numbers; a ProcessCallElement name is not the local process name (cite `process "<name>"`); before writing "not in the artifact", follow the flow to the next step (Step08 step 44 addCustomHeaderProperties turns customHeader_* into MPL headers); write Groovy runtime effects (null concatenation, GPath truth, .text() on lists) as what the line does, not as a result.

## XI and mapping scripts (learned on KB-06 B3, 2026-06-trial)
- mapSenderStageToReceiverStage.groovy has CRLF endings and no final newline: `wc -l` says 300, awk 301; strip `\r` before the non-blank check.
- `SapDynamicConfiguration_` occurs only in the two dc scripts (L5); dc(.*) is in the allowedHeaderList of Step02-05; Step08 XI uses {{AllowedHeadersList}} (no propdef default) and deletes `dc_*` in step 10.
- Step08 XI order: 1 dc props, 2 defaults (location XIGenericOutbound), 4 "XML?" (non-XML skips step 5 root XPath), 6 readPDValuesForXI, 7 setValuesForXI, 8 custom headers, 11 "Location & Auth?".
- Virtual-stage path: router "Virtual Stage Routing?" (`tenantStageVirtual = 'true'`) -> "Map to Receiver Stage" -> "Multiple Stages?" (async, Step04 only; sync goes straight to "Map to Business Name").
- All B3 script steps sit in the main process; Step02 async/sync/Step04 main process names are long ("Integration Process - ..."): copy them from the .iflw.
- Cite forms that passed earlier: `[<id>, property "allowedHeaderList"]`, `[<id>, parameter "<name>"]`, `[<id>, manifest "Require-Capability"]` for templates using the collection.
- Write "throws no exception of its own" rather than "no exception leaves the script".
- CHANGELOG cite form (D-019 amended): `[<package-folder>/CHANGELOG.txt#L<n>]` (generic-inbound, generic-outbound, generic-pipelines, templates). Read them before any version claim or "open" question: B3 r1 lost 3 bullets the change log answered.
- "Who sets X" questions: check the step-1 Content Modifier of the iFlow first (Step02 sync step 1 "Set custom extensions" sets SapQualityOfService = BestEffort), and grep the whole label before writing "no setter" (the mirror script may set it).
- A negative ("no template names it") needs "grep of every <scope> .iflw" stated or a resource cite.

## Helpers, logging, errors (learned on KB-06 B4, 2026-06-trial)
- md5 every copy, never trust the inventory: throwException has 3 variants (collection L8 default location differs from the six generic 35-line copies; sync 12 lines).
- Step08 exception handling: Add Custom Headers -> "Audit Log Header?" (no: Recover Audit Log from property auditLogProperty) -> "Add Exception to the Log" (messageText, location, mplAttachmentName ExceptionSubProcess_, mplLoggingConfiguration = {{Exception_LoggingConfiguration}}, no value) -> "Save in Audit Log" (addAuditLog) -> "Log Message per Config".
- Only Step08 Content Modifiers set `location`; exceptionType is set nowhere; attachAdditionallnformation runs twice (test mode, DLQ) except Step02 sync (once, no incomingQueue).
- Shell: .iflw paths contain spaces and " - ": an unquoted `$(find ...)` gives grep a "-" arg and it hangs on stdin; use `find -print0 | xargs -0` or `-exec`. `pkill -f <pattern>` kills the calling shell too.
- Expander: shorthand inside brackets plus a macro that adds brackets yields `[[...]]`; grep `\[\[` after every expansion. NamesTest rejects a backticked bare `.groovy`: write "every Groovy script".
