# Claims: tenant-export-review-2026-09

Claims extracted from knowledge/inputs/en/tenant-export-review-2026-09.md (spec Appendix A, applied to documents). Claims are the input's statements, unverified; WHERE is the input line. The claims from the preamble (L5) are listed under the first section.

## 1. Readability and scope

- R-001 · **CLAIM** The analysis covers four TENANT packages of the export. [tenant-specific] · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L5
- R-002 · **CLAIM** The supplied summary serves as historical context, not as an authoritative description of the new export. · KIND procedure · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L5
- R-003 · **CLAIM** The conclusions are based on the manifests, changelogs, BPMN definitions and source scripts of the exported artifacts. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L5
- R-004 · **CLAIM** The analysis is not a verification of the latest version available in SAP Discover, nor a runtime test on the tenant. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L5
- R-005 · **CLAIM** All attached deployable artifacts of the export are readable. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L9
- R-006 · **CLAIM** In the export, `resources.cnt` contains Base64 JSON. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L9
- R-007 · **CLAIM** In the export, `contentmetadata.md` contains Base64 text. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L9
- R-008 · **CLAIM** In the export, `<id>_content` is either the ZIP of the artifact or accompanying content. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L9
- R-009 · **CLAIM** The decoded metadata pairs names, types and versions with the content. · KIND procedure · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L9
- R-010 · **CLAIM** Generic Inbound contains 2 iFlows. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L13
- R-011 · **CLAIM** Generic Pipelines contains 5 iFlows. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L14
- R-012 · **CLAIM** Generic Outbound contains 3 iFlows. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L15
- R-013 · **CLAIM** Templates contains 20 iFlows plus a Script Collection and a Value Mapping. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L16
- R-014 · **CLAIM** The four packages contain 30 iFlows and 2 other deployable artifacts in total. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L17
- R-015 · **CLAIM** Templates contains 19 templates and one Upload Helper. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L19
- R-016 · **CLAIM** Earlier the historical summary stated 18 templates. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L19
- R-017 · **CLAIM** 32 deployable artifacts were unpacked; together with the changelogs and custom tags this produced 376 files. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L23
- R-018 · **CLAIM** 30 `.iflw` definitions, 30 `.prop` and 30 `.propdef` files and 32 manifests were loaded. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L24
- R-019 · **CLAIM** The 177 available Groovy files represent 71 distinct contents by SHA-256. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L25
- R-020 · **CLAIM** The Script Collection has 13 scripts. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L25
- R-021 · **CLAIM** The XML parser loaded all 101 files of the types `.iflw`, `.propdef`, `.project`, `.mmap`, `.xsd`, `.wsdl` and `.xml`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L26
- R-022 · **CLAIM** Loading the XML demonstrates syntactic readability, not validation of the SAP model or a successful deployment. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L26
- R-023 · **CLAIM** The message mapping `.mmap`, the Value Mapping XML, six XSDs and one WSDL were read. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L27
- R-024 · **CLAIM** Fourteen binary `.crt` certificates can be loaded as X.509; their subject is SAP SE. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L27
- R-025 · **CLAIM** The cryptographic validity of the package signatures was not verified. · KIND open-question · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L27
- R-026 · **CLAIM** All local references to Groovy files have a corresponding file. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L28
- R-027 · **CLAIM** Eight further uses of scripts point to the shared collection. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L28
- R-028 · **CLAIM** The content of the shared-collection scripts is present, but the technical ID of the collection does not match the references. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L28
- R-029 · **CLAIM** The eight URL items in the metadata are links, not missing ZIP artifacts; the content of the linked websites is not part of the export. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L29
- R-030 · **CLAIM** The unpacked copies are in four subfolders of `<value>`; the original exports were not modified. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L31
- R-031 · **CLAIM** The machine-readable materials are `inventar.csv`, `manifest-inventory.csv`, `file-hashes.csv`, `script-references.csv`, `flow-summary.txt`, `flow-index.txt`, `script-index.txt`. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L31
- R-032 · **CLAIM** The list of steps in the indexes preserves the XML order, which is not in itself the execution order. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L31
- R-033 · **CLAIM** The `sequenceFlow` links in the `.iflw` decide the execution order. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L31

## 2. How the pipeline works in these packages

