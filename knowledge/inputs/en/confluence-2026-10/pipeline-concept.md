<!-- Translated from Confluence page "Pipeline concept" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Pipeline concept

Extract from the official SAP documentation "Pipeline Concept". A summary in my own words for internal use — it does not replace the official documentation. The cross-references lead to the internal documentation of the individual iflows (reverse analysis of the Cloud Integration Pipeline packages).

## Notation for the integrated runtime

SAP recommends using the integrated messaging runtime. Passages marked [FULLY DECOUPLED PIPELINE ONLY] are not used in the integrated runtime — they are described for completeness and context.

## Part I — Explanation (1–7)

### 1. What the pipeline concept is and why it is called that

The pipeline concept transfers the way messages are processed in SAP PI/PO to Cloud Integration. Instead of every scenario having its own freely modelled iflow, a message passes through a fixed sequence of steps, always the same:

receiver determination → interface determination → mapping → delivery

Hence the name: the message flows through one pipe (pipeline) — a series of steps through which every message passes in the same order. The logic that differs between scenarios (where to send the message, how to map it) is not hard-wired in each flow separately — it is configuration that the common generic steps load at runtime according to the particular scenario.

Although the pipeline concept originated for the needs of migrations from SAP PO, today it makes sense for new scenarios as well — when to deploy it and when not is covered by Pipeline - decision matrix

Five building blocks (only by name for now, detail in 5). Before we go to the steps, it is good to know the vocabulary — we will come across these terms in the following chapters:

| Block | In one sentence |
|---|---|
| Generic flows | common pipeline steps, deployed once for all scenarios; maintained and updated by SAP |
| Scenario-specific flows | copies of templates carrying the conversions, mappings and adapters of a particular scenario |
| JMS queues | decouple the steps → persistence, restartability, guaranteed delivery |
| ProcessDirect | synchronous calls between flows (generic step → scenario-specific flow) |
| Partner Directory (PD) | per-scenario configuration (behaviour and XSLT mappings) — that is why one generic flow serves all |

### 2. Why use it

1. Significant saving of JMS queues. With a 1:1 migration, every async scenario would need its own queue — easily thousands, whereas the tenant has hard limits. The pipeline gets by with a few queues shared by all scenarios:
   - fully decoupled pipeline: 4 queues (PIPQ01–PIPQ04) + 4 DLQ = 8 in total,
   - integrated messaging runtime: 2 queues (PIPX01, PIPQ04) + 2 DLQ = 4 in total.
2. A ready-made operating model. Retry, DLQ, deduplication on input and output, restart, uniform custom statuses — none of this needs to be written or maintained. (Custom statuses = uniform names of message states in the monitor, by which it is possible to filter; what exactly they mean, see 12.)
3. Traceability in monitoring. The pipeline makes it possible to find a message by a business key (document number, partner, message type) just by setting a property with the prefix customHeader_, without custom code (see 12).
4. A new scenario = configuration, not development. A new partner of the same message type = just a few records in the Partner Directory, no deployment. A new message type does need a new scenario-specific flow as well (a copy of the template with the mapping and adapter), but the generic line does not change — only the small part specific to the scenario is deployed and transported, not the whole infrastructure.
5. Enforced standards. The Step07 templates (any of them — P2P, One-to-Many, Maintain Order, Sync, P2P→Generic XI/IDoc) have built-in standards (Exception Handling with the status RetryViaParentFlow, idempotent delivery, custom post-processing exit), so the developer cannot omit them by mistake — they only fill in the mapping, conversion and adapter. It is not one particular template; the common frame is the same in all variants, they differ only in the delivery pattern. Code review is thereby narrowed to the scenario-specific part.
6. Maintenance by SAP. The generic flows receive fixes and new functions (stage rules, bulk handling, DLQ behaviour…).

### 3. The individual pipelines and their steps

The concept evolved gradually. It started with the fully decoupled pipeline, where every step is a separate iflow — this is the most illustrative variant for understanding, because it is literally visible what happens where. Practice then showed that so many separate steps (and thus queues and logs) are not needed, and the integrated messaging runtime came into being, which handles the work of several steps in one. The sync variant is even more economical — without queues.

| | Fully decoupled | Integrated messaging async | Integrated messaging sync |
|---|---|---|---|
| Steps | 7 (Step01 → … → Step07) | 5 | 3 (no queues) |
| JMS queues | PIPQ01–Q04 | PIPX01 + PIPQ04 | none |
| Receiver + interface determination | separate XSLTs (combined is possible too) | combined XSLT only | combined XSLT, strictly 1 receiver + 1 interface |
| Reuse of XRD from PO | yes | only via custom RD (must also return interfaces) | no |
| skipRcvDet | yes | no | no |
| Custom interface determination | yes | no | no |
| P2P bypass / custom error handling / receiver-specific queues | yes | yes | P2P yes |

Note: "no" for skipRcvDet and custom interface determination in the integrated runtime is not a loss of functionality — why is explained by 4. The column Fully decoupled and the rows Reuse XRD, skipRcvDet and Custom interface determination describe functions that are not used in the integrated runtime ([FULLY DECOUPLED PIPELINE ONLY]).

#### Fully decoupled pipeline (7 steps)

[FULLY DECOUPLED PIPELINE ONLY] — this entire seven-step variant is not used in the integrated runtime (the steps Step02/Step04/Step05 and the queues PIPQ01–PIPQ03 are merged in the integrated one). Kept for illustration and completeness; for us, the integrated async/sync below applies.

