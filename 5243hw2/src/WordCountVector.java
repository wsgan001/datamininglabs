
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;
import org.tartarus.snowball.ext.PorterStemmer;

public class WordCountVector 
{
	static HashSet<String> EnglishDictionary = new HashSet<String>(); // English dictionary 
	static HashMap<String,Integer> Topics = new HashMap<String,Integer>(); // Holds a list of optional topics
	static HashMap<String,Integer> Words = new HashMap<String,Integer>();  // Holds a list of words that being used
	static HashMap<Integer,Integer> Document = new HashMap<Integer,Integer>(); // a match between documentID in the matrices to a lineId 
	static int[][] TopicsTrainingMatrix = null; // The matrix of training - word count vector
	static int[][] TopicsClassificationMatrix = null; // The matrix of classification - each document is related to which topic
	static int TitleWeight = 10; // The additional weight a topic word has.

	// Adds a document to the vectors
	static public void AddDocumentToTopicVector(int DocId, String[] DocumentTopics, String[] Title, String[] Body)
	{
		// If there's nothing to add - exit
		if (DocumentTopics == null || (Title == null && Body == null)) return;
		
		// Assign the document a number (the line it will be in in the matrices)
		int DocLine = 0;
		synchronized(Document)
		{
			if (Document.containsKey(DocId))
			{
				DocLine = Document.get(DocId);
			}
			else
			{
				DocLine = Document.size();
				Document.put(DocId, DocLine);
			}
		}
		
		// Add the title words to the matrices (word by word)
		if (Title != null)
		{
			for (String CurrTitle : Title)
			{
				CurrTitle = CurrTitle.replaceAll("\\W+", " "); // Removes all non character letters
				String[] TitleWords = CurrTitle.split(" ");
				
				for (String word : TitleWords)
				{
					AddWordToTopicVector(DocLine, word, TitleWeight);
				}
			}
		}
		
		// Add the body words to the matrices (word by word)
		if (Body != null)
		{
			for (String CurrBody : Body)
			{
				CurrBody = CurrBody.replaceAll("\\W+", " "); // Removes all non character letters
				String[] BodyWords = CurrBody.split(" ");
				
				for (String word : BodyWords)
				{
					AddWordToTopicVector(DocLine, word, 1);
				}
			}
		}
		
		// Add the topics to the appropriate matrix
		AddTopicsClassification(DocLine, DocumentTopics);
	}
	
	
	static private StandardAnalyzer analyzer = new StandardAnalyzer(Version.LUCENE_44); // A LUCENE analyzer for stopwords
	static private PorterStemmer stemmer = new PorterStemmer(); // A LUCENE stemmer for stemming
	
	// Adds a word to the vector
	static private void AddWordToTopicVector(int DocLine, String word, int weight)
	{
		// If the word is too short or long - drop it
		if (word.length() <= 2 || word.length() > 15) return;
		
		// Stem the word
		String OrigWord = word;
		stemmer.setCurrent(word);
		if (stemmer.stem())
		{
			word =stemmer.getCurrent();
			
			// If the word is not a known english word - drop it.
			if (EnglishDictionary!= null && (!EnglishDictionary.contains(word) && !EnglishDictionary.contains(OrigWord))) return;
		}
		else
		{
			// In the case word couldn't stem the word - we don't want it...
			return; 
		}
		
		// Check the word is not a stop word, if it is - drop it.
		CharArraySet set=analyzer.getStopwordSet();
		if (set.contains(word) || word == "")
			return;
		
		// The word is good!! yay!!
		// let's add it
		int WordPlace = 0;
		
		// Get the word column ID, if it doesn't have one yet - create a place for it.
		synchronized(Words)
		{
			if (Words.containsKey(word))
			{
				WordPlace = Words.get(word);
			}
			else
			{
				WordPlace = Words.size();
				Words.put(word, WordPlace);
			}
		}
		
		//// Make sure the Matrix has a room for it - if not - add a place
		// if the matrix was not initialized yet - do it!
		if (TopicsTrainingMatrix == null)
		{
			TopicsTrainingMatrix = new int[Document.size()][];
			TopicsTrainingMatrix[DocLine] = new int[Words.size()]; 
		}
		// If the matrix was initizlized - but there's no line meant for it - double the matrix size (increasing is common)
		else if (TopicsTrainingMatrix.length <= DocLine)
		{
			int[][] OldTopicsMatrix = TopicsTrainingMatrix;
			TopicsTrainingMatrix = new int[OldTopicsMatrix.length * 2][];
			// copy old data
			for (int i=0;i<OldTopicsMatrix.length;++i)
			{
				TopicsTrainingMatrix[i] = OldTopicsMatrix[i];
			}
		}
		
		// If the Line was not initialized yet (the room was pre-created, and not used yet...) create it
		if (TopicsTrainingMatrix[DocLine] == null)
		{
			TopicsTrainingMatrix[DocLine] = new int[Words.size()];
		}
		// If it was initialized - but before, and now there's a new word - increase the size of the vector
		else if(TopicsTrainingMatrix[DocLine].length <= WordPlace)
		{
			int[] OldLine = TopicsTrainingMatrix[DocLine];
			TopicsTrainingMatrix[DocLine] = new int[Words.size()];
			for (int i=0;i<OldLine.length;++i)
			{
				TopicsTrainingMatrix[DocLine][i] = OldLine[i];
			}
		}
		
		// Add the word
		TopicsTrainingMatrix[DocLine][WordPlace] = TopicsTrainingMatrix[DocLine][WordPlace] + weight;
	}

