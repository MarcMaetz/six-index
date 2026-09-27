# Approach

How the Index Reviewer was built and why: the choices made, what was rejected, and the assumptions behind them.
It is written in hindsight and ordered by topic; the commit history has the order in which things happened.
[DESIGN.md](DESIGN.md) describes the system as it is; this file explains why it looks that way.

## How the work went

- **Day 1 (2026-09-25).** Profiled the data and read the rulebook before writing code. Built the input loading,
  the configuration, the review engine, the report and the API; the Q3 result was confirmed in tests. Two design
  reviews ("grill me" sessions with the assistant) then refined the review status, capping and report storage.
- **Day 2 (2026-09-26).** Decided the open questions myself instead of asking SIX. An extensibility review
  against the SLI (three sections after the SMI in the same rulebook) showed that the first version hard-coded
  the methodology; it got the seams described below. A Spring conventions pass and more traceability followed.
- **Day 3 (2026-09-27).** Code-quality passes, then static analysis, mutation testing and coverage. Each tool
  found real gaps in the tests, all closed. The requirements were rechecked end to end on the packaged jar. A
  last full scan recomputed the Q3 result separately (same answer) and found that an index with too few
  rankable securities crashed in capping instead of being flagged; such a review now stops with a 422 (A12).
  A simplification pass then removed what served no current need: input file checksums, the capping
  interface (its only second implementation was in a test), records that copied others field for field, and
  configurable display precision.

## Starting point: the data and the rulebook

### Input data findings

Profiled before writing any parsing code.

- **Format:** `;`-separated, UTF-8 with BOM, CRLF line endings.
- **`composition.csv`:** 20 unique ids (the current SMI), all present in the universe and the security data.
- **`spi_universe.csv`:** 409 rows but 205 unique ids; every id except `166` appears twice as an exact duplicate.
- **`sec_data.csv`:** one row per id and date. Cut-off (2026-09-10): all values for 205 ids. Review date
  (2026-09-21): 204 ids, price always empty. `166` has no review-date row. Shares differ between the dates for
  17 ids and free float for 41, so which date a value comes from matters.
- No non-numeric or out-of-range values.
- **Preliminary ranking:** a plain top 20 gives 3 joiners and 3 leavers; with the buffer it is 1 and 1 (`177`
  joins, `103` leaves). `155` (~25%) and `205` (~23%) exceed the 18% cap.

### Rulebook rules applied

Source: *SIX Index Methodology Rulebook Governing Equity and Real Estate Indices*, v3.40 (08.06.2026),
section 5.12, with definitions in 2 and 4.3.

- **Universe (5.12.3.2):** SPI.
- **Selection (5.12.3.2):** 20 components. Ranks 1–18 are selected directly. Ranks 19–22 are the buffer:
  current constituents in the buffer come first, then new candidates, in rank order, until there are 20. A
  current constituent ranked 23 or lower leaves.
