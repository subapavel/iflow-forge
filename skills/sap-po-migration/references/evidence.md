# Local evidence

Optional workflow for a fact the knowledge base does not state, or states differently: a bounded local packet, an independent review, and an opt-in dossier v3 registration and use (MIG-35, MIG-36). It changes no phase entry, exit, approval gate or stop of SKILL.md.

## Purpose and inputs

1. Use it when a design section depends on an observation no page of `knowledge/pipeline/` or `patterns/` states: a newer package release, an existing PoC record, an export, documentation, or an owner's statement.
2. Inputs: the private source and its locator, the exact proposal section snapshot the claim is about, and an opt-in v3 `dossier.json` ([dossier](dossier.md)). Without v3 nothing in a packet is cited.
3. Packets, reviews, [experiment cards](../templates/experiment.md) and export sidecars are private (SKILL.md, "Local files"); a committed file never carries their content, paths or event IDs.

## Source kinds and versions

1. `sourceKind` is one of `export`, `documentation`, `derived`, `owner-confirmed`, `tenant-observed`. It is not a certainty ladder: it says where a statement came from, not how sure it is.
2. `owner-confirmed` never becomes `tenant-observed`. An owner may decide the design; that decision is no trace of technical behaviour.
3. Version roles stay separate; none fills or overwrites another:

| sidecar `role` | packet `versions` field | what it is |
|---|---|---|
| `export-label` | `exportLabel` | the label an export carries |
| `package-release` | `packageRelease` | a pipeline package release |
| `bundle-version` | `bundleVersion` | one artifact's MANIFEST Bundle-Version |
| `target` | `targetVersion` | the version the plan targets |
| `design-time` | `designTimeVersion` | the version seen at design time |
| `deployed` | `deployedVersion` | the deployed version the evidence shows |
| `legacy-installed` | none (sidecar only) | an old `installed` value |

4. Export and read timestamps (`observedAt`, `readAt`, `outputCreatedAt`) are not versions.
5. A newer downloaded release is a `package-release` observation and an "Update available" line; it never overwrites the installed row ([profile](../templates/profile.md), "Installed pipeline versions"). An old `installed` value without locator stays `installed`, never re-labelled `deployed`.

## Packet and review

