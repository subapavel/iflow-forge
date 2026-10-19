# Inputs: prior documentation, translated to English

Drafts for the knowledge base, **not sources of facts**. Every statement here must be checked against
`knowledge/sources/` before it enters `knowledge/pipeline/` (spec §5, §7, §13.3).

| Folder / file | Origin | Generation | State of the pipeline described |
|---|---|---|---|
| `confluence-2026-10/` | 39 pages of an internal Confluence space, exported to PDF on 2026-10-19, written in Czech by Pavol Šuba from a reverse analysis of the packages | b | mostly `2026-06-trial` versions (e.g. Step02 1.0.5, Step04 1.0.4), some pages updated |
| `analysis-2026-09.md` | Analysis of the September 2026 tenant export, written in Czech with ChatGPT on 2026-09-18 | c | `2026-09-current` versions, with a diff against generation a/b |

Translation rules applied: faithful, no additions or omissions; technical names (iFlow names, headers,
properties, scripts, partner directory keys, queue names, placeholders) verbatim; code blocks untranslated;
tenant identifiers replaced by `TENANT` / `tenant`. Two tenant-specific Confluence pages (runtime configuration
and naming conventions, partner directory configuration) are not here; they are input for the tenant profile
(part 2) and stay outside the repository.

An older generation (a): 13 Markdown pages from the first analysis in June 2026, superseded by the Confluence
pages, not included.
