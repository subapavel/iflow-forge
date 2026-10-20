# Handoff

state: KB-00b done 287527b (owner reviewed Q1–Q20); next ready: KB-12b, then KB-11d, KB-11e (version gate from change logs)
last commit: kb: benchmark key corrected after the owner's review (KB-00b); before it 721597b (KB-11c), 6d69048 (KB-00b draft), a51989f
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main) — all pushed; CI green through 721597b (39465906270); check the KB-00b run next
next exact action: KB-12b — fresh session with only the skill answers benchmark/questions.md (plan Task 12 step 3); no read of expected-answers-*.md
benchmark: questions.md is what the session reads; expected-answers-1/2.md are the key — a transcript that reads them or expected-proposal.md voids the run
owner's primary target: BPM and complex scenarios (4 BPM pilot); flow in docs/inputs/2026-10-20-owner-migration-flow.md — input to the part 3/5 specs
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a catalogue download, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B (local), ISSUE-3 (customer ids in src/ and OUTPUT-MODEL)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019–D-029 (D-025 attribute set, D-026 module proposals, D-027 KB-12 split, D-028 key, D-029 cites), F-6, F-7
budgets by the checker: loaded set 4,565/5,000; SC 1,478, contracts 1,463, naming 1,474 of 1,480 — any new row on these pages needs a prose cut first
customer data: spec/plan/backlog/PROGRESS neutralised 2026-10-20 (`<tenant>` placeholders for local testdata paths); src/ and OUTPUT-MODEL → ISSUE-3
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; gated hook via SessionStart (a6bfbef); self-check dc3644e
reviewer memory: .claude/agent-memory/kb-reviewer/MEMORY.md items 1–79 (architect appends; the reviewer is read-only); dumps reusable in target/kb-11b/
