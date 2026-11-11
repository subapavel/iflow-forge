# po-xray Message Monitoring Implementation Plan

> **For agentic workers:** this repository's own roles execute the plan (`docs/LOCAL-WORKFLOW.md`,
> `AGENTS.md`): the architect writes a packet per task from this plan, the test-author writes and
> observes the red tests, the implementer makes them green, the verifier runs the checks, the
> reviewer judges. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** read-only SAP PO message monitoring in po-xray: search (standard and user-defined), message
detail, audit log, payload and lookups of the PO service AdapterMessageMonitoring, from CLI and MCP.

**Architecture:** a hand-written SOAP 1.1 client over `java.net.http` and the JDK DOM parser in a new
package `io.poxray.monitoring` of `po-xray-core`, reusing `PoSystem` and the HTTP setup of
`PoClient`; records as the parsed model with the raw response kept; a `messages` command group in
`po-xray-cli` and `message_*` tools in `po-xray-mcp` on top.

**Tech Stack:** Java 25, Maven, JDK `java.net.http`, JDK DOM/`javax.xml`, Quarkus + Aesh (CLI),
Quarkus MCP server (existing `@Tool` pattern), Jackson via `io.poxray.json.Json`, JUnit 5 + AssertJ,
JDK `com.sun.net.httpserver` as the test server.

**Spec:** `docs/superpowers/specs/2026-11-11-message-monitoring-design.md` (sections cited as §n).

## Global Constraints

- Read operations only: exactly the 21 of §4. `resendMessages`, `cancelMessages`, `failEoioMessage`
  never appear in `src/main` — not as a method, not as a string.
- No new dependency (§6: "no SOAP or JAX-WS dependency").
- No customer data in the repository: no host, system name, user, message ID, interface name or
  captured response of a customer in `src/`, tests, findings or ledgers; fixtures are synthetic with
  invented names. The live WSDL is not committed.
- Endpoint path `/AdapterMessageMonitoring/basic?style=document`, SOAP 1.1, `SOAPAction: ""`,
  preemptive Basic auth, HTTP/1.1, connect timeout 30 s, request timeout 2 min, no redirects.
- Operation elements in `urn:AdapterMessageMonitoringVi`; types in
  `urn:com.sap.aii.mdt.server.adapterframework.ws` and `urn:com.sap.aii.mdt.api.data`, all
  qualified; MON-01's finding fixes the element-by-element mapping and overrides this line if it
  differs.
- Nothing written to disk without `--save` / `saveDir`; MCP never spills to a temp file (§7).
- Limits: CLI `--limit` default 200; MCP `limit` default 50; MCP result cap 80,000 characters; MCP
  payload `maxChars` default 20,000 (§7).
- Default system order: `--system`/`system` → env `POXRAY_SYSTEM` → `system=` in
  `~/.po-xray/monitoring.properties` → error naming all three (§5).
- Agents call SOAP operations only on the QA system, and only read operations (§9); on PROD and DEV
  never. Anonymous WSDL reads are not operation calls (§9).
- Context7 before library-dependent code (Quarkus MCP, Aesh); `javap` on the resolved jar if unclear.

## Review Focus

1. A message ID that maps to several keys (sender and receiver side) — the user expects a list to
   choose from, never a silently picked key → test in MON-03.
2. A PO answer that is a logon HTML page with HTTP 200 instead of a SOAP envelope — expected
   "authentication failed", not an XML parse error → test in MON-02.
3. A payload in a non-UTF-8 charset or a binary attachment — expected text in the declared charset,
   or base64, never mojibake or a crash → test in MON-03.
4. A `--save` directory inside the iflow-forge checkout outside `local/` and `testdata/` — expected a
   refusal before anything is written → test in MON-05.
5. An MCP search whose 50 messages exceed 80,000 characters — expected a cut list with
   `truncated: true` and the dropped count, never a temp file → test in MON-06.

---

### Task MON-01: Probe the service contract on QA

Kind: docs (finding). Live read access to QA is authorised by the owner (§9).

