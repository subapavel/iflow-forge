# ISSUE-4 — PO adapter attribute names for the migration fixture (owner-only)
Task: MIG-17 (not blocked: the fixture carries `synthetic.<setting>` keys meanwhile, D-043 b)
Date: 2026-10-21

## What is needed
The attribute NAMES (never values) that po-xray exports in `channels[].attributes` for a sender XI channel, a sender SOAP
channel, a receiver XI or IDoc channel and a receiver File or SFTP channel — the `Attribute Name` of
`ChannelAttributes/AdapterTypeData/Attribute` as they appear in a real dump under `testdata/`. Names only; a value, a host,
a user or a credential alias must not be copied.

## Why the owner
The repository holds verifiable attribute names for REST channels only (`po-xray-core/src/test/java/io/poxray/integration/
IntegrationReaderTest.java:78–83`, `docs/OUTPUT-MODEL.md:437`). The real names live in customer dumps under `testdata/`,
which the agents may not read into the repository (CLAUDE.md "No customer data"), and SAP's documentation pages were not
reachable from the session (2026-10-21). Writing PO-looking names from memory would plant unverifiable facts in a fixture
the skill's dry runs treat as a dump.

## What happens when it is answered
A code task replaces the `synthetic.*` keys of the five channels in `skills/sap-po-migration/fixtures/icos-SND_SYS.json`
with the real names (values stay synthetic), `AttributesCommandTest.everyFixtureChannelCarriesSyntheticAttributes` keeps
passing (it checks values, not key names), and `references/intake.md` may name the keys a migrator looks for.

Note: po-xray masks the value of any attribute whose name matches its secret pattern (`credential`, `token`, `secret`, …) as `<hidden>`
(`po-xray-core/src/main/java/io/poxray/integration/Secrets.java`), so a real key list should say which names arrive masked; the skill asks for those.
