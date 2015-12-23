package org.srmq.nlp.holmes.question;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import br.ufpe.cin.nlp.sentence.base.SentenceCompletionQuestions;

public class QuestionQueue implements Queue<SentenceCompletionQuestions.Question> {
	private ConcurrentLinkedQueue<SentenceCompletionQuestions.Question> questions;
	private AtomicInteger numQuestions;
	
	public QuestionQueue() {
		this.questions = new ConcurrentLinkedQueue<SentenceCompletionQuestions.Question>();
		this.numQuestions = new AtomicInteger(0);
	}
	
	public int size() {
		return this.numQuestions.get();
	}
	public boolean isEmpty() {
		return this.questions.isEmpty();
	}
	public boolean contains(Object o) {
		return this.questions.contains(o);
	}
	public Iterator<SentenceCompletionQuestions.Question> iterator() {
		return Collections.unmodifiableCollection(this.questions).iterator();
	}
	public Object[] toArray() {
		return this.questions.toArray();
	}
	public <T> T[] toArray(T[] a) {
		return this.questions.toArray(a);
	}
	public boolean remove(Object o) {
		final boolean ret = this.questions.remove(o);
		if (ret) this.numQuestions.decrementAndGet();
		return ret;
	}
	public boolean containsAll(Collection<?> c) {
		return this.questions.containsAll(c);
	}
	public boolean addAll(Collection<? extends SentenceCompletionQuestions.Question> c) {
		final boolean ret = this.questions.addAll(c);
		if (ret) this.numQuestions.addAndGet(c.size());
		return ret;
	}
	public boolean removeAll(Collection<?> c) {
		throw new UnsupportedOperationException("Unsupported operation");
	}
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException("Unsupported operation");
	}
	
	public void clear() {
		this.questions.clear();
		this.numQuestions.set(0);
		
	}
	public boolean add(SentenceCompletionQuestions.Question e) {
		final boolean ret = this.questions.add(e);
		if (ret) this.numQuestions.incrementAndGet();
		return ret;
	}
	public boolean offer(SentenceCompletionQuestions.Question e) {
		final boolean ret = this.questions.offer(e);
		if (ret) this.numQuestions.incrementAndGet();
		return ret;
	}
	public SentenceCompletionQuestions.Question remove() {
		SentenceCompletionQuestions.Question ret = this.questions.remove();
		this.numQuestions.decrementAndGet();
		return ret;
	}
	public SentenceCompletionQuestions.Question poll() {
		SentenceCompletionQuestions.Question ret = this.questions.poll();
		if (ret != null) this.numQuestions.decrementAndGet();
		return ret;
	}
	public SentenceCompletionQuestions.Question element() {
		return questions.element();
	}
	public SentenceCompletionQuestions.Question peek() {
		return questions.peek();
	}
}
