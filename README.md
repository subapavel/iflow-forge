# po-xray

Reader of SAP PO content for migration to SAP Integration Suite. Phase 1 reads an NWDI software component
archive (SCA): NW BPM processes (Galaxy model) and EJB modules (BPM functions, adapter modules, XI proxies,
scheduler jobs), and produces a JSON inventory plus generated documentation.

| Module | Content |
|---|---|
| `po-xray-core` | Library: SCA/MOIN/Galaxy readers, EJB reader, JSON output model, documentation generator |
| `po-xray-cli` | Command line interface (Quarkus + Aesh), JVM uber-jar or native executable |
| `po-xray-mcp` | MCP server (STDIO) with the same readers as tools for AI agents, JVM uber-jar or native executable |
| `po-xray-native` | Reflection registration of the output model for native images (shared by CLI and MCP) |

Docs: [output model](docs/OUTPUT-MODEL.md), [built-in function catalog](docs/BPM-BUILTIN-FUNCTIONS.md),
[exploration handoff](docs/HANDOFF.md).

This repository also hosts iflow-forge, which builds SAP Integration Suite iFlows on the SAP pipeline concept from SAP PO Integrated Configurations (read by po-xray) and from existing point-to-point iFlows. Its first part is a pipeline knowledge base under `knowledge/pipeline/`, derived from SAP's own packages and offered to agents as the skill `sap-is-pipeline`. Design: [knowledge base spec](docs/superpowers/specs/2026-10-19-pipeline-knowledge-base-design.md); build plan: [knowledge base plan](docs/superpowers/plans/2026-10-19-pipeline-knowledge-base.md).

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
| `iflow <path> [--json]` | Integration Suite artifacts (iFlow folder or zip, package export, or a folder of these): channels, steps in flow order, parameters, resources |
| `unpack <package> <out-dir> [--strip a,b]` | Package export into `<out-dir>/<symbolic-name>/` + `CHANGELOG.txt`, without `parameters.prop` and `Configured_Custom_Tags.txt`; tokens stripped from symbolic names |

A one-line summary and written files are reported on standard error; standard output carries only command
output (JSON). Exit code 0 = success, non-zero = error.

### ESR / Integration Directory (online)

| Command | Purpose |
|---|---|
| `components -s <system> [-n pattern] [--json]` | Communication components; name filter is case-insensitive (text = contains, `*` `?` = wildcards) |
| `ico -s <system> -c <component> [-d in\|out\|both] [-f json\|markdown\|jira\|all] [-l cs\|en] [-o dir]` | ICOs to/from a component with channels and ESR objects (interfaces, operation/message mappings, message and data types); also classic configuration (receiver / interface determinations, agreements) |
| `search -s <system> -t <types> [-n pattern] [--namespace pattern] [--json]` | Objects of any type by name, e.g. `-t messageMapping,operationMapping -n eportal` |
| `show -s <system> -t <type> -n <name> [--namespace ns]` | One object of any type as JSON (dedicated view or links + raw content) |
| `where-used -s <system> -t <type> -n <name> [--namespace ns] [--json]` | Objects referencing it, e.g. message mappings using a function library, ICOs using a channel |

Types are given as aliases (`messageMapping`, `operationMapping`, `serviceInterface`, `messageType`, `dataType`,
`externalDefinition`, `functionLibrary`, `importedArchive`, `ico`, `channel`, `component`, `receiverDetermination`,
`interfaceDetermination`, `senderAgreement`, `receiverAgreement`, `valueMapping`, `iflow`, ...) or ESR / Directory
type IDs (`XI_TRAFO`, `RoutingRelation`, ...).

The system is configured outside the repository in `~/.po-xray/systems/<system>.properties`:

```properties
url=http://host:port
user=...
password=...
```

A system available only as TPZ exports (no network access to it) is configured with a directory instead of a URL —
no user or password needed:

```properties
tpz=D:/customers/<customer>/tpz-prod
```

All commands and MCP tools then read the `.tpz` files of that directory **including subdirectories** (ESR and
Directory exports side by side, organised freely, e.g. one subdirectory per object type). Files load in relative
path order and an object occurring in several files is taken from the last one, so date-stamped export names make
updates predictable: a new export is just a new file in the directory. Keep replaced exports outside the directory,
they would be loaded too. TPZ exports do not carry environment-specific channel attributes (URLs, users) or passwords;
results carry a note. Export in the Integration Builder: ESR *Tools → Export Design Objects* per SWCV (including
underlying/common SWCVs), Directory *Tools → Export Configuration Objects* per configuration scenario or folder
(an ICO exported alone does not include its channels and components).

