# Legacy import

Import an existing pilot's decisions and questions into a dossier without inventing approval or wording and without a new event type, state or field.

## Conservative pass

1. For each legacy decision, record its evidence locator and the date the original states: `unknown` when it states none, never a file date.
2. `propose` its exact scope. Append `approve` only where the original records the owner's approval explicitly; otherwise append `approval-unresolved`.
3. Never infer approval from an old analysis, recommendation, accepted risk, or "direction", "correction" or "preference" wording. The owner still decides. [page: skills/sap-po-migration/references/questions.md#Proposed answers and evidence]
4. Propose only decision excerpts. List recommendation, evidence and context excerpts in a private import report.
5. Every record the dossier cannot hold goes to that report with its reason.

## Narrow excerpts

1. An excerpt is one heading's lines, or a contiguous line range inside one heading when the heading holds more than one statement unit (two decisions, or a decision and a recommendation).
2. Line 1 of an excerpt names the original path, heading and line range. The excerpts of one original contain each of its lines exactly once.
3. Write each excerpt once; never regenerate it. The same section/revision always names the same path and digest. [page: skills/sap-po-migration/references/dossier.md#Input v1]
4. Where the units interleave so that no contiguous range separates them, keep the whole heading as one excerpt, import it `approval-unresolved` even when the original approves it (this overrides the conservative pass), and mark it "mixed" in the report.
5. Give independent decisions their own sections.
6. A successor the original records as replacing an earlier decision uses the earlier decision's section ID with a new revision and its own excerpt path, so `supersede` names that shared section. [page: skills/sap-po-migration/references/dossier.md#Approval and current state]
7. A replacement of only part of one statement unit cannot be expressed; it goes to the report.

## Split questions

1. A question the original records as partially answered, partially deferred or partially resolved stays one open question with the original wording.
2. Split only parts the original words separately. Each part becomes its own `question-open` whose `text` is the original's words for that part, and whose `topic`, `owner`, `expectedEvidence`, `sections`, `classification`, `classificationSource`, `classificationReason` and `dependencies` are copied from the parent. [page: skills/sap-po-migration/references/readiness.md#Sections and dependencies]
3. Then append `question-answer` (`text` = the original's answer words, `evidenceSource` = the original's path and line range, `replaces` null) or `question-defer` with the original's reason and the review event or date it names as `reviewAt`.
4. Without a stated reason or review trigger, do not defer: the part stays open and the report says why.
5. Parts not worded separately are not split: the parent stays open and the report says so.
6. After the part events, append a new `question-coverage` for every affected section (every section when the parent is unclassified with empty sections), listing all currently applicable questions of that section, parent and parts included. [page: skills/sap-po-migration/references/readiness.md#Inventory and answers]
7. The parent still blocks what it blocked.

## Owner confirmation

1. After the conservative pass, write a private confirmation list. It holds each `approval-unresolved` decision, and each still `proposed` scope of a partially approved multi-section decision (which cannot take `approval-unresolved`). [page: skills/sap-po-migration/references/dossier.md#Approval and current state]
2. For each item give its decision ID, section, revision, excerpt path, the original's wording, why it is unresolved, a "mixed" flag where a whole heading was kept (the scope then includes the recommendation), and the supersession pair if the original records one. An old decision needs confirmation only so its supersession can be recorded.
3. Ask the owner once, as one question, to write a private confirmation file themself. An agent never writes or transcribes it.
4. Line 1 of the file is `date: <ISO date>`. Each further line is `<decision-id> confirmed` ("I approve this exact scope now") or `<decision-id> rejected`. Any other line, and any decision without a line, counts as unclear.
5. Record the file's SHA-256 in the report.
6. For each confirmed decision append `approve` with its exact scope, `at` = the file's date (else `unknown`), `source` = the file path and line.
7. Then append `supersede` where the original records the supersession and both decisions now have active approval on the shared section.
8. For each rejected decision append `reject` unless any of its scope is approved; then it stays as is and the report says so.
9. Unclear decisions stay `approval-unresolved`. Never "confirm all"; never mark an item the owner skipped.

## Outside the dossier

1. Evidence, review logs, provenance and tool status enter only through a packaged, independently reviewed, registered and used evidence packet; otherwise they stay in the import report. [page: skills/sap-po-migration/references/evidence.md#Packet and review]
2. Question rewording, hypotheses, test plans and verification procedures stay in the import report.
