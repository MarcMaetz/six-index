# Input data

CSV files provided with the assignment (location configurable via `index-reviewer.data-dir`):

| File               | Columns                                 | Content                                  |
|--------------------|-----------------------------------------|------------------------------------------|
| `spi_universe.csv` | `date;id`                               | SPI universe as of the review date       |
| `sec_data.csv`     | `id;date;price;free_float;shares`       | Security data at cut-off and review date |
| `composition.csv`  | `id`                                    | Current SMI constituents                 |

Format: `;`-separated, UTF-8 with BOM, CRLF line endings. Data-quality findings are recorded in
[`docs/APPROACH.md`](../docs/APPROACH.md#input-data-findings).