1. Step01 – Inbound Processing (scenario-specific) — sender adapter. Sets the headers SAP_Sender and SAP_SenderInterface (this pair uniquely determines the scenario) and writes the message to the input queue (PIPQ01) as early as possible, so that it is persistent and decoupled from the sender. → Step01 XI, Step01 IDoc, for the others copies of the template
2. Step02 – Inbound Processing (generic) — determines the partnerID, finds out from PD whether a conversion is needed, calls it and, according to the bypass option, sends the message on. → Step02 Inbound
3. Step03 – Inbound Conversion (scenario-specific) — JSON→XML, CSV→XML, unzip… Called from Step02.
4. Step04 – Receiver Determination (generic) — an XSLT from PD evaluates the conditions and returns a list of receivers; the message is split per receiver. → Step04
5. Step05 – Interface Determination (generic) — per receiver, an XSLT from PD returns a list of interfaces; it is split per interface and the key header SAP_OutboundProcessingEndpoint is set. → Step05
6. Step06 – Outbound Processing (generic) — dispatcher: sets the UniqueID (the basis of deduplication) and calls the scenario-specific outbound flow via ProcessDirect. → Step06
7. Step07 – Outbound Processing (scenario-specific) — mappings, outbound conversion, idempotent process call, delivery by the receiver adapter. → Step07 – Outbound Processing

#### Integrated messaging async (5 steps)

→ Step02 Async

1. Step01 – Inbound Processing (scenario-specific) — same as for the fully decoupled one, only the target queue is PIPX01. (For the generic XI/IDoc: Step01 XI, Step01 IDoc, for the others copies of the template)
2. Integrated Messaging Runtime Async (generic) — one step that does the work of Step02 + Step04 + Step05: inbound conversion, combined determination of receivers and interfaces by one XSLT, and retry handling. Output to PIPQ04. → Generic Step02 – Integrated Messaging Runtime Async
3. Step03 – Inbound Conversion (scenario-specific) — conversion, called from the runtime (as with the decoupled one). → Step03 – Inbound Conversion
4. Step06 – Outbound Processing (generic) — dispatcher, identical to the fully decoupled pipeline. → Generic Step06 - Outbound Processing
5. Step07 – Outbound Processing (scenario-specific) — mapping and delivery, identical to the fully decoupled pipeline. → Step07 – Outbound Processing

#### Integrated messaging sync (3 steps)

For synchronous request/reply (typically an ABAP proxy as the receiver). Without JMS, everything via ProcessDirect. → Step02 Sync

1. Step01 – Inbound Processing Sync (scenario-specific) — sender adapter + conversion of the request; calls /PIP/XS01 via Request Reply.
2. Integrated Messaging Runtime Sync (generic) — combined determination, strictly exactly 1 receiver and 1 interface (otherwise an error back to the caller), calls the scenario-specific outbound.
3. Step07 Sync (scenario-specific) — mapping of the request → call of the receiver → mapping of the response; the response travels back to the caller.

### 4 Why async and why the "unsupported" functions are not a disadvantage

Why async (and why as the default). SAP recommends the integrated messaging runtime and the default input queue of the generic XI/IDoc inbounds is today PIPX01 (formerly PIPQ01). The reason is simple: integrated async merges three steps into one, so instead of four working queues two are enough, there are fewer logs in the monitor and fewer ProcessDirect calls.

So what remains as a reason to go for the fully decoupled pipeline? (Both items below are [FULLY DECOUPLED PIPELINE ONLY] — not relevant for us, given for context.) Only two things:

1. Direct reuse of the XRD mapping from PO without changes — in async this has to be solved via custom receiver determination, there is a prepared template Step04 – Custom Receiver determination and interface determination via Extended Receiver Determination.
2. A separate log and entry point of each step — readability during debugging (every step has its own record in the monitor) and the possibility to enter a particular step separately.

And now the key thing. Integrated async "does not support" skipRcvDet and custom interface determination. It looks like a concession, but it is not — their result is achieved in async by a different, not worse way. The reason: in the integrated runtime, receivers and interfaces are determined by one combined mapping that returns both at once. All the logic of "where and with which interface" thus lives in one place.

Example — "fixed receiver, but interface by logic". This is exactly what skipRcvDet did (fix the receiver, determine the interface afterwards). In async you write it as a constant Receiver and a conditional Interface in one XSLT (receiverDetermination binary parameter of the scenario):

```xml
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:ns0="http://sap.com/xi/XI/System">
  <xsl:template match="/">
    <ns0:Receivers>
      <ReceiverNotDetermined><Type>Error</Type></ReceiverNotDetermined>

      <!-- Receiver je konstanta (to, co dlal skipRcvDet) -->
      <Receiver>
        <Service>ERP_QA</Service>

        <!-- Interface se vybere logikou nad payloadem (to, co dlala custom IF determination) -->
        <Interfaces>
          <xsl:choose>
            <xsl:when test="//OrderType = 'RUSH'">
              <Interface><Index>1</Index><Name>OrderFast_Out</Name>
                <Service>/scenario/sales/rush/out</Service></Interface>
            </xsl:when>
            <xsl:otherwise>
              <Interface><Index>2</Index><Name>OrderStandard_Out</Name>
                <Service>/scenario/sales/std/out</Service></Interface>
            </xsl:otherwise>
          </xsl:choose>
        </Interfaces>
      </Receiver>
    </ns0:Receivers>
  </xsl:template>
</xsl:stylesheet>
```

