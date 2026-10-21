# Handoff

state: RUNNING — MIG-10 done (F-10, D-037 profile amended at step 6); MIG-11, MIG-12 ready, MIG-13 pending; KB-12b owner judgement pending (GATE 5)
last commit: docs: profile amended when an answer settles its value — F-10, D-037, skill pages (MIG-10); before it f836cc3 (MIG-09), 801bbb9 (MIG-06)
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main); CI green f836cc3 (39530283773), 801bbb9 (39528327851), aa144d6 (39525701048)
next exact action: MIG-11 — dry-run gaps C (five silent page questions; features.md rows o2mo/eo/p2p/Step03/reuse-XRD): scout done, decide, page packet
spec: docs/superpowers/specs/2026-10-20-migration-front-end-design.md — §6 layout, §7 phases, §8 learning, §9 field loop, §11 acceptance, §12 P-1…P-12
dry run: skills/sap-po-migration/fixtures/dry-run-2026-10-21.md "Gaps" → MIG-09 (done), MIG-10 (done, run 2 exit 4/4), MIG-11 pages, MIG-12 skill, MIG-13 tool
D-036: dynamic routing → reuse-xrd derived; EO {custom, decoupled}, BE {custom}, one receiver, or stop. D-037: profile amended at questions.md step 6 (F-10)
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); kb spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a newest update download, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B (local), ISSUE-3 (customer ids in src/), KB-12b
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019–D-037 (D-025 attributes, D-029/D-030 cites, D-031/D-034 conventions, D-035–D-037), P-1…P-12, F-6/7/9/10
budgets by the checker: loaded set 4,674/5,000; Step05 1,498, 1X 1,485, 2a/2i 1,484, results 1,480, SC 1,479, po-ico 1,478, naming 1,478 — prose cut first
customer data: spec/plan/backlog/PROGRESS neutralised 2026-10-20 (`<tenant>` placeholders); src/ and OUTPUT-MODEL → ISSUE-3; local/ and testdata/ gitignored
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI .github/workflows/verify.yml
notes: agent/NOTES.md (by section); helpers io.poxray.migration, rules in docs/OUTPUT-MODEL.md "Migration helpers"; PROGRESS headings take the next hash
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; kb-reviewer memory items 1–112 (architect appends)
