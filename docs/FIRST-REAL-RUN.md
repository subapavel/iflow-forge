# First real run — migrating one SAP PO scenario with the sap-po-migration skill

Short version for one BPM process and its ICOs: `docs/QUICK-START.md`.

For you, on your own machine: the local side of the field loop (docs/WORKFLOW.md:215–217), running the skill
`sap-po-migration` against a real SAP PO system for the first time.

## What exists today (and what does not)

- The skill runs seven phases; a phase starts only when the previous exit holds [page: skills/sap-po-migration/SKILL.md#Phases].
- Phase 6, Artifacts, is a named placeholder, and parts 3 and 4 have no BACKLOG rows yet (agent/BACKLOG.md, section "Phases READER, GENERATOR, CONVERSION — parts 3, 4, 5").
  Your run ends with an approved plan and scenario specifications under `local/<customer>/scenarios/M-<n>/`.
  Nothing is generated (skills/sap-po-migration/SKILL.md:37, :43; [spec §7]), so there is nothing to push to a tenant.
- The pilot, MIG-07, is pending (agent/BACKLOG.md, row MIG-07). So far the skill has run only over synthetic fixtures
  (skills/sap-po-migration/fixtures/dry-run-2026-10-21.md, "Phases reached"; [spec §11]).
- The knowledge pages describe Generic Inbound 1.0.7, Generic Pipelines 1.0.4, Generic Outbound 1.0.3 and Templates
  1.1.1 (knowledge/sources/2026-06/INDEX.md:3, :16, :14, :36; D-019; D-044). Package versions are the same on
  every tenant [owner, 2026-10-21]. What 1.0.8, 1.0.5, 1.0.5 and 1.2.0 add is listed
  [page: knowledge/pipeline/versions/features.md#Features by package] (knowledge/sources/2026-09/INDEX.md:6,
  :19, :15, :41). KB-00a, still pending, moves the pages to the newest catalogue download (agent/BACKLOG.md, row KB-00a). Some
  runtime behaviour stays open (knowledge/pipeline/open-questions/kb-12b.md:3–4); expect such items on your open list.
- The fixture's channel attribute keys are synthetic (issues/ISSUE-4.md:2); a real dump carries the real names
  (issues/ISSUE-4.md:6–8).

## Prepare the machine

1. Install JDK 25 and Maven 3.9 (README.md:21).
2. Build once at the clone root (docs/inputs/README.md:9); the jar lands in `po-xray-cli/target/` (README.md:27, :35).

```bash
mvn -B -ntp -q verify
```

3. Create `~/.po-xray/systems/<system>.properties` (README.md:64); the code reads it from your home directory
   (po-xray-core/src/main/java/io/poxray/po/PoSystem.java:68–69, :77).
   - Online: a `url=` line, else the load fails (po-xray-core/src/main/java/io/poxray/po/PoSystem.java:97–100); optional `truststore`, `truststorePassword`,
     `proxy` (README.md:88).
   - Offline: one line `tpz=<directory of .tpz exports>`, no user or password; a relative directory starts at
     `~/.po-xray/systems/` (po-xray-core/src/main/java/io/poxray/po/PoSystem.java:92–96). TPZ exports carry no environment-specific channel attributes and no
     passwords (README.md:83).
4. Credentials (online only). The code takes user and password from the first source that has one:
   `POXRAY_<SYSTEM>_USER` / `POXRAY_<SYSTEM>_PASSWORD`, then `POXRAY_USER` / `POXRAY_PASSWORD`, then `user=` /
   `password=` in the properties file (po-xray-core/src/main/java/io/poxray/po/PoSystem.java:102–104). `<SYSTEM>` is the system name in upper case, every
   character other than a letter or digit as `_` (po-xray-core/src/main/java/io/poxray/po/PoSystem.java:113–116). With none, the load fails and names the
   variables (po-xray-core/src/main/java/io/poxray/po/PoSystem.java:105–107). A `password=` line works, but the environment overrides it. The hand-over guide
   says credentials go to the environment, never into the file (docs/inputs/README.md:25–27): do that. The system's
   printed form shows the user, never the password (po-xray-core/src/main/java/io/poxray/po/PoSystem.java:168–171). A read-only user is enough (README.md:92–93).
5. BPM target: put each NWDI software component archive in `testdata/bpm/<component>.sca` (docs/inputs/README.md:14–17).

## Produce the dumps

Run from the clone root. Summaries and written files go to standard error; standard output carries only command
output (README.md:46–47). Find the exact component name (README.md:53):

```bash
java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar components -s <system> -n <component>
```

BPM target: export each archive (docs/inputs/README.md:18). The language defaults to `cs`, so pass `-l en`
(po-xray-cli/src/main/java/io/poxray/cli/ExportCommand.java:21–23). It writes the JSON model plus `.md` and `.jira.txt` documentation (po-xray-cli/src/main/java/io/poxray/cli/ExportCommand.java:36–39;
po-xray-core/src/main/java/io/poxray/doc/DocFormat.java:6–7). A failing export is a reader bug: keep the archive, report the error text (docs/inputs/README.md:20–21).

```bash
java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar export testdata/bpm/<component>.sca -l en -o testdata/bpm/out/<component>
```

ICOs, once per communication component, sender and receiver side (docs/inputs/README.md:30–31). `-c` takes a name or a
wildcard pattern (po-xray-cli/src/main/java/io/poxray/cli/IcoCommand.java:31–33); `-d` defaults to `both`, `-f` to `all`, `-l` to `cs` (po-xray-cli/src/main/java/io/poxray/cli/IcoCommand.java:35–45).

```bash
java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar ico -s <system> -c <component> -d both -l en -o testdata/ico/<component>
```

It writes `icos-<component>.json`, `.md` and `.jira.txt` (po-xray-cli/src/main/java/io/poxray/cli/IcoCommand.java:75–87); in the name every character other than
a letter, digit, `_`, `.` or `-` becomes `_` (po-xray-cli/src/main/java/io/poxray/cli/PoService.java:33–35). No match prints a hint to `components`
(po-xray-cli/src/main/java/io/poxray/cli/IcoCommand.java:71–73). The first query reads all ICOs into `~/.po-xray/cache/<system>`; later queries use that
snapshot, `--refresh` re-reads (README.md:93–96; po-xray-cli/src/main/java/io/poxray/cli/IcoCommand.java:50–54).

Optional: the two helpers the skill runs at intake
([page: skills/sap-po-migration/references/intake.md#Inputs present and missing]). Both print Markdown, `--json` JSON
(po-xray-cli/src/main/java/io/poxray/cli/AttributesCommand.java:29–30; po-xray-cli/src/main/java/io/poxray/cli/BpmInventoryCommand.java:27–28); `bpm-inventory` fails on a directory without a BPM or
EJB inventory (po-xray-cli/src/main/java/io/poxray/cli/BpmInventoryCommand.java:41–43).

```bash
java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar attributes testdata/ico/<component>/icos-<component>.json
java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar bpm-inventory testdata/bpm/out/<component>
```

This layout matches the profile's input-path defaults (skills/sap-po-migration/templates/profile.md:7–11).

## Start the skill

1. Open Claude Code at the clone root. The pointer `.claude/skills/sap-po-migration/SKILL.md` carries the skill's name
   and description and points to `skills/sap-po-migration/SKILL.md` (.claude/skills/sap-po-migration/SKILL.md:2–3, :6).
   Whether it loads is your first local step of MIG-07 [spec §11]. If the skill is not offered, tell the agent to read
   `skills/sap-po-migration/SKILL.md` first, as the pointer says.
2. Choose a customer code: the directory name under `local/`, never in a committed file [spec §5].
3. Tell the agent the target (a BPM process, or the ICOs), its communication components, and which `.sca` holds its EJB
   modules (docs/inputs/README.md:37–38; skills/sap-po-migration/SKILL.md:32).
4. Every migration starts at phase 1 [page: skills/sap-po-migration/SKILL.md#Start here].

## Phases 1–5: what you do, what lands where

Each phase's output goes under `local/<customer>/scenarios/M-<n>/` [page: skills/sap-po-migration/SKILL.md#Phases].

| phase | you | lands |
|---|---|---|
| 1 Intake | new profile: answer T-1 to T-4, "unknown" allowed [page: skills/sap-po-migration/references/intake.md#Tenant profile]; run any command named for a missing input [page: skills/sap-po-migration/references/intake.md#Inputs present and missing] | `intake.md`, `local/<customer>/profile.md`, a line in `local/migrations.md` [page: skills/sap-po-migration/references/intake.md#Migration code] |
| 2 Analysis | read | `analysis.md` with the open list |
| 3 Question round | answer one question per message [page: skills/sap-po-migration/references/questions.md#One question at a time] | `questions.md`, append-only; a profile value amends `profile.md` at once (step 6) |
| 4 Plan | write `approved <date>` yourself [page: skills/sap-po-migration/references/plan.md#Approval] | `plan.md` |
| 5 Scenario specification | check | `scenario-<k>.md` per pipeline scenario |

In phase 2 adapter settings come from `channels[].attributes`. Passwords are never exported; a value whose name looks
like a secret is masked as `<hidden>` and asked ([page: skills/sap-po-migration/references/intake.md#Reading order of the dumps];
docs/OUTPUT-MODEL.md:437, :509–510; po-xray-core/src/main/java/io/poxray/integration/Secrets.java:18–20). The skill
never assumes the newest pipeline version; it asks [spec §10].

The flow leaves this order only at two named stops (skills/sap-po-migration/SKILL.md:40–43):

- **Human task stop.** A human task found in phase 2 ends the migration as out of scope; no plan, phase 7 still runs
  [page: skills/sap-po-migration/references/bpm.md#Human task stop]; P-5.
- **Artifacts placeholder.** Phase 6 writes "not available" into `retrospective.md` (skills/sap-po-migration/SKILL.md:37);
  specification and plan are the deliverable (skills/sap-po-migration/SKILL.md:43).

## What never leaves your machine

- `testdata/` and `local/` are gitignored (.gitignore:2, :5); `~/.po-xray/`, with system files and cache, is outside
  the clone (po-xray-core/src/main/java/io/poxray/po/PoSystem.java:68–69; README.md:93–94).
- Customer, tenant, system, host and person names, configured values and customer files stay in `local/` and
  `testdata/`; a committed file
  names a migration only by `M-<n>` [page: skills/sap-po-migration/SKILL.md#Two rules]; P-2. `local/migrations.md` is
  the only map from code to customer (P-12).
- A failed command's error text stays in local `intake.md`; a request carries a synthetic reproduction
  [page: skills/sap-po-migration/references/intake.md#Inputs present and missing].
- The forbidden-values list lives outside the repository or under `local/`, e.g. `local/forbidden.txt`, one identifier
  per line [page: skills/sap-po-migration/references/requests.md#Forbidden-values run before a push]; docs/WORKFLOW.md:224–226.

## After the run: retrospective, requests, field branch

Phase 7 writes `retrospective.md`; the migration is done when profile amendments are listed, the scenario is filed and
any requests are on a field branch [page: skills/sap-po-migration/references/retrospective.md#Exit]. Each lesson for
the cloud becomes `requests/REQ-<n>.md` of kind `kb`, `skill`, `pattern` or `tool`; `tool` evidence is a synthetic
fixture, never your file [page: skills/sap-po-migration/references/retrospective.md#Requests by kind].

Field branch [page: skills/sap-po-migration/references/requests.md#Field branch]: branch from the current `main`,
commit only `requests/` paths, run the forbidden-values check, push the branch, never `main`.

```bash
git fetch
git switch -c field/<date> main
git add requests/
git diff --cached --name-only
git commit
mvn -B -ntp -q -pl iflow-forge-knowledge -am test -Dkb.forbidden=local/forbidden.txt
git push -u origin field/<date>
```

A hit blocks the push; a path naming no file skips the check, so confirm `skipped="0"` in
`iflow-forge-knowledge/target/surefire-reports/TEST-io.iflowforge.knowledge.ForbiddenValuesTest.xml`; entries under four characters need `git grep -n -i -w`
[page: skills/sap-po-migration/references/requests.md#Forbidden-values run before a push]; docs/WORKFLOW.md:227–230.

A request the migration cannot continue without carries `urgent`
[page: skills/sap-po-migration/references/requests.md#Urgent requests]. The cloud merges every `field/*` branch and
turns each open request into a BACKLOG row (docs/WORKFLOW.md:234–240; P-11). You never commit under `src/`,
`knowledge/pipeline/` or `patterns/` (P-6). After the cloud commit, fast-forward `main` (D-017), rebuild the jar, record
the hashes in the profile [page: skills/sap-po-migration/references/requests.md#Field branch].

## Which scenario first

- The spec's pilot is your first BPM process [spec §11]; MIG-07 is pending (agent/BACKLOG.md, row MIG-07). A single ICO as a
  warm-up is your choice.
- The dry runs covered customers `demo` and `demo3`: the fixture's one sender component `SND_SYS`
  (skills/sap-po-migration/fixtures/icos-SND_SYS.json:12), three ICOs, four pipeline scenarios, phases 1–5, no BPM
  process (skills/sap-po-migration/fixtures/dry-run-2026-10-21.md, "Phases reached", "Plan", "Third run (MIG-14): full
  run after the gap fixes"; skills/sap-po-migration/fixtures/README.md:5–7).
- A scenario with a receiver, a mapping and one or two channels gives phase 2 something to read in each of sender,
  receivers, channels and mappings [page: skills/sap-po-migration/references/intake.md#Reading order of the dumps].
