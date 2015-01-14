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

import java.io.Closeable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.stat.Statistics;

import com.github.hotware.hsearch.event.EventConsumer;
import com.github.hotware.hsearch.query.HSearchQuery;
import com.github.hotware.hsearch.transaction.TransactionContext;

public interface SearchFactory extends Closeable, EventConsumer {

	/**
	 * provides means for getting vanilla Lucene IndexReaders. use this if you
	 * need direct index access because the SearchFactory doesn't allow the
	 * things you want to do
	 * 
	 * @return IndexReaderAccessor for this Searchfactory
	 */
	public IndexReaderAccessor getIndexReaderAccessor();

	/**
	 * @return Set of all indexed Entities' classes.
	 */
	public Set<Class<?>> getIndexedEntities();

	/**
	 * @return
	 */
	public QueryContextBuilder buildQueryBuilder();

	public void optimize();

	public void optimize(Class<?> entityClass);

	public Statistics getStatistics();

	public void purgeAll(Class<?> entityClass, TransactionContext tc);

	public default void purgeAll(Class<?> entityClass) {
		Transaction tc = new Transaction();
		this.purgeAll(entityClass, tc);
		tc.end();
	}

	public HSearchQuery createQuery(Query query, Class<?>... targetedEntities);

	public FilterCachingStrategy getFilterCachingStrategy();

	public Analyzer getAnalyzer(String name);

	public Analyzer getAnalyzer(Class<?> entityClass);

	public void purge(Class<?> entityClass, Serializable id,
			TransactionContext tc);

	public default void purge(Class<?> entityClass, Serializable id) {
		Transaction tc = new Transaction();
		this.purge(entityClass, id, tc);
		tc.end();
	}

	public void purge(Iterable<?> entities, TransactionContext tc);

	public default void purge(Object entity, TransactionContext tc) {
		this.purge(Arrays.asList(entity), tc);
	}

	public default void purge(Iterable<?> entities) {
		Transaction tc = new Transaction();
		this.purge(entities, tc);
		tc.end();
	}

	public default void purge(Object entity) {
		this.purge(Arrays.asList(entity));
	}

	/**
	 * this first queries for all matching documents and then deletes them by
	 * their id
	 */
	public default void purge(Class<?> entityClass, Query query) {
		Transaction tc = new Transaction();
		this.purge(entityClass, query, tc);
		tc.end();
	}

	/**
	 * this first queries for all matching documents and then deletes them by
	 * their id
	 */
	@Deprecated
	public void purge(Class<?> entityClass, Query query, TransactionContext tc);

}
