import java.util.HashMap;
import java.util.Map;

/**
 * This class represents a QMatrix as described in the paper describing USPan
 * @author Philippe Fournier-Viger, 2015
 * @see AlgoUSpan
 */
class QMatrix {

	/** the qmatrix for items  [item][itemset] -> utility  */
	int matrixItemUtility[][];
	/** the qmatrix for remaining utility [item][itemset] -> remaining utility*/
	int matrixItemRemainingUtility[][];

	// ---- Extended bounds (used by AlgoTKCM) ----------------------------------

	/**
	 * Intra-itemset remaining utility at cell (r, c):
	 *   sum of u(r', c, s) for all r' > r in the same column c.
	 * Used to compute the tighter I-PEU upper bound.
	 * Populated lazily by AlgoTKCM; null when not needed.
	 */
	int[][] matrixIntraRemaining;

	/**
	 * Inter-itemset remaining utility for column c:
	 *   sum of u(r'', c', s) for all c' > c and all r''.
	 * The value is identical for every row in the same column, so only
	 * a 1-D array indexed by column is stored (saves O(items) memory).
	 * Used to compute the tighter S-PEU upper bound.
	 * Populated lazily by AlgoTKCM; null when not needed.
	 */
	int[] matrixInterRemaining;
	/** the item names */
	int[] itemNames;

	/**
	 * Reverse mapping: item ID → row index in {@code itemNames} (and in the matrix).
	 * Built once in the constructor so that O(log n) {@code Arrays.binarySearch}
	 * calls throughout the mining code become O(1) HashMap lookups.
	 */
	Map<Integer, Integer> itemToRow;

	/**
	 * First-occupied-column suffix array.
	 * {@code nextPos[r][c]} = the smallest column c' >= c where
	 * {@code matrixItemUtility[r][c'] > 0}, or {@code nbCols} if no such column exists.
	 * Size: {@code [nbRows][nbCols + 1]} (index {@code nbCols} acts as a sentinel).
	 * Populated on demand by {@link #computeNextPos()}; null until called.
	 * Replaces the inner {@code for (c = col+1; ...) if (u > 0) break} scan
	 * in S-extension RSU and chain building with a single array read.
	 */
	int[][] nextPos;
	/** the swu of this sequence **/
	int swu;

	/**
	 * Constructor
	 * @param nbItem the number of item in the sequence
	 * @param nbItemset the number of itemsets in that sequence
	 */
	public QMatrix(int nbItem, int nbItemset, int[] itemNames, int itemNamesLength, int swu){
		matrixItemUtility = new int[nbItem][nbItemset];
		matrixItemRemainingUtility = new int[nbItem][nbItemset];
		this.swu = swu;

		this.itemNames = new int[itemNamesLength];
		System.arraycopy(itemNames, 0, this.itemNames, 0, itemNamesLength);

		// Build reverse mapping item → row.  Done once here so every downstream
		// binarySearch(itemNames, x) can be replaced with itemToRow.get(x).
		itemToRow = new HashMap<>(itemNamesLength * 2);
		for (int i = 0; i < itemNamesLength; i++) {
			itemToRow.put(itemNames[i], i);
		}
	}

	/**
	 * Register item in the matrix
	 * @param itemPos an item position in "itemNames"
	 * @param itemset the itemset number
	 * @param utility the utility of the item in that itemset
	 * @param remainingUtility the reamining utility of that item at that itemset
	 */
	public void registerItem(int itemPos, int itemset, int utility, int remainingUtility) {
		// we store the utility in the cell for this item/itemset
		matrixItemUtility[itemPos][itemset] = utility;
		// we store the remaining utility in the cell for this item/itemset
		matrixItemRemainingUtility[itemPos][itemset] = remainingUtility;
	}


