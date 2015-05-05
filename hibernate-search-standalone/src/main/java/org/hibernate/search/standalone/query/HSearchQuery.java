/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.standalone.query;

import java.util.List;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.hibernate.search.entity.EntityProvider;
import org.hibernate.search.filter.FullTextFilter;

public interface HSearchQuery {

	// TODO: check if more methods are from hsquery are needed here
	// FIXME: faceting is definitely needed!

	HSearchQuery sort(Sort sort);

	HSearchQuery filter(Filter filter);

	HSearchQuery firstResult(int firstResult);

	HSearchQuery maxResults(int maxResults);

	Query getLuceneQuery();

	<R> List<R> queryDto(Class<R> returnedType);

	List<Object[]> queryProjection(String... projection);

	int queryResultSize();

	FullTextFilter enableFullTextFilter(String name);

	void disableFullTextFilter(String name);

	@SuppressWarnings("rawtypes")
	List query(EntityProvider entityProvider, Fetch fetchType);

	@SuppressWarnings("rawtypes")
	default List query(EntityProvider entityProvider) {
		return this.query( entityProvider, Fetch.FIND_BY_ID );
	}

	public enum Fetch {
		BATCH, FIND_BY_ID
	}

}
