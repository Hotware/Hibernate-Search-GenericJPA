package com.github.hotware.lucene.extension.hsearch.query;

import java.util.List;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.hibernate.search.filter.FullTextFilter;

import com.github.hotware.lucene.extension.hsearch.entity.EntityProvider;

public interface HSearchQuery<T> {

	// TODO: check if more methods are from hsquery are needed here
	// FIXME: faceting is definitely needed!

	public HSearchQuery<T> sort(Sort sort);

	public HSearchQuery<T> filter(Filter filter);

	public HSearchQuery<T> firstResult(int firstResult);

	public HSearchQuery<T> maxResults(int maxResults);

	public Query getLuceneQuery();

	public <R> List<R> queryDto(Class<R> returnedType);

	public List<Object[]> queryProjection(String... projection);

	public int queryResultSize();

	public FullTextFilter enableFullTextFilter(String name);

	public void disableFullTextFilter(String name);

	//NEW METHODS:
	
	public <R> List<R> query(EntityProvider entityProvider, Class<R> returnedType);

}