# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

The review works end to end: `POST /api/indices/SMI/reviews/2026-Q3` returns the Q3 report (joiner `177`,
leaver `103`, `155` and `205` capped at 18%, status `COMPLETED_WITH_WARNINGS`). It is covered by
`IndexControllerTest` and by test scripts in the Postman collection. See the Timeline in
[APPROACH.md](APPROACH.md).

## Next, in order

1. README with build/run instructions and how to try the API (Swagger UI, Postman), and a design doc (can
   draw on `docs/APPROACH.md`).
