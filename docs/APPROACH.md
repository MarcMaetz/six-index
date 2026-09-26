# Approach & Decision Log

Running record of how the Index Reviewer was built and why, kept as the basis for the follow-up
technical interview. Newest entries go at the bottom of each section. Keep entries short: what we
decided, why, and what we rejected.

## Timeline

| Date       | Step                                                                 |
|------------|----------------------------------------------------------------------|
| 2026-09-25 | Scaffolded Spring Boot project |
| 2026-09-25 | IntelliJ run configs (`.run/`); Postman collection "SIX Index Reviewer" created via MCP |
| 2026-09-25 | Input CSVs added to `data/`; profiled them (see **Input data findings**) |
| 2026-09-25 | SMI rules extracted from the SIX rulebook v3.40 (see **Rulebook rules applied**) |
| 2026-09-25 | Prepared handover to fresh sessions: working docs split into `AGENT.md` / `docs/TODO.md` / `docs/APPROACH.md` (D7) |
| 2026-09-25 | Claude Code hook enforcing approach-log updates on commit (D8) |
| 2026-09-25 | `domain` input model and `ingest` CSV loader with data-quality warnings, unit-tested (D9, A9, A10) |
| 2026-09-25 | Design review of the ingest decisions ("grill me"): review status, business config, input layout, precision (D10–D13) |
| 2026-09-25 | Implemented D11/D12: `config/indices.yml`, input in `data/SMI/2026-Q3/`, SHA-256 checksums; endpoints to list indices and check a review's input, added to Postman (D14) |
| 2026-09-25 | Review engine: eligibility, FFMCAP ranking, buffer selection, iterative capping; Q3 result confirmed in a unit test (D15, A11, A12) |
| 2026-09-25 | Review report with audit trail and review status; warnings got an impact so harmless ones don't flag the review (D16, A13) |
| 2026-09-25 | `POST /api/indices/{index}/reviews/{period}` returns the report; end-to-end API test and Postman request with the expected Q3 results (D17) |
| 2026-09-25 | Consistency pass over this log: earlier entries (D4, D9–D11, D13, data findings) updated to match later decisions |
| 2026-09-25 | Delivery via the GitHub repo instead of a ZIP (D18) |
| 2026-09-25 | README rewritten for reviewers, `docs/DESIGN.md` added, Postman collection exported to `postman/` (D19) |
| 2026-09-25 | Design review of review and report ("grill me"): iterative capping kept, both weights and factors, status margin, report storage, structured status reasons (D20–D22, A11, A13, A14) |
| 2026-09-25 | Iterative capping shown on real data: Q3 at a 15% cap needs two rounds, `[[155, 205], [63, 64]]` (D20) |
| 2026-09-25 | Status margin: an unranked security is harmless only if its estimate is below half the buffer-end value (A13) |
| 2026-09-25 | Structured status reasons with a `relevance` enum; index incompleteness detected from the result (D22, A12) |
| 2026-09-25 | Report storage: every run saved as JSON, `POST` returns 201 + `Location`, list and get endpoints (D21) |
| 2026-09-26 | Docs and Postman caught up with D20–D22: `DESIGN.md` (storage, capping reading, weights vs factors, API, tests), `README.md`, stored-report requests in Postman and re-exported; whole collection passes with newman against the running app (D19) |
| 2026-09-26 | Open questions resolved as deliberate assumptions instead of asking SIX (D23) |
| 2026-09-26 | `DESIGN.md` architecture diagram redrawn as the flow of one review run (Spring on top, plain-Java pipeline below) instead of every package dependency |
| 2026-09-26 | Extensibility review against the brief's non-functional list: methodology hard-coded (single cap, FFMCAP-only ranking seam, orchestration in the controller). Seams to add, and what to leave as talking points, listed in `docs/TODO.md` |
| 2026-09-26 | Use cases moved from `IndexController` into `ReviewService`, tested without Spring (D24) |
| 2026-09-26 | Capping takes a `CappingRule` (per-constituent caps); `SingleCap` for the SMI, tiered rule proven in a test; DESIGN.md no longer claims the SLI needs no code change (D25) |
| 2026-09-26 | Ranking seam: claims that the selection list is "another implementation" corrected in code docs, README, DESIGN.md and A4; eligibility recognised as weighting data (D26) |
| 2026-09-26 | Review status logic moved from `report` to `review`, read via `ReviewResult.assessment()` (D27) |
| 2026-09-26 | Input loading behind an `InputSource` port, CSV folder as one implementation; `IndexCatalog` only looks up config (D28) |
| 2026-09-26 | Rulebook version (`methodology`) required per index, shown in the index list and every report; Postman updated and passing with newman (D29) |
| 2026-09-26 | `ArchitectureTest` (ArchUnit): layering, plain-Java review logic, no Spring in ingest/store, no cycles; checked with a deliberate violation (D30) |

## Design decisions

### D1 — Java 25 + Spring Boot 4.1 (Gradle)
- **Why:** Stack I work in daily (same as my other projects), so time goes into the domain, not tooling.
  Spring gives the API, validation, config binding and OpenAPI docs for free.
- **Rejected:** Python (brief allows it), but Java's type system suits a rules/model-heavy domain and
  makes the design easier to walk through.

