/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.standalone.factory;

import java.io.Closeable;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.filter.FilterCachingStrategy;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.standalone.query.HSearchQuery;
import org.hibernate.search.standalone.transaction.TransactionContext;
import org.hibernate.search.stat.Statistics;

public interface SearchFactory extends Closeable {

	/**
	 * provides means for getting vanilla Lucene IndexReaders. use this if you need direct index access because the
	 * SearchFactory doesn't allow the things you want to do
	 *
	 * @return IndexReaderAccessor for this Searchfactory
	 */
	IndexReaderAccessor getIndexReaderAccessor();

	/**
	 * @return Set of all indexed Entities' classes.
	 */
	Set<Class<?>> getIndexedEntities();

	/**
	 * @return
	 */
	QueryContextBuilder buildQueryBuilder();

	void optimize();

	void optimize(Class<?> entityClass);

	Statistics getStatistics();

	void purgeAll(Class<?> entityClass, TransactionContext tc);

	default void purgeAll(Class<?> entityClass) {
		Transaction tc = new Transaction();
		this.purgeAll( entityClass, tc );
		tc.end();
	}

	HSearchQuery createQuery(Query query, Class<?>... targetedEntities);

	FilterCachingStrategy getFilterCachingStrategy();

	Analyzer getAnalyzer(String name);

	Analyzer getAnalyzer(Class<?> entityClass);

	void purge(Class<?> entityClass, Serializable id, TransactionContext tc);

	default void purge(Class<?> entityClass, Serializable id) {
		Transaction tc = new Transaction();
		this.purge( entityClass, id, tc );
		tc.end();
	}

	void purge(Iterable<?> entities, TransactionContext tc);

	default void purge(Object entity, TransactionContext tc) {
		this.purge( Arrays.asList( entity ), tc );
	}

	default void purge(Iterable<?> entities) {
		Transaction tc = new Transaction();
		this.purge( entities, tc );
		tc.end();
	}

	default void purge(Object entity) {
		this.purge( Arrays.asList( entity ) );
	}

	/**
	 * this first queries for all matching documents and then deletes them by their id
	 */
	default void purge(Class<?> entityClass, Query query) {
		Transaction tc = new Transaction();
		this.purge( entityClass, query, tc );
		tc.end();
	}

	/**
	 * this first queries for all matching documents and then deletes them by their id
	 */
	@Deprecated
	void purge(Class<?> entityClass, Query query, TransactionContext tc);

	// same names

	void index(Iterable<?> entities, TransactionContext tc);

	default void index(Object entity, TransactionContext tc) {
		this.index( Arrays.asList( entity ), tc );
	}

	default void index(Iterable<?> entities) {
		Transaction tc = new Transaction();
		this.index( entities, tc );
		tc.end();
	}

	default void index(Object entity) {
		this.index( Arrays.asList( entity ) );
	}

	void update(Iterable<?> entities, TransactionContext tc);

	default void update(Object entity, TransactionContext tc) {
		this.update( Arrays.asList( entity ), tc );
	}

	default void update(Iterable<?> entities) {
		Transaction tc = new Transaction();
		this.update( entities, tc );
		tc.end();
	}

	default void update(Object entity) {
		this.update( Arrays.asList( entity ) );
	}

	void delete(Iterable<?> entities, TransactionContext tc);

	default void delete(Object entity, TransactionContext tc) {
		this.delete( Arrays.asList( entity ), tc );
	}

	default void delete(Iterable<?> entities) {
		Transaction tc = new Transaction();
		this.delete( entities, tc );
		tc.end();
	}

	default void delete(Object entity) {
		this.delete( Arrays.asList( entity ) );
	}

}
