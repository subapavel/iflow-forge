# Claude Code Cloud — the environment autonomous execution runs in

Agreed on 2026-10-19 (D-002, D-010). The autonomous session runs in an Anthropic-hosted Claude
Code Cloud VM (Ubuntu 24.04, 4 vCPU, 16 GB RAM; JDK 21 + Maven pre-installed, JDK 25 added by the
setup script). Nothing else is rented or deployed. Taken from `subapavel/flottila`
`deploy/claude-cloud/`.

## 1. GitHub (done 2026-10-19)

The private repository `subapavel/iflow-forge` holds the full po-xray history; `main` is the only
branch. Branch protection: block force pushes and deletion, nothing else — the agent pushes
directly to `main`. Claude has access through the GitHub connection in the Claude app.

## 2. The environment (Claude Desktop → Code tab → Cloud → new environment)

| Setting | Value |
| --- | --- |
| Name | `iflow-forge` |
| Network access | **Custom**, with "also include the default list of common package managers" checked (Maven Central, GitHub releases for the JDK download), plus `mcp.context7.com` |
| Setup script | the contents of `deploy/claude-cloud/setup.sh` |
| Permission mode | Auto |

Environment variables:

| Variable | Value | Why |
| --- | --- | --- |
| `JAVA_HOME` | `/opt/temurin-25` | installed by the setup script |
| `BASH_DEFAULT_TIMEOUT_MS` | `600000` | Maven runs |
| `BASH_MAX_TIMEOUT_MS` | `1800000` | same |
| `MAVEN_OPTS` | `-Xmx3g` | |
| `CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS` | `2` | one writer beside the architect, plus a reader (scout, verifier or a reviewer) — AGENTS.md keeps the one-writer rule |
| `CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH` | `1` | no nested delegation |
| `CLAUDE_CODE_SUBAGENT_PROMPT_CACHE_TTL` | `1h` | a subagent's cache otherwise expires after 5 minutes, which a Maven run exceeds |
| `CLAUDE_CODE_AUTO_COMPACT_WINDOW` | `400000` | compact at 400k tokens; decisions degrade long before the 1M limit |
| `GIT_AUTHOR_NAME` | owner's name | commit identity is the owner's (D-003) |
| `GIT_AUTHOR_EMAIL` | owner's e-mail | |
| `GIT_COMMITTER_NAME` | owner's name | |
| `GIT_COMMITTER_EMAIL` | owner's e-mail | |

Not set: `CLAUDE_CODE_EFFORT_LEVEL` (it would override the subagents' efforts; the architect's
effort is the session setting, `high`).

Session model: **Fable 5.1, effort high** — the architect. The agents' models are fixed in their
definitions under `.claude/agents/` (D-010).

## 3. Starting a session

First message, nothing else:

```
Read CLAUDE.md, then AGENTS.md and agent/HANDOFF.md, and run the loop of docs/WORKFLOW.md §1 from
the first ready row of agent/BACKLOG.md. Stop only on GATE_MET or IDLE_NO_READY_TASK.
```

The session reads everything it needs from the repository. A resumed session after a pause starts
the same way; `agent/HANDOFF.md` carries the state.

## 4. What stays local

`testdata/` (tenant exports, PDFs, forbidden-values list) never reaches the cloud. Rows marked
`local` in `agent/BACKLOG.md` (KB-09, the unpack byte-identity check of KB-02, KB-00a, KB-00b) are
run by the owner or a local session; the cloud records them `NOT_RUN` and continues.