### D2 — No database; CSV in, report out
- **Why:** Inputs are CSV files and the brief puts infrastructure out of scope. A DB adds setup
  without helping the review logic. Persistence can be added later behind an interface.

### D3 — Index definitions and review dates in configuration
- **Why:** "Additional indices / additional review dates" is an explicit extensibility requirement.
  SMI (universe SPI, 20 constituents, 18% cap, cut-off 2026-09-10, review 2026-09-21) lives in
  `application.properties` under `index-reviewer.indices.*`, so a new index or quarter is a config change.
- **Superseded in part by D11:** the index definitions move to a separate business-owned file.

### D4 — Package layout by responsibility
- `domain` (framework-free model) · `ingest` (CSV + validation) · `review` (ranking, selection,
  capping as rules) · `report` (constituents, weights, joiners, leavers, status) · `api` (REST) ·
  `config` (Spring wiring and config binding, added in D14) · `store` (stored reports, added in D21) ·
  `service` (use cases, added in D24).
- **Why:** Keeps the review logic independent of Spring and I/O, so it is unit-testable in isolation
  and new rules slot into `review` without touching ingest or API.
- Enforced by `ArchitectureTest` since D30.

### D5 — Single Gradle project at repo root; Gradle-driven IDE setup
- Unlike `kasse-ai` (with `backend/` + `frontend/`), the app lives at the repo root: there is no UI
  (out of scope), so one module is all the brief needs.
- Java 25 is provisioned by the Gradle toolchain (foojay resolver, bumped 0.9.0 → 1.0.0 because 0.9.0
  breaks on Gradle 9). IntelliJ uses shared Gradle run configs in `.run/` (`bootRun`, `test`).
- **Why:** Anyone who unzips the submission builds and runs with `./gradlew` only, without a local JDK 25
  or IDE-specific setup. Gradle run configs work in IntelliJ Community too.
- **Rejected:** Spring Boot run config type (Ultimate only).

### D6 — OpenAPI spec as API contract; Postman collection for manual testing
- springdoc generates the spec at `/api-docs` from the code; Swagger UI at `/swagger-ui.html`.
- A Postman collection "SIX Index Reviewer" (created via the Postman MCP server) holds example requests
  with test scripts, using a `{{baseUrl}}` variable. It contains only endpoints that actually exist.
- **Why:** The spec can't drift from the code; the collection gives reviewers ready-made calls with checks.

### D7 — Working docs split by how often they change
- `AGENT.md` (loaded by the AI assistant via `CLAUDE.md`): lasting guidance only: what to read, conventions,
  build, rules for keeping the docs current.
- `docs/TODO.md`: current status and ordered next steps; finished items are removed.
- `docs/APPROACH.md` (this file): history and reasoning: timeline, decisions, assumptions.
- **Why:** Work happens across several AI sessions with fresh context. Each session starts from `AGENT.md`,
  picks up work from `TODO.md`, and records reasoning here, so nothing lives only in a chat transcript.
  Keeping volatile status out of `AGENT.md` stops it going stale.
- **Rejected:** A status section inside `AGENT.md` (mixes lasting rules with fast-changing state).

### D8 — Enforce the approach log with a hook, not just an instruction
- A Claude Code `PreToolUse` hook (`.claude/settings.json` → `.claude/hooks/require-approach-log.sh`) blocks
  any `git commit` the assistant runs while `docs/APPROACH.md` is unchanged versus HEAD. `[no-approach]` in
  the commit message opts out for changes that need no entry.
- **Why:** The written rule in `AGENT.md` was loaded but still skipped twice. A check that runs at commit
  time makes the rule deterministic instead of relying on the assistant remembering.
- **Rejected:** A git pre-commit hook (would also block the author's own IDE commits, and git hooks aren't
  shared through the repo without extra setup).

### D9 — Ingest: strict per file, lenient per row; date-dependent checks in the review
- `InputDataLoader` (framework-free) reads the three CSVs into an immutable `InputData`: universe per date,
  `SecurityData` per security and date, current composition, plus a list of `DataQualityWarning`s
  (source file, line, message, affected ids; an `impact` was added in D16).
- A missing file or column throws `InputDataException`: nothing sensible can be reviewed. A bad row (wrong
  field count, unparsable or out-of-range value) or a duplicate is skipped with a warning, so one bad row
  doesn't block the review but stays visible in the report.
- Values are nullable in `SecurityData`: which values are required depends on the date's role (price at t',
  shares and free float at t), which only the review period knows. So A2 (`166`) is an eligibility check in
  `review`, not a load error.
- Numbers are `BigDecimal` (exact decimal input, no float rounding in FFMCAP); shares are `long`.
- Collections keep insertion order, so results and warnings are reproducible run to run.
- OpenCSV does the parsing (quoting, CRLF); the UTF-8 BOM is skipped by hand since OpenCSV doesn't.
- **Rejected:** failing on the first bad row (one typo would block the quarterly review); OpenCSV bean
  binding (annotations on domain classes and coarse errors instead of per-row warnings).

### D10 — Review status reflects whether data-quality problems can affect the result
- The loader stays lenient (D9), but the review derives its status from the warnings:
  `COMPLETED` (no warnings), `COMPLETED_WITH_WARNINGS` (warnings only on securities that can't change the
  result), `REQUIRES_ATTENTION` (a warning touches a current constituent or a security ranked within the
  buffer end, i.e. 1–22 for SMI). Uses the ids in `DataQualityWarning.securityIds`.
