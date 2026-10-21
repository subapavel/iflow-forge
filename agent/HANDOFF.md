# Handoff

state: RUNNING — MIG-06 done (skill ready to test, spec §11); dry-run gaps → MIG-09–MIG-13 ready; KB-12b owner judgement pending (GATE check 5)
last commit: docs: field loop — WORKFLOW §11, requests reference, D-035, dry run (MIG-06); before it aa144d6, df2df5b
branch: claude/eager-brown-uw61ft (D-017; owner fast-forwards main); CI green aa144d6 (39525701048), df2df5b (39524351034), 4ec942e (39523214189)
next exact action: MIG-09 — dry-run-2026-10-21.md "Gaps"; receiver_determination/mapping rules in attributes.md, po-ico, variants.md; decide; packet
MIG-00 spec: docs/superpowers/specs/2026-10-20-migration-front-end-design.md — §6 layout, §7 phases, §8 learning, §9 field loop, §12 P-1…P-12, §13
benchmark: 20 q first run 10/9/1, reruns 4/2/1 then Q10 correct; open-questions/kb-12b.md (missing Binary receiverDetermination); the owner judges the page
reader: io.poxray.cpi per plan Task 2 + F-1; order D-016; ScriptCollection 0 steps; propdef no defaults (D-015); ifl:property not exposed (NOTES)
plan: docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md (changelog F-1, D-016, D-015, F-2, F-4, F-5); spec -design.md (F-3, F-5, F-7)
sources: knowledge/sources/2026-06-trial (29 artifacts; fact base, D-019; 45 Groovy stems, 152 copies, no XSLT) and 2026-09-current (32; pages after KB-00a)
owner inputs pending: KB-00a newest update download, KB-09 tenant diff (local, testdata/), ISSUE-1 Phase B (local), ISSUE-3 (customer ids in src/, OUTPUT-MODEL)
roles: architect only; test-author, implementer, kb-author, reviewer, kb-reviewer Opus 5.5; verifier Sonnet 5.5; scout Haiku 5.5; gate-reviewer Fable 5.1
decisions pending at hand-over: D-016, F-1, D-019–D-035 (D-025 attributes; D-029/D-030 cites; D-031/D-034 conventions; D-035 field loop), P-1…P-12, F-6/7/9
budgets by the checker: loaded set 4,674/5,000; Step05 1,498, 1X 1,485, 2a/2i 1,484, results 1,480, SC 1,479, naming 1,478, po-ico 1,478 — prose cut first
customer data: spec/plan/backlog/PROGRESS neutralised 2026-10-20 (`<tenant>` placeholders for local testdata paths); src/ and OUTPUT-MODEL → ISSUE-3
build: `mvn -B -ntp -q verify > target/<task>-verify.log 2>&1`; JDK 25, Maven 3.9; counts in <module>/target/surefire-reports; CI .github/workflows/verify.yml
notes: agent/NOTES.md (by section); helpers io.poxray.migration, rules in docs/OUTPUT-MODEL.md "Migration helpers"; PROGRESS headings take the next hash
turn economy: WORKFLOW §10 (D-024) — foreground waits; target/.batch-in-review during review; gated hook via SessionStart (a6bfbef); self-check dc3644e
reviewer memory: .claude/agent-memory/kb-reviewer/MEMORY.md items 1–109 (architect appends; the reviewer is read-only); dumps reusable in target/kb-11b/
