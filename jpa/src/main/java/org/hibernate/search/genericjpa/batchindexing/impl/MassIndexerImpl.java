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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;

import org.hibernate.search.genericjpa.batchindexing.MassIndexer;
import org.hibernate.search.genericjpa.batchindexing.MassIndexerProgressMonitor;
import org.hibernate.search.genericjpa.db.events.IndexUpdater;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.entity.JPAReusableEntityProvider;
import org.hibernate.search.genericjpa.entity.EntityProvider;
import org.hibernate.search.genericjpa.exception.AssertionFailure;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.factory.StandaloneSearchFactory;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

/**
 * @author Martin Braun
 */
public class MassIndexerImpl implements MassIndexer, UpdateConsumer {

	private static final Logger LOGGER = Logger.getLogger( MassIndexerImpl.class.getName() );

	private final IndexUpdater indexUpdater;
	private final List<Class<?>> rootTypes;
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

	private boolean createdOwnExecutorServiceForIds = false;
	private boolean createdOwnExecutorServiceForObjects = false;

	private boolean started = false;
	private Map<Class<?>, String> idProperties;

	private Future<Void> future;

	private ConcurrentLinkedQueue<JPAReusableEntityProvider> entityProviders = new ConcurrentLinkedQueue<>();

	private MassIndexerProgressMonitor progressMonitor;
	private ConcurrentHashMap<Class<?>, AtomicInteger> idProgress = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Class<?>, AtomicInteger> objectLoadedProgress = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Class<?>, AtomicInteger> indexedProgress = new ConcurrentHashMap<>();

	private final Map<Class<?>, CountDownLatch> latches = new HashMap<>();
	private final ConcurrentLinkedQueue<Future<?>> idProducerFutures = new ConcurrentLinkedQueue<>();
	private CountDownLatch cleanUpLatch;

	private final ReadWriteLock cancelGuard = new ReentrantReadWriteLock();
	private boolean cancelled = false;

	private final StandaloneSearchFactory searchFactory;

	private EntityProvider userSpecifiedEntityProvider;

	public MassIndexerImpl(EntityManagerFactory emf, StandaloneSearchFactory searchFactory, IndexUpdater indexUpdater, List<Class<?>> rootTypes,
			boolean useUserTransaction) {
		this.emf = emf;
		this.searchFactory = searchFactory;
		this.indexUpdater = indexUpdater;
		this.rootTypes = rootTypes;
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
		if ( this.future != null ) {
			throw new IllegalStateException( "already started!" );
		}
		this.executorServiceForIds = executorService;
		this.executorServiceForObjects = executorService;
		return this;
	}

	@Override
	public MassIndexer executorServiceForIds(ExecutorService executorServiceForIds) {
		if ( this.future != null ) {
			throw new IllegalStateException( "already started!" );
		}
		this.executorServiceForIds = executorServiceForIds;
		return this;
	}

