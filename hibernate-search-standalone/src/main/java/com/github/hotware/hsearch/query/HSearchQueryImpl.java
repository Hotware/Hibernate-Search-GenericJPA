package com.github.hotware.hsearch.query;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.hibernate.search.engine.ProjectionConstants;
import org.hibernate.search.filter.FullTextFilter;
import org.hibernate.search.query.engine.spi.HSQuery;

import com.github.hotware.hsearch.dto.HibernateSearchQueryExecutor;
import com.github.hotware.hsearch.entity.EntityProvider;

public class HSearchQueryImpl<T> implements HSearchQuery<T> {

	private final HSQuery hsquery;
	private final AtomicBoolean frozen;
	private final HibernateSearchQueryExecutor queryExec;

	public HSearchQueryImpl(HSQuery hsquery,
			HibernateSearchQueryExecutor queryExec) {
		this.hsquery = hsquery;
		this.frozen = new AtomicBoolean(false);
		this.queryExec = queryExec;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#sort(org
	 * .apache.lucene.search.Sort)
	 */
	@Override
	public HSearchQuery<T> sort(Sort sort) {
		this.checkNotFrozen();
		this.hsquery.sort(sort);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#filter
	 * (org.apache.lucene.search.Filter)
	 */
	@Override
	public HSearchQuery<T> filter(Filter filter) {
		this.checkNotFrozen();
		this.hsquery.filter(filter);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#firstResult
	 * (int)
	 */
	@Override
	public HSearchQuery<T> firstResult(int firstResult) {
		this.checkNotFrozen();
		this.hsquery.firstResult(firstResult);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#maxResults
	 * (int)
	 */
	@Override
	public HSearchQuery<T> maxResults(int maxResults) {
		this.checkNotFrozen();
		this.hsquery.maxResults(maxResults);
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#getLuceneQuery
	 * ()
	 */
	@Override
	public Query getLuceneQuery() {
		this.checkNotFrozen();
		return this.hsquery.getLuceneQuery();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#queryDto
	 * (java.lang.Class)
	 */
	@Override
	public <R> List<R> queryDto(Class<R> returnedType) {
		this.frozen.set(true);
		return this.queryExec.executeHSQuery(this.hsquery, returnedType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#
	 * queryProjection(java.lang.String)
	 */
	@Override
	public List<Object[]> queryProjection(String... projection) {
		this.frozen.set(true);
		String[] projectedFieldsBefore = this.hsquery.getProjectedFields();
		List<Object[]> ret;
		{
			this.hsquery.getTimeoutManager().start();

			this.hsquery.projection(projection);
			ret = this.hsquery.queryEntityInfos().stream()
					.map((entityInfo) -> {
						return entityInfo.getProjection();
					}).collect(Collectors.toList());

			this.hsquery.getTimeoutManager().stop();
		}
		this.hsquery.projection(projectedFieldsBefore);
		return ret;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#
	 * queryResultSize()
	 */
	@Override
	public int queryResultSize() {
		this.frozen.set(true);
		this.hsquery.getTimeoutManager().start();
		int resultSize = this.hsquery.queryResultSize();
		this.hsquery.getTimeoutManager().stop();
		return resultSize;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#
	 * enableFullTextFilter(java.lang.String)
	 */
	@Override
	public FullTextFilter enableFullTextFilter(String name) {
		this.checkNotFrozen();
		return hsquery.enableFullTextFilter(name);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.github.hotware.lucene.extension.hsearch.query.HSearchQuery#
	 * disableFullTextFilter(java.lang.String)
	 */
	@Override
	public void disableFullTextFilter(String name) {
		this.checkNotFrozen();
		this.hsquery.disableFullTextFilter(name);
	}

	@Override
	public <R> List<R> query(EntityProvider entityProvider,
			Class<R> returnedType, Fetch fetchType) {
		List<R> ret;
		List<Object[]> projected = this.queryProjection(ProjectionConstants.ID);
		if (fetchType == Fetch.FIND_BY_ID) {
			ret = projected.stream().map((arr) -> {
				return entityProvider.get(returnedType, arr[0]);
			}).collect(Collectors.toList());
		} else {
			ret = entityProvider.getBatch(returnedType,
					projected.stream().map((arr) -> {
						return arr[0];
					}).collect(Collectors.toList()));
		}
		return ret;
	}

	private void checkNotFrozen() {
		if (this.frozen.get()) {
			throw new IllegalStateException(
					"this query was already used once and cannot be changed anymore");
		}
	}

}