	// Adds the topics to the vector
	static private void AddTopicsClassification(int DocLine, String[] DocumentTopics)
	{
		// If there are topics ...
		if (DocumentTopics == null) return;
		
		// Get the topic column, if it does not exist yet - add it.
		int TopicPlace = 0;
		synchronized(Topics)
		{
			for (String topic: DocumentTopics)
			{
				if (!Topics.containsKey(topic))
				{
					Topics.put(topic, Topics.size());
				}
			}
		}
		
		// Add each topic to the vector, one by one
		for (String topic: DocumentTopics)
		{
			TopicPlace = Topics.get(topic);
			
			// If the matrix was not initialized yet - initialize it
			if (TopicsClassificationMatrix == null)
			{
				TopicsClassificationMatrix = new int[Document.size()][];
				TopicsClassificationMatrix[DocLine] = new int[Topics.size()]; 
			}
			// If the matrix don't have enough lines - double the number of lines
			else if (TopicsClassificationMatrix.length <= DocLine)
			{
				int[][] OldTopicsMatrix = TopicsClassificationMatrix;
				TopicsClassificationMatrix = new int[OldTopicsMatrix.length * 2][];
				// copy old data
				for (int i=0;i<OldTopicsMatrix.length;++i)
				{
					TopicsClassificationMatrix[i] = OldTopicsMatrix[i];
				}
			}
			
			// If the line was not initialized yet - init it
			if (TopicsClassificationMatrix[DocLine] == null)
			{
				TopicsClassificationMatrix[DocLine] = new int[Topics.size()];
			}
			// If the line don't have enough columns - add more columns
			else if(TopicsClassificationMatrix[DocLine].length <= TopicPlace)
			{
				int[] OldLine = TopicsClassificationMatrix[DocLine];
				TopicsClassificationMatrix[DocLine] = new int[Topics.size()];
				for (int i=0;i<OldLine.length;++i)
				{
					TopicsClassificationMatrix[DocLine][i] = OldLine[i];
				}
			}
			
			// Add the topic to the vector
			TopicsClassificationMatrix[DocLine][TopicPlace] = TopicsClassificationMatrix[DocLine][TopicPlace] + 1; 
		}
	}

	
	// Load linux dictionary, if it exists (can be any dictionary)
	static public void LoadDictionary()
	{
		// Init the dictionary
		EnglishDictionary = new HashSet<String>();
		
		InputStream    fis;
		BufferedReader br;
		String         word;

		try
		{
			// Get the linux dictionary file
			fis = new FileInputStream("/usr/share/dict/words");
			br = new BufferedReader(new InputStreamReader(fis));
			
			// for each word, add it as is to the dictionary, and add its stemming - if it wasn't added earlier.
			while ((word = br.readLine()) != null) 
			{
				if (!EnglishDictionary.contains(word)) EnglishDictionary.add(word);
				stemmer.setCurrent(word);
				if (stemmer.stem())
				{
					word =stemmer.getCurrent();
					if (!EnglishDictionary.contains(word)) EnglishDictionary.add(word);
				}
			}
			br.close();
		}
		// In the case of an error reading the dictionary - don't initilize it at all.
		catch(Exception ex) {EnglishDictionary = null;}
		br = null;
		fis = null;
	}
	
