# SIX Index Reviewer

Index Reviewer application for the SIX Index IT home assignment: executes the SMI index review for Q3 2026
and exposes it through a REST API.

## Stack

- Java 25 (Gradle toolchain, auto-provisioned via foojay)
- Spring Boot 4.1 (Web MVC, Validation, Actuator)
- OpenCSV for input parsing
- springdoc-openapi for API docs / Swagger UI
- JUnit 5 for tests

## Layout

```
src/main/java/com/example/indexreviewer/
  domain/   core model (framework-free)
  ingest/   CSV loading + data validation
  review/   review engine: ranking, selection, capping
  report/   review report (constituents, weights, joiners, leavers, status)
  api/      REST controllers
data/       input CSVs (spi_universe.csv, sec_data.csv, composition.csv)
docs/       assignment brief, design notes, assumptions
```

## Build & run

```bash
./gradlew build          # compile + tests
./gradlew bootRun        # start on http://localhost:8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI spec: http://localhost:8080/api-docs
- Health: http://localhost:8080/actuator/health

## Configuration

Index definitions and review dates live in `src/main/resources/application.properties`
under `index-reviewer.indices.*`, so further indices or review periods can be added without code changes.
