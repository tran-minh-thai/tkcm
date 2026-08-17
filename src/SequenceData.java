import java.util.Map;

/**
 * Lightweight, algorithm-facing view of one database sequence.
 *
 * <p>A {@code SequenceData} is built from a {@link QMatrix} after all precomputation
 * arrays ({@code nextPos}, {@code matrixIntraRemaining}, {@code matrixInterRemaining})
 * have been filled.  All array references are <em>shared</em> — no data is copied —
 * so construction costs one object allocation and eight pointer writes per sequence.</p>
 *
 * <p>The integer {@link #id} uniquely identifies the sequence within a run and is used
 * for per-sequence deduplication inside {@code CandidateInfo} (replacing the old
 * {@code QMatrix} reference comparison).  Because {@code id} is a primitive, the
 * comparison {@code ci.lastSeenId != seq.id} is a simple integer inequality, which is
 * at least as fast as a pointer equality check.</p>
 *
 * <p>Using {@code SequenceData} instead of {@link QMatrix} throughout the mining code
 * breaks the algorithms' dependency on the USpan-specific {@link QMatrix} class, making
 * them easier to adapt to alternative sequence representations in the future.</p>
 *
 * @see UtilityChainEntry
 * @see AlgoTKCM
 * @see AlgoTKUS
 */
class SequenceData {

    // ---- Identity -----------------------------------------------------------

    /**
     * Unique zero-based index assigned at conversion time.
     * Used for O(1) per-sequence deduplication in {@code CandidateInfo}:
     * {@code ci.lastSeenId != seq.id}.
     */
    final int id;

    // ---- Core arrays (all shared with the source QMatrix — zero copy) -------

    /**
     * Sorted item names — one element per row of the Q-matrix.
     * {@code itemNames[r]} is the item ID occupying row {@code r}.
     */
    final int[] itemNames;

    /**
     * {@code matrixItemUtility[r][c]} — utility of {@code itemNames[r]} in itemset {@code c};
     * 0 if the item does not appear in that itemset.
     */
    final int[][] matrixItemUtility;

    /**
     * {@code matrixItemRemainingUtility[r][c]} — combined remaining utility
     * (intra + inter) of {@code itemNames[r]} after column {@code c}.
     * Used for TRSU computation.
     */
    final int[][] matrixItemRemainingUtility;

    /**
     * {@code matrixIntraRemaining[r][c]} — intra-itemset remaining utility at
     * position {@code (r, c)}: sum of utilities of items appearing in the same
     * column {@code c} but at rows {@code > r}.
     *
     * <p>Populated by {@link QMatrix#computeSplitRemaining()}.
     * {@code null} when split remaining has not been computed (e.g., in TKUS).</p>
     */
    final int[][] matrixIntraRemaining;

    /**
     * {@code matrixInterRemaining[c]} — inter-itemset remaining utility after
     * column {@code c}: total utility of all items appearing in columns {@code > c}.
     *
     * <p>Populated by {@link QMatrix#computeSplitRemaining()}.
     * {@code null} when split remaining has not been computed (e.g., in TKUS).</p>
     */
    final int[] matrixInterRemaining;

    /**
     * O(1) item-ID → row-index lookup built in the {@link QMatrix} constructor.
     * {@code itemToRow.get(x)} returns the row of item {@code x}, or {@code null}
     * if the item does not appear in this sequence.
     */
    final Map<Integer, Integer> itemToRow;

    /**
     * {@code nextPos[r][c]} — smallest column index {@code >= c} at which
     * {@code matrixItemUtility[r][?] > 0}, or {@code matrixItemUtility[r].length}
     * (sentinel) if no such column exists.
     *
     * <p>Populated by {@link QMatrix#computeNextPos()}.  Enables O(1) S-extension
     * first-column lookup, replacing an explicit inner column scan.</p>
     */
    final int[][] nextPos;

    /**
     * Sequence-weight utility — used by SUR and the CUPT construction pass.
     * Mirrors {@link QMatrix#swu}.
     */
    final int swu;

    // ---- Constructor --------------------------------------------------------

    /**
     * Build a {@code SequenceData} from an already-precomputed {@link QMatrix}.
     *
     * @param id  unique zero-based index for this sequence
     * @param qm  the source Q-matrix (must have had {@link QMatrix#computeNextPos()}
     *            called; and {@link QMatrix#computeSplitRemaining()} if split bounds
     *            are needed)
     */
    SequenceData(int id, QMatrix qm) {
        this.id                          = id;
        this.itemNames                   = qm.itemNames;
        this.matrixItemUtility           = qm.matrixItemUtility;
        this.matrixItemRemainingUtility  = qm.matrixItemRemainingUtility;
        this.matrixIntraRemaining        = qm.matrixIntraRemaining;
        this.matrixInterRemaining        = qm.matrixInterRemaining;
        this.itemToRow                   = qm.itemToRow;
        this.nextPos                     = qm.nextPos;
        this.swu                         = qm.swu;
    }
}
