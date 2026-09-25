# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

Setup, data profiling and rule analysis are done (see the Timeline in [APPROACH.md](APPROACH.md)).
No review logic is written yet.

## Next, in order

1. `domain` model + `ingest`: parse the three CSVs (`;`, UTF-8 BOM, CRLF), validate, and collect data-quality
   warnings (A1–A3 in `docs/APPROACH.md`) instead of failing.
2. `review`: FFMCAP ranking (price at cut-off, shares and free float at review date), then buffer selection
   (1–18 direct, 19–22 buffer with incumbent priority), then iterative 18% capping. Unit-test capping with the
   brief's A/B/C example (50% cap → 50 / 37.5 / 12.5).
3. `report`: final constituents, weights/capping factors, joiners, leavers, review status, warnings.
4. `api`: endpoint to run a review for an index + review period; add the request to the Postman collection.
5. End-to-end test on the real data: joiner `177`, leaver `103`; `155` and `205` capped at 18%.
6. Submission: README build/run instructions, design doc, ZIP.
