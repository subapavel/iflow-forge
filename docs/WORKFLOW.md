# Workflow — autonomous execution protocol

Agreed on 2026-10-19. Taken from `subapavel/flottila` `docs/WORKFLOW.md` (bounded tasks, evidence
states, handoff, roles) and adapted to a repository where half of the work is content (knowledge
pages) rather than code. This file is binding for every session working on iflow-forge;
`CLAUDE.md` summarises it.

## 1. The loop

1. Read `agent/HANDOFF.md`. Its `state` line decides whether work may start (`RUNNING` or
   `NOT_STARTED`; anything else needs the owner's explicit RESUME). Reconcile an interrupted task
   first: an `in-progress` row in BACKLOG whose commit is not on `main` is redone from its last
   pushed state, never assumed done.
2. Pick the first `ready` row in `agent/BACKLOG.md`. A row is `ready` when every id in its
   depends-on column is `done`. Gates are rows too; nothing in the next phase becomes `ready` until
   the gate row is `done`. A row marked `owner` in its depends-on column waits for an input only
   the owner can give (a tenant download, benchmark questions): if it is missing, write the issue,
   set the row `blocked`, take the next ready row.
3. **Code task:** write the acceptance spec (`AGENTS.md`); the test-author writes the red tests;
   approve them by reading the test files. Red first: the failing test exists, observed red, before
   the code. Then the task packet. **Content task:** write the page packet; no red test.
4. Implement through the implementer (code) or the kb-author (pages). The architect never writes.
   One writer at a time.
5. Verify through the verifier: `mvn -B -ntp verify` green at the reactor root (the knowledge
   module included), plus the checks the row names. The verifier reports exit codes and the
   failing section; the architect reads the log tail itself and records each check with its
   evidence state.
6. Review: code by the reviewer, pages by the kb-reviewer, the material handed over by path.
   `FAIL` reopens the task at step 4; a second `FAIL` reopens it at step 3 with a rewritten packet.
   A gate row is reviewed by the gate-reviewer (Fable 5.1) instead.
7. Commit and push to `main`. Append the PROGRESS entry, rewrite HANDOFF, set the row to `done`.
   These three edits go in the same commit as the code or pages.
8. Continue with the next `ready` row without asking. Stop only on `GATE_MET` (`docs/GATE.md`
   satisfied → write `HANDOVER.md`) or `IDLE_NO_READY_TASK`.

The VM can be paused or reclaimed at any time. Git and `agent/HANDOFF.md` are the recovery point;
nothing that matters may exist only in the conversation or in an unpushed working tree.

## 2. Files

| File | Purpose | Written by |
| --- | --- | --- |
| `agent/BACKLOG.md` | one row per task: id, title, depends-on, status, done-when, evidence | architect |
| `agent/HANDOFF.md` | ≤ 20 lines of ≤ 160 characters: state, last commit, next exact action, open issues, pointers; rewritten after every task | architect |
| `agent/NOTES.md` | module and area notes (shapes, commands, open follow-ups); one section per area, read on demand | architect |
| `.claude/agent-memory/<name>/MEMORY.md` | durable facts an agent learned; never task state | that agent |
| `PROGRESS.md` | append-only journal, one entry per task, ≤ 15 lines (format below) | architect |
| `docs/DECISIONS.md` | reversible decisions, one line each | architect |
| `docs/findings/F-<n>.md` | contradictions between practice and the part's spec | architect |
| `issues/ISSUE-<n>.md` | things only the owner can resolve | architect |
| `HANDOVER.md` | written once per gate, at `GATE_MET` | architect |

Status values in BACKLOG: `pending | ready | in-progress | done | blocked`.

Campaign states in HANDOFF: `NOT_STARTED | RUNNING | BLOCKED | PAUSED_QUOTA | STOPPED_USER |
IDLE_NO_READY_TASK | GATE_MET`. A reopened session does not clear `STOPPED_USER` or
`PAUSED_QUOTA`; only the owner does, by saying so.

### PROGRESS entry format

```
## KB-02 · 2026-10-21 · <commit hash>
Test first: IFlowReaderTest.stepsInSequenceFlowOrder (+8 in the class), PackageUnpackerTest.*, IFlowDumpTest.*
Changed: po-xray-core (io.poxray.cpi), po-xray-cli (IflowCommand, UnpackCommand), po-xray-native
Checks: mvn verify VERIFIED_PASS (target/kb-02-verify.log, Tests run: 212, Failures: 0, Skipped: 0) · SourcesAcceptanceTest VERIFIED_PASS (29 artifacts) · unpack byte-identity NOT_RUN (zips not in the cloud; owner's local check)
Review: PASS
Decisions: D-014
Deviation: none
Next: KB-03
```

A content task's `Test first:` line reads `none — content task; kb-reviewer PASS is the acceptance`.

## 3. Commits

- One task, one commit, direct to `main`. Never push red; a red `main` makes "fix main" the only
  ready task.
- Conventional Commits: `feat(cpi): …`, `feat(cli): …`, `test(core): …`, `kb: …` (knowledge
  pages), `docs: …`, `chore(build): …`, `refactor(…)`. Scope = module without the `po-xray-` /
  `iflow-forge-` prefix, or `kb` for `knowledge/`.
- Body: what and why, then the evidence line (the `Checks:` line of the PROGRESS entry).
- Trailers, in this order: `Task: KB-02`, `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`
  (the architect's model). The author identity is the owner's, set through the environment's git
  variables (`deploy/claude-cloud/README.md`). No DCO (private repository, D-003).
- Tags are not pushed from the cloud session; gates are recorded as commit hashes in
  `PROGRESS.md` and `HANDOVER.md`.

## 4. Checks (CI since KB-02b, D-018)

| Check | Command | Blocking |
| --- | --- | --- |
| build and tests | `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1` at the reactor root | yes |
| knowledge base | part of `verify` once `iflow-forge-knowledge` exists (KB-03); `-Dkb.skip=<checks>` only as the BACKLOG row allows | yes |
| forbidden values | `-Dkb.forbidden=testdata/pipeline/forbidden-values.txt`, local only (the file is not in git) | yes locally, `ENVIRONMENT_BLOCKED` in the cloud |
| CLI smoke | `java -jar po-xray-cli/target/po-xray-cli-*-runner.jar iflow knowledge/sources/<label>/generic-pipelines` | when the row names it |

The GitHub Actions workflow `.github/workflows/verify.yml` runs `mvn -B -ntp verify` on every push (JDK 25,
Temurin, Maven cache; KB-02b, D-018); the run on GitHub and the verifier's log are both evidence.

## 5. TDD and the review checklists

**Code (reviewer).** Red first, always. The reviewer receives the diff, the test output and the
packet, and answers per test: *would this test fail if the code were wrong?*

- asserts observable behaviour on a fixture the test built, not mock internals or "no exception";
- no `@Disabled`, no skipped cases, no swallowed exceptions, no `assertTrue(true)`;
- the test name states the behaviour it protects;
- the test was written or approved before the code (the PROGRESS `Test first:` line names it);
- the change keeps the design rules of `CLAUDE.md` and the signatures of the plan's Interfaces
  block; no Python; no customer data; no weakened or removed test.

**Content (kb-reviewer).** The page contract for `iflows/*.md` and `templates/*.md` is the ten H2
headings in order: `Purpose`, `Adapters`, `Steps`, `Headers and properties`, `Partner directory`,
`Scripts and resources`, `Externalized parameters`, `Error handling`, `Extension points`,
`Version notes`; for `scripts/*.md`: `Purpose`, `Inputs`, `Outputs`, `Gotchas`, `Used by`. The
kb-reviewer opens every cited artifact and checks:

- every behavioural statement has a citation and the artifact supports it;
- step order and names match the `po-xray iflow` dump; headers and properties named exist in the
  content modifiers or scripts cited;
- tenant-specific values are placeholders listed in `naming.md`; no customer token anywhere;
- the draft's claims that the artifact does not support went to `pitfalls.md`, not into the page;
- the page is within budget and in English.

Verdict `PASS` or `FAIL — <reason>` in the PROGRESS entry. `done` requires `PASS`. The reviewer
is never the agent that wrote the test; the kb-reviewer is never the kb-author of the page.

## 6. Decisions

Before the hand-over gate nothing is irreversible in the strict sense; "irreversible" means
*expensive to reverse once parts 2 to 4 build on it*.

| Class | Examples | Action |
| --- | --- | --- |
| reversible | library versions, internal shapes, test layout, page wording, step order inside a phase | decide, one line in `docs/DECISIONS.md`, continue |
| expensive to reverse | the scenario attribute set (plan Task 11), the page heading contract, the `{{tenant.*}}` placeholder namespace, public records of `io.poxray.cpi`, the `knowledge/` layout | design it, mark it `draft — review at hand-over`, list it in HANDOVER.md, continue |
| owner-only | credentials, a tenant download, anything that needs customer data, a dependency under AGPL/GPL/BSL/SSPL, a gate missed after two honest attempts, rewriting or deleting git history | `issues/ISSUE-<n>.md`, row `blocked`, next ready task |

The owner's interview the plan foresees (Task 11 step 1) is replaced during autonomous execution
by a decision marked `draft — review at hand-over` (D-013); the owner reviews it at the gate.

## 7. Findings — when practice contradicts the spec

During autonomous execution the architect is the architect of record. A contradiction is never
worked around silently:

1. `docs/findings/F-<n>.md` with assumption / how it fails / severity (`blocking | major | minor`)
   / evidence / change made — template in `docs/findings/TEMPLATE.md`.
2. `blocking` or `major`: implement the smallest change that keeps the documented intent, edit
   the spec in the same commit, add a changelog line at its top (date, finding id, what changed).
   `minor`: finding and changelog note only.
3. The PROGRESS entry says `Deviation: F-<n>`. HANDOVER.md lists every spec change for the
   owner's review.

## 8. Blocked, issues, failures

- Blocked = no progress on this task without the owner's answer, a credential, customer data, a
  network domain, or a tool failure that persists after one retry and one alternative route. Write
  the issue, set the row `blocked`, take the next ready task. `BLOCKED` as campaign state only
  when no ready task remains.
- `issues/ISSUE-<n>.md`: what is needed, why the agent cannot decide it, what was tried, what the
  default would be if the owner does not answer.
- Never guess a library API, a licence, a pipeline fact. Never invent work outside BACKLOG to
  spend allowance; add a row with a reason first.
- Quota or capacity exhaustion: stop. HANDOFF is already current after the last task; do not
  produce a final report after the limit. The owner resumes.

## 9. What the owner sees

Nothing is reported outside the repository. The owner reads `HANDOVER.md` at the gate, and
`agent/HANDOFF.md` + `PROGRESS.md` at any time. Chat messages per task are fine but carry nothing
that is not also in the files.