- **Capping (5.12.4):** a component above 18% of the total is capped at 18% with a capping factor; the excess
  is redistributed (the brief's example) until none is above 18%.
- **Q3 2026 result** (confirmed by `ReviewEngineTest`): ranks 1–18 are 17 incumbents plus `177` (joiner, rank
  7); in the buffer, incumbents `160` and `81` take the last two slots ahead of `249` and `28`; `103` (rank 35)
  leaves. `155` and `205` are capped at 18% in one round (capping factors 0.5654 and 0.6117). `166` is
  excluded (A2).
- **Not applied** (A4–A6): the full selection-list formula, the liquidity rule for multi-listed instruments,
  and issuer-level capping.

## Stack and delivery

- **Java 25 and Spring Boot 4.1, built with Gradle.** It is my daily stack, so the time went into the domain.
  Spring provides the API, configuration binding and OpenAPI docs. Python was allowed but rejected: Java's type
  system suits a rules-heavy model.
- **One Gradle project at the repository root.** There is no UI, so one module is enough. The Gradle toolchain
  downloads Java 25, so `./gradlew` is all a reviewer needs. IntelliJ uses shared Gradle run configurations in
  `.run/` (Spring Boot run configurations need IntelliJ Ultimate).
- **No database.** The input is CSV and the brief puts infrastructure out of scope. Input and storage sit behind
  interfaces, so a database can come later without touching the review.
- **OpenAPI and Postman.** springdoc generates the spec and Swagger UI from the code, so the documentation
  can't drift. A Postman collection with test scripts is exported to `postman/`; its requests pass values to
  each other, so it runs top to bottom in newman.
- **Delivered as the GitHub repository**, not a ZIP: reviewers see the history next to this file, and GitHub's
  "Download ZIP" still gives an archive. The README is the entry point; `DESIGN.md` gives the structured view.
  The brief and the SIX rulebook PDF are not in the repository: they are SIX's documents, not mine to publish.
  The rules the code applies are summarized under *Rulebook rules applied*, with section numbers.

## Architecture

- **Packages by responsibility:** `domain`, `ingest`, `review`, `report`, `store`, `service`, `api`, `config`. The review logic uses only the JDK, so it is unit-testable in isolation and a new rule goes into
  `review` alone. `ingest` adds only OpenCSV and `store` only Jackson. Spring appears only in `config`,
  `service` and `api`, and `config` is pure wiring that nothing depends on.
- **Enforced by ArchUnit.** A convention written in the docs erodes one import at a time, especially with
  AI-assisted changes; as a test it holds on its own. `ArchitectureTest` checks the dependency direction, the
  plain-Java core, where Spring may appear, and that packages have no cycles; a deliberate violation made it
  fail. Rejected: Spring Modulith and Java modules per package, both far heavier than this size needs. The
  lookup of configured indices (`IndexCatalog`) lives in `service`, its only user: it once had its own package,
  which held just the lookup and its not-found exception.
- **Use cases in `ReviewService`.** The controller only maps HTTP. The service looks up the index and period, then
  chains input → review → report → store and is tested without Spring, so a scheduler or command-line entry point would reuse it
  instead of copying the sequence. No interface: there is nothing to swap.
- **Input and storage behind interfaces** (`InputSource`, `ReportStore`). At SIX the data would come from a
  market-data system and reports would go to a database; each is then one new class and one bean. The input
  interface takes the index and period, not a file path, so a non-file source doesn't have to fake one.
- **Spring conventions.** Every error, Spring's own included, is an RFC 9457 problem response, and a 500 never
  shows internals such as file paths. Logging sits in `service` and `api`: each stored run at INFO, unexpected
  errors at ERROR. The plain-Java packages don't log; the report holds the detail.
- **Kept deliberately:** no `@WebMvcTest` slice (the full-stack API tests cover more than a slice with a mocked
  service), the `com.example` group id from Spring Initializr, and packages by layer, which are the natural seams
  with one feature.

## Configuration

- **Business parameters in `config/indices.yml`**, separate from technical settings in
  `application.properties`. Each index states its universe, constituent count, direct-selection rank, buffer
  end, weight cap, ranking strategy, rulebook version and review periods. These values belong to the index
  business, so a new quarter or a changed cap needs no rebuild. A default copy is packaged; a file in
  `./config/` overrides it. Rejected: a database or admin UI (out of scope), a rules engine or formulas as
  configuration (far too heavy for a handful of parameters).
- **Validated at startup.** Spring binds the file straight into `IndexDefinition` and `ReviewPeriod`, which
  check themselves when constructed, so a bad file stops the application with a clear reason and the rules live
  in one framework-free place. Bean Validation annotations would duplicate those checks. Indices and periods are
  lists with a `name`/`id`, not maps: Spring's relaxed binding mangles keys such as `2026-Q3`. Index names and
  period ids must be folder names (letters, digits, `_`, `.`, `-`), since they name the input and report folders;
  before, a name like `SMI Q3` passed startup and every review of it failed with a 500 when the report was saved.
- **Rulebook version per index.** Each index must state which rulebook version and section it follows, and every
  report repeats it: SIX revises the rulebook, and a v3.40 report must stay explainable after v3.41. There is no
  default, because a new index must state its rules.
- **One input folder per index and review period** (`data/SMI/2026-Q3/`). A single flat folder would let Q4 files
  overwrite Q3's, so past reviews couldn't be re-run. File paths per period in the YAML were rejected: they mix
  business configuration with file handling.

## Input validation

- **Strict per file, lenient per row.** A missing folder, file or column stops the review with a 422: nothing
  meaningful can be computed. A bad or duplicate row is skipped with a warning naming the file, line and
  security: one typo shouldn't block a quarterly review, but it has to stay visible. Rejected: failing on the
  first bad row, and OpenCSV's bean binding, which gives coarse errors instead of per-row warnings.
- **Strict UTF-8.** A file that is not valid UTF-8 is unusable too (422). Java's default decoding replaces bad
  bytes with `�` without a word, so a corrupted id or number would pass as a different one or surface as a
  puzzling row warning. The BOM the delivered files start with is skipped by hand; Commons IO's
  `BOMInputStream` would do the same with one more dependency.
- **Each warning has an impact:** `NONE` (nothing lost, e.g. an identical duplicate) or `MISSING_DATA` (a row
  ignored, conflicting rows dropped, a security excluded). Only lost data can affect the review status.
- **Missing values.** Which values a security needs depends on the date's role (price at cut-off, shares and
  free float at the review date), which only the review knows. So the loader keeps empty values as missing, and
  `166` becomes an eligibility question for the review, not a load error. `null` stands for "missing" only in
  these input and report records; inside the review logic values are `Optional`.