- R-034 · **CLAIM** The core of the pipeline is a set of reusable generic flows. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L35
- R-035 · **CLAIM** A specific scenario is determined by the message headers, the Partner Directory (PD) configuration, the routing mappings and the scenario flows called via ProcessDirect. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L35
- R-036 · **CLAIM** JMS separates the asynchronous phases and their retry. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L35
- R-037 · **CLAIM** Fully decoupled async path: Step01 → Q01 → Step02 (optionally Step03 conversion) → Q02 → Step04 receiver determination → Q03 → Step05 interface determination → Q04 → Step06 → scenario Step07 → possibly Step08. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L39
- R-038 · **CLAIM** Integrated Messaging Async path: Step01 → X01 → Step02 Integrated Async (conversion, receivers and interfaces) → Q04 → Step06 → scenario Step07 → possibly Step08. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L40
- R-039 · **CLAIM** Integrated Messaging Sync path: scenario entry or XI Step01 → Step02 Integrated Sync via ProcessDirect → scenario Step07 → possibly XI Step08 → response. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L41
- R-040 · **CLAIM** The default queue prefix is `PIP`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L43
- R-041 · **CLAIM** Bypass branches and receiver-specific queues exist, so the path table is not an exhaustive diagram of all paths. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L43
- R-042 · **CLAIM** The scenario flows in the export are predominantly templates, not finished business integrations. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L43
- R-043 · **CLAIM** When the input header is absent, `partnerID` is looked up via alternative partners, first by the combination sender/alias + interface + namespace, then via `SenderInterface`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L47
- R-044 · **CLAIM** The fallback `sender~interface` depends on `CustomXPid_SwitchOffTilde`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L47
- R-045 · **CLAIM** In the exported Step02 Async `CustomXPid_SwitchOffTilde` is `true`. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L47
- R-046 · **CLAIM** With `CustomXPid_SwitchOffTilde` = `true`, an error occurs when no PD configuration is found. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L47
- R-047 · **CLAIM** The PD string parameters `receiverDetermination` and `interfaceDetermination_<receiver>` enable the P2P bypass. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L48
- R-048 · **CLAIM** A receiver alone without an endpoint means `skipRcvDet`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L48
- R-049 · **CLAIM** The integrated variant rejects `skipRcvDet`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L48
- R-050 · **CLAIM** The integrated flow supports its own receiver/interface determination via `CustomXRDEndpoint`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L49
- R-051 · **CLAIM** The integrated variant rejects the direct reuse of the PO extended determination via `ReuseXRDEndpoint`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L49
- R-052 · **CLAIM** `CustomXRDEndpoint` must not be confused with `ReuseXRDEndpoint`; the historical summary has to be read with this distinction. · KIND pitfall · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L49
- R-053 · **CLAIM** The integrated routing XSLT is taken dynamically from `pd:${header.partnerID}:receiverDetermination:Binary`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L50
- R-054 · **CLAIM** The scenario XSLT itself is not in the export. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L50
- R-055 · **CLAIM** Flexible stages allow mapping a sender stage to a receiver stage. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L51
- R-056 · **CLAIM** The asynchronous variants contain branching for multiple target stages. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L51
- R-057 · **CLAIM** Sync rejects multiple receivers, interfaces and target stages. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L51
- R-058 · **CLAIM** Step06 controls the asynchronous delivery by the scenario Step07. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L52
- R-059 · **CLAIM** Step07 handles mapping, splitting, conversions and possible post-processing exits. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L52
- R-060 · **CLAIM** Step08 centralizes the XI/IDoc connections and protocol rules. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L52
- R-061 · **CLAIM** The import `src.main.resources.script.PipelineLogger` by itself does not prove a dependency on the Script Collection. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L53
- R-062 · **CLAIM** The generic flows have the `PipelineLogger` classes locally. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L53
- R-063 · **CLAIM** The dependency of the templates on the Script Collection is documented by the `scriptBundleId` attribute in the model. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L53

## 3. Versions compared to the supplied summary

