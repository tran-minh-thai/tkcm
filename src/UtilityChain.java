import java.util.ArrayList;
import java.util.List;

/**
 * Utility Chain data structures for the TKUS algorithm (Section 3.1 of the paper).
 *
 * A utility chain for pattern P is the paper's compact representation of P's projected
 * database. It consists of three logical parts:
 *   - Information Table  — one {@link UtilityChainEntry} per sequence where P occurs,
 *                          recording the sequence reference (SID) and its per-sequence PEU.
 *   - Head Table         — the entry list itself (ordered; SID + PEU per entry).
 *   - Utility-List       — the {@link UtilityChainElement} list within each entry:
 *                          (ItemsetID, Utility, RestUtility) per occurrence of P.
 *
 * Reference:
 *   Zhang, C., Du, Z., Gan, W., Yu, P.S.: "TKUS: Mining Top-k High-Utility Sequential
 *   Patterns." arXiv:2011.13454 (2020).
 *
 * @see AlgoTKUS
 */
public class UtilityChain {

    /**
     * Sum of {@link UtilityChainEntry#peu} across all entries. Carries the
     * published zero clause, so it bounds the pattern's DESCENDANTS and may
     * gate recursion — but not emission.
     */
    int totalPEU     = 0;

    /**
     * Sum of {@link UtilityChainEntry#bound} across all entries: the same
     * quantity WITHOUT the zero clause, so it also bounds the pattern itself.
     * Any filter that discards a candidate before it can be emitted must use
     * this one. (Confusing the two silently drops patterns whose occurrences
     * all sit at the end of their sequence.)
     */
    int totalBound   = 0;

    /** Sum of max(utility) per entry — actual utility of pattern P. */
    int totalUtility = 0;

    /** One entry per sequence where pattern P occurs (the Head Table rows). */
    final List<UtilityChainEntry> entries = new ArrayList<>();

    /** Add an entry and accumulate its PEU contribution. */
    void addEntry(UtilityChainEntry e) {
        entries.add(e);
        totalPEU   += e.peu;
        totalBound += e.bound;
    }

    /**
     * Reset this chain for reuse from the object pool.
     * Clears the entry list and resets all accumulated totals.
     * The caller is responsible for clearing each entry's elements
     * and returning them to the entry pool before calling this.
     */
    void reset() {
        entries.clear();
        totalPEU     = 0;
        totalBound   = 0;
        totalUtility = 0;
    }
}

// ---------------------------------------------------------------------------

/**
 * One occurrence of the current pattern within a single sequence
 * (one row of the Utility-List in the paper).
 */
class UtilityChainElement {

    /** Itemset (column) index — "ItemsetID" in the paper. */
    final int itemsetID;

    /** Accumulated prefix utility up to and including this occurrence — "Utility". */
    final int utility;

    /** Remaining utility after this position — "RestUtility". */
    final int restUtility;

    UtilityChainElement(int itemsetID, int utility, int restUtility) {
        this.itemsetID   = itemsetID;
        this.utility     = utility;
        this.restUtility = restUtility;
    }
}

// ---------------------------------------------------------------------------

/**
 * One entry in a utility chain — groups all occurrences of the current pattern
 * within a single sequence (corresponds to one row in the Head Table).
 *
 * <p>{@code sequence} acts as the SID and provides item-utility lookups via the
 * underlying {@link SequenceData}.</p>
 *
 * <p>{@code anchorRow} caches the row index of the pattern's last item in this
 * sequence so I-extension scans can start at {@code anchorRow + 1} without
 * repeating a binary search on every access.</p>
 */
class UtilityChainEntry {

    /** The sequence data — acts as the sequence identifier (SID). */
    SequenceData sequence;

    /**
     * max(utility + restUtility) over all elements, with the published zero
     * clause — per-sequence PEU contribution, valid for descendants only.
     */
    int peu;

    /** The same maximum WITHOUT the zero clause: bounds this pattern too. */
    int bound;

    /**
     * Row index of the pattern's last item in {@code sequence.itemNames}.
     * Cached so that I-extension candidate scans can start at {@code anchorRow + 1}
     * without repeating {@code Arrays.binarySearch} on every entry access.
     */
    int anchorRow;

    /** All occurrences of the pattern in this sequence (the Utility-List rows). */
    final List<UtilityChainElement> elements = new ArrayList<>();

    UtilityChainEntry(SequenceData sequence, int anchorRow) {
        this.sequence  = sequence;
        this.anchorRow = anchorRow;
    }

    /**
     * Reset this entry for reuse from the object pool.
     * Clears all accumulated elements and resets the per-sequence PEU.
     */
    void reset(SequenceData sequence, int anchorRow) {
        this.sequence  = sequence;
        this.anchorRow = anchorRow;
        this.peu       = 0;
        this.bound     = 0;
        this.elements.clear();
    }

    /**
     * Append one occurrence and update the per-sequence PEU.
     *
     * <p>Follows Definition 12 of the published TKUS (Inf. Sci. 570 (2021)
     * 342--359): an occurrence with {@code restUtility == 0} contributes 0,
     * not {@code utility}. Nothing follows such an occurrence in this
     * sequence, so no extension of the pattern can occur here and the
     * sequence's contribution to any descendant's utility is exactly zero;
     * crediting {@code utility} would be a pure overestimate. Because RSU is
     * defined in terms of PEU (their Definition 14), this tightens the width
     * bound as well.</p>
     */
    void addElement(int itemsetID, int utility, int restUtility) {
        elements.add(new UtilityChainElement(itemsetID, utility, restUtility));
        int p = utility + restUtility;
        if (p > bound) bound = p;
        if (restUtility > 0 && p > peu) peu = p;
    }
}