- **Too few rankable securities stop the review (A12).** If fewer securities can be ranked than the index has
  constituents, the review fails with a 422 naming both counts, and no report is stored. An SMI of 15 is not the
  SMI, and its caps might not add up to 100% (5 × 18% = 90%). The first version selected all it had and flagged
  the status, but that crashed in capping below 6 securities; the input is the problem, so it is treated like
  unusable input.
- **An input check before the review.** `GET .../reviews/{period}/input` loads and validates a quarter's files
  without running the review, so operations can check the data first. Unknown index or period: 404. Unusable
  input: 422, since the request is valid but the data isn't.

## The review

- **A pipeline of small steps:** eligibility → ranking → selection → capping, each a separate class with its own
  tests. The result keeps every step (exclusions, a decision for every ranked security, the capping rounds,
  leavers with reasons), so every line of the report traces to one rule, and each rule can change on its own.
- **Eligibility** checks the data the weights need: price at cut-off, shares and free float at the review date.
  Weights always use FFMCAP, so this holds whatever the ranking strategy.
- **Ranking by FFMCAP, through a named strategy.** `RankingStrategy` is an enum the YAML picks by name, so
  Spring's binding rejects an unknown name at startup. `EligibleSecurity.rankingValue` computes each strategy in
  a `switch` without a default: a new constant doesn't compile until it says how to rank. An earlier version
  had a strategy interface, a registry with its own lookup error, and a startup check, all for one criterion;
  the enum keeps the extension point with none of that. A strategy that needs outside services (a database of
  history) would need an interface again, and a wider input anyway (below).
- **The ranking seam is narrow on purpose.** A strategy sees one security's price, shares and free float. The
  rulebook's selection list (A4: 12-month average FFMCAP and turnover) needs more: turnover and history in the
  input, a strategy that sees the whole review's input, and a rule for securities with a short history. Widening
  the signature now would add a parameter nothing uses, for data we don't have.
- **Buffer selection** follows the rulebook: ranks 1–18 directly, then incumbents from the buffer before new
  candidates, each in rank order (A7). Ties are broken by id (A8). The buffer always fills constituent count
  minus direct selection rank slots (20 − 18 = 2), since the review stops earlier if too few are ranked (A12).
- **Capping is iterative.** Capped constituents get their cap, the rest share the remainder in proportion to
  FFMCAP, and this repeats while anyone is above the cap (A14). A literal reading of rulebook 5.12.4 caps only
  securities whose raw share exceeds 18%. On the real data at a 15% cap, that single pass would publish `63` at
  15.60%; the loop caps `63` and `64` in a second round. At 18% both agree. The cap exists to limit
  concentration, and the brief's example ends with "all constituents are now below the cap".
- **One cap for all constituents, no capping interface.** The SMI caps every constituent at 18%, so
  `WeightCapping` takes that one number. An earlier `CappingRule` interface let each constituent have its own cap,
  for the SLI (largest constituents at 9%, the rest at 4.5%), but its only second implementation lived in a test.
  It was removed as a seam for an index that isn't in scope. Adding tiered caps later is a small, local change:
  the loop would read each constituent's cap from a map instead of one value.
- **Weights and capping factors are both reported.** The weight is the review's result and can be checked
  against the cap; the capping factor is what index calculation carries until the next review (A11).