	@Override
	public MassIndexer executorServiceForObjects(ExecutorService executorServiceForObjects) {
		if ( this.future != null ) {
			throw new IllegalStateException( "already started!" );
		}
		this.executorServiceForObjects = executorServiceForObjects;
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
		this.cleanUpLatch = new CountDownLatch( this.optimizeOnFinish ? 2 : 1 );
		if ( this.executorServiceForIds == null ) {
			if ( this.useUserTransaction ) {
				throw new SearchException( "MassIndexer cannot create own threads if it has to use a UserTransaction!" );
			}
			this.executorServiceForIds = Executors.newFixedThreadPool( this.threadsToLoadIds );
			this.createdOwnExecutorServiceForIds = true;
		}
		if ( this.executorServiceForObjects == null ) {
			if ( this.useUserTransaction ) {
				throw new SearchException( "MassIndexer cannot create own threads if it has to use a UserTransaction!" );
			}
			this.executorServiceForObjects = Executors.newFixedThreadPool( this.threadsToLoadObjects );
			this.createdOwnExecutorServiceForObjects = true;
		}
		this.idProperties = this.getIdProperties( this.rootTypes );
		for ( Class<?> rootClass : this.rootTypes ) {
			if ( this.purgeAllOnStart ) {
				this.searchFactory.purgeAll( rootClass );
				if ( this.optimizeAfterPurge ) {
					this.searchFactory.optimize( rootClass );
				}
			}
			long totalCount = this.getTotalCount( rootClass );
			// FIXME: hacky casts...
			this.latches.put( rootClass, new CountDownLatch( (int) totalCount ) );
			long perTask = this.createNewIdEntityManagerAfter;
			long startingPosition = 0;
			while ( startingPosition < totalCount ) {
				IdProducerTask idProducer = new IdProducerTask( rootClass, this.idProperties.get( rootClass ), this.emf, this.useUserTransaction,
						this.batchSizeToLoadIds, this.batchSizeToLoadObjects, this, this.purgeAllOnStart, this.optimizeAfterPurge, this::onException );
				idProducer.count( perTask );
				idProducer.totalCount( totalCount );
				idProducer.startingPosition( startingPosition );
				idProducer.progressMonitor( this::idProgress );
				this.idProducerFutures.add( this.executorServiceForIds.submit( idProducer ) );
				startingPosition += perTask;
			}
		}
		this.future = this.getFuture();
		new Thread( "MassIndexer cleanup/finisher thread" ) {

			@Override
			public void run() {
				try {
					MassIndexerImpl.this.awaitJobsFinish();
					if ( MassIndexerImpl.this.optimizeOnFinish ) {
						for ( Class<?> rootEntity : MassIndexerImpl.this.rootTypes ) {
							MassIndexerImpl.this.searchFactory.optimize( rootEntity );
						}
						MassIndexerImpl.this.cleanUpLatch.countDown();
					}
					MassIndexerImpl.this.closeExecutorServices();
					MassIndexerImpl.this.closeAllOpenEntityManagers();
					MassIndexerImpl.this.cleanUpLatch.countDown();
				}
				catch (InterruptedException e) {
					throw new SearchException( "Error during massindexing!", e );
				}
			}

		}.start();
		return this.future;
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

	private Future<Void> getFuture() {
		return new Future<Void>() {

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				boolean ret = false;
				Iterator<Future<?>> it = MassIndexerImpl.this.idProducerFutures.iterator();
				while ( it.hasNext() ) {
					ret |= it.next().cancel( mayInterruptIfRunning );
				}
				Lock lock = MassIndexerImpl.this.cancelGuard.writeLock();
				lock.lock();
				try {
					MassIndexerImpl.this.cancelled = true;
				}
				finally {
					lock.unlock();
				}

				// FIXME: wait for all the running threads to finish up.

				// blow the signal to stop everything
				for ( CountDownLatch latch : MassIndexerImpl.this.latches.values() ) {
					while ( latch.getCount() > 0 ) {
						latch.countDown();
					}
				}

				// but we have to wait for the cleanup thread to finish up
				try {
					MassIndexerImpl.this.cleanUpLatch.await();
				}
				catch (InterruptedException e) {
					throw new SearchException( "couldn't wait for optimizeOnFinish", e );
				}
				return ret;

			}

			@Override
			public boolean isCancelled() {
				boolean ret = false;
				Iterator<Future<?>> it = MassIndexerImpl.this.idProducerFutures.iterator();
				while ( it.hasNext() ) {
					ret |= it.next().isCancelled();
				}
				return ret;
			}

			@Override
			public boolean isDone() {
				boolean ret = false;
				Iterator<Future<?>> it = MassIndexerImpl.this.idProducerFutures.iterator();
				while ( it.hasNext() ) {
					ret |= it.next().isDone();
				}
				return ret || this.isCancelled() || MassIndexerImpl.this.isFinished();
			}

			@Override
			public Void get() throws InterruptedException, ExecutionException {
				MassIndexerImpl.this.awaitJobsFinish();
				MassIndexerImpl.this.cleanUpLatch.await();
				return null;
			}

			@Override
			public Void get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				for ( CountDownLatch latch : MassIndexerImpl.this.latches.values() ) {
					// FIXME: not quite right...
					if ( !latch.await( timeout, unit ) ) {
						throw new TimeoutException();
					}
				}
				// FIXME: not quite right...
				MassIndexerImpl.this.cleanUpLatch.await( timeout, unit );
				return null;
			}

		};
	}

	private void awaitJobsFinish() throws InterruptedException {
		for ( CountDownLatch latch : MassIndexerImpl.this.latches.values() ) {
			latch.await();
		}
	}

	@Override
	public void updateEvent(List<UpdateInfo> updateInfo) {
		Lock lock = MassIndexerImpl.this.cancelGuard.readLock();
		lock.lock();
		try {
			if ( this.cancelled ) {
				return;
			}
			Class<?> entityClass = updateInfo.get( 0 ).getEntityClass();
			ObjectHandlerTask task = new ObjectHandlerTask( this.indexUpdater, entityClass, this::getEntityManager, this::disposeEntityManager,
					this.emf.getPersistenceUnitUtil(), this.latches.get( entityClass ), this::onException );
			task.batch( updateInfo );
			task.indexProgressMonitor( this::indexedProgress );
			task.objectLoadedProgressMonitor( this::objectLoadedProgress );
			this.executorServiceForObjects.submit( task );
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public MassIndexer progressMonitor(MassIndexerProgressMonitor progressMonitor) {
		this.progressMonitor = progressMonitor;
		return this;
	}

	@Override
	public MassIndexer entityProvider(EntityProvider entityProvider) {
		this.userSpecifiedEntityProvider = entityProvider;
		return this;
	}

	private void idProgress(Class<?> entityType, Integer count) {
		int newCount = this.idProgress.computeIfAbsent( entityType, (type) -> {
			return new AtomicInteger( 0 );
		} ).addAndGet( count );
		if ( this.progressMonitor != null ) {
			this.progressMonitor.idsLoaded( entityType, newCount );
		}
	}

	private void objectLoadedProgress(Class<?> entityType, Integer count) {
		int newCount = this.objectLoadedProgress.computeIfAbsent( entityType, (type) -> {
			return new AtomicInteger( 0 );
		} ).addAndGet( count );
		if ( this.progressMonitor != null ) {
			this.progressMonitor.objectsLoaded( entityType, newCount );
		}
	}

	private void indexedProgress(Class<?> entityType, Integer count) {
		int newCount = this.indexedProgress.computeIfAbsent( entityType, (type) -> {
			return new AtomicInteger( 0 );
		} ).addAndGet( count );
		if ( this.progressMonitor != null ) {
			this.progressMonitor.indexed( entityType, newCount );
		}
	}

	private boolean isFinished() {
		boolean ret = true;
		for ( CountDownLatch latch : this.latches.values() ) {
			try {
				ret &= latch.await( 1, TimeUnit.NANOSECONDS );
			}
			catch (InterruptedException e) {
				throw new SearchException( e );
			}
		}
		return ret;
	}

	private EntityProvider getEntityManager() {
		if ( this.userSpecifiedEntityProvider == null ) {
			JPAReusableEntityProvider em = this.entityProviders.poll();
			if ( em == null ) {
				em = new JPAReusableEntityProvider( this.emf, this.idProperties, this.useUserTransaction );
				em.open();
			}
			return em;
		}
		return this.userSpecifiedEntityProvider;
	}

	private void disposeEntityManager(EntityProvider provider) {
		if ( this.userSpecifiedEntityProvider == null ) {
			this.entityProviders.add( (JPAReusableEntityProvider) provider );
		}
	}

	private void onException(Exception e) {
		LOGGER.log( Level.WARNING, "Exception during indexing", e );
		this.future.cancel( true );
	}

	private void closeExecutorServices() {
		if ( this.createdOwnExecutorServiceForIds ) {
			this.executorServiceForIds.shutdown();
		}
		if ( this.createdOwnExecutorServiceForObjects ) {
			this.executorServiceForObjects.shutdown();
		}
	}

	private void closeAllOpenEntityManagers() {
		while ( this.entityProviders.size() > 0 ) {
			this.entityProviders.remove().close();
		}
	}

	private Map<Class<?>, String> getIdProperties(List<Class<?>> entityClasses) {
		Map<Class<?>, String> ret = new HashMap<>( entityClasses.size() );
		for ( Class<?> entityClass : entityClasses ) {
			ret.put( entityClass, this.getIdProperty( entityClass ) );
		}
		return ret;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private String getIdProperty(Class<?> entityClass) {
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

	public long getTotalCount(Class<?> entityClass) {
		long count = 0;
		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.useUserTransaction );
			tx.begin();
			try {
				CriteriaBuilder cb = em.getCriteriaBuilder();
				CriteriaQuery<Long> countQuery = cb.createQuery( Long.class );
				countQuery.select( cb.count( countQuery.from( entityClass ) ) );
				count = em.createQuery( countQuery ).getSingleResult();
				tx.commit();
			}
			catch (Exception e) {
				tx.rollback();
				throw new SearchException( e );
			}
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
		return count;
	}

}
