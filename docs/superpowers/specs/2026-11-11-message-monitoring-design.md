# po-xray message monitoring: design

Date: 2026-11-11. Status: **draft — review at hand-over** (new public CLI commands and MCP tools).
Owner decisions are quoted from the 2026-11-11 conversation; everything marked *(architect's call)*
is a default the owner has not decided and may overrule while reading.

Changelog: 2026-11-11 F-17 — §4 namespace of each data type corrected from the served WSDL; response
wrapper, array, flag and fault shapes added; fault child order corrected to args, code
(InvalidKeyException adds messageKey). 2026-11-11 review r1 — §8 `readOnlyHint` aligned with §6;
§9 states that anonymous WSDL reads are not operation calls.

## 1. Intent

The owner investigates failed SAP PO messages: finds them by interface and time window, reads their
error log and inspects the payload. Today this is done by hand in the PO monitor. po-xray gains a
read-only message monitoring client so that both the owner (from `po-xray-cli`) and an agent (through
`po-xray-mcp`, e.g. during a migration) can search messages, read logs and read payloads of a running
PO system.

Success: the owner can answer "which messages of interface X failed between T1 and T2, why, and
what did they contain" from the CLI, and an agent can do the same through MCP, without opening the
PO monitor. This includes searches by user-defined search attributes (e.g. OPM attributes on OTE
messages), which the standard filter cannot express.

## 2. Goals, non-goals, anti-goals

Goals
- All 21 read operations of the PO service **AdapterMessageMonitoring** (§4) are callable from the
  core library and reachable from both CLI and MCP.
- Same systems and credentials as the existing po-xray readers (§5); no new setup for the owner.
- Results are lossless: the raw SOAP response is kept next to the parsed model (CLAUDE.md, "Readers
  are lossless and honest").

Non-goals
- Other PO services (ChannelAdmin, `/mdt/*` servlets, BPM, alerting). The probe on 2026-11-11 found
  AdapterMessageMonitoring is the only message-search service; ChannelAdmin controls channels.
- ABAP Integration Engine messages (SXMB_MONI) on dual-stack systems.
- Monitoring of SAP Integration Suite tenants.

Anti-goals (a failure even if everything else works)
- **Any write operation.** Owner: "jen čtecích 21". `resendMessages`, `cancelMessages` and
  `failEoioMessage` are not implemented, not behind a flag, not reachable by any code path; a test
  asserts the client cannot emit them (§8).
- Customer data in the repository: no hostname, message ID, payload, interface name of a customer,
  or captured SOAP response in `src/` or tests. The WSDL fetched from the customer system is not
  committed either; §4 records what the builder needs from it.
- Payloads or logs written to disk without an explicit request (§7).

## 3. Users and surfaces

- Owner on the CLI: readable table by default, `--json` for the full model.
- Agent through MCP: JSON, size-bounded (§7).
- PROD is a normal system for this feature (owner: "PROD jako normální systém (jenom čteme, nic
  neměníme)"); no extra confirmation for PROD. Most searches are on PROD, some on QA, rarely DEV.

## 4. The service (from the WSDL, 2026-11-11)

Endpoint: `<url>/AdapterMessageMonitoring/basic?style=document`, SOAP 1.1, document/literal,
`soapAction=""`, Basic auth. Port type `AdapterMessageMonitoringVi`; operation elements in namespace
`urn:AdapterMessageMonitoringVi`; data types in `urn:com.sap.aii.mdt.server.adapterframework.ws`
(`AdapterFilter`, `AdapterFrameworkData`, `MessageSearchReturnValue`, `BusinessAttribute`,
`AttributeMetadata`, `IntegrationFlow`, `StatusDetail`, `Locale`, the two exceptions) and
`urn:com.sap.aii.mdt.api.data` (`MessageInterface`, `MessageParty`, `AuditLogEntryData`,
`BooleanAttribute`, `DurationAttribute`); string arrays in `urn:java/lang`. All schemas
`elementFormDefault="qualified"`, so a child element is in the namespace of the type that declares
it, not of its parent. The element-by-element mapping is F-17 (`docs/findings/F-17.md`); the builder
records it in a Java constant, not by committing the WSDL.

Shapes (F-17): every response is `<op>Response` with one child `Response`; an array is a wrapper of
repeated items (`String`, `AdapterFrameworkData`, …); the message flags (`cancelable`, `editable`,
`restartable`, `payloadPermissionWarning`, `businessMessage`, `wasEdited`) are `BooleanAttribute`
wrappers with a `value` child, `duration` a `DurationAttribute` with a `duration` child; a fault
detail is a per-operation element `<op>_com.sap.aii.mdt.server.adapterframework.ws.<Exception>` in
`urn:AdapterMessageMonitoringWsd/AdapterMessageMonitoringVi`.

| Group | Operation | Parameters → response |
|---|---|---|
| Search | `getMessageList` | `filter:AdapterFilter, maxMessages:int` → `MessageSearchReturnValue` |
| Search | `getUserDefinedSearchMessages` | `filter:AdapterFilter, maxMessages:int, attributes:BusinessAttribute[], operator:string` → `MessageSearchReturnValue` |
| By id | `getMessagesByIDs` | `messageIds[], referenceIds[], correlationIds[], archive` → `MessageSearchReturnValue` |
| By id | `getMessagesByKeys` | `filter:string[] (message keys), maxMessages` → `MessageSearchReturnValue` |
| By id | `getMessagesWithSuccessors` | `messageIds[], archive` → `MessageSearchReturnValue` |
| By id | `getPredecessorMessageId` | `messageId, direction` → `string` |
| Log | `getLogEntries` | `messageKey, archive, maxResults, locale, olderThan` → `AuditLogEntryData[]` |
| Payload | `getMessageBytesJavaLangStringBoolean` | `messageKey, archive` → `base64Binary` |
| Payload | `getMessageBytesJavaLangStringIntBoolean` | `messageKey, version:int, archive` → `base64Binary` |
| Payload | `getLoggedMessageBytes` | `messageKey, version:string, archive` → `base64Binary` |
| UDS | `getUserDefinedSearchFilters` | — → `MessageInterface[]` (interfaces that have UDS filters) |
| UDS | `getUserDefinedSearchExtractors` | `name, namespace` → `AttributeMetadata[]` |
| UDS | `getUserDefinedSearchAttributes` | `messageKey, archive` → `BusinessAttribute[]` |
| Lookup | `getInterfaces` | — → `MessageInterface[]` |
| Lookup | `getParties` | — → `MessageParty[]` |
| Lookup | `getServices` | — → `string[]` |
| Lookup | `getConnections` | — → `string[]` |
| Lookup | `getIntegrationFlows` | `language` → `IntegrationFlow[]` |
| Lookup | `getErrorCodes` | `errorLabelID:int` → `string[]` |
| Lookup | `getStatusDetails` | `errorCodes[], locale` → `StatusDetail[]` |
| Lookup | `getAllAvailableStatusDetails` | `locale` → `StatusDetail[]` |

`AdapterFilter` fields: applicationComponent, archive, connectionName, correlationID, dateType,
direction, errorCategory, errorCode, fromTime, toTime, interface, senderInterface,
receiverInterface (`MessageInterface`: name, namespace, senderParty, senderComponent, receiverParty,
receiverComponent), senderName, receiverName, senderParty, receiverParty (`MessageParty`: agency,
name, schema), messageFormat, messageIDs[], referenceIDs[], scenarioIDs[], rootID, messageType,
nodeId, onlyFaultyMessages, persistUntil, protocol, qualityOfService, retries, retryInterval,
scheduleTime, sequenceID, serializationContext, serviceDefinition, softwareComponent, status,
statuses[], timesFailed, transport, validUntil, wasEdited, returnLogLocations,
onlyLogLocationsWithPayload.

`AdapterFrameworkData` (one message) carries among others messageID, messageKey, status, errorCode,
errorCategory, errorLabel, startTime, endTime, sender/receiver party, component and interface,
qualityOfService, sequenceID, direction, connectionName, protocol, size, duration, rootID, parentID,
scenarioIdentifier, businessAttributes[], logLocations[], version, and the flags cancelable,
editable, restartable, payloadPermissionWarning. `MessageSearchReturnValue` adds `number`, `date`,
`warning` (more results than returned) and `displayPermissionWarning`.

Faults: `OperationFailedException` (args, code) and `InvalidKeyException` (args, code, messageKey).

## 5. Connection and credentials

Owner: "stejně jak to používá po-xray pro zkoumání artefaktů, stejný princip, vyhledávám pod tím
samým uživatelem". The client takes a `PoSystem` (`~/.po-xray/systems/<name>.properties`, env
overrides `POXRAY_<SYSTEM>_USER/_PASSWORD`, `POXRAY_USER/_PASSWORD`, `truststore`, `proxy`) and builds
its HTTP client the way `PoClient` does: HTTP/1.1, 30 s connect timeout, 2 min request timeout, no
redirects, preemptive Basic auth, truststore and proxy honoured *(architect's call: share the
builder with `PoClient` rather than copy it)*. Error mapping matches `PoClient.send()`: connection
failure, 401/3xx/logon page → authentication failed, 403 → names the roles needed for monitoring
(`SAP_XI_API_DISPLAY_J2EE` per SAP documentation; to be confirmed on the live system and recorded
as observed or not), SOAP fault → the fault code and text. A `tpz=` system is rejected with a clear
message: monitoring needs an online system.

Default system: the owner searches mostly on PROD and wants that as default, set in configuration.
The existing CLI has no default system (every `--system` is required). *(Architect's call)*: the
monitoring commands and tools take `--system` / `system` as optional and fall back first to the
environment variable `POXRAY_SYSTEM`, then to `system=<name>` in `~/.po-xray/monitoring.properties`;
if none is set, they fail with a message naming all three. Every result names the system it came
from. Other po-xray commands are not changed.

## 6. Shape of the code

- `po-xray-core`, new package `io.poxray.monitoring`: a SOAP client over `java.net.http` and the JDK
  XML parser — no SOAP or JAX-WS dependency *(architect's call: the service is small, document/literal,
  and the JDK covers it)*. Records for the model (`MessageFilter`, `Message`, `SearchResult`,
  `LogEntry`, `MessagePayload`, `BusinessAttribute`, …) in the style of the existing readers, built
  by static readers from the SOAP body. Every result carries the raw response XML next to the
  parsed model. Unknown elements in a response are kept and reported, not dropped.
- The client exposes one Java method per read operation (21). There is no generic "call any
  operation" method, so a write operation cannot be reached by name.
- Message ID or key *(architect's call)*: `log`, `payload` and `uds-attributes` accept either a
  message key or a message ID. An ID is resolved through `getMessagesByIDs`; if it yields exactly one
  key, that key is used; if several (sender and receiver side, several receivers), the command lists
  them (key, direction, receiver, status) and stops — the caller repeats with `--key` or
  `--direction`. No key is picked silently.
- Times: request times are sent as `xs:dateTime` with offset; the CLI accepts local time
  (`2026-11-10T08:00`) or ISO with offset and relative forms (`-2h`, `-1d`) *(architect's call)*, and
  displays local time with the zone.
- `po-xray-cli`: a command group `messages` with subcommands *(architect's call on names)*:
  `search` (standard filter), `uds-search` (user-defined search: `--attr name=value` repeatable,
  `--operator AND|OR`), `get` (by message ID, reference ID, correlation ID or key), `successors`,
  `predecessor`, `log`, `payload`, `uds-attributes`, `uds-filters`, `uds-extractors` and `lookup`
  (`interfaces|parties|services|connections|iflows|error-codes|status-details`). Common options:
  `--system`, `--json`, `--limit` (default 200), `--archive`.
- `po-xray-mcp`: tools with `readOnlyHint=true, openWorldHint=true` (a tool that takes `saveDir`
  is marked `readOnlyHint=false`, like `write_integration_documentation`, because it can write): `message_search`,
  `message_uds_search`, `message_get`, `message_successors`, `message_predecessor`, `message_log`,
  `message_payload`, `message_uds_attributes`, `message_uds_filters`, `message_uds_extractors`,
  `message_lookup` (kind parameter). Descriptions tell the agent to call `message_lookup` /
  `message_uds_filters` first when it does not know valid filter values.

## 7. Output size and storage

Owner: payloads and logs may be saved, "ale jenom v případě, že to bude vyžadovat scenář". So:

- Nothing is written to disk unless the caller asks. CLI: `--save <dir>`; MCP: a `saveDir`
  parameter. With it, the tool writes the raw SOAP response, the decoded payload bytes and the JSON
  model under `<dir>/<message-id>/`. The documented place is `local/<customer>/messages/` (gitignored);
  the tool refuses a `<dir>` inside the repository that is not under `local/` or `testdata/`
  *(architect's call)*.
- Saving is refused *(architect's call)* when `<dir>` lies inside a git work tree (found by walking
  up from `<dir>` to a `.git` entry) unless it is under `<work-tree-root>/local/` or
  `<work-tree-root>/testdata/`. Outside any work tree any directory is accepted.
- MCP: the existing tools spill results over 80,000 characters into a temp file (`ResultFile`). The
  monitoring tools must not do that, because a temp file is an unrequested save of customer data.
  *(Architect's calls:)* MCP results carry the parsed model only; the raw SOAP response is included
  only with `includeRaw=true` (the core model and `saveDir` always keep it, so nothing is lost). A
  search returns at most `limit` messages (default 50 in MCP, 200 in CLI) and reports the service's
  `warning` (more exist). If the serialised result still exceeds 80,000 characters, the message list
  is cut at the last message that fits and the result says `truncated: true`, how many were dropped,
  and that the agent should narrow the filter or lower `limit`. Never an error, never a file.
- Payload: `payload` uses `getMessageBytesJavaLangStringBoolean` (current version) by default;
  `--version <int>` switches to `getMessageBytesJavaLangStringIntBoolean`, `--logged <version>` to
  `getLoggedMessageBytes`. Paging applies to the text of one decoded part, in characters: `part`
  (default: the first part that is not the XI header; part 0 is listed but not paged), `offset`,
  `maxChars` (default 20,000 in MCP, unlimited in CLI), with `totalChars` and `truncated` in the
  result. A part that is not text is returned as base64 and paged in base64 characters.
- Payload decoding: the service returns the whole stored message (expected to be a MIME multipart
  with the XI header and payload parts). The model keeps the raw bytes and, when the content is
  multipart, lists the parts (content-id, content-type, size, body as text if it decodes as the
  declared or UTF-8 charset, otherwise base64). If it is not multipart, the raw content is returned
  with a note. Nothing is repaired.

## 8. Tests

- TDD as in CLAUDE.md. Fixtures are synthetic SOAP responses written by the test, with invented
  names (no customer names, IDs or hosts).
- A test HTTP server (JDK `com.sun.net.httpserver`) stands in for PO: asserts the request envelope
  (operation element, namespaces, filter fields, Basic auth header) and serves the synthetic reply;
  covers 401, 403, logon redirect, SOAP fault (`OperationFailedException`, `InvalidKeyException`),
  `warning=true`, empty results, multipart and non-multipart payloads, unknown response elements.
- A test asserts that no request the client can produce contains `resendMessages`,
  `cancelMessages` or `failEoioMessage`, and that the client class declares no such method.
- MCP: tool list contains the 11 tools, `readOnlyHint=true` except the tools that take `saveDir`
  (§6); payload paging; no file written without `saveDir`.
- Live acceptance: owner-led, local only, against QA first, then PROD: one search by interface and
  time window, one log, one payload, one user-defined search. Evidence in PROGRESS without customer
  values (counts and exit codes only).

## 9. Live probe and open points for the builder

Owner, 2026-11-11: an agent may call the **read** operations on the **QA** system during development
under the owner's configured user ("Ano, na QA"). The plan therefore starts with a probe task on QA
that settles the points below and the exact namespaces, recorded as a finding in `docs/findings/`
without customer values; later tasks build on that finding. Agents call no SOAP operation on PROD
or DEV during development. Reading the anonymous WSDL documents is not an operation call: the
owner's first request on 2026-11-11 had the architect read the PROD WSDL, and MON-01 read the PROD
WSDL once more to compare it with QA *(architect's clarification, for review at hand-over)*.

Points the probe settles:

- Semantics of `dateType` (int) and the accepted `status` / `statuses` values: discover from
  `getAllAvailableStatusDetails` and a live call; until confirmed, the CLI passes the value through
  and documents it as unverified.
- Meaning of `version` in the two payload operations and of `logLocations`; the live probe decides,
  the finding is recorded in `docs/findings/`.
- Whether `getMessageList` without `fromTime`/`toTime` is accepted; *(architect's call)* the CLI
  requires a time window for `search` and `uds-search` (default: last 24 h) to keep PROD queries
  bounded.

## 10. Native builds

*(Architect's call)*: the feature works in the native-image CLI and MCP builds like the rest of
po-xray; new model records are registered for reflection in `po-xray-native`, and the native smoke
covers one monitoring command against the test server where the existing smoke allows it.

## 11. Repository bookkeeping

This feature is outside the five parts in `CLAUDE.md` ("What this is"). On approval it gets a
decision in `docs/DECISIONS.md` (D-059, marked draft — review at hand-over) and BACKLOG rows
`MON-*`; the split into tasks is the plan's job (`docs/superpowers/plans/`).