- R-064 · **CLAIM** Step01 XI is version 1.0.6 in the export; the summary baseline was 1.0.5. · KIND fact · CONFIDENCE high · VERSION 1.0.5→1.0.6 · WHERE tenant-export-review-2026-09.md:L59
- R-065 · **CLAIM** Step01 IDoc is version 1.0.9 in the export; the summary baseline was 1.0.8. · KIND fact · CONFIDENCE high · VERSION 1.0.8→1.0.9 · WHERE tenant-export-review-2026-09.md:L60
- R-066 · **CLAIM** Step02 Inbound is version 1.0.6 in the export; the summary baseline was 1.0.5. · KIND fact · CONFIDENCE high · VERSION 1.0.5→1.0.6 · WHERE tenant-export-review-2026-09.md:L61
- R-067 · **CLAIM** Step02 Integrated Async is version 1.0.6 in the export; the summary baseline was 1.0.5. · KIND fact · CONFIDENCE high · VERSION 1.0.5→1.0.6 · WHERE tenant-export-review-2026-09.md:L62
- R-068 · **CLAIM** Step02 Integrated Sync is version 1.0.6 in the export; the summary baseline was 1.0.5. · KIND fact · CONFIDENCE high · VERSION 1.0.5→1.0.6 · WHERE tenant-export-review-2026-09.md:L63
- R-069 · **CLAIM** Step04 Receiver Determination is version 1.0.5 in the export; the summary baseline was 1.0.4. · KIND fact · CONFIDENCE high · VERSION 1.0.4→1.0.5 · WHERE tenant-export-review-2026-09.md:L64
- R-070 · **CLAIM** Step05 Interface Determination is version 1.0.4 in the export; the summary baseline was 1.0.3. · KIND fact · CONFIDENCE high · VERSION 1.0.3→1.0.4 · WHERE tenant-export-review-2026-09.md:L65
- R-071 · **CLAIM** Step06 Outbound is version 1.0.4 in the export; the summary baseline was 1.0.3. · KIND fact · CONFIDENCE high · VERSION 1.0.3→1.0.4 · WHERE tenant-export-review-2026-09.md:L66
- R-072 · **CLAIM** Step08 XI is version 1.0.4 in the export; the summary baseline was 1.0.2. · KIND fact · CONFIDENCE high · VERSION 1.0.2→1.0.4 · WHERE tenant-export-review-2026-09.md:L67
- R-073 · **CLAIM** Step08 IDoc is version 1.0.3 in the export; the summary baseline was 1.0.2. · KIND fact · CONFIDENCE high · VERSION 1.0.2→1.0.3 · WHERE tenant-export-review-2026-09.md:L68
- R-074 · **CLAIM** The Templates changelog is at 1.2.0 in the export; the summary baseline was 1.1.1. · KIND fact · CONFIDENCE high · VERSION 1.1.1→1.2.0 · WHERE tenant-export-review-2026-09.md:L69
- R-075 · **CLAIM** The Script Collection is version 1.0.1 in the export; the summary stated no separate version. · KIND fact · CONFIDENCE high · VERSION 1.0.1 · WHERE tenant-export-review-2026-09.md:L70
- R-076 · **CLAIM** The Skip Retry Value Mapping is version 1.0.1 in the export; it was not in the baseline inventory. · KIND fact · CONFIDENCE high · VERSION 1.0.1 · WHERE tenant-export-review-2026-09.md:L71
- R-077 · **CLAIM** The Skip Retry PD Upload Helper is version 1.0.0 in the export; it was not in the baseline inventory. · KIND fact · CONFIDENCE high · VERSION 1.0.0 · WHERE tenant-export-review-2026-09.md:L72
- R-078 · **CLAIM** The package/changelog version is not the same as the iFlow version. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L74
- R-079 · **CLAIM** The current header of the Generic Inbound changelog is 1.0.8. · KIND fact · CONFIDENCE high · VERSION 1.0.8 · WHERE tenant-export-review-2026-09.md:L74
- R-080 · **CLAIM** The current header of the Generic Pipelines changelog is 1.0.5. · KIND fact · CONFIDENCE high · VERSION 1.0.5 · WHERE tenant-export-review-2026-09.md:L74
- R-081 · **CLAIM** The current header of the Generic Outbound changelog is 1.0.5. · KIND fact · CONFIDENCE high · VERSION 1.0.5 · WHERE tenant-export-review-2026-09.md:L74
- R-082 · **CLAIM** The current header of the Templates changelog is 1.2.0. · KIND fact · CONFIDENCE high · VERSION 1.2.0 · WHERE tenant-export-review-2026-09.md:L74
- R-083 · **CLAIM** The local copy of ContentPackage has its own semanticVersion 1.0.0 in the metadata, which cannot be used as the version of all contained flows. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L74

## 4. Confirmed novelties and their significance

### 4.1 Step01: stages, headers and a lighter XI response

- R-084 · **CLAIM** The Generic Inbound changelog of 7 July 2026 and the actual Step01 XI flow both contain stage mapping in the P2P bypass branch. · KIND fact · CONFIDENCE high · VERSION 1.0.6 · WHERE tenant-export-review-2026-09.md:L80
- R-085 · **CLAIM** The asynchronous Step01 XI path contains, after sending to JMS, the step `Clean up body in XI response`. · KIND fact · CONFIDENCE high · VERSION 1.0.6 · WHERE tenant-export-review-2026-09.md:L80
- R-086 · **CLAIM** The changelog gives problems with large responses as the reason for `Clean up body in XI response`. · KIND rationale · CONFIDENCE high · VERSION 1.0.6 · WHERE tenant-export-review-2026-09.md:L80
- R-087 · **CLAIM** The Step01 Content Modifiers set `TenantName` with the expression `${sysenv.TENANT_NAME}`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L82
- R-088 · **CLAIM** `determinePartnerID` and the IDoc variant read the property `TenantName` instead of calling the environment directly. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L82
- R-089 · **CLAIM** Reading `TenantName` is not a complete removal of `System.getenv` (see chapter 5). · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L82
- R-090 · **CLAIM** The Step01 allowed header list includes, among others, `customHeaderProperties` and `dc_(.)`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L84
- R-091 · **CLAIM** The Step01 XI allowed header list additionally includes `tenantStage(.*)`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L84
- R-092 · **CLAIM** The literal pattern `dc_(.)` is not the same as `dc_(.*)`, so it cannot be generalized without verification that all `dc_` headers of arbitrary length pass. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L84
- R-093 · **CLAIM** The Generic Inbound changelog adds the receiver queue to the monitoring custom headers. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L84

### 4.2 Skip retry takes a different form in Step02 and Step06

