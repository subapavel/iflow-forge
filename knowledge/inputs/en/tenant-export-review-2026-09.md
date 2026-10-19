<!-- Translated from "Pipeline Concept — stav exportů z 18. 9. 2026" (Czech original, generation c, ChatGPT analysis of a tenant export). Draft, not a source of facts. -->

# Pipeline Concept — state of the exports as of 18 September 2026

Analysis of the four TENANT packages in this folder and of the summary `Pipeline_Concept_souhrn_pro_ChatGPT.md` supplied from Claude Code. The summary serves as historical context, not as an authoritative description of the new export. The conclusions below are based on the manifests, changelogs, BPMN definitions and source scripts of the exported artifacts. This is not a verification of the latest version available in SAP Discover, nor a runtime test on the tenant.

## 1. Readability and scope

**All attached deployable artifacts are readable.** The export is not a plain folder of named ZIPs: `resources.cnt` contains Base64 JSON, `contentmetadata.md` Base64 text, and `<id>_content` is either the ZIP of the artifact or accompanying content. The decoded metadata made it possible to pair names, types and versions with the content.

| Package | iFlows | Other deployable artifacts |
|---|---:|---|
| Generic Inbound | 2 | — |
| Generic Pipelines | 5 | — |
| Generic Outbound | 3 | — |
| Templates | 20 | Script Collection + Value Mapping |
| **Total** | **30** | **2** |

Templates contains 19 templates and one Upload Helper. The historical summary stated 18 templates.

Readability verification:

- 32 deployable artifacts were unpacked; together with the changelogs and custom tags this produced 376 files.
- 30 `.iflw` definitions, 30 `.prop` and 30 `.propdef` files and 32 manifests were loaded.
- The 177 available Groovy files represent 71 distinct contents by SHA-256; the Script Collection has 13 scripts.
- The XML parser successfully loaded all 101 files of the types `.iflw`, `.propdef`, `.project`, `.mmap`, `.xsd`, `.wsdl` and `.xml`. This demonstrates the syntactic readability of the XML, not validation of the SAP model or a successful deployment.
- The message mapping `.mmap`, the Value Mapping XML, six XSDs and one WSDL were also read. Fourteen binary `.crt` certificates can be loaded as X.509; their subject is SAP SE. The cryptographic validity of the package signatures was not verified.
- All local references to Groovy files have a corresponding file. Eight further uses of scripts point to the shared collection; the content of these scripts is present, but the technical ID of the collection does not match the references — see chapter 5.
- The eight URL items in the metadata are links, not missing ZIP artifacts. The content of the linked websites is not part of the export.

The unpacked copies are in four subfolders of `analyza`. The original exports were not modified. Machine-readable materials: `inventar.csv`, `manifest-inventory.csv`, `file-hashes.csv`, `script-references.csv`, `flow-summary.txt`, `flow-index.txt`, `script-index.txt`. The list of steps in the indexes preserves the XML order; it is not in itself the execution order. For that, the `sequenceFlow` links in the `.iflw` are decisive.

## 2. How the pipeline works in these packages

The core is a set of reusable generic flows. A specific scenario is determined by the message headers, the Partner Directory (PD) configuration, the routing mappings and the scenario flows called via ProcessDirect. JMS separates the asynchronous phases and their retry.

| Variant | Basic path |
|---|---|
| Fully decoupled async | Step01 → Q01 → Step02 (optionally Step03 conversion) → Q02 → Step04 receiver determination → Q03 → Step05 interface determination → Q04 → Step06 → scenario Step07 → possibly Step08 |
| Integrated Messaging Async | Step01 → X01 → Step02 Integrated Async (conversion, receivers and interfaces) → Q04 → Step06 → scenario Step07 → possibly Step08 |
| Integrated Messaging Sync | scenario entry or XI Step01 → Step02 Integrated Sync via ProcessDirect → scenario Step07 → possibly XI Step08 → response |

The default queue prefix is `PIP`; bypass branches and receiver-specific queues exist, so the table is not an exhaustive diagram of all paths. The scenario flows here are predominantly templates, not finished business integrations.

Important details confirmed in the implementation:

