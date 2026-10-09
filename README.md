# po-xray

Reader of SAP PO content for migration to SAP Integration Suite. Phase 1 reads an NWDI software component
archive (SCA): NW BPM processes (Galaxy model) and EJB modules (BPM functions, adapter modules, XI proxies,
scheduler jobs), and produces a JSON inventory plus generated documentation.

| Module | Content |
|---|---|
| `po-xray-core` | Library: SCA/MOIN/Galaxy readers, EJB reader, JSON output model, documentation generator |
| `po-xray-cli` | Command line interface (Quarkus + Aesh), JVM uber-jar or native executable |

Docs: [output model](docs/OUTPUT-MODEL.md), [built-in function catalog](docs/BPM-BUILTIN-FUNCTIONS.md),
[exploration handoff](docs/HANDOFF.md).

## Build

Requirements: JDK 25, Maven 3.9.

```bash
mvn verify
```

Produces `po-xray-cli/target/po-xray-cli-<version>-runner.jar` (self-contained).

Tests that need confidential client data from `testdata/` (gitignored, never committed) are skipped when the
directory is missing; `-Dpoxray.testdata=<dir>` points them elsewhere.

## CLI

```bash
java -jar po-xray-cli/target/po-xray-cli-0.1.0-SNAPSHOT-runner.jar --help
```

| Command | Purpose |
|---|---|
| `json <file.sca> [-o file]` | Inventory as JSON (standard output by default) |
| `doc <file.sca> [-f markdown\|jira\|all] [-l cs\|en] [-o dir]` | Documentation (Markdown and/or Jira wiki markup) |
| `export <file.sca> [-l cs\|en] [-o dir]` | JSON + both documentation formats into a directory |

A one-line summary and written files are reported on standard error; standard output carries only command
output (JSON). Exit code 0 = success, non-zero = error.

### ESR / Integration Directory (online)

| Command | Purpose |
|---|---|
| `components -s <system> [-n pattern] [--json]` | Communication components; name filter is case-insensitive (text = contains, `*` `?` = wildcards) |
| `ico -s <system> -c <component> [-d in\|out\|both] [-f json\|markdown\|jira\|all] [-l cs\|en] [-o dir]` | ICOs to/from a component with channels and ESR objects (interfaces, operation/message mappings, message and data types) |

The system is configured outside the repository in `~/.po-xray/systems/<system>.properties`:

```properties
url=http://host:port
user=...
password=...
```

`POXRAY_USER` / `POXRAY_PASSWORD` override the file. A read-only user is enough (roles `SAP_XI_DISPLAY_USER_J2EE`,
`SAP_XI_API_DISPLAY_J2EE`). Objects read from the system are kept as raw `xiObj` XML in `~/.po-xray/cache/<system>`
(`--refresh` re-reads them, `--cache <dir>` changes the location): the first `ico` query reads all ICOs, later
queries work from the snapshot.

## Native executable

Windows (GraalVM 25 + Visual Studio Build Tools with the C++ workload), from a shell prepared by `vcvars64.bat`:

```bash
"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
```

```bash
mvn -pl po-xray-cli -am package -Dnative -DskipTests
```

Linux executable built in a container (Docker), on any OS:

```bash
mvn -pl po-xray-cli -am package -Dnative -DskipTests -Dquarkus.native.container-build=true
```

The executable is `po-xray-cli/target/po-xray-cli-<version>-runner(.exe)`. Classes serialised by Jackson are
registered for reflection in `po-xray-cli` (`NativeReflection`, guarded by a test); resources of the core
(function catalog, document labels) are included via `quarkus.native.resources.includes`.

## Confidentiality

`testdata/` holds client SCAs and exports: never commit it. The built-in function catalog is derived from SAP
software (NWDS): private repositories only.
