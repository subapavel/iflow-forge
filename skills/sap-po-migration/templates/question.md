# Q-<n>

<!-- Append to private questions.md. Preserve earlier records; corrections name the exact latest answer replaced. Read references/questions.md. Replace every <…>. -->

## Open-list item

<analysis.md line, verbatim> — topic: <blocking | version | conversion | naming | other>

## Scope and dependency

- owner: <responsible owner>
- expected evidence: <observation or explicit decision needed>
- affected sections: <IDs; empty only for unclassified unknown scope>
- classification: <scoped | nonblocking | unclassified>
- classification source and reason: <locator; why>
- dependencies: <sectionId → neededByMilestoneId pairs; nonempty for scoped>
- inventory: <current question-coverage event(s), owner and evidence locator>

V2 uses exact fields and effective section membership from [page: skills/sap-po-migration/references/readiness.md#Input v2]. A topic is not a dependency. Missing classification or inventory grants no readiness.

## Why the analysis could not settle it

<the report's question, the missing input, or the page that leaves it to a human>

## Options and evidence

| option | evidence | proposed |
|---|---|---|
| <option> | <[page: <path>#<heading>] / <dump field> / <analysis.md row> / no evidence — migrator's call> | <yes, because <evidence> / no> |

## Answer or deferral

- factual status: <open | deferred | answered>
- answer: <actual answer, or unknown>
- evidenceSource: <answer locator; unknown while unanswered>
- replaces: <null for first answer; latest answer event ID for correction>
- deferral: <reason and reviewAt event/date, or none>

Waiting/deferral preserves dependencies; independent work can continue. Never write a proposed assumption as the factual answer.

## Explicit assumption or accepted risk

<none, or separately recorded acceptance: ID, question, text, owner, approvalSource, exact decision/section/revision/digest scope, named milestones>

For an assumption: <permitted design milestones; verifyAtMilestoneId>. For risk detail: <risk ID, limitations, affected questions, triggers new-evidence/requirement-change>. Keep unknown facts and verification debt visible. A named risk waiver is an exception, not technical evidence. [page: skills/sap-po-migration/references/readiness.md#Assumptions, bases and risks]

## Top three uncertainties and PoC card

Review the migration's three highest-impact uncertainties; fewer are allowed when fewer exist.

| rank | question | impact on design | evidence gap |
|---|---|---|---|
| 1 | <Q-ID> | <affected scope/milestone> | <unknown> |
| 2 | <Q-ID or none> | <impact> | <unknown> |
| 3 | <Q-ID or none> | <impact> | <unknown> |

- selected uncertainty and impact: <why investigate this now>
- smallest experiment: <bounded proposed test>
- expected observation: <result that distinguishes the options>
- exclusions: <what this experiment cannot prove or change>
- time/scope bound: <maximum effort and exact environment/data boundary>
- return-to-design scope: <sections/decisions to revisit for each result>

This card plans an experiment; it grants no authorization to call a tenant, deploy, message others or run external operations. Record permission and observed evidence separately if execution is later authorized.

## Consequence

<analysis/profile row changed by the actual answer; separately labelled assumption scope; changed bases/revisions requiring new approval; or none>

## New open-list items

- [ ] <item> — source: Q-<n> — why open: <reason> — type: <blocking | version | conversion | naming>

<or none; refresh affected v2 inventories>
