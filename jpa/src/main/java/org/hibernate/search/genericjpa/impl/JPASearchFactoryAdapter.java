/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.impl;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.TransactionManager;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;

import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.engine.metadata.impl.MetadataProvider;
import org.hibernate.search.genericjpa.JPASearchFactoryController;
import org.hibernate.search.genericjpa.batchindexing.MassIndexer;
import org.hibernate.search.genericjpa.batchindexing.impl.MassIndexerImpl;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.db.events.index.IndexUpdater;
import org.hibernate.search.genericjpa.entity.EntityManagerEntityProvider;
import org.hibernate.search.genericjpa.entity.EntityProvider;
import org.hibernate.search.genericjpa.entity.JPAReusableEntityProvider;
import org.hibernate.search.genericjpa.factory.SearchConfigurationImpl;
import org.hibernate.search.genericjpa.factory.StandaloneSearchFactory;
import org.hibernate.search.genericjpa.factory.StandaloneSearchFactoryImpl;
import org.hibernate.search.genericjpa.metadata.MetadataRehasher;
import org.hibernate.search.genericjpa.metadata.MetadataUtil;
import org.hibernate.search.genericjpa.metadata.RehashedTypeMetadata;
import org.hibernate.search.genericjpa.query.HSearchQuery;
import org.hibernate.search.genericjpa.transaction.TransactionContext;
import org.hibernate.search.indexes.IndexReaderAccessor;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.metadata.IndexedTypeDescriptor;
import org.hibernate.search.query.dsl.QueryContextBuilder;
import org.hibernate.search.spi.SearchIntegrator;
import org.hibernate.search.spi.SearchIntegratorBuilder;
import org.hibernate.search.stat.Statistics;

/**
 * Base class to create SearchFactories in a JPA environment.
 *
 * @author Martin Braun
 */
