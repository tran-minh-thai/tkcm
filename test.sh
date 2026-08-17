#!/usr/bin/env bash
# test.sh - Ground-truth correctness suite for TKUS and TKCM.
#
# Compares both top-k algorithms against exhaustive USpan enumeration
# (minUtility = 1) on datasets small enough to enumerate completely.
# Fails (exit 1) if any algorithm misses a pattern, reports an extra
# pattern, or reports a wrong utility at any tested k.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/src"
TEST_DIR="$SCRIPT_DIR/test"
BIN_DIR="$TEST_DIR/bin"

# Rebuild from clean so a stale class cannot make the suite pass on old code.
rm -rf "$BIN_DIR"
mkdir -p "$BIN_DIR"
if ! javac -d "$BIN_DIR" "$SRC_DIR"/*.java "$TEST_DIR"/VerifyTopK.java \
        "$TEST_DIR"/VerifyUSpanPruning.java; then
    echo "Test build FAILED." >&2
    exit 1
fi

FAIL=0
run() {
    java -cp "$BIN_DIR" VerifyTopK "$@" || FAIL=1
}

# IPEU counterexample: the unsound I-PEU gate pruned <(1,2)(3)>:102 at k=1.
run "$TEST_DIR/data/ipeu_counterexample.txt" 1 2 3

# CUPT self-pair counterexample: skipping single-distinct-item sequences in
# computeCUPT under-counted CUPT(a,a) and pruned <(1)(1)>:101 at k=1.
run "$TEST_DIR/data/cupt_selfpair_counterexample.txt" 1 2 3

# Filtered-reload counterexample: when the fixpoint removes items, the SUtility
# field in the file over-states the utility of the sequence that is actually
# mined. Five sequences here differ only in an item the filter removes, so after
# the reload they spell the SAME pattern and SUR summed 5x301 = 1505 for it
# instead of the true 1500 — raising minUtility past the genuine top-1 pattern,
# which then vanished. Only reachable through flag f, which is why the toy sets
# never showed it.
run "$TEST_DIR/data/retirement_fixture.txt" 1 2 3 4 5

# Small benchmark datasets — exhaustively enumerable.
run "$SCRIPT_DIR/test/data/uspan.txt" 1 3 5 10 20 100 400
run "$SCRIPT_DIR/test/data/HUSRM.txt" 1 3 8 20

# The ground truth is USpan run at minUtility=1, where its own depth test is
# vacuous. Check separately that the pruning it applies at real thresholds
# discards nothing, so the reference implementation is sound in its own right.
prune() {
    java -cp "$BIN_DIR" VerifyUSpanPruning "$@" || FAIL=1
}
prune "$SCRIPT_DIR/test/data/uspan.txt" 10 20 30 40 50 55 60
prune "$SCRIPT_DIR/test/data/HUSRM.txt" 5 10 20 30 40

if [ "$FAIL" -eq 0 ]; then
    echo ""
    echo "ALL CORRECTNESS TESTS PASSED."
else
    echo ""
    echo "CORRECTNESS TESTS FAILED." >&2
fi
exit $FAIL
