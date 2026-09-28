# Approach

Why the Index Reviewer is built the way it is: the choices I made, what I rejected, and the assumptions behind
them. [DESIGN.md](DESIGN.md) describes what the system is; this file explains why.

## How the work went

- **Day 1 (2026-09-25).** Set up the project, this file and the commit hook that keeps it current. Profiled the
  data and read the rulebook before writing code, then built input loading, configuration, the review engine,
  the report and the API, with the Q3 result confirmed in tests. Two design reviews with the assistant reshaped
  the review status (judged by impact and position, with an estimate for unranked securities), iterative
  capping and storing every run.
- **Day 2 (2026-09-26).** Decided the open questions and recorded them as assumptions. An extensibility review
  against the brief's non-functional list and the SLI (in the same rulebook) showed that the first version
  hard-coded the methodology, so it got the seams described below: use cases in a service, input behind an
  interface, the rulebook version per index and ArchUnit layering tests. A Spring conventions pass (one error
  format, config outermost) and more traceability (FFMCAP inputs and build revision in every report) followed.
- **Day 3 (2026-09-27).** Code-quality passes, then Error Prone, mutation testing, coverage and an
  OpenRewrite/PMD scan; each tool found real gaps in the tests or the code. A final end-to-end check on the
  packaged jar recomputed Q3 independently (same result) and found that too few rankable securities crashed
  capping; that now stops with a 422 (A12). A last simplification pass removed what served no current need
  (input checksums, a capping interface, the ranking strategy registry, duplicate records, configurable display
  precision) and replaced clever constructs with plain ones. All Java was then formatted with
  palantir-java-format.
- **Day 4 (2026-09-28).** Read-through of the documentation as a reviewer would meet it: README, DESIGN.md and
  this file tightened, duplicates folded together, and the finished TODO list dropped.

## Starting point: the data and the rulebook

### Input data findings

Profiled before writing any parsing code:

- **Format:** `;`-separated, UTF-8 with BOM, CRLF line endings. No non-numeric or out-of-range values.
- **`composition.csv`:** 20 unique ids (the current SMI), all present in the universe and security data.
- **`spi_universe.csv`:** 409 rows, 205 unique ids; every id except `166` appears twice as an exact duplicate.
- **`sec_data.csv`:** one row per id and date. Cut-off (2026-09-10): all values for 205 ids. Review date
  (2026-09-21): 204 ids, price always empty; `166` has no row. Shares differ between the dates for 17 ids and
  free float for 41, so which date a value comes from matters.
- **Preliminary ranking:** a plain top 20 gives 3 joiners and 3 leavers; with the buffer it is 1 and 1 (`177`
  joins, `103` leaves). `155` (~25%) and `205` (~23%) exceed the 18% cap.

### Rulebook rules applied

Source: *SIX Index Methodology Rulebook Governing Equity and Real Estate Indices*, v3.40 (08.06.2026),
section 5.12, with definitions in 2 and 4.3.

- **Universe (5.12.3.2):** SPI.
- **Selection (5.12.3.2):** 20 components. Ranks 1–18 are selected directly; ranks 19–22 are the buffer, where
  current constituents come first, then new candidates, until there are 20. A current constituent ranked 23 or
  lower leaves.
