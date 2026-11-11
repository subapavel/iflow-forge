# Handoff

state: STOPPED — MON-01 committed locally (F-17 contract probe); Phase MON spec, plan and D-059 (draft) in the same commit.
checkout: main; MON-01 local commit on top of MIG-40 (dbbd379); no push.
prior: MIG-40 dbbd379 (legacy import rules, D-058); MIG-39 22081aa (LOCAL-WORKFLOW, D-057); MIG-38 9004d9f.
current: docs/findings/F-17.md; spec 2026-11-11-message-monitoring-design.md; plan 2026-11-11-message-monitoring.md; review r2 PASS.
next: MON-02 — write its packet from plan MON-02, test-author red tests (MonitoringClientSearchTest, PoHttpTest), then implementer.
watch: MON-02 required AdapterFilter defaults (false/0) are an architect's call; MON-07 checks on QA that they do not narrow a search.
hand-over: D-059 draft; anonymous WSDL reads ≠ operation calls (spec §9) needs owner acceptance; agents call SOAP ops on QA only.
open: MIG-41 owner L4 confirmation file; F-16 (reviewer hashing); MIG-36 notes; D-056 draft; "unattended" undefined.
observed: MIG-40 verify.log ends with "Cannot write …provenance.json" at exit 0; origin not traced.
limits: 24 missing-private + 2 unset kb.forbidden skips unverified; native executable/full forbidden NOT_RUN.
private: probe WSDLs local/<customer>/monitoring-probe/; QA system file in ~/.po-xray/systems (owner's, outside repo).
preservation: MIG-07 ledger hunks, agent-memory notes and untracked .codex/ remain unstaged.
staging: selective ledgers and explicit paths only; no bulk add, tenant/private-original edits, deployment or push.
