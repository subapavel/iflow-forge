# Handoff

state: IDLE_NO_READY_TASK — KB-11b done (hash in next PROGRESS entry; KB-12a 3bb4a67, KB-11 0ac772e, c5626fa); KB-12b and KB-GATE wait for the owner
last commit: kb: migration procedure fixes from the first rehearsal (KB-11b); before it 3bb4a67 (KB-12a), 0ac772e (C3), c5626fa (C1+C2), f8a2be4
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main) — all pushed; CI green through 3bb4a67 (39440147847); check the KB-11b run next
next exact action: none ready. KB-12b once KB-00b exists (owner); KB-GATE (gate-reviewer) after KB-09 (owner, local) and KB-12b
follow-up candidates (not rows yet): Java CiteCheckTest; recipe-page contract check; naming.md used-by for conversion/README.md (naming.md 1,474 words)
follow-up candidates (not rows yet): Java CiteCheckTest; recipe-page contract check; naming.md used-by for conversion/README.md (naming.md at 1,474 words)
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a clean catalogue download, KB-00b benchmark questions (rows carry `owner`; write an ISSUE if a task needs them)
local-only: KB-09 (tenant diff), the unpack byte-identity check of KB-02, ForbiddenValuesTest (-Dkb.forbidden) need testdata/; NOT_RUN in the cloud
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019–D-027 (D-025 attribute set, D-026 module table as proposals, D-027 KB-12 split), F-6, F-7
budgets by the checker: loaded set 4,478/5,000; SC 1,477, contracts 1,459, naming 1,474 of 1,480 — any new row on these pages needs a prose cut first
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI: .github/workflows/verify.yml
notes: agent/NOTES.md (sources, iFlow format, inputs, build and reader follow-ups, knowledge module); PROGRESS headings take the hash from the next entry
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; gated hook via SessionStart (a6bfbef); self-check dc3644e
reviewer memory: .claude/agent-memory/kb-reviewer/MEMORY.md items 1–63 (architect appends; the reviewer is read-only)
