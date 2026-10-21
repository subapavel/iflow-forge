# Handoff

state: RUNNING — MIG-12 done (D-039 skill conventions); MIG-13 code ready (red tests first); KB-12b owner judgement pending (GATE 5)
last commit: docs: skill conventions from the dry runs — profile sources, target version, open-list touch, ICO split, runtime default (MIG-12); prev 1453946
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main); CI green 1453946 (39534344113), 80d82c6 (39531684969), f836cc3 (39530283773)
next exact action: MIG-13 — acceptance spec; test-author red tests (fixture `esr`, same-interface rule D-036, BE+dynamic flag, condition ns)
spec: docs/superpowers/specs/2026-10-20-migration-front-end-design.md — §6.3 tool, §7 phases, §8 learning, §9 field loop, §11 acceptance, §12 P-1…P-12
dry run: fixtures/dry-run-2026-10-21.md "Gaps" → MIG-09, MIG-10 (run 2 exit 4/4), MIG-11, MIG-12 done; MIG-13 (code) is the last gap task
D-036 routing (reuse-xrd derived; EO {custom, decoupled}, BE {custom}); D-037 profile amended at step 6 (F-10); D-038 silent pages, present.md; D-039 skill
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1/2/4/5, D-015/016); kb spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a newest update download, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B (local), ISSUE-3 (customer ids in src/), KB-12b
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019–D-039 (D-025 attributes, D-029/D-030 cites, D-031/D-034 conventions, D-035–D-039), P-1…P-12, F-6/7/9/10
budgets by the checker: loaded set ≤5,000; Step05 1,498, 1X 1,485, 2a/2i 1,484, SC 1,480, po-ico 1,478, naming 1,478, attributes 1,478 — prose cut first
customer data: spec/plan/backlog/PROGRESS neutralised 2026-10-20 (`<tenant>` placeholders); src/ and OUTPUT-MODEL → ISSUE-3; local/ and testdata/ gitignored
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI .github/workflows/verify.yml
notes: agent/NOTES.md (by section); helpers io.poxray.migration, rules in docs/OUTPUT-MODEL.md "Migration helpers"; PROGRESS headings take the next hash
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; kb-reviewer memory items 1–115 (architect appends)
