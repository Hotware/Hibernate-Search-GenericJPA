/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing.impl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;

import org.h2.mvstore.ConcurrentArrayList;
import org.hibernate.search.genericjpa.batchindexing.MassIndexer;
import org.hibernate.search.genericjpa.db.events.IndexUpdater;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.exception.AssertionFailure;
import org.hibernate.search.genericjpa.exception.SearchException;

/**
 * @author Martin Braun
 */
public class MassIndexerImpl implements MassIndexer, UpdateConsumer {

	private final IndexUpdater indexUpdater;
	private final List<Class<?>> rootEntities;
	private final boolean useUserTransaction;
	private final EntityManagerFactory emf;

	private ExecutorService executorServiceForIds;
	private ExecutorService executorServiceForObjects;

	private boolean purgeAllOnStart = true;
	private boolean optimizeAfterPurge = true;
	private boolean optimizeOnFinish = true;

	private int batchSizeToLoadIds = 100;
	private int batchSizeToLoadObjects = 10;
	private int threadsToLoadIds = 2;
	private int threadsToLoadObjects = 4;
	private int createNewIdEntityManagerAfter = 1000;

	private boolean started = false;
	private Map<Class<?>, String> idProperties;

	private ConcurrentLinkedQueue<EntityManager> entityManagers = new ConcurrentLinkedQueue<>();

	private final ConcurrentArrayList<Future<?>> futures = new ConcurrentArrayList<>();

	public MassIndexerImpl(EntityManagerFactory emf, IndexUpdater indexUpdater, List<Class<?>> rootEntities, boolean useUserTransaction) {
		this.emf = emf;
		this.indexUpdater = indexUpdater;
		this.rootEntities = rootEntities;
		this.useUserTransaction = useUserTransaction;
	}

	@Override
	public MassIndexer purgeAllOnStart(boolean purgeAllOnStart) {
		this.purgeAllOnStart = purgeAllOnStart;
		return this;
	}

	@Override
	public MassIndexer optimizeAfterPurge(boolean optimizeAfterPurge) {
		this.optimizeAfterPurge = optimizeAfterPurge;
		return this;
	}

	@Override
	public MassIndexer optimizeOnFinish(boolean optimizeOnFinish) {
		this.optimizeOnFinish = optimizeOnFinish;
		return this;
	}

	@Override
	public MassIndexer batchSizeToLoadIds(int batchSizeToLoadIds) {
		if ( batchSizeToLoadIds <= 0 ) {
			throw new IllegalArgumentException( "batchSizeToLoadIds may not be null!" );
		}
		this.batchSizeToLoadIds = batchSizeToLoadIds;
		return this;
	}

	@Override
	public MassIndexer batchSizeToLoadObjects(int batchSizeToLoadObjects) {
		if ( batchSizeToLoadObjects <= 0 ) {
			throw new IllegalArgumentException( "batchSizeToLoadObjects may not be null!" );
		}
		this.batchSizeToLoadObjects = batchSizeToLoadObjects;
		return this;
	}

	@Override
	public MassIndexer threadsToLoadIds(int threadsToLoadIds) {
		this.threadsToLoadIds = threadsToLoadIds;
		return this;
	}

	@Override
	public MassIndexer threadsToLoadObjects(int threadsToLoadObjects) {
		this.threadsToLoadObjects = threadsToLoadObjects;
		return this;
	}

	@Override
	public MassIndexer executorService(ExecutorService executorService) {
		if ( this.executorServiceForIds != null || this.executorServiceForObjects != null ) {
			throw new IllegalStateException( "already started!" );
		}
		this.executorServiceForIds = executorService;
		this.executorServiceForObjects = executorService;
		return this;
	}

	@Override
	public MassIndexer createNewIdEntityManagerAfter(int createNewIdEntityManagerAfter) {
		this.createNewIdEntityManagerAfter = createNewIdEntityManagerAfter;
		return this;
	}

