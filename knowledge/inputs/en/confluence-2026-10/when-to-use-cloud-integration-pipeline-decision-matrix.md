<!-- Translated from Confluence page "Kdy použít Cloud Integration Pipeline? (rozhodovací matice)" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# When to use the Cloud Integration Pipeline? (decision matrix)

Working draft — a living document. It was created as an initial guide; it will be extended and adjusted according to needs and operational experience.
A supplement to the Pipeline concept and the documentation of the individual pipeline steps.

## Starting point

Pipeline v2 is the default approach for integration between systems in our environment. It is meant for asynchronous processing (A2A/B2B, IDoc/XI/proxy, master data distribution).
For synchronous request/reply scenarios (e.g. an ABAP proxy as receiver) it is an optional approach — see the section "Synchronous scenarios".

So the question is not "when to use the pipeline" but "when to reach for something else":

- A clearly atypical one-off, where the generic pattern adds only overhead → **a single iflow**.
- A synchronous scenario without a need for landscape stages → the choice is up to the developer (both the pipeline and a plain iflow are fine).
- Orchestration / aggregation across multiple systems in sync → **an orchestration iflow**.
- Real-time notifications / events, where persistent processing via queues is an unnecessary burden → **Event Mesh / AEM**.

Everything else is a candidate for the pipeline.

## Pipeline v2 vs. a standalone plain iflow

| | Pipeline v2 (default) | Standalone plain iflow |
|---|---|---|
| When | async always when automatic resending is needed; sync at discretion and also when landscape stages are needed | an atypical one-off or a simple sync call without stages, real-time notifications / events |
| Nature of communication | async messaging (EO via JMS) + sync runtime for request/reply | anything, but purpose-built for a single case |
| Delivery guarantee | exactly once (JMS + dedup) async; BestEffort sync | according to design — you build it yourself |
| Error handling & retry | standardized, ready-made (retry, DLQ, custom statuses) — async | custom per iflow |
| Introducing a new scenario for the same message type | configuration in Partner Directory, no deployment | a new iflow + deployment |
| 1→N routing | recipient list from configuration (XSLT / PD) — async | has to be modelled manually |
| Monitoring | unified, correlated; business keys via custom headers | per iflow, custom (custom headers via your own script) |
| Landscape stages | data in PD (aliases + stages), iflow content identical across DEV/QA/PROD | you program and maintain it per iflow and per environment |

## Why the pipeline is the default for async (even for new scenarios)

For asynchronous communication with a need for automatic resending, the pipeline is a clear choice — these advantages are real and in a plain iflow they would have to be built and maintained manually:

- Operating model "for free": retry, DLQ, deduplication on input and output, unified custom statuses — the team writes and maintains none of it.
- Exactly Once guarantee via JMS (EO + dedup) — in a plain iflow you have to design persistence and deduplication yourself.
- Landscape stages as data, not as code: the sender→receiver stage mapping is handled by mapSenderStageToReceiverStage.groovy entirely from Partner Directory (the list of stages in SAP_Integration_Suite_Landscape, getAlternativePartnerId(...) finds in which stages the receiver exists, the stageRule_* rule engine with priorities). In a plain iflow you program and maintain this in the iflow, separately for each environment. With the pipeline the iflow content is identical across DEV/QA/PROD and only PD differs (in our landscape PD is identical, just created 4×).
- Introducing a new scenario for the same message type: e.g. a new B2B partner = records in Partner Directory, no transport, no deployment.
- 1→N routing (recipient list) from configuration (XSLT / PD).
- Standards: the developer uses prepared templates, standards cannot be bypassed, code review shrinks to the scenario-specific part.
- JMS queue economy: 2–4 queues for the entire async core vs. a queue per scenario.
- Maintenance from SAP: the generic flows receive fixes and features (changelogs: stage rules, bulk handling, DLQ behaviour…).

Note on monitoring: looking up by business key (document number, partner, message type) is done by the pipeline via addCustomHeaderProperties.groovy → messageLog.addCustomHeaderProperty(). But this is a standard CPI API — the developer can set up the same with a Groovy script in a plain iflow too. The pipeline just does it uniformly and without extra work; it is not a pipeline-exclusive advantage, rather consistency "for free".

