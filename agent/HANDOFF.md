# Handoff

state: STOPPED — MON-09 committed locally (XI header check reads only the root, StAX); phase MON complete; awaiting owner hand-over review.
checkout: main; 28 local commits ahead of origin (19 MIG/workflow of 2026-10-26/27 + MON-01..09); no push — owner asked whether to push all.
prior: MON-08 32729ec; MON-07 d078555 (F-18); MON-06 8b1b71f; MON-05 91bab42; MON-04 7ed7a83; MON-03 4e82724; MON-02 84642fa; MON-01 8bd25c0.
current: CLI messages + MCP message_* tools, verified live on QA; reactor 1003/0/0/26.
next: owner hand-over — D-059 draft, spec §9 open points (F-18), optional owner PROD search; then push on request.
watch: open (F-18): payload version meaning, dateType, filter defaults timesFailed/retries/archive/nodeId/retryInterval, windowless search.
hand-over: D-059 draft; anonymous WSDL reads ≠ operation calls (spec §9) needs owner acceptance; agents call SOAP ops on QA only.
open: MIG-41 owner L4 confirmation file; F-16 (reviewer hashing); MIG-36 notes; D-056 draft; "unattended" undefined.
observed: MIG-40 verify.log ends with "Cannot write …provenance.json" at exit 0; origin not traced.
limits: 24 missing-private + 2 unset kb.forbidden skips unverified; native executable/full forbidden NOT_RUN.
private: probe WSDLs local/<customer>/monitoring-probe/; QA system file in ~/.po-xray/systems (owner's, outside repo).
preservation: MIG-07 ledger hunks, agent-memory notes and untracked .codex/ remain unstaged.
staging: selective ledgers and explicit paths only; no bulk add, tenant/private-original edits, deployment or push.