**Files:**
- Create: `docs/findings/F-17.md` (template `docs/findings/TEMPLATE.md`)
- Modify: `docs/superpowers/specs/2026-11-11-message-monitoring-design.md` (§4 corrections, changelog line), only if the probe contradicts it

**Interfaces:**
- Produces: the namespace of every request element and response element used by MON-02..04; the
  child order of `AdapterFilter`, `MessageInterface`, `MessageParty`, `BusinessAttribute`, `Locale`;
  the fault element names; whether QA and PROD WSDL list the same 24 operations.

- [ ] **Step 1:** fetch the three WSDL documents of QA anonymously (service, `?wsdl=binding`,
  `?wsdl=interface`) into `local/<customer>/monitoring-probe/` with one-line `curl` commands; also
  the same three PROD documents, anonymous WSDL only (no authenticated call, no operation call; spec §9).
- [ ] **Step 2:** write F-17: namespace map per element, child order per type, fault structure,
  operation list diff QA vs PROD. Neutral wording, no host or system names.
- [ ] **Step 3:** documentation review (LOCAL-WORKFLOW, "workflow documentation" exception: Maven
  `NOT_RUN`, privacy grep of the staged files for host, user and system names).
- [ ] **Step 4:** commit `docs(monitoring): record the AdapterMessageMonitoring contract (F-17)`.

### Task MON-02: SOAP transport, error mapping and `getMessageList`

**Files:**
- Create: `po-xray-core/src/main/java/io/poxray/po/PoHttp.java`
- Modify: `po-xray-core/src/main/java/io/poxray/po/PoClient.java:44-73` (use `PoHttp`)
- Create in `po-xray-core/src/main/java/io/poxray/monitoring/`: `MonitoringClient.java`,
  `MonitoringException.java`, `Soap.java`, `MessageFilter.java`, `Message.java`, `Party.java`,
  `MessageInterface.java`, `BusinessAttribute.java`, `SearchResult.java`
- Test: `po-xray-core/src/test/java/io/poxray/monitoring/MonitoringClientSearchTest.java`,
  `.../monitoring/FakePo.java` (test server helper), `.../po/PoHttpTest.java`

**Interfaces:**
- Consumes: `PoSystem` (unchanged), F-17.
- Produces:
  - `PoHttp.client(PoSystem) : HttpClient` (moved from `PoClient.httpClient`, same behaviour and
    messages); `PoHttp.authorization(PoSystem) : String` (`"Basic " + base64(user:password)`).
  - `public final class MonitoringClient` — `MonitoringClient(PoSystem)`; rejects a `tpz=` system with
    `MonitoringException("System '<name>' is a directory of TPZ exports ... monitoring needs an online system")`;
    `PoSystem system()`; `SearchResult getMessageList(MessageFilter filter, int maxMessages)`.
  - `MonitoringException extends PoException`.
  - `MessageFilter` — immutable, `MessageFilter.builder()...build()`, one builder method per
    `AdapterFilter` field, serialised in exactly this order (unset optional fields omitted, the
    eight required fields always sent, see below):
    applicationComponent, archive, connectionName, correlationID, dateType, direction,
    errorCategory, errorCode, fromTime, interface, messageFormat, messageIDs, messageType, nodeId,
    onlyFaultyMessages, persistUntil, protocol, qualityOfService, receiverInterface, receiverName,
    receiverParty, referenceIDs, retries, retryInterval, scheduleTime, senderInterface, senderName,
    senderParty, sequenceID, serializationContext, serviceDefinition, softwareComponent, status,
    statuses, timesFailed, toTime, transport, validUntil, wasEdited, scenarioIDs, rootID,
    returnLogLocations, onlyLogLocationsWithPayload (order confirmed by F-17). Optional fields that
    are unset are omitted. The eight fields the WSDL declares required (F-17) are always sent, with
    these defaults unless set: archive=false, dateType=0, nodeId=0, onlyFaultyMessages=false,
    retries=0, retryInterval=0, timesFailed=0, wasEdited=false *(architect's call; MON-07 checks on
    QA that these defaults do not narrow a search, and a contradiction becomes a FIX row)*.
    Times are `OffsetDateTime`. Namespaces per element as F-17 Appendix A records (a child takes the
    namespace of the type that declares it); faults per F-17 Appendix D.
  - `record Party(String agency, String name, String schema)`;
    `record MessageInterface(String name, String namespace, String senderParty, String senderComponent, String receiverParty, String receiverComponent)`;
    `record BusinessAttribute(String name, String value)`.
  - `record Message(String messageId, String messageKey, String status, String errorCode, String errorCategory, Integer errorLabel, OffsetDateTime startTime, OffsetDateTime endTime, Party senderParty, String senderName, MessageInterface senderInterface, Party receiverParty, String receiverName, MessageInterface receiverInterface, MessageInterface interfaceRef, String direction, String qualityOfService, String connectionName, String sequenceId, String rootId, String parentId, Long size, List<BusinessAttribute> businessAttributes, List<String> logLocations, Map<String,String> otherFields)` —
    plus `Map<String,Boolean> flags` (each `BooleanAttribute` child — cancelable, editable,
    restartable, businessMessage, payloadPermissionWarning, wasEdited — by element name, from its
    `value` child) and `Long durationMillis` (from `duration/duration`; unit confirmed by MON-07)
    as the two components after `logLocations`.
    `otherFields` holds every other simple child of `AdapterFrameworkData` by element name, in
    document order, so nothing is dropped; an unknown complex child is kept as its XML string.
  - `record SearchResult(String system, List<Message> messages, Integer number, boolean moreAvailable, boolean displayPermissionWarning, OffsetDateTime date, String rawResponse)` — `moreAvailable` = the service's `warning`.

