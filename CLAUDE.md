# iflow-forge — rules for Claude

Read before any work: this file, `AGENTS.md`, `agent/HANDOFF.md` and the task's row in
`agent/BACKLOG.md`. `docs/WORKFLOW.md` §1 is the loop. The binding design of the part being built is
its spec under `docs/superpowers/specs/` and its plan under `docs/superpowers/plans/` — read by
section, the sections the row or the packet names, through the scout, never whole. `docs/GATE.md`
and `docs/DECISIONS.md` only where a row or a packet refers to them. Everything in the repository is
in English.

## What this is

iflow-forge produces SAP Integration Suite iFlows built on the SAP **pipeline concept** from two
inputs: SAP PO Integrated Configurations (read by the po-xray readers in this repository) and
existing point-to-point iFlows. Java 25, Maven, Quarkus + Aesh CLI, MCP server. Five parts, built
in order: (1) pipeline knowledge base as the skill `sap-is-pipeline`, (2) tenant profile, (3)
Integration Suite iFlow reader, (4) generator of importable packages, (5) code conversion of PO
modules and Java mappings. The spine is a **scenario specification**: both inputs produce it, a
human approves a proposal built on it, the generator consumes it; an agent never hand-writes iFlow
BPMN XML. Output is always an importable package; pushing through the tenant API is optional.

## Design rules (non-negotiable)

- **Java only.** Every tool, check and helper is Java inside the Maven modules with JUnit 5 +
  AssertJ tests, in the style of the existing readers (records, static readers,
  `io.poxray.json.Json`). No Python, no shell logic beyond one-line commands. The owner cannot
  read Python; a Python file is a review failure.
- **No customer data in the repository.** No tenant names, personnel IDs, hostnames, configured
  parameter values, customer documents. `testdata/` is gitignored and is the only place for them;
  tests that need it skip when it is absent (`TestData`, `assumeTrue`). `knowledge/sources/` holds
  SAP packages scrubbed as `knowledge/sources/README.md` describes: never `parameters.prop`, never
  `Configured_Custom_Tags.txt`, no tenant suffix in a symbolic name.
- **Knowledge is files, derived from artifacts.** A statement about pipeline behaviour in
  `knowledge/pipeline/` cites the artifact (`[<symbolic-name>, step "<name>"]` or
  `[scripts/<file>#L<n>]`) or is not written; it becomes an open question in `pitfalls.md`.
  `knowledge/inputs/` are drafts, never sources of facts. Tenant-specific values are
  `{{tenant.<path>}}` placeholders listed in `naming.md`. Page budgets: loaded set ≤ 5,000 words,
  any page ≤ 1,500 words. The module `iflow-forge-knowledge` enforces what it can on `mvn verify`.
- **Current pipeline version is primary**; older-version differences are inline "(vN: was X)"
  notes plus `versions/<label>/changelog.md` built from artifact diff and SAP's `CHANGELOG.txt`.
- **Readers are lossless and honest.** A reader keeps the raw source next to the parsed model, and
  reports bugs or typos found in the source explicitly in the output, never silently fixed.
  Analysis of a customer's PO is reported neutrally; the tool never recommends changing the
  customer's PO.
- **Library APIs:** Context7 (`.mcp.json`) first — Quarkus, Aesh, Jackson, JUnit; if unreachable or
  ambiguous, `javap -cp <jar>` on the resolved jar in `~/.m2` decides. Never guess an API from
  memory.
- Dependencies: Apache 2.0, MIT, BSD, MPL 2.0, EPL only; SAP artifacts are never redistributed
  beyond the scrubbed package sources already in `knowledge/sources/`.

## Tests (TDD)

- Red first. A code task starts with the failing test that defines its acceptance; the PROGRESS
  entry names it. Then the code. Then cleanup.
- The implementer may not change a test it did not write, except to fix a wrong expectation with
  the reason in the commit message.
- A test asserts observable behaviour on synthetic fixtures built by the test; real SAP sources
  under `knowledge/sources/` are used by acceptance tests only, skipped when the directory is
  absent. No `@Disabled`, no skipped cases, no swallowed exceptions, no `assertTrue(true)`.
