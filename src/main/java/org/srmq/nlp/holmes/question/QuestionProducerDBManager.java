package org.srmq.nlp.holmes.question;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.commons.pool2.impl.GenericObjectPool;

import br.ufpe.cin.util.db.PooledHSQLDBConnectionFactory;

public class QuestionProducerDBManager {
	
	public static class TrigramInfo {
		private String[] words;
		private int firstTwoId = -1;
		private int lastId = -1;
		private int[] wordIds;
		public String[] getWords() {
			return words;
		}
		public void setWords(String[] words) {
			this.words = words;
		}
		public int getFirstTwoId() {
			return firstTwoId;
		}
		public void setFirstTwoId(int firstTwoId) {
			this.firstTwoId = firstTwoId;
		}
		public int getLastId() {
			return lastId;
		}
		public void setLastId(int lastId) {
			this.lastId = lastId;
		}
		public int[] getWordIds() {
			return wordIds;
		}
		public void setWordIds(int[] wordIds) {
			this.wordIds = wordIds;
		}
	}
	
	
	public static final String dbFile = "trigramDB/trigramDB";
	private boolean isClosed = false;
	private int maxLastTrigramId;
	private final Random rnd;
	
	private GenericObjectPool<Connection>  pool;
	public QuestionProducerDBManager() throws Exception {
		this.pool = PooledHSQLDBConnectionFactory.newHSQLDBPool(new File(dbFile), "SA", "");
		this.maxLastTrigramId = computeLastTrigramId();
		this.rnd = new Random(1);
	}
	
	public void close() {
		if (!isClosed) {
			this.pool.close();
			isClosed = true;
		}
	}
	
	public TrigramInfo getRandomTrigram() throws Exception {
		final int randId = rnd.nextInt(maxLastTrigramId + 1);
		final String query = "SELECT w1.word as fstword, w2.word as sndword, w3.word as thrdword, tft.id as firstTwoId, tl.id as lastId, w1.id as fstid, w2.id as sndid, w3.id as thrdid  " +
				"FROM words w1, words w2, words w3, trigramLast tl, trigramFirstTwo tft " + 
				"WHERE tl.id = ? AND tl.lastword = w3.id AND tl.firsttwo = tft.id AND tft.sndword = w2.id AND tft.fstword = w1.id";
		Connection conn = this.pool.borrowObject();
		try {
			PreparedStatement pstmt = conn.prepareStatement(query);
			pstmt.setInt(1, randId);
			ResultSet rset = pstmt.executeQuery();
			rset.next();
			String[] retWords = new String[3];
			retWords[0] = rset.getString(1);
			retWords[1] = rset.getString(2);
			retWords[2] = rset.getString(3);
			
			int[] wordIds = new int[3];
			
			TrigramInfo ret = new TrigramInfo();
			ret.setFirstTwoId(rset.getInt(4));
			ret.setLastId(rset.getInt(5));
			wordIds[0] = rset.getInt(6);
			wordIds[1] = rset.getInt(7);
			wordIds[2] = rset.getInt(8);
			ret.setWordIds(wordIds);
			ret.setWords(retWords);
			assert (!rset.next());
			pstmt.close();
			return ret;
		} finally {
			this.pool.returnObject(conn);
		}
	}
	

	private int computeLastTrigramId() throws Exception {
		final String query = "SELECT MAX(id) FROM trigramLast";
		Connection conn = this.pool.borrowObject();
		try {
			Statement stmt = conn.createStatement();
			ResultSet rset = stmt.executeQuery(query);
			rset.next();
			final int id = rset.getInt(1);
			stmt.close();
			return id;
		} finally {
			this.pool.returnObject(conn);
		}		
	}
	
	@Override
	protected void finalize() throws Throwable {
		close();
		super.finalize();
	}
	
	public List<String> bestKCompletions(TrigramInfo trigram, int nCompletions) throws Exception {
		List<String> result = new ArrayList<String>(nCompletions);
		String query = "SELECT TOP ? w.word as completion FROM words w, trigramLast tl " +
				"WHERE tl.firsttwo = ? AND tl.id <> ? AND tl.lastword = w.id " +
				"ORDER BY tl.occurr DESC";
		Connection conn = this.pool.borrowObject();
		try {
			PreparedStatement pstmt = conn.prepareStatement(query);
			pstmt.setInt(1, nCompletions);
			pstmt.setInt(2, trigram.getFirstTwoId());
			pstmt.setInt(3, trigram.getLastId());
			ResultSet rset = pstmt.executeQuery();
			for (int i = 0; i < nCompletions && rset.next(); i++) {
				final String word = rset.getString(1);
				result.add(word);
			}
			
			return result;
		} finally {
			this.pool.returnObject(conn);
		}
	}
	
	
	public static void main(String[] args) throws Exception {
		QuestionProducerDBManager questionDb = new QuestionProducerDBManager(); 
		for (int i = 0; i < 10000; i++) {
			TrigramInfo randomTrigram = questionDb.getRandomTrigram();
			List<String> complet = questionDb.bestKCompletions(randomTrigram, 30);
			System.out.println(complet);
		}
	}


}
