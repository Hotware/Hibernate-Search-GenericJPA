/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package com.github.hotware.hsearch.db.events.jpa;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.InitialContext;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.transaction.UserTransaction;

import com.github.hotware.hsearch.db.events.EventModelInfo;
import com.github.hotware.hsearch.db.events.UpdateConsumer;
import com.github.hotware.hsearch.db.events.UpdateConsumer.UpdateInfo;
import com.github.hotware.hsearch.db.events.UpdateSource;
import com.github.hotware.hsearch.db.events.EventModelInfo.IdInfo;
import com.github.hotware.hsearch.entity.jpa.EntityManagerCloseable;
import com.github.hotware.hsearch.jpa.util.MultiQueryAccess;
import com.github.hotware.hsearch.jpa.util.MultiQueryAccess.ObjectClassWrapper;

/**
 * a {@link UpdateSource} implementation that uses JPA to retrieve the updates from the database. For this to work the
 * entities have to be setup with JPA annotations
 *
 * @author Martin Braun
 */
public class JPAUpdateSource implements UpdateSource {

	private static final Logger LOGGER = Logger.getLogger( JPAUpdateSource.class.getName() );

	private final List<EventModelInfo> eventModelInfos;
	private final EntityManagerFactory emf;
	private final long timeOut;
	private final TimeUnit timeUnit;
	private final int batchSizeForUpdates;
	private final int batchSizeForDatabaseQueries;

	private final List<Class<?>> updateClasses;
	private final Map<Class<?>, EventModelInfo> updateClassToEventModelInfo;
	private final Map<Class<?>, Function<Object, Object>> idAccessorMap;

	private List<UpdateConsumer> updateConsumers;
	private final ScheduledExecutorService exec;
	private final boolean useJTATransaction;

	/**
	 * this doesn't do real batching for the databasequeries
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos, EntityManagerFactory emf, boolean useJTATransaction, long timeOut, TimeUnit timeUnit,
			int batchSizeForUpdates) {
		this( eventModelInfos, emf, useJTATransaction, timeOut, timeUnit, batchSizeForUpdates, Executors.newScheduledThreadPool( 1 ) );
	}

	/**
	 * this doesn't do real batching for the databasequeries
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos, EntityManagerFactory emf, boolean useJTATransaction, long timeOut, TimeUnit timeUnit,
			int batchSizeForUpdates, ScheduledExecutorService exec) {
		this( eventModelInfos, emf, useJTATransaction, timeOut, timeUnit, batchSizeForUpdates, 1, exec );
	}

	/**
	 * this does batching for databaseQueries according to what you set
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos, EntityManagerFactory emf, boolean useJTATransaction, long timeOut, TimeUnit timeUnit,
			int batchSizeForUpdates, int batchSizeForDatabaseQueries) {
		this( eventModelInfos, emf, useJTATransaction, timeOut, timeUnit, batchSizeForUpdates, batchSizeForDatabaseQueries, Executors
				.newScheduledThreadPool( 1 ) );
	}

	/**
	 * this does batching for databaseQueries according to what you set
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos, EntityManagerFactory emf, boolean useJTATransaction, long timeOut, TimeUnit timeUnit,
			int batchSizeForUpdates, int batchSizeForDatabaseQueries, ScheduledExecutorService exec) {
		this.eventModelInfos = eventModelInfos;
		this.emf = emf;
		if ( timeOut <= 0 ) {
			throw new IllegalArgumentException( "timeout must be greater than 0" );
		}
		this.timeOut = timeOut;
		this.timeUnit = timeUnit;
		if ( batchSizeForUpdates <= 0 ) {
			throw new IllegalArgumentException( "batchSize must be greater than 0" );
		}
		this.batchSizeForUpdates = batchSizeForUpdates;
		this.batchSizeForDatabaseQueries = batchSizeForDatabaseQueries;
		this.updateClasses = new ArrayList<>();
		this.updateClassToEventModelInfo = new HashMap<>();
		for ( EventModelInfo info : eventModelInfos ) {
			this.updateClasses.add( info.getUpdateClass() );
			this.updateClassToEventModelInfo.put( info.getUpdateClass(), info );
		}
		this.idAccessorMap = new HashMap<>();
		for ( EventModelInfo evi : eventModelInfos ) {
			try {
				Method idMethod = evi.getUpdateClass().getDeclaredMethod( "getId" );
				idMethod.setAccessible( true );
				idAccessorMap.put( evi.getUpdateClass(), (obj) -> {
					try {
						return idMethod.invoke( obj );
					}
					catch (Exception e) {
						throw new RuntimeException( e );
					}
				} );
			}
			catch (SecurityException | NoSuchMethodException e) {
				throw new RuntimeException( "could not access the \"getId()\" method of class: " + evi.getUpdateClass() );
			}
		}
		this.exec = exec;
		this.useJTATransaction = useJTATransaction;
	}

	/*
	 * (non-Javadoc)
	 * @see com.github.hotware.hsearch.db.events.UpdateSource#setUpdateConsumer(com
	 * .github.hotware.hsearch.db.events.UpdateConsumer)
	 */
	@Override
	public void setUpdateConsumers(List<UpdateConsumer> updateConsumers) {
		this.updateConsumers = updateConsumers;
	}