	/**
	 * Precompute the first-occupied-column suffix array {@link #nextPos}.
	 *
	 * <p>{@code nextPos[r][c]} is the smallest column index c' >= c such that
	 * {@code matrixItemUtility[r][c'] > 0}, or {@code nbCols} when no such column
	 * exists.  The extra sentinel column at index {@code nbCols} always holds
	 * {@code nbCols}, so callers can unconditionally check
	 * {@code if (nextPos[r][col+1] < nbCols)} without bounds worries.</p>
	 *
	 * <p>Must be called once after all {@link #registerItem} calls are complete.
	 * Replaces the {@code for (c = col+1; ...) if (u > 0) break} inner loop in
	 * S-extension RSU accumulation and S-extension chain building with a single
	 * O(1) array read per call site.</p>
	 */
	public void computeNextPos() {
		int nbRows = itemNames.length;
		int nbCols = matrixItemUtility[0].length;
		nextPos = new int[nbRows][nbCols + 1];
		for (int r = 0; r < nbRows; r++) {
			nextPos[r][nbCols] = nbCols; // sentinel: no occurrence past the last column
			for (int c = nbCols - 1; c >= 0; c--) {
				nextPos[r][c] = (matrixItemUtility[r][c] > 0) ? c : nextPos[r][c + 1];
			}
		}
	}

	/**
	 * Compute and store intraRemaining and interRemaining for all cells.
	 *
	 * <p>This method must be called once after the matrix is fully populated
	 * by {@link #registerItem}. It is invoked by AlgoTKCM during dataset loading
	 * and is a no-op for other algorithms that do not set these fields.</p>
	 *
	 * <ul>
	 *   <li><b>intraRemaining[r][c]</b> = sum of u(r', c) for all r' &gt; r in column c.</li>
	 *   <li><b>interRemaining[r][c]</b> = sum of u(r'', c') for all c' &gt; c and all r''.</li>
	 * </ul>
	 */
	public void computeSplitRemaining() {
		int nbRows = itemNames.length;
		int nbCols = matrixItemUtility[0].length;

		matrixIntraRemaining = new int[nbRows][nbCols];

		// --- interRemaining: precompute column-sum suffix array (stored as 1-D) ---
		// The inter-remaining value is the same for every row in a given column,
		// so we store only one value per column instead of nbRows values.
		// columnSum[c] = sum of all u(r, c) for all r
		int[] columnSum = new int[nbCols];
		for (int c = 0; c < nbCols; c++) {
			for (int r = 0; r < nbRows; r++) {
				columnSum[c] += matrixItemUtility[r][c];
			}
		}
		// suffixColSum[c] = total utility of all items in columns c+1, c+2, ..., end
		int[] suffixColSum = new int[nbCols];
		for (int c = nbCols - 2; c >= 0; c--) {
			suffixColSum[c] = suffixColSum[c + 1] + columnSum[c + 1];
		}
		// interRemaining[c] = suffixColSum[c]  (same for every row — 1-D array)
		matrixInterRemaining = suffixColSum;

		// --- intraRemaining: for each column, suffix-sum over rows ---
		for (int c = 0; c < nbCols; c++) {
			// intraRemaining[r][c] = sum of u(r', c) for r' > r
			int running = 0;
			for (int r = nbRows - 1; r >= 0; r--) {
				matrixIntraRemaining[r][c] = running;
				running += matrixItemUtility[r][c];
			}
		}
	}

	/**
	 * Get a string representation of this matrix (for debugging purposes)
	 * @return the string representation
	 */
	public String toString() {
		StringBuffer buffer = new StringBuffer();
		buffer.append(" MATRIX \n");
		for(int i=0; i< itemNames.length; i++) {
			buffer.append( "\n  item: " + itemNames[i] + "  ");
			for(int j=0; j< matrixItemUtility[i].length; j++) {
				buffer.append("  " + matrixItemUtility[i][j] + "[" +
						+ matrixItemRemainingUtility[i][j] + "]");
			}
		}
		buffer.append("   swu: " + swu);
		buffer.append("\n");
		return buffer.toString();
	}
}
