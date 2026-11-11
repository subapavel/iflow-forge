# Handoff

state: STOPPED — MON-04 committed locally (UDS, lookups, 21-operation allow-list); MON-05 ready.
checkout: main; MON-04 local commit on top of MON-03 (4e82724); no push.
prior: MON-03 4e82724 (detail, log, payload); MON-02 84642fa (SOAP client); MON-01 8bd25c0 (spec, plan, F-17).
current: io.poxray.monitoring complete (21 read ops); review r2 PASS; reactor 840/0/0/26.
next: MON-05 — packet from plan MON-05 (CLI messages group, default system, save target) plus MON-04 carry; Context7 for Aesh first.
watch: required AdapterFilter defaults (false/0) are an architect's call; MON-07 checks on QA that they do not narrow a search.
hand-over: D-059 draft; anonymous WSDL reads ≠ operation calls (spec §9) needs owner acceptance; agents call SOAP ops on QA only.
open: MIG-41 owner L4 confirmation file; F-16 (reviewer hashing); MIG-36 notes; D-056 draft; "unattended" undefined.
observed: MIG-40 verify.log ends with "Cannot write …provenance.json" at exit 0; origin not traced.
limits: 24 missing-private + 2 unset kb.forbidden skips unverified; native executable/full forbidden NOT_RUN.
private: probe WSDLs local/<customer>/monitoring-probe/; QA system file in ~/.po-xray/systems (owner's, outside repo).
preservation: MIG-07 ledger hunks, agent-memory notes and untracked .codex/ remain unstaged.
staging: selective ledgers and explicit paths only; no bulk add, tenant/private-original edits, deployment or push.
