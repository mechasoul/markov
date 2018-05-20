package my.cute.markov;

import java.io.Serializable;
import java.util.Objects;

public class Bigram implements Serializable{

	/**
	 * 
	 */
	private static final long serialVersionUID = -1417931671833458820L;
	private String word1;
	private String word2;
	
	public Bigram(String w1, String w2) {
		word1 = w1.intern();
		word2 = w2.intern();
	}
	
	public Bigram() {
		this("".intern(),"".intern());
	}
	
	public void setWord1(String w) {
		this.word1 = w.intern();
	}
	
	public void setWord2(String w) {
		this.word2 = w.intern();
	}
	
	public String getWord1() {
		return this.word1;
	}
	
	public String getWord2() {
		return this.word2;
	}
	
	public boolean equals(Object test) {
		//self-comparison
		if (this == test) return true;
		
		//make sure the tested object is a Pair
		if (!(test instanceof Bigram)) return false;
		
		//cast is safe by above
		Bigram toTest = (Bigram) test;
		
		return
			this.word1.equals(toTest.word1) &&
			this.word2.equals(toTest.word2);
	}
	
	public int hashCode() {
		return Objects.hash(this.word1, this.word2);
	}
	
	public String toString() {
		return "(" + this.word1 + ", " + this.word2 + ")";
	}
}
