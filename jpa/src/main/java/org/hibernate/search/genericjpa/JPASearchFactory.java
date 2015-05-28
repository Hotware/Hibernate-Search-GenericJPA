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
import java.sql.SQLException;
import java.sql.Statement;
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

import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.Query;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.engine.metadata.impl.MetadataProvider;
import org.hibernate.search.entity.EntityManagerCloseable;
import org.hibernate.search.entity.EntityManagerEntityProvider;
import org.hibernate.search.entity.JPAReusableEntityProvider;
import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.IndexUpdater;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdateSource;
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
 * Base class to create SearchFactories in a JPA environment. Uses a JPAEventSource. 
 * <b>You have to call the init() and shutdown() method respectively.</b>
 *
 * @author Martin Braun
 */
public abstract class JPASearchFactory implements StandaloneSearchFactory, UpdateConsumer {

	private final Logger LOGGER = Logger.getLogger( EntityManagerFactory.class.getName() );
	StandaloneSearchFactory searchFactory;
	UpdateSource updateSource;
	Set<Class<?>> indexRelevantEntities;
	Map<Class<?>, String> idProperties;
	Map<Class<?>, List<Class<?>>> containedInIndexOf;

	public EntityProvider entityProvider(EntityManager em) {
		return new EntityManagerEntityProvider( new EntityManagerCloseable( em ), this.idProperties );
	}

	protected abstract EntityManagerFactory getEmf();

	protected abstract String getConfigFile();

	protected abstract List<Class<?>> getIndexRootTypes();

	// THESE ARE NEEDED FOR THE UPDATES
	// TODO: make this easier

	protected abstract List<Class<?>> getUpdateClasses();

	protected abstract TimeUnit getDelayUnit();

	protected abstract long getDelay();

	protected abstract int getBatchSizeForUpdates();

	protected abstract TriggerSQLStringSource getTriggerSQLStringSource();
	
	protected abstract Connection getConnectionForSetup(EntityManager em);

	/**
	 * for JTA transactions this has to be a {@link javax.enterprise.concurrent.ManagedScheduledExecutorService}
	 */
	protected abstract ScheduledExecutorService getExecutorServiceForUpdater();

	protected abstract boolean isUseJTATransaction();

	public void init() {
		if ( this.isUseJTATransaction() && !( this.getExecutorServiceForUpdater() instanceof ManagedScheduledExecutorService ) ) {
			throw new IllegalArgumentException( "an instance of" + ManagedScheduledExecutorService.class
					+ "has to be used for scheduling when using JTA transactions!" );
		}
		SearchConfigurationImpl config;
		if ( this.getConfigFile() != null && !this.getConfigFile().equals( "" ) ) {
			LOGGER.info( "using config @" + this.getConfigFile() );
			try (InputStream is = this.getClass().getResourceAsStream( this.getConfigFile() )) {
				Properties props = new Properties();
				props.load( is );
				config = new SearchConfigurationImpl( props );
			}
			catch (IOException e) {
				throw new RuntimeException( "IOException while loading property file.", e );
			}
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
		this.containedInIndexOf = MetadataUtil.calculateInIndexOf( rehashedTypeMetadatas );

		SearchIntegratorBuilder builder = new SearchIntegratorBuilder();
		// we have to build an integrator here (but we don't need it afterwards)
		builder.configuration( config ).buildSearchIntegrator();
		this.indexRelevantEntities.forEach( (clazz) -> {
			builder.addClass( clazz );
		} );
		SearchIntegrator impl = builder.buildSearchIntegrator();
		this.searchFactory = new StandaloneSearchFactoryImpl( impl.unwrap( ExtendedSearchIntegrator.class ) );

		JPAReusableEntityProvider entityProvider = new JPAReusableEntityProvider( this.getEmf(), this.idProperties, this.isUseJTATransaction() );
		IndexUpdater indexUpdater = new IndexUpdater( rehashedTypeMetadataPerIndexRoot, this.containedInIndexOf, entityProvider,
				impl.unwrap( ExtendedSearchIntegrator.class ) );
		EventModelParser eventModelParser = new EventModelParser();
		List<EventModelInfo> eventModelInfos = eventModelParser.parse( new ArrayList<>( this.getUpdateClasses() ) );

		this.setupTriggers( eventModelInfos );

		this.updateSource = new JPAUpdateSource( eventModelInfos, this.getEmf(), this.isUseJTATransaction(), this.getDelay(), this.getDelayUnit(),
				this.getBatchSizeForUpdates(), this.getExecutorServiceForUpdater() );
		this.updateSource.setUpdateConsumers( Arrays.asList( indexUpdater, this ) );
		this.updateSource.start();
	}

	private void setupTriggers(List<EventModelInfo> eventModelInfos) {
		EntityManager em = null;
		try {
			em = this.getEmf().createEntityManager();
			Connection connection = this.getConnectionForSetup( em );

			TriggerSQLStringSource triggerSource = this.getTriggerSQLStringSource();
			try {
				for ( String str : triggerSource.getSetupCode() ) {
					Statement statement = connection.createStatement();
					LOGGER.info( str );
					statement.addBatch( connection.nativeSQL( str ) );
					statement.executeBatch();
					connection.commit();
				}
				for ( EventModelInfo info : eventModelInfos ) {
					for ( String unSetupCode : triggerSource.getSpecificUnSetupCode( info ) ) {
						Statement statement = connection.createStatement();
						LOGGER.info( unSetupCode );
						statement.addBatch( connection.nativeSQL( unSetupCode ) );
						statement.executeBatch();
						connection.commit();
					}
					for ( String setupCode : triggerSource.getSpecificSetupCode( info ) ) {
						Statement statement = connection.createStatement();
						LOGGER.info( setupCode );
						statement.addBatch( connection.nativeSQL( setupCode ) );
						statement.executeBatch();
						connection.commit();
					}
					for ( int eventType : EventType.values() ) {
						String[] triggerDropStrings = triggerSource.getTriggerDropCode( info, eventType );
						for ( String triggerCreationString : triggerDropStrings ) {
							Statement statement = connection.createStatement();
							LOGGER.info( triggerCreationString );
							statement.addBatch( connection.nativeSQL( triggerCreationString ) );
							statement.executeBatch();
							connection.commit();
						}
					}
					for ( int eventType : EventType.values() ) {
						String[] triggerCreationStrings = triggerSource.getTriggerCreationCode( info, eventType );
						for ( String triggerCreationString : triggerCreationStrings ) {
							Statement statement = connection.createStatement();
							LOGGER.info( triggerCreationString );
							statement.addBatch( connection.nativeSQL( triggerCreationString ) );
							statement.executeBatch();
							connection.commit();
						}
					}

				}
			}
			catch (SQLException e) {
				try {
					connection.rollback();
				}
				catch (SQLException e1) {
					// TODO: better Exception:
					throw new RuntimeException( e1 );
				}
				// TODO: better Exception:
				throw new RuntimeException( e );
			}
		}
		finally {
			if ( em != null && !this.isUseJTATransaction() ) {
				em.close();
			}
		}
	}

	public void shutdown() {
		try {
			this.updateSource.stop();
			this.close();
		}
		catch (IOException e) {
			throw new RuntimeException( e );
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