	// Build a file for holding the information
	static public void WriteToFile(String FileName)
	{
		// Get ready to save the information - build data structure to help do it faster
		HashMap<Integer,String> ReverseWords = new HashMap<>();
		HashMap<Integer,String> ReverseTopics = new HashMap<>();
		HashMap<Integer,Integer> ReverseDocumentIds = new HashMap<>();
		
		// Reverse all the data vectors so searching it would be possible.
		for(String word : Words.keySet())
		{
			ReverseWords.put(Words.get(word), word);
		}
		for(String topic : Topics.keySet())
		{
			ReverseTopics.put(Topics.get(topic), topic);
		}
		for(int docId : Document.keySet())
		{
			ReverseDocumentIds.put(Document.get(docId), docId);
		}
		int NumOfWords = Words.size();
		int NumOfTopics = Topics.size();
		
		
		// Build a file using the following format : columns, Matrix (first column is documentId), and afterwards same format for topics
		OutputStream fis;
		BufferedWriter bw;

		try
		{
			fis = new FileOutputStream(FileName);
			bw = new BufferedWriter(new OutputStreamWriter(fis));
			
			String LineToPut = "";
			
			// Write words
			for (int i=0; i < ReverseWords.size();++i)
			{
				if (i==0)
					LineToPut = "DocumentId," + ReverseWords.get(i);
				else
					LineToPut += "," + ReverseWords.get(i);
			}
			bw.write(LineToPut + "\n\n");
			
			// Write the words vector, each line first parameter is the document id - afterwards the word count of the matching column
			for (int i=0; i< Document.size();++i)
			{
				for(int j=0; TopicsTrainingMatrix[i] != null && j < TopicsTrainingMatrix[i].length; ++j)
				{
					if (j==0) LineToPut = String.valueOf(ReverseDocumentIds.get(i)) + "," + String.valueOf(TopicsTrainingMatrix[i][j]);
					else LineToPut += "," + String.valueOf(TopicsTrainingMatrix[i][j]);
				}
				int StartVal = 0;
				if (TopicsTrainingMatrix[i]!= null ) StartVal = TopicsTrainingMatrix[i].length;
				for (int j = StartVal; j < NumOfWords; ++j)
				{
					if (j==0) LineToPut = String.valueOf(Document.get(i)) + "," + String.valueOf(0);
					else LineToPut += "," + String.valueOf(0);
				}
				bw.write(LineToPut+"\n");
			}
			bw.write("\n");
			
			// Write Topics
			for (int i=0; i < ReverseWords.size();++i)
			{
				if (i==0)
					LineToPut = "DocumentId," + ReverseWords.get(i);
				else
					LineToPut += "," + ReverseWords.get(i);
			}
			bw.write(LineToPut + "\n\n");
			
			// Write the words vector, each line first parameter is the document id - afterwards the word count of the matching column
			for (int i=0; i< Document.size();++i)
			{
				for(int j=0; TopicsClassificationMatrix[i] != null && j < TopicsClassificationMatrix[i].length; ++j)
				{
					if (j==0) LineToPut = String.valueOf(ReverseDocumentIds.get(i)) + "," + String.valueOf(TopicsClassificationMatrix[i][j]);
					else LineToPut += "," + String.valueOf(TopicsClassificationMatrix[i][j]);
				}
				int StartVal = 0;
				if (TopicsClassificationMatrix[i] != null ) StartVal = TopicsClassificationMatrix[i].length;
				for (int j = StartVal; j < NumOfTopics; ++j)
				{
					if (j==0) LineToPut = String.valueOf(Document.get(i)) + "," + String.valueOf(0);
					else LineToPut += "," + String.valueOf(0);
				}
				bw.write(LineToPut+"\n");
			}
			bw.write("\n");
			
			bw.close();
		}
		catch(Exception ex) 
		{
			System.out.println("Failed to save file. error: " + ex.getMessage());
		}
		bw = null;
		fis = null;
		
	}
}