# reviewer memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.

- `io.poxray.cpi` readers use `putIfAbsent` (first wins) on duplicate property keys, manifest headers and
  element ids; check the honesty rule ("report source bugs, never fix silently") there first (KB-02 review).
- KB-02 evidence layout: diff `target/<task>.diff`, red logs `target/<task>-red*.log`, surefire XML under
  `<module>/target/surefire-reports/` carry the counts when the run was `-q`.
- Acceptance-spec fixtures can leave filter branches and first/last-wins rules unexercised; a stub test with no backticked token is vacuous (KB-07, iflow-forge-knowledge Checks). Try "delete this line, does a test fail?" on every branch.
- Spec green-gate greps ("no reference to X") can be broken by javadoc the test-author writes; grep src/test too (KB-13, po-xray-core bpm).
- ScenarioAttributes (po-xray-core): the exit rules depend on `customExit()` running before `inboundConversion()` (the sender-module fields are set in the first); with a sender module, a receiver module and an unresolved receiver channel together the candidates are {post, pre+post}, which OUTPUT-MODEL's "all four on an unresolved receiver" does not spell out (MIG-02b).
- A diff named in a review brief may be path-filtered by the implementer packet (MIG-02b round 2: `git diff -- po-xray-core docs/OUTPUT-MODEL.md` left out po-xray-cli); ask for `git status --short` and `git diff HEAD --stat` files as evidence for "only allowed paths" before failing on it.
