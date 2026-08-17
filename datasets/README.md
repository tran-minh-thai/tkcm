# datasets/

**Empty by design.** Nothing is redistributed here. Every database has an
upstream owner and is fetched from it, so that a result measured on a file can
be reproduced from the same file everyone else uses.

Anything specific to this project is kept out of this directory: correctness
fixtures and counterexamples live in `test/data/`, because they are part of the
test suite rather than benchmark subjects, and `bash test.sh` therefore runs on a
fresh clone with no download at all.

## Where each database comes from

| database | source | how to obtain |
|---|---|---|
| `bible`, `bms`, `fifa`, `sign`, `leviathan`, `OnlineRetail_II_all` | shared collection, released per version | `bash fetch-datasets.sh` |
| `Yoochoose`, `MicroblogPCU`, `e_shop` | distributed with **HUSP-SP** (Zhang, Yang, Du, Gan, Yu — *HUSP-SP: Faster Utility Mining on Sequence Data*, ACM TKDD 2022) | obtain from the authors' distribution; they are not redistributed here |
| `kosarak10k` | derived in this project from the shared `KOSARAK` | see below |

The shared collection publishes each database as a pair — a sequence file with
internal quantities and a table of external profits — and releases them as
assets rather than tracking them in git. `fetch-datasets.sh` downloads a pinned
release, verifies it against the manifest published with that release, and
combines the pair into the single-file form this project reads.

Pin the release. The manifest on the collection's default branch always
describes the newest release, so verifying an older download against it reports
every file added since as missing.

## kosarak10k

This is a 10,000-sequence subset of `KOSARAK`, made for this project because the
full database does not finish in the top-*k* regime at the thresholds studied.
It is a derivation, not new data, so what belongs in a repository is the command
that produces it together with the checksum of the result — not the file.

Recorded alongside the fetch script so the subset can be rebuilt and checked
byte for byte.
