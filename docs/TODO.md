# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

Setup, data profiling, rule analysis and input loading (`domain` + `ingest`) are done (see the Timeline in
[APPROACH.md](APPROACH.md)). No review logic is written yet.

## Next, in order

1. `review`: eligibility per review period (in the universe on the review date, with price at t' and shares
   and free float at t; others such as `166` are excluded with a warning, A2, D9), then FFMCAP ranking, then
   buffer selection (1–18 direct, 19–22 buffer with incumbent priority), then iterative 18% capping. Unit-test capping with the
   brief's A/B/C example (50% cap → 50 / 37.5 / 12.5).
2. `report`: final constituents, weights/capping factors, joiners, leavers, review status, warnings.
3. `api`: endpoint to run a review for an index + review period; add the request to the Postman collection.
4. End-to-end test on the real data: joiner `177`, leaver `103`; `155` and `205` capped at 18%.
5. Submission: README build/run instructions, design doc, ZIP.
