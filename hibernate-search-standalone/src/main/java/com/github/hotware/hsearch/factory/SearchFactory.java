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
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.backend.DeletionQuery;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.stat.Statistics;

import com.github.hotware.hsearch.event.EventConsumer;
import com.github.hotware.hsearch.query.HSearchQuery;
import com.github.hotware.hsearch.transaction.TransactionContext;

public interface SearchFactory extends Closeable, EventConsumer {

	public IndexReaderAccessor getIndexReaderAccessor();
	
	public Set<Class<?>> getIndexedEntities();

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

	public <T> HSearchQuery<T> createQuery(Class<T> targetedEntity, Query query);

	public FilterCachingStrategy getFilterCachingStrategy();

	public Analyzer getAnalyzer(String name);

	public Analyzer getAnalyzer(Class<?> entityClass);

	public default void deleteByQuery(Class<?> entityClass,
			DeletionQuery deletionQuery) {
		Transaction tc = new Transaction();
		this.deleteByQuery(entityClass, deletionQuery, tc);
		tc.end();
	}

	public void deleteByQuery(Class<?> entityClass,
			DeletionQuery deletionQuery, TransactionContext tc);

}
