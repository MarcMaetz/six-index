# Agent Guide

Take-home assignment: SIX Index Reviewer for the SMI Q3 2026 review. Java 25 / Spring Boot 4.1 / Gradle.

## Read first

- `docs/ASSIGNMENT.md`: the assignment brief (source of truth for requirements). Local only, excluded in
  `.git/info/exclude`: never commit it.
- `docs/TODO.md`: current status and next steps. Start here to pick up work; keep it current.
- `docs/APPROACH.md`: decision log, assumptions and interview talking points.
- `docs/six-methodology-smi-equity-and-re-en.pdf`: SIX methodology rulebook v3.40, local only like the brief.
  SMI rules are in section 5.12; its summary is in `docs/APPROACH.md` under **Rulebook rules applied**.

## Keep `docs/APPROACH.md` up to date

This file is what the author will use to explain the work in the interview. Whenever a session:
- makes a design or technology decision → add a `D<n>` entry (what, why, what was rejected),
- makes an assumption about ambiguous requirements or rulebook details → add it to **Assumptions**,
- hits something the brief or rulebook leaves open → decide it, add it to **Assumptions**, and list it under
  **Deliberate assumptions** if it would otherwise be a question for SIX (D23),
- completes a meaningful step → add a row to **Timeline**,
- changes or refines an earlier decision → also update that earlier entry (e.g. "Refined by D16"), so no
  entry describes behaviour the code no longer has.

Keep entries short and factual. Update it **in the same change** as the work it describes, before
committing. A timeline row alone is not enough when the work involved a choice: tooling, libraries and
project setup count as decisions too. A hook in `.claude/settings.json` enforces this: a `git commit` is
blocked while `docs/APPROACH.md` is unchanged. Use `[no-approach]` in the commit message only when a change
truly needs no entry, and tell the user why.

## Build

```bash
./gradlew build     # compile + tests
./gradlew bootRun   # http://localhost:8080, Swagger UI at /swagger-ui.html
```

## Conventions

- Review logic in `domain` / `review` stays framework-free and unit-tested.
- Index parameters and review dates are business configuration in `config/indices.yml` (D11), not code.
  Technical settings stay in `application.properties`.
- Input CSVs live in `data/<index>/<review period>/`, e.g. `data/SMI/2026-Q3/` (D12).

## Postman

Collection **SIX Index Reviewer** (id `fed703bb-9ae7-4baf-a21c-f42ec10f15ba`) in "My Workspace"
(`b64038fa-2991-452c-8277-a9910531823a`), reachable via the `postman` MCP server. Requests use the
`{{baseUrl}}` collection variable (default `http://localhost:8080`). When adding or changing API endpoints,
add or update the matching request there, then re-export it to `postman/six-index-reviewer.postman_collection.json`
(reviewers only get the repo, not the workspace).
