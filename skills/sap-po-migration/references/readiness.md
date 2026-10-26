# Scoped milestone readiness

Opt in with `templates/dossier-v2.json`; retain dated plan rounds and explicit whole-plan approval. V1 remains supported but returns `not-assessed`: missing classification or inventories never means ready. Preserve historical questions, decisions and consent. [page: skills/sap-po-migration/references/plan.md#Approval]

## Input v2

V2 retains the root, common event fields and P1 events of [page: skills/sap-po-migration/references/dossier.md#Input v1], changing only `formatVersion` to integer `2`. New types are rejected in v1. Unknown/missing fields, duplicate keys/IDs and wrong types fail; invalid compound events apply atomically. References name earlier valid entities/events. Strings are nonblank; ID arrays have unique entries and retain order. All fields below are required; only `replaces` is nullable.

| type | Additional fields |
|---|---|
| `section-declare` | sectionId, text |
| `milestone-declare` | milestoneId, kind, sections, scope, connections, connectionRationale, requires |
| `question-open` | text, topic, owner, expectedEvidence, sections, classification, classificationSource, classificationReason, dependencies |
| `question-classify` | question, sections, classification, classificationSource, classificationReason, dependencies |
| `question-coverage` | sections, questions, owner, evidenceSource |
| `question-answer` | question, text, evidenceSource, replaces |
| `question-defer` | question, reason, reviewAt |
| `assumption-accepted` | question, text, owner, approvalSource, scope, milestones, verifyAtMilestoneId |
| `scope-basis` | scope, answers |
| `scope-basis-import` | scope, answers, approval, legacyPath, legacySha256, evidenceSource |
| `risk-detail` | risk, owner, approvalSource, scope, limitations, triggers, milestones, questions |
| `risk-reopen` | risk, triggerType, evidenceSource, text |
| `milestone-approve` | milestone, definition, owner, approvalSource, inputs |

`sections`, `requires`, `questions`, `milestones`, `triggers`, `inputs` are string arrays. `scope` and `connections` contain exact Dependency objects `{decisionId,sectionId,revision,sha256}`; basis events use one object, others arrays. `dependencies` contains `{sectionId,neededByMilestoneId}`; `answers` contains `{questionId,resolutionEventId}` with unique questions. Other fields are strings, except nullable `replaces`. Entity IDs can differ from their registration event IDs; event IDs remain globally unique.

## Sections and dependencies

P1 proposals or `section-declare` introduce sections; repeated declarations fail. Milestone kinds: `design`, `final-plan`, `specification`, `implementation`, `deployment`, `verification`. Its nonempty `sections` lists declared parts; `scope` pins at most one revision per part. Design may omit pins; other kinds require every part pinned. `connections` may be empty or contain distinct known sections outside the parts; `connectionRationale` always explains the wiring or its absence.

**Effective sections = parts UNION connection sections.** Every inventory, question, assumption, risk, basis and blocker membership check uses this union. Consumed connections need approved content and their own current inventory. Declared wiring is not proof of semantic completeness.

`requires` lists earlier milestones, independently of kind order: verification can precede deployment. Cycles/self-reference fail. Latest valid milestone declaration wins; replacing it invalidates its approval and downstream approvals. It cannot strand an existing question dependency. Introduction order and earlier definitions remain preserved.

The `question-open` event ID identifies the question. `topic` preserves legacy blocking/version/conversion/naming labels; it does not determine blocking. `classification` is:

- `scoped`: nonempty dependencies; each section is affected and contained by its named milestone.
- `nonblocking`: empty dependencies, explicit classification source and reason.
- `unclassified`: empty dependencies; affected sections block assessment. Only this class permits empty sections, meaning unknown scope blocks assessment everywhere.

Append `question-classify` to change classification/affected sections. It cannot remove an accepted assumption's verification dependency or silently detach a consumed answer basis. Open scoped questions block their named milestone and downstream dependants; independent work may continue.

## Inventory and answers

Every effective section needs current `question-coverage`, even in a new dossier. Its nonempty sections and question list must exactly inventory all currently applicable questions, including unknown-scope unclassified questions; owner and evidence locator are mandatory. An empty question list explicitly records evidenced “none found”, not proof that no unknown fact exists. Latest coverage wins per section; each projection retains only its applicable subset. Opening/moving questions can stale affected inventories. Missing/stale coverage means `not-assessed`, never ready; current coverage does not clear question blockers.

First answer: `replaces:null`; correction: exact latest answer event ID. Both need evidenceSource; an answer record alone proves no tenant observation. Deferral requires reason and reviewAt (named event/date), preserves unknown/dependencies and cannot replace an answered question. No scheduler, clock or automatic answer exists.

## Assumptions, bases and risks

An assumption requires explicit owner acceptance and approvalSource, exact proposed scopes within the question, nonempty permitted design milestones covering them, and an existing non-design verification milestone covering the same sections. It excuses only those design dependencies, leaves the question unknown and creates a verification dependency there and downstream. It never supplies technical evidence or waives verification.

`scope-basis` binds once, before first scope approval, to nonempty current answers or applicable accepted assumptions affecting that section. Consumed resolutions require a current basis for complete content. Rebinding is invalid; changed answers require a new revision and approval. Old approvals remain historical, visibly stale for use. Stale bases propagate to derived documents: required error, optional warning.

The narrow `scope-basis-import` exception adds a first basis to a still-active exact scope approved in an immutable v1 snapshot. Its identity and full events array must match a preserved prefix before import; legacyPath and legacySha256 are checked each run. `approval` names that snapshot's matching approval; only first answers with `replaces:null` qualify, never corrections/assumptions. evidenceSource locates their historical relationship to consent; no new consent is inferred. Comparable dates reject answers later than approval; unknown or mixed precision stays uncertain. Second imports/rebindings fail; later answer replacement stales this basis. Changed/forged/non-prefix evidence gives `LEGACY_IMPORT_INVALID`; locator authenticity is not proven.

`risk-detail` enriches one earlier `risk-accepted` once: exact scopes within its sections, owner, approvalSource, limitations, nonempty questions/milestones covering that scope, and triggers from `new-evidence`/`requirement-change`. Only this detail excuses specified unresolved dependencies at those exact milestones, including an assumption's verification debt. The fact stays unknown; the exception is visible and never transfers downstream. Bare P1 risks grant no exception. `risk-reopen` needs new evidence and an allowed trigger; repeated reopening or phase-change fails. Reopening restores the block; reacceptance requires a new risk ID and approval.

## Readiness and approval

| Value | Meaning |
|---|---|
| `canWork` | Valid dossier, current inventories, classified/answered or explicitly excused dependencies, ready prerequisites. Design may lack selected/approved revisions or bases; other kinds require complete approved content. |
| `complete` | Dependencies clear; all required scopes/connections active, approved, current, digest-valid and based on current resolutions. Unpinned design is incomplete. |
| `approved` | Current milestone approval binds current definition and exact consumed inputs. |
| `ready` | Design: canWork. Verification: complete. Final-plan: complete AND approved. Specification/implementation/deployment: complete AND a ready final-plan ancestor. |

That **one final-plan ancestor** must cover every exact required scope and connection (decision, section, revision, digest). Matching section names or combining unrelated partial plans is insufficient. Partial approvals never replace whole-plan approval.

`approvalInputs` deterministically deduplicates current definitions through prerequisites, consumed scope approvals/bases, coverage events, applicable latest classifications, answers or exact exceptions, and present prerequisite milestone approvals. `milestone-approve.inputs` must equal that entire set (order irrelevant, duplicates invalid), bind the current definition, and follow complete content. Changed consumed inputs stale approval; unrelated changes do not. The latest applied approval ID remains visible when stale; reapproval is explicit.

## Checking and limitations

`po-xray dossier-check dossier.json --readiness --json` emits the assessment; `--milestone <id>` implies readiness and exits 0 only for a valid dossier and ready selected milestone. Unknown IDs/v1 selections exit 1. Without selection exit follows validity, not readiness. Without either option the P1 output/exit contract stays unchanged. Public APIs, exact output fields and diagnostic codes: [page: docs/OUTPUT-MODEL.md#Dossier readiness assessment].

Only v1→v2 with the same identity and exact structural event prefix is an allowed version upgrade against an explicit baseline; it emits `HISTORY_FORMAT_UPGRADED`. Downgrades fail. The checker edits nothing, opens no approval-source locator, proves no runtime behavior or wiring completeness, and grants no tenant operation permission. Reports/evidence remain private. Invalid dossiers cannot affirm any readiness boolean.
