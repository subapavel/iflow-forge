# MIG-25 — P3a documentation packet

Status: accepted after implementation release; independent documentation review r1 PASS and final root checks passed.
Base: 125865f; governing source contract is the sibling mig-25-p3a-packet.md, D-050.
Read docs/LOCAL-WORKFLOW.md first. One writer, no nested delegation, no git or ledger edits.

## Allowed paths and inputs

Edit only docs/OUTPUT-MODEL.md, skills/sap-po-migration/references/bpm.md and
skills/sap-po-migration/references/intake.md. Check the repository mirror convention; do not
edit an additional mirror without architect authorization. No SKILL frontmatter or templates.
Inputs: canonical P3a packet, actual BpmSourceEvidence/BpmComponent/BpmEvidenceReader,
DocumentBuilder and its new synthetic tests. Read relevant sections, not whole large files.
No private sources, tenant actions, new pipeline knowledge claims or unrelated wording cleanup.

## Required content

OUTPUT-MODEL describes the optional independently versioned extension, exact outer and nested
field meanings, selectionVersion bpm-source-v1 and locator archive/partition/list ordinal.
Explain occurrence identity, duplicate IDs/links, unique/missing/ambiguous endpoints and the
difference between a boundary reference and a selected full payload. Document explicit empty
collections/null fields, ordered nullable Property entries and the null-key diagnostic.
Selection includes candidate process partitions even when no Process can be interpreted.
Explain direct support selection and absence of recursive WSDL/XSD expansion.

State this is parsed-source evidence: parser normalization/loss and source digest remain relevant;
it is neither byte-exact XML nor proof of runtime execution. Existing model meanings and shared
schema0.1 remain unchanged. Old JSON lacks evidence; a new explicit empty selection is different.
The three-argument constructor remains source-compatible; adding a record component does not
guarantee binary record-pattern or strict old JSON consumer compatibility.

Document Markdown evidence summaries, raw flags/directed link status/locators, initial values and
structural assignments, including disabled rows. Blank expression does not mean deletion.
References bpm/intake guide users to existing json/export output and its limitations: no new
behavior command or direct MCP view exists in P3a; no current runtime, reachability, name-to-ID
binding, loop execution, fault interception or producer analysis is claimed.

## Checks and handback

Follow existing public citation and portability rules. Link to actual implemented APIs and
existing commands; do not introduce a new public citation syntax or refer to private evidence.
Run allowed-path whitespace/link checks, check no new private identifiers and preserve all
unrelated changes. Author need not repeat full Maven; the independent verifier runs the final
reactor with skill mirror/citation checks after all changes. Report observed checks only.
Hand back at most 40 lines with changed paths, key boundaries, exact checks/results/log paths,
remaining uncertainty and writer release. Independent documentation reviewer must verify
claims against source/tests and canonical packet before the MIG-25 commit.
