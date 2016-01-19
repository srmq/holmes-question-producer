package org.srmq.nlp.holmes.question;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import br.ufpe.cin.nlp.sentence.base.SentencesLuceneIndex;

public class QuestionProducerService implements Runnable{
	private int numMaxQuestions;
	private final ListeningExecutorService pool;
	
	public static final int DEFAULT_MAXQUESTIONS = 10000;
	
	private int minQuestionThreshold;
	
	private final QuestionQueue questions;
	
	private final Set<ListenableFuture<?>> taskSet;
	
	private static Logger log = LoggerFactory.getLogger(QuestionProducerService.class);
	
	private final QuestionProducerDBManager manager;
	
	private final SentencesLuceneIndex sentenceIndex;
	
	private int numThreads;
	
	static final String SENTENCE_INDEX = "/dev/shm/Holmes-sentence-index";
	
	
	public QuestionProducerService(int numThreads, int maxQuestions) throws Exception{
		numMaxQuestions = maxQuestions;
		this.numThreads = numThreads;
		pool = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(numThreads));
		this.minQuestionThreshold = DEFAULT_MAXQUESTIONS /10;
		this.questions = new QuestionQueue();
		this.taskSet = Collections.newSetFromMap(new ConcurrentHashMap<ListenableFuture<?>, Boolean>());
		this.manager = new QuestionProducerDBManager();
		this.sentenceIndex = new SentencesLuceneIndex(SENTENCE_INDEX, true);
	}
	
	public QuestionProducerService() throws Exception {
		this(Runtime.getRuntime().availableProcessors(), DEFAULT_MAXQUESTIONS);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void run() {
		for(;;) {
				if (questions.size() < minQuestionThreshold && taskSet.size() < this.numThreads) {
					final ListenableFuture<?> taskStatus = pool.submit(new QuestionProducer(numMaxQuestions, questions, manager, sentenceIndex));
					taskSet.add(taskStatus);
					Futures.addCallback(taskStatus, new FutureCallback() {
	
						public void onSuccess(Object result) {
							taskSet.remove(taskStatus);
							
						}
	
						public void onFailure(Throwable t) {
							log.warn("Failure when trying to generate questions", t);
							taskSet.remove(taskStatus);
						}
					});
				} else {
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						log.warn("InterrupedException while sleeping", e);
					}
				}
				
		}
		
	}
public static void main(String[] args) throws Exception {
	QuestionProducerService service = new QuestionProducerService();
	Thread t = new Thread(service);
	t.start();
	for(;;) {
		Thread.sleep(1000);
	}
}
}
