package org.srmq.nlp.holmes.question;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.StringTokenizer;

import org.apache.lucene.queryparser.classic.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.ufpe.cin.nlp.sentence.base.SentenceCompletionQuestions;
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
	private final Random rnd;

	public QuestionProducer(int numMaxQuestions, QuestionQueue questions, QuestionProducerDBManager manager,
			SentencesLuceneIndex sentenceIndex) {
		this.numMaxQuestions = numMaxQuestions;
		this.questions = questions;
		this.manager = manager;
		this.sentenceIndex = sentenceIndex;
		this.rnd = new Random();
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
		for (int i = 0; it.hasNext() && i < maxDocs; i++) {
			result.add(it.next());
		}
		return result;
	}

	public void run() {
		while (questions.size() < numMaxQuestions) {
			try {
				List<String> sentences;
				QuestionProducerDBManager.TrigramInfo rndTrigram;
				String[] trigramWords;
				do {
					rndTrigram = manager.getRandomTrigram();
					trigramWords = rndTrigram.getWords();
					sentences = sentencesWithTrigram(trigramWords, MAX_DOCS);
				} while (sentences.size() == 0);

				String chosenSentence;
				if (sentences.size() > 1) {
					Collections.shuffle(sentences);
				}
				chosenSentence = sentences.get(0);
				sentences = null;

				List<String> completions = manager.bestKCompletions(rndTrigram, N_COMPLETIONS);
				if (completions.size() < 4)
					continue;
				if (completions.size() > 4)
					Collections.shuffle(completions);
				final String trigString = rndTrigram.toString();
				final int trigIndex = chosenSentence.toLowerCase(Locale.ENGLISH).indexOf(trigString);
				List<String> tokensBefore = new ArrayList<String>();
				if (trigIndex > 0) {
					StringTokenizer tokBefore = new StringTokenizer(chosenSentence.substring(0, trigIndex),
							" \t\n\r\f,.:;?![]'(){}", true);
					while (tokBefore.hasMoreElements()) {
						String token = (String) tokBefore.nextElement();
						if (!Character.isWhitespace(token.charAt(0)) || !Character.isISOControl(token.charAt(0))) {
							token = token.trim();
							if (token.length() > 0)
								tokensBefore.add(token);
						}
					}
				} else if (trigIndex < 0)
					continue;
				tokensBefore.add(trigramWords[0]);
				tokensBefore.add(trigramWords[1]);

				int correctIndex = rnd.nextInt(5);
				List<String> options = new ArrayList<String>(5);
				for (int i = 0; i < 5; i++) {
					final String option;
					if (i != correctIndex) {
						option = completions.remove(completions.size() - 1);
					} else {
						option = trigramWords[2];
					}
					options.add(option);
				}

				final int indexAfter = trigIndex + trigString.length();
				List<String> tokensAfter = new ArrayList<String>();
				if (indexAfter < chosenSentence.length()) {
					StringTokenizer tokAfter = new StringTokenizer(
							chosenSentence.substring(indexAfter, chosenSentence.length()), " \t\n\r\f,.:;?![]'(){}",
							true);
					while (tokAfter.hasMoreElements()) {
						String token = (String) tokAfter.nextElement();
						if (!Character.isWhitespace(token.charAt(0)) || !Character.isISOControl(token.charAt(0))) {
							token = token.trim();
							if (token.length() > 0)
								tokensAfter.add(token);
						}
					}

				}

				SentenceCompletionQuestions.Question q = new SentenceCompletionQuestions.Question(tokensBefore,
						tokensAfter, options, correctIndex);
				this.questions.add(q);
				log.info("Current number of questions: " + this.questions.size());
			} catch (Exception e) {
				log.error("QuestionProducer throwed Exception", e);
			}
		}
		log.info("Current number of questions: " + this.questions.size());
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
