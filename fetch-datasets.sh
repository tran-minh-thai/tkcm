#!/usr/bin/env bash
# Fetch the benchmark databases into datasets/.
#
# Three upstreams, because the databases have three owners and none of them is
# this project. Fetching from the owner is also the stronger position for
# reproducibility: a reader gets the same bytes the original authors published,
# where a second copy is a second source that can drift. That is not
# hypothetical -- leviathan once existed here as a copy whose utilities differed
# from the published file, and every measurement taken on it is void.
#
# Every file is checked against datasets/MANIFEST.sha256 after download. If an
# upstream ever replaces a file, that check is what turns a silent change of data
# into a visible failure.
set -eu
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/datasets"
fail=0

get() {  # url  local-name
  printf '  %-24s ' "$2"
  if curl -fsSL "$1" -o "$DIR/$2"; then
    printf '%10d B\n' "$(wc -c < "$DIR/$2")"
  else
    echo "FAILED"; fail=1
  fi
}

# 1. The HUSPM distribution -- the dataset set the TKUS baseline was evaluated
#    on, already carrying utilities, in the single-file format read here.
echo "github.com/DSI-Lab1/HUSPM"
A="https://raw.githubusercontent.com/DSI-Lab1/HUSPM/main/datasets"
get "$A/BIBLE.txt"           bible.txt
get "$A/SIGN.txt"            sign.txt
get "$A/Kosarak10k.txt"      kosarak10k.txt
get "$A/Yoochoose.txt"       Yoochoose.txt
get "$A/Leviathan.txt"       leviathan.txt
get "$A/Scalability_10K.txt" scalability_10k.txt
get "$A/Scalability_80K.txt" scalability_80k.txt

# 2. SPMF -- the utility-sequence versions of two standard benchmarks. Verified
#    byte-identical to the copies this project measured on.
echo
echo "philippe-fournier-viger.com/spmf"
B="https://www.philippe-fournier-viger.com/spmf/publicdatasets/husp"
get "$B/BMS_sequence_utility.txt"  bms.txt
get "$B/FIFA_sequence_utility.txt" fifa.txt

# 3. Preserved copies. These three were distributed with HUSP-SP and were in the
#    HUSPM directory above; they are NOT there any more, so without a copy a
#    result measured on them could not be re-run from any address. Cite HUSP-SP
#    (Zhang et al., ACM TKDD 2022) -- see datasets/cite_dataset.txt.
echo
echo "preserved HUSP-SP copies (no longer published upstream)"
C="https://github.com/tran-minh-thai/huspm-datasets/releases/download/husp-sp-v1"
get "$C/e_shop.txt"                e_shop.txt
get "$C/MicroblogPCU.txt"          MicroblogPCU.txt
get "$C/OnlineRetail_II_all.txt"   OnlineRetail_II_all.txt

echo
echo "verifying against datasets/MANIFEST.sha256:"
( cd "$DIR/.." && shasum -a 256 -c datasets/MANIFEST.sha256 2>&1 | grep -v ': OK$' || true )
echo "  (only mismatches are listed; correctness fixtures live in test/data and are not fetched)"
[ "$fail" -eq 0 ] || { echo; echo "at least one download failed"; exit 1; }