- **Why:** With D9 alone, a typo in a top constituent's row would make it a leaver under a normal-looking
  status. A wrong published composition costs more than a delayed one. It also gives the brief's
  "review status" real meaning.
- **Rejected:** failing the load on any bad row (blocks reviews over irrelevant rows); status that only says
  whether the calculation ran (hides typo-driven outcomes).
- **Refined by D16:** only warnings that lose data count, and unranked securities are judged by an estimated
  rank.

### D11 — Business parameters in a separate `config/indices.yml`, formulas in code
- Index definitions (methodology, added in D29; universe, constituent count, direct-selection rank, buffer end rank, weight cap, ranking
  strategy name, review periods with cut-off and review dates) move from `application.properties` to
  `config/indices.yml`. A default copy is packaged so the app runs out of the box, and `./config/indices.yml`
  overrides it. `application.properties` keeps only technical settings.
- Inconsistent config (e.g. buffer end below constituent count, cap outside (0, 1]) fails at startup.
  How the file is imported, bound and validated is in D14.
- The status relevance band (D10) is derived from the buffer end rank, not configured separately.
- Ranking formulas stay in code as named strategies (`FFMCAP` now); the YAML only picks one by name. The
  rulebook selection list needs more than a new strategy (D26).
- **Why:** In practice these values are owned by the index business, not engineering; changing the cap or
  adding a quarter should not need a rebuild.
- **Rejected:** database or admin UI (out of scope: no UI or user management); rules engine (far too heavy
  for a handful of parameters); formulas as config expressions.

### D12 — Input folder per index and review period, with file checksums in the report
- Input lives in `<data-dir>/<index>/<period>/`, e.g. `data/SMI/2026-Q3/`. File names are fixed by
  convention; the universe file is named after the index's universe (`spi` → `spi_universe.csv`).
- The report records the input directory and a SHA-256 checksum per file.
- **Why:** One flat `data/` allows one index only and lets Q4 files overwrite Q3's, so past reviews can't be
  re-run or audited. Checksums prove which input produced a result (traceability).
- **Rejected:** explicit file paths per period in `indices.yml` (mixes business config with file
  operations); sharing universe and market data across indices (possible later, not needed now).

### D13 — Full-precision arithmetic, rounding only for display
- FFMCAP, weights and capping factors are calculated with `BigDecimal` and `MathContext.DECIMAL128`
  (34 significant digits, HALF_EVEN), with no intermediate rounding.
- Invariants are asserted and tested: weights sum to 1 within 1e-20, and no weight exceeds the cap.
- Display precision (weights 6 decimals in percent, capping factors 10 decimals, FFMCAP 2 decimals, D17) is a
  technical setting in `application.properties`, not a business one. Displayed weights are not adjusted to add up to exactly 100%.
- **Why:** Iterative capping divides repeatedly; rounding in between compounds and makes results depend on
  the number of iterations.
- **Rejected:** `double` (loses the reason for exact decimals and makes test comparisons fragile); rounding
  each capping iteration to the published precision.

