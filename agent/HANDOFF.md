# Handoff

state: MIG-04 done (cite check, phases 3–4 pages); MIG-02b done 3b88963; KB-12b owner judgement pending (GATE check 5); next KB-11d, MIG-05
last commit: feat: cite check for skill pages and references for the question round and the plan (MIG-04); before it 3b88963, 6218ac9
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main); CI green 3b88963 (39520985125), 6218ac9 (39517967726), 84ba389 (39515866770)
next exact action: KB-11d — re-read its BACKLOG row and the spec section it names; page packet; then MIG-05, MIG-06
MIG-00 spec: docs/superpowers/specs/2026-10-20-migration-front-end-design.md — §6 layout, §7 phases, §8 learning, §9 field loop, §12 P-1…P-12, §13
benchmark: 20 q first run 10/9/1, reruns 4/2/1 then Q10 correct; open-questions/kb-12b.md (missing Binary receiverDetermination); the owner judges the page
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a newest update download, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B (local), ISSUE-3 (customer ids in src/, OUTPUT-MODEL)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019–D-034 (D-025 attributes, D-029/D-030 cites, D-031/D-034 conventions, D-032/D-033), P-1…P-12, F-6, F-7
budgets by the checker: loaded set 4,674/5,000; Step05 1,498, 1X 1,485, 2a/2i 1,484, results 1,480, SC 1,479, naming 1,478, po-ico 1,478 — prose cut first
customer data: spec/plan/backlog/PROGRESS neutralised 2026-10-20 (`<tenant>` placeholders for local testdata paths); src/ and OUTPUT-MODEL → ISSUE-3
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI .github/workflows/verify.yml
notes: agent/NOTES.md (by section); helpers io.poxray.migration, rules in docs/OUTPUT-MODEL.md "Migration helpers"; PROGRESS headings take the next hash
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; gated hook via SessionStart (a6bfbef); self-check dc3644e
reviewer memory: .claude/agent-memory/kb-reviewer/MEMORY.md items 1–103 (architect appends; the reviewer is read-only); dumps reusable in target/kb-11b/