- [ ] **Step 1: write the failing tests** (`MonitoringClientSearchTest`, against `FakePo` on a
  random localhost port, `PoSystem` pointing at it, synthetic names such as `SenderA`, `urn:test`):
  - `sendsGetMessageListEnvelope` — request path `/AdapterMessageMonitoring/basic?style=document`,
    method POST, `Content-Type: text/xml; charset=UTF-8`, `SOAPAction: ""`, `Authorization` =
    Basic of the test user; body has `getMessageList` in `urn:AdapterMessageMonitoringVi`, filter
    children in the pinned order, `onlyFaultyMessages=true`, `fromTime`/`toTime` with offset,
    `maxMessages=200`; the eight required fields present with their defaults (`archive` false,
    `dateType` 0, …); unset optional fields absent.
  - `parsesMessagesAndKeepsRaw` — two messages parsed field by field; `rawResponse` equals the
    served body; an unknown element `<futureField>x</futureField>` appears in `otherFields`.
  - `warningMeansMoreAvailable`, `emptyListIsEmptyNotNull`.
  - `http401AndRedirectAndLogonPageAreAuthenticationFailures` — 401, 302, and 200 with a body
    containing `j_username` and `j_password` each throw `MonitoringException` whose message contains
    `authentication failed`.
  - `http403NamesTheMonitoringRole` — message contains `SAP_XI_API_DISPLAY_J2EE`.
  - `soapFaultCarriesCodeAndText` — `OperationFailedException` fault with `code` 42 → message
    contains `42` and the fault string.
  - `tpzSystemIsRejected`.
  - `PoHttpTest.authorizationIsPreemptiveBasic`; existing `PoClient` tests unchanged and green.
- [ ] **Step 2:** verifier runs `mvn -B -ntp -q -pl po-xray-core test -Dtest='MonitoringClientSearchTest,PoHttpTest'`; expected: compile failure / FAIL (red observed, log `target/mon-02-red.log`).
- [ ] **Step 3:** implementer: `PoHttp` (move, no behaviour change), `Soap.envelope(String operation, String bodyXml)`, `Soap.parse(HttpResponse<byte[]>) : Document` with the error mapping of `PoClient.send()` plus SOAP fault, `MonitoringClient.getMessageList`, the records. DOM with namespace awareness, secure processing on, external entities off.
- [ ] **Step 4:** verifier: the two tests green, then `mvn -B -ntp verify` at the root green (log `target/mon-02-verify.log`).
- [ ] **Step 5:** reviewer PASS; commit `feat(monitoring): add the read-only message search client`.

