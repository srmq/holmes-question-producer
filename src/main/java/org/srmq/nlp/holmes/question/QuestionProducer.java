package org.srmq.nlp.holmes.question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.queryparser.classic.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.ufpe.cin.nlp.sentence.base.SentenceIndexIterator;
import br.ufpe.cin.nlp.sentence.base.SentencesLuceneIndex;

public class QuestionProducer implements Runnable {
	private final int numMaxQuestions;
	private final QuestionQueue questions;
	private final QuestionProducerDBManager manager;
	private final SentencesLuceneIndex sentenceIndex;
	private static Logger log = LoggerFactory.getLogger(QuestionProducer.class);
	private static final int MAX_DOCS = 1024;
	private static final int N_COMPLETIONS = 30;
	
	public QuestionProducer(int numMaxQuestions, QuestionQueue questions, QuestionProducerDBManager manager, SentencesLuceneIndex sentenceIndex) {
		this.numMaxQuestions = numMaxQuestions;
		this.questions = questions;
		this.manager = manager;
		this.sentenceIndex = sentenceIndex;
	}
	
	private List<String> sentencesWithTrigram(String[] trigram, int maxDocs) throws IOException, ParseException {
		StringBuffer stb = new StringBuffer(trigram[0].length() + trigram[1].length() + trigram[2].length() + 5);
		stb.append("sentence:");
		stb.append('"');
		stb.append(trigram[0]);
		stb.append(' ');
		stb.append(trigram[1]);
		stb.append(' ');
		stb.append(trigram[2]);
		stb.append('"');
		SentenceIndexIterator it = sentenceIndex.searchSentencesWithWord(stb.toString(), maxDocs);
		List<String> result = new ArrayList<String>(maxDocs);
		for(int i = 0; it.hasNext() && i < maxDocs; i++) {
			result.add(it.next());
		}
		return result;
	}
	

	public void run() {
		while (questions.size() < numMaxQuestions) {
			try {
				List<String> sentences;
				QuestionProducerDBManager.TrigramInfo rndTrigram;
				do {
					rndTrigram = manager.getRandomTrigram();
					sentences = sentencesWithTrigram(rndTrigram.getWords(), MAX_DOCS);
				} while(sentences.size() == 0);
				String chosenSentence;
				if (sentences.size() > 1) {
					Collections.shuffle(sentences);
				}
				chosenSentence = sentences.get(0);
				sentences = null;
				
				List<String> completions = manager.bestKCompletions(rndTrigram, N_COMPLETIONS);
				if (completions.size() < 4) continue;
				if (completions.size() > 4) Collections.shuffle(completions);
				
			} catch (Exception e) {
				log.error("QuestionProducer throwed Exception", e);
			}
		}

	}
	public static void main(String[] args) throws Exception {
		int numMaxQuestions = 10000;
		QuestionQueue questions = new QuestionQueue();
		QuestionProducerDBManager manager = new QuestionProducerDBManager();
		SentencesLuceneIndex sentenceIndex = new SentencesLuceneIndex(QuestionProducerService.SENTENCE_INDEX, true);
		QuestionProducer producer = new QuestionProducer(numMaxQuestions, questions, manager, sentenceIndex);
		Thread t = new Thread(producer);
		producer.run();
	}

}
