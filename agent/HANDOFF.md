# Handoff

state: STOPPED — P7 complete (MIG-38 + MIG-39 committed locally); migration process plan P1–P7 done.
checkout: main; MIG-38 local commit on top of 22081aa (MIG-39); no push.
prior: MIG-37 7874898 (P6); CODEX-01 d856ec1; MIG-39 22081aa (docs/LOCAL-WORKFLOW.md, D-057).
current: MIG-38 done: M-1 private audit view and replay; cases 0,1,2,6a,7,8 PASS; 3,4,5,6b NOT_RUN; reviewer R PASS.
next: owner review of F-15 (dossier v3 vs real pilot states) and F-16 (reviewer hashing); then MIG-07 or MIG-21 per owner.
open: MIG-36 review notes; MIG-37 experiment.md "none" wording; D-056 draft; "unattended" undefined in WORKFLOW.md.
semantics: structural evidence is not runtime proof; matching names or automatic candidates are not semantic equivalence or approval.
limits: 24 missing-private + 2 unset kb.forbidden skips unverified; native executable/full forbidden NOT_RUN; no M-1 SCA on disk.
private: audit view local/<customer>/audit/M-1-p7/ (gitignored); originals unchanged (hash diff empty).
preservation: MIG-07 ledger hunks, agent-memory notes and untracked .codex/ remain unstaged.
staging: selective ledgers and explicit paths only; no bulk add, tenant/private-original edits, deployment or push.