	@Override
	public Future<?> start() {
		if ( this.started ) {
			throw new AssertionFailure( "already started this instance of MassIndexer once!" );
		}
		this.started = true;
		if ( this.executorServiceForIds != null ) {
			this.executorServiceForIds = Executors.newFixedThreadPool( this.threadsToLoadIds );
		}
		if ( this.executorServiceForObjects != null ) {
			this.executorServiceForObjects = Executors.newFixedThreadPool( this.threadsToLoadObjects );
		}
		this.idProperties = this.getIdProperties( this.rootEntities );
		for ( Class<?> rootClass : this.rootEntities ) {
			IdProducerTask idProducer = new IdProducerTask( rootClass, this.idProperties.get( rootClass ), emf, useUserTransaction, batchSizeToLoadIds,
					batchSizeToLoadObjects, this.createNewIdEntityManagerAfter, this );
			this.executorServiceForIds.submit( idProducer );
		}
		return new Future<Void>() {

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				boolean ret = false;
				Iterator<Future<?>> it = MassIndexerImpl.this.futures.iterator();
				while ( it.hasNext() ) {
					ret |= it.next().cancel( mayInterruptIfRunning );
				}
				return ret;
			}

			@Override
			public boolean isCancelled() {
				boolean ret = false;
				Iterator<Future<?>> it = MassIndexerImpl.this.futures.iterator();
				while ( it.hasNext() ) {
					ret |= it.next().isCancelled();
				}
				return ret;
			}

			@Override
			public boolean isDone() {
				boolean ret = false;
				Iterator<Future<?>> it = MassIndexerImpl.this.futures.iterator();
				while ( it.hasNext() ) {
					ret |= it.next().isDone();
				}
				return ret;
			}

			@Override
			public Void get() throws InterruptedException, ExecutionException {
				Iterator<Future<?>> it = MassIndexerImpl.this.futures.iterator();
				while ( it.hasNext() ) {
					it.next().get();
				}
				return null;
			}

			@Override
			public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				Iterator<Future<?>> it = MassIndexerImpl.this.futures.iterator();
				while ( it.hasNext() ) {
					it.next().get( timeout, unit );
				}
				return null;
			}

		};
	}

	@Override
	public void updateEvent(List<UpdateInfo> updateInfo) {
		Class<?> entityClass = updateInfo.get( 0 ).getEntityClass();
		ObjectHandlerTask task = new ObjectHandlerTaskImpl( this.indexUpdater, entityClass, this.getEntityManager(), this.useUserTransaction,
				this.idProperties, this::disposeEntityManager, this.emf.getPersistenceUnitUtil() );
		task.batch( updateInfo );
		this.executorServiceForObjects.submit( task );
	}

	private EntityManager getEntityManager() {
		EntityManager em = this.entityManagers.poll();
		if ( em == null ) {
			this.emf.createEntityManager();
		}
		return em;
	}

	private void disposeEntityManager(EntityManager em) {
		this.entityManagers.add( em );
	}

	private void closeAllOpenEntityManagers() {
		while ( this.entityManagers.size() > 0 ) {
			this.entityManagers.remove().close();
		}
	}

	@Override
	public void startAndWait() throws InterruptedException {
		try {
			this.start().get();
		}
		catch (ExecutionException e) {
			throw new SearchException( e );
		}
	}

	public Map<Class<?>, String> getIdProperties(List<Class<?>> entityClasses) {
		Map<Class<?>, String> ret = new HashMap<>( entityClasses.size() );
		for ( Class<?> entityClass : entityClasses ) {
			ret.put( entityClass, this.getIdProperty( entityClass ) );
		}
		return ret;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public String getIdProperty(Class<?> entityClass) {
		String idProperty = null;
		Metamodel metamodel = this.emf.getMetamodel();
		EntityType entity = metamodel.entity( entityClass );
		Set<SingularAttribute> singularAttributes = entity.getSingularAttributes();
		for ( SingularAttribute singularAttribute : singularAttributes ) {
			if ( singularAttribute.isId() ) {
				idProperty = singularAttribute.getName();
				break;
			}
		}
		if ( idProperty == null ) {
			throw new SearchException( "id field not found for: " + entityClass );
		}
		return idProperty;
	}

}
