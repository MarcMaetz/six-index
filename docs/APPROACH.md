# Approach & Decision Log

Running record of how the Index Reviewer was built and why, kept as the basis for the follow-up
technical interview. Newest entries go at the bottom of each section. Keep entries short: what we
decided, why, and what we rejected.

## Timeline

| Date       | Step                                                                 |
|------------|----------------------------------------------------------------------|
| 2026-09-25 | Scaffolded Spring Boot project |
| 2026-09-25 | IntelliJ run configs (`.run/`); Postman collection "SIX Index Reviewer" created via MCP |

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

## Assumptions

Where the brief or rulebook is ambiguous, record the assumption here (and reference it in code).

| #  | Assumption | Rationale |
|----|------------|-----------|
|    |            |           |

## Open questions

Candidates to send to lucas.damalix@six-group.com / sorin.ivascu@six-group.com.

-

## Interview talking points

- How the design accommodates new indices, dates and rules (D3, D4).
- Traceability/auditability: how a reviewer can see why a security joined, left or was capped.
- Testing strategy: the brief's worked example (A/B/C, 50% cap → 50 / 37.5 / 12.5) as a first test case.
- Tooling: one-command build/run (D5), API contract + Postman collection (D6).
- Use of AI assistance (allowed by the brief): Claude Code with this decision log kept alongside, so every
  choice is written down and can be explained.
- What I'd do next with more time.
