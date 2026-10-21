# Field requests and the field branch

Phase 7 hands over here (`references/retrospective.md`, "Requests by kind"). Rule: [page: docs/superpowers/specs/2026-10-20-migration-front-end-design.md#9. Field requests and branches (statement 12)]; loop: [page: docs/WORKFLOW.md#11. Field loop (spec §9, P-10, P-11; MIG-06)]; decision D-035.

## Request record

1. One request per lesson: one kind, one file.
2. The id `REQ-<n>` is the next free number in the "Index" of `requests/README.md`; never reused.
3. Write `requests/REQ-<n>.md` from `templates/request.md`, with the fields of [page: requests/README.md#Record].
4. Append the index row `| REQ-<n> | <kind> | open | | |`; row and commit stay empty.
5. Write the id into `retrospective.md`.

## Synthetic fixtures

1. Build the evidence under `requests/REQ-<n>/`, never under `src/` or `testdata/`, never from the customer's file (spec §8.4).
2. Style of `skills/sap-po-migration/fixtures/`: synthetic names only, a `README.md` naming each file.
3. A `tool` request always carries a fixture that lets the cloud reproduce the gap: the exact command (`po-xray <command> requests/REQ-<n>/<file>`), the input, the expected and the observed output or error line. The cloud writes its red test from it (WORKFLOW §11).
4. A `kb`, `skill` or `pattern` request may give a generic statement instead (spec §9).
5. The forbidden-values check covers `requests/`, fixtures included.

## Forbidden-values run before a push

1. Keep the list outside the repository or under `local/` (gitignored), e.g. `local/forbidden.txt`: one identifier per line (customer, tenant, system, host, person names, configured values). Never committed, never quoted.
2. Run from the repository root (WORKFLOW §11) over `requests/`, `patterns/` and `skills/` [page: requests/README.md#Before a push]:
   `mvn -B -ntp -q -pl iflow-forge-knowledge -am test -Dkb.forbidden=<path>`
   A relative `<path>` starts at the repository root.
3. A hit fails the run, naming file, line and the value's number and length, not the value. It blocks the push: rewrite the line generically, amend, rerun.
4. A `<path>` naming no file silently skips the check. Confirm `skipped="0"` in `iflow-forge-knowledge/target/surefire-reports/TEST-io.iflowforge.knowledge.ForbiddenValuesTest.xml`.
5. Lines under 4 characters are not checked: search those with `git grep -n -i -w <identifier> -- requests skills patterns`.

## Field branch

1. `git fetch`; branch `field/<yyyy-mm-dd>` from the current `main`.
2. Commit only under `requests/`: requests, pattern drafts, fixtures; never `src/`, `knowledge/pipeline/`, `patterns/` (spec §12 P-6). `git diff --cached --name-only` must list only `requests/` paths.
3. Run the forbidden-values check, then push the branch. Never push `main`, never merge the branch into the local `main`.
4. After the cloud commit the owner fast-forwards `main` (D-017); pull, rebuild the runner jar (`docs/inputs/README.md` §1), resume.
5. Record the new hashes in the profile (`templates/profile.md`, "Tool and skill versions"): `M-<n> — runner jar <git hash> — skills/sap-po-migration <git hash>`.

## Cloud side of the loop

At session start the cloud architect (WORKFLOW §1 step 1, §11; spec §9):

1. Fetches every `field/*` branch and merges each into the designated branch: a merge, never a rewrite. A merge conflict from a `field/*` branch can touch only `requests/` (the index table); the architect resolves it in the merge commit; anything outside `requests/` is an issue for the owner (WORKFLOW §11, D-035).
2. Reads `requests/`; each `open` request becomes a BACKLOG row of its kind, titled by the request id:
   - `tool`: code row (red test from the fixture, implementer, reviewer).
   - `kb`, `skill`: content row (page packet, kb-author, kb-reviewer).
   - `pattern`: content row; the kb-author writes `patterns/<id>.md` from the generic text, kb-reviewer review (`patterns/README.md`, "Page contract"), migrations cited `[migration M-<n>, <date>]` (D-030).
3. Sets the index status to `row <id>`; the commit after the one that closes the row writes `done <hash>` (D-035).

Acceptance is the request's own ("Acceptance" in `templates/request.md`). Until the spec §9 automation is on, the owner starts the cloud session by hand.

## Urgent requests

1. A request the migration cannot continue without sets `urgent` to yes in its header.
2. Urgent: a stop the migration cannot pass, e.g. the tool cannot read an input the plan needs.
3. Not urgent: a wish, a wording fix, a pattern for later, a gap a recorded assumption worked around.
4. The cloud inserts its row as the first `ready` row of BACKLOG, with no depends-on (WORKFLOW §1 step 1). There is no other fast path: no local edit of `src/`, a page or `patterns/`, no push to `main` (spec §9).