public final class JPASearchFactoryAdapter
		implements StandaloneSearchFactory, UpdateConsumer, JPASearchFactoryController {

	private static final Logger LOGGER = Logger.getLogger( JPASearchFactoryAdapter.class.getName() );

	private final Set<UpdateConsumer> updateConsumers = new HashSet<>();
	private final Lock lock = new ReentrantLock();

	private String name;
	private EntityManagerFactory emf;
	private Properties properties;
	private UpdateSourceProvider updateSourceProvider;
	private List<Class<?>> indexRootTypes;
	private List<Class<?>> jpaRootTypes;
	private boolean useJTATransaction;
	private StandaloneSearchFactory searchFactory;
	private UpdateSource updateSource;
	private Set<Class<?>> indexRelevantEntities;
	private Map<Class<?>, String> idProperties;
	private int updateDelay = 500;
	private int batchSizeForUpdates = 5;
	private IndexUpdater indexUpdater;
	private Map<Class<?>, RehashedTypeMetadata> rehashedTypeMetadataForIndexRoot;
	private Map<Class<?>, List<Class<?>>> containedInIndexOf;
	private ExtendedSearchIntegrator searchIntegrator;

	private TransactionManager transactionManager;

	@Override
	public void updateEvent(List<UpdateInfo> updateInfo) {
		this.lock.lock();
		try {
			for ( UpdateConsumer updateConsumer : this.updateConsumers ) {
				try {
					updateConsumer.updateEvent( updateInfo );
				}
				catch (Exception e) {
					LOGGER.log( Level.WARNING, "Exception during notification of UpdateConsumers", e );
				}
			}
		}
		finally {
			this.lock.unlock();
		}
	}

	public StandaloneSearchFactory getSearchFactory() {
		return this.searchFactory;
	}

	private UpdateSource createUpdateSource() {
		return this.updateSourceProvider.getUpdateSource(
				this.updateDelay,
				TimeUnit.MILLISECONDS,
				this.batchSizeForUpdates
		);
	}

	public EntityProvider entityProvider(EntityManager em) {
		return new EntityManagerEntityProvider( em, this.idProperties );
	}

	public final void init() {
		SearchConfigurationImpl config;
		if ( this.properties != null ) {
			LOGGER.info( "using config: " + this.properties );
			config = new SearchConfigurationImpl( this.properties );
		}
		else {
			config = new SearchConfigurationImpl();
		}

		MetadataProvider metadataProvider = MetadataUtil.getDummyMetadataProvider( config );
		MetadataRehasher rehasher = new MetadataRehasher();

		List<RehashedTypeMetadata> rehashedTypeMetadatas = new ArrayList<>();
		this.rehashedTypeMetadataForIndexRoot = new HashMap<>();
		for ( Class<?> indexRootType : this.getIndexRootTypes() ) {
			RehashedTypeMetadata rehashed = rehasher.rehash( metadataProvider.getTypeMetadataFor( indexRootType ) );
			rehashedTypeMetadatas.add( rehashed );
			rehashedTypeMetadataForIndexRoot.put( indexRootType, rehashed );
		}

		this.indexRelevantEntities = Collections.unmodifiableSet(
				MetadataUtil.calculateIndexRelevantEntities(
						rehashedTypeMetadatas
				)
		);
		this.idProperties = MetadataUtil.calculateIdProperties( rehashedTypeMetadatas );

		SearchIntegratorBuilder builder = new SearchIntegratorBuilder();
		// we have to build an integrator here (but we don't need it afterwards)
		builder.configuration( config ).buildSearchIntegrator();
		this.indexRelevantEntities.forEach(
				builder::addClass
		);
		SearchIntegrator impl = builder.buildSearchIntegrator();
		this.searchIntegrator = impl.unwrap( ExtendedSearchIntegrator.class );
		this.searchFactory = new StandaloneSearchFactoryImpl( this.searchIntegrator );

		this.updateSource = this.createUpdateSource();
		if ( this.updateSource != null ) {
			this.containedInIndexOf = MetadataUtil.calculateInIndexOf( rehashedTypeMetadatas );

			JPAReusableEntityProvider entityProvider = new JPAReusableEntityProvider(
					this.getEmf(),
					this.idProperties,
					this.transactionManager
			);
			this.indexUpdater = new IndexUpdater(
					rehashedTypeMetadataForIndexRoot, containedInIndexOf, entityProvider,
					impl.unwrap( ExtendedSearchIntegrator.class )
			);
			this.updateSource.setUpdateConsumers( Arrays.asList( indexUpdater, this ) );
			this.updateSource.start();
		}
	}

	public TransactionManager getTransactionManager() {
		return transactionManager;
	}

	public JPASearchFactoryAdapter setTransactionManager(TransactionManager transactionManager) {
		this.transactionManager = transactionManager;
		return this;
	}

	public String getName() {
		return this.name;
	}

	public JPASearchFactoryAdapter setName(String name) {
		this.name = name;
		return this;
	}

	public EntityManagerFactory getEmf() {
		return this.emf;
	}

	public JPASearchFactoryAdapter setEmf(EntityManagerFactory emf) {
		this.emf = emf;
		return this;
	}

	public boolean isUseJTATransaction() {
		return this.useJTATransaction;
	}

	public JPASearchFactoryAdapter setUseJTATransaction(boolean useJTATransaction) {
		this.useJTATransaction = useJTATransaction;
		return this;
	}

	public List<Class<?>> getIndexRootTypes() {
		return this.indexRootTypes;
	}

	public JPASearchFactoryAdapter setIndexRootTypes(List<Class<?>> indexRootTypes) {
		this.indexRootTypes = indexRootTypes;
		return this;
	}

	public List<Class<?>> getJpaRootTypes() {
		return jpaRootTypes;
	}

	public JPASearchFactoryAdapter setJpaRootTypes(List<Class<?>> jpaRootTypes) {
		this.jpaRootTypes = jpaRootTypes;
		return this;
	}

	public Properties getProperties() {
		return this.properties;
	}

	public JPASearchFactoryAdapter setProperties(Map properties) {
		this.properties = new Properties();
		this.properties.putAll( properties );
		return this;
	}

	public UpdateSourceProvider getUpdateSourceProvider() {
		return this.updateSourceProvider;
	}

	public JPASearchFactoryAdapter setUpdateSourceProvider(UpdateSourceProvider updateSourceProvider) {
		this.updateSourceProvider = updateSourceProvider;
		return this;
	}

	public int getBatchSizeForUpdates() {
		return this.batchSizeForUpdates;
	}

	public JPASearchFactoryAdapter setBatchSizeForUpdates(int batchSizeForUpdates) {
		this.batchSizeForUpdates = batchSizeForUpdates;
		return this;
	}

	public int getUpdateDelay() {
		return this.updateDelay;
	}

	public JPASearchFactoryAdapter setUpdateDelay(int updateDelay) {
		this.updateDelay = updateDelay;
		return this;
	}

	@Override
	public void pauseUpdating(boolean pause) {
		if ( this.updateSource != null ) {
			this.updateSource.pause( pause );
		}
	}

	@Override
	public FullTextEntityManager getFullTextEntityManager(EntityManager em) {
		// em may be null for when we don't need an EntityManager
		if ( em != null && em instanceof FullTextEntityManager ) {
			return (FullTextEntityManager) em;
		}
		else {
			return ImplementationFactory.createFullTextEntityManager( em, this );
		}
	}

	public MassIndexer createMassIndexer(List<Class<?>> indexRootTypes) {
		return new MassIndexerImpl( this.emf, this.searchIntegrator, indexRootTypes, this.transactionManager );
	}

	public MassIndexer createMassIndexer() {
		return this.createMassIndexer( this.jpaRootTypes );
	}

	public ExtendedSearchIntegrator getSearchIntegrator() {
		return this.searchIntegrator;
	}

	@Override
	public void index(Iterable<?> entities, TransactionContext tc) {
		this.searchFactory.index( entities, tc );
	}

	@Override
	public void close() {
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
	public void purgeByTerm(Class<?> entityClass, String field, Integer val, TransactionContext tc) {
		searchFactory.purgeByTerm( entityClass, field, val, tc );
	}

	@Override
	public void purgeByTerm(Class<?> entityClass, String field, Long val, TransactionContext tc) {
		searchFactory.purgeByTerm( entityClass, field, val, tc );
	}

	@Override
	public void purgeByTerm(Class<?> entityClass, String field, Float val, TransactionContext tc) {
		searchFactory.purgeByTerm( entityClass, field, val, tc );
	}

	@Override
	public void purgeByTerm(Class<?> entityClass, String field, Double val, TransactionContext tc) {
		searchFactory.purgeByTerm( entityClass, field, val, tc );
	}

	@Override
	public void purgeByTerm(Class<?> entityClass, String field, String val, TransactionContext tc) {
		searchFactory.purgeByTerm( entityClass, field, val, tc );
	}

	@Deprecated
	@Override
	public void purge(Class<?> entityClass, Query query, TransactionContext tc) {
		this.searchFactory.purge( entityClass, query, tc );
	}

	@Override
	public void flushToIndexes(TransactionContext tc) {
		this.searchFactory.flushToIndexes( tc );
	}

	@Override
	public IndexedTypeDescriptor getIndexedTypeDescriptor(Class<?> entityType) {
		return this.searchFactory.getIndexedTypeDescriptor( entityType );
	}

	@Override
	public Set<Class<?>> getIndexedTypes() {
		return this.searchFactory.getIndexedTypes();
	}

	@Override
	public <T> T unwrap(Class<T> cls) {
		return this.searchFactory.unwrap( cls );
	}

	@Override
	public void addUpdateConsumer(UpdateConsumer updateConsumer) {
		this.lock.lock();
		try {
			this.updateConsumers.add( updateConsumer );
		}
		finally {
			this.lock.unlock();
		}
	}

	@Override
	public void removeUpdateConsumer(UpdateConsumer updateConsumer) {
		this.lock.lock();
		try {
			this.updateConsumers.remove( updateConsumer );
		}
		finally {
			this.lock.unlock();
		}
	}

}
