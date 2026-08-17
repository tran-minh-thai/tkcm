# TKCM

Top-*k* high-utility sequential pattern mining. The repository contains one
unified engine whose optimisations are individually switchable, the two
baselines it is compared against, and the tooling to measure them.

## Build

    bash build.sh

Requires a JDK (developed against JDK 26). No external dependencies.

## Datasets

Datasets are kept in a shared repository so that several projects use byte-
identical files:

    bash fetch-datasets.sh

## Correctness

    bash test.sh

Every flag combination of the engine is checked against exhaustive enumeration
on small fixtures in `test/data/`, and against a threshold-based USpan run on
the same data. The suite exits non-zero on any disagreement.

## Running a measurement

    bash bench.sh --datasets bible,sign --k 100,500 \
                  --algos tkus,eng-w1s1c1m1f1 --xmx 8g --timeout 3600

Results are written to `results/results-<timestamp>-<commit>.csv`, one row per
repetition, with the machine, JVM, heap, dataset hash and git commit recorded in
every row so a number can always be traced to the run that produced it.

### Algorithm names

| name | meaning |
|---|---|
| `load` | dataset loading only — the shared fixed cost |
| `sur` | loading plus the initial threshold, then stop |
| `tkus` | the TKUS baseline |
| `uspan-oracle` | threshold-based USpan given the threshold top-*k* converges to |
| `eng-w<0/1>s<0/1>c<0/1>m<0/1>f<0/1>` | the engine, one digit per optimisation |

Engine flags:

| flag | off | on |
|---|---|---|
| `w` | RSU width bound | tighter candidate-specific bound |
| `s` | shared PEU gate for both extension types | separate gate for S-extensions |
| `c` | no co-occurrence filter | co-occurrence pruning table |
| `m` | pairwise utility chains | column-merged chains |
| `f` | full vocabulary | least-fixpoint vocabulary filter |

`eng-w0s0c0m0f0` is the baseline strategy set; `eng-w1s1c1m1f1` is the full
configuration.

## Screening a dataset

    bash screen.sh datasets/bible.txt 100

Reports the properties that decide whether the merged representation and the
vocabulary filter can pay on a given dataset, without running a full search.

## Probes

`probe/` holds small drivers that read the engine's counters — feasibility
checks and diagnostics. They decide what is worth measuring; they are not
themselves measurements.

## Layout

    src/      engine, baselines, data structures
    bench/    measurement harness, writes the result CSV
    test/     correctness suite and its fixtures
    screen/   dataset screening
    probe/    counter-reading diagnostics
