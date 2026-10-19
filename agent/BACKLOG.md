# Backlog

One row per task. The architect takes the first `ready` row (all depends-on `done`), never
invents work outside this table without adding a row with a reason, and sets `done` only with a
`review: PASS` in PROGRESS.md. Status: `pending | ready | in-progress | done | blocked`.
Evidence = commit hash + PROGRESS entry. Gates are rows; the next phase stays `pending` until the
gate row is `done`. `owner` in depends-on = an input only the owner can give (WORKFLOW §1.2).
Task detail (files, interfaces, steps, tests) lives in the plan:
`docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md` (KB rows map to its Tasks 1–13).

## Phase KB — part 1, pipeline knowledge base (spec: `docs/superpowers/specs/2026-10-19-pipeline-knowledge-base-design.md`)

| Id | Title | Plan | Depends on | Kind | Status | Done when | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| KB-00 | Task 0: local data collection, scrubbing, staging, push — sources `2026-06-trial` and `2026-09-current`, English inputs | Task 0 | — | local | done | `knowledge/sources/` and `knowledge/inputs/en/` committed, no customer data | c4329e3, 0554b80 (with the owner, before bootstrap) |
| KB-00a | Clean catalogue download of the four pipeline packages → `knowledge/sources/2026-10-catalog/` (replaces `2026-09-current`, expected diff empty); switch `kb.sources` | Task 0 step 7 | owner, KB-02 | local | pending | new label committed; `INDEX.md` diff against `2026-09-current` empty | |
| KB-00b | Benchmark questions (~20, with expected answers) → `knowledge/pipeline/benchmark/questions.md` | Task 0 step 8 | owner | local | pending | file committed | |
| KB-01 | Knowledge base skeleton and skill wrapper (`knowledge/pipeline/` stubs, `.claude/skills/sap-is-pipeline/SKILL.md`, README paragraph) | Task 1 | — | content | done | every file of plan Task 1 exists; `git status` clean of `testdata` | PROGRESS KB-01 (hash in the next entry) |
| KB-02 | iFlow reader in `po-xray-core` (`io.poxray.cpi`: `IFlow`, `Participant`, `Step`, `Param`, `IFlowReader`, `PackageExport`, `PackageUnpacker`, `IFlowDump`), CLI `iflow` and `unpack`, native registration, `SourcesAcceptanceTest` | Task 2 | — | code | done | plan Task 2 tests green; acceptance test reads 29 artifacts of `2026-06-trial`; CLI dump of `generic-pipelines` without stack trace; byte-identity of `unpack` is the owner's local check (`NOT_RUN` in the cloud is accepted) | branch claude/eager-brown-uw61ft, PROGRESS KB-02 (hash in the next entry) |
| KB-02b | GitHub Actions workflow `mvn -B -ntp verify` on every push to `main` (JDK 25) | — (D-011) | KB-02 | code | done | workflow green on `main` | PROGRESS KB-02b (hash in the next entry; GitHub run recorded there) |
| KB-03 | Module `iflow-forge-knowledge`: `KnowledgeBase`, `Checks` (coverage, headings, placeholders, names, budget, forbidden), `ChecksTest`, six real-KB test classes, `-Dkb.skip` / `-Dkb.forbidden` | Task 3 | KB-02 | code | done | `mvn -q -pl iflow-forge-knowledge -am test -Dkb.skip=coverage,headings` green; `ChecksTest` covers every check positive and negative | PROGRESS KB-03 (hash in the next entry); F-2 |
| KB-04 | Pass A: 10 generic iFlow pages (`iflows/<id>.md`), ten-heading contract, every fact cited, drafts from `inputs/en/confluence-2026-10` | Task 4 | KB-01, KB-03 | content | done | pages exist for every id under `generic-*`; headings test green; kb-reviewer PASS per batch | 25f362c, 67862af, 598e578, PROGRESS KB-04; F-3 |
| KB-05 | Pass A: template pages (`templates/<id>.md`) incl. script collection, value mapping, upload helper | Task 5 | KB-04 | content | done | pages for every id under `templates`; coverage failures mention only `scripts/` | 7dabf99, 0e659e2, 876f706, 04422e7, B5 (hash in the next entry), PROGRESS KB-05; F-3 ext., ISSUE-1; value mapping + upload helper pages after KB-00a (D-019) |
| KB-06 | Pass A: script pages (`scripts/<stem>.md`) for every distinct Groovy/XSLT resource | Task 6 | KB-05 | content | in-progress | full `verify` green with no `-Dkb.skip`; the pom default `kb.skip=coverage` (F-2) cleared | |
| KB-07 | `contracts.md`, `partner-directory.md`; `Checks.contractNames` + `ContractNamesTest` | Task 7 | KB-06 | code+content | pending | contract-names test green; kb-reviewer PASS | |
| KB-08 | `naming.md` (placeholder list), `versions/README.md`, `glossary.md` | Task 8 | KB-07 | content | pending | placeholder test green; kb-reviewer PASS | |
| KB-09 | Local tenant comparison → extension points (runs locally with `testdata/`, not in the cloud) | Task 9 | KB-04, owner | local | pending | `deviations-czint.md` written locally; only `iflows/*` *Extension points* changed in git; `-Dkb.forbidden` green | |
| KB-10 | Pass B: claims from `inputs/en/` (Appendix A prompt), triage table, enrichment, `pitfalls.md`, `versions/<label>/changelog.md` from artifact diff + SAP change logs | Task 10 | KB-08 | content | pending | every claim has a triage row; changelog covers every difference between the two labels; kb-reviewer PASS | |
| KB-11 | Pass C: scenario attribute set (D-draft), `scenario-contribution.md` decision table, `migration/po-ico-to-pipeline.md`, `migration/is-iflow-to-pipeline.md`, `proposal-template.md`, `conversion/README.md`, `overview.md` with Mermaid, final `SKILL.md` | Task 11 | KB-10 | content | pending | budget test green (loaded set ≤ 5,000 words); kb-reviewer PASS; attribute set recorded as `draft — review at hand-over` | |
| KB-12 | Benchmark: `questions.md` in place, `sample-ico.json` + `BenchmarkFixtureTest`, `expected-proposal.md`, fresh-session run, `results-<date>.md`, fixes | Task 12 | KB-11, KB-00b | code+content | pending | results recorded; every answer correct or partial with reason (owner judges) | |
| KB-13 | ISSUE-1 (owner: option a): port `tools/gen_bpm_builtin_functions.py` to a Java tool in `po-xray-cli` (same JSON output, `BuiltinFunctionCatalog` and `docs/BPM-BUILTIN-FUNCTIONS.md` updated), delete it and `docs/prototype/*.py` | — (ISSUE-1) | KB-06 | code | pending | no `.py` file tracked; red test on a synthetic plugin fixture green; `mvn verify` green; output byte-identity on the real NWDS plugins is the owner's local check (`NOT_RUN` in the cloud accepted) | |
| KB-GATE | Part 1 gate: `docs/GATE.md` KB-GATE checks 1–8 verified; `HANDOVER.md` written; spec status line updated | Task 13 | KB-09, KB-12, KB-13 | gate | pending | gate-reviewer `gate: MET` | |

## Phase PROFILE — part 2, tenant profile (spec to be written; owner's go-ahead first)

| Id | Title | Plan | Depends on | Kind | Status | Done when | Evidence |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PR-00 | Design: profile format (YAML), placeholder binding to `naming.md`, deviations section, anonymized sample; tenant pages from `testdata/pipeline/confluence-2026-10/txt/czint-*` as local input | — | KB-GATE, owner | design | pending | spec approved by the owner | |

## Phases READER, GENERATOR, CONVERSION — parts 3, 4, 5

Rows are added when their specs exist. Part 3 starts from `io.poxray.cpi.IFlowReader` (KB-02).

## Fixes (rows added when `main` is red — WORKFLOW §3: "fix main" is then the only ready task)
