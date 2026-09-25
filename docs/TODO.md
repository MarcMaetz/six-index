# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

Setup, data profiling, rule analysis and input loading (`domain` + `ingest`) are done (see the Timeline in
[APPROACH.md](APPROACH.md)). No review logic is written yet.

## Next, in order

1. Restructure config and input layout (D11, D12): move index definitions to `config/indices.yml` with a
   validated `@ConfigurationProperties` record (add direct-selection rank 18, buffer end 22, ranking strategy
   `FFMCAP`); move the CSVs to `data/SMI/2026-Q3/`; have the loader take the index/period directory and derive
   the universe file name from the universe; compute SHA-256 checksums of the input files. Update `AGENT.md`
   conventions and `data/README.md` to match.
2. `review`: eligibility per review period (in the universe on the review date, with price at t' and shares
   and free float at t; others such as `166` are excluded with a warning, A2, D9), then FFMCAP ranking, then
   buffer selection (1–18 direct, 19–22 buffer with incumbent priority), then iterative 18% capping.
   Unit-test capping with the brief's A/B/C example (50% cap → 50 / 37.5 / 12.5). Full precision with
   invariants (D13).
3. `report`: final constituents, weights/capping factors, joiners, leavers, warnings, input directory and
   checksums; review status derived from warning relevance (D10); rounding for display only (D13).
4. `api`: endpoint to run a review for an index + review period; add the request to the Postman collection.
5. End-to-end test on the real data: joiner `177`, leaver `103`; `155` and `205` capped at 18%.
6. Submission: README build/run instructions, design doc, ZIP.
