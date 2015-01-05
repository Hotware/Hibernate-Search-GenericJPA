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
		return this.query(entityProvider, Fetch.FIND_BY_ID);
	}
	
	public static enum Fetch {
		BATCH,
		FIND_BY_ID
	}

}