- R-094 · **CLAIM** Step02 Integrated Async explicitly sets `skipRetry=true` when no receiver is found with an error policy. · KIND fact · CONFIDENCE high · VERSION 1.0.6 · WHERE tenant-export-review-2026-09.md:L90
- R-095 · **CLAIM** Step02 Integrated Async explicitly sets `skipRetry=true` for an unsupported configuration (`skipRcvDet`, reuse XRD). · KIND fact · CONFIDENCE high · VERSION 1.0.6 · WHERE tenant-export-review-2026-09.md:L90
- R-096 · **CLAIM** The Step02 Integrated Async exception subprocess distinguishes a partner determination error and the branch `stopRetry=true or skipRetry=true`, which ends with saving to the DLQ. · KIND fact · CONFIDENCE high · VERSION 1.0.6 · WHERE tenant-export-review-2026-09.md:L90
- R-097 · **CLAIM** `checkExceptionType.groovy` is not wired into Step02 Integrated Async. · KIND fact · CONFIDENCE high · VERSION 1.0.6 · WHERE tenant-export-review-2026-09.md:L90
- R-098 · **CLAIM** Step02 Integrated Async cannot be credited with the same general exception classification as Step06. · KIND pitfall · CONFIDENCE high · VERSION 1.0.6 · WHERE tenant-export-review-2026-09.md:L90
- R-099 · **CLAIM** Step06 calls `checkExceptionType.groovy`. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L92
- R-100 · **CLAIM** `checkExceptionType.groovy` walks the chain of exception causes. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L92
- R-101 · **CLAIM** In the export `SkipRetriesGlobal_Enabled` is `true`. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L96
- R-102 · **CLAIM** In the export `SkipRetriesGlobal_Source` is `Default`. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L97
- R-103 · **CLAIM** In the export `SkipRetriesGlobal_SourcePidProfile` is `SAP_Integration_Suite_SkipRetryGlobalProfile`. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L98
- R-104 · **CLAIM** The source `Default` skips the retry for `com.sap.xi.mapping.camel.XiMappingException` and `com.sap.aii.mappingtool.tf7.IllegalInstanceException`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L100
- R-105 · **CLAIM** The alternatives to the source `Default` are a Value Mapping or a PD profile. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L100
- R-106 · **CLAIM** The scenario PD parameter `SkipRetriesProfile` is read directly in Step06 and transferred into the header `CustomXSkipRetriesProfile`. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L100
- R-107 · **CLAIM** The scenario `SkipRetriesProfile` branch takes precedence even over a global disabling of skip retry. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L100
- R-108 · **CLAIM** A commented-out attempt to read `SkipRetriesProfile` in Step02 is not active functionality. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L100
- R-109 · **CLAIM** The attached Value Mapping has 22 entries: 21 with `false`, one `XiMappingException` with `true`. · KIND fact · CONFIDENCE high · VERSION 1.0.1 · WHERE tenant-export-review-2026-09.md:L102
- R-110 · **CLAIM** Changing the source `Default` to `ValueMapping` need not preserve the same behaviour for all exception types. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L102

### 4.3 PD Upload Helper

- R-111 · **CLAIM** The PD Upload Helper loads the Value Mapping ZIP via the API, extracts `value_mapping.xml`, compares the content with the PD StringParameters and creates missing values or updates differing ones. · KIND fact · CONFIDENCE high · VERSION 1.0.0 · WHERE tenant-export-review-2026-09.md:L108
- R-112 · **CLAIM** The PD Upload Helper ignores surplus PD keys: it is a completion/update, not an exact synchronization including deletion. · KIND fact · CONFIDENCE high · VERSION 1.0.0 · WHERE tenant-export-review-2026-09.md:L108
- R-113 · **CLAIM** The default target PID of the PD Upload Helper is `SAP_Integration_Suite_SkipRetryGlobalProfile`, with VM version 1.0.1. · KIND fact · CONFIDENCE high · VERSION 1.0.0 · WHERE tenant-export-review-2026-09.md:L110
- R-114 · **CLAIM** In the PD Upload Helper the API hostname is a placeholder. · KIND fact · CONFIDENCE high · VERSION 1.0.0 · WHERE tenant-export-review-2026-09.md:L110
- R-115 · **CLAIM** The credential alias of the PD Upload Helper is `<credential-alias>`. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION 1.0.0 · WHERE tenant-export-review-2026-09.md:L110
- R-116 · **CLAIM** The PD Upload Helper was not run and nothing was written to PD. · KIND fact · CONFIDENCE high · VERSION 1.0.0 · WHERE tenant-export-review-2026-09.md:L110

### 4.4 Message restoration for DLQ and custom error handling

