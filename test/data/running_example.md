# `running_example.txt` — the worked example for the paper

Eight sequences, five items, small enough to typeset in full. Built for this
paper rather than reused from a published one, because a borrowed example
exhibits the borrower's properties only by accident.

## What it demonstrates, measured not asserted

| property | measured | how |
|---|---|---|
| redundancy factor `p̄` | **1.683** at k=3, 1.622 at k=5, 1.556 at k=8 | `Representation running_example 3,5,8` |
| compounding over the run | **1.4×–1.5×** | same |
| bound refinements leave the tree unchanged | projections **7 = 7** at k=3, 11 = 11, 16 = 16 | same |
| top-k agrees with a threshold oracle | **identical set**, 3 patterns at θ=136, 5 at θ=115 | `CrossFamily running_example 3,5` |
| the vocabulary fixpoint needs a **second** round | item 4 survives round 1 at SWU 165 and is removed in round 2 at SWU **108** | round-by-round replay at θ=136 |

`p̄ = 1.683` puts it in the same band as `leviathan` (1.56–1.64) on real data. The
existing fixtures do not: `uspan.txt` measures **1.049**, close enough to 1 that
the mechanism barely acts, which is why it cannot carry this part of the paper.

## How the redundancy is built

Item 1 occupies **three** separate itemsets in each of the first five sequences,
and item 3 occupies three later ones. A pairwise utility chain stores one element
per (parent element, occurrence) pair, so extending the prefix by item 3 creates
up to 3 x 3 entries where the merged representation keeps one column per
occurrence. That is the redundancy the paper defines, visible at a size a reader
can check by hand.

The last three sequences share no item with the first five except item 2 and
item 4. They exist so the search has a second, cheaper region: without them every
pattern comes from one homogeneous block and the threshold behaviour is
degenerate.

## The fixpoint round that a single pass would miss

At θ = 136 the iteration is, item by item:

| round | SWU | removed |
|---|---|---|
| 1 | 1:170 · 2:129 · 3:170 · **4:165** · 5:62 | 2, 5 |
| 2 | 1:165 · 3:165 · **4:108** | **4** |
| 3 | 1:159 · 3:159 | nothing — converged |

**Item 4 is the point of the example.** Its SWU is 165 in the first round, above
the threshold, so a single pass keeps it. Once items 2 and 5 leave, the utility
of the sequences item 4 sits in is recomputed over the surviving vocabulary only,
and its SWU falls to 108 — below θ. Iterating to convergence removes it; filtering
once does not.

Both numbers are small enough to check by hand, which is the whole reason a paper
prints a worked example instead of a table.

**CORRECTED 2026-08-16.** This file previously said the example did not exhibit
this, and listed it as something to fix. That was written without measuring: a
round-by-round replay shows the second round biting on the example exactly as
first built. The claim was asserted from having designed for two properties and
not the third, which is not evidence about the third.

## What it still does not cover

Nothing in the three contributions. It is a small example, so it demonstrates the
mechanisms rather than the scale at which they matter: the memory wall that
separates the merged representation from the pairwise one appears only at sizes no
paper prints. Cite the measured tables for that, and this example for what the
mechanisms *are*.
