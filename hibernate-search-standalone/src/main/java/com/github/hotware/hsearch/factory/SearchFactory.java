package com.github.hotware.hsearch.factory;

import java.io.Closeable;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.backend.DeletionQuery;
import org.hibernate.search.engine.impl.FilterDef;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.stat.Statistics;

import com.github.hotware.hsearch.event.EventConsumer;
import com.github.hotware.hsearch.query.HSearchQuery;
import com.github.hotware.hsearch.transaction.TransactionContext;

public interface SearchFactory extends Closeable, EventConsumer {

	public IndexReaderAccessor getIndexReaderAccessor();

	public QueryContextBuilder buildQueryBuilder();

	public void optimize();

	public void optimize(Class<?> entityClass);

	public Statistics getStatistics();
	
	public void purgeAll(Class<?> entityClass, TransactionContext tc);

	public default void purgeAll(Class<?> entityClass) {
		TransactionContextImpl tc = new TransactionContextImpl();
		this.purgeAll(entityClass, tc);
		tc.end();
	}

	public <T> HSearchQuery<T> createQuery(Class<T> targetetEntity, Query query);

	public FilterCachingStrategy getFilterCachingStrategy();

	public FilterDef getFilterDefinition(String name);

	public int getFilterCacheBitResultsSize();

	public Analyzer getAnalyzer(String name);

	public Analyzer getAnalyzer(Class<?> entityClass);

	public default void deleteByQuery(Class<?> entityClass,
			DeletionQuery deletionQuery) {
		TransactionContextImpl tc = new TransactionContextImpl();
		this.deleteByQuery(entityClass, deletionQuery, tc);
		tc.end();
	}

	public void deleteByQuery(Class<?> entityClass,
			DeletionQuery deletionQuery, TransactionContext tc);

}