### Task MON-03: By-id lookups, audit log, payload and key resolution

**Files:**
- Modify: `po-xray-core/src/main/java/io/poxray/monitoring/MonitoringClient.java`
- Create: `LogEntry.java`, `LogResult.java`, `MessagePayload.java`, `PayloadPart.java`,
  `ValueResult.java`, `Mime.java`, `KeyResolver.java` in the same package
- Test: `MonitoringClientDetailTest.java`, `MimeTest.java`, `KeyResolverTest.java`

**Interfaces:**
- Consumes: MON-02 (`MonitoringClient`, `Soap`, `Message`, `SearchResult`).
- Produces (methods on `MonitoringClient`):
  - `SearchResult getMessagesByIds(List<String> messageIds, List<String> referenceIds, List<String> correlationIds, boolean archive)`
  - `SearchResult getMessagesByKeys(List<String> messageKeys, int maxMessages)`
  - `SearchResult getMessagesWithSuccessors(List<String> messageIds, boolean archive)`
  - `ValueResult getPredecessorMessageId(String messageId, String direction)`; `record ValueResult(String system, String value, String rawResponse)`
  - `LogResult getLogEntries(String messageKey, boolean archive, int maxResults, java.util.Locale locale, OffsetDateTime olderThan)`;
    `record LogEntry(OffsetDateTime timeStamp, String status, String textKey, List<String> params, String localizedText)`;
    `record LogResult(String system, String messageKey, List<LogEntry> entries, String rawResponse)`
  - `MessagePayload getMessageBytes(String messageKey, boolean archive)`,
    `MessagePayload getMessageBytes(String messageKey, int version, boolean archive)`,
    `MessagePayload getLoggedMessageBytes(String messageKey, String version, boolean archive)`;
    `record MessagePayload(String system, String messageKey, String source, byte[] bytes, List<PayloadPart> parts, String note, String rawResponse)` — `source` is `current`, `version:<n>` or `logged:<v>`;
    `record PayloadPart(int index, String contentId, String contentType, long size, String text, String base64)` — exactly one of `text`/`base64` set.
  - `Mime.parts(byte[] bytes) : List<PayloadPart>` — multipart split by the boundary from the
    leading headers; non-multipart → empty list (the caller sets `note = "not multipart"`).
  - `KeyResolver.resolve(MonitoringClient client, String idOrKey, String direction, boolean archive) : Resolution`;
    `sealed interface Resolution permits Resolution.Key, Resolution.Ambiguous, Resolution.NotFound`;
    `record Key(String messageKey)`, `record Ambiguous(List<Message> candidates)`, `record NotFound(String idOrKey)`.
    A value containing `\` is a key and is returned as is; otherwise it is an ID resolved by
    `getMessagesByIds(List.of(id), List.of(), List.of(), archive)`, filtered by `direction` when
    given (F-17 / MON-07 confirm the key format).

- [ ] **Step 1: failing tests:**
  - `MonitoringClientDetailTest`: one envelope test per method (element name, parameter names and
    order, `Locale` as language/country/variant); `logEntriesParsedInOrder`; `payloadBase64Decoded`
    (served base64 of known bytes → `bytes` equal); `invalidKeyFaultNamesTheKey` (`InvalidKeyException`
    → message contains the key).
  - `MimeTest`: `splitsHeaderAndPayloadParts` (two parts, content-ids, types, sizes);
    `declaredCharsetIsHonoured` (ISO-8859-2 part with `č` → `text` contains `č`);
    `binaryPartIsBase64` (`application/octet-stream` with bytes 0x00..0xFF → `base64` set, `text` null);
    `nonMultipartGivesNoParts`.
  - `KeyResolverTest` (fake client results): `keyIsPassedThrough`, `singleKeyResolved`,
    `twoKeysAreAmbiguousWithBothCandidates`, `directionNarrowsToOne`, `unknownIdIsNotFound`.
- [ ] **Step 2:** red observed (`target/mon-03-red.log`).
- [ ] **Step 3:** implement; `Mime` with the JDK only.
- [ ] **Step 4:** tests green, root verify green (`target/mon-03-verify.log`).
- [ ] **Step 5:** reviewer PASS; commit `feat(monitoring): add message detail, log and payload reads`.

### Task MON-04: User-defined search and lookups

**Files:**
- Modify: `MonitoringClient.java`
- Create: `AttributeMetadata.java`, `IntegrationFlow.java`, `StatusDetail.java`, `Lookup.java`
- Test: `MonitoringClientLookupTest.java`, `ReadOnlySurfaceTest.java`

**Interfaces:**
- Consumes: MON-02, MON-03.
- Produces: `record Lookup<T>(String system, List<T> values, String rawResponse)`;
  `record AttributeMetadata(String name, String description)`;
  `record IntegrationFlow(String id, String name, String description)`;
  `record StatusDetail(String errorLabelId, String description, String text)`; methods:
  `SearchResult getUserDefinedSearchMessages(MessageFilter filter, int maxMessages, List<BusinessAttribute> attributes, String operator)`,
  `Lookup<MessageInterface> getUserDefinedSearchFilters()`,
  `Lookup<AttributeMetadata> getUserDefinedSearchExtractors(String name, String namespace)`,
  `Lookup<BusinessAttribute> getUserDefinedSearchAttributes(String messageKey, boolean archive)`,
  `Lookup<MessageInterface> getInterfaces()`, `Lookup<Party> getParties()`,
  `Lookup<String> getServices()`, `Lookup<String> getConnections()`,
  `Lookup<IntegrationFlow> getIntegrationFlows(String language)`,
  `Lookup<String> getErrorCodes(int errorLabelId)`,
  `Lookup<StatusDetail> getStatusDetails(List<String> errorCodes, java.util.Locale locale)`,
  `Lookup<StatusDetail> getAllAvailableStatusDetails(java.util.Locale locale)`.

- [ ] **Step 1: failing tests:**
  - `MonitoringClientLookupTest`: envelope + parse test per method; `udsSearchSendsAttributesAndOperator`
    (two attributes in order, operator `AND`).
  - `ReadOnlySurfaceTest`: `exactlyTheTwentyOneReadOperations` — the set of SOAP operation names
    `MonitoringClient` can send (recorded by `FakePo` while each public method is called once) equals
    the 21 names of §4; `noWriteOperationInSources` — no file under
    `po-xray-core/src/main/java` contains `resendMessages`, `cancelMessages` or `failEoioMessage`;
    `noPublicMethodBeyondTheReadSet` — public methods of `MonitoringClient` are `system()` plus the
    21 (overloads of `getMessageBytes` counted per operation).
- [ ] **Step 2:** red observed (`target/mon-04-red.log`).
- [ ] **Step 3:** implement.
- [ ] **Step 4:** green, root verify green (`target/mon-04-verify.log`).
- [ ] **Step 5:** reviewer PASS; commit `feat(monitoring): add user-defined search and lookups`.

### Task MON-05: CLI `messages` command group, default system, saving

**Files:**
- Create in `po-xray-core/src/main/java/io/poxray/monitoring/`: `MonitoringSystems.java`, `SaveTarget.java`, `MessageArchive.java`
- Create in `po-xray-cli/src/main/java/io/poxray/cli/`: `MessagesCommand.java` (group) and one
  class per subcommand: `MessagesSearchCommand`, `MessagesUdsSearchCommand`, `MessagesGetCommand`,
  `MessagesSuccessorsCommand`, `MessagesPredecessorCommand`, `MessagesLogCommand`,
  `MessagesPayloadCommand`, `MessagesUdsAttributesCommand`, `MessagesUdsFiltersCommand`,
  `MessagesUdsExtractorsCommand`, `MessagesLookupCommand`; `TimeArg.java` (time parsing)
- Modify: `po-xray-cli/src/main/java/io/poxray/cli/PoXrayCommand.java:15-18` (register `MessagesCommand`), usage text
- Test: core `MonitoringSystemsTest`, `SaveTargetTest`, `MessageArchiveTest`; CLI `MessagesCommandTest`, `TimeArgTest`

**Interfaces:**
- Consumes: MON-02..04.
- Produces:
  - `MonitoringSystems.resolve(String explicit, Map<String,String> env, Path poxrayHome) : String` —
    order of Global Constraints; error message names `--system`, `POXRAY_SYSTEM` and
    `~/.po-xray/monitoring.properties`.
  - `SaveTarget.check(Path dir) : Path` — walks up to the first `.git` entry; inside a work tree
    accepts only `<root>/local/**` and `<root>/testdata/**`, else throws
    `MonitoringException("refusing to save customer data inside the repository outside local/ or testdata/: <dir>")`.
  - `MessageArchive.save(Path dir, String messageId, Object model, String rawResponse, byte[] payload) : Path` —
    writes `<dir>/<messageId>/response.xml`, `model.json` (via `Json.write`), `payload.bin` when
    given; returns the message directory; calls `SaveTarget.check` first.
  - `TimeArg.parse(String, Clock) : OffsetDateTime` — `2026-11-10T08:00` (local zone),
    ISO with offset, `-2h`, `-30m`, `-1d`.
  - CLI: `po-xray messages <sub>`; common options `-s/--system` (optional), `--json`,
    `-l/--limit` (200), `--archive`, `--save <dir>`; `search`/`uds-search`: `--interface`,
    `--interface-ns`, `--sender`, `--receiver`, `--status` (repeatable), `--faulty`, `--from`
    (default `-24h`), `--to` (default now); `uds-search` adds `--attr name=value` (repeatable),
    `--operator AND|OR` (AND); `log`/`payload`/`uds-attributes` take `<id-or-key>` plus `--key`,
    `--direction`; `payload`: `--version <int>`, `--logged <version>` (the WSDL parameter `version` of `getLoggedMessageBytes`), `--part <n>`; `lookup <kind>` with
    kind `interfaces|parties|services|connections|iflows|error-codes|status-details`. Table output
    shows local time with zone; ambiguity prints the candidates and exits 2.

- [ ] **Step 1: failing tests:**
  - `MonitoringSystemsTest`: explicit wins; env next; file next; none → message names all three.
  - `SaveTargetTest` (temp dirs with a `.git` dir): `outsideWorkTreeAccepted`, `localAccepted`,
    `testdataAccepted`, `repoRootRefused`, `srcRefused`, `dotDotEscapeRefused`
    (`<root>/local/../src` refused after normalisation).
  - `MessageArchiveTest`: `writesThreeFiles`; `refusedTargetWritesNothing`.
  - `TimeArgTest`: the four forms, fixed `Clock`.
  - `MessagesCommandTest` (CLI run against `FakePo` from core test-jar or a copy in CLI tests):
    `searchPrintsTableWithLocalTime`, `searchJsonIsTheModel`, `searchWithoutSystemUsesEnv`,
    `logWithAmbiguousIdListsCandidatesAndExits2`, `payloadPrintsSelectedPart`,
    `saveIntoSrcIsRefusedAndNothingWritten`, `noSaveWritesNothing` (temp cwd stays empty).
- [ ] **Step 2:** red observed (`target/mon-05-red.log`).
- [ ] **Step 3:** implement (Context7 for Aesh group commands and repeatable options first).
- [ ] **Step 4:** green, root verify green, CLI smoke `po-xray messages --help` (`target/mon-05-verify.log`).
- [ ] **Step 5:** reviewer PASS; commit `feat(cli): add the messages command group`.

### Task MON-06: MCP tools and native registration

**Files:**
- Create: `po-xray-mcp/src/main/java/io/poxray/mcp/MessageTools.java`, `.../mcp/McpBudget.java`
- Modify: `po-xray-mcp/src/main/java/io/poxray/mcp/Inventories.java` (a cached `MonitoringClient` per system), `po-xray-native/src/main/java/io/poxray/nativeimage/NativeReflection.java` (all monitoring records)
- Test: `po-xray-mcp/src/test/java/io/poxray/mcp/MessageToolsTest.java`, `McpBudgetTest.java`; `NativeReflectionTest` extended

**Interfaces:**
- Consumes: MON-02..05 (`MonitoringSystems`, `MessageArchive`, `KeyResolver`).
- Produces: 11 tools, all `readOnlyHint=true, openWorldHint=true` except that `saveDir` makes a
  call write — so tools with `saveDir` set `readOnlyHint=false` (`message_search`,
  `message_uds_search`, `message_get`, `message_log`, `message_payload`) following the
  `write_integration_documentation` precedent: `message_search`, `message_uds_search`,
  `message_get`, `message_successors`, `message_predecessor`, `message_log`, `message_payload`,
  `message_uds_attributes`, `message_uds_filters`, `message_uds_extractors`, `message_lookup(kind)`.
  Parameters mirror the CLI; `system` optional; `includeRaw` (false); `limit` (50); payload `part`,
  `offset` (0), `maxChars` (20000) with `totalChars`, `truncated` in the result. Descriptions tell the
  agent to call `message_lookup` / `message_uds_filters` first for valid filter values.
  `McpBudget.fit(SearchResult, int maxChars) : FittedSearch(List<Message> messages, boolean truncated, int dropped, String hint)`.

- [ ] **Step 1: failing tests:**
  - `MessageToolsTest`: `elevenToolsRegistered`; `readOnlyHintMatchesSaveDir` (tools without
    `saveDir` are read-only); `searchOmitsRawByDefault`, `includeRawAddsIt`;
    `oversizedSearchIsCutNotSpilled` (50 synthetic messages of ~2,000 characters each → result ≤
    80,000 characters, `truncated: true`, `dropped` > 0, and `<java.io.tmpdir>/po-xray` gains no file);
    `payloadPagesByChars` (offsets 0/20000/40000 of a 45,000-char part → `truncated` true/true/false on three pages);
    `ambiguousIdReturnsCandidates`; `noSaveDirWritesNothing`.
  - `McpBudgetTest`: fits all; cuts at last fitting message; single message larger than the cap →
    zero messages, `truncated`, hint.
  - `NativeReflectionTest`: every record in `io.poxray.monitoring` is registered.
- [ ] **Step 2:** red observed (`target/mon-06-red.log`).
- [ ] **Step 3:** implement (Context7 for the Quarkus MCP `@Tool` annotations first).
- [ ] **Step 4:** green, root verify green; native build only if the existing smoke runs it
  (record `NOT_RUN` with the reason otherwise) (`target/mon-06-verify.log`).
- [ ] **Step 5:** reviewer PASS; commit `feat(mcp): add the message monitoring tools`.

### Task MON-07: Live acceptance on QA

Kind: acceptance (finding + possible spec edit). QA only, read only.

**Files:**
- Create: `docs/findings/F-18.md`
- Modify: the spec §9 (settled points, changelog line); private evidence under `local/<customer>/monitoring-acceptance/`

- [ ] **Step 1:** verifier runs on QA with the built CLI: `lookup status-details`, `lookup interfaces`
  (count only), one `search --faulty --from -7d`, one `log` and one `payload` of a found message,
  `uds-filters`, one `uds-search` on an interface that has filters. Output stays in the private
  directory; the tracked log has commands with placeholders, exit codes and counts only.
- [ ] **Step 2:** F-18 records: accepted `status` values, `dateType` meaning, payload `version` and
  `logLocations` meaning, message key format, roles that were sufficient, anything that contradicted
  MON-01..06. A contradiction becomes a `FIX`/`MON` row with a red test, not a silent edit.
- [ ] **Step 3:** documentation review; commit `docs(monitoring): record the QA acceptance (F-18)`.
- [ ] **Step 4:** the owner may run one PROD search; agents do not.