The same output as skipRcvDet + custom interface determination together, just written in one mapping instead of being split into a fixed string parameter + a separate step.

If you do not want to write the logic in XSLT, in async you still have custom receiver determination (PD parameter CustomXRDEndpoint). Verified in readCustomReceiverDeterminationFromPD.groovy: the script loads CustomXRDEndpoint and sets customXRDBoolean=true; the pipeline then calls your custom flow via ProcessDirect and it must return the receivers and their interfaces (Receivers XML with nested Interfaces) Schema Receivers.xsd. The logic can thus be a full-fledged iflow, not just a mapping. There is also the template Custom Receiver and Interface Determination via Extended Receiver Determination, which, unlike ReuseXRD, is usable in the integrated runtime as well (see 6).

Conclusion. In async the capability is not missing, the logic is just written in one place (combined mapping / custom RD flow). In return you get half the number of queues. That is the reason why async is the default.

### 5 The building blocks of the pipeline and their purpose

Two types of iflows. Generic flows are deployed once and without changes — they are the same for all scenarios and receive updates from SAP. Scenario-specific flows are created by copying templates (or by the migration tool) and carry the conversions, mappings and adapters of a particular scenario. The boundary between them is the whole point of the concept: what is common is generic and maintained by SAP; what differs is configuration or a small copy of a template.

JMS queues decouple the steps. This creates persistence (the message survives a crash), restartability (it is possible to continue from the queue) and guaranteed delivery. The price is the number of queues — hence the effort to have as few of them as possible (see 2).

ProcessDirect is a synchronous call between flows — fast, but synchronous: when the called flow fails, it remains in the status Failed, even though the retry is controlled by the caller. Hence the convention of the status RetryViaParentFlow ("ignore this log, the retry is handled by the parent step").

Partner Directory (PD) holds the per-scenario configuration in two forms: string parameters (behaviour of the processing — endpoints, retry counts, switches) and binary parameters (XSLT mappings). Thanks to this, there is one generic flow for all scenarios — they differ only by their records in PD. Detail in 8.

XSLT mappings replace the multicast + routers from PO: the routing conditions (XPath) are evaluated inside the XSLT. XSLT was chosen instead of graphical message mapping because PD can store XSLT as a binary parameter. The conditions do not have to be over the payload only — headers with the prefix dc (dynamic configuration) can be read in XSLT as xsl:param. Beware: XSLT is run only over XML; when the payload is not XML, the body is replaced with <dummy></dummy> before the mapping and the routing is then governed exclusively by the dc parameters (headers) — it is not possible to decide over the content.

### 6 Scripts and templates with explanation

#### Templates (18) — default skeletons of scenario-specific flows

You copy the template, rename it, add the logic and deploy it per scenario. Full description: Templates.

Common features of the template copies: ProcessDirect input (must be set uniquely per scenario for every copy), Exception Handling with the status RetryViaParentFlow and, for Step07, a built-in custom post-processing exit. (This does not apply to the ready-made generic XI/IDoc inbound flows — they have no ProcessDirect input, they are started directly by the XI/IDoc adapter.)

| Group | Templates | What for |
|---|---|---|
| Step01 | Exactly Once / At Least Once / Synchronous | sender adapter (HTTP, SOAP, SFTP…); with/without deduplication, or sync |
| Step03 | Inbound Conversion | conversion of the input called by the generic Step02 |
| Step04/05 | Custom RD (empty) · RD+IF via Exchange Properties · RD+IF via Extended RD · Reuse Extended RD · Custom Interface Determination (in italics only for the fully decoupled pipeline) | scenario-specific determination of receivers/interfaces when the XSLT in PD is not enough |
| Step07 | P2P · One-to-Many (1 type) · One-to-Many (multiple types) · Maintain Order at Runtime · Sync · P2P→Generic XI · P2P→Generic IDoc | mapping + conversion + delivery; variants according to the determination pattern from PO |
| Custom exits | Custom Exit · Custom Error Handling | extensions without touching the generic flows |

[FULLY DECOUPLED PIPELINE ONLY] — of the Step04/05 templates, Reuse Extended RD and Custom Interface Determination (separate interface determination) are not used in the integrated runtime. The others (Custom RD, RD+IF via Exchange Properties, RD+IF via Extended Receiver Determination) return receivers and interfaces at once, so they work in the integrated one.

#### Script Collection (13 scripts) — shared artifact

The templates do not bundle the scripts locally, they reference them from the collection Pipeline v2 – Script Collection (scriptBundleId = com.sap.integration.cloud.pipeline.v2.script.collection). It is deployed once and must be on the tenant before the copies of the templates.

## Part II — Reference (8–15)

### 8 Partner Directory in depth

#### Partner ID = configuration scenario

The recommended way: alternative partner. PartnerID = the name of the scenario; the sender → scenario mapping via AlternativePartner: Agency = the sender system, Scheme = the constant SenderInterface, Id = the name of the interface. This is the standard setting. There is also an optional variant for distinguishing interfaces with the same name in different namespaces — it is tried first if the namespace is available: Scheme = the name of the interface, Id = the namespace. (Verified in determinePartnerID.groovy: getPartnerId(agency, interface, namespace) is tried first, only then getPartnerId(agency, "SenderInterface", interface).) Advantages: longer limits (agency/scheme 120, ID 255 characters vs. 60 for partnerID), sender wildcard, grouping in the PD UI and only this variant supports landscape stages.

