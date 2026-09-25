# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

The review works end to end (Q3: joiner `177`, leaver `103`, `155` and `205` capped at 18%, status
`COMPLETED_WITH_WARNINGS`), with README, `docs/DESIGN.md` and the exported Postman collection. A second design
review (D20–D22) added the changes below. See the Timeline in [APPROACH.md](APPROACH.md).

## Next, in order

1. Structured status reasons (D22): record with `securityId`, `relevance` enum, `warning`, `explanation`;
   status derived from the enum; A12's warning maps to `INDEX_INCOMPLETE`. Update `StatusAssessmentTest` to
   assert on the enum.
2. Report storage (D21): `ReportStore` interface with a file implementation under `reports/`; `POST` returns
   201 + `Location`; `GET .../reports` and `GET .../reports/{id}`. Tests with a temp directory.
3. Postman: update the run-review request (201), add list/get report requests, re-export to `postman/`.
4. Docs: `DESIGN.md` (capping reading, weights vs factors, status margin and reasons, storage, API table) and
   `README.md` (endpoints, 201). Mention the 15% capping test in the DESIGN testing table. (The status margin
   is already documented.)
