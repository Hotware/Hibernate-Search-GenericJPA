/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.query;

import java.util.List;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.hibernate.search.filter.FullTextFilter;

import com.github.hotware.hsearch.entity.EntityProvider;

public interface HSearchQuery {

	// TODO: check if more methods are from hsquery are needed here
	// FIXME: faceting is definitely needed!

	public HSearchQuery sort(Sort sort);

	public HSearchQuery filter(Filter filter);

	public HSearchQuery firstResult(int firstResult);

	public HSearchQuery maxResults(int maxResults);

	public Query getLuceneQuery();

	public <R> List<R> queryDto(Class<R> returnedType);

	public List<Object[]> queryProjection(String... projection);

	public int queryResultSize();

	public FullTextFilter enableFullTextFilter(String name);

	public void disableFullTextFilter(String name);

	@SuppressWarnings("rawtypes")
	public List query(EntityProvider entityProvider, Fetch fetchType);

	@SuppressWarnings("rawtypes")
	public default List query(EntityProvider entityProvider) {
		return this.query( entityProvider, Fetch.FIND_BY_ID );
	}

	public static enum Fetch {
		BATCH, FIND_BY_ID
	}

}
