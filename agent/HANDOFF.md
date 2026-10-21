# Handoff

state: IDLE_NO_READY_TASK — MIG-13 done (D-040 helper rules, fixture esr); dry-run gaps MIG-09…13 done; every open row waits on the owner
last commit: feat: attributes helper flags BE dynamic routing, notes condition prefixes; fixture esr section (MIG-13); prev ad2ce7a
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main); CI green ad2ce7a (39536047507), 1453946 (39534344113), 80d82c6 (39531684969)
next exact action: none ready; on an owner message: KB-12b judgement (GATE check 5) → KB-GATE, or a third dry run over the fixtures (spec §11)
spec: docs/superpowers/specs/2026-10-20-migration-front-end-design.md — §6.3 tool, §7 phases, §8 learning, §9 field loop, §11 acceptance, §12 P-1…P-12
dry run: fixtures/dry-run-2026-10-21.md "Gaps" → MIG-09…MIG-13 done; next dry run (run 3) over the fixtures would re-validate the skill end to end
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
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; kb-reviewer memory items 1–116 (architect appends)
