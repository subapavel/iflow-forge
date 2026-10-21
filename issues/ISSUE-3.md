# ISSUE-3 — Customer identifiers in the po-xray tests and reader documentation (owner-only)
Task: none blocked (found while neutralising the customer name in the part-1 spec, 2026-10-20)
Date: 2026-10-20

## What is needed
A decision on the client-project identifiers that pre-date CLAUDE.md's "no customer data" rule and sit
outside `testdata/`: the po-xray test classes named after client projects (`po-xray-core/src/test/java/
io/poxray/bpm/DigiSignTest.java`, `PpasOteTest.java`, and references in `BpmIntegrationTest`,
`IntegrationReaderTest`, `TpzGatewayTest`, `TpzArchiveTest`, `DocWritersTest`, `po-xray-mcp ViewsTest`,
`ObjectCatalogTest.java:152`), the example values in `docs/OUTPUT-MODEL.md` (DC names, Java packages,
RFC function, JCo destination, table and persistence-unit names of the client), `docs/HANDOFF.md` and
`docs/SOURCES-ESR-DIRECTORY.md`, and the tenant fragment literal in `BenchmarkFixtureTest.java:47`.
The 2026-10-20 commit neutralised the customer name in the part-1 spec, plan, backlog, decisions and
PROGRESS only (markdown; the architect does not edit `src/`).

## Options
(a) A code task: rename the test classes to what they test (BPM reader on a local SCA, OTE routing
sample), replace the OUTPUT-MODEL example values with `example.com` / `com.example` names, and make
the fragment literal a `TestData` property. (b) Accept the identifiers as project names of a private
repository and record that in `docs/DECISIONS.md`. (c) Something else.

## Status
Open. Not blocking; a BACKLOG row is added as `blocked` on this issue.

## Scope addendum (2026-10-21, found during MIG-18)
The same two identifiers (the customer name, the PO system name) also sit in: `README.md:76` and `:91` (neutralised
2026-10-21 by the architect — a documentation example, not in the list above), `tools/dist-windows.ps1:35`, the javadoc
of `po-xray-core/src/main/java/io/poxray/po/PoSystem.java:20`, `integration/Secrets.java:11`, `IntegrationQuery.java:34`,
`MappingLogicReader.java:32`, `IntegrationReader.java:213`, `ObjectCatalog.java:44`, `ArchiveReader.java:31`,
`sca/DcDefinition.java:45`, `xi/XiBlob.java:15`, the tests `po/PoClientTest.java:134–153`, `po/TpzGatewayTest.java:70–133`,
`integration/ArchiveReaderTest.java:26`, `MappingLogicReaderTest.java:22`, `CachingObjectSourceTest.java:41`, and the
system hostname in `docs/SOURCES-ESR-DIRECTORY.md:3`. The hostname is the one item CLAUDE.md names explicitly; option (a)
should take it first. `git grep -n -i -w '<name>\|<system>'` over tracked files outside `testdata/` and `local/` is the check.
