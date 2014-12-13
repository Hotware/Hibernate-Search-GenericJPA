package com.github.hotware.hsearch.factory;

import java.io.Closeable;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.engine.impl.FilterDef;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.stat.Statistics;

import com.github.hotware.hsearch.event.EventConsumer;
import com.github.hotware.hsearch.query.HSearchQuery;

public interface SearchFactory extends Closeable, EventConsumer {
	
	public IndexReaderAccessor getIndexReaderAccessor();
	
	public QueryContextBuilder buildQueryBuilder();
	
	public void optimize();
	
	public void optimize(Class<?> entity);
	
	public Statistics getStatistics();
	
	public void purgeAll(Class<?> entityClass);
	
	public <T> HSearchQuery<T> createQuery(Query query, Class<T> targetedEntity);

	public FilterCachingStrategy getFilterCachingStrategy();

	public FilterDef getFilterDefinition(String name);

	public int getFilterCacheBitResultsSize();

	public Analyzer getAnalyzer(String name);

	public Analyzer getAnalyzer(Class<?> clazz);
	
}
