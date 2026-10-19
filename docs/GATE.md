# Gates

One gate per part. Autonomous execution stops at `GATE_MET` when every check of the current gate
holds, each verified by the command or file given and recorded in `HANDOVER.md`.

## KB-GATE — part 1, "an agent with only the skill can plan a pipeline migration"

The done criteria of `docs/superpowers/specs/2026-10-19-pipeline-knowledge-base-design.md` §9,
restated as checks:

1. **Coverage.** Every generic iFlow and every template of the current source label has its page
   under `knowledge/pipeline/iflows/` or `templates/`, every referenced script a page under
   `scripts/`. Verified by `mvn -B -ntp verify` with no `-Dkb.skip` (the coverage test green).
2. **Contracts.** `contracts.md` and `partner-directory.md` cover every header, property, queue,
   endpoint and partner-directory key that appears in the artifacts, each with where it is set and
   where it is read. Verified by the contract-names test and the kb-reviewer's `PASS` on KB-07.
3. **Changelog.** `versions/<label>/changelog.md` lists every difference between the two source
   labels, built from the artifact diff and SAP's `CHANGELOG.txt`. Verified by the kb-reviewer's
   `PASS` on KB-10.
4. **Placeholders.** `naming.md` lists every `{{tenant.*}}` placeholder used anywhere in the
   knowledge base. Verified by the placeholder test green.
5. **Benchmark.** A fresh session with only the skill loaded answers the owner's questions and
   produces a plausible proposal for the synthetic ICO; results recorded in
   `knowledge/pipeline/benchmark/results-<date>.md`, every answer correct or partial with a
   documented reason. The owner judges; until the owner has judged, the gate row stays
   `in-progress` and the campaign state is `IDLE_NO_READY_TASK`.
6. **Inputs mined.** Every claim in `knowledge/inputs/claims/triage.md` has a disposition.
7. **No customer data.** `-Dkb.forbidden` run green locally by the owner (`ENVIRONMENT_BLOCKED` in
   the cloud is acceptable only with the owner's local `VERIFIED_PASS` recorded in PROGRESS).
8. **Reviews.** Every task of the phase has `review: PASS`; every expensive-to-reverse decision is
   marked `draft — review at hand-over`; every deviation has a finding.

### HANDOVER.md contents for KB-GATE

How to load the skill and run the benchmark; the scenario attribute set as decided (draft); every
deviation from the spec with its finding id; decisions marked `draft — review at hand-over`; open
issues; the next three tasks (part 2 start).

## Later gates

`PROFILE-GATE` (part 2), `READER-GATE` (part 3), `GENERATOR-GATE` (part 4), `CONVERSION-GATE`
(part 5) are written when their parts are designed; until then their phase rows stay `pending`.