### D14 — Config wiring, and an input check endpoint before the review endpoint
- The domain records `IndexDefinition` and `ReviewPeriod` validate themselves in their constructors; Spring binds
  `config/indices.yml` straight into them (`IndexReviewerProperties`), so the rules live in one framework-free
  place and a bad file stops startup with a clear reason. A new `config` package holds the Spring side
  (properties, `IndexCatalog` for lookups and loading a review's input; loading moved to `InputSource` in D28).
- Indices and review periods are YAML **lists** with `name`/`id`, not maps: Spring's relaxed binding mangles map
  keys such as `2026-Q3` unless bracketed.
- `config/indices.yml` is the single source: Gradle packages it into the jar as the default copy, and
  `spring.config.import` lets `./config/indices.yml` override it (checked: an external file with an
  inconsistent buffer stops startup).
- `GET /api/indices` lists the configuration; `GET /api/indices/{index}/reviews/{period}/input` loads and
  validates a review's input without running it (files + checksums, counts per date, composition, warnings).
- Errors are RFC 9457 problem responses: unknown index or period → 404; missing or unusable input files → 422
  (the request is valid, the data for it isn't).
- **Why:** Something to try before the review logic exists, and a real use afterwards: operations can check a
  quarter's files before running the review.
- **Rejected:** separate Jakarta Bean Validation annotations on a properties class (would duplicate the domain
  checks); a 500 for unusable input (it isn't a bug in the app).

### D15 — Review engine as a pipeline of small, pure steps
- `ReviewEngine.run(index, period, input)` chains `Eligibility` → `Ranking` → `Selection` → `WeightCapping`,
  each a static, framework-free function with its own unit tests. `ReviewResult` keeps every intermediate
  result: exclusions with reasons, a `SelectionDecision` for **every** ranked security (direct, buffer
  incumbent, buffer new, buffer full, below buffer), capping rounds, and leavers with a `LeaveReason`
  (not in universe, not eligible, buffer full, below buffer).
- The ranking criterion is a `RankingStrategy` looked up by the name in `indices.yml`; an unknown name stops
  startup. Weights always use FFMCAP (rulebook 5.12.4), whatever the ranking strategy.
- Capping: capped constituents get exactly the cap; the rest share the remaining weight in proportion to
  FFMCAP; repeat until none is above the cap. This is the brief's redistribution rule (proportional to current
  weight = proportional to FFMCAP) written without accumulating rounding. It ends in at most n rounds.
- Invariants (D13) are checked inside `WeightCapping`; a violation throws, since it would be a bug.
- **Refined by D25:** each constituent's cap comes from a `CappingRule`; the loop is the same.
- **Why:** Every decision in the report can be traced to one step and one rule, and each rule can be changed
  or replaced without touching the others. (The selection list for A4 needs more than that, see D26.)
- **Rejected:** one method doing it all (hard to test and explain); weights as `double` (see D13).

### D16 — Report with audit trail; status from warning impact and relevance
- `ReportBuilder` (framework-free, `report` package) turns a `ReviewResult` into a `ReviewReport`: status with
  reasons, index parameters used, constituents (rank, selection decision, joiner flag, FFMCAP, raw and final
  weight in %, capping factor), joiners, leavers with reasons, exclusions, the full ranking with a decision per
  security, capping rounds, input files with checksums, and all warnings. Values are rounded only here (D13).
  A `Clock` is injected for `generatedAt`, so tests are deterministic.
- Refines D10. Applied literally, D10 flagged the Q3 review: the duplicate-rows warning names all 204
  duplicated ids, constituents included, and `166` can't be ranked at all. So:
  - each `DataQualityWarning` has an `Impact`: `NONE` (nothing lost, e.g. identical duplicate dropped) or
    `MISSING_DATA` (a row ignored, conflicting rows, a security excluded). Only `MISSING_DATA` can raise the
    status;
  - a security that couldn't be ranked is judged by an **estimated ranking value** from the values it has on
    either date: harmless only if below half the value at the buffer end rank (A13). No estimate possible, or
    no security named → counts as relevant.
- Result for Q3: `COMPLETED_WITH_WARNINGS`; `166`'s estimated FFMCAP is 12.9M against 15.4bn at the buffer end
  rank 22 (it would rank 197 of 205).
- Every status comes with reasons naming the warning, the security and why it does or doesn't matter.
- **Why:** The status should tell a reviewer whether to look closer, and the reasons should say where.
- **Rejected:** a warning count threshold (says nothing about impact); treating every unranked security as
  relevant (would flag this quarter over a penny stock).
- **Refined** in the second design review: the estimate is compared with a safety margin (A13), and reasons
  become structured records (D22). The status logic moved from `report` to `review` (D27).

### D17 — Review endpoint: POST, returns the report, stores nothing
- `POST /api/indices/{index}/reviews/{period}` loads the input, runs `ReviewEngine`, builds the report and
  returns it (200). The controller only wires the three steps; the logic stays in the framework-free packages.
  (Refined by D24: the wiring moved into `ReviewService`.)
- Report display precision is technical config (`index-reviewer.report.*` in `application.properties`, D13),
  bound into `ReportFormat`. `ReviewEngine`, `ReportBuilder` and a UTC `Clock` are beans in `ReviewConfiguration`.
- JSON keeps the fixed decimals (`18.000000`, capping factor `0.5653526015`). The rounded weights add up to
  99.999999% for Q3; at full precision they add up to 1 (D13), and the display is not adjusted.
- **Why POST:** running a review is an action, not a lookup of a stored resource, even though the same input
  gives the same result. No 201, because nothing is created.
- **Rejected:** storing reports (a file in `reports/` or a DB) — the brief doesn't need it, and the report
  already carries input checksums, so any report can be reproduced. It is the obvious next step for an audit
  history.
- **Superseded by D21:** reports are now stored, and `POST` returns 201.

### D18 — Deliver the GitHub repository instead of a ZIP
- The submission is the GitHub repo; no ZIP task is built.
- **Why:** Reviewers get the full commit history, which shows how the work progressed alongside this log.
  GitHub's "Download ZIP" still gives the plain archive the brief mentions.
- **Rejected:** a Gradle ZIP task (extra work, and it would drop the history).

### D19 — Reviewer-facing docs: README, DESIGN.md, exported Postman collection
- `README.md` is the entry point: the Q3 result, build and run, endpoints, configuration and where each
  setting lives, and a map of the docs. `docs/DESIGN.md` describes the system as it is (architecture,
  pipeline, validation and status, traceability, extensibility, testing) and links here for the reasoning.
- The Postman collection is exported to `postman/six-index-reviewer.postman_collection.json` (without
  workspace metadata). The MCP-managed workspace copy stays the working copy; `AGENT.md` says to re-export after
  changes.
- Requests that depend on an earlier one are chained through collection variables: "Run review" saves the
  stored report's id from the `Location` header as `reportId`, which the get-report request uses. So the
  *Indices* folder runs top to bottom in the Postman runner or newman without manual edits.
- **Why:** Reviewers only get the repo (D18), and the workspace collection is private. This log is ordered by
  time and full of alternatives, which makes it a poor first read; the design doc gives the structured view.
- **Rejected:** turning this log into the design doc (it would lose the history the interview needs).

### D20 — Capping is iterative, even where the rulebook wording is narrower
- Kept: cap, share the excess, repeat while anyone is above the cap (D15). Rejected: a single pass that caps only
  securities whose **raw** share is above the cap, as a literal reading of rulebook 5.12.4 suggests.
- The loop can cap a security whose raw share was below the cap (A14); a single pass can publish a weight
  above the cap. Real data shows the difference: at a 15% cap, a single pass leaves `63` at 15.60%; the loop
  caps `63` and `64` in round 2. At 18% both give the same Q3 result.
- **Why:** The cap exists to limit concentration, and the brief's example ends with "all constituents are now
  below the cap". SIX's intra-review rule also re-caps "so that any component again has a maximum weight of 18%".
  A visible breach on day one is worse than capping one more security.
- The report gives both final weights (the review's result, checkable against the cap and the brief's example)
  and capping factors (what index calculation carries forward until the next review). See A11.

### D21 — Every review run is stored as written
- `POST /api/indices/{index}/reviews/{period}` saves the report as JSON under
  `reports/<index>/<period>/<run id>.json` and returns **201 Created** with a `Location` header.
  `GET .../reviews/{period}/reports` lists the stored runs; `GET .../reports/{id}` returns one.
- Files are written once, never changed. `reports/` is git-ignored. Storage sits behind a `ReportStore`
  interface, so a database can replace files without touching the review (D2).
- Implementation (`store` package, `FileReportStore`): the run id is the UTC generation time
  (`20260925T201052184Z`), with `-2`, `-3`, … if runs share a millisecond; files are created with
  `CREATE_NEW`, so an existing run can't be overwritten. JSON is written with the application's `JsonMapper`,
  so a stored report looks exactly like the API response. `GET .../reports/{id}` returns the stored bytes
  unchanged rather than re-serializing, so what you get is what was written. Ids and path segments are checked
  against a safe pattern, so a request can't read outside the store. The folder is
  `index-reviewer.reports-dir` (technical config).
- **Why:** Without storage, "which report did we publish for Q3, and when?" has no answer, and re-running
  after a config change gives a different report. Traceability is required and auditability is asked for.
- **Rejected:** no storage (D17); a database (out of scope, D2).

### D22 — Structured status reasons
- Each status reason is a record: `securityId`, `relevance`, an `explanation` sentence, and the `warning` it
  comes from (source, line, impact, message; without the id list, since the reason names the one id that
  matters). `relevance` is an enum covering every path of the status logic; attention: `CURRENT_CONSTITUENT`,
  `RANKED_WITHIN_BUFFER`, `ESTIMATED_NEAR_BUFFER`, `NOT_ESTIMABLE`, `BUFFER_NOT_FULL`, `SECURITY_UNKNOWN`,
  `INDEX_INCOMPLETE` (A12); harmless: `NO_DATA_LOST`, `RANKED_BELOW_BUFFER`, `ESTIMATED_FAR_BELOW_BUFFER`.
  The status follows from the enum.
- All reasons are kept, harmless ones too, even when the status is `REQUIRES_ATTENTION`.
- `INDEX_INCOMPLETE` is detected from the result, not from a warning: too few constituents is a review outcome,
  not a data problem, and matching a warning by its message would be fragile. So the engine no longer adds a
  warning for it.
- **Why:** Sentences can't be filtered or acted on, tests had to match substrings, and stored reports (D21)
  are a long-lived record where free text ages badly.

### D23 — Open questions decided, not sent to SIX
- The five questions collected for SIX are answered by my own assumptions and listed under **Deliberate
  assumptions**. None of them changes the Q3 result, and each is visible in the report and easy to change.
- **Why:** An assumption that is written down, justified and configurable is what the brief asks for ("make
  reasonable assumptions and document them clearly"); owning them is a stronger position than waiting on
  answers before the deadline.
- **Rejected:** emailing the questions to SIX.

### D24 — Use cases in a `ReviewService`, not in the controller
- `ReviewService` (new `service` package) holds the use cases by index name and period id: list indices, load a
  review's input, run and store a review, list and read stored reports. `IndexController` depends only on it and
  maps HTTP (status codes, `Location`, response DTOs).
- It is a Spring `@Service` with constructor injection, but has no other Spring dependency: `ReviewServiceTest`
  builds it by hand and runs the Q3 review without a context.
- **Why:** The controller chained catalog → engine → report builder → store itself, so any other entry point
  (scheduled review, CLI, message) would have copied that sequence. "Future enhancements with minimal
  refactoring" (brief). Found in the extensibility review (TODO item 1).
- **Rejected:** building it in `ReviewConfiguration` as a plain bean (it needs `IndexCatalog`, so `config` and
  `service` would depend on each other); an interface plus implementation (one implementation, nothing to swap).

### D25 — Capping rule decides each constituent's cap
- `CappingRule.caps(ffmcapById)` returns a maximum weight per constituent. `WeightCapping.cap` takes a rule instead
  of one `BigDecimal` and runs the same loop against per-constituent caps; the feasibility check becomes "caps add
  up to at least 1" and the invariant "no weight above its own cap". `SingleCap` (all at 18%) is the only
  implementation in `main`; `ReviewEngine.cappingRule` builds it from `weightCap`.
- A tiered rule (SLI-style: largest n at one cap, the rest at another) lives only in `WeightCappingTest`, with a
  two-round case where a constituent is capped at the lower tier. It proves the seam without shipping unused code.
- Config is unchanged (`weight-cap: 0.18`). A tiered index would add its fields to `IndexDefinition` and pick its
  rule in `ReviewEngine.cappingRule`.
- **Why:** The extensibility review showed the SLI, three sections after the SMI in the same rulebook, couldn't
  be added: a single scalar cap ran through `IndexDefinition`, `WeightCapping` and the engine. The loop itself
  (water-filling against a cap) was already right; only "which cap applies to whom" varies between indices.
- **Rejected:** a `capping:` block with a rule type in the YAML now (designing a config format for an index we
  don't have, and Spring binding to polymorphic types is clumsy); `TieredCap` in `main` (no index uses it; the real
  SLI also takes its top 4 from a half-year ranking we have no data for); a rule that returns final weights
  (every rule would re-implement the loop and its invariants).

### D26 — Ranking seam described as it is, not widened
- The extensibility review found `RankingStrategy`'s Javadoc, A4 and DESIGN.md claiming the rulebook's selection
  list "would be another implementation". It can't: `rankingValue(EligibleSecurity)` sees one security's price,
  shares and free float, and the selection list needs 12-month average FFMCAP and turnover.
- Kept the code; corrected the claims (Javadoc, README, DESIGN.md, A4). What the selection list would take:
  1. turnover and history in the input files, `InputData` and `InputDataLoader`;
  2. `RankingStrategy` receives the review's input (e.g. `InputData` and period), not one `EligibleSecurity`;
  3. a rule for securities without enough history (a rulebook question).
- `Eligibility` is **not** FFMCAP-ranking logic, as the review suspected: it checks the data the weights need
  (weights always use FFMCAP, rulebook 5.12.4), so it holds for every strategy. A strategy needing more data would
  add its own exclusions on top.
- **Why:** Widening the signature now would add a parameter `FfmcapRanking` doesn't use, for data `InputData`
  doesn't have; the brief rates simplicity over features. What matters for the interview is that the claim is
  true and the next steps are known.
- **Rejected:** strategy declares its data needs and eligibility follows from it (the weights' data needs don't
  depend on the strategy, so eligibility can't move into it); passing `InputData` to the strategy now (speculative).

### D27 — Review status is part of the review, not the report
- `StatusAssessment`, `ReviewStatus` and `StatusReason` moved from `report` to `review`. The status is read as
  `ReviewResult.assessment()`, derived from the result like `joiners()`. `ReportBuilder` only renders it.
- **Why:** The assessment judges the result: it uses the buffer end, estimates ranking values through the ranking
  strategy and reads the raw input. That is review logic; in `report` it made the report package depend on
  review internals (`RankingStrategies`, `Selection`) and hid a business rule in the formatting layer. Any other
  consumer of a `ReviewResult` (e.g. a scheduler deciding whether to publish) now gets the status without
  building a report. The report already used review types such as `SelectionDecision` and `LeaveReason`, so
  status types living in `review` follows the same pattern.
- **Rejected:** a `status` field on `ReviewResult` set by the engine (the assessment needs the finished result, so
  the record would need a nullable field or a second result type); keeping it in `report` with a note.

### D28 — Input behind an `InputSource` port
- `InputSource.load(index, period)` in `ingest` returns a review's `InputData`. `CsvFolderInputSource` implements
  the folder convention (`<data-dir>/<index>/<period>`, D12) with the existing `InputDataLoader`. It is a bean in
  `ReviewConfiguration` and injected into `ReviewService`. `IndexCatalog` only looks up configuration now.
- **Why:** Output already had a port (`ReportStore`), input didn't: `IndexCatalog` created its own
  `InputDataLoader` and resolved paths itself. At SIX the data would come from a market-data system, not a folder;
  with the port that is one new class and one bean. It also separates "what is configured" from "where the data
  lives".
- **Rejected:** the interface in `config` (it would tie a data concern to Spring wiring); passing a `Path` through
  the interface (every non-file source would have to fake one); an in-memory test implementation (the CSV one is
  already covered on real data, and `InputData` is built by hand in `StatusAssessmentTest`).

### D29 — Rulebook version recorded per index and in every report
- `IndexDefinition` has a required `methodology`: the rulebook version and section the index follows. For the SMI:
  "SIX Index Methodology Rulebook Equity and Real Estate v3.40 (2026-06-08), section 5.12". A blank value stops
  startup. It is listed by `GET /api/indices` and written into each report's `parameters`.
- Postman checks it in "List indices" and "Run review"; the whole collection passes with newman (33 assertions).
- **Why:** A stored report (D21) already recorded the parameters and input checksums, but not which rules produced
  them. SIX revises the rulebook; a report from v3.40 must stay explainable after v3.41. Auditability is on the
  brief's list.
- **Rejected:** a structured version (number, date, section as separate fields; nothing reads them separately); a
  default value (a new index must state its rules); versioning the whole configuration by effective date (a
  feature, kept as a talking point in TODO).

### D30 — Package rules enforced by ArchUnit tests
- `ArchitectureTest` (ArchUnit 1.5.1, test scope only) checks four rules on the main classes:
  1. dependency direction, as layers: `api` → `service` → `config` → `store` → `report` → `review` → `domain`,
     with `ingest` used only by `config`, `service` and `api` (`domain` may be used by all);
  2. `domain`, `review` and `report` depend only on the JDK and the application's own classes;
  3. `ingest` and `store` don't depend on Spring;
  4. no cycles between packages.
- The code already met all four; the test locks it in. Checked that it bites: a probe class in `review` with a
  Spring annotation and a dependency on `report` failed three of the four rules.
- **Why:** "Framework-free review logic" (D4) was a convention stated in docs; a convention erodes one import at a
  time, especially with AI-assisted changes. As a test it holds without anyone remembering it, and the rules
  double as executable documentation of the architecture.
- **Rejected:** Spring Modulith (module model built around Spring beans, more than seven packages need); Java
  modules (JPMS) per package (one Gradle module per layer, far too heavy for this size); a code review checklist.

## Input data findings

Profiled 2026-09-25, before writing any parsing code.

- **Format:** `;`-separated, UTF-8 **with BOM**, CRLF line endings. Parser must strip the BOM.
- **`composition.csv`:** 20 unique ids (current SMI). All present in universe and security data.
- **`spi_universe.csv`** (`date;id`): 409 rows but only **205 unique ids**. Every id except `166` appears
  twice as an exact duplicate row, all dated 2026-09-21 (review date).
- **`sec_data.csv`** (`id;date;price;free_float;shares`): one row per id and date.
  - 2026-09-10 (cut-off): price, free float and shares filled for all 205 ids.
  - 2026-09-21 (review): 204 ids, **price always empty**, free float and shares filled.
  - Id **`166`** has **no review-date row** (only cut-off data). Not a current constituent; its cut-off
    FFMCAP would be tiny (price 0.13, free float 0.087).
  - Shares differ between the two dates for 17 ids and free float for 41. So the dates matter:
    price must come from t', shares and free float from t.
- **No** non-numeric values, free float outside (0, 1], or non-positive price or shares.
- **Preliminary ranking** (FFMCAP = price(t') × shares(t) × free float(t), plain top 20, no buffer):
  - Current constituents are at ranks 1–6, 8–18, 21 (`160`), 22 (`81`) and 35 (`103`).
  - Non-constituents in the top 20: rank 7 (`177`), 19 (`249`), 20 (`28`).
  - So **the buffer rule decides the outcome**: a plain top 20 gives 3 joiners and 3 leavers.
    With the rulebook buffer (see below) it is 1 joiner (`177`) and 1 leaver (`103`).
  - Ranks 1 (`155`, ~25%) and 2 (`205`, ~23%) exceed the 18% cap on raw weights. Capping must be able to
    iterate, since redistributing can push others over the cap; for Q3 one round is enough (next largest,
    `63`, ends at 14.26%).

## Rulebook rules applied

Source: *SIX Index Methodology Rulebook Governing Equity and Real Estate Indices*, v3.40 (08.06.2026),
section 5.12, with definitions in 2 and 4.3.

- **Universe (5.12.3.2):** SPI.
- **Selection (5.12.3.2):** 20 components. Ranks 1–18 are selected directly. Ranks 19–22 are the buffer:
  current constituents in the buffer are included first, then new candidates from the buffer, in rank
  order, until there are 20. A current constituent ranked 23 or lower leaves.
- **Capping (5.12.4):** a component whose FFMCAP exceeds 18% of the total is capped at 18% with a
  capping factor. Excess weight is redistributed (brief's example) until no component exceeds 18%.
- **Expected Q3 2026 outcome with this data:**
  - Ranks 1–18: 17 incumbents plus `177` (rank 7, joiner).
  - Buffer 19–22: `249` (new), `28` (new), `160` (incumbent), `81` (incumbent). Incumbents take priority,
    so `160` and `81` fill the last two slots.
  - Leaver: `103` (rank 35).
- **Confirmed by `ReviewEngineTest`** on the provided data: 20 constituents, joiner `177`, leaver `103`
  (rank 35); `155` (raw 25.42%) and `205` (raw 23.49%) capped to 18% in a single round, capping factors
  0.5654 and 0.6117; the others scale up by the same factor (e.g. `63`: 11.38% → 14.26%). `166` excluded (A2).
- **Not applied** (see A4–A6): the full selection-list formula, the liquidity rule for multi-listed
  instruments, and issuer-level capping.

## Assumptions

Where the brief or rulebook is ambiguous, record the assumption here (and reference it in code).

| #  | Assumption | Rationale |
|----|------------|-----------|
| A1 | Exact duplicate rows in `spi_universe.csv` are de-duplicated, with a data-quality warning in the report, not rejected. | They are identical (same date and id), so no information conflicts. |
| A2 | A universe security with no review-date security data (`166`) is excluded from ranking, with a warning in the report. | FFMCAP needs shares(t) and free float(t); falling back to cut-off values would break the brief's date rule. |
| A3 | The empty review-date price column is expected: prices are only taken at cut-off (t'). | Matches the FFMCAP formula in the brief. |
| A4 | Ranking uses FFMCAP only, as the brief specifies, not the rulebook's selection list (4.3: 50% average 12-month FFMCAP share + 50% 12-month turnover share). | No turnover or history data is provided; the brief defines FFMCAP ranking explicitly. Adding the full formula needs new input data and a wider `RankingStrategy` signature (D26). |
| A5 | The extra liquidity rule for instruments with primary listings on several exchanges (5.12.3.2) is not applied. | The data has no listing or turnover fields. |
| A6 | Each id is treated as a separate issuer, so issuer-level cumulative capping (5.12.4) is not applied. | The data has no issuer field. |
| A7 | Buffer candidates of the same kind (incumbent or new) are taken in rank order. | Rulebook says incumbents come first but not how to order within a group; rank order is the natural reading. |
| A8 | Ties in FFMCAP are broken by id, so results are deterministic. | No ties occur in this data; the rule only guards reproducibility. |
| A9 | Conflicting rows in `sec_data.csv` (same id and date, different values) are all dropped with a warning; identical ones are de-duplicated. | Neither row can be trusted; dropping them makes the security ineligible (visible in the report) rather than silently picking one. Doesn't occur in this data. |
| A10 | Rows with out-of-range values (price or shares not positive, fractional shares, free float outside (0, 1]) are skipped with a warning. | Such values can't be real and would distort FFMCAP. None occur in this data. |
| A11 | Capping factors are normalised so the largest is 1, i.e. uncapped constituents have factor 1 and capped ones below 1. | Only the ratios between factors affect weights; this is the usual published form. Check with SIX (open question). The brief's "weighting factors" is read as capping factors; final weights are reported as well (D20). |
| A12 | If fewer securities can be ranked than the index needs, all ranked securities are selected and the review status is `REQUIRES_ATTENTION` with reason `INDEX_INCOMPLETE` (D22). | The rulebook doesn't cover it; a smaller index with a visible warning beats inventing a fill rule. Can't happen with this data. |
| A13 | For the review status, an unranked security's rank is estimated with its price from the cut-off date (else the review date) and shares and free float from the review date (else the cut-off date). | Only used to judge whether missing data could matter; never used for selection or weights, which keep the strict date rule. The estimate counts as harmless only if its value is below **half** the ranking value at the buffer end, a margin for the data it borrows from the other date (17 ids change shares and 41 change free float between the dates). |
| A14 | Capping is iterative: a security pushed above the cap by redistribution is capped too, even if its raw share was below the cap (D20). | Guarantees no published weight above the cap; the rulebook's wording only names components above 18% of the total. |

## Deliberate assumptions

Points the brief and rulebook leave open. I decided them myself instead of asking SIX (D23). Each is written
down, visible in the report, and can be changed in one place.

- **Ranking by point-in-time FFMCAP only (A4).** The brief defines the FFMCAP formula; the rulebook's
  selection list needs 12-month FFMCAP and turnover history, which the data doesn't have. Adding it means new
  input data plus a wider ranking strategy signature; the steps are named in D26.
- **Duplicate rows in `spi_universe.csv` are de-duplicated (A1).** The rows are identical, so nothing is lost
  either way; a warning with impact `NONE` keeps them visible.
- **Id `166`, with no review-date data, is excluded (A2).** Falling back to cut-off values would break the
  brief's date rule. Its estimated FFMCAP (12.9M) is far below rank 22 (15.4bn), so it can't change the
  result, and the status stays `COMPLETED_WITH_WARNINGS` (A13).
- **Full precision in the calculation, rounding only for display; capping factors scaled so the largest is 1
  (D13, A11).** Display precision is technical config in `application.properties`.
- **The 18% cap is applied iteratively (A14, D20).** No published weight exceeds the cap, as in the brief's
  example. For Q3 a single pass gives the same result.

## Interview talking points

- How the design accommodates new indices, dates and rules (D3, D4).
- Where the brief simplifies the rulebook (A4–A6) and how the design leaves room for the full rules.
- The five points left open were decided, not asked (D23): each is documented, visible in the report and
  changeable in one place, and none changes the Q3 result.
- The buffer is what changes the result: plain top 20 gives 3 joiners and 3 leavers, the buffer gives 1 and 1.
- Traceability/auditability: how a reviewer can see why a security joined, left or was capped.
- Separating business-owned parameters (`config/indices.yml`) from technical config and from formulas (D11).
- Why data-quality warnings drive the review status instead of blocking the load (D9, D10), and how
  impact plus an estimate with a safety margin keep harmless warnings from flagging a review (D16, A13).
- Testing strategy: each pipeline step unit-tested on small hand-made cases (the brief's A/B/C example, a
  two-round capping case, buffer edge cases, each status case); the real Q3 data checked at engine, report and
  API level; Postman test scripts for manual runs. Tests compare decimals with tolerances where the last of
  34 digits can round either way.
- Tooling: one-command build/run (D5), API contract + Postman collection (D6).
- Use of AI assistance (allowed by the brief): Claude Code with this decision log kept alongside, so every
  choice is written down and can be explained.
- Why the displayed weights add up to 99.999999% and that's correct (D13, D17).
- Iterative vs single-pass capping (D20): Q3 needs one round, but at a 15% cap the real data shows a single
  pass publishing `63` at 15.60%. Live demo: set `weight-cap: 0.15` in `config/indices.yml`, restart, run.
- Weights vs capping factors: the weight is the review's result and drifts with prices; the factor is what
  index calculation carries until the next review (D20, A11).
- What I'd do next with more time: the rulebook selection list, once turnover and history data exist (A4, D26), issuer-level
  capping (A6), a database behind `ReportStore` (D21).
