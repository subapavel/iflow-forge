# MIG-40 — Legacy import rules: split questions, narrow excerpts, owner confirmation round

Status: revision3 — architect rewrite after preflight r1 FAIL8 and r2 FAIL4 (mirror pointers,
pointer sentence dropping clauses, coverage scope, reject token); preflight r3 PASS, its five
non-blocking notes absorbed. Accepted 2026-10-27: review r1 PASS; see Result.
Origin: F-15 (MIG-38). Owner decision 2026-10-27 (D-058): dossier v3 stays unchanged; F-15 is
resolved by import rules in the migration skill. No new event type, state or field.
Kind: docs (skill reference text only; no code, no test, no page under `knowledge/`).
Read `docs/LOCAL-WORKFLOW.md` first. Follow-up pilot: MIG-41 (below), not part of this task.

## Goal

A migrator importing an existing pilot into a v3 dossier can represent partial deferrals, partial
answers, decisions mixed with recommendations in one heading, and supersessions whose successor the
owner confirms — without inventing approval or wording and without a schema change.

## Facts this packet relies on

- Today's import rule, `skills/sap-po-migration/references/dossier.md:11`, has five clauses:
  record the evidence locator and known date; propose the exact scope; append
  `approval-unresolved` if explicit approval cannot be established; use `unknown` for an unknown
  date; never infer approval from an old analysis, recommendation or accepted risk — the owner
  still decides. Cite: `[page: skills/sap-po-migration/references/questions.md#Proposed answers and evidence]`.
- Later approval is allowed (`dossier.md:37`; `DossierValidator.java:158-196`); global
  reject/defer/unresolved is invalid once any scope of the decision is approved (`dossier.md:37`).
  `supersede` needs active approval of both decisions on each named section (`dossier.md:39`;
  `DossierValidator.java:210`); the conflict check runs on the final state (`:246-251`).
- Same section/revision always names the same path and digest; changed content needs a new
  revision (`dossier.md:31`). `source` is a locator, not proof of approval (`dossier.md:17`).
- Question events and fields: `readiness.md:13-17`; `replaces` null for a first answer and
  `reviewAt` a named event/date (`readiness.md:47`). Coverage must exactly inventory every
  currently applicable question of each effective section, unknown-scope unclassified questions
  included, and must be current (`readiness.md:45`); unclassified questions with empty sections
  apply to every section (`readiness.md:39`). A deferred question still blocks
  (`DossierReadiness.java:442`).
- Cites have the form `[page: <repo path>#<H2 or H3 heading>]` (`Checks.java:65`, `:562-601`).
- Budgets: any skill page ≤ 1,500 words. `dossier.md` 1,211 words; `SKILL.md` 1,358. The files
  `.claude/skills/sap-po-migration/SKILL.md` and `.agents/skills/sap-po-migration/SKILL.md` are
  pointers (frontmatter + one line naming the canonical file); the mirror check compares
  frontmatter and the canonical path only (`Checks.java:457-461`, `:505-518`). No `SKILL.md` row
  routes importing an existing pilot (`SKILL.md:17-22`).
- M-1 replay counts (F-15): 61 of 169 records unresolved; about 13 belong to the gaps below; the
  rest are evidence, reviews, provenance and wording-less records that correctly stay outside.

## Change

1. New page `skills/sap-po-migration/references/legacy-import.md`, ≤ 900 words: `# Legacy import`,
   one intro sentence, then five H2 sections in this order and with these exact headings:
   `## Conservative pass`, `## Narrow excerpts`, `## Split questions`, `## Owner confirmation`,
   `## Outside the dossier`, holding L1–L5 in the skill's imperative style. Cites use the form
   above, at least: `questions.md#Proposed answers and evidence`, `dossier.md#Approval and current
   state`, `readiness.md#Sections and dependencies` and `readiness.md#Inventory and answers`,
   `evidence.md#Packet and review`.
2. `dossier.md:11` is replaced by one sentence: "Import legacy decisions as
   `references/legacy-import.md` describes." with
   `[page: skills/sap-po-migration/references/legacy-import.md#Conservative pass]`. Nothing else in
   `dossier.md` changes. Every clause of the old line moves into L1 (none dropped).
3. One `SKILL.md` routing row: "to import an existing pilot into a dossier" →
   `references/legacy-import.md`. The pointer files are not touched.

## Rules (content of `legacy-import.md`)

- L1. **Conservative pass.** For each legacy decision record its evidence locator and the date the
  original states (`unknown` when it states none, never a file date); propose its exact scope;
  `approve` only where the original records the owner's approval explicitly; otherwise
  `approval-unresolved`. Never infer approval from an old analysis, recommendation, accepted
  risk, or "direction", "correction" or "preference" wording; the owner still decides (cite
  `questions.md#Proposed answers and evidence`). Only decision excerpts are proposed;
  recommendations, evidence and context excerpts are listed in a private import report. Every
  record the dossier cannot hold goes to that report with its reason.
- L2. **Narrow excerpts.** An excerpt is one heading's lines, or a contiguous line range inside one
  heading when the heading holds more than one statement unit (two decisions, or a decision and a
  recommendation). Line 1 names the original path, heading and line range. The excerpts of one
  original contain each of its lines exactly once. Each excerpt is written once and never
  regenerated. Where the units interleave so no contiguous range separates them, keep the whole
  heading as one excerpt, import it `approval-unresolved` even when the original approves it
  (this overrides L1), and mark it "mixed" in the report.
  Independent decisions get their own sections. A successor the original records as replacing an
  earlier decision uses the earlier decision's section ID with a new revision and its own excerpt
  path, so `supersede` names that shared section. A replacement of only part of one statement
  unit cannot be expressed; it goes to the report.
