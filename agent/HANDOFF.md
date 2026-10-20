# Handoff

state: RUNNING — KB-00b draft committed (hash in next PROGRESS entry), owner approval pending; KB-11c ready/next; KB-11b a51989f, KB-12a 3bb4a67
last commit: kb: benchmark questions and the owner's answer key, draft (KB-00b); before it a51989f (KB-11b), 3bb4a67 (KB-12a), 0ac772e, c5626fa
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main) — all pushed; CI green through a51989f (39446947709); check the KB-00b run next
next exact action: KB-11c — kb-author with a page packet built from BACKLOG row 32 (six gaps); batch marker; kb-review; verifier; commit
then: owner approves benchmark/questions.md (D-028) → KB-12b ready; KB-GATE (gate-reviewer) after KB-09 (owner, local) and KB-12b
benchmark: questions.md is what the session reads; expected-answers-1/2.md are the owner's key — a transcript that reads them voids the run (D-028)
benchmark: questions.md is what the session reads; expected-answers-1/2.md are the owner's key — a transcript that reads them voids the run (D-028)
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a catalogue download, KB-00b approval, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B (local); ForbiddenValuesTest NOT_RUN
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019–D-028 (D-025 attribute set, D-026 module proposals, D-027 KB-12 split, D-028 key), F-6, F-7
budgets by the checker: loaded set 4,478/5,000; SC 1,477, contracts 1,459, naming 1,474 of 1,480 — any new row on these pages needs a prose cut first
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; gated hook via SessionStart (a6bfbef); self-check dc3644e
reviewer memory: .claude/agent-memory/kb-reviewer/MEMORY.md items 1–71 (architect appends; the reviewer is read-only); dumps reusable in target/kb-11b/
