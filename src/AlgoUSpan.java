import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * This is an implementation of the "USPAN" algorithm for High-Utility Sequential Pattern Mining
 * as described in the conference paper : <br/><br/>
 *
 *  Yin, Junfu, Zhigang Zheng, and Longbing Cao. "USpan: an efficient algorithm for
 *  mining high utility sequential patterns." Proceedings of the 18th ACM SIGKDD
 *  international conference on Knowledge discovery and data mining. ACM, 2012.
 *
 *  This implementation tries to be as faithful as possible to the article.
 *
 * @see SequenceData
 * @see UtilityChain
 *
 * @author Philippe Fournier-Viger, 2015
 */
public class AlgoUSpan {

	/** timer for measuring execution time */
	Timer timer = new Timer();
	/** the number of patterns generated */
	int patternCount = 0;

/** output writer for pattern results **/
	OutputResult outputResult = null;

	/** buffer for storing the current pattern that is mined when performing mining
	* the idea is to always reuse the same buffer to reduce memory usage. **/
	final int BUFFERS_SIZE = 2000;
	private int[] patternBuffer = null;

	/** if true, debugging information will be shown in the console */
	final boolean DEBUG = false;

	/** if true, save result to file in a format that is easier to read by humans **/
	final boolean SAVE_RESULT_EASIER_TO_READ_FORMAT = true;

	/** the minUtility threshold **/
	int minUtility = 0;

	/** max pattern length **/
	int maxPatternLength = -1; // -1 means no length constraint

	/** the input file path **/
	String input;

	/**
	 * Default constructor
	 */
	public AlgoUSpan() {

	}

