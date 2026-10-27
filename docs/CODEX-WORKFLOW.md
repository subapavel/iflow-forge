# Codex workflow — local continuation and migration pilots

This override applies to Codex sessions. Read it before [CLAUDE.md](../CLAUDE.md),
[AGENTS.md](../AGENTS.md) and [WORKFLOW.md](WORKFLOW.md). It supersedes only conflicting
execution rules in those files, `.claude/agents/` and the
[migration skill](../skills/sap-po-migration/SKILL.md), including its field-branch requirement.
The original Claude mode and historical decisions remain preserved. User instructions govern
the requested scope; all other design, privacy, phase-gate and evidence requirements still apply.

## Roles and bounded delegation

- Keep the architect, test-author, implementer, kb-author, verifier, reviewer, kb-reviewer and
  gate-reviewer responsibilities from AGENTS.md; the scout remains read-only.
- The main session is the architect. It writes acceptance specs, packets, ledgers, decisions
  and findings, approves red tests by reading them, and handles authorized commits. It never
  edits source, writes tests or knowledge pages, or runs Maven.
- Delegate bounded workflow documentation to a documentation author and local migration
  evidence to a migration author. Their packets explicitly name allowed paths, inputs,
  deliverables, acceptance criteria and checks. This does not grant source or knowledge-page
  ownership; those changes use the existing specialist roles.
- Use at most two subagents beside the architect, exactly one writer at a time, no nested
  delegation, agent teams or scheduled loops. During a writer's task nobody else edits files,
  ledgers or git. Read-only verification may write only its designated evidence logs.
- Use the session's available model, inherited by default. Record the actual model when known;
  otherwise record it as unknown. Never pass unavailable Claude model names to Codex calls,
  claim Claude performed the work, or invent a model mapping.
- Existing `.claude/agents/` role guidance applies subject to this override and each explicit
  allowed-path packet; its Claude model and runtime declarations are descriptive in Codex.
  Resume the same author or reviewer for corrections when available.
  Handbacks stay within 40 lines; authors never edit ledgers or commit. Any durable memory
  update must be explicitly included in the packet's allowed paths.

## User-led local work

- Read HANDOFF and the relevant BACKLOG rows for context; inspect the actual checkout, branch,
  worktrees and uncommitted work before changing anything. Reuse a suitable existing worktree.
  A historical handoff branch or last pushed state is not evidence of the current local state.
- An explicit local continuation or pilot request selects the work ahead of the first ready
  backlog row. Preserve unrelated pending tasks and completed work. Do not resume an unattended
  campaign or discard local changes because the old loop prescribes it.
- Work through the authorized task, report results and ask one question at a time when an
  owner decision is needed. The unattended prohibition on asking and its two terminal states
  do not govern a user-led session. A pilot request does not waive product phase gates.
- Product repairs needed within the requested scope may be implemented locally through normal
  acceptance specs, observed red tests, Context7 lookup before library-dependent code,
  bounded implementation, verification and independent review. Local execution is not restricted
  to requests-only edits. Keep the existing two-failure packet-rewrite rule.
- Cloud handoff and `field/*` branches are optional when requested, never prerequisites for
  a local repair or pilot retrospective. Record neutral lessons or requests locally as needed;
  do not claim they were delivered to, reviewed by or completed in the cloud.

## Migration evidence and privacy

- Source-specific files belong in the owner's existing designated external work directory or
  gitignored `local/` and `testdata/`. Confirm ignore coverage before putting private files in
  the checkout. Do not relocate or duplicate existing evidence merely to fit a template path.
- Keep the migration code map, profiles, source paths and phase outputs in that private area;
  adapt the skill's local paths consistently. Tracked output uses neutral codes such as `M-<n>`
  and synthetic fixtures only, never customer, tenant, person, host, system or application
  identifiers, configured values, private paths or customer documents.
- Existing analyses are evidence, not approved final specifications. Import previous decisions
  with their source, date when known, approval status and remaining uncertainty. Keep unresolved
  questions open; do not turn old assumptions into owner approval. Ask one question at a time.
- Keep the migration skill's intake, analysis, question, plan-approval and specification exits,
  human-task stop and citation rules. An external directory changes storage, not acceptance.
  A plan still requires explicit approval before the scenario specification phase.
- Phase 6 remains an honest artifacts placeholder until the generator and conversion exist:
  record what is unavailable. Never hand-write iFlow BPMN or present a plan as a generated
  importable package. Preserve the retrospective even when a named stop is reached.

## Verification, review and git

- The verifier runs the packet's checks and keeps real output in `target/<task>-*.log`, with
  exit codes, counts and failing sections. The architect reads the evidence tail before writing
  `VERIFIED_PASS`, `EXECUTED_FAIL`, `NOT_RUN` or `ENVIRONMENT_BLOCKED` into PROGRESS.
  Private evidence logs stay in the designated private area when their output is sensitive;
  tracked summaries carry neutral results only.
- Report baseline skips and their reasons explicitly. An exit code alone does not prove skipped
  behavior; do not weaken checks or silently treat absent private inputs as verified coverage.
- For workflow documentation only, when no checker-dependent content changes, the verifier may
  check links, diff scope, whitespace and privacy instead of Maven. Record Maven as `NOT_RUN`
  with that reason. Independent documentation review checks consistency against the packet.
  This exception does not lower source-code or knowledge-content verification floors.
- Code retains the reactor `mvn -B -ntp verify` floor and independent reviewer; knowledge pages
  retain their module checks, citations and independent kb-reviewer. Gate rows retain independent
  gate review. Applicable forbidden-values checks and approved skip restrictions still apply.
- Commit only within the user's authorized scope after verification and review. Preserve
  Conventional Commits, task references and truthful evidence; never add a false Claude
  coauthor or change the owner's identity to imitate the cloud environment.
- Do not automatically fetch, merge, push, create remote requests or perform other remote
  mutations because the old loop says to. Execute those only within explicit user authorization.
  Local handoff records describe actual saved work, pending checks and next actions honestly;
  an unpushed commit is not a failed local handoff and must not be reported as pushed.
