# Dataset provenance

Every file in this directory, where it came from, and whether it can be obtained
by a reader. Kept as a table rather than prose because the answer differs per
file and a wrong answer here invalidates a measurement.

## From the published HUSPM distribution

`https://github.com/DSI-Lab1/HUSPM/tree/main/datasets` — almost certainly the
distribution TKUS evaluated on: it carries TKUS's six datasets, with
`Scalability_80K` in place of what the TKUS paper calls Syn80K.

| file here | file there | note |
|---|---|---|
| `bible.txt` | `BIBLE.txt` | |
| `sign.txt` | `SIGN.txt` | |
| `kosarak10k.txt` | `Kosarak10k.txt` | |
| `Yoochoose.txt` | `Yoochoose.txt` | |
| `leviathan.txt` | `Leviathan.txt` | **replaced a generated copy** — see below |
| `scalability_80k.txt` | `Scalability_80K.txt` | the TKUS paper's Syn80K |
| `scalability_10k.txt` | `Scalability_10K.txt` | |

These are used verbatim. Earlier copies of `bible`, `sign` and `kosarak10k` in
this project differed only in whitespace — a leading space, a double space
before `SUtility`, and CRLF line endings — and were replaced after checking that
the difference changes nothing: both copies give the same `output_sha256`, the
same minUtility, the same pattern count and the same projection count, at two
thresholds each.

`leviathan.txt` is different. The earlier copy had utilities generated for this
study; the published file has its own. Item identifiers match line for line but
the values do not — the first sequence sums to 2,934 in the generated copy
against 223 in the published one. **Every measurement taken on the generated
copy is void** and is listed for re-running in `paper/status.md`.

## From the HUSP-SP distribution

Distributed with Zhang, Yang, Du, Gan and Yu, *HUSP-SP: Faster Utility Mining on
Sequence Data*, ACM TKDD 2022 (see `cite_dataset.txt`). Not present in the HUSPM
distribution above, and not redistributed by this project.

    e_shop.txt    MicroblogPCU.txt    OnlineRetail_II_all.txt

These were published for **threshold** mining. Top-*k* with small *k* implies a
much lower threshold and is a harder regime, which is why all three appear in the
infeasibility analysis rather than the comparison tables.

## From the SPMF collection

    bms.txt    fifa.txt

Standard sequence-mining benchmarks in the SPMF utility-sequence format.

## Correctness fixtures

    uspan.txt    HUSRM.txt

The worked examples from the USpan and HUSRM papers. They are inputs to the
correctness suite, never benchmark subjects, and `screen.sh` excludes them.

## Verifying

    shasum -a 256 -c datasets/MANIFEST.sha256

The manifest covers every file in this directory and is regenerated whenever one
changes.

## Which databases a command can fetch

All twelve. `bash fetch-datasets.sh` retrieves them from three upstreams and
verifies every file against `MANIFEST.sha256` afterwards.

| databases | upstream |
|---|---|
| `bible` `sign` `kosarak10k` `Yoochoose` `leviathan` `scalability_10k` `scalability_80k` | `github.com/DSI-Lab1/HUSPM/tree/main/datasets` |
| `bms` `fifa` | SPMF, `publicdatasets/husp/` — verified byte-identical to the copies measured here |
| `e_shop` `MicroblogPCU` `OnlineRetail_II_all` | preserved copies, `github.com/tran-minh-thai/huspm-datasets` release `husp-sp-v1` |

**Only the last three are hosted by us, and only because the upstream removed
them.** They were distributed with HUSP-SP and were in the HUSPM directory
above; that directory now publishes seven files and none of them are these,
measured 2026-08-16 by listing it. Without a copy, work measured on them could
not be re-run from any address. Cite HUSP-SP for them, never this repository —
see `cite_dataset.txt`.

Nothing else is re-hosted. Fetching from the owner is also the stronger position
for reproducibility: a second copy is a second source that can drift, and
`leviathan` once existed here as a generated copy whose utilities differed from
the published file, voiding every measurement taken on it.

The correctness fixtures `uspan.txt` and `HUSRM.txt` are not fetched; they ship
in `test/data/` so `bash test.sh` runs on a fresh clone.
