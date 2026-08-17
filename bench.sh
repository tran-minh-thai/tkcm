#!/usr/bin/env bash
# bench.sh - Self-describing benchmark pipeline (see PROTOCOL.md).
#
# Usage:
#   bash bench.sh [--datasets uspan,HUSRM,sign] [--k 10,20] [--algos load,tkus,eng-w1s1c1m1f1]
#                 [--xmx 8g] [--timeout 1800] [--out results]
#
# One fresh child JVM per (dataset, k, algo) configuration; one CSV row per
# measured repetition, written to results/results-<run_id>.csv where
# run_id = <timestamp>-<git commit>[-dirty]. Refuses to run if the
# correctness suite (test.sh) fails.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BIN_DIR="$SCRIPT_DIR/bench/bin"

# Always rebuild from source: bench/bin is a separate output tree from the
# one build.sh writes, so a stale class here would silently benchmark old code.
rm -rf "$BIN_DIR"
mkdir -p "$BIN_DIR"
if ! javac -d "$BIN_DIR" "$SCRIPT_DIR/src"/*.java "$SCRIPT_DIR/bench/BenchRunner.java"; then
    echo "Bench build FAILED." >&2
    exit 1
fi

# Gate: never benchmark a build that fails ground truth (editorial rule 2.1).
if ! bash "$SCRIPT_DIR/test.sh" > /dev/null 2>&1; then
    echo "REFUSING TO BENCHMARK: correctness suite failed. Run: bash test.sh" >&2
    exit 1
fi
echo "Correctness suite passed — starting benchmark."

cd "$SCRIPT_DIR" && java -cp "$BIN_DIR" BenchRunner "$@"