- **Capping (5.12.4):** a component above 18% is capped at 18% with a capping factor; the excess is
  redistributed until none is above 18% (the brief's example).
- **Q3 2026 result** (confirmed by `ReviewEngineTest`): ranks 1–18 are 17 incumbents plus `177` (joiner, rank
  7). In the buffer, incumbents `160` and `81` take the last two places ahead of `249` and `28`. `103` (rank 35)
  leaves. `155` and `205` are capped at 18% in one round (capping factors 0.5654 and 0.6117). `166` is excluded
  (A2).
- **Not applied** (A4–A6): the full selection-list formula, the liquidity rule for multi-listed instruments,
  and issuer-level capping.

## Stack and delivery

- **Java 25 and Spring Boot 4.1 with Gradle.** My daily stack, so the time went into the domain; Java's types
  suit a rules-heavy model. Python was allowed but rejected.
- **One Gradle project.** There is no UI, so one module is enough. The Gradle toolchain downloads Java 25, so
  `./gradlew` is all a reviewer needs; shared IntelliJ run configurations are in `.run/`.
- **No database.** The input is CSV and the brief puts infrastructure out of scope. Input and storage sit behind
  interfaces, so a database can come later without touching the review.
- **OpenAPI and Postman.** springdoc generates the spec and Swagger UI from the code, so they can't drift. The
  exported Postman collection has test scripts and passes values between requests, so it runs top to bottom
  in newman.
- **Delivered as a Git repository**, so reviewers see the history. The brief and the rulebook PDF are SIX's
  documents and not included; the rules applied are summarized above with section numbers.

## Architecture

- **Packages by responsibility, with a plain-Java core.** The review logic uses only the JDK, so it is
  unit-testable in isolation and a new rule touches `review` alone. Spring stays in `config`, `service` and
  `api`; `config` is pure wiring that nothing depends on. Packages by layer are the natural seams for a single
  feature.
- **Enforced by ArchUnit.** A convention written in docs erodes one import at a time, especially with AI-written
  changes; a test holds on its own. Rejected: Spring Modulith and Java modules, both far heavier than this size
  needs.
- **Use cases in `ReviewService`.** The controller only maps HTTP, so a scheduler or CLI would reuse the
  service instead of copying the sequence. It has no interface, since there is nothing to swap.
- **Input and storage behind interfaces** (`InputSource`, `ReportStore`). At SIX the data would come from a
  market-data system and reports would go to a database; each is then one new class and one bean. The input
  interface takes the index and period, not a path, so a non-file source doesn't have to fake one.
- **One error format.** Every error, Spring's own included, is an RFC 9457 problem response, and a 500 never
  shows internals such as file paths. The plain-Java packages don't log; the report holds the detail.
- **No `@WebMvcTest` slice.** The controller tests run the full stack on the real data, which covers more than
  a slice with a mocked service. Error responses the real data can't trigger are unit-tested on the exception
  handler directly.

## Configuration

- **Business parameters in `config/indices.yml`**, technical settings in `application.properties`. Constituent
  count, buffer, cap, ranking strategy and review dates belong to the index business, so a new quarter or a
  changed cap needs no rebuild. Rejected: a database or admin UI (out of scope), a rules engine or formulas as
  configuration (far too heavy for a handful of parameters).
- **Validated at startup, in the domain.** Spring binds the YAML straight into `IndexDefinition` and
  `ReviewPeriod`, which check themselves when constructed. A bad file stops the app with a clear reason, and the
  rules live in one framework-free place; Bean Validation would duplicate them. Indices and periods are lists
  with a `name`/`id` rather than maps, because Spring's relaxed binding mangles keys like `2026-Q3`. Names must
  be valid folder names, since they name the input and report folders; otherwise a name like `SMI Q3` would
  start fine and then fail every review with a 500.
- **Rulebook version per index, with no default.** SIX revises the rulebook, and a v3.40 report must stay
  explainable after v3.41. Every report repeats the version.
- **One input folder per index and review period.** A single folder would let Q4 files overwrite Q3's, so past
  reviews couldn't be re-run. Rejected: file paths per period in the YAML, which mix business configuration
  with file handling.

## Input validation

- **Strict per file, lenient per row.** Without a file or column nothing meaningful can be computed, so that
  stops with a 422. One typo in a row shouldn't block a quarterly review, so the row is skipped, with a
  warning that keeps it visible. Rejected: failing on the first bad row, and OpenCSV's bean binding, which gives
  coarse errors instead of per-row warnings.
- **Strict UTF-8.** Java's default decoding silently replaces bad bytes with `�`, so a corrupted id or number
  would pass as a different one. The BOM is skipped by hand rather than adding Commons IO for it.
- **Warnings carry an impact** (`NONE` or `MISSING_DATA`), so only lost data can affect the review status.
- **Missing values are the review's question.** Which values a security needs depends on the date's role, which
  only the review knows. So the loader keeps empty values as missing, and `166` becomes an eligibility decision,
  not a load error. `null` means "missing" only in the input and report records; the review logic uses
  `Optional`.
- **Too few rankable securities stop the review (A12).** An SMI of 15 is not the SMI, and its caps might not
  reach 100% (5 × 18% = 90%). An earlier version selected what it had and flagged the status, but crashed in
  capping below 6 securities. The input is the problem, so it is treated as unusable input.
- **An input check endpoint.** `GET .../input` validates a quarter's files without running the review, so
  operations can check the data first.

## The review

- **A pipeline of small steps**, each a class with its own tests. The result keeps every step, so each line of
  the report traces to one rule, and each rule can change on its own. The pipeline stays one flat package: Java
  has no subpackage visibility, so splitting it would make the package-private steps public.
- **Eligibility checks what the weights need** (price at cut-off, shares and free float at the review date).
  Weights always use FFMCAP, so this holds whatever the ranking strategy. The extensibility review suggested
  moving eligibility behind the ranking strategy; rejected for that reason.
- **Ranking strategy as an enum.** The YAML picks it by name, so binding rejects an unknown name at startup, and
  `EligibleSecurity.rankingValue` switches over it without a default, so a new constant doesn't compile until
  it says how to rank. It replaced a strategy interface, a registry and a startup check, all for one criterion.
- **The ranking seam is narrow on purpose.** A strategy sees one security's price, shares and free float. The
  rulebook's selection list (A4) needs turnover and history, a view of the whole input, and a rule for short
  histories. Widening the signature now would add a parameter nothing uses, for data we don't have.
- **Capping is iterative (A14).** A literal single pass would publish `63` at 15.60% on the real data at a 15%
  cap. The cap exists to limit concentration, and the brief's example ends with "all constituents are now below
  the cap".
- **One cap, no capping interface.** A `CappingRule` interface allowed per-constituent caps for the SLI, but its
  only second implementation lived in a test, so it was removed as a seam for an index not in scope. Tiered caps
  later are a local change: read each constituent's cap from a map instead of one value.
- **Weights and capping factors both reported.** The weight is the result and can be checked against the cap;
  the capping factor is what index calculation carries forward (A11).
- **Full precision, rounding only for display.** Iterative capping divides repeatedly, so intermediate rounding
  would compound. Displayed weights are not forced to 100%. The display precision is fixed in `ReportBuilder`
  instead of being three settings nobody would change.

## Review status

- **Why a status.** With lenient loading alone, a typo in a top constituent's row would silently turn it into a
  leaver. The status says whether data problems could have changed the result.
- **Judged by impact and position, not by count.** The first version flagged Q3 because the duplicate-row
  warning names 204 ids; the warning's impact fixed that. Rejected: a warning-count threshold (says nothing
  about impact) and failing the load on any bad row.
