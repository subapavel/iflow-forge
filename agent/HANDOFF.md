# Handoff

state: STOPPED — MON-07 committed locally (QA acceptance, F-18); MON-08 ready (default payload part by content), needs go-ahead.
checkout: main; MON-07 local commit on top of MON-06 (8b1b71f); no push.
prior: MON-06 8b1b71f (MCP tools); MON-05 91bab42; MON-04 7ed7a83; MON-03 4e82724; MON-02 84642fa; MON-01 8bd25c0.
current: CLI + MCP message reads verified live on QA; F-18 settled §9 points; reactor 983/0/0/26.
next: MON-08 — core rule picks the payload part by content (skip a SOAP/XI header part), CLI and MCP use it; red test first.
watch: open (F-18): payload version meaning, dateType, filter defaults timesFailed/retries/archive/nodeId/retryInterval, windowless search.
hand-over: D-059 draft; anonymous WSDL reads ≠ operation calls (spec §9) needs owner acceptance; agents call SOAP ops on QA only.
open: MIG-41 owner L4 confirmation file; F-16 (reviewer hashing); MIG-36 notes; D-056 draft; "unattended" undefined.
observed: MIG-40 verify.log ends with "Cannot write …provenance.json" at exit 0; origin not traced.
limits: 24 missing-private + 2 unset kb.forbidden skips unverified; native executable/full forbidden NOT_RUN.
private: probe WSDLs local/<customer>/monitoring-probe/; QA system file in ~/.po-xray/systems (owner's, outside repo).
preservation: MIG-07 ledger hunks, agent-memory notes and untracked .codex/ remain unstaged.
staging: selective ledgers and explicit paths only; no bulk add, tenant/private-original edits, deployment or push.
