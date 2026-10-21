# REQ-<n>

<!-- One request per lesson, written in phase 7 (`references/retrospective.md`, "Requests by kind"). Replace every <…>; no customer, tenant, system, host or person name (SKILL.md, "Two rules", rule 2). -->

## Header

| field | value |
|---|---|
| kind | `<kb or skill or pattern or tool>` |
| migration | `M-<n>` |
| date | `<yyyy-mm-dd>` |
| urgent | `<yes or no>` |
| status | `open` |

Kinds [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.4 What the retrospective writes]: `kb`, a page was wrong, silent or ambiguous; `skill`, a phase, reference or template of `sap-po-migration` misled or lacked; `pattern`, a generalisable way the migration found; `tool`, po-xray or a helper could not read or derive something. `urgent` yes: an ordinary request the cloud takes first. Status runs `open` → `row <id>` → `done <hash>`, as `requests/README.md` indexes it.

## What could not be done

The page, phase, reference, template or tool, and what it did not do: `<…>`.

## Evidence

A synthetic fixture the request author built under `requests/REQ-<n>/`, or a generic statement; never the customer's file, never under `src/`. A `tool` request always carries the fixture (spec §8.4).

## Expected behaviour

One paragraph: what the page, skill or tool should do instead.

For `pattern`, the page it expects, in the form of [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#8.3 Pattern catalogue (committed, generic)], anonymised: the problem in pipeline terms, the options with trade-offs and page cites, where each was used as `[migration M-<n>, <date>]`, the open points. Drafted under `requests/`, never under `patterns/` locally.

## Acceptance

The check the cloud can run: a red test for `tool`; the kb-reviewer's PASS for `kb`, `skill`, `pattern`.