- L3. **Split questions.** A question the original records as partially answered, partially
  deferred or partially resolved stays one open question with the original wording. Split only
  parts the original words separately: each becomes its own `question-open` whose `text` is the
  original's words for that part, and whose `topic`, `owner`, `expectedEvidence`, `sections`,
  classification fields and `dependencies` are copied from the parent. Then `question-answer`
  (text = the original's answer words, evidenceSource = the original's path and line range,
  `replaces` null) or
  `question-defer` with the original's reason and the review event or date it names. Without a
  stated reason or review trigger, do not defer: the part stays open and the report says why.
  Parts not worded separately are not split: the parent stays open and the report says so. After
  the part events, append a new `question-coverage` for every affected section — every section
  when the parent is unclassified with empty sections — listing all currently applicable
  questions of that section, parent and parts included. The parent still blocks what it blocked.
- L4. **Owner confirmation.** After the conservative pass, write a private confirmation list: for
  each `approval-unresolved` decision, and each still `proposed` scope of a partially approved
  multi-section decision (which cannot take `approval-unresolved`, `dossier.md:37`), its decision ID, section, revision, excerpt path, the
  original's wording, why it is unresolved, a "mixed" flag where L2 kept a whole heading (the
  scope then includes the recommendation), and the supersession pair if the original records one
  (an old decision needs confirmation only so its supersession can be recorded). Ask the owner
  once, as one question, to write a private confirmation file themself; an agent never writes or
  transcribes it. Line 1 is `date: <ISO date>`; each further line is `<decision-id> confirmed`
  ("I approve this exact scope now") or `<decision-id> rejected`. Any other line, and any decision
  without a line, counts as unclear. Record the file's SHA-256 in the report. For each confirmed
  decision append `approve` with its exact scope, `at` = the file's date (else `unknown`),
  `source` = the file path and line. Then append `supersede` where the original records the
  supersession and both decisions now have active approval on the shared section. For each
  rejected decision append `reject` unless any of its scope is approved (then it stays as is and
  the report says so). Unclear decisions stay `approval-unresolved`. Never "confirm all"; never
  mark an item the owner skipped.
- L5. **Outside the dossier.** Evidence, review logs, provenance and tool status enter only through
  a packaged, independently reviewed, registered and used evidence packet (cite
  `evidence.md#Packet and review`); otherwise they stay in the import report. Question rewording,
  hypotheses, test plans and verification procedures stay in the import report.

## Allowed paths (writer)

`skills/sap-po-migration/references/legacy-import.md` (new),
`skills/sap-po-migration/references/dossier.md` (line 11 only), `skills/sap-po-migration/SKILL.md`
(one routing row). The writer never edits ledgers, pointer files or agent memory, and never commits.

## Roles and order

0. Architect: `git status --short` baseline to `target/mig-40/status-before.txt`.
1. Writer (one documentation author): the change; hands back word counts of the three pages.
2. Verifier: word-count lines of the three pages before/after; `iflow-forge-knowledge` checks and
   reactor `mvn -B -ntp -q verify`, logged under `target/mig-40/`; exit codes and failing
   sections only.
3. Reviewer (not the writer): the change against this packet, F-15, `dossier.md` "Approval and
   current state", the cited `readiness.md` heading and `evidence.md` "Packet and review": no rule
   contradicts v3, drops a clause of the old line 11, permits inferred approval or invented
   wording, or introduces an event, state or field; L3 keeps the parent open. Two FAILs → the
   architect rewrites this packet.
4. Architect: ledgers, one commit.

## Acceptance

- Reviewer `PASS`; verifier green with logged exit codes; every page ≤ its budget.
- `git status --short` against the baseline: only the allowed paths changed by the writer.
- Commit set (explicit paths; ledgers staged selectively): this packet, the three skill pages,
  `docs/DECISIONS.md` (D-058 row), `docs/findings/F-15.md`, `agent/BACKLOG.md` (MIG-40/41 rows),
  `PROGRESS.md` (MIG-40 entry), `agent/HANDOFF.md`. Never: `.claude/agent-memory/*`, `.codex/`, or
  the uncommitted MIG-07 pilot hunks in `PROGRESS.md` and `agent/BACKLOG.md` (selective blobs;
  check `git diff --cached --stat` and `git diff --cached -- PROGRESS.md agent/BACKLOG.md`).
- No customer, tenant or M-1 detail in the text; examples, if any, are synthetic.

## MIG-41 (follow-up pilot, separate row, needs the owner)

Re-import M-1 privately under L1–L5 into a new audit view (`local/<customer>/audit/M-1-p8/`),
originals read-only, hashes before/after. The owner writes the L4 confirmation file (37 items
today). Report: unresolved before/after, approvals explicit/confirmed/unresolved, `dossier-check`
plain and per milestone. Done when the counts are logged and reviewer PASS; no claim beyond them.

## Out of scope

Any v3 schema or checker change (D-058); linking fields such as `splitFrom`; F-16; MIG-41 itself.

## Result (2026-10-27)

`legacy-import.md` written (795 words), `dossier.md` line 11 replaced by a pointer (1,169 words),
one `SKILL.md` row (1,370 words). Knowledge 72/0/0/2 and reactor 729/0/0/26, exit 0; only the
allowed paths changed. Review r1 PASS. Watch in MIG-41: when the original explicitly approves
both a decision and its successor, the conservative pass leaves two active decisions on one
section (approval conflict) until `supersede` is appended.
