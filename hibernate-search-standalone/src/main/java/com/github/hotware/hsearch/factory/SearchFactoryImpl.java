package com.github.hotware.hsearch.factory;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.backend.spi.Work;
import org.hibernate.search.backend.spi.WorkType;
import org.hibernate.search.backend.spi.Worker;
import org.hibernate.search.engine.impl.FilterDef;
import org.hibernate.search.engine.spi.SearchFactoryImplementor;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.query.engine.spi.HSQuery;
import org.hibernate.search.stat.Statistics;

import com.github.hotware.hsearch.dto.HibernateSearchQueryExecutor;
import com.github.hotware.hsearch.query.HSearchQuery;
import com.github.hotware.hsearch.query.HSearchQueryImpl;
import com.github.hotware.hsearch.transaction.TransactionContext;

public class SearchFactoryImpl implements SearchFactory {

	private final SearchFactoryImplementor searchFactoryImplementor;
	private final HibernateSearchQueryExecutor queryExec;

	public SearchFactoryImpl(SearchFactoryImplementor searchFactoryImplementor) {
		super();
		this.searchFactoryImplementor = searchFactoryImplementor;
		this.queryExec = new HibernateSearchQueryExecutor();
	}

	@Override
	public void index(Iterable<?> entities, TransactionContext tc) {
		this.doIndexWork(entities, WorkType.ADD, tc);
	}

	@Override
	public void update(Iterable<?> entities, TransactionContext tc) {
		this.doIndexWork(entities, WorkType.UPDATE, tc);
	}

	@Override
	public void delete(Iterable<?> entities, TransactionContext tc) {
		this.doIndexWork(entities, WorkType.PURGE, tc);
	}

	@Override
	public IndexReaderAccessor getIndexReaderAccessor() {
		return this.searchFactoryImplementor.getIndexReaderAccessor();
	}

	@Override
	public void close() throws IOException {
		this.searchFactoryImplementor.close();
	}

	@Override
	public QueryContextBuilder buildQueryBuilder() {
		return this.searchFactoryImplementor.buildQueryBuilder();
	}

	@Override
	public void optimize() {
		this.searchFactoryImplementor.optimize();
	}

	@Override
	public void optimize(Class<?> entity) {
		this.searchFactoryImplementor.optimize(entity);
	}

	@Override
	public Statistics getStatistics() {
		return this.searchFactoryImplementor.getStatistics();
	}

	private void doIndexWork(Iterable<?> entities, WorkType workType,
			TransactionContext tc) {
		Worker worker = this.searchFactoryImplementor.getWorker();
		for (Object object : entities) {
			worker.performWork(new Work(object, workType), tc);
		}
	}

	@Override
	public void purgeAll(Class<?> entityClass) {
		TransactionContextImpl tc = new TransactionContextImpl();
		Worker worker = this.searchFactoryImplementor.getWorker();
		worker.performWork(new Work(entityClass, null, WorkType.PURGE_ALL), tc);
		tc.end();
	}

	public void doIndexWork(Object entities, WorkType workType) {
		this.doIndexWork(Arrays.asList(entities), workType);
	}

	@Override
	public <T> HSearchQuery<T> createQuery(Query query, Class<T> targetedEntity) {
		HSQuery hsQuery = this.searchFactoryImplementor.createHSQuery();
		hsQuery.luceneQuery(query);
		hsQuery.targetedEntities(Arrays.asList(targetedEntity));
		return new HSearchQueryImpl<T>(hsQuery, this.queryExec, targetedEntity);
	}

	@Override
	public FilterCachingStrategy getFilterCachingStrategy() {
		return this.searchFactoryImplementor.getFilterCachingStrategy();
	}

	@Override
	public FilterDef getFilterDefinition(String name) {
		return this.searchFactoryImplementor.getFilterDefinition(name);
	}

	@Override
	public int getFilterCacheBitResultsSize() {
		return this.searchFactoryImplementor.getFilterCacheBitResultsSize();
	}

	@Override
	public Analyzer getAnalyzer(String name) {
		return this.searchFactoryImplementor.getAnalyzer(name);
	}

	@Override
	public Analyzer getAnalyzer(Class<?> clazz) {
		return this.searchFactoryImplementor.getAnalyzer(clazz);
	}

}
