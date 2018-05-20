package my.cute.markov;
import java.io.File;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import javafx.util.Pair;


public class OutputHandler {
	
	public Random rand;
	MarkovDatabase db;
	Pattern tokens;
	
	public OutputHandler(MarkovDatabase m) {
		rand = new Random();
		db = m;
		tokens = Pattern.compile("\\s+<\\[(END|START|TOTAL)\\]>");
	}
	
	public String createMessage(boolean printDebug) {
		int count = 16;
		long startTime = System.currentTimeMillis();
		String outputMessage="";
		String seedType="";
		String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(Calendar.getInstance().getTime());
		Pair<String, Integer> pair = maxRecentStartFrequency(db.getRecentStarts());
		
		//common recent word. use it to seed our message
		//the case when its the end token occurs when program has been recently loaded. use random in this case
		if(pair.getValue() >= db.RECENT_WORD_THRESHOLD && !(pair.getKey().equals("<[END]>"))) {
			seedType = "recent word";
			outputMessage = generateLine(db.getMaster(), pair.getKey().intern());
			
			//prevent empty messages by regenerating until we get non-empty
			if(outputMessage.replaceAll("\\s+","").length() == 0) {
				do {
					outputMessage = generateLine(db.getMaster(), getRandomStart(db.getStartMap()).intern());
					count--;
				} while(outputMessage.replaceAll("\\s+", "").length() == 0 && count>=0);
			}
		} else {
			seedType = "random";
			outputMessage = generateLine(db.getMaster(), getRandomStart(db.getStartMap()).intern());
			
			//same as above, regenerate if message empty
			while(outputMessage.replaceAll("\\s+", "").length() == 0 && count > 0) {
				outputMessage = generateLine(db.getMaster(), getRandomStart(db.getStartMap()).intern());
				count--;
			}
		}
		
		//move this to after we output our message to save time? somehow
		if(printDebug) {
			try {
				long endTime = System.currentTimeMillis();
				generateDebugLog(timeStamp, seedType, pair, outputMessage, (startTime - endTime));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		outputMessage = tokens.matcher(outputMessage).replaceAll("");
		return outputMessage;
	}

	public String generateLine(Map<Bigram, ArrayList<String>> table, String startWord) {
		long time1= System.currentTimeMillis();
    	long time2=0;
    	int wordCount=0;
    	String line=" ";
    	//create starting key from start token and random starting word
    	Bigram curKey = new Bigram("<[START]>", startWord);
    	//curKey is the starting phrase
    	
    	System.out.println("starting line generation with start word: " + startWord + ", key: " + curKey);
    	
    	line+=startWord;
    	wordCount++;
    	
    	//enter loop as long as our phrase is less than 128 words and we haven't hit the end token yet 
    	//note if we only hit the end token then our string should be empty! which should never happen! i think!
    	while(wordCount<=128 && !(curKey.getWord2().equals("<[END]>"))) {
    		//grab the list of following words for the current bigram, and choose a random one
    		//should never return null i think as long as we've loaded the proper files
    		//just in case tho
    		if(!table.containsKey(curKey)) {
    			return "something went horribly wrong";
    		//this is what should like always happen
    		} else {
    			//list of all following words. get a random one from the list to use as the next word
    	    	ArrayList<String> curList = table.get(curKey);
    	    	String nextWord = curList.get(rand.nextInt(curList.size())).intern();
    	    	//we found a word, so add it to our line and use the two most recent words as our new key
    	    	line+=" " + nextWord;
    	    	wordCount++;
    	    	curKey = new Bigram(curKey.getWord2(), nextWord);
    	    	//note if we've reached an END token at this point, our curKey.word1 is END and we break the outer loop
    		}
    	}
    	time2 = System.currentTimeMillis();
    	System.out.println("finished line generation in: " + (time2 - time1) + "ms");
    	return line;
	}
	
	//returns a pair of K: most frequently used recent start word, V: its count
	public Pair<String, Integer> maxRecentStartFrequency(Deque<String> recentStarts) {
		//temp map that will store a count of each recent start word
		Map<String, Integer> starts = new ConcurrentHashMap<String, Integer>(20, 0.8f, 2);
		int maxCount=0;
    	int currentCount=0;
    	String maxWord="";
    	String curWord="";
    	Iterator<String> it = recentStarts.iterator();
    	//loop over all elements in deque
    	while(it.hasNext()) {
    		curWord = it.next();
    		//if this word hasn't been seen yet, set its count to 1
    		if(!starts.containsKey(curWord)) {
    			currentCount = 1;
    		//otherwise, increment
    		} else {
    			currentCount = starts.get(curWord) + 1;
    		}
    		//store the new count
    		starts.put(curWord, currentCount);
    		//check if it's greater than our old max count 
    		//if equal, previous word gets priority. earlier elements were more recently used -> should have priority
    		if(currentCount > maxCount) {
    			maxWord = curWord;
    			maxCount = currentCount;
    		}
    	}
    	
    	Pair<String, Integer> maxPair = new Pair<String, Integer>(maxWord, maxCount);
    	return maxPair;
	}
	
	public String getRandomStart(Map<String, Integer> starts) {
		String startWord="";
		int count = rand.nextInt(starts.get("<[TOTAL]>"));
		Iterator<Entry<String, Integer>> it = starts.entrySet().iterator();
		Entry<String, Integer> curEntry;
		
		//loop through each entry, subtracting its count from the total starting words
		//more commonly used words will have a greater range that puts them under 0
		//so they get used more often
		while(it.hasNext() && count >= 0) {
			curEntry = it.next();
			//make sure to skip our TOTAL token entry! because thats not actually something we want to consider
			//and if we dont exclude it, it will basically always be the resulting start word
			//which should NEVER happen and breaks our shit
			//so we continue as long as our current word isnt that token
			if(!(curEntry.getKey().equals("<[TOTAL]>"))) {
				startWord = curEntry.getKey();
				count -= curEntry.getValue();
			}
		}
		
		return startWord;
	}
	
	public void generateDebugLog(String timeStamp, String seedType, Pair<String, Integer> pair, String outputMessage, long totalTime) throws Exception {
		File dir = new File("./logs/" + db.getId());
		System.out.println("b");
		if(!(dir.isDirectory())) {//if directory doesnt exist
			System.out.println("c");
			dir.mkdirs();//create it
		}
		PrintWriter out = new PrintWriter("./logs/" + db.getId() + "/" + timeStamp + "_debug-out_" + db.getId() + "-" + db.getName() + ".txt");
		out.println("message from: " + db.getId() + "-" + db.getName());
		out.println("message time: " + timeStamp);
		out.println("most common word: " + pair.getKey() + ", with count: " + pair.getValue());
		out.println("recent word deque: " + db.getRecentStarts());
		out.println("seed type: " + seedType);
		out.println("message: " + outputMessage);
		out.println("(generated in " + totalTime + "ms)");
		out.println("");
		out.close();
	}
}
