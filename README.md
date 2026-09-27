# SIX Index Reviewer

Runs the SMI index review for Q3 2026 and exposes it through a REST API. Solution to the SIX Index IT home
assignment.

The review follows SIX rulebook section 5.12. Securities in the SPI universe are ranked by free float market
capitalization (FFMCAP = price at cut-off × shares × free float at the review date). Ranks 1–18 are selected
directly, and ranks 19–22 form a buffer where current constituents have priority. Weights are then capped at
18%. The report lists the new composition with weights and capping factors, joiners, leavers and a review
status, plus an audit trail showing why each security was selected or not. Every run is stored as JSON, so
each published report can be looked up later.

## Result for SMI Q3 2026

| | |
|---|---|
| Constituents | 20 |
| Joiner | `177` (rank 7) |
| Leaver | `103` (rank 35, below the buffer) |
| Buffer (ranks 19–22) | incumbents `160` and `81` keep their places over the new candidates `249` and `28` |
| Capped at 18% | `155` (raw weight 25.42%, capping factor 0.5654) and `205` (23.49%, 0.6117) |
| Review status | `COMPLETED_WITH_WARNINGS`: duplicate rows in `spi_universe.csv` (nothing lost), and id `166` excluded for missing review-date data (its estimated FFMCAP, 12.9M, is far below the 15.4bn at rank 22, so it can't affect the result) |

## Build and run

You need a JDK 17 or newer on the `PATH` to start Gradle. Gradle downloads the Java 25 toolchain the project
compiles with, so no JDK 25 install is needed.

```bash
./gradlew build      # compile and run all tests
./gradlew bootRun    # start the API on http://localhost:8080
./gradlew pitest     # mutation tests of the review logic, report in build/reports/pitest
./gradlew test jacocoTestReport   # line and branch coverage, report in build/reports/jacoco
```

Run the review:

```bash
curl -i -X POST http://localhost:8080/api/indices/SMI/reviews/2026-Q3
```

The response is `201 Created` with a summary: status, the 20 constituents with weights, joiners and leavers.
The full report with the audit trail (ranking with input values, capping rounds, warnings) is stored, and there
are two ways to reach it:

- **Over the API:** `reportUrl` in the response body (also in the `Location` header), e.g.
  `http://localhost:8080/api/indices/SMI/reviews/2026-Q3/reports/20260927T203246781715411Z`.
- **On disk:** `reports/<index>/<review period>/<run id>.json` in the working directory, e.g.
  `reports/SMI/2026-Q3/20260927T203246781715411Z.json`. The folder is gitignored and set by
  `index-reviewer.reports-dir`. Files are never overwritten; each run adds one.

| Endpoint | Purpose |
|---|---|
| `GET /api/indices` | Configured indices and review periods |
| `GET /api/indices/{index}/reviews/{period}/input` | Load and validate a review's input without running it: files read, counts, warnings |
| `POST /api/indices/{index}/reviews/{period}` | Run the review, store the full report and return a summary with `reportUrl` (201, `Location` header) |
| `GET /api/indices/{index}/reviews/{period}/reports` | List the stored runs of a review, oldest first |
| `GET /api/indices/{index}/reviews/{period}/reports/{id}` | Get a stored report exactly as it was written |

- Swagger UI: http://localhost:8080/swagger-ui.html (OpenAPI spec at `/api-docs`)
- Health and build info: `/actuator/health`, `/actuator/info` (version and Git commit)
- Postman: import [`postman/six-index-reviewer.postman_collection.json`](postman/six-index-reviewer.postman_collection.json)
  and run the *Indices* folder. Each request has test scripts that check the expected Q3 results.
- Errors are RFC 9457 problem responses: an unknown index, period or stored report returns 404, and missing or
  unusable input files return 422, as does a review where fewer securities can be ranked than the index needs.

### IntelliJ IDEA

Open the project root as a Gradle project, then use the shared run configurations **IndexReviewer (bootRun)**
and **All tests** from `.run/`. Any Gradle JVM ≥ 17 works.

## Configuration

| What | Where | Owner |
|---|---|---|
| Index definitions: rulebook version (methodology), universe, constituent count, direct-selection and buffer ranks, weight cap, ranking strategy, review periods with cut-off and review dates | [`config/indices.yml`](config/indices.yml) | Index business |
| Data folder, report folder, report display precision, API docs paths | [`application.properties`](src/main/resources/application.properties) | Engineering |
| Input CSVs | `data/<index>/<review period>/`, e.g. [`data/SMI/2026-Q3/`](data/SMI/2026-Q3) | Operations |

A copy of `config/indices.yml` is packaged in the jar. A `config/indices.yml` in the working directory
overrides it, so it can be changed without a rebuild. Inconsistent values stop the app at startup with a clear
message.

**Adding a quarter:** add a review period to `config/indices.yml` and put its three CSVs in
`data/SMI/<period>/`. **Adding an index** that follows the SMI's kind of rules (buffer selection, one cap for all):
add an index block and a `data/<index>/<period>/` folder. **Adding a ranking rule** computed from price, shares and
free float: implement `RankingStrategy`, register it, and select it by name in the YAML. Other rules, such as the
SLI's tiered capping or the rulebook's full selection list, need code; what each takes, and what the design
deliberately doesn't cover, is in [docs/DESIGN.md](docs/DESIGN.md#configuration-and-extensibility).

## Documentation

- [docs/DESIGN.md](docs/DESIGN.md): architecture, review pipeline, data quality, extensibility, testing, limits.
- [docs/APPROACH.md](docs/APPROACH.md): why it is built this way, by topic; the input data findings, the
  rulebook rules applied, and the assumptions (A1–A15).
- [data/README.md](data/README.md): input file formats.

## Layout

```
config/indices.yml   index definitions and review periods (business configuration)
data/SMI/2026-Q3/    input CSVs of the Q3 2026 SMI review
reports/             stored review runs, created at runtime (git-ignored)
postman/             Postman collection with test scripts
docs/                design doc, approach
src/main/java/com/example/indexreviewer/
  domain/            input model and index definitions (framework-free)
  ingest/            input source: CSV loading and row validation
  review/            eligibility, ranking, buffer selection, weight capping, review status (framework-free)
  report/            review report, rounded for display (framework-free)
  store/             stored review reports, one JSON file per run
  catalog/           lookup of configured indices and review periods
  config/            Spring wiring and configuration binding
  service/           use cases: check input, run and store a review, read reports
  api/               REST controllers and error handling
```

## Use of AI assistance

The brief allows AI tools. This project was built with Claude Code. `AGENT.md` holds the instructions it
worked under, and `docs/APPROACH.md` records every decision as it was made, so each choice can be explained.
