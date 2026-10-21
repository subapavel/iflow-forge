# Handoff

state: RUNNING — MIG-17 done (D-043: esr cross-check, synthetic channel attributes); next MIG-18 first-real-run guide (owner asked 2026-10-21)
last commit: docs: neutralise a dry-run profile prefix in the D-042 reason (FIX-04, ISSUE-5); prev 7ee752c MIG-17 feat (D-043), b69e2f2 MIG-16
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main); CI green b69e2f2 (39571099472), 3e20251 (39568803472), 08893f4 (39566640773)
next exact action: MIG-18 — docs/FIRST-REAL-RUN.md guide (owner asked 2026-10-21): scout facts, page packet, kb-author, kb-reviewer, verifier
spec: docs/superpowers/specs/2026-10-20-migration-front-end-design.md — §6.3 tool, §7 phases (F-11), §8, §9, §10 gate, §11 pilot, §12 P-1 to P-12
dry run: fixtures/dry-run-2026-10-21.md runs 1–3; all run-3 gaps closed (MIG-15 skill, MIG-16 pages, MIG-17 tool); a 4th run or a real pilot is the next test
D-036 routing; D-037 profile at step 6; D-038 silent pages; D-039 skill; D-040 helper; D-041 run-3 conventions; D-042 queue shape; D-043 esr check, keys
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1/2/4/5, D-015/016); kb spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a newest update download, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B, ISSUE-3 (customer ids in src/), ISSUE-4, KB-12b
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019 to D-043 (D-025 attributes; D-029, D-030 cites; D-031, D-034 conventions), P-1 to P-12, F-6, F-7, F-9 to F-11
budgets by the checker: loaded set ≤5,000; Step05 1,498, 1X 1,485, 2a/2i 1,484, SC 1,480, po-ico 1,480, naming 1,480, attributes 1,477 — prose cut first
customer data: spec/plan/backlog/PROGRESS neutralised 2026-10-20; src/, OUTPUT-MODEL → ISSUE-3; D-042 prefix FIX-04, history ISSUE-5; commit guard grep 0
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI .github/workflows/verify.yml
notes: agent/NOTES.md (by section); helpers io.poxray.migration, rules in docs/OUTPUT-MODEL.md "Migration helpers"; PROGRESS headings take the next hash
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; kb-reviewer memory items 1–120 (architect appends)