The legacy "tilde" way. The partnerID is not built via an alternative partner, but literally as the string <Sender>~<Interface> (e.g. EDI_HUB~PurchaseOrder_In). "Sender" is the sender alias (or the logical system / a combination with party), "Interface" is SAP_SenderInterface (the characters & % / are replaced by _).

Where it is "assembled": you do not configure or assemble it manually anywhere. The string is built automatically by the script of the generic flow as the last fallback, when no alternative partner works out and CustomXPid_SwitchOffTilde is true. It applies to XI and IDoc alike — XI: determinePartnerID.groovy (pid = agency + "~" + interface); IDoc: determinePartnerIDForIDocInbound.groovy (Pid = Agency + '~' + IdocSenderInterface…, "Fifth option"). Your job is only to create the scenario parameters in PD under this resulting ID. So: the construction of the ID = automatic in the iflow; the configuration of the scenario = in PD under that ID. Example PID: EDI_HUB~PurchaseOrder_In

It is switched on/off in the generic iflow via the externalized parameter CustomXPid_SwitchOffTilde (default true = off) in parameters.prop of all five flows that determine the partnerID (Step01 XI, Step01 IDoc, three variants of Step02). It is a flow-level switch, not per scenario and not in PD. The default true ensures that a missing configuration ends in a readable error instead of silently building a wrong ID. When you need tilde (quick backward compatibility with PO, where the scenario was not identified via alternative partners), you switch CustomXPid_SwitchOffTilde = false (applies to XI and IDoc alike). It is, however, an emergency way — without the advantages of an alternative partner (shorter limits, no wildcard, no stages).

The sender interface is read from the header SAP_SenderInterface. For XI it is filled by Step01 XI from the XI adapter; for IDoc, Step01 IDoc composes it from the IDoc type MESTYP.IDOCTYP[.CIMTYP].

Order of the partnerID checks — XI (determinePartnerID.groovy, from the most specific):

1. Agency + interface (as scheme) + namespace
2. Agency + SenderInterface + interface
3. tilde Sender~Interface (if switched on)
4. error "Partner ID Missing Error"

Order of the partnerID checks — IDoc (determinePartnerIDForIDocInbound.groovy; each attempt tries AltPartner via interface+namespace, then SenderInterface+interface, with a differently composed Agency):

1. virtual receiver (Agency = sender + receiver)
2. sender only (logical system → alias / logical system)
3. SNDPRT ≠ LS via sender alias / logical system
4. SNDPRT ≠ LS via the direct partner number SNDPRN (compatibility)
5. tilde alias~interface (if switched on)
6. error

IDoc inbound has its own, extended rules (determinePartnerIDForIDocInbound.groovy) — the Agency is derived from the EDIDC fields:

- SNDPRT = LS → logical system = SNDPRN; with stages it is first mapped to an alias (AlternativePartner: Agency=stage, Scheme=LogicalSystemName, Id=log. system → Pid=alias).
- SNDPRT ≠ LS (KU, LI, US…) → logical system from the port SNDPOR (format SAP<SID>) + MANDT; Agency = ALE#<SNDPRT>[#<SNDPFC>]#<SNDPRN>.

Virtual receiver (IDoc, off by default, CustomXPid_ResolveVirtualReceiver): the scenario is determined not only from the sender, but from the sender and the receiver encoded in the IDoc (fields RCVPRT/RCVPFC/RCVPRN). The Agency is then <sender part>:<receiver part>. What it is for: the same sender and the same interface can lead to different configuration scenarios depending on whom the IDoc is addressed to — without the virtual receiver they could not be distinguished.

#### String parameters of the scenario

| Id | Meaning | Default |
|---|---|---|
| InboundConversionEndpoint[_<sender>] | endpoint of the inbound conversion (per-sender variant for wildcard) | – |
| MaxJMSRetries | max number of retries | 5 |
| ReuseXRDEndpoint + ReceiverNotDeterminedType | reuse of the XRD mapping from PO — [only the fully decoupled pipeline] (in the integrated one replaced by a custom RD flow) | Type=Error |
| CustomXRDEndpoint / CustomXIDEndpoint | custom receiver / interface determination | – |
| InboundQueue | override of the inbound queue (only the generic XI/IDoc inbounds) | PIPX01 |
| KeepBulk | do not split the IDoc bulk | false |
| CustomXPreEnabled + CustomXPreEndpoint | custom pre-processing exit (see 15) | – |

For a partner = receiver: ReceiverSpecificQueue (globally) or ReceiverSpecificQueue_<receiver> (overrides the global one).

InboundQueue — what it is for. Verified in readInboundQueueFromPD.groovy: per scenario it overrides the input queue to which the generic XI/IDoc inbound writes. With it you send the scenario to a different pipeline instance than the default PIPX01. Why you would want that: to run several parallel pipelines with their own queue prefix (e.g. low/medium/high priority), so that priority scenarios do not wait in the queue behind mass batches — or to direct a particular scenario to the fully decoupled pipeline (PIPQ01) instead of the integrated one (PIPX01). Described in more detail in TENANT - Integrated messaging runtime - configuration and optimization

#### Binary parameters (XSLT)

