# Handoff

state: RUNNING — MIG-14 done (dry run 3: open list 0, plan approved, exit 4/4); MIG-15, MIG-16 content, MIG-17 code ready; KB-12b owner judgement pending
last commit: docs: third dry run of sap-po-migration over the fixtures after the gap fixes (MIG-14); prev 71fc425
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main); CI green 71fc425 (39539381995), ad2ce7a (39536047507), 1453946 (39534344113)
next exact action: MIG-15 — BACKLOG row (six skill gaps of run 3): scout, decide, page packet; then MIG-16; then MIG-17 (acceptance spec, red tests)
spec: docs/superpowers/specs/2026-10-20-migration-front-end-design.md — §6.3 tool, §7 phases, §8 learning, §9 field loop, §11 acceptance, §12 P-1…P-12
dry run: fixtures/dry-run-2026-10-21.md runs 1–3; run-3 gaps → MIG-15 (skill), MIG-16 (pages), MIG-17 (tool, fixture channels); local/demo* gitignored
D-036 routing (reuse-xrd derived; EO {custom, decoupled}, BE {custom}); D-037 profile at step 6 (F-10); D-038 silent pages; D-039 skill; D-040 helper
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1/2/4/5, D-015/016); kb spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a newest update download, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B (local), ISSUE-3 (customer ids in src/), KB-12b
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019–D-040 (D-025 attributes, D-029/D-030 cites, D-031/D-034 conventions, D-035–D-040), P-1…P-12, F-6/7/9/10
budgets by the checker: loaded set ≤5,000; Step05 1,498, 1X 1,485, 2a/2i 1,484, SC 1,480, po-ico 1,478, naming 1,478, attributes 1,478 — prose cut first
customer data: spec/plan/backlog/PROGRESS neutralised 2026-10-20 (`<tenant>` placeholders); src/ and OUTPUT-MODEL → ISSUE-3; local/ and testdata/ gitignored
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI .github/workflows/verify.yml
notes: agent/NOTES.md (by section); helpers io.poxray.migration, rules in docs/OUTPUT-MODEL.md "Migration helpers"; PROGRESS headings take the next hash
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; kb-reviewer memory items 1–117 (architect appends)
