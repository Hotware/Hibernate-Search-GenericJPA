/*
 * Copyright 2015 Martin Braun
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.hotware.hsearch.factory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.backend.DeletionQuery;
import org.hibernate.search.backend.spi.DeleteByQueryWork;
import org.hibernate.search.backend.spi.Work;
import org.hibernate.search.backend.spi.WorkType;
import org.hibernate.search.backend.spi.Worker;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
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

	private final ExtendedSearchIntegrator searchIntegrator;
	private final HibernateSearchQueryExecutor queryExec;

	public SearchFactoryImpl(ExtendedSearchIntegrator searchIntegrator) {
		super();
		this.searchIntegrator = searchIntegrator;
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
		return this.searchIntegrator.getIndexReaderAccessor();
	}

	@Override
	public void close() throws IOException {
		this.searchIntegrator.close();
	}

	@Override
	public QueryContextBuilder buildQueryBuilder() {
		return this.searchIntegrator.buildQueryBuilder();
	}

	@Override
	public void optimize() {
		this.searchIntegrator.optimize();
	}

	@Override
	public void optimize(Class<?> entity) {
		this.searchIntegrator.optimize(entity);
	}

	@Override
	public Set<Class<?>> getIndexedEntities() {
		return this.searchIntegrator.getIndexedTypes();
	}

	@Override
	public Statistics getStatistics() {
		return this.searchIntegrator.getStatistics();
	}

	private void doIndexWork(Iterable<?> entities, WorkType workType,
			TransactionContext tc) {
		Worker worker = this.searchIntegrator.getWorker();
		for (Object object : entities) {
			worker.performWork(new Work(object, workType), tc);
		}
	}

	@Override
	public void purgeAll(Class<?> entityClass, TransactionContext tc) {
		Worker worker = this.searchIntegrator.getWorker();
		worker.performWork(new Work(entityClass, null, WorkType.PURGE_ALL), tc);
	}

	public void doIndexWork(Object entities, WorkType workType) {
		this.doIndexWork(Arrays.asList(entities), workType);
	}

	@Override
	public HSearchQuery createQuery(Query query, Class<?>... targetedEntities) {
		HSQuery hsQuery = this.searchIntegrator.createHSQuery();
		hsQuery.luceneQuery(query);
		// to make sure no entity is used twice
		hsQuery.targetedEntities(new ArrayList<>(new HashSet<>(Arrays
				.asList(targetedEntities))));
		return new HSearchQueryImpl(hsQuery, this.queryExec, this.searchIntegrator);
	}

	@Override
	public FilterCachingStrategy getFilterCachingStrategy() {
		return this.searchIntegrator.getFilterCachingStrategy();
	}

	@Override
	public Analyzer getAnalyzer(String name) {
		return this.searchIntegrator.getAnalyzer(name);
	}

	@Override
	public Analyzer getAnalyzer(Class<?> entityClass) {
		return this.searchIntegrator.getAnalyzer(entityClass);
	}

	@Override
	public void deleteByQuery(Class<?> entityClass,
			DeletionQuery deletionQuery, TransactionContext tc) {
		Worker worker = this.searchIntegrator.getWorker();
		worker.performWork(new DeleteByQueryWork(entityClass, deletionQuery),
				tc);
	}

}