receiverDetermination (receiver, possibly combined determination) at the partner of the scenario; interfaceDetermination_<receiver> per receiver — the suffix is the value SAP_ReceiverAlias (i.e. the receiver as it came out of the determination). When you use landscape stages, this is deliberately an alias and the real name of the system is looked up only later. When you do not use stages and the receiver has no alias mapping in PD, the same value is used directly as the business system name — verified in mapReceiverAliasToBusinessSystemName.groovy, where, when the mapping is missing, getAlternativePartnerId(...) ?: Pid is returned (fallback to the original value). In that case the suffix is precisely the business system name. Upload via the PD UI (content type xsl, it encodes UTF-8 itself) or the REST API (base64). Management of PD: REST API (OAuth + XSRF; objects StringParameters, BinaryParameters, AlternativePartners, AuthorizedUsers) or the PD UI.

[FULLY DECOUPLED PIPELINE ONLY] The separate binary parameter interfaceDetermination_<receiver> (an XSLT for determining the interface) belongs to the separate Step05. In the integrated runtime the interfaces are returned directly from the combined receiverDetermination XSLT, so you do not need this parameter. (The string variant interfaceDetermination_<receiver> for the p2p bypass works in the integrated one too — see 10.)

### 9 Generic XI and IDoc flows (Step01/Step08)

XI inbound: the single entry point of the XI proxy scenarios. Headers from the XI adapter: SAP_Sender = sapsenderservice, SAP_SenderInterface / SAP_MessageType = sapinterfacename, SAP_ApplicationID = SapMessageIdEx. QoS "Handled by Integration Flow" → async and sync. Async: idempotent process call with the Message ID SapMessageIdEx → duplicate = DuplicateDiscarded. Dynamic headers from the XI DynamicConfiguration → dc_SapDynamicConfiguration_<name>.

IDoc inbound: headers from EDI_DC40: SAP_Sender=SNDPRN, testMode (TEST), SAP_SenderInterface = MESTYP.IDOCTYP[.CIMTYP]. Bulk: IDoc numbers into custom header properties (without leading zeros), split by the IDoc Splitter by default, KeepBulk=true switches it off. Dedup: uniqueID = SNDPRN+DOCNUM.

XI/IDoc outbound (Step08): the single output point (/generic/step08/xi or /generic/step08/idoc). A three-layer precedence of configuration: the externalized parameters of the flow → overridden by Partner Directory (connectivity at the partner = business system) → overridden by the message headers from the scenario-specific flow. A rule of thumb: global Step08 → configure via PD/headers; Step08 per receiver → externalized parameters or PD. → Step08 XI, Step08 IDoc

Deduplication on the outbound — how it works. So that a message is not delivered to the receiver twice (typically on a retry after an outage), it carries the header UniqueID. It is set by Step06 (the step "Set unique identifier") — by default to the MPL ID of the message (SAP_MessageProcessingLogID), which is stable across all retries of the same message. The UniqueID travels as a header to Step07, where the template has the Idempotent Process Call (before "Send to Receiver"): it checks whether a message with this ID has already been delivered and, if so, discards it as a duplicate. Only after this check does Step07 hand the message over for the physical sending (for an XI/IDoc receiver via the generic Step08 — that itself has no idempotent call any more).

Override with a functional ID (per scenario). The default MPL ID can be overridden with a functional ID (order/invoice number) — then the deduplication is business-meaningful: two technically different deliveries of the same document are recognized as a duplicate. This is done in the scenario Step07 (not in PD, there is no generic parameter for it), before the Idempotent Process Call: a Content Modifier overrides the header UniqueID with a value from the payload, e.g.:

```
Content Modifier „Set unique ID"  (ped Idempotent Process Call)
  Header  UniqueID = ${xpath.//Invoice/DocumentNumber}
```

The Idempotent Process Call then deduplicates by the invoice number.

Deduplication in the One-to-Many templates. When one incoming message creates N outgoing ones (Iterating Splitter), they must not all carry the same UniqueID — otherwise the idempotent call would deliver the first one and discard the rest as duplicates. The template therefore composes for every partial message its own dedup ID = base + split order:

```
${header.UniqueID}_${CamelSplitIndex}        (One-to-Many s jedním typem)
${header.UniqueID}_Branch<n>_${CamelSplitIndex}   (více typ zpráv)
```

CamelSplitIndex is the index of the iteration (0, 1, 2…), so the partial messages are distinguished from each other and all pass — and at the same time they are all tied to the base UniqueID of the original message. Because the partial ID is composed of that base UniqueID, overriding the base with a functional value does not break the dedup of the partial messages — only the prefix changes, the suffix _${CamelSplitIndex} is added anyway.

Example — order PO-5000 with 3 items → split into 3 messages:

| | Base UniqueID | Partial dedup ID |
|---|---|---|
| without override | AGhx7… (MPL ID) | AGhx7…_0, AGhx7…_1, AGhx7…_2 |
| with override to the order number | PO-5000 | PO-5000_0, PO-5000_1, PO-5000_2 |

In both cases there are three different partial IDs → all three are delivered; when the whole PO-5000 is sent again, the same three IDs arise → the idempotent call recognizes them as duplicates. So _${CamelSplitIndex} solves "so that the splits are not regarded as duplicates of each other", the base UniqueID solves "so that a re-send of the whole message is recognized as a duplicate".

What to watch out for:

- The functional ID must be unique per document and stable across all sendings.
- Without an override the MPL ID remains — it catches only technical duplicates from the JMS retry, not a business re-send of the same document.