	/*
	 * (non-Javadoc)
	 * @see com.github.hotware.hsearch.db.events.UpdateSource#start()
	 */
	@Override
	public void start() {
		if ( this.updateConsumers == null ) {
			throw new IllegalStateException( "updateConsumers was null!" );
		}
		this.exec.scheduleWithFixedDelay( () -> {
			try {
				if ( !this.emf.isOpen() ) {
					return;
				}
				EntityManager em = null;
				try {
					em = new EntityManagerCloseable( this.emf.createEntityManager() );
					EntityTransaction tx;
					UserTransaction utx;
					if ( !this.useJTATransaction ) {
						tx = em.getTransaction();
						tx.begin();
						utx = null;
					}
					else {
						utx = (UserTransaction) InitialContext.doLookup( "java:comp/UserTransaction" );
						utx.begin();
						em.joinTransaction();
						tx = null;
					}
					MultiQueryAccess query = this.query( em );
					List<Object[]> toRemove = new ArrayList<>( this.batchSizeForUpdates );
					List<UpdateInfo> updateInfos = new ArrayList<>( this.batchSizeForUpdates );
					long processed = 0;
					while ( query.next() ) {
						// we have no order problems here since
						// the query does
						// the ordering for us
				Object val = query.get();
				toRemove.add( new Object[] { query.entityClass(), val } );
				EventModelInfo evi = this.updateClassToEventModelInfo.get( query.entityClass() );
				for ( IdInfo info : evi.getIdInfos() ) {
					updateInfos.add( new UpdateInfo( info.getEntityClass(), info.getIdAccessor().apply( val ), evi.getEventTypeAccessor().apply( val ) ) );
				}
				// TODO: maybe move this to a method as
				// it is getting reused
				if ( ++processed % this.batchSizeForUpdates == 0 ) {
					for ( UpdateConsumer consumer : this.updateConsumers ) {
						consumer.updateEvent( updateInfos );
					}
					for ( Object[] rem : toRemove ) {
						// the class is in rem[0], the
						// entity is in
						// rem[1]
						query.addToNextValuePosition( (Class<?>) rem[0], -1L );
						em.remove( rem[1] );
					}
					toRemove.clear();
					updateInfos.clear();
					em.flush();
					// clear memory :)
					em.clear();
				}
			}
			if ( updateInfos.size() > 0 ) {
				for ( UpdateConsumer consumer : this.updateConsumers ) {
					consumer.updateEvent( updateInfos );
				}
				for ( Object[] rem : toRemove ) {
					// the class is in rem[0], the
					// entity is in rem[1]
					query.addToNextValuePosition( (Class<?>) rem[0], -1L );
					em.remove( rem[1] );
				}
				toRemove.clear();
				updateInfos.clear();
				em.flush();
				// clear memory :)
				em.clear();
			}

			if ( !this.useJTATransaction ) {
				tx.commit();
			}
			else {
				utx.commit();
			}
		}
		catch (Exception e) {
			throw new RuntimeException( "Error occured during Update processing!", e );
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
	}
	catch (Exception e) {
		LOGGER.log( Level.SEVERE, e.getMessage(), e );
	}
}, 0, this.timeOut, this.timeUnit );
	}

	MultiQueryAccess query(EntityManager em) {
		Map<Class<?>, Long> countMap = new HashMap<>();
		Map<Class<?>, Query> queryMap = new HashMap<>();
		for ( EventModelInfo evi : this.eventModelInfos ) {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			long count;
			{
				CriteriaQuery<Long> countQuery = cb.createQuery( Long.class );
				countQuery.select( cb.count( countQuery.from( evi.getUpdateClass() ) ) );
				count = em.createQuery( countQuery ).getSingleResult();
			}
			countMap.put( evi.getUpdateClass(), count );

			{
				CriteriaQuery<?> q = cb.createQuery( evi.getUpdateClass() );
				Root<?> ent = q.from( evi.getUpdateClass() );
				q = q.orderBy( cb.asc( ent.get( "id" ) ) );
				TypedQuery<?> query = em.createQuery( q.multiselect( ent ) );
				queryMap.put( evi.getUpdateClass(), query );
			}
		}
		MultiQueryAccess access = new MultiQueryAccess( countMap, queryMap, (first, second) -> {
			int res = Long.compare( this.id( first ), this.id( second ) );
			if ( res == 0 ) {
				throw new IllegalStateException( "database contained two update entries with the same id!" );
			}
			return res;
		}, this.batchSizeForDatabaseQueries );
		return access;
	}

	private Long id(ObjectClassWrapper val) {
		return ( (Number) this.idAccessorMap.get( val.clazz ).apply( val.object ) ).longValue();
	}

	/*
	 * (non-Javadoc)
	 * @see com.github.hotware.hsearch.db.events.UpdateSource#stop()
	 */
	@Override
	public void stop() {
		if ( this.exec != null ) {
			this.exec.shutdown();
		}
	}

}
