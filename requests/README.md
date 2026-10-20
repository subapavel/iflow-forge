# Field requests

A field request is how a local migration reports a gap to the cloud session: a page of the knowledge base, the skill `sap-po-migration`, the pattern catalogue or a tool could not do what the migration needed. The local side writes the request on a field branch; the cloud session turns it into a BACKLOG row, does the work through the normal review loop and marks the request done. This file is the index.

## Record

A request is `requests/REQ-<n>.md`, written from `skills/sap-po-migration/templates/request.md`, with:

- **id**: `REQ-<n>`.
- **kind**: one of `kb` (a page was wrong, silent or ambiguous), `skill` (a phase, reference or template of the skill misled or lacked), `pattern` (a generalisable way a migration found, anonymised), `tool` (po-xray or a helper could not read or derive something).
- **migration code**: the `M-<n>` that raised it.
- **what could not be done**: the step, page or command and what it failed to give.
- **evidence**: a synthetic fixture the request author built, under `requests/REQ-<n>/`, or a generic statement; never the customer's file, never under `src/`.
- **expected behaviour**: what the page, skill or tool should do instead.
- **acceptance**: what the cloud session can test to call the request done.

## Status

- `open`: written and pushed on a field branch.
- `row <id>`: the cloud session turned it into the BACKLOG row `<id>`.
- `done <hash>`: the commit `<hash>` closed it.
- A request that blocks a migration carries the marker `urgent` in its header and is taken first; there is no other fast path.

## Before a push

Run the knowledge module with `-Dkb.forbidden=<local list>`, a local file of the customer's identifiers that is never committed, over `requests/`, `patterns/` and `skills/`. A hit blocks the push. A local commit touches only `requests/` (requests, pattern drafts, synthetic fixtures); never `src/`, never `knowledge/pipeline/`, never `patterns/`.

## Index

| id | kind | status | row | commit |
|---|---|---|---|---|
