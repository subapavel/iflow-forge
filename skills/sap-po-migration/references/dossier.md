# Migration dossier

Optional sidecar for exact approval scope and append-only history (MIG-23/P1). It supplements dated plan rounds and preserves explicit whole-plan approval. Opt-in v2 adds scoped questions and milestone readiness; partial approval never starts phase 5. [page: skills/sap-po-migration/references/plan.md#Approval] [page: skills/sap-po-migration/references/readiness.md#Readiness and approval]

## Files and ownership

Copy `templates/dossier.json` (v1 history) or explicitly opt into `templates/dossier-v2.json` (readiness) in the migration's private directory. Both templates start empty. Keep immutable snapshots, explicitly chosen prior ledgers and generated views private, under gitignored `local/` or the owner's existing external directory. Tracked files contain only synthetic examples and neutral `M-<n>` codes. Reports contain supplied text/paths and remain private.

The coordinator owns decisions, ledger updates, the next question and next action. Specialist authors still produce technical content and formal migration outputs; source, tests and knowledge pages retain their assigned owners. Exactly one writer operates at a time. [page: AGENTS.md#Roles]

For an imported legacy decision, record its evidence locator and known date, propose its exact scope, then append `approval-unresolved` if explicit approval cannot be established. Use `unknown` for an unknown date. Never infer approval from an old analysis, recommendation or accepted risk. The owner still decides. [page: skills/sap-po-migration/references/questions.md#Proposed answers and evidence]

## Input v1

The root has exactly `formatVersion` (integer `1`), `migrationId` (`M-` followed by a positive integer), and `events` (array, possibly empty). Unknown/missing fields, wrong types, duplicate JSON keys and duplicate event IDs are rejected; values are not coerced.

Every event has exactly the common fields `id`, `type`, `at`, `source` plus its type's fields below. Strings are nonblank. `id` is unique across events. `at` is an ISO date, an ISO datetime with offset, or `unknown`. `source` locates an answer or evidence; it is not proof of authentic approval and is not automatically opened. Events are applied in array order, not date order; references name earlier valid proposals.

| type | Additional required fields | Meaning |
|---|---|---|
| `propose` | `text`, `scope` | Event ID becomes the decision ID. |
| `approve` | `decision`, `scope` | Approve only these exact proposed scopes. |
| `reject` | `decision` | Reject a never-approved proposal. |
| `defer` | `decision` | Defer a never-approved proposal. |
| `approval-unresolved` | `decision` | Approval remains unestablished. |
| `supersede` | `decision`, `replaces`, `sections` | Approved successor replaces the old decision in named sections. |
| `risk-accepted` | `text`, `sections` | Separate risk record; grants no approval. |
| `derived-document` | `documentId`, `path`, `required`, `consumes` | Register a document and its dependencies; `required` is boolean. |
| `next-action` | `text` | Latest valid event supplies the next action. |

`scope` is a nonempty array of objects with exactly `sectionId`, `revision`, `path`, `sha256`. A section appears at most once in a scope array. The same section/revision must always name the same path and digest; changed content needs a new revision. SHA-256 is 64 hex characters, compared case-insensitively, calculated from raw file bytes.

`sections` is a nonempty array of distinct known section IDs. `consumes` is a nonempty array of objects with exactly `decisionId`, `sectionId`, `revision`, `sha256`. Paths are local file paths: relative to the current dossier's directory, or absolute for existing private evidence. URI paths are rejected; the checker downloads nothing.

## Approval and current state

An approval's scope must be an exact subset of its proposal. Approval of one section/revision never covers neighbouring sections or later revisions. Repeating an active approval adds evidence; a superseded scope cannot be reapproved. A rejected, deferred or unresolved proposal may later receive explicit approval. Once any scope has been approved, global reject/defer/unresolved is invalid; append a new proposal for a change.

A successor proposal alone changes nothing. Approve the successor, then append `supersede`, naming the old decision and every replaced section. Both decisions must have active approval for each named section. Replacement is atomic, preserves other sections, and forbids self-reference and cycles. Two remaining active decisions for one section conflict, even if their contents agree.

Statuses are `proposed`, `deferred`, `rejected`, `approval-unresolved`, `approved`, `partially-approved`, `superseded`, `partially-superseded`. The checker verifies existence and digest of every ever-approved snapshot, including replaced revisions. Unapproved proposal snapshots are not verified approvals. Keep old snapshots immutable; append history instead of rewriting it. [page: skills/sap-po-migration/references/plan.md#Iterations]

For derived documents, the latest valid registration of a `documentId` wins; older events remain. `current` means a regular file exists and every dependency matches an active approved decision/section/revision/digest. Otherwise it is `missing` or `stale`: an error when required, a warning otherwise. This checks declared dependencies, not prose meaning. Register scenario specifications with their consumed revisions; the generated current view only carries `ledgerSha256` and is not its own authority. [page: skills/sap-po-migration/templates/scenario.md#Approval]

## Check and baseline

```text
po-xray dossier-check dossier.json --baseline snapshots/dossier-r1.json
po-xray dossier-check dossier.json --baseline snapshots/dossier-r1.json --json
```

The command reads inputs without modifying them. Markdown is the default stdout; `--json` emits `DossierReport`. Summary/errors go to stderr. Exit `0` means valid, including warnings; `1` means invalid or an input/parse error, without a stack trace. Save output only to a separate private file. Snapshot creation and baseline selection remain the coordinator's responsibility; the checker never picks the newest file.

The baseline must share identity and its entire event array must be an unchanged structural prefix. Versions must match, except explicit v1 baseline→v2 current upgrade, which emits `HISTORY_FORMAT_UPGRADED`; downgrade fails. Identical histories pass. Object key order/whitespace do not matter; array order/values do. All evidence resolves from the current dossier, regardless of baseline location. The checker never upgrades a file itself.

`historyStatus` is `verified`, `changed`, `invalid-baseline`, or `not-checked`. Omitting `--baseline` produces `HISTORY_NOT_CHECKED`; a valid report then has no verified history. `verified` proves only prefix preservation against that chosen baseline, not correctness or authenticity of earlier approvals.

## Report and diagnostics

`valid` means no error diagnostics, not readiness. An invalid projection is non-authoritative. Default output remains `DossierReport`: digest, decisions/scopes/approvals, separate risks, derived documents, next action and diagnostics, with existing JSON shape/order. Its check validates v2 event semantics too, but exposes no readiness fields. `--readiness` or `--milestone <id>` emits the separate assessment; neither proves runtime evidence or authentic consent. [page: skills/sap-po-migration/references/readiness.md#Checking and limitations]

Diagnostics have `severity` (`error`/`warning`), `code`, and `message`. JSON includes `eventId` as a string when assigned; otherwise that field is omitted (the Java value is null):

| Codes | Meaning |
|---|---|
| `INVALID_DOCUMENT`, `UNSUPPORTED_FORMAT`, `DUPLICATE_EVENT_ID` | Invalid schema/version or repeated ID. |
| `INVALID_REFERENCE`, `INVALID_TRANSITION`, `INVALID_SCOPE` | Invalid reference, state change or scope. |
| `APPROVAL_CONFLICT`, `SUPERSESSION_CYCLE` | Conflicting active approvals or cyclic replacement. |
| `SNAPSHOT_MISSING`, `SNAPSHOT_CHANGED` | Approved evidence unavailable or changed. |
| `DERIVED_MISSING`, `DERIVED_STALE` | Derived file absent or dependencies outdated. |
| `HISTORY_NOT_CHECKED`, `HISTORY_CHANGED`, `INVALID_BASELINE` | Baseline absent, prefix changed, or unusable baseline. |
| `HISTORY_FORMAT_UPGRADED`, `ANSWER_BASIS_CHANGED` | Warnings: preserved v1→v2 prefix; consumed resolution changed. |
| `MILESTONE_CYCLE`, `LEGACY_IMPORT_INVALID` | Invalid prerequisite graph or historical basis import evidence. |

Malformed/unreadable main JSON, duplicate JSON keys and trailing JSON tokens fail before a report is produced. Diagnostics do not reproduce snapshot payloads.
