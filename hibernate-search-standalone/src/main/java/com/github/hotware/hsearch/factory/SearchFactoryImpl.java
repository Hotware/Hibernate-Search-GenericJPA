/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.factory;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.backend.spi.Work;
import org.hibernate.search.backend.spi.WorkType;
import org.hibernate.search.backend.spi.Worker;
import org.hibernate.search.engine.ProjectionConstants;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.query.engine.spi.HSQuery;
import org.hibernate.search.stat.Statistics;

import com.github.hotware.hsearch.dto.DtoQueryExecutor;
import com.github.hotware.hsearch.query.HSearchQuery;
import com.github.hotware.hsearch.query.HSearchQueryImpl;
import com.github.hotware.hsearch.transaction.TransactionContext;

public class SearchFactoryImpl implements SearchFactory {

	private final ExtendedSearchIntegrator searchIntegrator;
	private final DtoQueryExecutor queryExec;

	public SearchFactoryImpl(ExtendedSearchIntegrator searchIntegrator) {
		super();
		this.searchIntegrator = searchIntegrator;
		this.queryExec = new DtoQueryExecutor();
	}

	@Override
	public void index(Iterable<?> entities, TransactionContext tc) {
		this.doIndexWork( entities, WorkType.ADD, tc );
	}

	@Override
	public void update(Iterable<?> entities, TransactionContext tc) {
		this.doIndexWork( entities, WorkType.UPDATE, tc );
	}

	@Override
	public void delete(Iterable<?> entities, TransactionContext tc) {
		this.purge( entities, tc );
	}

	@Override
	public void purge(Iterable<?> entities, TransactionContext tc) {
		this.doIndexWork( entities, WorkType.PURGE, tc );
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
		this.searchIntegrator.optimize( entity );
	}

	@Override
	public Set<Class<?>> getIndexedEntities() {
		return this.searchIntegrator.getIndexedTypes();
	}

	@Override
	public Statistics getStatistics() {
		return this.searchIntegrator.getStatistics();
	}

	private void doIndexWork(Iterable<?> entities, WorkType workType, TransactionContext tc) {
		Worker worker = this.searchIntegrator.getWorker();
		for ( Object object : entities ) {
			worker.performWork( new Work( object, workType ), tc );
		}
	}

	@Override
	public void purgeAll(Class<?> entityClass, TransactionContext tc) {
		Worker worker = this.searchIntegrator.getWorker();
		worker.performWork( new Work( entityClass, null, WorkType.PURGE_ALL ), tc );
	}

	public void doIndexWork(Object entities, WorkType workType) {
		this.doIndexWork( Arrays.asList( entities ), workType );
	}

	@Override
	public HSearchQuery createQuery(Query query, Class<?>... targetedEntities) {
		HSQuery hsQuery = this.searchIntegrator.createHSQuery();
		hsQuery.luceneQuery( query );
		// to make sure no entity is used twice
		hsQuery.targetedEntities( new ArrayList<>( new HashSet<>( Arrays.asList( targetedEntities ) ) ) );
		return new HSearchQueryImpl( hsQuery, this.queryExec, this.searchIntegrator );
	}

	@Override
	public FilterCachingStrategy getFilterCachingStrategy() {
		return this.searchIntegrator.getFilterCachingStrategy();
	}

	@Override
	public Analyzer getAnalyzer(String name) {
		return this.searchIntegrator.getAnalyzer( name );
	}

	@Override
	public Analyzer getAnalyzer(Class<?> entityClass) {
		return this.searchIntegrator.getAnalyzer( entityClass );
	}

	@Override
	public void purge(Class<?> entityClass, Query query, TransactionContext tc) {
		HSearchQuery hsQuery = this.createQuery( query, entityClass );
		int count = hsQuery.queryResultSize();
		int processed = 0;
		while ( processed < count ) {
			hsQuery.firstResult( processed ).maxResults( 10 );
			processed += 10;
			for ( Object[] vals : hsQuery.queryProjection( ProjectionConstants.OBJECT_CLASS, ProjectionConstants.ID ) ) {
				this.purge( entityClass, (Serializable) vals[1], tc );
			}
		}
	}

	@Override
	public void purge(Class<?> entityClass, Serializable id, TransactionContext tc) {
		Worker worker = this.searchIntegrator.getWorker();
		worker.performWork( new Work( entityClass, id, WorkType.PURGE ), tc );
	}

}
