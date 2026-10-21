# Local inputs — how the owner hands over customer artifacts

Customer artifacts never enter the repository (CLAUDE.md). They live under `testdata/` at the clone root,
which `.gitignore` excludes (line 2). A cloud session never sees `testdata/`; work that needs it runs in a
local Claude Code session in the same clone, where the tests that need it stop skipping (`TestData`).

## 1. Build the CLI once

`mvn -B -ntp -q verify` (or `mvn -pl po-xray-cli -am package -DskipTests`) produces
`po-xray-cli/target/po-xray-cli-<version>-runner.jar`. Below `po-xray` stands for `java -jar <that jar>`.

## 2. BPM processes and EJB modules — NWDI software component archives

- Export from NWDI the software component that holds each BPM process and the EJB development components it
  calls (the EJB module DC and its EAR DC are classified by `EjbReader`; adapter modules get the role
  `adapterModule`, BPM functions `bpmFunction`). One `.sca` per software component.
- Put them in `testdata/bpm/<component>.sca`.
- Check each one: `po-xray export testdata/bpm/<component>.sca -o testdata/bpm/out/<component>` writes the
  JSON model and the documentation; `docs/OUTPUT-MODEL.md` describes what to expect (processes, data objects,
  nodes, flows, expressions, mappings, EJB inventory). A failing export is a reader bug: keep the archive and
  report the error text.

## 3. ICOs around the process — online or TPZ

- Online: `~/.po-xray/systems/<name>.properties` with `url=https://<po-host>:<port>`, optionally
  `truststore`, `truststorePassword`, `proxy`. Credentials go to the environment, never into the file:
  `POXRAY_USER` and `POXRAY_PASSWORD` (or `POXRAY_<NAME>_USER` / `POXRAY_<NAME>_PASSWORD` per system, `<NAME>` upper case, other characters as `_`; `PoSystem.envPrefix`).
- Offline: the same file with `tpz=<directory of .tpz exports>` (relative to `~/.po-xray/systems/`), holding
  the Integration Directory and ESR exports of the objects.
- Then, per communication component of the process (sender and receiver side, wildcards allowed):
  `po-xray ico -s <name> -c '<component or *pattern*>' -d both -o testdata/ico/<component>`. It writes the
  ICOs, channels with modules, operation and message mappings, UDF code and imported archives; a snapshot is
  cached under `~/.po-xray/cache/<name>` (`--refresh` re-reads).

## 4. What to tell the agent

The pilot process, the names of its communication components, and which `.sca` holds its EJB modules. The
agent then reads `testdata/bpm/out/` and `testdata/ico/` in the local session and starts the question round.
