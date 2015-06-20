/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.batchindexing.impl;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
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

import javax.persistence.EntityManagerFactory;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.Metamodel;
import javax.persistence.metamodel.SingularAttribute;

import org.hibernate.search.backend.OptimizeLuceneWork;
import org.hibernate.search.backend.PurgeAllLuceneWork;
import org.hibernate.search.backend.impl.batch.DefaultBatchBackend;
import org.hibernate.search.backend.spi.BatchBackend;
import org.hibernate.search.engine.integration.impl.ExtendedSearchIntegrator;
import org.hibernate.search.genericjpa.batchindexing.MassIndexer;
import org.hibernate.search.genericjpa.batchindexing.MassIndexerProgressMonitor;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.entity.EntityManagerEntityProvider;
import org.hibernate.search.genericjpa.entity.EntityProvider;
import org.hibernate.search.genericjpa.entity.TransactionWrappedEntityManagerEntityProvider;
import org.hibernate.search.genericjpa.exception.AssertionFailure;
import org.hibernate.search.genericjpa.exception.SearchException;

/**
 * @author Martin Braun
 */
public class MassIndexerImpl implements MassIndexer, UpdateConsumer {

	private static final Logger LOGGER = Logger.getLogger( MassIndexerImpl.class.getName() );

	private BatchBackend batchBackend;
	private final ExtendedSearchIntegrator searchIntegrator;

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

	private boolean createdOwnExecutorServiceForIds = false;
	private boolean createdOwnExecutorServiceForObjects = false;

	private boolean started = false;
	private Map<Class<?>, String> idProperties;

	private Future<Void> future;

	private ConcurrentLinkedQueue<EntityManagerEntityProvider> entityProviders = new ConcurrentLinkedQueue<>();

	private MassIndexerProgressMonitor progressMonitor;
	private final ConcurrentHashMap<Class<?>, AtomicInteger> idProgress = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Class<?>, AtomicInteger> objectLoadedProgress = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Class<?>, AtomicInteger> documentBuiltProgress = new ConcurrentHashMap<>();
	private final AtomicInteger documentsAdded = new AtomicInteger();

	/**
	 * used to wait for finishing the indexing process
	 */
	private final Map<Class<?>, NumberCondition> finishConditions = new HashMap<>();
	private final ConcurrentLinkedQueue<Future<?>> idProducerFutures = new ConcurrentLinkedQueue<>();

	/**
	 * this latch is used to wait for the cleanup thread to finish.
	 */
	private CountDownLatch cleanUpLatch;

	/**
	 * this is needed so we don't flood the executors for object handling. we store the amount of currently submitted
	 * ObjectHandlerTasks in here
	 */
	private NumberCondition objectHandlerTaskCondition;

	/**
	 * lock to guard the cancelled variable. the cancel method of our future has the write lock while all the others are
	 * "readers". -> cancel is is more important.
	 */
	private final ReadWriteLock cancelGuard = new ReentrantReadWriteLock();
	private boolean cancelled = false;

	private EntityProvider userSpecifiedEntityProvider;

