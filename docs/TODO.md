# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

Setup, data profiling, rule analysis, input loading (`domain` + `ingest`), business config in
`config/indices.yml`, the per-review input folders, the input check endpoint, the review engine (`review`) and the report
(`report`) are done (see the Timeline in [APPROACH.md](APPROACH.md)). The Q3 report is checked in a unit test;
there is no review endpoint yet.

## Next, in order

1. `api`: `POST /api/indices/{index}/reviews/{period}` runs a review and returns the `ReviewReport`. Wire
   `ReviewEngine` and `ReportBuilder` (with a `Clock`) as beans; bind `ReportFormat` from
   `index-reviewer.report.*` in `application.properties` (weights 6, capping factors 10, FFMCAP 2 decimals,
   D13). Add the request to the Postman collection's Indices folder next to the input check.
2. End-to-end API test on the real data: joiner `177`, leaver `103`; `155` and `205` capped at 18%;
   status `COMPLETED_WITH_WARNINGS` (already covered at engine and report level by `ReviewEngineTest` and
   `ReportBuilderTest`).
3. Submission: README build/run instructions, design doc, ZIP.