- When the input header is absent, `partnerID` is looked up via alternative partners, first by the combination sender/alias + interface + namespace, then via `SenderInterface`. The fallback `sender~interface` depends on `CustomXPid_SwitchOffTilde`; in the exported Step02 Async it is `true`, so when no PD configuration is found an error occurs.
- The PD string parameters `receiverDetermination` and `interfaceDetermination_<receiver>` enable the P2P bypass. A receiver alone without an endpoint means `skipRcvDet`, which the integrated variant rejects.
- The integrated flow supports its own receiver/interface determination via `CustomXRDEndpoint`. It must not be confused with the direct reuse of the PO extended determination via `ReuseXRDEndpoint`, which the integrated variant rejects. The summary therefore has to be read with this distinction.
- The integrated routing XSLT is taken dynamically from `pd:${header.partnerID}:receiverDetermination:Binary`. The scenario XSLT itself is not in this export.
- Flexible stages allow mapping a sender stage to a receiver stage; the asynchronous variants contain branching for multiple target stages. Sync rejects multiple receivers, interfaces and target stages.
- Step06 controls the asynchronous delivery by the scenario Step07; Step07 handles mapping, splitting, conversions and possible post-processing exits. Step08 centralizes the XI/IDoc connections and protocol rules.
- The import `src.main.resources.script.PipelineLogger` by itself does not prove a dependency on the Script Collection. The generic flows have these classes locally. The dependency of the templates is documented by the `scriptBundleId` attribute in the model.

## 3. Versions compared to the supplied summary

| Artifact | Baseline in the summary | Export / manifest |
|---|---:|---:|
| Step01 XI | 1.0.5 | **1.0.6** |
| Step01 IDoc | 1.0.8 | **1.0.9** |
| Step02 Inbound | 1.0.5 | **1.0.6** |
| Step02 Integrated Async | 1.0.5 | **1.0.6** |
| Step02 Integrated Sync | 1.0.5 | **1.0.6** |
| Step04 Receiver Determination | 1.0.4 | **1.0.5** |
| Step05 Interface Determination | 1.0.3 | **1.0.4** |
| Step06 Outbound | 1.0.3 | **1.0.4** |
| Step08 XI | 1.0.2 | **1.0.4** |
| Step08 IDoc | 1.0.2 | **1.0.3** |
| Templates changelog | 1.1.1 | **1.2.0** |
| Script Collection | no separate version stated | **1.0.1** |
| Skip Retry Value Mapping | not in the baseline inventory | **1.0.1** |
| Skip Retry PD Upload Helper | not in the baseline inventory | **1.0.0** |

The package/changelog version is not the same as the iFlow version. Current headers of the attached changelogs: Inbound 1.0.8, Pipelines 1.0.5, Outbound **1.0.5**, Templates 1.2.0. The local copy of ContentPackage has its own semanticVersion 1.0.0 in the metadata; it cannot be used as the version of all contained flows.

## 4. Confirmed novelties and their significance

### 4.1 Step01: stages, headers and a lighter XI response

The changelog of 7 July 2026 and the actual XI flow both contain stage mapping in the P2P bypass branch. The asynchronous XI path contains, after sending to JMS, the step `Clean up body in XI response`; the changelog gives the reason as problems with large responses.

The Content Modifiers set `TenantName` with the expression `${sysenv.TENANT_NAME}`. `determinePartnerID` and the IDoc variant read this property instead of calling the environment directly. This is, however, not a complete removal of `System.getenv` — see chapter 5.

The allowed header list includes, among others, `customHeaderProperties` and `dc_(.)`; XI additionally `tenantStage(.*)`. The literal pattern `dc_(.)` is not the same as `dc_(.*)`, so without verification it cannot be generalized that all `dc_` headers of arbitrary length pass. The changelog also adds the receiver queue to the monitoring custom headers.

Source: `Generic Inbound/Change log.txt`, both Step01 `.iflw`, `determinePartnerID*.groovy`, `mapSenderStageToReceiverStage.groovy`.

### 4.2 Skip retry takes a different form in Step02 and Step06

**Step02 Integrated Async:** the model explicitly sets `skipRetry=true` when no receiver is found with an error policy and for an unsupported configuration (`skipRcvDet`, reuse XRD). The exception subprocess distinguishes a partner determination error and the branch `stopRetry=true or skipRetry=true`, which ends with saving to the DLQ. `checkExceptionType.groovy` is not wired into this flow; it cannot be credited with the same general exception classification as Step06.

**Step06:** actually calls `checkExceptionType.groovy`. It walks the chain of exception causes. Exported settings:

| Parameter | Value in the export |
|---|---|
| `SkipRetriesGlobal_Enabled` | `true` |
| `SkipRetriesGlobal_Source` | `Default` |
| `SkipRetriesGlobal_SourcePidProfile` | `SAP_Integration_Suite_SkipRetryGlobalProfile` |

`Default` skips the retry for `com.sap.xi.mapping.camel.XiMappingException` and `com.sap.aii.mappingtool.tf7.IllegalInstanceException`. The alternatives are a Value Mapping or a PD profile. The scenario PD parameter `SkipRetriesProfile` is read directly in Step06 and transferred into the header `CustomXSkipRetriesProfile`; this branch takes precedence even over a global disabling of skip retry. A commented-out attempt to read this profile in Step02 is not active functionality.