- **An estimate for unranked securities (A13).** Without it, a penny stock with missing data would flag every
  review. `166` would rank around 197 of 205, so Q3 is `COMPLETED_WITH_WARNINGS`. The estimate only judges; it
  never selects or weights. It is its own class (`UnrankedEstimate`) because it is the largest and most
  assumption-laden part of the status.
- **Structured reasons.** Free text can't be filtered or tested reliably, and stored reports live long. All
  reasons are kept, harmless ones too.
- **Part of the review, not the report.** The status applies review rules (buffer end, ranking), so in `report`
  it would hide a business rule in the formatting layer. It is the subpackage `review.status` because it reads
  only a finished result; an ArchUnit rule keeps `review` from depending on it.

## Traceability and auditability

- **The report explains itself.** It holds the input values behind every FFMCAP, so `103` = 165.7 × 45,867,891
  × 1 can be recomputed by hand, and everything else DESIGN.md lists under *Traceability*. A security excluded
  from ranking is recorded once, as a warning; a separate `excluded` list only repeated it.
- **Input values, not file checksums, are the audit trail.** SHA-256 checksums of the files added little over
  the recorded values and tied a CSV detail into the domain. With a market-data system, the `InputSource`
  would record a snapshot id or as-of time instead.
- **The build is recorded** in each report and in `/actuator/info`. `git.properties` is limited to branch,
  commit and time, so no names or emails end up in the jar.
