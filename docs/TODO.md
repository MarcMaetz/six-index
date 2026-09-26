# TODO

Current status and next steps. Keep it short and current: when an item is finished, remove it here and
add a row to the Timeline in [APPROACH.md](APPROACH.md) (plus any decisions or assumptions it involved).

## Status

All requirements from the brief are implemented. The review works end to end (Q3: joiner `177`, leaver `103`,
`155` and `205` capped at 18%, status `COMPLETED_WITH_WARNINGS`), every run is stored as JSON, and README,
`docs/DESIGN.md` and the exported Postman collection match the code. See the Timeline in [APPROACH.md](APPROACH.md).

An extensibility review against the brief's non-functional list found that the design is configurable across
SMI reviews but hard-codes the methodology: a sibling index from the same rulebook (SLI, 5.17: 9%/4.5% tiered
capping) would need changes in five classes. The items below add the missing seams without adding features:
the brief rates "simplicity, clarity, maintainability, and sound design over feature quantity".

## Next, in order

Each item names the brief's point it serves. Q3 results must stay identical; `ReviewEngineTest` and the
Postman collection are the regression check.

1. **Move `StatusAssessment` into `review`** (clarity). It estimates ranking values and calls
   `RankingStrategies`: that is review logic, not report formatting. `ReportBuilder` only renders its result.
2. **Input behind a port** (extensibility, auditability). `IndexCatalog` does `new InputDataLoader()`. Add an
   `InputSource` interface next to `ReportStore`, with the CSV folder as one implementation, injected by Spring.
3. **Record the methodology version** (auditability). Add the rulebook version (v3.40) to the index definition
   in `indices.yml` and to the report's parameters, so a stored report says which rules produced it.
4. **Enforce the layering with a test** (automated testing). ArchUnit test: `domain`, `review`, `report`,
   `ingest` and `store` don't depend on Spring or on `api`/`config`.
5. **Update docs**: DESIGN.md extensibility section (what adding SLI would take after these changes), APPROACH.md
   decisions for each item, README if the config format changes, Postman if any response changes.
6. Final read-through of README and DESIGN.md as a reviewer would see them on GitHub (Mermaid diagram renders,
   links work).

## Deliberately not doing (interview talking points)

Named here so the answer is ready, not built, since each adds features rather than design:

- **Full SLI or SMIM support.** SLI's top 4 at 9% come from a half-year ranking we have no data for; SMIM's
  universe is "SMI Expanded minus the SMI", i.e. one index depends on another's result.
- **Review schedule rules** (e.g. "third Friday in September", ordinary vs. extraordinary reviews). Periods stay
  a configured list of dates.
- **Chaining reviews**: taking the next review's current composition from the last official stored run, and
  marking one run as official.
- **Versioned, effective-dated configuration** and archiving input files with the report (checksums prove which
  input was used, but don't let you reproduce it if the folder is overwritten).
- **Simplifying the status estimate** (A13) — keep it, but be ready to defend it against "simplicity first":
  it is what stops a penny stock with missing data from flagging the review.
