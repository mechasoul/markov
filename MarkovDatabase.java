package my.cute.markov;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;


public class MarkovDatabase {
	
	//master database for bigram->list of following words
	public Map<Bigram, ArrayList<String>> master;
	//map starting terms -> count. this is where we draw our starting term from for random generation
	Map<String, Integer> startMaster;
	//fuk
	Deque<String> recentStartQueue;
	
	String previousChecksum;
	
	String identifier;
	String name;
	
	public final int RECENT_WORD_COUNT;
	public final int RECENT_WORD_THRESHOLD;
	
	Charset utf8;
	
	boolean debugOn;
	
	public MarkovDatabase(String id, String n, int wordCount, int wordThreshold) {
		RECENT_WORD_COUNT = wordCount;
		RECENT_WORD_THRESHOLD = wordThreshold;
		
		master = new ConcurrentHashMap<Bigram, ArrayList<String>>(65536, 0.8f, 4);
		startMaster = new ConcurrentHashMap<String, Integer>(65536, 0.8f, 4);
		recentStartQueue = new ConcurrentLinkedDeque<String>();
		//fill deque with end token as placeholder
		for(int i=0; i < RECENT_WORD_COUNT; i++) {
			recentStartQueue.addFirst("<[END]>".intern());
		}
		
		previousChecksum = "";
		identifier = id;
		name = n;
		
		utf8 = StandardCharsets.UTF_8;
		debugOn = false;
		
		loadDatabase();
	}
	
	public MarkovDatabase(String id, String n, int wordCount, int wordThreshold, boolean debug) {
		RECENT_WORD_COUNT = wordCount;
		RECENT_WORD_THRESHOLD = wordThreshold;
		
		master = new ConcurrentHashMap<Bigram, ArrayList<String>>(65536, 0.8f, 4);
		startMaster = new ConcurrentHashMap<String, Integer>(65536, 0.8f, 4);
		recentStartQueue = new ConcurrentLinkedDeque<String>();
		//fill deque with end token as placeholder
		for(int i=0; i < RECENT_WORD_COUNT; i++) {
			recentStartQueue.addFirst("<[END]>".intern());
		}
		
		previousChecksum = "";
		identifier = id;
		name = n;
		
		utf8 = StandardCharsets.UTF_8;
		debugOn = debug;
		
		loadDatabase();
	}
	
	private File getNewestFile(File[] files) {
		File newestFile = files[0];
		for (File file : files) {
			if (newestFile.lastModified() < file.lastModified()) {
				newestFile = file;
			}
		}
		
		return newestFile;
	}

	/* take string @in, and go down the list, in 2-word pairs, and record the following word
	* wordpairs (bigrams) act as keys in master table, map to another hashtable
	* second hashtable contains words that follow that word pair, w/ the number of times theve been recorded
	* so like, maybe master record contains pairs "it is", "i am", "that was", etc
	* then a single pair eg "i am" maps to its own dict, which maps words that have followed the phrase "i am"
	* so "i am" maps to a dict that maps "nice" -> 6, "cool" -> 3, "gay" -> 1 if we've recorded
	* "i am nice" 6 times, "i am cool" 3 times, "i am gay" 1 time
	* 
	* inputs:
	* @table: the master databse map to add the pairs to
	* @in: the parsed line to be added (parse via InputHandler. starts with start token)
	* @starts: 
	* 
	* outputs:
	* stores the data from @in into @table
	* increments the count of the starting word in our starting word map
	*/
	public void process(List<String> in) {
		
		//special case: line only contains start token (something is horribl wrong)
		if(in.size() <= 1) {
			//dont do shit
		} else {
			String firstWord = in.get(1).intern();
			String nextWord;//to store the word following the bigram
			int currentValue=0;

			//first we add start word to our map. if it's not already there:
			if(!startMaster.containsKey(firstWord)) {
				startMaster.put(firstWord, 1);//set its count to 1
			} else {
				currentValue = startMaster.get(firstWord) + 1;
				startMaster.put(firstWord, currentValue);//otherwise increment its count
			}
			
			//similarly, create a total counter if its not already there
			if(!startMaster.containsKey("<[TOTAL]>".intern())) {
				startMaster.put("<[TOTAL]>".intern(), 1);
				//otherwise increment it
			} else {
				currentValue = startMaster.get("<[TOTAL]>".intern()) + 1;
				startMaster.put("<[TOTAL]>".intern(), currentValue);
			}
			
			//boot out the oldest start word, add the new one
			recentStartQueue.removeLast();
			recentStartQueue.addFirst(firstWord);
			
			for(int i=1; i < in.size(); i++) {
				//always exists bc length >=2
				Bigram curKey = new Bigram(in.get(i-1).intern(), in.get(i).intern());
				ArrayList<String> curList;

				if(i==(in.size() - 1)) {
					nextWord = "<[END]>".intern();//if we're at the end of the string, bigram maps to a special end-of-line indicator
				} else {
					nextWord = in.get(i+1).intern();//otherwise it maps to the next word. avoid oob by above
				}
				if(master.containsKey(curKey)) {
					curList = master.get(curKey);//curList is the list of words following the bigram
					
				} else {//first instance of this bigram
					curList = new ArrayList<String>(1);//need to create a new list
				}
				curList.add(nextWord.intern());
				master.put(curKey, curList);//update table for this server
			}
		}
	}
	