- **`POST` returns a summary, not the full report.** The response answers "what changed, and is it OK?" on one
  screen; the 204-row ranking and capping rounds are in the stored report. `reportUrl` repeats the `Location`
  header because Swagger UI, Postman's body view and JSON-only clients don't show headers. The disk path isn't
  returned: it means nothing to a client on another machine.
- **Stored files are never changed.** The run id is the nanosecond UTC time, written create-only, so a clash
  fails instead of overwriting. Millisecond ids with retry suffixes were dropped as extra code for a case that
  practically never happens.
- **Stored reports are returned byte for byte.** A report from an older build may not match today's
  `ReviewReport`, so it isn't re-read into it.
- **The report format stays flat.** It is the published JSON, so nesting fields later would break clients; the
  fields that could be mixed up are tested instead.

## Code quality

After the features were done, the code was scanned smell by smell:

- **A record only where the shape differs.** The API returns `IndexDefinition` itself and the report reuses the
  review's `Leaver`, since their copies matched field for field. Own records remain where values are rounded or
  fields left out. The summary's constituent is `ConstituentSummary` because OpenAPI names schemas by simple
  class name, and a second `Constituent` was silently merged with the report's; a test keeps them apart.
- **Narrow try blocks.** A wide one can swallow a bug as an "invalid row".
- **Streams to build collections, `for` loops for multi-step work,** and no `forEach` lambdas that fill another
  collection, since they hide the side effect.
- **Methods that read as the algorithm.** Long methods were split into named steps (capping: weights this round
  → anyone above the cap? → cap them and repeat → final weights and factors). Where several steps share data
  (`WeightCapping`, `StatusAssessment`), a static entry point creates a private instance holding it. Stateless
  steps stay static and `final`.
- **Duplication removed only where it was the same rule.** "Joiner" is derived from the selection outcome, not
  stored twice. Look-alikes stay where the rule differs: eligibility and the status estimate read the same data
  under different rules.
- **Formatting by a tool.** palantir-java-format via Spotless is closest to the hand-written style (4 spaces,
  120 columns); Google's 2 spaces and 100 columns would have changed nearly every line. The reformat is its own
  commit, listed in `.git-blame-ignore-revs`.

Three tools check the code and tests. None is a percentage gate: a threshold rewards tests written for the
number, and the value is in reading what they find.

- **Error Prone** on every compile, warnings failing the build: cheap, and a warning that only scrolls by is
  never read. One finding needed care: its format-string annotation on `Validation.require` would have put a
  library into `domain`, so callers pass a finished message instead.
