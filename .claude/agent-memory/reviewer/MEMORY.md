# reviewer memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.

- `io.poxray.cpi` readers use `putIfAbsent` (first wins) on duplicate property keys, manifest headers and
  element ids; check the honesty rule ("report source bugs, never fix silently") there first (KB-02 review).
- KB-02 evidence layout: diff `target/<task>.diff`, red logs `target/<task>-red*.log`, surefire XML under
  `<module>/target/surefire-reports/` carry the counts when the run was `-q`.
- Acceptance-spec fixtures can leave filter branches and first/last-wins rules unexercised; a stub test with no backticked token is vacuous (KB-07, iflow-forge-knowledge Checks). Try "delete this line, does a test fail?" on every branch.
