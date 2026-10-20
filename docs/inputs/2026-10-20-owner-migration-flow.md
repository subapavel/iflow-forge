# Owner input — the migration flow as the owner pictures it (2026-10-20)

Source: the owner, in the session of 2026-10-20 (cite form `[owner, 2026-10-20]`, D-029). This is an
input for the designs of parts 3, 4 and 5, not a design. Restated in English; nothing added.

## The owner's description

1. The request is "migrate scenario (ICO) X and Y". The input comes one of two ways: the owner hands
   over TPZ exports plus the Java modules where they exist, or the agent has a direct connection to the
   PO system and pulls every artifact of the ICO itself through po-xray.
2. From the artifacts the agent establishes: which adapter modules are in the channels, which mapping
   types the operation mapping uses (message mapping, XSLT, Java mapping), whether message mappings
   carry UDFs, and what can be carried over 1:1 versus what has to be analysed from scratch.
3. Sender-side module: what does it do? Examples the owner gave: it fetches a token — not needed in
   Integration Suite, a security material replaces it, or when the token is non-standard the Step03
   copy fetches it by a call and sets a header; it unzips a file; it performs a conversion.
4. Mapping: message mapping — has UDFs? what do they do? XSLT — carries over 1:1? Java mapping — what
   does it do, does it set adapter-specific message attributes, can it be converted to Groovy?
5. BPM scenarios: the same analysis, with one more input — the iFlow that runs before the process.

## What po-xray already covers (for the part 3/5 designs; verified in the sources 2026-10-20)

- Live read of a PO system: `po-xray-core/.../po/PoClient.java` (`query`, `read`, `readRaw`); the CLI
  `ico` command reads ICOs to or from a component with channels and ESR objects, snapshot cached.
- TPZ input: `po-xray-core/.../xi/TpzArchive.java`.
- Channel modules with position, name, type and parameters; operation and message mappings with
  multiplicity; `mappingPrograms` (Java / XSLT, imported archives); `functionLibraries` with the UDF
  code, arguments and the SAP APIs used — all in `docs/OUTPUT-MODEL.md`, "Integration inventory".
- Not covered yet: the classification "1:1 / analyse" and the module-to-step recipe. That is part 5,
  guided by `knowledge/pipeline/conversion/README.md`.

## Second statement, same session (the owner's primary target)

6. The primary target is the migration of NW BPM processes and the more complex scenarios; four BPM
   processes wait as the pilot. Simple ICO migrations have SAP's own migration tool (which, the owner
   notes, does not use the newest pipeline). The flow the owner expects: po-xray analysis of the BPM, the
   EJB modules and the ICOs around it, combined with a brainstorm with the migrator, completing the open
   questions and the specification, then — with the pipeline and Integration Suite knowledge — a
   migration proposal, and only then the migration itself. po-xray already reads EJB modules; the owner
   can provide an EJB adapter-module example locally if the inputs lack one.

## Third statement, same session

7. Human tasks are out of scope: the owner has not met them at any client. The owner pictures the
   migration front end as a brainstorming skill (like `superpowers:brainstorming`) with SAP PO, Integration
   Suite and pipeline knowledge: every question answered one by one, a question may raise a new question,
   the round ends when nothing is unclear between agent and migrator; only then the migration plan, refined
   iteratively; after the final approval the artifacts are created.

## Fourth statement, same session

8. The test data (SCA exports, ICOs) is already local and po-xray has been run on it; the pilot is tested
   locally. The owner asks for the whole front end to be prepared: a custom skill, ideally usable with every
   agent (Claude Code, Codex, Gemini CLI); the test itself runs in Claude Code. Recorded as the design row
   MIG-00 in `agent/BACKLOG.md`.