- **Mutation testing** (pitest, on demand) rose from 88% to 98%. It found untested rules (the tie-break, buffer-
  full leavers, the estimate's boundaries) and a condition that was always true. Three survivors are accepted:
  removing the weight invariant check (tested directly), its tolerance boundary, and a redundant but
  explanatory check in the capping loop.
- **Coverage** (JaCoCo) rose from 89% to 99.6% of branches; the gaps were in input handling and validation.
  Left uncovered: one-line I/O rethrows, `main` and a missing commit id.

A one-off scan with OpenRewrite and PMD 7 found little: a few import-order, method-reference and
reassigned-parameter fixes. Neither is in the build; PMD's defaults are mostly noise here, and a permanent
gate would mostly collect suppressions.

## Working with AI assistance

The brief allows AI assistance; this project was built with Claude Code.

- **Lasting rules in `AGENT.md`, the reasoning in this file.** Work spans sessions with fresh context, so
  nothing may live only in a chat.
- **A hook keeps this file current.** A commit is blocked while this file is unchanged; the written rule alone
  was skipped twice.
- **Code comments stand on their own.** A comment that defends a choice gives the reason itself. Assumption
  numbers stay in the code, where it applies a rulebook interpretation, like citing a clause.

## Assumptions

Where the brief or rulebook is ambiguous, the assumption is recorded here and referenced in code. None of them
changes the Q3 result, and each is visible in the report and can be changed in one place.

| #  | Assumption | Rationale |
|----|------------|-----------|
| A1 | Exact duplicate rows in `spi_universe.csv` are de-duplicated, with a warning. | They are identical, so no information conflicts; the warning has impact `NONE`. |
| A2 | A universe security without review-date data (`166`) is excluded from ranking, with a warning. | FFMCAP needs shares and free float at the review date; cut-off values would break the brief's date rule. Its estimated FFMCAP (12.9M) is far below rank 22 (15.4bn), so Q3 stays `COMPLETED_WITH_WARNINGS` (A13). |
| A3 | The empty review-date price column is expected. | Prices are only taken at cut-off, as in the brief's formula. |
| A4 | Ranking uses FFMCAP, as the brief specifies, not the rulebook's selection list (4.3: 50% 12-month average FFMCAP share, 50% 12-month turnover share). | No turnover or history data is provided. |
| A5 | The liquidity rule for instruments listed on several exchanges (5.12.3.2) is not applied. | The data has no listing or turnover fields. |
| A6 | Each id is a separate issuer; issuer-level capping (5.12.4) is not applied. | The data has no issuer field. |
| A7 | Buffer candidates of the same kind (incumbent or new) are taken in rank order. | The rulebook puts incumbents first but doesn't order within a group. |
| A8 | Ties in FFMCAP are broken by id. | Keeps results deterministic; no ties occur in this data. |
| A9 | Conflicting rows in `sec_data.csv` (same id and date, different values) are all dropped with a warning; identical ones are de-duplicated. | Neither row can be trusted; the security becomes ineligible visibly instead of one row being picked silently. |
| A10 | Rows with impossible values (price or shares not positive, fractional shares, free float outside (0, 1]) are skipped with a warning. | They would distort FFMCAP. |
| A11 | Capping factors are scaled so the largest is 1. The brief's "weighting factors" are read as capping factors; final weights are reported too. | Only the ratios matter; this is the usual published form. Computed at full precision. |
| A12 | If fewer securities can be ranked than the index needs, the review fails (422) and stores no report. | The rulebook doesn't cover it; an undersized index isn't a valid composition, and inventing a fill rule would be worse. For Q3, 204 of 205 can be ranked. |
| A13 | For the status only, an unranked security's ranking value is estimated from either date's data. It counts as harmless only below **half** the value at the buffer end. | It never selects or weights. The margin covers the borrowed values (17 ids change shares, 41 free float between the dates). |
| A14 | Capping is iterative: a security pushed above the cap by redistribution is capped too. | No published weight exceeds the cap; the rulebook's wording only names components above 18% of the total. A single pass gives the same Q3 result. |
| A15 | Market data is taken for the exact cut-off or review date, with no fallback to an earlier value. | The brief ties each value to one date; an older value would silently mix in stale data. Only `166` lacks a row. |

## Next steps

- **The rulebook's selection list (A4)**, once the input has turnover and 12-month history. DESIGN.md, *Limits
  of the design*, lists what that changes.
- **Issuer-level capping (A6)**, once the input has an issuer field.
- **Chaining reviews:** mark one stored run as official and take the next review's current composition from
  it instead of a CSV.
- **A database behind the report store**: one new `ReportStore` class and bean.
