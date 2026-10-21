# Quick start — one BPM process and its ICOs

## Before the first run

1. Build the jar once; it lands in `po-xray-cli/target/` (docs/inputs/README.md:9–10; docs/FIRST-REAL-RUN.md#Prepare the machine).

```bash
mvn -B -ntp -q -pl po-xray-cli -am package -DskipTests
```

2. Create `~/.po-xray/systems/<system>.properties` with one `url=` line; user and password go into the environment as `POXRAY_<SYSTEM>_USER` and `POXRAY_<SYSTEM>_PASSWORD`, never into the file (docs/FIRST-REAL-RUN.md#Prepare the machine).
3. Create `local/<customer>/profile.md` from `skills/sap-po-migration/templates/profile.md`, writing `unknown` for what you do not know; without it the skill creates it and asks T-1 to T-4 first [page: skills/sap-po-migration/references/intake.md#Tenant profile].

## Dumps

4. Export each BPM software component archive; `-l en` because the language defaults to `cs` (docs/FIRST-REAL-RUN.md#Produce the dumps).

```bash
java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar export testdata/bpm/<component>.sca -l en -o testdata/bpm/out/<component>
```

5. Dump the ICOs once per component the process receives from and sends to, the ICO before the process included (docs/FIRST-REAL-RUN.md#Produce the dumps; [page: skills/sap-po-migration/references/intake.md#Inputs present and missing]).

```bash
java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar ico -s <system> -c <component> -d both -l en -o testdata/ico/<component>
```

## Run

6. Open Claude Code at the clone root and send this; the skill allocates `M-1` in a new `local/migrations.md` and starts at phase 1 (docs/FIRST-REAL-RUN.md#Start the skill; [page: skills/sap-po-migration/references/intake.md#Migration code]).

```text
Read skills/sap-po-migration/SKILL.md and start a migration. Customer: <customer>. Target: the BPM process (its name) in testdata/bpm/out/<component>, its EJB modules in testdata/bpm/<component>.sca, its communication components: <component>, <component>.
```

7. Answer one question per message, then approve the plan yourself: write `approved` and the date into its approval section [page: skills/sap-po-migration/references/questions.md#One question at a time] [page: skills/sap-po-migration/references/plan.md#Approval].
8. Read the outputs: `intake.md`, `analysis.md`, `questions.md`, `plan.md` and one `scenario-<k>.md` per pipeline scenario [page: skills/sap-po-migration/SKILL.md#Phases].

```bash
ls local/<customer>/scenarios/M-1/
```

## When it stops or ends

- **Human task stop**: a human task in the process ends the migration as out of scope; `analysis.md` records it, no plan is written, the retrospective still runs [page: skills/sap-po-migration/references/bpm.md#Human task stop].
- **Artifacts placeholder**: phase 6 is a named placeholder until the generator exists and writes "not available" into `retrospective.md`; the approved plan and the scenario specifications are the deliverable [page: skills/sap-po-migration/SKILL.md#Phases].

## Then

The retrospective files each lesson the cloud has to act on as a request under `requests/` [page: skills/sap-po-migration/references/retrospective.md#Requests by kind]; push the field branch only after the forbidden-values check passes (docs/FIRST-REAL-RUN.md#After the run: retrospective, requests, field branch; [page: skills/sap-po-migration/references/requests.md#Forbidden-values run before a push]).