	/**
	 * Run the USPAN algorithm
	 * @param input the input file path
	 * @param output the output file path
	 * @param minUtility the minimum utility threshold
	 * @throws IOException exception if error while writing the file
	 */
	public void runAlgorithm(String input, String output, int minUtility) throws IOException {
		// reset maximum
		MemoryLogger.getInstance().reset();

		// input path
		this.input = input;

		// initialize the buffer for storing the current itemset
		patternBuffer = new int[BUFFERS_SIZE];

		// record the start time of the algorithm
		timer.start();

		// create the output writer
		outputResult = new OutputResult(output, SAVE_RESULT_EASIER_TO_READ_FORMAT);

		// save the minimum utility threshold
		this.minUtility = minUtility;

		// Load the dataset using the Dataset class
		Dataset datasetLoader = new Dataset(DEBUG, BUFFERS_SIZE);
		Dataset.DatasetResult datasetResult = null;
		try {
			datasetResult = datasetLoader.loadDataset(input, minUtility);
		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		if(datasetResult == null) {
			System.err.println("Failed to load dataset");
			return;
		}
		List<SequenceData> database = datasetResult.sequenceDatabase;

		// check the memory usage
		MemoryLogger.getInstance().checkMemory();

		// Mine the database recursively using the USpan procedure
		// This procedure is the USPan procedure optimized for the first recursion
		uspanFirstTime(patternBuffer, 0, database);

		// check the memory usage again and close the file.
		MemoryLogger.getInstance().checkMemory();
		// close output file
		outputResult.close();
		patternCount = outputResult.getPatternCount();
		// record end time
		timer.stop();
	}


	/**
	 * This is the initial call to the USpan procedure to find all High utility sequential patterns
	 * of length 1. It is optimized for finding patterns of length 1.
	 * To find larger patterns the "uspan" method is then used recursively.
	 * @param prefix  This is the buffer for storing the current prefix. Initially, it is empty.
	 * @param prefixLength The current prefix length. Initially, it is zero.
	 * @param database This is the original sequence database.
	 * @throws IOException If an error occurs while reading/writting to file.
	 */
	private void uspanFirstTime(int[] prefix, int prefixLength, List<SequenceData> database) throws IOException {

		// For the first call to USpan, we only need to check I-CONCATENATIONS
		// =======================  I-CONCATENATIONS  ===========================/
		// scan the projected database to
		// calculate the SWU of each item
		Map<Integer,Integer> mapItemSWU = new HashMap<Integer,Integer>();
		for(SequenceData seq : database) {
			// for each row (item) we will update the swu of the corresponding item
			for(int item : seq.itemNames) {
				// get its swu
				Integer currentSWU = mapItemSWU.get(item);
				// update its swu
				if(currentSWU == null) {
					mapItemSWU.put(item, seq.swu);
				}else {
					mapItemSWU.put(item, currentSWU + seq.swu);
				}
			}
		}

		// For each item
		for(Entry<Integer,Integer> entry: mapItemSWU.entrySet()) {
			Integer itemSWU = entry.getValue();
			// if the item is promising
			if(itemSWU >= minUtility) {
				// We get the item
				int item = entry.getKey();
				// We initialize two variables for calculating the total utility and remaining utility of that item
				int totalUtility = 0;
				int totalRemainingUtility = 0;

				// We also initialize a variable to remember the projected entries of sequences
				// where this item appears. This will be used for call to the recursive
				// "uspan" method later.
				List<UtilityChainEntry> matrixProjections = new ArrayList<UtilityChainEntry>();
				// For each sequence
				for(SequenceData seq : database){

					// if the item appears in that sequence
					Integer boxedRow = seq.itemToRow.get(item);
					if(boxedRow != null) {
						int row = boxedRow;

						UtilityChainEntry seqEntry = new UtilityChainEntry(seq, row);

						// find the max utility of this item in that sequence
						// and the max remaining utility
						int maxUtility = 0;
						int maxRemainingUtility = 0;
						// for each itemset in that sequence
						for(int col=0; col < seq.matrixItemUtility[row].length; col++) {
							// get the utility of the item in that itemset
							int utility = seq.matrixItemUtility[row][col];
							// if the utility is higher than 0
							if(utility >0) {
								int remaining = seq.matrixItemRemainingUtility[row][col];
								seqEntry.elements.add(new UtilityChainElement(col, utility, remaining));

								// if it is the maximum utility until now
								if(utility > maxUtility) {
									// record it as the maximum utility until now
									maxUtility = utility;

									// If it is the first occurrence of this item
									// we remember the remaining utility as the max remaining utility
									if(remaining > 0 && maxRemainingUtility == 0) {
										maxRemainingUtility = remaining;
									}
								}
							}
						}

						// update the total utility and total remaining utility for all sequences
						// until now by adding the utility and remaining utility of the current
						// sequence
						totalUtility += maxUtility;
						totalRemainingUtility += maxRemainingUtility;

						matrixProjections.add(seqEntry);
					}
				}

				// create the pattern consisting of this item
				// by appending the item to the prefix in the buffer, which is empty
				prefix[0] = item;
				// if the pattern is high utility, then output it
				if(totalUtility >= minUtility) {
					writeOut(prefix,1, totalUtility);
				}
//				//Tin checks:
//				if(itemSWU < totalUtility + totalRemainingUtility) {
//					System.out.println("swu(" + item + ") = " + itemSWU + " < SPU(" + item + ") = " + (totalUtility + totalRemainingUtility));
//					System.in.read();
//				}

				// if this item passes the depth pruning (remaining utility + totality >= minutil)
				if(totalUtility + totalRemainingUtility >= minUtility) {

					//Then, we recursively call the procedure uspan for growing this pattern and
					// try to find larger high utility sequential patterns
					if(maxPatternLength < 0 || 1 < maxPatternLength) {
						uspan(prefix, 1, matrixProjections, 1);
					}
				}
			}
		}
		// we check the memory usage.
		MemoryLogger.getInstance().checkMemory();
	}


	/**
	 * This inner class is used to store the SWU of an item and the last sequence where it was seen.
	 * It is used in the uspan() method.
	 */
	private class Pair{
		/** the total SWU of an item */
		int swu;
		/** ID of the last sequence where this item was seen — used for per-sequence dedup. */
		int lastSeenId = -1;
	}

	/**
	 * This is the general USpan procedure to find all High utility sequential patterns of length
	 * greater than 1.
	 * @param prefix  This is the buffer for storing the current prefix.
	 * @param prefixLength The current prefix length.
	 * @param projectedDatabase The projected sequence database for the current prefix.
	 * @param itemCount the number of items in the prefix
	 * @throws IOException If an error occurs while reading/writting to file.
	 */
	private void uspan(int[] prefix, int prefixLength, List<UtilityChainEntry> projectedDatabase, int itemCount) throws IOException {
		if(DEBUG){
			// Print the current prefix
			for(int i=0; i< prefixLength; i++){
				System.out.print(prefix[i] + " ");
			}
			System.out.println();
			System.out.println();
		}

		// =======================  I-CONCATENATIONS  ===========================/
		// We first try to perform I-Concatenations to grow the pattern larger.
		// We scan the projected database to calculated the SWU of each item that could
		// be concatenated to the prefix.
		// The following map will store for each item, their SWU (key: item  value: swu)
		Map<Integer,Pair> mapItemSWU = new HashMap<Integer,Pair>();
		// For each sequence in the projected database
		for(UtilityChainEntry entry : projectedDatabase) {
			SequenceData seq = entry.sequence;

			// For each occurrence of the prefix in that sequence
			for(UtilityChainElement el : entry.elements) {
				// Because we are looking for i-concatenation, we will search for items
				// occurring in the same column (itemset) as the current position
				// but from the next row
				int row = entry.anchorRow + 1;
				int column = el.itemsetID;

				// The sequence utility for updating the SWU
				// will be the remaining utility at the current position
				int localSequenceUtility = seq.matrixItemRemainingUtility[entry.anchorRow][column];

				// for each row we will update the local SWU of the corresponding item
				for(; row < seq.itemNames.length; row++) {
					// get the item for this row
					int item = seq.itemNames[row];

					// if the item appears in that column
					if(seq.matrixItemUtility[row][column] > 0) {
						// get its swu until now
						Pair currentSWU = mapItemSWU.get(item);
						// if it is the first time that we see this item
						if(currentSWU == null) {
							Pair pair = new Pair();
							pair.lastSeenId = seq.id;
							pair.swu = el.utility + localSequenceUtility;
							mapItemSWU.put(item, pair);
						}else if (currentSWU.lastSeenId != seq.id){
							// first time we see this item in this sequence
							currentSWU.lastSeenId = seq.id;
							currentSWU.swu += el.utility + localSequenceUtility;
						}else{
							// BUGFIX 2017: second occurrence in same sequence — keep higher SWU
							int tempSWU = el.utility + localSequenceUtility;
							if(tempSWU > currentSWU.swu){
								currentSWU.swu = tempSWU;
							}
						}
					}
				}
			}
		}
		//  Now that we have calculated the local SWU of each item,
		// We perform a loop on each item and for each promising item we will create
		// the i-concatenation and calculate the utility of the resulting pattern.

		// For each item
		for(Entry<Integer,Pair> entry: mapItemSWU.entrySet()) {
			// Get the Pair object that store the calculated SWU for that item
			Pair itemSWU = entry.getValue();
			// if the item is promising (SWU >= minutil)
			if(itemSWU.swu >= minUtility) {
				// get the item
				int item = entry.getKey();

				// This variable will be used to calculate this item's utility for the whole database
				int totalUtility = 0;
				// This variable will be used to calculate this item's remaining utility for the whole database
				int totalRemainingUtility = 0;

				// Initialize the projected database for this i-concatenation
				List<UtilityChainEntry> matrixProjections = new ArrayList<UtilityChainEntry>();

				// for each sequence in the projected database
				for(UtilityChainEntry seqEntry : projectedDatabase){
					SequenceData seq = seqEntry.sequence;

					// if the item appears in that sequence
					Integer boxedRow = seq.itemToRow.get(item);
					if(boxedRow != null) {
						int rowItem = boxedRow;

						int maxUtility = 0;
						int maxRemainingUtility = 0;
						UtilityChainEntry newEntry = new UtilityChainEntry(seq, rowItem);

						// for each occurrence of the prefix in this sequence
						for(UtilityChainElement el : seqEntry.elements) {
							int column = el.itemsetID;

							// check if the new item appears in the same itemset (i-concatenation)
							int newItemUtility = seq.matrixItemUtility[rowItem][column];
							if(newItemUtility > 0) {
								int newPrefixUtility = el.utility + newItemUtility;
								int remaining = seq.matrixItemRemainingUtility[rowItem][column];
								newEntry.elements.add(new UtilityChainElement(column, newPrefixUtility, remaining));

								if(newPrefixUtility > maxUtility) {
									maxUtility = newPrefixUtility;
									if(remaining > 0 && maxRemainingUtility == 0) {
										maxRemainingUtility = remaining;
									}
								}
							}
						}

						totalUtility += maxUtility;
						totalRemainingUtility += maxRemainingUtility;
						matrixProjections.add(newEntry);
					}
				}

				// create the i-concatenation by appending the item to the prefix in the buffer
				prefix[prefixLength] = item;
				// if the i-concatenation is high utility, then output it
				if(totalUtility >= minUtility) {
					writeOut(prefix,prefixLength+1, totalUtility);
				}

//				//Tin checks:
//				if(itemSWU.swu < totalUtility + totalRemainingUtility) {
//					System.out.println("swu(i-ext " + ToString(prefix, prefixLength+1) + "-1 -2) <= swu(" + item + ") = " + itemSWU.swu + " < SPU(i-ext " + ToString(prefix, prefixLength+1) + " -1 -2) = " + (totalUtility + totalRemainingUtility));
//				System.in.read();
//				}

				// if his i-concatenation passes the depth pruning (remaining utility + totality)
				if(totalUtility + totalRemainingUtility >= minUtility) {

					// Finally, we recursively call the procedure uspan for growing this pattern
					// I-extension stays in the same itemset — pass itemCount unchanged
					uspan(prefix, prefixLength+1, matrixProjections, itemCount);

				}
			}
		}

		// =======================  S-CONCATENATIONS  ===========================/
		// We will next look for for S-CONCATENATIONS.
		// We first clear the map for calculating the SWU of items to reuse it instead
		// of creating a new one
		mapItemSWU.clear();
		// Now, we will loop over sequences of the projected database to calculate the local SWU
		// of each item.
		//For each sequence in the projected database
		for(UtilityChainEntry entry : projectedDatabase) {
			SequenceData seq = entry.sequence;

			// For each occurrence of the prefix in this sequence
			for(UtilityChainElement el : entry.elements) {

				// The local sequence utility is the remaining utility at the current position
				int localSequenceUtility = seq.matrixItemRemainingUtility[entry.anchorRow][el.itemsetID];

				// For each item
				for(int row = 0; row < seq.itemNames.length; row++) {
					int item = seq.itemNames[row];

					// Look for s-concatenations starting from the next itemset (column)
					for(int column = el.itemsetID + 1;
							column < seq.matrixItemUtility[row].length; column++) {
						if(seq.matrixItemUtility[row][column] > 0) {
							Pair currentSWU = mapItemSWU.get(item);
							if(currentSWU == null) {
								Pair pair = new Pair();
								pair.lastSeenId = seq.id;
								pair.swu = el.utility + localSequenceUtility;
								mapItemSWU.put(item, pair);
							}else if (currentSWU.lastSeenId != seq.id){
								currentSWU.lastSeenId = seq.id;
								currentSWU.swu += el.utility + localSequenceUtility;
							}else{
								// BUGFIX 2017: second occurrence in same sequence — keep higher SWU
								int tempSWU = el.utility + localSequenceUtility;
								if(tempSWU > currentSWU.swu){
									currentSWU.swu = tempSWU;
								}
							}
							// found the first column after the current position — stop
							break;
						}
					}
				}
			}
		}

		// Next we will calculate the utility of each s-concatenation for promising
		// items that can be appended by s-concatenation
		for(Entry<Integer,Pair> entry: mapItemSWU.entrySet()) {
//			System.out.println(entry.getKey() + "  swu: " + entry.getValue().swu);
			// Get the item and its SWU
			Pair itemSWU = entry.getValue();
			// if the item is promising (SWU >= minutil
			if(itemSWU.swu >= minUtility) {

				// get the item
				int item = entry.getKey();

				// This variable is used to store the utility of this s-concatenation in the whole database
				int totalUtility = 0;

				// This variable is used to store the remaining utility of this
				// s-concatenation in the whole database
				int totalRemainingUtility = 0;

// Initialize the projected database for this s-concatenation
				List<UtilityChainEntry> matrixProjections = new ArrayList<UtilityChainEntry>();

				// For each sequence of the projected database
				for(UtilityChainEntry seqEntry : projectedDatabase){
					SequenceData seq = seqEntry.sequence;

					// if the item appears in that sequence
					Integer boxedRow = seq.itemToRow.get(item);
					if(boxedRow != null) {
						int rowItem = boxedRow;

						int maxUtility = 0;
						int maxRemainingUtility = 0;
						UtilityChainEntry newEntry = new UtilityChainEntry(seq, rowItem);

						// for each occurrence of the prefix in this sequence
						for(UtilityChainElement el : seqEntry.elements) {
							// Search for the item in columns after the current position (s-concatenation)
							for(int column = el.itemsetID + 1;
									column < seq.matrixItemUtility[rowItem].length; column++) {
								int newItemUtility = seq.matrixItemUtility[rowItem][column];
								if(newItemUtility > 0) {
									int newPrefixUtility = el.utility + newItemUtility;
									int remaining = seq.matrixItemRemainingUtility[rowItem][column];
									newEntry.elements.add(new UtilityChainElement(column, newPrefixUtility, remaining));
									if(newPrefixUtility > maxUtility) {
										maxUtility = newPrefixUtility;
										if(remaining > 0 && maxRemainingUtility == 0) {
											maxRemainingUtility = remaining;
										}
									}
								}
							}
						}

						totalUtility += maxUtility;
						totalRemainingUtility += maxRemainingUtility;
						matrixProjections.add(newEntry);
					}
				}

				// create ths s-concatenation by appending an itemset separator to
				// start a new itemset
				prefix[prefixLength] = -1;
				// then we append the new item
				prefix[prefixLength+1] = item;
				// if this s-concatenation is high utility and within the itemset limit, output it
				if(totalUtility >= minUtility && (maxPatternLength < 0 || itemCount+1 <= maxPatternLength)) {
					writeOut(prefix,prefixLength+2, totalUtility);
				}

//				//Tin checks:
//				if(itemSWU.swu < totalUtility + totalRemainingUtility) {
//					System.out.println("swu(s-ext " + ToString(prefix, prefixLength+2) + "-1 -2) <= swu(" + item + ") = " + itemSWU.swu + " < SPU(s-ext " + ToString(prefix, prefixLength+2) + "-1 -2) = " + (totalUtility + totalRemainingUtility));
//				System.in.read();
//				}

				// if this s-concatenation passes the depth pruning
				// (remaining utility + totality >= minutil)
				if(totalUtility + totalRemainingUtility >= minUtility) {

					// Finally, we recursively call the procedure uspan() for growing this pattern
					// S-extension opens a new itemset — recurse only if still within the limit
					if(maxPatternLength < 0 || itemCount+1 <= maxPatternLength) {
						uspan(prefix, prefixLength+2, matrixProjections, itemCount+1);
					}
				}
			}
		}
		// We check the memory usage
		MemoryLogger.getInstance().checkMemory();
	}

////Tin added for checking above:
//	public String ToString(int[] prefix, int length) {
//		StringBuilder sb = new StringBuilder();
//		for (int i=0; i < length; i++)
//			sb.append("" + prefix[i] + " ");
//		return sb.toString();
//	}

	/**
	 * Set the maximum pattern length
	 * @param maxPatternLength the maximum pattern length (number of itemsets)
	 */
	public void setMaxPatternLength(int maxPatternLength) {
		this.maxPatternLength = maxPatternLength;
	}

	/**
	 * Method to write a high utility itemset to the output file.
	 * @param the prefix to be written o the output file
	 * @param utility the utility of the prefix concatenated with the item
	 * @param prefixLength the prefix length
	 */
	private void writeOut(int[] prefix, int prefixLength, int utility) throws IOException {
		// delegate formatting and writing to OutputResult
		outputResult.writePattern(prefix, prefixLength, utility);

		// if in debugging mode, also print the pattern to the console
		if (DEBUG) {
			System.out.println(" SAVING : " + outputResult.formatPattern(prefix, prefixLength, utility));
			System.out.println();

			// check if the calculated utility is correct for debugging
			checkIfUtilityOfPatternIsCorrect(prefix, prefixLength, utility);
		}
	}

	/**
	 * This method check if the utility of a pattern has been correctly calculated for
	 * debugging purposes. It is not designed to be efficient since it is just used for
	 * debugging.
	 * @param prefix a pattern stored in a buffer
	 * @param prefixLength the pattern length
	 * @param utility the utility of the pattern
	 * @throws IOException if error while writting to file
	 */
	private void checkIfUtilityOfPatternIsCorrect(int[] prefix, int prefixLength, int utility) throws IOException {
		int calculatedUtility = 0;

		BufferedReader myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(input))));
		// we will read the database
		try {
			// prepare the object for reading the file

			String thisLine;
			// for each line (transaction) until the end of file
			while ((thisLine = myInput.readLine()) != null) {
				// if the line is  a comment, is  empty or is a kind of metadata
				if (thisLine.isEmpty() == true || thisLine.charAt(0) == '#' || thisLine.charAt(0) == '%' || thisLine.charAt(0) == '@') {
					continue;
				}

				// split the sequence according to the " " separator
				String tokens[] = thisLine.split(" ");

				int tokensLength = tokens.length -3;

				int[] sequence = new int[tokensLength];
				int[] sequenceUtility = new int[tokensLength];

				// Copy the current sequence in the sequence buffer.
				// For each token on the line except the last three tokens
				// (the -1 -2 and sequence utility).
				for(int i=0; i< tokensLength; i++) {
					String currentToken = tokens[i];

					// if empty, continue to next token
					if(currentToken.length() == 0) {
						continue;
					}

					// read the current item
					int item;
					int itemUtility;

					// if the current token is -1
					if(currentToken.equals("-1")) {
						item = -1;
						itemUtility = 0;
					}else {
						// if  the current token is an item
						//  We will extract the item from the string:
						int positionLeftBracketString = currentToken.indexOf('[');
						int positionRightBracketString = currentToken.indexOf(']');
						String itemString = currentToken.substring(0, positionLeftBracketString);
						item = Integer.parseInt(itemString);

						// We also extract the utility from the string:
						String utilityString = currentToken.substring(positionLeftBracketString+1, positionRightBracketString);
						itemUtility = Integer.parseInt(utilityString);
					}
					sequence[i] = item;
					sequenceUtility[i] = itemUtility;
				}

 				// For each position of the sequence
				int util = tryToMatch(sequence,sequenceUtility, prefix, prefixLength, 0, 0, 0);
				calculatedUtility += util;
			}
		} catch (Exception e) {
			// catches exception if error while reading the input file
			e.printStackTrace();
		}finally {
			if(myInput != null){
				// close the input file
				myInput.close();
			}
	    }

		if(calculatedUtility != utility) {
			System.out.print(" ERROR, WRONG UTILITY FOR PATTERN : ");
			for(int i=0; i<prefixLength; i++) {
				System.out.print(prefix[i]);
			}
			System.out.println(" utility is: " + utility + " but should be: " + calculatedUtility);
System.in.read();
		}
	}

	/**
	 * This is some code for verifying that the utility of a pattern is correctly calculated
	 * for debugging only. It is not efficient. But it is a mean to verify that
	 * the result is correct.
	 * @param sequence a sequence (the items and -1)
	 * @param sequenceUtility a sequence (the utility values and -1)
	 * @param prefix the current pattern stored in a buffer
	 * @param prefixLength the current pattern length
	 * @param prefixPos the position in the current pattern that we will try to match with the sequence
	 * @param seqPos the position in the sequence that we will try to match with the pattenr
	 * @param utility the calculated utility until now
	 * @return the utility of the pattern
	 */
	private int tryToMatch(int[] sequence, int[] sequenceUtility, int[] prefix,	int prefixLength,
			int prefixPos, int seqPos, int utility) {

		// Note: I do not put much comment in this method because it is just
		// used for debugging.

		List<Integer> otherUtilityValues = new ArrayList<Integer>();

		// try to match the current itemset of prefix
		int posP = prefixPos;
		int posS = seqPos;

		int previousPrefixPos = prefixPos;
		int itemsetUtility = 0;
		while(posP < prefixLength & posS < sequence.length) {
			if(prefix[posP] == -1 && sequence[posS] == -1) {
				posS++;

				// try to skip the itemset in prefix
				int otherUtility = tryToMatch(sequence, sequenceUtility, prefix, prefixLength, previousPrefixPos, posS, utility);
				otherUtilityValues.add(otherUtility);

				posP++;
				utility += itemsetUtility;
				itemsetUtility = 0;
				previousPrefixPos = posP;
			}else if(prefix[posP] == -1) {
				// move to next itemset of sequence
				while(posS < sequence.length && sequence[posS] != -1){
					posS++;
				}

				// try to skip the itemset in prefix
				int otherUtility = tryToMatch(sequence, sequenceUtility, prefix, prefixLength, previousPrefixPos, posS, utility);
				otherUtilityValues.add(otherUtility);

				utility += itemsetUtility;
				itemsetUtility = 0;
				previousPrefixPos = posP;

			}else if(sequence[posS] == -1) {
				posP = previousPrefixPos;
				itemsetUtility = 0;
				posS++;
			}else if(prefix[posP] == sequence[posS]) {
				posP++;
				itemsetUtility += sequenceUtility[posS];
				posS++;
				if(posP == prefixLength) {

					// try to skip the itemset in prefix
					// move to next itemset of sequence
					while(posS < sequence.length && sequence[posS] != -1){
						posS++;
					}
					int otherUtility = tryToMatch(sequence, sequenceUtility, prefix, prefixLength, previousPrefixPos, posS, utility);
					otherUtilityValues.add(otherUtility);


					utility += itemsetUtility;
				}
			}else if(prefix[posP] != sequence[posS]) {
				posS++;
			}
		}

		int max = 0;
		if(posP == prefixLength) {
			max = utility;
		}
		for(int utilValue : otherUtilityValues) {
			if(utilValue > utility) {
				max = utilValue;
			}
		}
		return max;
	}

	/**
	 * Print statistics about the latest execution to System.out.
	 */
	public void printStatistics() {
		System.out.println("=============  USPAN ALGORITHM v2.14 - STATS ==========" );
		System.out.println(" Total time ~ " + ANSI.BRIGHT_GREEN + timer.formatElapsed(Timer.TimeUnit.MILLISECONDS) + " / " + timer.formatElapsed(Timer.TimeUnit.SECONDS) + ANSI.RESET);
		System.out.println(" Max Memory ~ " + ANSI.BRIGHT_YELLOW + MemoryLogger.formatMemory(MemoryLogger.getInstance().getMaxMemory(), MemoryLogger.MemoryUnit.MB) + ANSI.RESET);
		System.out.println(" High-utility sequential pattern count : " + ANSI.CANDY + patternCount + ANSI.RESET);
		System.out.println("========================================================");
	}

	public void logo() {
		// Gradient: white -> cyan -> blue -> purple
		int[][] g = {{255,255,255}, {0,220,220}, {30,80,220}, {148,0,200}};
		System.out.println(ANSI.gradientLine(".____     .________._______ .______  .______  ", g));
		System.out.println(ANSI.gradientLine("|    |___ |    ___/: ____  |:      \\ :      \\ ", g));
		System.out.println(ANSI.gradientLine("|    |   ||___    \\|    :  ||   .   ||       |", g));
		System.out.println(ANSI.gradientLine("|    :   ||       /|   |___||   :   ||   |   |", g));
		System.out.println(ANSI.gradientLine("|        ||__:___/ |___|    |___|   ||___|   |", g));
		System.out.println(ANSI.gradientLine("|. _____/    :                  |___|    |___|", g));
		System.out.println(ANSI.gradientLine(" :/                                           ", g));
		System.out.println(ANSI.gradientLine(" :                                            ", g));
	}

	public static void main(String [] arg) throws IOException{
		String input = "../datasets/HUSRM.txt";
		String output = "../outputs/uspan.txt";
		int minutil = 2;

		AlgoUSpan algo = new AlgoUSpan();
		algo.logo();
		algo.runAlgorithm(input, output, minutil);
		algo.printStatistics();
	}
}