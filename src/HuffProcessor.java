import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out)
	{
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}
	
	/**
	 * Finds the frequency of each character in a given bitstream
	 * @param in Buffered bit stream of the file to be compressed
	 * @return Integer array of frequencies of every character
	 */
	private int[] readForCounts(BitInputStream in) 
	{
		int[] arr = new int[ALPH_SIZE + 1];
		while(true) 
		{
			int val = in.readBits(BITS_PER_WORD);
			if(val == -1) 
			{
				break;
			}
			arr[val]++;
		}
		arr[PSEUDO_EOF] = 1;
		return arr;
	}
	
	/**
	 * Creates a Huffman tree given frequencies of characters
	 * @param arr The frequencies of the characters for the Huffman tree
	 * @return A Huffman tree based on arr
	 */
	private HuffNode makeTreeFromCounts(int[] arr) 
	{
		PriorityQueue<HuffNode> pq = new PriorityQueue<HuffNode>();
		
		for(int i = 0; i < arr.length; i++) 
		{
			if(arr[i] > 0) 
			{
				pq.add(new HuffNode(i,arr[i],null,null));
			}
		}
		
		while(pq.size() > 1) 
		{
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1,left.myWeight + right.myWeight, left,right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}
	
	/**
	 * Creates the codings from a given Huffman tree
	 * @param root the tree the codings are based off of
	 * @return the 0 and 1 represenation of every root in the Huffman tree
	 */
	private String[] makeCodingsFromTree(HuffNode root) 
	{
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}
	
	/**
	 * Helper method for makeCodingsFromTree
	 * @param root the Huffman tree being traversed
	 * @param curr the current encoding
	 * @param encodings the array of encodings to be updated
	 */
	private void codingHelper(HuffNode root, String curr, String[] encodings) 
	{
		if(root == null) 
		{
			return;
		}
		if(root.myRight == null && root.myLeft == null) 
		{
			encodings[root.myValue] = curr;
			return;
		}
		
		codingHelper(root.myLeft, curr + "0", encodings);
		codingHelper(root.myRight, curr + "1", encodings);
		
	}
	
	/**
	 * Writes out the Huffman tree
	 * @param root the Huffman tree to be written out
	 * @param out the output stream to where the tree is written to
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) 
	{
		if(root == null) 
		{
			return;
		}
		if(!(root.myRight == null && root.myLeft == null))
		{
			out.writeBits(1, 0);
			writeHeader(root.myLeft,out);
			writeHeader(root.myRight,out);
		}
		else 
		{
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}
	
	/**
	 * Writes out the compressed version of the input stream
	 * @param codings The Huffman codings that the characters are changed to
	 * @param in The input stream that contains the thing to be compressed
	 * @param out the output stream to which the compressed version is written to
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) 
	{
		while(true) 
		{
			int val = in.readBits(BITS_PER_WORD);
			if(val == -1) 
			{
				break;
			}
			String code = codings[val];
			out.writeBits(code.length(),Integer.parseInt(code,2));
		}
		String code = codings[PSEUDO_EOF];
		out.writeBits(code.length(), Integer.parseInt(code,2));
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int bits = in.readBits(BITS_PER_INT);
		if(bits != HUFF_TREE) 
		{
			throw new HuffException("illegal header starts with "+bits);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}
	
	/**
	 * Reads in the tree for a given input stream
	 * @param in the input stream of an encoded Huffman tree
	 * @return the Huffnode associated with the encodings in the input stream
	 */
	private HuffNode readTreeHeader(BitInputStream in) 
	{
		int bit = in.readBits(1);
		if(bit == -1) 
		{
			throw new HuffException("bad input, no PSEUDO_EOF");
		}
		if(bit == 0) 
		{
			HuffNode left = readTreeHeader(in);	
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else 
		{
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}
	}
	
	/**
	 * Reads the compressed bits in the input stream
	 * @param root The Huffman tree the encodings of the characters are based on
	 * @param in the input stream that is to be read/decompressed
	 * @param out the output stream to which the results are written to
	 */
	private void readCompressedBits(HuffNode root,BitInputStream in,BitOutputStream out) 
	{
		HuffNode current = root;
		while(true) 
		{
			int bits = in.readBits(1);
			if(bits == -1) 
			{
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else 
			{
				if(bits == 0) 
				{
					current = current.myLeft;
				}
				else 
				{
					current = current.myRight;
				}
				
				if(current.myRight == null && current.myLeft == null) 
				{
					if(current.myValue == PSEUDO_EOF) 
					{
						break;
					}
					else 
					{
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
}