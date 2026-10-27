# Experiment card: `<short question>`

<!-- Private: keep beside the migration's outputs, never committed (SKILL.md, "Local files"). Read references/evidence.md. Replace every <…>. One card per selected experiment. -->

This card plans an experiment; it grants no authorization to call a tenant, deploy, message others or run external operations. Record permission and observed evidence separately if execution is later authorized.

## Selection

At intake, or after a major change, list at most three open uncertainties with the largest impact; fewer when fewer exist.

| rank | question | impact on design | evidence gap |
|---|---|---|---|
| 1 | <Q-<n>> | <affected scope or milestone> | <what is unknown> |
| 2 | <Q-<n> or none> | <impact> | <unknown> |
| 3 | <Q-<n> or none> | <impact> | <unknown> |

- selected: <rank>, because <why investigate this now>

## Card

- question: <Q-<n> and its text, verbatim>
- affected part: <decisionId, sectionId, revision, sha256 — the exact Dependency of the current proposal>
- smallest experiment: <bounded proposed test, local and synthetic where possible>
- expected observation: <the result that distinguishes the options; a property is tested only by observing that property>
- what the result does not prove: <e.g. a successful API call proves no cache, idempotence or retry resilience>
- time and scope limit: <maximum effort; exact environment and data boundary>

## Return path

In this order (references/evidence.md, "PoC results and the design" and "Citing local evidence"):

1. answer: <question-answer correction event ID>, replaces <latest answer event ID, or null for a first answer>
2. new revision: <snapshot path with the cited sentence; propose event ID; revision>, or none only when the affected revision has neither a scope-basis nor an approval and its snapshot already contains the cited sentence and `[evidence: <use-id>]` (changed bytes are a new revision)
3. scope basis: <scope-basis event ID> on <the new revision's exact Dependency>
4. packet, review, register, use: see "Evidence links"
5. readiness check: <date and use state>
6. new approval: <milestone and approval event ID, or pending>

- sections to revisit per result: <result → section IDs>

The result never approves, answers by itself, waives a risk, or raises or bypasses the approved complexity cap. An accepted risk reopens only by `risk-reopen` with trigger `new-evidence` or `requirement-change`.

## Evidence links

| item | value |
|---|---|
| packet path | <private path> |
| review path | <private path> |
| review verdict per claim | <claimId: PASS or FAIL> |
| register event ID | <evidence-register ID, or not registered> |
| use event ID | <evidence-use ID, or not used> |
| readiness state of the use | <supported, superseded, unavailable or proposed-unavailable>, checked <date> |

Cite only a `supported` use, as [Citing local evidence](../references/evidence.md) says; any other state leaves the statement uncited and its question open.