	public MassIndexerImpl(EntityManagerFactory emf, ExtendedSearchIntegrator searchIntegrator, List<Class<?>> rootTypes, boolean useUserTransaction) {
		this.emf = emf;
		this.searchIntegrator = searchIntegrator;
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
	public Future<?> start() {
		if ( this.started ) {
			throw new AssertionFailure( "already started this instance of MassIndexer once!" );
		}
		this.batchBackend = new DefaultBatchBackend( this.searchIntegrator, new org.hibernate.search.batchindexing.MassIndexerProgressMonitor() {

			@Override
			public void documentsAdded(long increment) {
				// hacky: whatever...
				int count = MassIndexerImpl.this.documentsAdded.addAndGet( (int) increment );
				if ( MassIndexerImpl.this.progressMonitor != null ) {
					MassIndexerImpl.this.progressMonitor.documentsAdded( count );
				}
			}

			@Override
			public void indexingCompleted() {

			}

			@Override
			public void entitiesLoaded(int size) {

			}

			@Override
			public void documentsBuilt(int number) {

			}

			@Override
			public void addToTotalCount(long count) {

			}

		} );
		this.started = true;
		this.cleanUpLatch = new CountDownLatch( this.optimizeOnFinish ? 2 : 1 );
		if ( this.executorServiceForIds == null ) {
			if ( this.useUserTransaction ) {
				throw new SearchException( "MassIndexer cannot create own threads if it has to use a UserTransaction!" );
			}
			this.executorServiceForIds = Executors.newFixedThreadPool( this.threadsToLoadIds, new NamingThreadFactory( "MassIndexer Id Loader Thread" ) );
			this.createdOwnExecutorServiceForIds = true;
		}
		if ( this.executorServiceForObjects == null ) {
			if ( this.useUserTransaction ) {
				throw new SearchException( "MassIndexer cannot create own threads if it has to use a UserTransaction!" );
			}
			this.executorServiceForObjects = Executors.newFixedThreadPool( this.threadsToLoadObjects, new NamingThreadFactory(
					"MassIndexer Object Loader Thread" ) );
			this.createdOwnExecutorServiceForObjects = true;
		}
		if ( this.threadsToLoadObjects > 0 ) {
			this.objectHandlerTaskCondition = new NumberCondition( this.threadsToLoadObjects * 4 );
		}
		else {
			this.objectHandlerTaskCondition = new NumberCondition( 1000 );
		}
		this.idProperties = this.getIdProperties( this.rootTypes );
		for ( Class<?> rootClass : this.rootTypes ) {
			try {
				if ( this.purgeAllOnStart ) {
					this.batchBackend.enqueueAsyncWork( new PurgeAllLuceneWork( rootClass ) );
					if ( this.optimizeAfterPurge ) {
						this.batchBackend.enqueueAsyncWork( new OptimizeLuceneWork( rootClass ) );
					}
					this.batchBackend.flush( new HashSet<>( this.rootTypes ) );
				}
			}
			catch (Exception e) {
				throw new SearchException( e );
			}

			this.finishConditions.put( rootClass, new NumberCondition( 0, 0, false ) );
			IdProducerTask idProducer = new IdProducerTask( rootClass, this.idProperties.get( rootClass ), this.emf, this.useUserTransaction,
					this.batchSizeToLoadIds, this.batchSizeToLoadObjects, this, this.purgeAllOnStart, this.optimizeAfterPurge, this::onException,
					this.finishConditions.get( rootClass ) );
			idProducer.progressMonitor( this::idProgress );
			this.idProducerFutures.add( this.executorServiceForIds.submit( idProducer ) );
		}
		this.future = this.getFuture();
		new Thread( "MassIndexer Cleanup/Finisher Thread" ) {

			@Override
			public void run() {
				try {
					MassIndexerImpl.this.awaitJobsFinish();
					if ( MassIndexerImpl.this.optimizeOnFinish ) {
						for ( Class<?> rootEntity : MassIndexerImpl.this.rootTypes ) {
							try {
								MassIndexerImpl.this.batchBackend.enqueueAsyncWork( new OptimizeLuceneWork( rootEntity ) );
							}
							catch (InterruptedException e) {
								LOGGER.log( Level.WARNING, "interrupted while optimizing on finish!", e );
							}
						}
						MassIndexerImpl.this.cleanUpLatch.countDown();
					}

					// flush all the works that are left in the queue
					MassIndexerImpl.this.batchBackend.flush( new HashSet<>( MassIndexerImpl.this.rootTypes ) );

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

				MassIndexerImpl.this.objectHandlerTaskCondition.disable();

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
				for ( NumberCondition condition : MassIndexerImpl.this.finishConditions.values() ) {
					condition.disable();
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
				for ( NumberCondition condition : MassIndexerImpl.this.finishConditions.values() ) {
					// FIXME: not quite right...
					if ( !condition.check( timeout, unit ) ) {
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
		for ( NumberCondition condition : MassIndexerImpl.this.finishConditions.values() ) {
			condition.check();
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
			try {
				// check if we should wait with submitting
				this.objectHandlerTaskCondition.check();
			}
			catch (InterruptedException e) {
				this.onException( e );
			}
			Class<?> entityClass = updateInfo.get( 0 ).getEntityClass();
			ObjectHandlerTask task = new ObjectHandlerTask( this.batchBackend, entityClass, this.searchIntegrator.getIndexBinding( entityClass ),
					this::getEntityProvider, this::disposeEntityManager, this.emf.getPersistenceUnitUtil(), this.finishConditions.get( entityClass ),
					this::onException );
			task.batch( updateInfo );
			task.documentBuiltProgressMonitor( this::documentBuiltProgress );
			task.objectLoadedProgressMonitor( this::objectLoadedProgress );
			this.objectHandlerTaskCondition.up( 1 );
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

	private void documentBuiltProgress(Class<?> entityType, Integer count) {
		int newCount = this.documentBuiltProgress.computeIfAbsent( entityType, (type) -> {
			return new AtomicInteger( 0 );
		} ).addAndGet( count );
		if ( this.progressMonitor != null ) {
			this.progressMonitor.documentsBuilt( entityType, newCount );
		}
	}

	private boolean isFinished() {
		boolean ret = true;
		for ( NumberCondition numberCondition : this.finishConditions.values() ) {
			try {
				ret &= numberCondition.check( 1, TimeUnit.NANOSECONDS );
			}
			catch (InterruptedException e) {
				throw new SearchException( e );
			}
		}
		return ret;
	}

	private EntityProvider getEntityProvider() {
		if ( this.userSpecifiedEntityProvider == null ) {
			EntityManagerEntityProvider em = this.entityProviders.poll();
			if ( em == null ) {
				em = new TransactionWrappedEntityManagerEntityProvider( this.emf.createEntityManager(), this.idProperties, this.useUserTransaction );
			}
			return em;
		}
		return this.userSpecifiedEntityProvider;
	}

	private void disposeEntityManager(ObjectHandlerTask task, EntityProvider provider) {
		if ( this.userSpecifiedEntityProvider == null ) {
			( (TransactionWrappedEntityManagerEntityProvider) provider ).clearEm();
			this.entityProviders.add( (EntityManagerEntityProvider) provider );
		}
		this.objectHandlerTaskCondition.down( 1 );
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
			try {
				this.entityProviders.remove().close();
			}
			catch (IOException e) {
				LOGGER.log( Level.WARNING, "Exception while closing EntityManagers", e );
			}
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

}
