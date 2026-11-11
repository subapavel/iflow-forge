# Handoff

state: STOPPED — MON-03 committed locally (detail, log, payload, key resolution); MON-04 ready.
checkout: main; MON-03 local commit on top of MON-02 (84642fa); no push.
prior: MON-02 84642fa (SOAP client, PoHttp); MON-01 8bd25c0 (spec, plan, D-059 draft, F-17).
current: io.poxray.monitoring + Mime, KeyResolver; review r2 PASS; reactor 802/0/0/26.
next: MON-04 — packet from plan MON-04 (UDS, lookups, read-only surface guard) plus MON-03 carry (PROGRESS); test-author first.
watch: required AdapterFilter defaults (false/0) are an architect's call; MON-07 checks on QA that they do not narrow a search.
hand-over: D-059 draft; anonymous WSDL reads ≠ operation calls (spec §9) needs owner acceptance; agents call SOAP ops on QA only.
open: MIG-41 owner L4 confirmation file; F-16 (reviewer hashing); MIG-36 notes; D-056 draft; "unattended" undefined.
observed: MIG-40 verify.log ends with "Cannot write …provenance.json" at exit 0; origin not traced.
limits: 24 missing-private + 2 unset kb.forbidden skips unverified; native executable/full forbidden NOT_RUN.
private: probe WSDLs local/<customer>/monitoring-probe/; QA system file in ~/.po-xray/systems (owner's, outside repo).
preservation: MIG-07 ledger hunks, agent-memory notes and untracked .codex/ remain unstaged.
staging: selective ledgers and explicit paths only; no bulk add, tenant/private-original edits, deployment or push.
