# Agent Guide

Take-home assignment: SIX Index Reviewer for the SMI Q3 2026 review. Java 25 / Spring Boot 4.1 / Gradle.

## Read first

- `docs/ASSIGNMENT.md`: the assignment brief (source of truth for requirements). Local only, excluded in
  `.git/info/exclude`: never commit it.
- `docs/APPROACH.md`: decision log, assumptions, open questions and interview talking points.

## Keep `docs/APPROACH.md` up to date

This file is what the author will use to explain the work in the interview. Whenever a session:
- makes a design or technology decision → add a `D<n>` entry (what, why, what was rejected),
- makes an assumption about ambiguous requirements or rulebook details → add it to **Assumptions**,
- hits something that should be clarified with SIX → add it to **Open questions**,
- completes a meaningful step → add a row to **Timeline**.

Keep entries short and factual.

## Build

```bash
./gradlew build     # compile + tests
./gradlew bootRun   # http://localhost:8080, Swagger UI at /swagger-ui.html
```

## Conventions

- Review logic in `domain` / `review` stays framework-free and unit-tested.
- Index parameters and review dates come from configuration (`index-reviewer.indices.*`), not code.
- Input CSVs live in `data/`.
