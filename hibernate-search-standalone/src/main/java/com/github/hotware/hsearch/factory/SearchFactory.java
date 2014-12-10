package com.github.hotware.hsearch.factory;

import java.io.Closeable;
import java.util.Arrays;

import org.apache.lucene.search.Query;
import org.hibernate.search.backend.spi.WorkType;
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
	
	public void optimize(Class<?> entity);
	
	public Statistics getStatistics();
	
	public default void doIndexWork(Object entities, WorkType workType, TransactionContext tc) {
		this.doIndexWork(Arrays.asList(entities), workType, tc);
	}
	
	public void doIndexWork(Iterable<?> entities, WorkType workType, TransactionContext tc);

	public void doIndexWork(Iterable<?> entities, WorkType workType);
	
	public void doIndexWork(Object entities, WorkType workType);
	
	public void purgeAll(Class<?> entityClass);
	
	public <T> HSearchQuery<T> createQuery(Query query, Class<T> targetedEntity);
	
}
