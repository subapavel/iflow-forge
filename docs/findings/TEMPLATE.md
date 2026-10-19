# F-<n> — <short title>

Date: <YYYY-MM-DD> · Task: <backlog id> · Spec: <path> §<n>

- **Assumption in the document:** <quote or paraphrase the exact claim, with the § reference>
- **How it fails in practice:** <the concrete scenario met while implementing, writing a page or
  measuring; what breaks, who would notice>
- **Severity:** blocking | major | minor
- **Evidence:** <test name, dump output, artifact path and step, javap output — something
  observed, not remembered>
- **Change made:** <the smallest change that keeps the documented intent; what was done instead,
  and what stays as designed>
- **Spec edit:** <changelog line added at the top of the spec> — or "none (minor)"
- **PROGRESS entry:** <task id> marked `Deviation: F-<n>`
- **For the owner's review:** <one sentence on what to look at, and whether a better alternative
  was considered and rejected>

Findings are numbered in creation order across the whole project. Severity `blocking` = the gate
cannot be met as designed; `major` = a documented mechanism is replaced while the intent is kept;
`minor` = the document is imprecise, the work follows the intent, no mechanism changes.