- R-117 · **CLAIM** `backupMessage.groovy` and `restoreMessage.groovy` are present and used in Step02 Inbound/Async, Step04, Step05 and Step06. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L116
- R-118 · **CLAIM** The backup contains the payload, headers and attachments. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L116
- R-119 · **CLAIM** A non-XML payload and attachments are stored using Base64. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L116
- R-120 · **CLAIM** Step02 Async distinguishes the original and the converted payload and, when they are identical, does not create a second backup. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L116
- R-121 · **CLAIM** The changelog describes the restoration as a restoration of the entire state, but the implementation has limits; it cannot be described as an unconditionally exact copy of the input message. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L118, L125
- R-122 · **CLAIM** Some technical headers are explicitly excluded from the backup. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L120
- R-123 · **CLAIM** New headers are set to an empty string on restoration, not necessarily removed. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L121
- R-124 · **CLAIM** An error in backing up/restoring an individual attachment is logged and the attachment is skipped. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L122
- R-125 · **CLAIM** If the input had no attachments, `bckAttachmentMetadata` is not created and the restore branch does not clean up attachments added later. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L123

### 4.5 The original error after retries are exhausted

- R-126 · **CLAIM** Step02 Integrated Async, Step04 and Step05 have `getOriginalExceptionFromMplApi.groovy` wired in. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L129
- R-127 · **CLAIM** The flows wiring `getOriginalExceptionFromMplApi.groovy` have the parameters `CI_ODataAPI_TokenURL`, `CI_ODataAPI_URL`, `CI_ODataAPI_UserCredentials`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L129
- R-128 · **CLAIM** `getOriginalExceptionFromMplApi.groovy` obtains an OAuth token and reads `MessageProcessingLogs('<MPL ID>')/ErrorInformation/$value`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L129
- R-129 · **CLAIM** When the parameters are not filled in or the API call fails, `getOriginalExceptionFromMplApi.groovy` keeps the generic text about retry exhaustion and does not throw the API error further. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L131
- R-130 · **CLAIM** The delivered export contains placeholders for the API parameters, so a concretely working API connection is not documented. · KIND open-question · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L131
- R-131 · **CLAIM** In Step06 a file `getOriginalExceptionFromMplApi.groovy` also exists, but its `.iflw` does not call it and does not have the three parameters. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L133
- R-132 · **CLAIM** The presence of a script in the ZIP is not proof of an active function. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L133

### 4.6 Monitoring of payload size and queues

- R-133 · **CLAIM** The asynchronous flows have added monitoring of queue names. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L137
- R-134 · **CLAIM** The backup scripts can measure the body size and add `originalPayloadSizeBytes` / `convertedPayloadSizeBytes` to the MPL custom headers. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L137
- R-135 · **CLAIM** Step02 Async has both payload size measurements switched off in the export. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L137
- R-136 · **CLAIM** Step06 has `PayloadSizeMeasurement=true` in the export. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L137
- R-137 · **CLAIM** The Step06 model maps `PayloadSizeMeasurement` to `originalPayloadSizeMeasurementEnabled`. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L137

### 4.7 Step08: extension of the PD connection configuration

- R-138 · **CLAIM** Step08 XI and IDoc can also load connection parameters from the PID of the business system. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L141
- R-139 · **CLAIM** In the business-system branch the business system values take precedence over the alias parameters `<key>_<businessSystem>`, which in turn override the externalized defaults. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L141
- R-140 · **CLAIM** The business-system branch is in both scripts conditional on the presence of both the receiver alias and the receiver. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L141
- R-141 · **CLAIM** Selected scenario parameters fill in headers if they were not set by the input. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L143
- R-142 · **CLAIM** For XI the scenario parameters cover credential name, private key alias and authentication type, with the key `<key>_<receiverAliasOrReceiver>_<interface>`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L145
- R-143 · **CLAIM** For IDoc the scenario parameters cover the same categories and the content type; the last part of the key is the interface suffix. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L146
- R-144 · **CLAIM** The IDoc interface suffix can be determined via `pidParameterReadSuffixPattern` and the EDIDC values. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L146
- R-145 · **CLAIM** Not all connection parameters can be freely overridden at every level. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L148
- R-146 · **CLAIM** The concrete lists `aliasKeys` and `scenarioKeys` are in the source scripts (`readPDValuesForXI.groovy`, `setValuesForXI.groovy`, `setAllDefaultAndScenarioValuesFromPDForIDoc.groovy`, `setEdidcFromParamsForIDoc.groovy`). · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L148

### 4.8 XI/IDoc identifiers and the additional fix of 20 July 2026

