#!/usr/bin/env bash
# Establish the claims stated in counters or hashes -- the FAST ones.
#
# This machine verifies; it does not measure. Anything that would take more than
# a few minutes belongs on the machine reserved for measurement, even when the
# quantity is machine-independent, because a long run here is an experiment
# wearing a test's clothes.
#
# Cells left out are listed at the end with the reason, never dropped silently.
#
# Usage: bash probe/run-invariant.sh [xmx]
set -eu
cd "$(dirname "${BASH_SOURCE[0]}")/.."
XMX="${1:-13g}"
javac -cp src -d probe/bin probe/*.java

run() { echo; echo "--- $* ---"; java -Xmx"$XMX" -cp "src:probe/bin" "$@"; }

# Bound refinements leave the search tree unchanged.
run BoundInvariance sign      20,50   bounds-sign
run BoundInvariance bible     100,500 bounds-bible
run BoundInvariance leviathan 100,500 bounds-leviathan

# Pairwise and merged chains agree, and what merging removes.
run Representation sign       20,50      repr-sign
run Representation kosarak10k 5,10,12,16 repr-kosarak10k
run Representation bible      100        repr-bible
run Representation leviathan  100,200,500 repr-leviathan
run Representation Yoochoose  10,100,1000 repr-Yoochoose

# Top-k and threshold mining agree on the same answer.
run CrossFamily sign       20,50      xfam-sign
run CrossFamily kosarak10k 5,10,12,16 xfam-kosarak10k
run CrossFamily leviathan  100,500    xfam-leviathan
run CrossFamily Yoochoose  10,100     xfam-Yoochoose

cat <<'NOTE'

--- deliberately NOT run here, and why ---
  bible k=1000, 2000    bound combinations and the pairwise arm take hours at
                        these thresholds. The quantity is machine-independent,
                        but the run is not fast, so it belongs on the measuring
                        machine alongside the timings it will sit next to.
  bible k=500 pairwise  held 39,836 MB at that threshold on the measuring
                        machine; this one has less memory than that.
  Yoochoose k=1000 oracle
                        the oracle at a low threshold enumerates far more than
                        the engine does.
NOTE

echo
python3 analysis/evaluation_matrix.py --gaps-only || true
