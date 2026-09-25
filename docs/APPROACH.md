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
  capping as rules) · `report` (constituents, weights, joiners, leavers, status) · `api` (REST).
- **Why:** Keeps the review logic independent of Spring and I/O, so it is unit-testable in isolation
  and new rules slot into `review` without touching ingest or API.

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
- `docs/APPROACH.md` (this file): history and reasoning: timeline, decisions, assumptions, open questions.
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
  (source file, line, message, affected ids).
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

### D11 — Business parameters in a separate `config/indices.yml`, formulas in code
- Index definitions (universe, constituent count, direct-selection rank, buffer end rank, weight cap, ranking
  strategy name, review periods with cut-off and review dates) move from `application.properties` to
  `config/indices.yml`, imported via `spring.config.import=optional:file:./config/indices.yml`. A default copy
  is packaged so the app runs out of the box. `application.properties` keeps only technical settings.
- Bound to a validated `@ConfigurationProperties` record; inconsistent config (e.g. buffer end below
  constituent count, cap outside (0, 1]) fails at startup.
- The status relevance band (D10) is derived from the buffer end rank, not configured separately.
- Ranking formulas stay in code as named strategies (`FFMCAP` now, the rulebook selection list later, A4);
  the YAML only picks one by name.
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
- Display precision (weights 6 decimals in percent, capping factors 10 decimals) is a technical setting in
  `application.properties`, not a business one. Displayed weights are not adjusted to add up to exactly 100%.
- **Why:** Iterative capping divides repeatedly; rounding in between compounds and makes results depend on
  the number of iterations.
- **Rejected:** `double` (loses the reason for exact decimals and makes test comparisons fragile); rounding
  each capping iteration to the published precision.

### D14 — Config wiring, and an input check endpoint before the review endpoint
- The domain records `IndexDefinition` and `ReviewPeriod` validate themselves in their constructors; Spring binds
  `config/indices.yml` straight into them (`IndexReviewerProperties`), so the rules live in one framework-free
  place and a bad file stops startup with a clear reason. A new `config` package holds the Spring side
  (properties, `IndexCatalog` for lookups and loading a review's input).
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
- **Why:** Every decision in the report can be traced to one step and one rule, and each rule can be changed
  or replaced (e.g. the selection list for A4) without touching the others.
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
  - a security that couldn't be ranked is judged by an **estimated rank** from the values it has on either
    date (A13). No estimate possible, or no security named → counts as relevant.
- Result for Q3: `COMPLETED_WITH_WARNINGS`; `166`'s estimate is rank 197 of 205, far below the buffer end 22.
- Every status comes with reasons naming the warning, the security and why it does or doesn't matter.
- **Why:** The status should tell a reviewer whether to look closer, and the reasons should say where.
- **Rejected:** a warning count threshold (says nothing about impact); treating every unranked security as
  relevant (would flag this quarter over a penny stock).

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
  - Ranks 1 (`155`, ~25%) and 2 (`205`, ~23%) exceed the 18% cap on raw weights, so capping must
    iterate (redistributing can push others over the cap).

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
| A4 | Ranking uses FFMCAP only, as the brief specifies, not the rulebook's selection list (4.3: 50% average 12-month FFMCAP share + 50% 12-month turnover share). | No turnover or history data is provided; the brief defines FFMCAP ranking explicitly. The ranking criterion is a pluggable rule so the full formula could be added. |
| A5 | The extra liquidity rule for instruments with primary listings on several exchanges (5.12.3.2) is not applied. | The data has no listing or turnover fields. |
| A6 | Each id is treated as a separate issuer, so issuer-level cumulative capping (5.12.4) is not applied. | The data has no issuer field. |
| A7 | Buffer candidates of the same kind (incumbent or new) are taken in rank order. | Rulebook says incumbents come first but not how to order within a group; rank order is the natural reading. |
| A8 | Ties in FFMCAP are broken by id, so results are deterministic. | No ties occur in this data; the rule only guards reproducibility. |
| A9 | Conflicting rows in `sec_data.csv` (same id and date, different values) are all dropped with a warning; identical ones are de-duplicated. | Neither row can be trusted; dropping them makes the security ineligible (visible in the report) rather than silently picking one. Doesn't occur in this data. |
| A10 | Rows with out-of-range values (price or shares not positive, fractional shares, free float outside (0, 1]) are skipped with a warning. | Such values can't be real and would distort FFMCAP. None occur in this data. |
| A11 | Capping factors are normalised so the largest is 1, i.e. uncapped constituents have factor 1 and capped ones below 1. | Only the ratios between factors affect weights; this is the usual published form. Check with SIX (open question). |
| A12 | If fewer securities can be ranked than the index needs, all ranked securities are selected and the review adds a warning. | The rulebook doesn't cover it; a smaller index with a visible warning beats inventing a fill rule. Can't happen with this data. |
| A13 | For the review status, an unranked security's rank is estimated with its price from the cut-off date (else the review date) and shares and free float from the review date (else the cut-off date). | Only used to judge whether missing data could matter; never used for selection or weights, which keep the strict date rule. |

## Open questions

Candidates to send to the SIX contacts from the original brief.

- ~~Exact SMI selection buffer rule~~ — resolved from rulebook 5.12.3.2 (see **Rulebook rules applied**).
- Confirm that ranking on point-in-time FFMCAP (A4), without turnover, is intended.
- Are the duplicate rows in `spi_universe.csv` intentional (a data-validation test) or an export artifact?
- Id `166` has no review-date data: exclude it, or is there a missing row?
- Is there a required precision for published weights and capping factors that the calculation itself must
  use (D13)? And are capping factors normalised so the largest is 1 (A11)?

## Interview talking points

- How the design accommodates new indices, dates and rules (D3, D4).
- Where the brief simplifies the rulebook (A4–A6) and how the design leaves room for the full rules.
- The buffer is what changes the result: plain top 20 gives 3 joiners and 3 leavers, the buffer gives 1 and 1.
- Traceability/auditability: how a reviewer can see why a security joined, left or was capped.
- Separating business-owned parameters (`config/indices.yml`) from technical config and from formulas (D11).
- Why data-quality warnings drive the review status instead of blocking the load (D9, D10), and how
  impact plus estimated rank keep harmless warnings from flagging a review (D16).
- Testing strategy: the brief's worked example (A/B/C, 50% cap → 50 / 37.5 / 12.5) as a first test case.
- Tooling: one-command build/run (D5), API contract + Postman collection (D6).
- Use of AI assistance (allowed by the brief): Claude Code with this decision log kept alongside, so every
  choice is written down and can be explained.
- What I'd do next with more time.
