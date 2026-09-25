# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

Setup, data profiling, rule analysis, input loading (`domain` + `ingest`), business config in
`config/indices.yml`, the per-review input folders and the input check endpoint are done (see the Timeline in
[APPROACH.md](APPROACH.md)). No review logic is written yet.

## Next, in order

1. `review`: eligibility per review period (in the universe on the review date, with price at t' and shares
   and free float at t; others such as `166` are excluded with a warning, A2, D9), then FFMCAP ranking, then
   buffer selection (1–18 direct, 19–22 buffer with incumbent priority), then iterative 18% capping.
   Unit-test capping with the brief's A/B/C example (50% cap → 50 / 37.5 / 12.5). Full precision with
   invariants (D13).
2. `report`: final constituents, weights/capping factors, joiners, leavers, warnings, input directory and
   checksums; review status derived from warning relevance (D10); rounding for display only (D13).
3. `api`: `POST /api/indices/{index}/reviews/{period}` runs a review and returns the report; add it to the
   Postman collection's Indices folder next to the input check.
4. End-to-end test on the real data: joiner `177`, leaver `103`; `155` and `205` capped at 18%.
5. Submission: README build/run instructions, design doc, ZIP.