Optional (online systems): `truststore=windows|jdk|<file>` (+ `truststorePassword`) for HTTPS — by default a
certificate is accepted if the JDK certificates or, on Windows, the Windows certificate store trust it (company CAs,
as in the browser) — and `proxy=host:port`. Environment variables override the file: `POXRAY_<SYSTEM>_USER` / `POXRAY_<SYSTEM>_PASSWORD`
for one system (name upper case, other characters as `_`, e.g. `POXRAY_PO_DEV_PASSWORD` for `po-dev`), then
`POXRAY_USER` / `POXRAY_PASSWORD` for all systems. A read-only user is enough (roles `SAP_XI_DISPLAY_USER_J2EE`,
`SAP_XI_API_DISPLAY_J2EE`). Objects read from the system are kept as raw `xiObj` XML in `~/.po-xray/cache/<system>`
(`--refresh` re-reads them, `--cache <dir>` changes the location): the first `ico` query reads all ICOs, later
queries work from the snapshot. Before the first object is taken from the snapshot, the system is asked once which
objects changed since the last check; changed objects are read again (state in `sync.properties`).

## Native executable

Windows (GraalVM 25 + Visual Studio Build Tools with the C++ workload), from a shell prepared by `vcvars64.bat`:

```bash
"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
```

```bash
mvn -pl po-xray-cli,po-xray-mcp -am package -Dnative -DskipTests
```

Linux executable built in a container (Docker), on any OS:

```bash
mvn -pl po-xray-cli,po-xray-mcp -am package -Dnative -DskipTests -Dquarkus.native.container-build=true
```

The executables are `po-xray-cli/target/po-xray-cli-<version>-runner(.exe)` and
`po-xray-mcp/target/po-xray-mcp-<version>-runner(.exe)`. Classes serialised by Jackson are registered for reflection
in the shared module `po-xray-native` (`NativeReflection`, guarded by a test; indexed by the applications with
`quarkus.index-dependency`), MCP result records in `po-xray-mcp` (`McpReflection`); resources of the core (function
catalog, document labels) are included via `quarkus.native.resources.includes`.

### Windows package without Java

```bash
powershell -ExecutionPolicy Bypass -File tools/dist-windows.ps1
```

creates `dist/po-xray-<version>-windows-x64.zip` with `po-xray.exe`, `po-xray-mcp.exe`, the Visual C++ runtime DLLs
they need (app-local, nothing to install) and `INSTALL.txt`. Native images are built with `-march=compatibility`
(any x86-64 CPU) and HTTPS support (`quarkus.ssl.native`).

## MCP server

`po-xray-mcp` exposes the readers as MCP tools (STDIO transport), built by `mvn verify` as
`po-xray-mcp/target/po-xray-mcp-<version>-runner.jar`. Tools:

| Tool | Purpose |
|---|---|
| `find_components` | communication components of a PO system by part of the name |
| `list_icos` | ICOs to / from a component: routing conditions, interface determinations, channels, names of ESR objects |
| `get_object` | one object of that inventory: ICO, channel, service interface, operation / message mapping (field logic, UDFs), function library, message / data type |
| `search_objects`, `read_object`, `where_used` | any object type independent of ICOs: search by name, read one, find its users |
| `bpm_integration` | cross-reference of the BPM processes of an SCA with a PO system: interfaces a process uses (inbound / outbound, ESR operation mapping called) and the ICOs, determinations, agreements and mappings referencing them |
| `write_integration_documentation` | Markdown / Jira / JSON files of the ICO inventory |
| `read_sca`, `get_sca_object` | overview of an SCA, then a process outline, single steps with their mappings, process data, catalogs, EJB modules, issues |
| `write_sca_documentation` | Markdown / Jira / JSON files of an SCA |

Results are deliberately small (compact JSON, overview first, details on request): MCP clients truncate large tool
results. A result above the client limit is written to `<temp>/po-xray/<tool>-*.json` as indented JSON and the tool
returns the file path instead. When the PO system cannot be reached (network / VPN), listings come from the snapshot and the result carries
a warning (`OFFLINE_SNAPSHOT` issue, `warnings` of `find_components`).
PO systems are the ones configured in `~/.po-xray/systems/<name>.properties` (see CLI); the snapshot cache is shared
with the CLI.

Register in Claude Code (absolute path to the jar):

```bash
claude mcp add po-xray -- java -jar C:/Users/pavol/Documents/Projekty/po-xray/po-xray-mcp/target/po-xray-mcp-0.1.0-SNAPSHOT-runner.jar
```

With the native executable (faster start, rebuild it after changes):

```bash
claude mcp add po-xray -- C:/Users/pavol/Documents/Projekty/po-xray/po-xray-mcp/target/po-xray-mcp-0.1.0-SNAPSHOT-runner.exe
```

Claude Desktop chat uses its own configuration, not the Claude Code one: add the server to
`%APPDATA%\Claude\claude_desktop_config.json` (top level, next to `preferences`) and restart Claude Desktop completely:

```json
"mcpServers": { "po-xray": { "command": "C:\\Tools\\po-xray\\po-xray-mcp.exe" } }
```

(or `"command": "java", "args": ["-jar", "…/po-xray-mcp-<version>-runner.jar"]` with a JVM).

## Confidentiality

`testdata/` holds client SCAs and exports: never commit it. The built-in function catalog is derived from SAP
software (NWDS): private repositories only.
