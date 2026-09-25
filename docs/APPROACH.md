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

## Open questions

Candidates to send to the SIX contacts from the original brief.

- ~~Exact SMI selection buffer rule~~ — resolved from rulebook 5.12.3.2 (see **Rulebook rules applied**).
- Confirm that ranking on point-in-time FFMCAP (A4), without turnover, is intended.
- Are the duplicate rows in `spi_universe.csv` intentional (a data-validation test) or an export artifact?
- Id `166` has no review-date data: exclude it, or is there a missing row?

## Interview talking points

- How the design accommodates new indices, dates and rules (D3, D4).
- Where the brief simplifies the rulebook (A4–A6) and how the design leaves room for the full rules.
- The buffer is what changes the result: plain top 20 gives 3 joiners and 3 leavers, the buffer gives 1 and 1.
- Traceability/auditability: how a reviewer can see why a security joined, left or was capped.
- Testing strategy: the brief's worked example (A/B/C, 50% cap → 50 / 37.5 / 12.5) as a first test case.
- Tooling: one-command build/run (D5), API contract + Postman collection (D6).
- Use of AI assistance (allowed by the brief): Claude Code with this decision log kept alongside, so every
  choice is written down and can be explained.
- What I'd do next with more time.
