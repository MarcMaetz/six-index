# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
update the matching topic in [APPROACH.md](APPROACH.md) (plus any assumptions it involved).

## Status

All requirements from the brief are implemented. The review works end to end (Q3: joiner `177`, leaver `103`,
`155` and `205` capped at 18%, status `COMPLETED_WITH_WARNINGS`), every run is stored as JSON, and README,
`docs/DESIGN.md` and the exported Postman collection match the code. See *How the work went* in [APPROACH.md](APPROACH.md).

The extensibility review against the brief's non-functional list is done: use cases in a service,
capping and input behind interfaces, status logic in `review`, rulebook version in every report, layering
enforced by ArchUnit. What is deliberately not built is in `docs/DESIGN.md` under *Limits of the design*.

## Next, in order

Nothing open. Prepare for the interview with the talking points in [APPROACH.md](APPROACH.md#interview-talking-points).
