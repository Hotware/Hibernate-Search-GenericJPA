/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.jpa;

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

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.db.events.EventModelInfo.IdInfo;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;
import org.hibernate.search.genericjpa.jpa.util.MultiQueryAccess;
import org.hibernate.search.genericjpa.jpa.util.MultiQueryAccess.ObjectClassWrapper;

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
	private boolean createdOwnExecutorService = false;
	private final boolean useJTATransaction;

	/**
	 * this doesn't do real batching for the databasequeries
	 */
	public JPAUpdateSource(List<EventModelInfo> eventModelInfos, EntityManagerFactory emf, boolean useJTATransaction, long timeOut, TimeUnit timeUnit,
			int batchSizeForUpdates) {
		this( eventModelInfos, emf, useJTATransaction, timeOut, timeUnit, batchSizeForUpdates, Executors.newScheduledThreadPool( 1 ) );
		this.createdOwnExecutorService = true;
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
		this.createdOwnExecutorService = true;
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
		if(exec == null) {
			throw new IllegalArgumentException("the ScheduledExecutorService may not be null!");
		}
		this.exec = exec;
		this.useJTATransaction = useJTATransaction;
	}

	@Override
	public void setUpdateConsumers(List<UpdateConsumer> updateConsumers) {
		this.updateConsumers = updateConsumers;
	}

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
					em = this.emf.createEntityManager();
					JPATransactionWrapper tx = JPATransactionWrapper.get( em, useJTATransaction );
					tx.begin();
					try {
						MultiQueryAccess query = query( this, em );
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
						}
		
						em.flush();
						// clear memory :)
						em.clear();
			
						tx.commit();
					}
					catch(Throwable e) {
						tx.rollback();
						throw e;
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

	public static MultiQueryAccess query(JPAUpdateSource updateSource, EntityManager em) {
		Map<Class<?>, Long> countMap = new HashMap<>();
		Map<Class<?>, Query> queryMap = new HashMap<>();
		for ( EventModelInfo evi : updateSource.eventModelInfos ) {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			long count;
			{
				CriteriaQuery<Long> countQuery = cb.createQuery( Long.class );
				countQuery.select( cb.count( countQuery.from( evi.getUpdateClass() ) ) );
				count = em.createQuery( countQuery ).getSingleResult();
			}
			countMap.put( evi.getUpdateClass(), count );

			{
				Query query = em.createQuery( new StringBuilder().append( "SELECT obj FROM " ).append( evi.getUpdateClass().getName() )
						.append( " obj ORDER BY obj.id" ).toString() );
				queryMap.put( evi.getUpdateClass(), query );
			}
		}
		MultiQueryAccess access = new MultiQueryAccess( countMap, queryMap, (first, second) -> {
			int res = Long.compare( updateSource.id( first ), updateSource.id( second ) );
			if ( res == 0 ) {
				throw new IllegalStateException( "database contained two update entries with the same id!" );
			}
			return res;
		}, updateSource.batchSizeForDatabaseQueries );
		return access;
	}

	private Long id(ObjectClassWrapper val) {
		return ( (Number) this.idAccessorMap.get( val.clazz ).apply( val.object ) ).longValue();
	}

	@Override
	public void stop() {
		if ( this.createdOwnExecutorService && this.exec != null ) {
			this.exec.shutdown();
		}
	}

}
