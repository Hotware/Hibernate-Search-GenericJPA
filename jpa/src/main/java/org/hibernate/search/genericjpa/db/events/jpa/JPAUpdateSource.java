/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hibernate.search.genericjpa.annotations.IdInfo;
import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateEventInfo;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;
import org.hibernate.search.genericjpa.jpa.util.MultiQueryAccess;
import org.hibernate.search.genericjpa.jpa.util.MultiQueryAccess.ObjectIdentifierWrapper;
import org.hibernate.search.genericjpa.util.NamingThreadFactory;

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

	//we store the order of the data fields mapped to the
	//table name in here
	private final Map<String, List<String>> dataFields;

	private final Map<String, EventModelInfo> updateTableToEventModelInfo;
	private final ScheduledExecutorService exec;
	private final TransactionManager transactionManager;
	private final ReentrantLock lock = new ReentrantLock();
	private List<UpdateConsumer> updateConsumers;
	private ScheduledFuture<?> job;
	private boolean cancelled = false;
	private boolean pause = false;

	/**
	 * this doesn't do real batching for the databasequeries
	 */
	public JPAUpdateSource(
			List<EventModelInfo> eventModelInfos,
			EntityManagerFactory emf,
			TransactionManager transactionManager,
			long timeOut,
			TimeUnit timeUnit,
			int batchSizeForUpdates) {
		this(
				eventModelInfos,
				emf,
				transactionManager,
				timeOut,
				timeUnit,
				batchSizeForUpdates,
				1,
				Executors.newSingleThreadScheduledExecutor( tf() )
		);
	}

	/**
	 * this does batching for databaseQueries according to what you set
	 */
	public JPAUpdateSource(
			List<EventModelInfo> eventModelInfos,
			EntityManagerFactory emf,
			TransactionManager transactionManager,
			long timeOut,
			TimeUnit timeUnit,
			int batchSizeForUpdates,
			int batchSizeForDatabaseQueries) {
		this(
				eventModelInfos,
				emf,
				transactionManager,
				timeOut,
				timeUnit,
				batchSizeForUpdates,
				batchSizeForDatabaseQueries,
				Executors
						.newSingleThreadScheduledExecutor( tf() )
		);
	}

	/**
	 * this does batching for databaseQueries according to what you set
	 */
	public JPAUpdateSource(
			List<EventModelInfo> eventModelInfos,
			EntityManagerFactory emf,
			TransactionManager transactionManager,
			long timeOut,
			TimeUnit timeUnit,
			int batchSizeForUpdates,
			int batchSizeForDatabaseQueries,
			ScheduledExecutorService exec) {
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
		this.updateTableToEventModelInfo = new HashMap<>();
		for ( EventModelInfo info : eventModelInfos ) {
			this.updateTableToEventModelInfo.put( info.getUpdateTableName(), info );
		}
		if ( exec == null ) {
			throw new IllegalArgumentException( "the ScheduledExecutorService may not be null!" );
		}
		this.exec = exec;
		this.transactionManager = transactionManager;

		{
			final Map<String, List<String>> dataFields = new HashMap<>();
			for ( EventModelInfo evi : eventModelInfos ) {
				String tableName = evi.getUpdateTableName();
				dataFields.put( tableName, new ArrayList<>() );
				for ( EventModelInfo.IdInfo idInfo : evi.getIdInfos() ) {
					dataFields.get(tableName).add(idInfo.getColumnsInUpdateTable());
				}
			}
			this.dataFields = dataFields;
		}
	}

	private static ThreadFactory tf() {
		return new NamingThreadFactory( "JPAUpdateSource Thread" );
	}

	public static MultiQueryAccess query(
			JPAUpdateSource updateSource,
			EntityManager em) {
		Map<String, Long> countMap = new HashMap<>();
		Map<String, Query> queryMap = new HashMap<>();
		for ( EventModelInfo evi : updateSource.eventModelInfos ) {
			CriteriaBuilder cb = em.getCriteriaBuilder();
			long count;
			{
				count = ((Number) ((Object[]) em.createNativeQuery( "SELECT count(*) FROM " + evi.getUpdateTableName() )
						.getSingleResult())[0]).longValue();
			}
			countMap.put( evi.getUpdateTableName(), count );

			{
				StringBuilder queryString = new StringBuilder().append( "SELECT " )
						.append( evi.getUpdateIdColumn() )
						.append( ", " )
						.append( evi.getEventTypeColumn() );
				List<String> dataFieldsCurUpdateTable = updateSource.dataFields.get( evi.getUpdateTableName() );
				for ( String field : dataFieldsCurUpdateTable ) {
					queryString.append( ", " ).append( field );
				}
				queryString.append( " FROM " ).append( evi.getUpdateTableName() ).append(
						" ORDER BY "
				).append( evi.getUpdateIdColumn() );

				Query query = em.createQuery(
						queryString.toString()
				);
				queryMap.put( evi.getUpdateTableName(), query );
			}
		}
		return new MultiQueryAccess(
				countMap, queryMap, (first, second) -> {
			int res = Long.compare( updateSource.id( first ), updateSource.id( second ) );
			if ( res == 0 ) {
				throw new IllegalStateException( "database contained two update entries with the same id!" );
			}
			return res;
		}, updateSource.batchSizeForDatabaseQueries
		);
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
		this.cancelled = false;
		this.job = this.exec.scheduleWithFixedDelay(
				() -> {
					this.lock.lock();
					try {
						if ( this.pause ) {
							return;
						}
						if ( this.cancelled ) {
							return;
						}
						if ( !this.emf.isOpen() ) {
							return;
						}
						EntityManager em = null;
						try {
							em = this.emf.createEntityManager();
							JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.transactionManager );
							tx.begin();
							try {
								MultiQueryAccess query = query( this, em );
								List<Object[]> toRemove = new ArrayList<>( this.batchSizeForUpdates );
								List<UpdateEventInfo> updateInfos = new ArrayList<>( this.batchSizeForUpdates );
								long processed = 0;
								while ( query.next() ) {
									// we have no order problems here since
									// the query does the ordering for us
									Object[] values = (Object[]) query.get();
									toRemove.add( new Object[] {query.identifier(), values[0]} );
									EventModelInfo evi = this.updateTableToEventModelInfo.get( query.identifier() );
									evi.getIdInfos()
											.forEach(
													(info) -> {
														info.get
														updateInfos.add(
																new UpdateEventInfo(
																		info.getEntityClass(),
																		info.getIdAccessor()
																				.apply( val ),
																		evi.getEventTypeAccessor()
																				.apply( val ),
																		info.getHints()
																)
														);
													}
											);
									// TODO: maybe move this to a method as
									// it is getting reused
									if ( ++processed % this.batchSizeForUpdates == 0 ) {
										for ( UpdateConsumer consumer : this.updateConsumers ) {
											consumer.updateEvent( updateInfos );
											LOGGER.fine( "handled update-event: " + updateInfos );
										}
										for ( Object[] rem : toRemove ) {
											// the class is in rem[0], the
											// entity is in
											// rem[1]
											query.addToNextValuePosition( rem[0].toString(), -1L );
											em.remove( rem[1] );
										}
										toRemove.clear();
										updateInfos.clear();
									}
								}
								if ( updateInfos.size() > 0 ) {
									for ( UpdateConsumer consumer : this.updateConsumers ) {
										consumer.updateEvent( updateInfos );
										LOGGER.fine( "handled update-event: " + updateInfos );
									}
									for ( Object[] rem : toRemove ) {
										// the class is in rem[0], the
										// entity is in rem[1]
										query.addToNextValuePosition( rem[0].toString(), -1L );
										em.remove( rem[1] );
									}
									toRemove.clear();
									updateInfos.clear();
								}

								if ( processed > 0 ) {
									LOGGER.info( "processed " + processed + " updates" );
								}

								em.flush();
								// clear memory :)
								em.clear();

								tx.commit();
							}
							catch (Throwable e) {
								tx.rollback();
								throw e;
							}
						}
						catch (Exception e) {
							throw new SearchException( "Error occured during Update processing!", e );
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
					finally {
						this.lock.unlock();
					}
				}, 0, this.timeOut, this.timeUnit
		);
	}

	private Long id(ObjectIdentifierWrapper val) {
		return ((Number) ((Object[]) val.object)[0]).longValue();
	}

	@Override
	public void stop() {
		// first cancel the update job and wait for it to be done.
		if ( this.job != null ) {
			this.lock.lock();
			try {
				this.cancelled = true;
				this.job.cancel( false );
			}
			finally {
				this.lock.unlock();
			}
		}
		// and shutdown the executorservice
		if ( this.exec != null ) {
			this.exec.shutdown();
		}
	}

	@Override
	public void pause(boolean pause) {
		this.lock.lock();
		try {
			this.pause = pause;
		}
		finally {
			this.lock.unlock();
		}
	}

}
