import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This class is responsible for loading and preprocessing the dataset.
 * It performs two database scans:
 * 1. First scan: Calculate SWU (Sequential Weighted Utility) of each item
 * 2. Second scan: Create QMatrix for each sequence and filter unpromising items
 *
 * @author Trinh D.D. Nguyen, 2026
 * @see QMatrix
 */
public class Dataset {

	/** if true, debugging information will be shown in the console */
	private boolean DEBUG = false;

	/** buffer size for reading sequences */
	private int BUFFERS_SIZE = 2000;

	/**
	 * Constructor
	 * @param debug whether to print debug information
	 * @param bufferSize the size of buffers for processing
	 */
	public Dataset(boolean debug, int bufferSize) {
		this.DEBUG = debug;
		this.BUFFERS_SIZE = bufferSize;
	}

	/**
	 * Load and preprocess the dataset from a file
	 * @param input the input file path
	 * @param minUtility the minimum utility threshold
	 * @return a DatasetResult containing the QMatrix database and item SWU map
	 * @throws Exception if error while reading the file
	 */
	public DatasetResult loadDataset(String input, int minUtility) throws Exception {
		return loadDataset(input, minUtility, null);
	}

	/**
	 * Load the dataset, optionally keeping only an explicit set of items.
	 *
	 * @param allowedItems when non-null, an item is kept only if it belongs to
	 *        this set; the SWU test is then bypassed. Used for the fixpoint
	 *        item filter, whose surviving set is stronger than any single SWU
	 *        threshold test. Items dropped here have their utility subtracted
	 *        from the sequence utility, exactly as unpromising items are.
	 */
	public DatasetResult loadDataset(String input, int minUtility,
	                                 java.util.Set<Integer> allowedItems) throws Exception {
		// create a map to store the SWU of each item
		// key: item  value: the swu of the item
		final Map<Integer, Integer> mapItemToSWU = new HashMap<Integer, Integer>();

		// List for tracking items in current sequence to avoid duplicates
		List<Integer> consideredItems = new ArrayList<Integer>();

		// ==========  FIRST DATABASE SCAN TO IDENTIFY PROMISING ITEMS =========
		// We scan the database a first time to calculate the SWU of each item.
		int sequenceCount = 0;
		// Collect raw sequence utilities for TKUS SUR strategy
		List<Integer> sequenceUtilities = new ArrayList<Integer>();
		BufferedReader myInput = null;
		String thisLine;

		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));

			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a kind of metadata, skip it
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}

				// Tokenise on RUNS of whitespace, after trimming.
				//
				// Splitting on a single space made the token list depend on how
				// the file happened to be spaced. Every published dataset here
				// writes two spaces before SUtility, which produced an empty
				// token, which shifted the positional arithmetic below and gave
				// every sequence one extra EMPTY itemset — inert for mining, but
				// it made the loader disagree with the file's own separator
				// count on five of the nine datasets, and produced three
				// different values for "average sequence length". Leading spaces
				// (five files have one) had the same character. Tokenising on
				// whitespace removes the whole class.
				String tokens[] = thisLine.trim().split("\\s+");

				// get the sequence utility (the last token on the line)
				String sequenceUtilityString = tokens[tokens.length-1];
				int positionColons = sequenceUtilityString.indexOf(':');
				int sequenceUtility = Integer.parseInt(sequenceUtilityString.substring(positionColons+1));

				// SOUNDNESS (2026-08-08). The SUtility field in the file is the
				// utility of the WHOLE sequence. When allowedItems filters items
				// out, the sequence that is actually mined is smaller, and the
				// declared value over-states it. That over-statement is not
				// cosmetic: it feeds SurThreshold as the utility of the pattern
				// the sequence spells out, and TKUS's SUR argument (their Proof
				// 1) requires the ACTUAL utility of a distinct pattern -- an
				// over-estimate raises minUtility past patterns that belong in
				// the answer. Observed on test/data/retirement_fixture.txt at
				// k=1: five sequences differing only in a filtered-out item
				// collapse to one pattern, summing 5*301 = 1505 instead of the
				// true 1500, and the genuine top-1 pattern was lost.
				// It also makes SWU here u(s) rather than u_F(s), which is what
				// ItemFixpoint's argument is stated over.
				if (allowedItems != null) {
					int retained = 0;
					// Stop at the -2 terminator rather than at a fixed offset from
					// the end: the sequence ends where the file says it ends, not
					// three tokens before the last one.
					for(int i=0; i< tokens.length; i++) {
						String tk = tokens[i];
						if(tk.equals("-2")) break;
						if(tk.length() == 0 || tk.charAt(0) == '-') continue;
						int lb = tk.indexOf('[');
						if(lb < 0) continue;
						if(!allowedItems.contains(Integer.parseInt(tk.substring(0, lb)))) continue;
						retained += Integer.parseInt(tk.substring(lb+1, tk.indexOf(']', lb)));
					}
					sequenceUtility = retained;
				}

				// Then read each token from this sequence (except the last three tokens
				// up to the -2 terminator; anything after it belongs to the
				// SUtility field and is read separately above.
				for(int i=0; i< tokens.length; i++) {
					String currentToken = tokens[i];
					if(currentToken.equals("-2")) break;
					// if the current token is not -1
					if(currentToken.length() !=0 && currentToken.charAt(0) != '-') {
						// find the left bracket
						int positionLeftBracketString = currentToken.indexOf('[');
						// get the item
						String itemString = currentToken.substring(0, positionLeftBracketString);
						Integer item = Integer.parseInt(itemString);

						// Avoid counting the same item twice in the same sequence
						if (!consideredItems.contains(item)) {
							consideredItems.add(item);

							// get the current SWU of that item
							Integer swu = mapItemToSWU.get(item);

							// add the sequence utility to the swu of this item
							swu = (swu == null)?  sequenceUtility : swu + sequenceUtility;
							mapItemToSWU.put(item, swu);
						}
					}
				}

				// Clear for next sequence
				consideredItems.clear();

				// Collect sequence utility for TKUS SUR strategy
				sequenceUtilities.add(sequenceUtility);

				// increase sequence count
				sequenceCount++;
			}
		} finally {
			if(myInput != null) {
				myInput.close();
			}
		}

		// If we are in debug mode, we will show statistics
		if(DEBUG) {
			System.out.println("INITIAL ITEM COUNT " + mapItemToSWU.size());
			System.out.println("SEQUENCE COUNT = " + sequenceCount);
			System.out.println("INITIAL SWU OF ITEMS");
			for(Entry<Integer,Integer> entry : mapItemToSWU.entrySet()) {
				System.out.println("Item: " + entry.getKey() + " swu: " + entry.getValue());
			}
		}

		//================  SECOND DATABASE SCAN ===================
		// Read the database again to create the QMatrix for each sequence
		List<QMatrix> database = new ArrayList<QMatrix>(sequenceCount);

		try {
			// prepare the object for reading the file
			myInput = new BufferedReader(new InputStreamReader(new FileInputStream(new File(input))));

			// We will read each sequence in buffers.
			// The first buffer will store the items of a sequence and the -1 between them
			int[] itemBuffer = new int[BUFFERS_SIZE];
			// The second buffer will store the utility of items in a sequence and the -1 between them
			int[] utilityBuffer = new int[BUFFERS_SIZE];
			// The following variable will contain the length of the data stored in the two previous buffers
			int itemBufferLength;
			// Buffer for storing the items from a sequence without the -1
			int[] itemsSequenceBuffer = new int[BUFFERS_SIZE];
			// The following variable will contain the length of the data stored in the previous buffer
			int itemsLength;

			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is a comment, is empty or is a kind of metadata, skip it
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}

				// We reset the buffer lengths because we are reading a new sequence
				itemBufferLength = 0;
				itemsLength = 0;

				// Same tokenisation as the first pass — see the note there.
				String tokens[] = thisLine.trim().split("\\s+");

				// get the sequence utility (the last token on the line)
				String sequenceUtilityString = tokens[tokens.length-1];
				int positionColons = sequenceUtilityString.indexOf(':');
				int sequenceUtility = Integer.parseInt(sequenceUtilityString.substring(positionColons+1));

				// This variable will count the number of itemsets
				int nbItemsets = 1;
				// This variable remembers if an itemset contains at least a promising item
				boolean currentItemsetHasAPromisingItem = false;

				// Copy the current sequence in the sequence buffer.
				// For each token on the line except the last three tokens
				// Up to the LAST ITEM, not to the -2 terminator.
				//
				// A "-1" is a separator that closes the itemset before it. The
				// one that sits immediately before -2 closes the final itemset
				// and must not open another, or every sequence gains a trailing
				// empty itemset. Bounding the loop by the last item token drops
				// it, and drops any run of separators or stray tokens after it,
				// without assuming how many tokens trail the sequence — which is
				// what the old fixed offset assumed and what the double space
				// before SUtility in every published file here broke.
				int lastItem = -1;
				for(int i=0; i< tokens.length; i++) {
					if(tokens[i].indexOf('[') > 0) lastItem = i;
					else if(tokens[i].equals("-2")) break;
				}
				for(int i=0; i<= lastItem; i++) {
					String currentToken = tokens[i];

					// if empty, continue to next token
					if(currentToken.length() == 0) {
						continue;
					}

					// if the current token is -1
					if(currentToken.equals("-1")) {
						// It means that it is the end of an itemset.
						// So we check if there was a promising item in that itemset
						if(currentItemsetHasAPromisingItem) {
							// If yes, then we keep the -1, because that itemset will not be empty

							// Grow buffers if this sequence is longer than the current capacity
							if(itemBufferLength >= itemBuffer.length) {
								itemBuffer = Arrays.copyOf(itemBuffer, itemBuffer.length * 2);
								utilityBuffer = Arrays.copyOf(utilityBuffer, utilityBuffer.length * 2);
							}
							// We store the -1 in the respective buffers
							itemBuffer[itemBufferLength] = -1;
							utilityBuffer[itemBufferLength] = -1;
							// We increase the length of the data stored in the buffers
							itemBufferLength++;

							// we update the number of itemsets in that sequence that are not empty
							nbItemsets++;
							// we reset the following variable for the next itemset
							currentItemsetHasAPromisingItem = false;
						}
					} else {
						// if the current token is an item
						// We will extract the item from the string:
						int positionLeftBracketString = currentToken.indexOf('[');
						int positionRightBracketString = currentToken.indexOf(']');
						String itemString = currentToken.substring(0, positionLeftBracketString);
						Integer item = Integer.parseInt(itemString);

						// We also extract the utility from the string:
						String utilityString = currentToken.substring(positionLeftBracketString+1, positionRightBracketString);
						Integer itemUtility = Integer.parseInt(utilityString);

						// if the item is promising (its SWU >= minUtility), then keep it
						if(allowedItems != null ? allowedItems.contains(item)
						                        : mapItemToSWU.get(item) >= minUtility) {
							// We remember that this itemset contains a promising item
							currentItemsetHasAPromisingItem = true;

							// Multiset normalization: some datasets (e.g. OnlineRetail_II)
							// list the same item several times within one itemset. The
							// Q-matrix has one cell per (item, itemset), so duplicate
							// occurrences are merged by summing their utilities (the
							// itemset total is unchanged). Duplicates are adjacent
							// because items are sorted within each itemset.
							if(itemBufferLength > 0 && itemBuffer[itemBufferLength - 1] == item) {
								utilityBuffer[itemBufferLength - 1] += itemUtility;
								continue;
							}

							// Grow buffers if this sequence is longer than the current capacity
							if(itemBufferLength >= itemBuffer.length) {
								itemBuffer = Arrays.copyOf(itemBuffer, itemBuffer.length * 2);
								utilityBuffer = Arrays.copyOf(utilityBuffer, utilityBuffer.length * 2);
							}
							if(itemsLength >= itemsSequenceBuffer.length) {
								itemsSequenceBuffer = Arrays.copyOf(itemsSequenceBuffer, itemsSequenceBuffer.length * 2);
							}
							// We store the item and its utility in the buffers
							itemBuffer[itemBufferLength] = item;
							utilityBuffer[itemBufferLength] = itemUtility;
							itemBufferLength++;

							// We also put this item in the buffer for all items of this sequence
							itemsSequenceBuffer[itemsLength++] = item;
						} else {
							// if the item is not promising, subtract its utility
							// from the sequence utility
							sequenceUtility -= itemUtility;
						}
					}
				}

				// If the sequence utility is now zero, the sequence is empty after
				// removing unpromising items, so we don't keep it
				if(sequenceUtility == 0) {
					continue;
				}

				// If we are in debug mode
				if(DEBUG) {
					System.out.println("SEQUENCE BEFORE REMOVING UNPROMISING ITEMS:\n" + thisLine);
					System.out.print("SEQUENCE AFTER REMOVING UNPROMISING ITEMS:\n ");
					for(int i=0; i< itemBufferLength; i++) {
						System.out.print(itemBuffer[i] + "[" + utilityBuffer[i] + "] ");
					}
					System.out.println("\nNEW SEQUENCE UTILITY " + sequenceUtility);
				}

				// Sort the buffer for all items from the current sequence in alphabetical order
				Arrays.sort(itemsSequenceBuffer, 0, itemsLength);

				// Remove duplicates from the sorted items
				int newItemsPos = 0;
				int lastItemSeen = -999;
				for(int i=0; i< itemsLength; i++) {
					int item = itemsSequenceBuffer[i];
					if(item != lastItemSeen) {
						itemsSequenceBuffer[newItemsPos++] = item;
						lastItemSeen = item;
					}
				}

				// If we are in debugging mode
				if(DEBUG) {
					System.out.print("LIST OF PROMISING ITEMS IN THAT SEQUENCE:\n ");
					for(int i=0; i< newItemsPos; i++) {
						System.out.print(itemsSequenceBuffer[i] + " ");
					}
					System.out.println();
				}

				// Count the number of items in that sequence
				int nbItems = newItemsPos;

				// Create the QMatrix for that sequence
				QMatrix matrix = new QMatrix(nbItems, nbItemsets, itemsSequenceBuffer, newItemsPos, sequenceUtility);
				// Add the QMatrix to the database (used by AlgoUSpan)
				database.add(matrix);

				// Now fill the matrix column by column
				int posBuffer = 0;
				for(int itemset=0; itemset < nbItemsets; itemset++) {
					int posNames = 0;
					while(posBuffer < itemBufferLength) {
						int item = itemBuffer[posBuffer];
						if(item == -1) {
							posBuffer++;
							break;
						}
						else if(item == matrix.itemNames[posNames]) {
							int utility = utilityBuffer[posBuffer];
							sequenceUtility -= utility;
							matrix.registerItem(posNames, itemset, utility, sequenceUtility);
							posNames++;
							posBuffer++;
						} else if(item > matrix.itemNames[posNames]) {
							// if the next item in the sequence is larger than the current row in the matrix
							// it means that the item do not appear in that itemset, so we put a utility of 0
							// for that item and move to the next row in the matrix.
							matrix.registerItem(posNames, itemset, 0, sequenceUtility);
							posNames++;
						} else {
							// Otherwise, we put a utility of 0 for the current row in the matrix and move
							// to the next item in the sequence
							matrix.registerItem(posNames, itemset, 0, sequenceUtility);
							posBuffer++;
						}
					}
				}
			}
		} finally {
			if(myInput != null) {
				myInput.close();
			}
		}

		// Precompute auxiliary arrays and build SequenceData views.
		// computeNextPos() is required by both TKCM and TKUS.
		// computeSplitRemaining() is required by TKCM's split-utility bounds;
		// TKUS never reads those arrays so the cost is negligible.
		List<SequenceData> sequenceDatabase = new ArrayList<>(database.size());
		for (int i = 0; i < database.size(); i++) {
			QMatrix qm = database.get(i);
			qm.computeSplitRemaining();
			qm.computeNextPos();
			sequenceDatabase.add(new SequenceData(i, qm));
		}

		// Return the results
		return new DatasetResult(sequenceDatabase, mapItemToSWU, sequenceCount, sequenceUtilities);
	}

	/**
	 * Inner class to hold the results of dataset loading
	 */
	public static class DatasetResult {
		/** Algorithm-facing view of the database (shared arrays, precomputed). */
		public final List<SequenceData> sequenceDatabase;
		public final Map<Integer, Integer> mapItemToSWU;
		public final int sequenceCount;
		/** Raw utility of every q-sequence before item filtering. Used by TKUS SUR strategy. */
		public final List<Integer> sequenceUtilities;

		public DatasetResult(List<SequenceData> sequenceDatabase,
		                     Map<Integer, Integer> mapItemToSWU, int sequenceCount, List<Integer> sequenceUtilities) {
			this.sequenceDatabase = sequenceDatabase;
			this.mapItemToSWU = mapItemToSWU;
			this.sequenceCount = sequenceCount;
			this.sequenceUtilities = sequenceUtilities;
		}
	}
}