- R-147 · **CLAIM** XI Step08 uses the ID Mapper and the receiver adapters in Reuse mode. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L154
- R-148 · **CLAIM** `PassMessageIdFromE2E=true` makes it possible to use the original `SAP_SenderMessageIdEx` if it has the UUID format and no split is detected via `SapSplitExpression`. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L154
- R-149 · **CLAIM** Otherwise `UniqueID` may be mapped into `SapMessageIdEx`. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L154
- R-150 · **CLAIM** The Generic Outbound changelog of 20 July 2026 / 1.0.5 fixes an exceeded ID Mapper input length. · KIND fact · CONFIDENCE high · VERSION 1.0.5 · WHERE tenant-export-review-2026-09.md:L156
- R-151 · **CLAIM** The 20 July 2026 fix is a novelty beyond the 7 July changes announced in the historical summary. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L156
- R-152 · **CLAIM** XI Step08 1.0.4 has an active `prepIdMapperForXiMessageId.groovy`. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L156
- R-153 · **CLAIM** In `prepIdMapperForXiMessageId.groovy` the source = `UniqueID + SAP_ReceiverInterface`, truncated to 120 characters. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L158
- R-154 · **CLAIM** In `prepIdMapperForXiMessageId.groovy` the context = the namespace cleaned to alphanumeric characters, with the prefix `C`, truncated to 40 characters; without a namespace `IDMapper1`. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L159
- R-155 · **CLAIM** IDoc Step08 sets `SapMessageId` from UniqueID. · KIND fact · CONFIDENCE high · VERSION 1.0.3 · WHERE tenant-export-review-2026-09.md:L161
- R-156 · **CLAIM** The IDoc Step08 receiver adapters also use Reuse. · KIND fact · CONFIDENCE high · VERSION 1.0.3 · WHERE tenant-export-review-2026-09.md:L161
- R-157 · **CLAIM** The new IDoc one-to-many template overwrites UniqueID for the partial messages on a split. · KIND fact · CONFIDENCE high · VERSION 1.2.0 · WHERE tenant-export-review-2026-09.md:L161

### 4.9 Templates and logging

- R-158 · **CLAIM** Templates 1.2.0 adds `Step07 - Outbound Processing One-to-Many to Generic IDoc Outbound`. · KIND fact · CONFIDENCE high · VERSION 1.2.0 · WHERE tenant-export-review-2026-09.md:L165
- R-159 · **CLAIM** Templates 1.2.0 adds the Skip Retry Value Mapping. · KIND fact · CONFIDENCE high · VERSION 1.2.0 · WHERE tenant-export-review-2026-09.md:L165
- R-160 · **CLAIM** Templates 1.2.0 adds the PD Upload Helper. · KIND fact · CONFIDENCE high · VERSION 1.2.0 · WHERE tenant-export-review-2026-09.md:L165
- R-161 · **CLAIM** The new One-to-Many to Generic IDoc Outbound template has a General Splitter, an adjustment of UniqueID, setting of the IDoc connection/control headers and a ProcessDirect call of the generic IDoc outbound. · KIND fact · CONFIDENCE high · VERSION 1.2.0 · WHERE tenant-export-review-2026-09.md:L165
- R-162 · **CLAIM** The Script Collection remains at 13 scripts. · KIND fact · CONFIDENCE high · VERSION 1.0.1 · WHERE tenant-export-review-2026-09.md:L167
- R-163 · **CLAIM** The Script Collection's `logMessage.groovy` supports exceptions in MPL attachments. · KIND fact · CONFIDENCE high · VERSION 1.0.1 · WHERE tenant-export-review-2026-09.md:L167
- R-164 · **CLAIM** If `CamelExceptionCaught` exists, `logMessage.groovy` automatically includes exception logging. · KIND fact · CONFIDENCE high · VERSION 1.0.1 · WHERE tenant-export-review-2026-09.md:L167
- R-165 · **CLAIM** `logMessage.groovy` logs the class, message, stack trace, cause and, depending on the available methods, also the HTTP response body/status or the SOAP fault. · KIND fact · CONFIDENCE high · VERSION 1.0.1 · WHERE tenant-export-review-2026-09.md:L167

## 5. Important deviations and points for further work

### The technical IDs of the dependencies are not consistent

- R-166 · **CLAIM** Eight uses of shared scripts in seven templates refer to `com.sap.integration.cloud.pipeline.v2.script.collection`. · KIND fact · CONFIDENCE high · VERSION 1.2.0 · WHERE tenant-export-review-2026-09.md:L173
- R-167 · **CLAIM** The manifest of the attached collection has `com.sap.integration.cloud.pipeline.v2.script.collection.TENANT`. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L173
- R-168 · **CLAIM** The attached collection copy alone does not document the fulfilment of the referenced runtime dependency; the original collection may exist separately on the tenant. · KIND open-question · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L173
- R-169 · **CLAIM** The PD Upload Helper has `ValueMapping_TechnicalID=com.sap.integration.cloud.pipeline.v2.value.mapping.skipretry`. · KIND fact · CONFIDENCE high · VERSION 1.0.0 · WHERE tenant-export-review-2026-09.md:L175
- R-170 · **CLAIM** The attached Value Mapping has `Bundle-SymbolicName=com.sap.integration.cloud.pipeline.v2.value.mapping.skipretry.<value>`. [tenant-specific] · KIND fact · CONFIDENCE high · VERSION 1.0.1 · WHERE tenant-export-review-2026-09.md:L175
- R-171 · **CLAIM** Before using the PD Upload Helper, its configuration must match the intended actual artifact. · KIND procedure · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L175

### The move of TENANT_NAME is not completed across the code

