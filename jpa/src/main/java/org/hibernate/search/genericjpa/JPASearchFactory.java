/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.engine.metadata.impl.MetadataProvider;
import org.hibernate.search.genericjpa.db.events.IndexUpdater;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.entity.EntityManagerEntityProvider;
import org.hibernate.search.genericjpa.entity.JPAReusableEntityProvider;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.metadata.IndexedTypeDescriptor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.spi.SearchIntegrator;
import org.hibernate.search.spi.SearchIntegratorBuilder;
import org.hibernate.search.standalone.entity.EntityProvider;
import org.hibernate.search.standalone.factory.SearchConfigurationImpl;
import org.hibernate.search.standalone.factory.StandaloneSearchFactory;
import org.hibernate.search.standalone.factory.StandaloneSearchFactoryImpl;
import org.hibernate.search.standalone.metadata.MetadataRehasher;
import org.hibernate.search.standalone.metadata.MetadataUtil;
import org.hibernate.search.standalone.metadata.RehashedTypeMetadata;
import org.hibernate.search.standalone.query.HSearchQuery;
import org.hibernate.search.standalone.transaction.TransactionContext;
import org.hibernate.search.stat.Statistics;

/**
 * Base class to create SearchFactories in a JPA environment.
 * 
 * @author Martin Braun
 */
public abstract class JPASearchFactory implements StandaloneSearchFactory, UpdateConsumer {

	private final Logger LOGGER = Logger.getLogger( JPASearchFactory.class.getName() );
	StandaloneSearchFactory searchFactory;
	private UpdateSource updateSource;
	Set<Class<?>> indexRelevantEntities;
	Map<Class<?>, String> idProperties;

	public EntityProvider entityProvider(EntityManager em) {
		return new EntityManagerEntityProvider( em, this.idProperties );
	}

	protected abstract EntityManagerFactory getEmf();

	protected abstract Properties getConfigProperties();

	protected abstract List<Class<?>> getIndexRootTypes();

	// THESE ARE NEEDED FOR THE UPDATES
	// TODO: make this easier

	protected abstract List<Class<?>> getUpdateClasses();

	protected abstract TimeUnit getDelayUnit();

	protected abstract long getDelay();

	protected abstract int getBatchSizeForUpdates();

	/**
	 * for JTA transactions this has to be a {@link javax.enterprise.concurrent.ManagedScheduledExecutorService}
	 */
	protected abstract ScheduledExecutorService getExecutorServiceForUpdater();

	protected abstract boolean isUseUserTransaction();

	protected abstract UpdateSource getUpdateSource();

	public final void init() {
		if ( this.isUseUserTransaction() ) {
			ScheduledExecutorService exec = this.getExecutorServiceForUpdater();
			try {
				if ( !Class.forName( "javax.enterprise.concurrent.ManagedScheduledExecutorService" ).isAssignableFrom( exec.getClass() ) ) {
					throw new IllegalArgumentException( "an instance of" + " javax.enterprise.concurrent.ManagedScheduledExecutorService"
							+ "has to be used for scheduling when using JTA transactions!" );
				}
			}
			catch (ClassNotFoundException e) {
				throw new SearchException( "coudln't load class javax.enterprise.concurrent.ManagedScheduledExecutorService "
						+ "even though JTA transaction is to be used!" );
			}
		}
		SearchConfigurationImpl config;
		if ( this.getConfigProperties() != null && !this.getConfigProperties().equals( "" ) ) {
			LOGGER.info( "using config @" + this.getConfigProperties() );
			config = new SearchConfigurationImpl( this.getConfigProperties() );
		}
		else {
			config = new SearchConfigurationImpl();
		}

		MetadataProvider metadataProvider = MetadataUtil.getMetadataProvider( config );
		MetadataRehasher rehasher = new MetadataRehasher();

		List<RehashedTypeMetadata> rehashedTypeMetadatas = new ArrayList<>();
		Map<Class<?>, RehashedTypeMetadata> rehashedTypeMetadataPerIndexRoot = new HashMap<>();
		for ( Class<?> indexRootType : this.getIndexRootTypes() ) {
			RehashedTypeMetadata rehashed = rehasher.rehash( metadataProvider.getTypeMetadataFor( indexRootType ) );
			rehashedTypeMetadatas.add( rehashed );
			rehashedTypeMetadataPerIndexRoot.put( indexRootType, rehashed );
		}

		this.indexRelevantEntities = Collections.unmodifiableSet( MetadataUtil.calculateIndexRelevantEntities( rehashedTypeMetadatas ) );
		this.idProperties = MetadataUtil.calculateIdProperties( rehashedTypeMetadatas );

		SearchIntegratorBuilder builder = new SearchIntegratorBuilder();
		// we have to build an integrator here (but we don't need it afterwards)
		builder.configuration( config ).buildSearchIntegrator();
		this.indexRelevantEntities.forEach( (clazz) -> {
			builder.addClass( clazz );
		} );
		SearchIntegrator impl = builder.buildSearchIntegrator();
		this.searchFactory = new StandaloneSearchFactoryImpl( impl.unwrap( ExtendedSearchIntegrator.class ) );

		this.updateSource = this.getUpdateSource();
		if ( this.updateSource != null ) {
			Map<Class<?>, List<Class<?>>> containedInIndexOf = MetadataUtil.calculateInIndexOf( rehashedTypeMetadatas );

			JPAReusableEntityProvider entityProvider = new JPAReusableEntityProvider( this.getEmf(), this.idProperties, this.isUseUserTransaction() );
			IndexUpdater indexUpdater = new IndexUpdater( rehashedTypeMetadataPerIndexRoot, containedInIndexOf, entityProvider,
					impl.unwrap( ExtendedSearchIntegrator.class ) );
			this.updateSource.setUpdateConsumers( Arrays.asList( indexUpdater, this ) );
			this.updateSource.start();
		}
	}

