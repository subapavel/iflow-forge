# Handoff

state: STOPPED — MON-06 committed locally (MCP message_* tools, budget, native registration); MON-07 ready, needs owner go-ahead.
checkout: main; MON-06 local commit on top of MON-05 (91bab42); no push.
prior: MON-05 91bab42 (CLI messages); MON-04 7ed7a83; MON-03 4e82724; MON-02 84642fa; MON-01 8bd25c0 (spec, plan, F-17).
current: 11 MCP message_* tools; review r2 PASS; reactor 983/0/0/26.
next: MON-07 — live read-only acceptance on QA with the built CLI and MCP; finding F-18; private evidence under local/<customer>/.
watch: MON-07 checks required AdapterFilter defaults, Locale 3 children, key format, XI header part, MCP JSON array → List<String>.
hand-over: D-059 draft; anonymous WSDL reads ≠ operation calls (spec §9) needs owner acceptance; agents call SOAP ops on QA only.
open: MIG-41 owner L4 confirmation file; F-16 (reviewer hashing); MIG-36 notes; D-056 draft; "unattended" undefined.
observed: MIG-40 verify.log ends with "Cannot write …provenance.json" at exit 0; origin not traced.
limits: 24 missing-private + 2 unset kb.forbidden skips unverified; native executable/full forbidden NOT_RUN.
private: probe WSDLs local/<customer>/monitoring-probe/; QA system file in ~/.po-xray/systems (owner's, outside repo).
preservation: MIG-07 ledger hunks, agent-memory notes and untracked .codex/ remain unstaged.
staging: selective ledgers and explicit paths only; no bulk add, tenant/private-original edits, deployment or push.
