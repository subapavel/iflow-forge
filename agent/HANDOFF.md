# Handoff

state: PAUSED — owner 2026-10-21 continues locally this week; MIG-20 done (72d54e3); MIG-21 ready (spec in agent/NOTES.md, last section); MIG-22 pending
last commit: chore: hand-over for local continuation — MIG-21 spec and scout facts in NOTES; prev 72d54e3 MIG-20, 33707a2 MIG-19
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main); CI 72d54e3 run 39587302862 green; 33707a2 run 39584178282 green; e39fac5 green (attempt 2)
next exact action: MIG-21 — test-author red tests per agent/NOTES.md "MIG-21" (DiscoverCommandTest, PoSystemTest, 2 core tests); implementer; reviewer
spec: docs/superpowers/specs/2026-10-20-migration-front-end-design.md — §6.3 tool, §7 phases (F-11), §8, §9, §10 gate, §11 pilot, §12 P-1 to P-12
dry run: fixtures/dry-run-2026-10-21.md runs 1–3; all run-3 gaps closed (MIG-15 skill, MIG-16 pages, MIG-17 tool); a 4th run or a real pilot is the next test
D-036 routing; D-037 profile at step 6; D-038 silent pages; D-039 skill; D-040 helper; D-041 run-3 conventions; D-042 queue shape; D-043 esr check, keys
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1/2/4/5, D-015/016); kb spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06 (29 artifacts; fact base D-019; 1.0.7/1.0.4/1.0.3/1.1.1, see versions/README.md), 2026-09 (32; 1.0.8/1.0.5/1.0.5/1.2.0)
owner inputs pending: KB-00a newest update download, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B, ISSUE-3 (customer ids in src/), ISSUE-4, KB-12b
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019 to D-044 (D-025 attributes; D-029, D-030 cites; D-031, D-034 conventions), P-1 to P-12, F-6, F-7, F-9 to F-12
budgets by the checker: loaded set ≤5,000; changelog 1,497, Step05 1,498, 1X 1,485, 2a/2i 1,484, four pages 1,480, attributes 1,477 — prose cut first
customer data: spec/plan/backlog/PROGRESS neutralised 2026-10-20; src/, OUTPUT-MODEL → ISSUE-3; D-042 prefix FIX-04, history ISSUE-5; commit guard grep 0
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI .github/workflows/verify.yml
notes: agent/NOTES.md (by section); helpers io.poxray.migration, rules in docs/OUTPUT-MODEL.md "Migration helpers"; PROGRESS headings take the next hash
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; kb-reviewer memory 1–122, reviewer +1 (architect appends)