- `mvn -B -ntp verify` green at the reactor root is the floor; coverage and mutation floors are not
  set in part 1 (D-012) and are introduced by a decision when the generator (part 4) starts.
- A content task (a knowledge-base page) has no red test; its acceptance is the kb-reviewer's
  `PASS` plus a green `iflow-forge-knowledge` run (`AGENTS.md`, "Page packet").

## Working rules

- The main session is the architect (`AGENTS.md`): it writes the acceptance spec, the task packet
  or the page packet, approves the red tests, decides, commits. It never edits `src/`, never
  writes a test, never writes a knowledge page, never runs Maven — the test-author, implementer,
  kb-author, verifier, reviewer and kb-reviewer do, on Opus 5.5, Sonnet 5.5 and Haiku 5.5.
  Fable 5.1 is the architect and the gate reviewer, nothing else.
- One task = one commit, Conventional Commits, direct to `main`, green before push. Format and
  trailers in `docs/WORKFLOW.md`.
- After every task: PROGRESS.md entry, HANDOFF.md rewritten, BACKLOG.md status updated, review by
  the reviewer or kb-reviewer agent. A task is `done` only with `review: PASS`.
- Decisions: reversible → `docs/DECISIONS.md`; expensive-to-reverse (the scenario attribute set,
  the knowledge-base page contract, public types) → design it, mark it `draft — review at
  hand-over`; owner-only (credentials, a tenant download, customer data, a missed gate) →
  `issues/ISSUE-<n>.md` and continue with the next ready task. Rules in `docs/WORKFLOW.md`.
- When practice contradicts the part's spec: write the finding (`docs/findings/`), make the
  smallest change that keeps the documented intent, edit the spec with a changelog line, flag the
  PROGRESS entry "deviation". Never work around the document silently.
- Never: weaken or skip a test to go green; write a fact without its citation; force-push or
  rewrite history; claim a build, test or push you did not observe; put customer data or a secret
  in the repository; add a Python file.
- Never ask the owner during autonomous execution. If blocked, write the issue and move on. The
  session ends only on `GATE_MET` or `IDLE_NO_READY_TASK` (see `docs/WORKFLOW.md`).

## Context hygiene

- The conversation is not memory. Before each task re-read `agent/HANDOFF.md` and the task's
  BACKLOG row. After a context compaction, treat everything older than the last PROGRESS entry as
  unknown and re-read the files; say "context compacted" in one line of the next PROGRESS entry
  and re-verify with `git status` and `git log -3` before changing anything.
- Keep the context small: `mvn -B -ntp -q` by default; send build and test output to a file under
  `target/` and read only its tail or the failing section; never cat a file longer than ~200
  lines — use offsets, grep, or the scout; never paste a diff you do not need to reason about.
  Never load an `.iflw` file whole; the `po-xray iflow` dump is the reading surface.
- Large reads (surveying modules, finding call sites, reading sources) go to the scout agent; its
  summary comes back, not the files.
- From a delegated task only the handback (at most 40 lines) enters the architect's context. The
  diff goes to `target/<task>.diff` for the reviewer; the architect reads `git diff --stat` and
  the test files it approves, never the whole patch. A knowledge page goes to the kb-reviewer by
  path; the architect reads the kb-reviewer's verdict, not the page.
- `agent/HANDOFF.md` is at most 20 lines of at most 160 characters: state, last commit, next exact
  action, open issues, pointers. Module notes live in `agent/NOTES.md`, read by section on demand.
  A PROGRESS entry is at most 15 lines; the review-round narrative goes to the commit body.
- One task per context episode: finish, commit, push, rewrite HANDOFF, then start the next. Never
  carry an in-flight task across a compaction without a WIP line in `agent/HANDOFF.md`.

## Compact instructions

When compacting, keep: the HANDOFF state line and next exact action, the id and status of the
task in flight, the acceptance spec and the task or page packet as written, the names of the red
tests and whether their red was observed, every evidence line (`VERIFIED_PASS` / `EXECUTED_FAIL`
with its log path), the reviewer's last verdict with its reasons, open decisions and findings by
id. Drop: tool output, build logs, diffs, file contents already committed, and the handbacks of
finished agents beyond their last line. After compaction re-read `agent/HANDOFF.md` and run
`git status` and `git log -3` before changing anything.
