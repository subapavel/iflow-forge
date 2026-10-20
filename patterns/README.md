# Pattern catalogue

The pattern catalogue holds generic, committed answers to recurring migration problems on the SAP Integration Suite pipeline: one problem per page `patterns/<id>.md`, the options a migration can take, their trade-offs, and where each option was used. A pattern page names no customer and no system; it rests on pages of `knowledge/pipeline/` for every pipeline fact. This file is the index.

## Page contract

- **Problem**: the problem in pipeline terms, with no customer or system name.
- **Options**: each option with its trade-offs and the pipeline facts it rests on, cited `[page: knowledge/pipeline/<path>#<heading>]`.
- **Where used**: per option, the migrations that took it, cited `[migration M-<n>, <date>]`.
- **Open points**: what is not settled yet, as open questions.
- **Budget**: at most 1,500 checker words per page.
- **Review**: a pattern page is born as a request of kind `pattern` (`requests/README.md`), written by the kb-author in the cloud from the request's generic text, reviewed by the kb-reviewer like a knowledge page, then committed. The local side may draft it under `requests/`, never commit under `patterns/`.

## Cite forms

The three cite forms of D-030:

- `[page: knowledge/pipeline/<path>#<heading>]`: a pipeline fact, by page and heading of the knowledge base.
- `[pattern: <id>]`: another pattern page of this catalogue.
- `[migration M-<n>, <date>]`: evidence that a migration used an option; `M-<n>` is a migration code.

The limit of two external cites per section (D-029) does not apply to these forms.

## Rule

The map from a migration code `M-<n>` to customer and scenario lives only in the gitignored `local/migrations.md`. A committed page carries the code, never a customer, tenant, system, host or person name.

## Index

| id | problem | options | used in |
|---|---|---|---|
