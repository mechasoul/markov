package my.cute.markov;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;



public class InputHandler {
	
	MarkovDatabase db;
	Pattern thingsToRemove;
	Pattern removeDelimiter1;
	Pattern removeDelimiter2;
	
	public InputHandler(MarkovDatabase m) {
		db = m;
		
		thingsToRemove = Pattern.compile("(<\\[START\\]>)|(<\\[END\\]>)");
		removeDelimiter1 = Pattern.compile("(\\s+)(\\|+)|(\\|+)(\\s+)");
		removeDelimiter2 = Pattern.compile("\\|\\|+");
	}

	//todo: include regex for things to clean out  of input (punctuation especially)
	
	//initial parsing of string to add
	//takes a string and outputs a list of words, including a specific start-of-line token
	public List<String> parseLine(String in) {
		in = " " + in + " ";//pad to make regex easier b/c im bad (gets removed later anyway)
		String processing, finished;
		String[] out;
		//in order: remove tokens, remove end/start of word | characters (thats our delimiter so we need to remove it), and replace any occurrences of ||+ with |
		processing = removeDelimiter2.matcher(removeDelimiter1.matcher(thingsToRemove.matcher(in).replaceAll("")).replaceAll(" ")).replaceAll("\\|");
		finished = "<[START]> " + processing;//insert start token
		out = finished.split("\\s+");
		return Arrays.asList(out);
	}
	
	public void processLine(String in) {
		List<String> parsed = parseLine(in);
		db.process(parsed);
	}
}
