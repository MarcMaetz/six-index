# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

Setup, data profiling, rule analysis, input loading (`domain` + `ingest`), business config in
`config/indices.yml`, the per-review input folders, the input check endpoint and the review engine (`review`) are done (see the
Timeline in [APPROACH.md](APPROACH.md)). The engine gives the expected Q3 result in a unit test; there is no
report or review endpoint yet.

## Next, in order

1. `report`: map `ReviewResult` to the report: final constituents, weights/capping factors, joiners, leavers
   (with reasons), exclusions, capping rounds, warnings, input files and checksums; review status derived from
   warning relevance (D10: a warning on a current constituent or a security ranked within the buffer end →
   `REQUIRES_ATTENTION`); rounding for display only (D13).
2. `api`: `POST /api/indices/{index}/reviews/{period}` runs a review and returns the report; add it to the
   Postman collection's Indices folder next to the input check.
3. End-to-end API test on the real data: joiner `177`, leaver `103`; `155` and `205` capped at 18%
   (already covered at engine level by `ReviewEngineTest`).
4. Submission: README build/run instructions, design doc, ZIP.
