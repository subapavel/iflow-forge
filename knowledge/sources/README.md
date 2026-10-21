# SAP pipeline package sources

Unpacked SAP Integration Suite packages of the pipeline concept, one folder per export, one subfolder per
package, one per artifact (named by `Bundle-SymbolicName`). Each package folder has `CHANGELOG.txt` (SAP's own
change log) and each export an `INDEX.md` (artifact, type, symbolic name, version).

| Label | Origin | Content |
|---|---|---|
| `2026-06` | Package export, June 2026 | 29 artifacts: generic inbound (2), generic pipelines (5), generic outbound (3), templates (18) + script collection |
| `2026-09` | Package export, September 2026, **scrubbed** (see below) | 32 artifacts: same plus 2 new templates/helpers and a value mapping (Templates 1.2.0) |

What was removed or changed when staging:

- `parameters.prop` (configured externalized-parameter values) is never staged; `parameters.propdef` (SAP
  defaults) is. In both exports the configured values were SAP defaults anyway.
- `Configured_Custom_Tags.txt` (tenant tags) is never staged.
- In `2026-09` the tenant had copied the packages under its own names: a tenant prefix on package
  names and a tenant suffix on `Bundle-SymbolicName` (manifest and `.project`), and a creation timestamp suffix
  on three artifacts added by the 1.2.0 update. Those suffixes were stripped so the tree matches SAP's naming.
  Nothing else was touched; `.iflw`, scripts, mappings, XSD and `.propdef` files are byte-identical to the export.
- `2026-09` is to be replaced by a clean catalogue download of the same versions when available; the
  expected diff is empty.

Windows checkouts need `git config core.longpaths true` (paths exceed 260 characters). Line endings are stored
as exported (`.gitattributes`: `-text`).

Versions per artifact are in `INDEX.md`; package change-log versions (e.g. Generic Pipelines 1.0.4 → 1.0.5,
Templates 1.1.1 → 1.2.0) are the version of the first dated entry of each `CHANGELOG.txt` (line 5; line 1 is the title).

The staging script (`stage.py`) is local and not part of the repository; `po-xray unpack <package.zip> <out-dir> [--strip a,b]`
(po-xray-cli, KB-02) reproduces this layout from a package zip.