	public void shutdown() {
		try {
			this.updateSource.stop();
			this.close();
		}
		catch (IOException e) {
			throw new SearchException( e );
		}
	}

	public Set<Class<?>> getIndexRelevantEntities() {
		return this.indexRelevantEntities;
	}

	@Override
	public void index(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.index( entities, tc );
	}

	@Override
	public void close() throws IOException {
		this.searchFactory.close();
	}

	@Override
	public void update(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.update( entities, tc );
	}

	@Override
	public IndexReaderAccessor getIndexReaderAccessor() {
		return this.searchFactory.getIndexReaderAccessor();
	}

	@Override
	public QueryContextBuilder buildQueryBuilder() {
		return this.searchFactory.buildQueryBuilder();
	}

	@Override
	public void optimize() {
		this.searchFactory.optimize();
	}

	@Override
	public void optimize(Class<?> entity) {
		this.searchFactory.optimize( entity );
	}

	@Override
	public Statistics getStatistics() {
		return this.searchFactory.getStatistics();
	}

	@Override
	public void delete(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.delete( entities, tc );
	}

	@Override
	public void purgeAll(Class<?> entityClass) {
		this.searchFactory.purgeAll( entityClass );
	}

	@Override
	public Analyzer getAnalyzer(String name) {
		return this.searchFactory.getAnalyzer( name );
	}

	@Override
	public Analyzer getAnalyzer(Class<?> clazz) {
		return this.searchFactory.getAnalyzer( clazz );
	}

	@Override
	public void purgeAll(Class<?> entityClass, TransactionContext tc) {
		this.searchFactory.purgeAll( entityClass, tc );
	}

	@Override
	public HSearchQuery createQuery(Query query, Class<?>... targetedEntities) {
		return this.searchFactory.createQuery( query, targetedEntities );
	}

	@Override
	public void purge(Class<?> entityClass, Serializable id, TransactionContext tc) {
		this.searchFactory.purge( entityClass, id, tc );
	}

	@Override
	public void purge(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.purge( entities, tc );
	}

	@Override
	public void purge(Class<?> entityClass, Query query, TransactionContext tc) {
		this.searchFactory.purge( entityClass, query, tc );
	}

	public void flushToIndexes(TransactionContext tc) {
		searchFactory.flushToIndexes( tc );
	}

	public IndexedTypeDescriptor getIndexedTypeDescriptor(Class<?> entityType) {
		return searchFactory.getIndexedTypeDescriptor( entityType );
	}

	public Set<Class<?>> getIndexedTypes() {
		return searchFactory.getIndexedTypes();
	}

	public <T> T unwrap(Class<T> cls) {
		return searchFactory.unwrap( cls );
	}

}