### 10 Bypass options (speeding up the runtime)

1. Point-to-Point (p2p): 1 receiver + 1 interface → no XSLT; string receiverDetermination = receiver, string interfaceDetermination_<receiver> = endpoint. It skips both Step04 and Step05, straight to PIPQ04. It works in the sync runtime too.
2. Combined determination in one XSLT: receiverDetermination returns receivers with nested Interfaces → Step05 is skipped. In the integrated runtime the only supported mode (see 4).
3. Bypass interface determination - [FULLY DECOUPLED PIPELINE ONLY]: a subset of the combined one — a recipient list without the interface split.
4. skipRcvDet — [FULLY DECOUPLED PIPELINE ONLY]: string receiverDetermination = receiver, XSLT only for the interfaces. Not supported in integrated/sync — replaced by the combined XSLT.

String vs. binary receiverDetermination. Verified in readBypassOptionFromPD.groovy: as a string, receiverDetermination is read only in the bypass shortcuts (p2p, and skipRcvDet in the decoupled pipeline) — then its value is directly the name of the receiver. In the standard determination it is always a binary parameter with XSLT. So for the integrated runtime: string receiverDetermination = exclusively p2p.

### 11 Special cases

#### Sender wildcard

Goal: not to create a separate scenario for every minor variation of the sender. Two situations:

- (a) Different sender systems, the same interface and the same configuration. E.g. three plants send the same PurchaseOrder_In and you want to serve them with one scenario. Solution: several AlternativePartners (one per sender system) pointing to the same PartnerID. Three inputs → one scenario.
- (b) The same system, different interfaces. Here an alternative partner does not help: the key of an AlternativePartner is the combination Agency + Scheme, and it would repeat for the same system (a collision — PD would reject the second record as a duplicate key). Solution: do not use the PD lookup at all and set the header partnerID directly in the scenario-specific inbound flow. The existence of the header partnerID switches off the PD search, so the generic step skips the determination and takes the supplied ID.

For the generic XI/IDoc inbounds, situation (b) is simpler: there the first layout of the AlternativePartner uses Scheme = the name of the interface (not the constant SenderInterface), so the keys of different interfaces do not repeat and no collision arises — no special iflow is needed.

#### Service interface with multiple operations

