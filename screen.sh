#!/usr/bin/env bash
# screen.sh — decide whether a candidate dataset is worth adding, and which
# contribution it can evidence, without running the slow configuration.
#
#   bash screen.sh datasets/foo.txt            # k=100, 10-minute stage-2 limit
#   bash screen.sh datasets/foo.txt 500        # a different k
#   bash screen.sh datasets/foo.txt 500 1800   # ...and a different limit
#   bash screen.sh --all 100                   # every dataset in datasets/
#
# The screen exists because the paper's weakest point is dataset coverage for
# the representation contribution: merging is measured to pay on ONE dataset.
# Finding a second cannot be done by spending machine time on the datasets we
# already have — it needs new data, and guessing which data is expensive. The
# redundancy factor makes it cheap: one fast run answers it.
#
# Screening is NOT measurement. Nothing it prints may enter a paper table: it
# runs one repetition, without warm-up, and on whatever machine is to hand.
# Its output is a DECISION about whether to spend official machine time.

set -u
cd "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" || exit 1

BIN=screen/bin
# The heap must be stated, not inherited. Screening MicroblogPCU and
# OnlineRetail_II_all ran at the JVM default of a quarter of RAM -- 8 GB on the
# scouting machine -- while the official runs get 48 GB, so a non-completion
# could have been garbage collection rather than compute and the negative arm
# of the probe was weaker than it looked. Override with XMX=24g screen.sh ...
XMX="${XMX:-16g}"

mkdir -p "$BIN"
if ! javac -d "$BIN" src/*.java screen/ScreenDataset.java 2>&1; then
  echo "build failed" >&2; exit 1
fi

if [ "${1:-}" = "--all" ]; then
  K="${2:-100}"; LIMIT="${3:-600}"
  for f in datasets/*.txt; do
    # uspan/HUSRM are correctness fixtures, not benchmark subjects;
    # cite_dataset.txt is the provenance note that lives beside the data.
    case "$(basename "$f")" in
      uspan.txt|HUSRM.txt|cite_dataset.txt|MANIFEST*) continue ;;
    esac
    java -Xmx"$XMX" -cp "$BIN" ScreenDataset "$f" "$K" "$LIMIT"
    echo
  done
  exit 0
fi

java -Xmx"$XMX" -cp "$BIN" ScreenDataset "${1:?usage: screen.sh <dataset> [k] [timeoutSeconds]}" \
     "${2:-100}" "${3:-600}"