1. Packet: strict JSON, `formatVersion` 1, fields `id`, `author`, `sourceKind`, `sourceLocator`, `sourceSha256`, `observedAt`, `versions`, `limitations`, `kbDifference`, `claims` (`docs/OUTPUT-MODEL.md`, "Migration helpers").
2. A claim's `text` is the exact sentence the design will carry; its `scope` lists exact Dependencies (`decisionId`, `sectionId`, `revision`, `sha256`) of a proposal that exists when the packet is registered.
3. `sourceLocator` is inert text, never followed. `observedAt` is the source's own stated time or null, never the packaging time.
4. Review: a separate strict JSON file naming `packetId` and `packetSha256`, with `reviewer`, `reviewedAt` and per claim `claimId`, `scope`, `verdict` (`PASS` or `FAIL`), `limitations`.
5. The reviewer differs from the author after trimming and ignoring case. That independence is attributed, not authenticated.
6. A mixed review is normal: a `FAIL` blocks only uses of that claim. A review with no `PASS` makes the packet unavailable.
7. Existing pre-v3 PoC records stay context with their limitations. They support a cite only once packaged as a new packet (`sourceLocator` to the record, `sourceKind` as it really is, `observedAt` the record's own stated time or null), reviewed, registered and used.

## Dossier v3 registration and use

1. `formatVersion` 3 is opt-in: every v1 and v2 event plus two types; [readiness](readiness.md) rules apply unchanged.
2. `evidence-register`: `packetPath`, `packetSha256`, `reviewPath`, `reviewSha256`. Paths are local, relative to the dossier or absolute.
3. `evidence-use`: `scope` (one exact Dependency), `evidence` (an earlier registration ID), `claimId`, `text`, `replaces` (null, or the tail of an earlier same-scope chain).
4. Packet state: `valid-attribution` when the document checks pass (digests, strict parse, packet ID and digest named by the review, independent reviewer) and at least one review claim is `PASS`; `unavailable` otherwise.
5. A use is supported only when the packet's claim has the exact use text and scope and its review claim is `PASS` for that scope.
6. Use states: `supported`, `superseded`, `unavailable`, `proposed-unavailable`. In one `replaces` chain the latest supported node is current, earlier nodes `superseded`, later unsupported nodes `proposed-unavailable`; with none supported the root is `unavailable`. Independent roots are separate requirements. `proposed-unavailable` never blocks.
7. Every packet or use reason (missing file, digest, review identity, `FAIL`, text or scope mismatch, missing `PASS`) is a warning `EVIDENCE_UNAVAILABLE`. Shape errors in an evidence event (unknown field, blank value, URI path, bad digest) are `INVALID_DOCUMENT`; out-of-order events (no earlier proposal for the scope, no earlier registration, a forking `replaces`) and UNC, device or non-default-filesystem paths are `INVALID_EVIDENCE` errors.
8. Milestones: a section with any use, supported or not, gets `EVIDENCE_SCOPE_UNRESOLVED` unless the milestone consumes an exact dependency of that section; a consumed exact scope with any `unavailable` use gets `EVIDENCE_UNAVAILABLE`. Both set `complete` and `canWork` false; approval stays recorded.
9. A basis whose scope has any `unavailable` use, consumed or not, is reported `stale`; that creates no `STALE_BASIS` blocker and stales no derived document.
10. The checker blocks only sections and scopes that have uses: a revision with no use raises no evidence blocker, even if an older revision had a supported use. A supported use lifts no other blocker.

## PoC results and the design

1. At intake or after a major change, pick at most three open uncertainties with the largest impact; plan each selected experiment on an [experiment card](../templates/experiment.md).
2. A result returns through existing events only: a `question-answer` correction naming the answer it replaces; a new section snapshot with the cited sentence and `[evidence: <use-id>]`; `propose` (the new revision); `scope-basis` on that revision (never rebound, [readiness](readiness.md), "Assumptions, bases and risks"); packet with that proposal as claim scope, independent review, registration and use, in the order of "Citing local evidence"; the readiness check; a new approval of the affected milestone.
3. Supported evidence never approves, answers, waives a risk, or raises or bypasses the approved complexity cap, which stays valid without a concrete trigger.
4. An accepted risk reopens only by `risk-reopen` with trigger `new-evidence` or `requirement-change`.
5. A successful API call proves no cache, idempotence or retry resilience; a test of such a property observes that property.

## Export provenance

1. `po-xray export <sca> --provenance-input <descriptor.json>` also writes `<base>.provenance.json` (`ExportManifest`). The descriptor lists `build` and `skill` artifacts and version observations with the roles and kinds above.
2. The sidecar records caller-supplied identity: the files the caller names, measured as they are, not a running build or loaded skill. Observations are kept verbatim, never inferred from one another.
3. `FileDigest` states: `measured`, `unavailable`, `read-time-snapshot` (the descriptor). Warnings `EXPORT_ARTIFACT_UNAVAILABLE` and `EXPORT_ROLE_MISSING` appear in the sidecar only.
4. A failure after the documentation is written leaves the earlier outputs, cleans nothing and exits `1`.
5. The profile's "Tool and skill versions" line names each identity's source kind and locator or sidecar path.

## Citing local evidence

The private cite form is `[evidence: <use-id>]`; `<use-id>` is a dossier v3 `evidence-use` event ID (draft, D-056).

1. Where: only private `plan.md` and `scenario-<k>.md` statements of the exact proposal section the use names (decisionId, sectionId, revision, sha256). In `questions.md` a use may be listed as option evidence when its scope is the affected section's current proposed revision; it never answers the question. `analysis.md` does not use the form.
2. When: the dossier is v3 and `po-xray dossier-check <dossier.json> --readiness` reports that use `supported`. Without a v3 dossier, or with any other state, the statement is uncited and opens a question as today.
3. Text: the cited sentence reproduces the use text verbatim, which equals the packet claim text. Consequences are separate sentences citing a `Q-<n>` or a page. A visible "KB difference" note stays when the knowledge base says otherwise or is silent.
4. Order (the scope sha256 is the snapshot's raw bytes): choose the use ID; write the section snapshot with the cited sentence and `[evidence: <use-id>]`; `propose`; prepare the packet whose claim scope is that proposal; independent review; `evidence-register`; `evidence-use` with the chosen ID; run the readiness check; only then show the round.
5. Editing the sentence later creates a new revision: new packet claim scope, review, registration and use. `replaces` works only within the same exact scope.
6. Rule 1, checked by reading: a statement cites only a use supported for its exact revision.
7. Committed pages show the form only as `[evidence: <use-id>]`. The public cite checker never reads private packets. Generalising a packet fact into `knowledge/pipeline/` is a separate anonymised, cited, kb-reviewed `kb` request ([retrospective](retrospective.md)).

## Limits

1. Byte and reviewer-attribution checks are not source authentication or runtime proof.
2. Any path component Java reports as a symbolic link or other (reparse) entry makes a dossier packet or review unavailable, an opt-in export source or descriptor fail, and a supplied artifact unavailable; an alias-unresolved artifact plus any existing destination fails the whole preflight. Cloud placeholders may be reported as reparse entries; not tested.
3. Mounted or mapped volume locality cannot be authenticated by portable Java.
4. This page, readiness and supported evidence authorize no tenant experiment, deployment, external call or operation.