When: one service interface carries multiple operations (e.g. Create, Update, Cancel), and you need to route or map by operation. Prerequisite: 1:1 between operation and message type. How: in a Content Modifier you set the properties SAP-MT2OP-<Operation> = message type and messageTypeFromXMLRoot (the XPath /*/local-name() reads the name of the XML root); the script mapMessageTypeToOperation derives from them the header SAP_SenderOperation, which you then use in routing conditions (also in the XSLT determination, note: in XSLT only via a custom header dc_, e.g. dc_operation).

#### Sender/Receiver party

When: the scenario works with B2B partners, where the system alone is not enough and the party is needed too — typically EDI/B2B exchanges, where one system represents multiple business partners. How: simplified support by the format Party.System in the headers SAP_Sender / SAP_Receiver (XI inbound composes it automatically from SapSenderParty + SapSenderService). The pipeline derives SAP_SenderParty / SAP_ReceiverParty from it and writes both the party and the system into the custom header properties — so in the monitor you find the message by the business partner, not just by the technical system.

### 12 Monitoring and error handling

Standard retry handling: maxJMSRetries from PD (default 5) travels by header through all steps. On an error: below the limit → AutomaticRetry + Error end (JMS retry); above the limit → MaxRetriesExceeded, the queue information into MPL (script attachAdditionallnformation) and the message into the DLQ <inbound queue>_DLQ, the end by default Escalated (DLQ_EndEvent). In the steps with a splitter (Step04/05) the limit is checked at the start of the flow before the split (the condition > instead of =) — otherwise reprocessing from the DLQ would create duplicates. testMode=true → the retry is skipped (ErrorInTestMode).

Escalated, Failed, Completed — three ways of ending. This trio governs what happens to the message and how it looks in the monitor:

- Failed — JMS takes it as a failure and delivers the message again (retry).
- Completed — success, green in the monitor.
- Escalated — the message is fully processed (no further retry), but clearly marked in the monitor as "ended with a handled problem".

That is why, when the retries run out and the message goes to the DLQ, the flow ends Escalated: if it ended Failed, JMS would keep trying again endlessly — but we gave up and put it aside into the DLQ; if Completed, the problem would disappear in the monitor as a success. Escalated is the middle: done, do not retry, but visible. The contract of custom error handling uses the same trio — it returns customXError_EndEvent = Failed (retry) / Escalated (DLQ, give up) / Completed (silently discard).

Custom exception handling: CustomXError_Enabled=true + receiver CustomErrorFlow. The generic step passes the error headers + the original payload; the custom flow returns customXError_Status (→ custom status in MPL) and customXError_EndEvent (see above). The template Custom Error Handling implements the equivalent of the standard behaviour (router "Max retries exceeded?" → DLQ with Escalated, or retry with Failed).

Tracing: the correlation ID links the logs of all steps of one message; filter by Sender / Application Message Type / Custom Status (MaxRetriesExceeded, RetryViaParentFlow, DuplicateDiscarded, ReceiverNotFound*, InterfaceNotFoundError, SyncCallFailed…). Custom header properties = search by payload data: a property with the prefix customHeader_ + the script addCustomHeaderProperties (Base64 JSON withstands special characters). It works in the sync runtime too.

### 13 Test mode

Test mode is a flag that marks a message as a test one. It is not an IDoc-only matter — it is the header testMode, which is propagated through the whole pipeline and changes its behaviour (mainly on errors) as well as where the message is delivered. SAP introduced it primarily for regression and migration testing. The value is the string true (the pipeline tests ${header.testMode} = 'true').

How it is set:

- IDoc: automatically — from the field TEST in the EDIDC control record (testMode = //*:TEST != ''), set by the generic IDoc inbound.
- XI / others: there is no automatic source (XI has no TEST field); you set the header testMode yourself in the scenario-specific inbound flow (where you write your own inbound — HTTP/SOAP, sync). You add a Content Modifier (next to the setting of SAP_Sender / SAP_SenderInterface) and the header:

```
Header  testMode = true
```

It must be set before the message enters the pipeline (before the JMS write to TENANTX01, or before the Request-Reply to /TENANT/XS01 for sync); further on it is propagated by itself (it is in the AllowedHeadersList of all steps).

So that it is not hard-wired as "always true", the flag is usually derived from something that distinguishes test traffic:

- a dedicated test endpoint/flow — a test variant of the inbound flow that hardcodes testMode = true;
- from a flag from the caller — e.g. the HTTP header X-Test-Mode: true → testMode = ${header.X-Test-Mode};
- by sender — a router that sets testMode = true for a test sender system.

Beware with a pure XI proxy: the ABAP proxy sender goes through the generic Step01 XI, where you write no scenario-specific inbound flow — so there you do not inject testMode per message by default. You set the header only where you have your own inbound flow (HTTP/SOAP, sync). For proxy scenarios, testing is rather solved by a test system/client.

What it does: testMode = true → on errors the retry is skipped (status ErrorInTestMode, Escalation End instead of recycling the message), and if the scenario routes by testMode, the message goes to a mock/test receiver.

Propagation: the header testMode travels through all steps and is on the whitelist AllowedHeadersList in Step08 (XI and IDoc), so it survives up to the outbound.

What it does across the pipeline (verified in the scripts):

1. It changes the error semantics of every step. On an error and testMode='true' the step sets the custom status ErrorInTestMode and ends via the Escalation End (not Failed). A test message is thus not recycled (no JMS retry) and ends neither in a loop nor in the DLQ. Verified in Step02, Step05, Step06 and IDoc Step01.
2. It does not pollute the DLQ log. attachAdditionallnformation.groovy skips, with testMode='true', the record about the move to the DLQ (a test message does not go to the DLQ).
3. Optionally it skips deduplication. A separate test switch CustomXTest_SkipDuplicateCheck (default false) — so that you can send the same test message repeatedly without the idempotent call discarding it as a duplicate.
4. Mock receivers. The pipeline only propagates the flag — in the scenario-specific Step07 you branch on testMode and send to a mock/stub instead of the real backend (the point: not to touch the target systems during a test). The pipeline itself does not mock, it gives you the flag for it.
5. Testing tools. Regression/migration tools (Int4, Figaf) use testMode for automated tests.

Why it is pipeline-wide, not an IDoc detail: test mode changes the error behaviour of the whole pipeline — a test message ends cleanly and visibly (ErrorInTestMode / Escalated, see 12) instead of getting stuck in retry or falling into the DLQ. That is why it is accounted for in all steps, not just for IDocs.

### 14 Landscape stages

Goal: the same PD configuration across DEV/QA/PRD. Principle: the real name of the system is mapped to an alias on input, the pipeline works with aliases (in XSLT and in the parameters too!), and on output the alias is mapped back to the real name of the target stage. The headers SAP_Sender / SAP_Receiver in the monitor show the real names.

Tenant → stage mapping: partner SAP_Integration_Suite_Landscape, string parameter Id = the name of the tenant (env TENANT_NAME), the value = stage IDs separated by a comma (e.g. DEV,QA,PRD). One stage per tenant = the stage is unambiguous from the tenant. Without a record → default PRD.

Multiple virtual stages per tenant (e.g. a five-stage landscape of systems on three tenants — project/maintenance branches): the stage is determined successively from: (1) the header tenantStage from the scenario-specific flow, (2) authorized user → partner Landscape_Stage~<stage>, (3) the string parameter landscapeStage at the system, (4) authorized user → system + its landscapeStage, (5) implicitly from the unambiguous system→alias mapping.

System ↔ alias mapping: partner = alias; per stage an AlternativePartner (Agency=stage, Scheme=BusinessSystemName or LogicalSystemName, Id=the real name).

#### Stage rules — routing between stages (since 1.0.4)

Sometimes "send to the receiver in the same stage as the message arrived on" is not enough. Stage rules allow redirecting the target stage or duplicating the message into multiple stages. They are defined by a string parameter whose value starts with > and is a comma-separated list of target stages:

```
stageRule_<zdrojová stage>[~<receiver alias>]  =  ><cíl1>,<cíl2>,...
```

- <source stage> = the stage on which the message arrived;
- the value after > = the target stage / stages;
- ~<receiver alias> (optional) = the rule only for a particular receiver.

You can create the parameter either at the receiver alias (applies globally for the given receiver), or at the scenario (partnerID — applies only to that scenario and overrides the global one).

Example 1 — redirection. On the tenant DEV you want a particular scenario to send to the QA stage of the receiver ERP (say that ERP has no DEV instance of its own). At the scenario you create:

```
stageRule_DEV~ERP  =  >QA
```

A message that arrives on the stage DEV is delivered for the receiver ERP to ERP@QA.

Example 2 — duplication into multiple stages (a general principle; not used in our landscape). If the receiver existed in two different stages and you wanted to deliver one message to both:

```
stageRule_<stage>~<receiver>  =  >QA,PROD
```

The message is duplicated and delivered to the receiver in QA and in PROD. (Duplication works only async/Step04; sync can do exclusively 1→1.)

Evaluation priority (from the script mapSenderStageToReceiverStage.groovy), from the strongest:

1. scenario (partnerID) + a specific receiver: stageRule_<stage>~<receiver>
2. scenario for all receivers: stageRule_<stage>
3. receiver alias (globally): stageRule_<stage>[~<receiver>]
4. natural path — the same stage, if the receiver exists in it
5. tenant fallback stageRule_<tenant>… — only when the stage is not unambiguous from the tenant (virtual stages)

Our landscape: 4 tenants (DEV/QA/PreProd/PROD), 3 SAP systems → PreProd is mapped to the stage QA. One tenant = one stage, no virtual stages. PD is per tenant, the content is identical → the simplest is to create it via the PD UI in each tenant.

What we actually use from this: we have only 3 separate backends (DEV/QA/PROD) and PreProd shares the QA backend, so duplication makes no sense (there is nowhere to duplicate to — to the same backend twice) and the tenant fallback (point 5) does not apply (single-stage tenants). In practice we therefore run the natural path (point 4) and possibly the redirection (Example 1) for a system that has no instance in the given stage.

### 15 Custom exits, extension points and configuration without PD

Extensions without touching the generic flows (after modifying them you would lose the updates):

- custom exception handling (12),
- custom pre/post-processing exit (see below),
- custom receiver determination (CustomXRDEndpoint; contract = Receivers/Interfaces XML; it works in the integrated one too — the custom flow returns receivers and interfaces at once). The separate custom interface determination (CustomXIDEndpoint) and reuse of XRD (ReuseXRDEndpoint) are [FULLY DECOUPLED PIPELINE ONLY] — in the integrated one you do not need them (you put the logic into the custom RD flow / the combined XSLT). Template variants: an empty skeleton, via Exchange Properties, via Extended Receiver Determination — the last one, unlike ReuseXRD, is usable in the integrated runtime as well,
- DLQ_EndEvent, JMS queue prefix (multiple parallel pipelines), externalized adapter parameters, custom header properties, dc* headers for header-based routing.

#### Custom pre-processing exit — how and in what order

The pre-processing exit allows inserting your own logic into a generic step without touching it. One exit flow serves all steps — the Custom Exit template has branches for the steps 01–07 and routes by the header customXPipelineStep.

Two independent exits. In the flow there are two separate routers, which run one after the other, not as alternatives:

1. The global exit (router "Global exit?") — controlled by the externalized parameter of the flow CustomXPreGlobal_Enabled / CustomXPreGlobal_Endpoint. When switched on, it is called for all messages of that flow.
2. The scenario exit (router "Scenario-specific exit?") — controlled by CustomXPreEnabled / CustomXPreEndpoint (from the header or PD), called only for the given scenario and step.

The global and the scenario exit do not override each other — both run. When the global exit is switched on and you also have a scenario exit, the scenario one does not switch off the global one; both are called, in the order: first the global, then the scenario one. The global exit can be switched off only via its flow parameter (CustomXPreGlobal_Enabled), not from PD nor by a header.

What the value of CustomXPreEnabled is: a |-separated list of steps in which the scenario exit is to run (e.g. 01|02|06). Verified in readCustomXFromPD.groovy: the script compares the current step (customXPipelineStep — a two-digit number derived from the camelId of the generic step, if it is not in the header) against this list and is activated only on a match. (In the integrated runtime the determination is merged into one step — so mainly the input/runtime step and the outbound are relevant, not the separate 04/05.)

Priority — applies only to the SCENARIO exit (from readCustomXFromPD.groovy):

1. message header CustomXPreEnabled / CustomXPreEndpoint (supplied by the scenario-specific flow) — overrides PD,
2. PD parameter at the scenario (CustomXPreEnabled / CustomXPreEndpoint).

I.e. header > PD of the scenario. The global exit (CustomXPreGlobal_*) does not belong to this priority — it is a separate flow-level switch that runs independently and additionally (see "both run" above), not a default value that PD/the header would override.

#### Custom scenario configuration without PD

Instead of creating records in the Partner Directory, the configuration of the scenario can be passed by headers directly from the scenario-specific inbound flow. The header partnerID is mandatory (any value — its existence switches off the PD lookup); for content-based routing customXRDEndpoint; for p2p partnerID + SAP_OutboundProcessingEndpoint.

When and why to use it: when for a simple or isolated scenario you do not want to create PD records at all — the whole configuration is in the inbound flow, so the scenario is self-contained and portable (and easier to test). Example: a one-off p2p scenario — in the Content Modifier of the inbound flow you set partnerID = AdHoc_Export and SAP_OutboundProcessingEndpoint = /scenario/adhoc/out, and the pipeline skips the determination entirely, without you creating anything in PD.

Limitations: it does not work for the generic XI/IDoc inbounds, tenant staging, receiver-specific queues or ReuseXRD — there you cannot avoid configuration in PD.