	public void loadDatabase() {
		System.out.println("inside load");
		long time1 = System.currentTimeMillis();
		File startFile, masterFile, dir;
		File[] startFiles;
		File[] masterFiles;
		
		String prevName;
		
		//create filters to make life easier. master files end in .masterdb,
		//start files end in .startdb (see saveDatabase() below)
		FilenameFilter masterFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if(name.endsWith(".masterdb")) {
					return true;
				} else {
					return false;
				}
			}
		};
		
		FilenameFilter startFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				if(name.endsWith(".startdb")) {
					return true;
				} else {
					return false;
				}
			}
		};
		
		dir = new File("./markovdb/" + identifier);
		//false if directory doesnt exist or if theres a file named that for some reason?
		if(dir.isDirectory()) {
			//grab all files in directory
			masterFiles = new File("./markovdb/" + identifier).listFiles(masterFilter);
			startFiles = new File("./markovdb/" + identifier).listFiles(startFilter);
			
			//empty arrays occur if no files in directory. that shouldnt happen beyond outside intervention
			//but to be safe we return here
			if(masterFiles.length == 0 || startFiles.length == 0) {
				return;
			}
			//array nonempty by above
			prevName = getNewestFile(masterFiles).getName();
			//dont mess with the file structure or the above will break
			masterFile = new File("./markovdb/" + identifier + "/" + prevName);
			
			//repeat above for startFile. i know technically i dont need to loop twice
			//since the save method always gives a near identical name
			//but w/e
			prevName = getNewestFile(startFiles).getName();
			startFile = new File("./markovdb/" + identifier + "/" + prevName);
			
			//now masterFile and startFile hold references to the most recent 
			//respective dbs, so now we load them into our objects
			//first, master
			try {
				System.out.println(prevName);
				
				LineIterator it = FileUtils.lineIterator(masterFile, "UTF-8");
				
				try {
					
					//loop through each line in our input file
					//form the first two words into our key
					//the remaining words are used to form the list of following words
					//(naturally shit breaks really badly if the files are modified by something other than the program)
					while(it.hasNext()) {
						//our delimiter is "||", which in regex is matched by \\|\\|
						String[] words = it.nextLine().split("\\|\\|");
						//construct new key each pass
						Bigram key = new Bigram(words[0].intern(), words[1].intern());
						
						//again, shit breaks if file is modified. but by program, every line will have at least 3 words
						//this is the list of following words & will hold all words in the line except the first two
						ArrayList<String> curList = new ArrayList<String>(words.length - 2);
						
						//get all words from line, except the first two
						for(int i=2; i < words.length; i++) {
							curList.add(words[i].intern());
						}
						
						master.put(key, curList);
					}
				} finally {
					it.close();
				}
				
				//same process, but for the start file
				
				LineIterator startIt = FileUtils.lineIterator(startFile, "UTF-8");
				String test="";
				
				try {
					
					while(startIt.hasNext()) {
						String line = startIt.nextLine();
						test = line;
						String[] words = line.split("\\|\\|");
						startMaster.put(words[0].intern(), Integer.parseInt(words[1].intern()));
					}
				} catch (Exception e) {
					System.out.println("error in line: " + test);
				} finally {
					startIt.close();
				}
				//both files are now fully loaded!
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ArrayIndexOutOfBoundsException e) {
				e.printStackTrace();
			}
			if(debugOn) {
				String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(Calendar.getInstance().getTime());
				long time2 = System.currentTimeMillis();
				try {
					dir = new File("./logs/" + identifier);
					if(!(dir.isDirectory())) {//if directory doesnt exist
						dir.mkdirs();//create it
					}
					PrintWriter out = new PrintWriter("./logs/" + identifier + "/" + timeStamp + "_debug-load_" + identifier + "-" + name + ".txt");
					out.println("load finished on " + identifier + " in " + (time2 - time1) + "ms");
					out.close();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		}//the end of our if statement. if the directory didnt exist in the first
		//place, then we load nothing because its our first run
		System.out.println("end of load");
	}
	
	//checks if database object is different from previously checked
	//if it is, saves it and the start master object to a csv file
	public void saveDatabase() {
		File masterFile, startFile, dir;
		String currentChecksum;
		long time1 = System.currentTimeMillis();
		try {
			currentChecksum = MD5Checksum.getObjectMD5(master);
			//we enter the IF as long as our current channel's map is different than it was when initialized / last saved. avoid redundant backups
			if(!(previousChecksum.equals(currentChecksum))) {
				previousChecksum = currentChecksum;
				dir = new File("./markovdb/" + identifier);
				if(!(dir.isDirectory())) {//if directory doesnt exist
					dir.mkdirs();//create it
				}
				
				StringBuilder builder = new StringBuilder();
				//iterate over all entries in our master map
				//need to further iterate over all entries in the following list
				//our output csv will be formatted as:
				//keyword1\\keyword2\\followingword1\\followingword2\\followingword3\\followingword4\\...
				for(Entry<Bigram, ArrayList<String>> e : master.entrySet()) {
					String word1 = e.getKey().getWord1().intern();
					String word2 = e.getKey().getWord2().intern();
					
					ArrayList<String> curList = e.getValue();
					
					builder.append(word1 + "||" + word2);
					
					for(int i=0; i < curList.size(); i++) {
						builder.append("||" + curList.get(i).intern());
					}
					
					builder.append("\r\n");
				}
				//and shove it all in a csv
				String timeStamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(Calendar.getInstance().getTime());
				String data = builder.toString().trim();
				long time2 = System.currentTimeMillis();
				masterFile = new File("./markovdb/" + identifier + "/" + timeStamp + "_" + identifier + "-" + name + ".masterdb");
				try {
					FileUtils.writeStringToFile(masterFile, data, utf8);
				} catch (IOException e) {
					e.printStackTrace();
				}
				long time3 = System.currentTimeMillis();
				StringBuilder startBuilder = new StringBuilder();
				//repeat the process for our start master file
				for(Entry<String, Integer> e : startMaster.entrySet()) {
					startBuilder.append(e.getKey().intern() + "||");
					startBuilder.append("" + e.getValue());
					startBuilder.append("\r\n");
				}
				String startData = startBuilder.toString().trim();
				long time4 = System.currentTimeMillis();
				startFile = new File("./markovdb/" + identifier + "/" + timeStamp + "_start_" + identifier + "-" + name + ".startdb");
				try {
					FileUtils.writeStringToFile(startFile, startData, utf8);
				} catch (IOException e) {
					e.printStackTrace();
				}
				long time5 = System.currentTimeMillis();
				if(debugOn) {
					try {
						dir = new File("./logs/" + identifier);
						if(!(dir.isDirectory())) {//if directory doesnt exist
							dir.mkdirs();//create it
						}
						PrintWriter out = new PrintWriter("./logs/" + identifier + "/" + timeStamp + "_debug-save_" + identifier + "-" + name + ".txt");
						out.println("save finished on " + identifier + " in " + (time5 - time1) + "ms");
						out.println("stringbuilder for master took: " + (time2 - time1) + "ms");
						out.println("file write for master took: " + (time3 - time2) + "ms");
						out.println("stringbuilder for start took: " + (time4 - time3) + "ms");
						out.println("file write for start took: " + (time5 - time4) + "ms");
						out.close();
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String getId() {
		return identifier;
	}
	
	public Deque<String> getRecentStarts() {
		return recentStartQueue;
	}
	
	public Map<Bigram, ArrayList<String>> getMaster() {
		return master;
	}
	
	public Map<String, Integer> getStartMap() {
		return startMaster;
	}
	
	public void toggleDebug() {
		debugOn = !debugOn;
	}

	public String getName() {
		return name;
	}
}