The attached Value Mapping has 22 entries: 21 with `false`, one `XiMappingException` with `true`. Changing the source `Default` to `ValueMapping` therefore need not preserve the same behaviour for all exception types.

Source: Step02 Async `.iflw`, Step06 `.iflw`, `parameters.prop`, `checkExceptionType.groovy`, `readRetryHandlingFromPDOutbound.groovy`, Value Mapping `value_mapping.xml`.

### 4.3 PD Upload Helper

The new helper loads the Value Mapping ZIP via the API, extracts `value_mapping.xml`, compares the content with the PD StringParameters and creates missing values or updates differing ones. Surplus PD keys are ignored in the comparison: it is a completion/update, not an exact synchronization including deletion.

The default target PID is `SAP_Integration_Suite_SkipRetryGlobalProfile`, VM version 1.0.1. The API hostname is a placeholder and the credential alias is `OWN_API_ACCESS`. The helper was not run and nothing was written to PD.

Source: helper `.iflw`, `ExtractValueMapping.groovy`, `CompareValueMapping.groovy`, `BuildUpdatePayload.groovy`, `parameters.prop`.

### 4.4 Message restoration for DLQ and custom error handling

`backupMessage.groovy` and `restoreMessage.groovy` are present and used in Step02 Inbound/Async, Step04, Step05 and Step06. The backup contains the payload, headers and attachments; a non-XML payload and attachments are stored using Base64. Async distinguishes the original and the converted payload and, when they are identical, does not needlessly create a second backup.

The changelog describes the restoration as a restoration of the entire state, but the implementation has limits:

- Some technical headers are explicitly excluded from the backup.
- New headers are set to an empty string on restoration, not necessarily removed.
- An error in backing up/restoring an individual attachment is logged and the attachment is skipped.
- If the input had no attachments, `bckAttachmentMetadata` is not created and the restore branch does not clean up attachments added later.

Therefore this functionality cannot be described as an unconditionally exact copy of all aspects of the input message.

### 4.5 The original error after retries are exhausted

Step02 Integrated Async, Step04 and Step05 have `getOriginalExceptionFromMplApi.groovy` wired in, along with the parameters `CI_ODataAPI_TokenURL`, `CI_ODataAPI_URL`, `CI_ODataAPI_UserCredentials`. The script obtains an OAuth token and reads `MessageProcessingLogs('<MPL ID>')/ErrorInformation/$value`.

When the parameters are not filled in or the API call fails, it keeps the generic text about retry exhaustion; it does not throw the API error further. The delivered export contains placeholders, so a concretely working API connection is not documented.

In Step06 a file of the same name also exists, but its `.iflw` does not call it and does not have the corresponding three parameters. The presence of the script in the ZIP is here not proof of an active function.

### 4.6 Monitoring of payload size and queues

The asynchronous flows have added monitoring of queue names. The backup scripts can measure the body size and add `originalPayloadSizeBytes` / `convertedPayloadSizeBytes` to the MPL custom headers. Step02 Async has both measurements switched off in the export; Step06 has `PayloadSizeMeasurement=true`, which the model maps to `originalPayloadSizeMeasurementEnabled`.

### 4.7 Step08: extension of the PD connection configuration

XI and IDoc can also load connection parameters from the PID of the business system. In the respective branch the business system values take precedence over the alias parameters `<key>_<businessSystem>`, which in turn override the externalized defaults. This branch is in both scripts conditional on the presence of both the receiver alias and the receiver.

Selected scenario parameters fill in headers if they were not set by the input:

- XI: credential name, private key alias, authentication type; key `<key>_<receiverAliasOrReceiver>_<interface>`.
- IDoc: the same categories and the content type; the last part of the key is the interface suffix. It can be determined via `pidParameterReadSuffixPattern` and the EDIDC values.

This does not mean that all connection parameters can be freely overridden at every level. The concrete lists `aliasKeys` and `scenarioKeys` are in the source scripts.

Source: `readPDValuesForXI.groovy`, `setValuesForXI.groovy`, `setAllDefaultAndScenarioValuesFromPDForIDoc.groovy`, `setEdidcFromParamsForIDoc.groovy`.

### 4.8 XI/IDoc identifiers and the additional fix of 20 July 2026

XI Step08 uses the ID Mapper and the receiver adapters in Reuse mode. `PassMessageIdFromE2E=true` makes it possible to use the original `SAP_SenderMessageIdEx` if it has the UUID format and no split is detected via `SapSplitExpression`. Otherwise `UniqueID` may be mapped into `SapMessageIdEx`.

