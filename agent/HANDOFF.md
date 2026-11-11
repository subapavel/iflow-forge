# Handoff

state: STOPPED — MON-05 committed locally (CLI messages group, default system, save rules); MON-06 ready.
checkout: main; MON-05 local commit on top of MON-04 (7ed7a83); no push.
prior: MON-04 7ed7a83 (UDS, lookups, allow-list); MON-03 4e82724; MON-02 84642fa; MON-01 8bd25c0 (spec, plan, F-17).
current: po-xray messages … in CLI; review r2 PASS; reactor 919/0/0/26.
next: MON-06 — packet from plan MON-06 (MCP tools, size budget, native registration) plus MON-05 carry (env seam in MCP).
watch: required AdapterFilter defaults (false/0) are an architect's call; MON-07 checks on QA that they do not narrow a search.
hand-over: D-059 draft; anonymous WSDL reads ≠ operation calls (spec §9) needs owner acceptance; agents call SOAP ops on QA only.
open: MIG-41 owner L4 confirmation file; F-16 (reviewer hashing); MIG-36 notes; D-056 draft; "unattended" undefined.
observed: MIG-40 verify.log ends with "Cannot write …provenance.json" at exit 0; origin not traced.
limits: 24 missing-private + 2 unset kb.forbidden skips unverified; native executable/full forbidden NOT_RUN.
private: probe WSDLs local/<customer>/monitoring-probe/; QA system file in ~/.po-xray/systems (owner's, outside repo).
preservation: MIG-07 ledger hunks, agent-memory notes and untracked .codex/ remain unstaged.
staging: selective ledgers and explicit paths only; no bulk add, tenant/private-original edits, deployment or push.