- **Full precision, rounding only for display.** `BigDecimal` with 34 significant digits and no intermediate
  rounding, since iterative capping divides repeatedly and rounding would compound. The weights are checked to
  add up to 1 and to stay within their caps. Displayed weights are not forced to 100%: for Q3 they add up to
  99.999999%, which is correct. The display precision (weights 6 decimals in percent, capping factors 10,
  FFMCAP 2) is fixed in `ReportBuilder`; it used to be three settings nobody would change.

## Review status

- **Why a status.** With lenient loading alone, a typo in a top constituent's row would silently make it a
  leaver. The status says whether data problems could have changed the result: `COMPLETED`,
  `COMPLETED_WITH_WARNINGS` (they couldn't) or `REQUIRES_ATTENTION` (they could).
- **Judged by impact and position.** Missing data matters on a current constituent, on a security ranked within
  the buffer, or on an unknown security. The first version flagged Q3 because the duplicate-row warning names 204
  ids; the warning's impact fixed that.
- **An estimate for unranked securities.** A security that couldn't be ranked gets an estimated ranking value
  from whatever data it has; it is harmless only below half the value at the buffer end (A13). `166` would rank
  around 197 of 205, so Q3 is `COMPLETED_WITH_WARNINGS`. The estimate adds complexity, but without it a penny
  stock with missing data would flag every review. It only judges; it never selects or weights.
- **Structured reasons.** Each reason names the security, a `relevance` value that decides the status, an
  explanation and the warning it comes from. All reasons are kept, harmless ones too. Free text can't be filtered
  or tested reliably, and stored reports are long-lived.
- **Part of the review, not the report.** The status applies review rules (buffer end, ranking strategy), so it
  lives in `review.status`; in the report package it would hide a business rule in the formatting layer. It is a
  subpackage because it is a separate stage: it reads a finished `ReviewResult` and only public review types, and
  the review never depends on it (an ArchUnit rule, since the slice check sees `review` as one). `ReportBuilder`
  calls `StatusAssessment.assess(result)` directly; a `ReviewResult.assessment()` shortcut would make the two
  packages depend on each other. The pipeline stays one flat package: Java has no subpackage visibility, so
  splitting ranking, selection or capping would make their package-private steps public.
- **Split by rule.** `StatusAssessment.assess(result)` builds a private instance holding the result and its rank
  lookup, and reads top to bottom: warnings → where each security stands → status. The A13 estimate, the largest
  and most assumption-laden part, is its own class (`UnrankedEstimate`) with the buffer-end value computed once.
  The ranked case is one comparison with the buffer end, so it stays a method (`ranked`) next to
  `unranked.judge`; a class for it would be mostly constructor.
  The rule from reasons to status is `ReviewStatus.of`. Rejected: a nested state-holder inside a static-only
  class (two classes for one job) and one class for all three rules (160 lines, the A13 details drowned out
  the overview).
- **Rejected:** a warning-count threshold (says nothing about impact), treating every unranked security as
  relevant, and failing the load on any bad row.

## Traceability and auditability

- **The report explains itself.** It holds the parameters with the rulebook version, the full ranking with the
  price, shares and free float behind each FFMCAP (so `103` = 165.7 × 45,867,891 × 1 can be recomputed by hand),
  leavers with reasons, weights and capping factors, the capping rounds, the input files read,
  all warnings and the status reasons. A security excluded from ranking is recorded once, as a warning with its
  reason: the status is built from warnings, and a separate `excluded` list only repeated them.
- **The input values are the audit trail, not file checksums.** The ranking records every price, shares and free
  float the result was computed from, so the report can be rechecked on its own. SHA-256 checksums of the input
  files were dropped: they add little over those values, and they tied a CSV detail into the domain. In
  production the input would come from a market-data system or database, where a snapshot id or as-of time
  would identify the data, recorded by that `InputSource`.
- **It records the build.** Each report carries the application version and Git revision (`-dirty` with
  uncommitted changes, `unknown` without Git); `/actuator/info` shows the same. `git.properties` is limited to
  branch, commit and time, so no names or emails end up in the jar.
- **Every run is stored as written.** `POST` saves the full report as JSON and returns 201 with its location and a
  summary: report id and `reportUrl`, status and reasons, the constituents with weights, joiners and leavers. The
  audit trail (204-row ranking, capping rounds, input files, warnings) is only in the stored report, so the
  response answers "what changed, and is it OK?" in one screen; returning the full report was rejected as mostly
  detail nobody reads at that moment. `reportUrl` repeats the `Location` header in the body: the header is the HTTP
  standard, but Swagger UI, Postman's body view and JSON-only clients don't show it. The disk path is not in the
  response; it is the store's internal layout and means nothing to a client on another machine. List and get
  endpoints read runs back. Files are created once and never changed, so "which report did we publish for Q3, and
  when?" has an answer even after the configuration or data change. The run id is the UTC generation time to the
  nanosecond, written with a single create-only attempt: a clash fails the request instead of overwriting.
  Millisecond ids with a `-2`, `-3` retry suffix were dropped as extra code and an extra sort rule for a case that
  practically never happens.
- **Stored reports are returned byte for byte.** A report written by an older build may not match today's
  `ReviewReport`, so it isn't re-read into it; the OpenAPI docs still show the current schema. Listing reads only
  the time and status, and fails with the field's name if one is missing.
- **The report format stays flat.** It is the published JSON (API, stored reports, Postman), so nesting its
  fields would be a breaking change; the fields that could be mixed up are tested instead.
- **Not done:** archiving the input files with each report; the report keeps the values that were used, not
  the files. This is listed in `DESIGN.md` under *Limits of the design*.

## Code quality

After the features were complete, the code was scanned smell by smell:

- **A record only where the shape differs.** The API returns `IndexDefinition` itself, and the report uses the
  review's `Leaver` and `Exclusion`; their former copies matched field for field. Own records remain where the
  shape really differs: the report rounds values (`Constituent`, `RankingEntry`), and `Joiner` and `WarningRef`
  leave fields out (a reason's `WarningRef` drops the warning's id list, which would otherwise repeat 204 ids in
  each of 204 reasons). The summary's constituent record is called `ConstituentSummary`: OpenAPI names schemas
  by simple class name, so a second `Constituent` was silently merged with the report's in Swagger, showing
  the summary with fields it doesn't have. A test keeps the two schemas apart.
- **Try blocks** wrap only the call whose exception they translate; a wide try can swallow a bug as an
  "invalid row".
- **Loops and streams.** A stream when it builds a new list or map (`toList()`, `groupingBy`); a plain `for`
  loop when it fills an ordered map or needs several steps per element. `forEach` with a lambda that fills
  another collection hides the side effect, so the code has none.
- **Plain over clever.** Buffer filling sorts incumbents first and takes the free slots (a stable sort keeps the
  rank order within each group) instead of looping over `{true, false}`. The status estimate picks its relevance
  and wording from one boolean instead of passing sentence fragments to a helper.
- **Long methods** were split into named steps, so the top-level method reads as the algorithm (capping:
  weights this round → anyone above the cap? → cap them and repeat → final weights with capping factors).
  Single loops that read top to bottom were left alone.
- **Shared state in an object.** Where the steps of one calculation all need the same data (`WeightCapping`:
  FFMCAP, the cap, the growing capped set; `StatusAssessment`: the result and its rank lookup), a static entry
  point creates a private instance that holds it, instead of passing three parameters to every step. Steps
  without shared state (`Eligibility`, `Ranking`, `Selection`) stay static, with a private constructor and
  `final` so they can't be instantiated or extended.
- **Duplication** was removed where it was the same rule. A `Constituent` is its selection outcome plus its
  weight, and "joiner" is derived from the outcome, not stored a second time. Look-alikes stayed where the shape or
  the rule differs: the API's summary is a subset of the report, and eligibility and the status estimate read the same data under
  different rules.
- **Visibility and naming.** The pipeline steps are package-private; other packages see only the engine and
  its results.   Accessor chains name each hop (`o.ranked().eligible().ffmcap()`).
- **Warnings** are collected per input file instead of in one list passed through every method.
- **Dead code:** one unused import, plus two conditions that were always true, found by the tools below.

- **Formatting by a tool.** Spotless with palantir-java-format (4 spaces, 120 columns, the closest standard
  formatter to the hand-written style); `spotlessCheck` runs in the build, so formatting never comes up in
  review. Google's formatter was rejected: 2 spaces and 100 columns would have changed nearly every line. The
  reformat is one commit with nothing else in it, listed in `.git-blame-ignore-revs` so `git blame` still shows
  who wrote each line. It was added last, after the code was stable.

Three tools check the code and the tests themselves. None of them is a percentage gate: a threshold rewards
tests written for the number, and the value is in reading what they find.

- **Error Prone** runs on every compile, and its warnings fail the build: it is quiet and cheap, and a warning
  that only scrolls by is never read. Its first six findings were fixed. One needed care: annotating
  `Validation.require` for format-string checks would put a library annotation into `domain`, which the
  architecture rules forbid, so callers now pass a finished message instead.
- **Mutation testing** (`./gradlew pitest`, on demand) plants small bugs in the review logic and checks that a
  test catches each: 88% at first, 98% now. It found untested rules (the tie-break by id, buffer-full leavers,
  the estimate's boundaries) and a condition that was always true. Three survivors are accepted: removing
  the weight invariant check (a guard that only fails on a bug; tested directly), its tolerance boundary, and a
  redundant but explanatory check in the capping loop.
- **Coverage** (`./gradlew test jacocoTestReport`): 89% of branches at first, 99.6% now. The gaps were in input
  handling and validation: files without a byte order mark, blank lines, empty files, out-of-range values, the
  422 response, configuration rules. Left uncovered: one-line I/O rethrows, `main` and a missing commit id.

A one-off smell scan checked for anything these three miss: OpenRewrite's Java 25 migration and
static-analysis recipes (dry run), and PMD 7 with its design, best-practice and code-style rules. Neither is part
of the build. PMD's defaults assume shared mutable state and pre-SLF4J logging, so most of its findings are
noise here, and a permanent gate would mostly collect suppressions. OpenRewrite found no missed modern idiom:
the code already uses records, `var`, `Stream.toList()`, `formatted()` and sequenced collections. Taken: import order, a parameter
that was reassigned as a counter, and two lambdas that could be method references. Left as they are: exceptions that become
row warnings or 404s without their cause (the message is the whole story), `serialVersionUID` on exceptions
that are never serialized, and the lazy `() -> reportsDir.toString()` supplier, which must not read the
`@TempDir` field when it is registered.

## Working with AI assistance

The brief allows AI assistance; this project was built with Claude Code.

- **Docs split by how often they change.** `AGENT.md` holds lasting rules, `docs/TODO.md` the current status,
  this file the reasoning. Work spans sessions with fresh context, so nothing may live only in a chat.
- **A hook keeps this file current.** A commit by the assistant is blocked while this file is unchanged; the
  written rule alone was skipped twice.
- **Code comments stand on their own.** Where a comment defends a choice a reader might otherwise undo, it
  gives the reason itself instead of pointing into this file. Assumption numbers (A1–A15) stay in the code:
  they mark where it applies an interpretation of the rulebook, like citing a clause.

## Assumptions

Where the brief or rulebook is ambiguous, the assumption is recorded here and referenced in code.

| #  | Assumption | Rationale |
|----|------------|-----------|
| A1 | Exact duplicate rows in `spi_universe.csv` are de-duplicated, with a warning, not rejected. | They are identical, so no information conflicts. |
| A2 | A universe security with no review-date data (`166`) is excluded from ranking, with a warning. | FFMCAP needs shares and free float at t; falling back to cut-off values would break the brief's date rule. |
| A3 | The empty review-date price column is expected. | Prices are only taken at cut-off (t'), as in the brief's formula. |
| A4 | Ranking uses FFMCAP only, as the brief specifies, not the rulebook's selection list (4.3: 50% 12-month average FFMCAP share + 50% 12-month turnover share). | No turnover or history data is provided; see *The review* for what adding it would take. |
| A5 | The extra liquidity rule for instruments listed on several exchanges (5.12.3.2) is not applied. | The data has no listing or turnover fields. |
| A6 | Each id is treated as a separate issuer; issuer-level capping (5.12.4) is not applied. | The data has no issuer field. |
| A7 | Buffer candidates of the same kind (incumbent or new) are taken in rank order. | The rulebook says incumbents come first, but not how to order within a group. |
| A8 | Ties in FFMCAP are broken by id. | Keeps results deterministic; no ties occur in this data. |
| A9 | Conflicting rows in `sec_data.csv` (same id and date, different values) are all dropped with a warning; identical ones are de-duplicated. | Neither row can be trusted; the security becomes ineligible, visibly, instead of one row being picked silently. |
| A10 | Rows with out-of-range values (price or shares not positive, fractional shares, free float outside (0, 1]) are skipped with a warning. | Such values can't be real and would distort FFMCAP. |
| A11 | Capping factors are normalised so the largest is 1: uncapped constituents have 1, capped ones less. | Only the ratios matter; this is the usual published form. The brief's "weighting factors" is read as capping factors; final weights are reported too. |
| A12 | If fewer securities can be ranked than the index needs, the review fails (422) and no report is stored. | The rulebook doesn't cover it. An index with fewer constituents than defined isn't a valid composition, and inventing a fill rule would be worse; the input has to be fixed. |
| A13 | For the review status, an unranked security's ranking value is estimated with data from either date (price preferably from the cut-off, shares and free float preferably from the review date). It counts as harmless only below **half** the value at the buffer end. | Only judges whether missing data could matter, never selects or weights. The margin covers the borrowed data (17 ids change shares, 41 free float between the dates). |
| A14 | Capping is iterative: a security pushed above the cap by redistribution is capped too. | No published weight exceeds the cap; the rulebook's wording only names components above 18% of the total. |
| A15 | Market data is looked up for the exact cut-off or review date. There is no fallback to the latest earlier value; a security without a row for the date is ineligible, with a warning. | The brief ties each value to one date; an older value would silently mix in stale data. Both dates are delivered for every id except `166` (A2). |

## Deliberate assumptions

Points the brief and rulebook leave open. I decided them myself instead of asking SIX: the brief asks for
reasonable, documented assumptions, none of these changes the Q3 result, and each is visible in the report and
can be changed in one place.

- **Ranking by point-in-time FFMCAP only (A4).** The rulebook's selection list needs history the data doesn't
  have.
- **Duplicate universe rows are de-duplicated (A1).** They are identical; a warning with impact `NONE` keeps
  them visible.
- **`166`, with no review-date data, is excluded (A2).** Its estimated FFMCAP (12.9M) is far below rank 22
  (15.4bn), so the status stays `COMPLETED_WITH_WARNINGS` (A13).
- **Full precision, rounding only for display; capping factors scaled so the largest is 1 (A11).**
- **The 18% cap is applied iteratively (A14).** For Q3 a single pass gives the same result.
- **Too few rankable securities fail the review (A12).** Not the case for Q3: 204 of 205 can be ranked.
- **Market data only for the exact date (A15).** No fallback to an earlier day; for Q3 only `166` lacks a row.

## Interview talking points

- **Designed for change:** new indices and quarters are configuration; new ranking rules go behind the ranking
  strategy, other rules into `review` as new steps; why there is no capping interface (removed as speculative); where the design stops is in `DESIGN.md`, *Limits of the design*.
- **The extensibility review** against the SLI, including the finding I rejected: moving eligibility behind the
  ranking strategy. Eligibility checks the FFMCAP inputs (price, shares, free float), and the weights need
  FFMCAP however securities are ranked, so the check holds for every index and stays the pipeline's first step.
- **The buffer decides the result:** a plain top 20 gives 3 joiners and 3 leavers, the buffer 1 and 1.
- **Capping:** iterative vs. single pass, with a live demo at `weight-cap: 0.15`; weights vs. capping factors;
  why the displayed weights add up to 99.999999%.
- **Validation and status:** lenient rows, status from warning impact, the estimate with its safety margin, and
  why the estimate stays despite "simplicity first".
- **Traceability:** rulebook version, recomputable FFMCAP from the input values in the report, build
  revision, every run stored; why file checksums were dropped.
- **Rulebook simplifications** (A4–A6) and the deliberate assumptions.
- **Testing:** hand-made cases per step, the real Q3 data at engine, report and API level, architecture rules as
  tests; test quality measured with mutation testing and coverage, which found real gaps.
- **Spring shape:** a plain-Java core wired in an outermost `config`, one error format, and why the ranking
  strategy is an enum, not a registry of beans.
- **AI assistance:** Claude Code with this file, a commit hook and written conventions.
- **Next steps:** the rulebook selection list once turnover and history exist (A4), issuer-level capping (A6),
  chaining reviews from the official stored run, a database behind the report store.
