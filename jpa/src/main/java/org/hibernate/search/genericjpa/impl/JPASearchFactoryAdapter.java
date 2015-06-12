/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.impl;

import java.io.IOException;
import java.io.Serializable;
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
import org.hibernate.search.genericjpa.JPASearchFactory;
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
public final class JPASearchFactoryAdapter implements StandaloneSearchFactory, UpdateConsumer, JPASearchFactory {

	private final Logger LOGGER = Logger.getLogger( JPASearchFactoryAdapter.class.getName() );
	private StandaloneSearchFactory searchFactory;
	private UpdateSource updateSource;
	private Set<Class<?>> indexRelevantEntities;
	private Map<Class<?>, String> idProperties;

	private final String name;
	private final EntityManagerFactory emf;
	private final Properties properties;
	private final UpdateConsumer updateConsumer;
	private final ScheduledExecutorService exec;
	private final UpdateSourceProvider updateSourceProvider;
	private final List<Class<?>> indexRootTypes;
	private final boolean useUserTransaction;

	private int updateDelay = 500;
	private int batchSizeForUpdates = 5;

	@SuppressWarnings("unchecked")
	public JPASearchFactoryAdapter(String name, EntityManagerFactory emf, boolean useUserTransaction, List<Class<?>> indexRootTypes, @SuppressWarnings("rawtypes") Map properties,
			UpdateConsumer updateConsumer, ScheduledExecutorService exec, UpdateSourceProvider updateSourceProvider) {
		this.name = name;
		this.emf = emf;
		this.useUserTransaction = useUserTransaction;
		this.indexRootTypes = indexRootTypes;
		this.properties = new Properties();
		this.properties.putAll( properties );
		this.updateConsumer = updateConsumer;
		this.exec = exec;
		this.updateSourceProvider = updateSourceProvider;
	}

	@Override
	public void updateEvent(List<UpdateInfo> updateInfo) {
		if ( this.updateConsumer != null ) {
			this.updateConsumer.updateEvent( updateInfo );
		}
	}

	private UpdateSource createUpdateSource() {
		return this.updateSourceProvider.getUpdateSource( this.updateDelay, TimeUnit.MILLISECONDS, this.batchSizeForUpdates, this.exec );
	}

	public EntityProvider entityProvider(EntityManager em) {
		return new EntityManagerEntityProvider( em, this.idProperties );
	}

	public final void init() {
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

		this.updateSource = this.createUpdateSource();
		if ( this.updateSource != null ) {
			Map<Class<?>, List<Class<?>>> containedInIndexOf = MetadataUtil.calculateInIndexOf( rehashedTypeMetadatas );

			JPAReusableEntityProvider entityProvider = new JPAReusableEntityProvider( this.getEmf(), this.idProperties, this.isUseUserTransaction() );
			IndexUpdater indexUpdater = new IndexUpdater( rehashedTypeMetadataPerIndexRoot, containedInIndexOf, entityProvider,
					impl.unwrap( ExtendedSearchIntegrator.class ) );
			this.updateSource.setUpdateConsumers( Arrays.asList( indexUpdater, this ) );
			this.updateSource.start();
		}
	}

	public void pauseUpdateSource(boolean pause) {
		if ( this.updateSource != null ) {
			this.updateSource.pause( pause );
		}
	}

	public void shutdown() {
		try {
			this.close();
		}
		catch (IOException e) {
			throw new SearchException( e );
		}
	}

	@Override
	public void index(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.index( entities, tc );
	}

	@Override
	public void close() throws IOException {
		try {
			if ( this.updateSource != null ) {
				this.updateSource.stop();
			}
			this.searchFactory.close();
		}
		finally {
			SearchFactoryRegistry.unsetup( name, this );
		}
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
		this.searchFactory.flushToIndexes( tc );
	}

	public IndexedTypeDescriptor getIndexedTypeDescriptor(Class<?> entityType) {
		return this.searchFactory.getIndexedTypeDescriptor( entityType );
	}

	public String getName() {
		return this.name;
	}

	public Set<Class<?>> getIndexedTypes() {
		return this.searchFactory.getIndexedTypes();
	}

	public <T> T unwrap(Class<T> cls) {
		return this.searchFactory.unwrap( cls );
	}

	/**
	 * @return the updateDelay
	 */
	public int getUpdateDelay() {
		return this.updateDelay;
	}

	/**
	 * @param updateDelay the updateDelay to set
	 */
	public void setUpdateDelay(int updateDelay) {
		this.updateDelay = updateDelay;
	}

	/**
	 * @return the batchSizeForUpdates
	 */
	public int getBatchSizeForUpdates() {
		return this.batchSizeForUpdates;
	}

	/**
	 * @param batchSizeForUpdates the batchSizeForUpdates to set
	 */
	public void setBatchSizeForUpdates(int batchSizeForUpdates) {
		this.batchSizeForUpdates = batchSizeForUpdates;
	}

	private EntityManagerFactory getEmf() {
		return this.emf;
	}

	private Properties getConfigProperties() {
		return this.properties;
	}

	private List<Class<?>> getIndexRootTypes() {
		return this.indexRootTypes;
	}

	private boolean isUseUserTransaction() {
		return this.useUserTransaction;
	}

}