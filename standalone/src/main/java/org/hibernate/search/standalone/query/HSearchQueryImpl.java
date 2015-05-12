/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.standalone.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.hibernate.search.engine.ProjectionConstants;
import org.hibernate.search.filter.FullTextFilter;
import org.hibernate.search.query.engine.spi.FacetManager;
import org.hibernate.search.query.engine.spi.HSQuery;
import org.hibernate.search.spatial.Coordinates;
import org.hibernate.search.spatial.impl.Point;
import org.hibernate.search.spi.SearchIntegrator;
import org.hibernate.search.standalone.dto.DtoQueryExecutor;
import org.hibernate.search.standalone.entity.EntityProvider;

public class HSearchQueryImpl implements HSearchQuery {

	private static final Logger LOGGER = Logger.getLogger( HSearchQueryImpl.class.getName() );

	private final HSQuery hsquery;
	private final DtoQueryExecutor queryExec;
	private final SearchIntegrator searchIntegrator;

	public HSearchQueryImpl(HSQuery hsquery, DtoQueryExecutor queryExec, SearchIntegrator searchIntegrator) {
		this.hsquery = hsquery;
		this.queryExec = queryExec;
		this.searchIntegrator = searchIntegrator;
	}

	@Override
	public HSearchQuery sort(Sort sort) {
		this.hsquery.sort( sort );
		return this;
	}

	@Override
	public HSearchQuery filter(Filter filter) {
		this.hsquery.filter( filter );
		return this;
	}

	@Override
	public HSearchQuery firstResult(int firstResult) {
		this.hsquery.firstResult( firstResult );
		return this;
	}

	@Override
	public HSearchQuery maxResults(int maxResults) {
		this.hsquery.maxResults( maxResults );
		return this;
	}

	@Override
	public Query getLuceneQuery() {
		return this.hsquery.getLuceneQuery();
	}

	@Override
	public <R> List<R> queryDto(Class<R> returnedType) {
		return this.queryExec.executeHSQuery( this.hsquery, returnedType );
	}

	@Override
	public List<Object[]> queryProjection(String... projection) {
		String[] projectedFieldsBefore = this.hsquery.getProjectedFields();
		List<Object[]> ret;
		{
			this.hsquery.getTimeoutManager().start();

			this.hsquery.projection( projection );
			ret = this.hsquery.queryEntityInfos().stream().map( (entityInfo) -> {
				return entityInfo.getProjection();
			} ).collect( Collectors.toList() );

			this.hsquery.getTimeoutManager().stop();
		}
		this.hsquery.projection( projectedFieldsBefore );
		return ret;
	}

	@Override
	public int queryResultSize() {
		this.hsquery.getTimeoutManager().start();
		int resultSize = this.hsquery.queryResultSize();
		this.hsquery.getTimeoutManager().stop();
		return resultSize;
	}

	@Override
	public FullTextFilter enableFullTextFilter(String name) {
		return hsquery.enableFullTextFilter( name );
	}

	@Override
	public void disableFullTextFilter(String name) {
		this.hsquery.disableFullTextFilter( name );
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public List query(EntityProvider entityProvider, Fetch fetchType) {
		List<Object> ret;
		List<Object[]> projected = this.queryProjection( ProjectionConstants.OBJECT_CLASS, ProjectionConstants.ID );
		if ( fetchType == Fetch.FIND_BY_ID ) {
			ret = projected.stream().map( (arr) -> {
				return entityProvider.get( (Class<?>) arr[0], arr[1] );
			} ).collect( Collectors.toList() );
		}
		else {
			ret = new ArrayList<>( projected.size() );
			Map<Class<?>, List<Object>> idsForClass = new HashMap<>();
			List<Object> originalOrder = new ArrayList<>();
			Map<Object, Object> idToObject = new HashMap<>();
			// split the ids for each class (and also make sure the original
			// order is saved. this is needed even for only one class)
			projected.stream().forEach( (arr) -> {
				originalOrder.add( arr[1] );
				idsForClass.computeIfAbsent( (Class<?>) arr[0], (clazz) -> {
					return new ArrayList<>();
				} ).add( arr[1] );
			} );
			// get all entities of the same type in one batch
			idsForClass.entrySet().forEach( (Map.Entry<Class<?>, List<Object>> entry) -> {
				entityProvider.getBatch( entry.getKey(), entry.getValue() ).stream().forEach( (object) -> {
					Object id = this.searchIntegrator.getIndexBinding( entry.getKey() ).getDocumentBuilder().getId( object );
					Object value = object;
					idToObject.put( id, value );
				} );
			} );
			// and put everything back into order
			originalOrder.stream().forEach( (id) -> {
				Object value = idToObject.get( id );
				if ( value == null ) {
					LOGGER.info( "ommiting object of id " + id + " which was found in the index but not in the database!" );
				}
				else {
					ret.add( idToObject.get( id ) );
				}
			} );
		}
		if ( ret.size() != projected.size() ) {
			LOGGER.info( "returned size was not equal to projected size" );
		}
		return ret;
	}

	@Override
	public HSearchQuery setTimeout(long timeout, TimeUnit timeUnit) {
		this.hsquery.getTimeoutManager().setTimeout( timeout, timeUnit );
		this.hsquery.getTimeoutManager().raiseExceptionOnTimeout();
		return this;
	}

	@Override
	public HSearchQuery limitExecutionTimeTo(long timeout, TimeUnit timeUnit) {
		this.hsquery.getTimeoutManager().setTimeout( timeout, timeUnit );
		this.hsquery.getTimeoutManager().limitFetchingOnTimeout();
		return this;
	}

	@Override
	public boolean hasPartialResults() {
		return this.hsquery.getTimeoutManager().hasPartialResults();
	}

	@Override
	public Explanation explain(int documentId) {
		return this.hsquery.explain( documentId );
	}

	@Override
	public String toString() {
		return this.hsquery.toString();
	}

	@Override
	public FacetManager getFacetManager() {
		return this.hsquery.getFacetManager();
	}

	@Override
	public HSearchQuery setSpatialParameters(double latitude, double longitude, String fieldName) {
		this.setSpatialParameters( Point.fromDegrees( latitude, longitude ), fieldName );
		return this;
	}

	@Override
	public HSearchQuery setSpatialParameters(Coordinates center, String fieldName) {
		this.hsquery.setSpatialParameters( center, fieldName );
		return this;
	}

}
