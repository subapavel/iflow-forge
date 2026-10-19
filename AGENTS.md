# iflow-forge — agents, ownership, evidence

Every agent and tool working in this repository (Claude Code, its subagents, any other) follows
`CLAUDE.md`; this file adds who does what and what counts as evidence. The full protocol is
`docs/WORKFLOW.md`, which calls the main session the coordinator; here it is the architect, the
same role. The method is taken from `subapavel/flottila` (D-002).

## Roles

| Role | Model | Effort | Tools | Owns |
| --- | --- | --- | --- | --- |
| architect (the main session) | claude-fable-5-1 | high (session setting) | all | task selection, acceptance specs, task packets and page packets, approval of the red tests, design decisions, findings, the scenario attribute set and the knowledge-base page contract (designed in the packet, written by the agents), BACKLOG / HANDOFF / PROGRESS, commits |
| test-author (`.claude/agents/test-author.md`) | claude-opus-5-5 | high | read, search, edit, write, bash | the red tests of one code task, observed red |
| implementer (`.claude/agents/implementer.md`) | claude-opus-5-5 | high (`xhigh` per call for the BPMN step-order walk and public records) | read, search, edit, write, bash | one bounded code task at a time, inside its allowed paths |
| kb-author (`.claude/agents/kb-author.md`) | claude-opus-5-5 | high | read, search, edit, write, bash (dumps only) | the pages of one content task, every fact cited |
| verifier (`.claude/agents/verifier.md`) | claude-sonnet-5-5 | low | bash, read, search | running the checks, reporting observed results |
| scout (`.claude/agents/scout.md`) | claude-haiku-5-5 | medium | read, search | locating code, artifacts, requirements, prior results; factual context only |
| reviewer (`.claude/agents/reviewer.md`) | claude-opus-5-5 | high | read, search | independent review of every completed code task |
| kb-reviewer (`.claude/agents/kb-reviewer.md`) | claude-opus-5-5 | high | read, search | independent fact check of every completed content task against the artifacts |
| gate-reviewer (`.claude/agents/gate-reviewer.md`) | claude-fable-5-1 | high | read, search | the gate rows only |

The architect delegates everything that produces or runs code or content. It never edits `src/`,
never writes a test, never writes a page under `knowledge/pipeline/`, never runs Maven. It writes
the spec and the packet, reads the test files to approve them, reads handbacks and verdicts,
decides, and commits. A one-line fix the reviewer names goes back to the same agent, resumed by
name.

Model per call: the architect may pass `model: sonnet` to the implementer for fixtures, README
rows, stubs and `.gitkeep`-level work, and to the kb-author for glossary and index pages;
`effort: xhigh` for the step-order walk in `IFlowReader`, the public records of `io.poxray.cpi`,
and `contracts.md`. The definition's model is the default. Fable 5.1 runs only as the architect and
as the gate reviewer.

Concurrency: at most two subagents at once beside the architect, and exactly one writer — while
the test-author, the implementer or the kb-author owns a task, nobody else edits source, pages,
the ledger files or git; the scout, verifier, reviewer and kb-reviewer never write. No nested
delegation, no agent teams, no scheduled loops.

A finished agent stays addressable by name in the session: a second review round, a partial
hand-back at the turn cap, or a one-line fix resumes the same agent with its context instead of
starting a fresh one.

Memory: implementer, test-author, kb-author, reviewer and kb-reviewer keep
`.claude/agent-memory/<name>/MEMORY.md` (committed; durable facts only, never task state; under
120 lines). The architect reads a memory file only when a handback points at it.

## Acceptance spec (architect → test-author), code tasks

Task id; module(s) and allowed test paths; the behaviours to guarantee, one line each with the
proposed test name (the plan's test list is the starting point); fixtures to build; what counts
as red (a failing assertion, or a compile error on a type the task introduces). The architect
approves the written tests by reading the test files, not the handback.

## Task packet (architect → implementer), code tasks

Task id from `agent/BACKLOG.md`; base commit; allowed paths; the red test(s) that define
acceptance (approved by the architect); the exact signatures from the plan's Interfaces block;
expected behaviour in two sentences; the spec and plan sections that govern the change; checks to
run; known constraints. Only the context the task needs — the implementer does not see the
conversation.

## Page packet (architect → kb-author), content tasks

Task id; the pages to write (paths); for each page the artifacts it is derived from (symbolic
names, script paths) and the dump command to run; the draft page under `knowledge/inputs/en/` to
start from, if any; the heading contract (`docs/WORKFLOW.md` §5); the citation rule; placeholders
already defined in `naming.md`; the word budget; what goes to `pitfalls.md` when draft and
artifact disagree. The kb-author never sees `testdata/`.

## Handback (any agent → architect)

At most 40 lines. Changed paths; one line per path group; commands executed with observed results
(evidence word, counts line, log path under `target/`); remaining uncertainty; proposed next
action. A failure quotes the failing test and at most 10 lines of its section. A turn cap returns
partial work, marked as such. No agent updates BACKLOG / HANDOFF / PROGRESS or commits to `main`.

## Verification (verifier, after every handback and before every commit)

The architect names the commands; the verifier runs them with output under `target/<task>-*.log`
and reports exit codes, counts lines and the failing section. The architect reads the tail of the
log itself for the evidence line it writes into PROGRESS.md — a claim without observed output
behind it is not evidence. For a content task the command is the knowledge module's test run
(`mvn -B -ntp -q -pl iflow-forge-knowledge -am test`, with `-Dkb.skip=` as the row allows).

## Review (reviewer, after every code task)

Input from the architect, by path: the diff (`target/<task>.diff`), the test output and the task
packet. The reviewer answers, per test, "would this fail if the code were wrong?" against the
checklist in `docs/WORKFLOW.md` §5 and returns `PASS` or `FAIL — <reason>`, every blocking reason
in one round. The architect records the verdict in PROGRESS.md; `FAIL` reopens the task. After a
second `FAIL` the architect rewrites the packet (the spec was wrong, not only the code) rather than
running a third round on the same packet. The reviewer is never the agent that wrote the test.

## Fact check (kb-reviewer, after every content task)

Input by path: the pages written, the page packet, the dump files. The kb-reviewer opens every
cited artifact and answers, per statement, "does the artifact say this?", and per page whether the
heading contract, the placeholder rule and the budget hold. Verdict `PASS` or `FAIL — <reason>`
naming page and line; a statement without a citation, or a citation that does not support it, is
a blocking reason. Same two-round rule as the code review.

## Gate review (gate-reviewer, on gate rows only)

Run once per gate row on the gate commit. Returns `gate: MET` or `gate: NOT_MET — <reasons>`;
`NOT_MET` makes the named condition the only ready task.

## Evidence vocabulary

Every check in PROGRESS.md carries one of: `VERIFIED_PASS` (ran, observed green),
`EXECUTED_FAIL` (ran, observed red), `NOT_RUN`, `ENVIRONMENT_BLOCKED` (could not run here, why).
A claim without the observed output behind it is not evidence. A skeleton is not the feature; a
page without citations is not knowledge.