**A novelty beyond the 7 July announced in the summary:** the Outbound changelog of 20 July 2026 / 1.0.5 fixes an exceeded ID Mapper input length. The present XI Step08 1.0.4 has an active `prepIdMapperForXiMessageId.groovy`:

- source = `UniqueID + SAP_ReceiverInterface`, truncated to 120 characters;
- context = the namespace cleaned to alphanumeric characters, with the prefix `C`, truncated to 40 characters; without a namespace `IDMapper1`.

IDoc Step08 sets `SapMessageId` from UniqueID and its receiver adapters also use Reuse. The new IDoc one-to-many template overwrites UniqueID for the partial messages on a split.

### 4.9 Templates and logging

Templates 1.2.0 adds `Step07 - Outbound Processing One-to-Many to Generic IDoc Outbound`, the Skip Retry Value Mapping and the PD Upload Helper. The new template has a General Splitter, an adjustment of UniqueID, setting of the IDoc connection/control headers and a ProcessDirect call of the generic IDoc outbound.

The Script Collection remains at 13 scripts, but its `logMessage.groovy` supports exceptions in MPL attachments. If `CamelExceptionCaught` exists, it automatically includes exception logging. It logs the class, message, stack trace, cause and, depending on the available methods, also the HTTP response body/status or the SOAP fault.

## 5. Important deviations and points for further work

### The technical IDs of the dependencies are not consistent

Eight uses of shared scripts in seven templates refer to `com.sap.integration.cloud.pipeline.v2.script.collection`. The manifest of the attached collection, however, has `com.sap.integration.cloud.pipeline.v2.script.collection.TENANT`. We have the content of the scripts, but this copy alone does not document the fulfilment of the referenced runtime dependency. The original collection may exist separately on the tenant.

Similarly, the helper has `ValueMapping_TechnicalID=com.sap.integration.cloud.pipeline.v2.value.mapping.skipretry`, whereas the attached VM has `Bundle-SymbolicName=com.sap.integration.cloud.pipeline.v2.value.mapping.skipretry.16.7.2026_10.16.42`. Before using the helper, its configuration must match the intended actual artifact.

### The move of TENANT_NAME is not completed across the code

An active `System.getenv("TENANT_NAME")` remains in `mapSenderStageToReceiverStage.groovy` in Step01 XI, all three Step02 variants and Step04. These scripts are actually referenced in the flows. By contrast, in `determinePartnerID*.groovy` the old calls are comments and the active code reads `TenantName`. A claim of complete removal of direct environment access would be inaccurate.

### Step06: the offer of sources in the code is not uniform

`checkExceptionType.groovy` also contains the branches `PartnerDirectoryGlobal` and `PartnerDirectoryScenario`, but the preceding `readRetryHandlingFromPDOutbound.groovy`, without a scenario override, allows only `Default`, `ValueMapping`, `PartnerDirectory`; any other value it evaluates as invalid and switches skip retry off. It is necessary to document and use the whole chain, not just the classifier's switch.

In addition, the comments use the similar names `CustomXSkipRetryProfile` and `CustomXSkipRetriesProfile`; the decisive header in the classifier is the **Retries** variant. The detailed origin of these modifications cannot be determined without a clean SAP export and history. It is advisable to record them as the behaviour of this TENANT copy, not automatically as a general SAP specification.

### Missing baseline and runtime configuration

- We have a historical summary, not the old versions of the source packages nor the 11+2 original documents. The version differences and changelog novelties are documented; a full line-by-line diff and the authorship of each change are not.
- We do not have the PD String/BinaryParameters, alternative partners, the content of the dynamic XSLTs, the actual security materials, the tenant configuration or the deployment status. We cannot confirm the specific recipients, the rules of all scenarios or the current activation of functions on the tenant.
- The readability of the sources is not proof of runtime correctness. The Groovy scripts were neither compiled nor run against the SAP runtime. Nothing was changed or deployed into SAP.
- No import of `com.sap.it.script.v2.api` was found in the Groovy files; the warning about the v2 API in the historical summary is therefore not proof that this export has already moved to v2.

## 6. Materials for follow-up work

For further discussion, use this document together with the unpacked sources as the baseline of the **export of 18 September 2026**. The novelties are no longer only an announcement: most of them can be traced directly in the flows and scripts, including the later XI fix.

The practical priority of any further revision is to check the technical IDs of the shared collection and of the VM/helper on the tenant, to clarify the intended skip retry configuration in Step06, and to add a PD export for specific scenarios. Old exports would make it possible to separate a clean SAP upgrade from local modifications by a full diff.
