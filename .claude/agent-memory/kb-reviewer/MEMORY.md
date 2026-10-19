# kb-reviewer memory

Durable facts only (build quirks, verified APIs, recurring findings). Never task state. Under 120 lines.
- KB-04 B1 (2026-10-19): recurring findings on iFlow pages — (1) duplicate-named Send steps: check each step's
  message flow and participant in the .iflw (grep the ServiceTask id, then the messageFlow sourceRef), the page often
  swaps them; (2) "library, unused" claims for helper scripts: grep the step scripts for `import` of the helper first.
