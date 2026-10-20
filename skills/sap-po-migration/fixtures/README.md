# Synthetic fixtures of the migration helpers

All files are synthetic: example names only, no customer, tenant or system data.

- `icos-SND_SYS.json` — an `IntegrationInventory` with three ICOs: a sync XI scenario (BE), an async IDoc
  scenario with two receivers, one of them conditional, and an async SOAP scenario with extended receiver
  determination, a sender module and a one-to-many mapping.
- `bpm-export/SYNTH_SCA.json` — a `ScaInventory` with one NW BPM process (one human task) and one EJB module.
- `bpm-export/notes.json` — not an inventory; the helper reports it and skips it.

Run from the repository root:

    po-xray attributes skills/sap-po-migration/fixtures/icos-SND_SYS.json
    po-xray bpm-inventory skills/sap-po-migration/fixtures/bpm-export

Add `--json` for the JSON report.