- R-172 · **CLAIM** An active `System.getenv("TENANT_NAME")` remains in `mapSenderStageToReceiverStage.groovy` in Step01 XI, all three Step02 variants and Step04. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L179
- R-173 · **CLAIM** The `mapSenderStageToReceiverStage.groovy` copies with `System.getenv` are actually referenced in the flows. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L179
- R-174 · **CLAIM** In `determinePartnerID*.groovy` the old `System.getenv` calls are comments and the active code reads `TenantName`. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L179
- R-175 · **CLAIM** A claim of complete removal of direct environment access would be inaccurate. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L179

### Step06: the offer of sources in the code is not uniform

- R-176 · **CLAIM** `checkExceptionType.groovy` also contains the branches `PartnerDirectoryGlobal` and `PartnerDirectoryScenario`. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L183
- R-177 · **CLAIM** `readRetryHandlingFromPDOutbound.groovy`, without a scenario override, allows only `Default`, `ValueMapping`, `PartnerDirectory`; any other value it evaluates as invalid and switches skip retry off. · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L183
- R-178 · **CLAIM** The whole skip-retry chain of Step06 has to be documented and used, not just the classifier's switch. · KIND procedure · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L183
- R-179 · **CLAIM** The comments use the similar names `CustomXSkipRetryProfile` and `CustomXSkipRetriesProfile`. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L185
- R-180 · **CLAIM** The decisive header in the classifier is `CustomXSkipRetriesProfile` (the Retries variant). · KIND fact · CONFIDENCE high · VERSION 1.0.4 · WHERE tenant-export-review-2026-09.md:L185
- R-181 · **CLAIM** The detailed origin of the skip-retry modifications cannot be determined without a clean SAP export and history. · KIND open-question · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L185
- R-182 · **CLAIM** Record the skip-retry modifications as the behaviour of this TENANT copy, not automatically as a general SAP specification. [tenant-specific] · KIND procedure · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L185

### Missing baseline and runtime configuration

- R-183 · **CLAIM** The analysis has a historical summary, but not the old versions of the source packages nor the 11+2 original documents. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L189
- R-184 · **CLAIM** The version differences and changelog novelties are documented, but a full line-by-line diff and the authorship of each change are not. · KIND open-question · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L189
- R-185 · **CLAIM** The PD String/BinaryParameters, alternative partners, the content of the dynamic XSLTs, the actual security materials, the tenant configuration and the deployment status are not available. · KIND open-question · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L190
- R-186 · **CLAIM** The specific recipients, the rules of all scenarios and the current activation of functions on the tenant cannot be confirmed. · KIND open-question · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L190
- R-187 · **CLAIM** The readability of the sources is not proof of runtime correctness; the Groovy scripts were neither compiled nor run against the SAP runtime. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L191
- R-188 · **CLAIM** Nothing was changed or deployed into SAP. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L191
- R-189 · **CLAIM** No import of `com.sap.it.script.v2.api` was found in the Groovy files. · KIND fact · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L192
- R-190 · **CLAIM** Earlier the historical summary warned about the v2 API; that warning is not proof that this export has already moved to v2. · KIND pitfall · CONFIDENCE high · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L192

## 6. Materials for follow-up work

- R-191 · **CLAIM** Use the review together with the unpacked sources as the baseline of the export of 18 September 2026. · KIND procedure · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L196
- R-192 · **CLAIM** Most of the novelties can be traced directly in the flows and scripts, including the later XI fix. · KIND fact · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L196
- R-193 · **CLAIM** Priority: check the technical IDs of the shared collection and of the VM/helper on the tenant. · KIND procedure · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L198
- R-194 · **CLAIM** Priority: clarify the intended skip retry configuration in Step06. · KIND procedure · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L198
- R-195 · **CLAIM** Priority: add a PD export for specific scenarios. · KIND procedure · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L198
- R-196 · **CLAIM** Old exports would make it possible to separate a clean SAP upgrade from local modifications by a full diff. · KIND rationale · CONFIDENCE medium · VERSION export-2026-09-18 · WHERE tenant-export-review-2026-09.md:L198

## Name index