## Synchronous scenarios (sync runtime)

For synchronous request/reply (typically a sync ABAP proxy as receiver, migration of sync proxy scenarios from PO) the pipeline runs via the sync runtime (/PIP/XS01).
The receiver call itself runs in the scenario-specific outbound flow (Step07), which the pipeline calls via Request Reply on the SAP_OutboundProcessingEndpoint and which returns the response. Around that, the pipeline wraps the receiver/interface determination from PD and unified monitoring.

In sync mode you do not get what makes the pipeline strong in async — no JMS, EO, retry or DLQ (you run BestEffort). What remains are softer benefits: consistent monitoring, uniform handling of headers, standards, PD determination. And because custom headers and a reasonable structure can be arranged in a plain iflow too, with sync the only pipeline-exclusive technical benefits are in practice landscape stages (alias → real system across DEV/QA/PROD, which in a plain iflow would be programmed and maintained per environment) and unified monitoring.

Decision guide for sync:

- The scenario needs landscape stages (identical configuration across DEV/QA/PROD via aliases) → pipeline (sync runtime). Here the pipeline has a clear added value.
- The scenario does not need stages (a simple isolated sync call, same connectivity / without alias mapping) → the choice is up to the developer. Both the pipeline and a plain iflow are fine — monitoring with business keys (custom headers via Groovy) and a reasonable structure can be done without the pipeline too; what decides is what is more readable and faster to build in the given case.
- Orchestration / aggregation across multiple systems → an orchestration iflow (see "Shape of the sync runtime"); the sync runtime of the pipeline cannot do this in principle.

Shape of the sync runtime (and its limitations): it processes exactly 1 receiver and 1 interface, BestEffort, without split (the router "Any Receiver?" throws "Too many receivers" when count > 1; stage routing rejects a split for a BestEffort interface). From this:

- Calling a single backend (sync proxy, sync SOAP) → the sync runtime fits, the guide above applies.
- Calling multiple systems and composing one response → this is orchestration (sequential Request Reply calls + aggregation into one response). Request/reply returns exactly one response, so distributing to multiple recipients as in async makes no sense here → an orchestration iflow.

Bypass of determination in sync (p2p): the primary mode of the sync runtime is content-based routing — a combined determination of receiver and interface by a single XSLT from PD (the same receiverDetermination XSLT as async), strictly with the result of 1 receiver + 1 interface. p2p is a supported bypass for the case of a fixed 1:1: it skips the determination and takes the endpoint directly from PD. It fits sync well, because p2p by definition carries exactly 1 receiver + 1 interface — which is exactly what sync requires. It is set in PD on the partnerID by two string parameters: receiverDetermination (= receiver alias) and interfaceDetermination_<receiverAlias> (= outbound endpoint) — both must be present; receiverDetermination alone would mean skipRcvDet, which sync does not support (→ exception). Even in p2p the message then passes through stage routing (sync 1→1) and the "exactly 1 interface" check, then Request Reply to the outbound flow.

## When to reach elsewhere

- A clearly atypical one-off — when it is certain that similar scenarios will not appear and the generic pattern (PD + know-how) adds only overhead, a plain iflow is more readable. Note: the criterion is atypicality and one-off nature.
- A simple sync call without a need for stages — the choice is up to the developer (see "Synchronous scenarios").
- Orchestration with a synchronous call — if a sync scenario needs to call multiple systems and compose a response from them, the sync runtime (1 receiver, 1 interface) cannot do it → an orchestration iflow.
- Real-time notifications / events → Event Mesh / AEM. When you do not need the guaranteed processing of the pipeline (EO/retry/DLQ/determination) and persistence via queues only adds latency, the pipeline is an unnecessary burden. In the integrated runtime an async message goes through TENANTX01 → TENANTQ04 (two JMS hops) plus the entire determination — for a lightweight real-time notification that is overkill; there it makes sense to publish directly to a topic via the event broker.