- Step01 XI: R-039, R-064, R-084, R-085, R-091, R-172
- Step01 IDoc: R-065
- Step02 Inbound: R-066, R-117, R-172
- Step02 Integrated Async: R-038, R-045, R-067, R-094, R-095, R-096, R-097, R-098, R-117, R-120, R-126, R-135, R-172
- Step02 Integrated Sync: R-039, R-068, R-172
- Step03: R-037
- Step04 Receiver Determination: R-037, R-069, R-117, R-126, R-172
- Step05 Interface Determination: R-037, R-070, R-117, R-126
- Step06 Outbound: R-037, R-038, R-058, R-071, R-098, R-099, R-106, R-117, R-131, R-136, R-137, R-178, R-194
- Step07: R-037, R-038, R-039, R-058, R-059, R-158
- Step08 XI: R-039, R-072, R-138, R-147, R-152
- Step08 IDoc: R-073, R-138, R-155, R-156
- Script Collection: R-013, R-020, R-027, R-061, R-063, R-075, R-162, R-163, R-167, R-168, R-193
- Skip Retry Value Mapping: R-076, R-109, R-159, R-170
- PD Upload Helper: R-077, R-111, R-112, R-113, R-114, R-115, R-116, R-160, R-169, R-171
- Step07 - Outbound Processing One-to-Many to Generic IDoc Outbound: R-157, R-158, R-161
- Clean up body in XI response: R-085, R-086
- `${sysenv.TENANT_NAME}`: R-087
- `TenantName`: R-087, R-088, R-089, R-174
- `System.getenv`: R-089, R-172, R-173, R-174
- `determinePartnerID*.groovy`: R-088, R-174
- `mapSenderStageToReceiverStage.groovy`: R-172, R-173
- `checkExceptionType.groovy`: R-097, R-099, R-100, R-176
- `readRetryHandlingFromPDOutbound.groovy`: R-177
- `backupMessage.groovy`: R-117
- `restoreMessage.groovy`: R-117
- `getOriginalExceptionFromMplApi.groovy`: R-126, R-127, R-128, R-129, R-131
- `readPDValuesForXI.groovy`: R-146
- `setValuesForXI.groovy`: R-146
- `setAllDefaultAndScenarioValuesFromPDForIDoc.groovy`: R-146
- `setEdidcFromParamsForIDoc.groovy`: R-146
- `prepIdMapperForXiMessageId.groovy`: R-152, R-153, R-154
- `logMessage.groovy`: R-163, R-164, R-165
- `src.main.resources.script.PipelineLogger`: R-061
- `PipelineLogger`: R-061, R-062
- `value_mapping.xml`: R-111
- `scriptBundleId`: R-063
- `com.sap.integration.cloud.pipeline.v2.script.collection`: R-166
- `com.sap.integration.cloud.pipeline.v2.script.collection.TENANT`: R-167
- `com.sap.integration.cloud.pipeline.v2.value.mapping.skipretry`: R-169, R-170
- `ValueMapping_TechnicalID`: R-169
- `Bundle-SymbolicName`: R-170
- `com.sap.it.script.v2.api`: R-189
- `PIP`: R-040
- `partnerID`: R-043, R-053
- `SenderInterface`: R-043
- `sender~interface`: R-044
- `CustomXPid_SwitchOffTilde`: R-044, R-045, R-046
- `receiverDetermination`: R-047, R-053
- `interfaceDetermination_<receiver>`: R-047
- `pd:${header.partnerID}:receiverDetermination:Binary`: R-053
- `skipRcvDet`: R-048, R-049, R-095
- `CustomXRDEndpoint`: R-050, R-052
- `ReuseXRDEndpoint`: R-051, R-052
- `customHeaderProperties`: R-090
- `dc_(.)`: R-090, R-092
- `tenantStage(.*)`: R-091
- `skipRetry`: R-094, R-095, R-096
- `stopRetry`: R-096
- `SkipRetriesGlobal_Enabled`: R-101
- `SkipRetriesGlobal_Source`: R-102
- `SkipRetriesGlobal_SourcePidProfile`: R-103
- `SAP_Integration_Suite_SkipRetryGlobalProfile`: R-103, R-113
- `SkipRetriesProfile`: R-106, R-107, R-108
- `CustomXSkipRetriesProfile`: R-106, R-179, R-180
- `CustomXSkipRetryProfile`: R-179
- `Default` (skip-retry source): R-102, R-104, R-105, R-110, R-177
- `ValueMapping` (skip-retry source): R-110, R-177
- `PartnerDirectory` (skip-retry source): R-177
- `PartnerDirectoryGlobal`: R-176
- `PartnerDirectoryScenario`: R-176
- `com.sap.xi.mapping.camel.XiMappingException`: R-104
- `com.sap.aii.mappingtool.tf7.IllegalInstanceException`: R-104
- `<credential-alias>`: R-115
- `bckAttachmentMetadata`: R-125
- `CI_ODataAPI_TokenURL`: R-127
- `CI_ODataAPI_URL`: R-127
- `CI_ODataAPI_UserCredentials`: R-127
- `MessageProcessingLogs('<MPL ID>')/ErrorInformation/$value`: R-128
- `originalPayloadSizeBytes`: R-134
- `convertedPayloadSizeBytes`: R-134
- `PayloadSizeMeasurement`: R-136, R-137
- `originalPayloadSizeMeasurementEnabled`: R-137
- `<key>_<businessSystem>`: R-139
- `<key>_<receiverAliasOrReceiver>_<interface>`: R-142
- `pidParameterReadSuffixPattern`: R-144
- `aliasKeys`: R-146
- `scenarioKeys`: R-146
- ID Mapper: R-147, R-150
- `PassMessageIdFromE2E`: R-148
- `SAP_SenderMessageIdEx`: R-148
- `SapSplitExpression`: R-148
- `UniqueID`: R-149, R-153, R-155, R-157, R-161
- `SapMessageIdEx`: R-149
- `SapMessageId`: R-155
- `SAP_ReceiverInterface`: R-153
- `IDMapper1`: R-154
- `CamelExceptionCaught`: R-164
- `sequenceFlow`: R-033
