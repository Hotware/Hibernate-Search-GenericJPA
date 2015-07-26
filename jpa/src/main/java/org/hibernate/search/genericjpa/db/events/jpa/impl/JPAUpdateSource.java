/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.jpa.impl;

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

import org.hibernate.search.genericjpa.db.ColumnType;
import org.hibernate.search.genericjpa.db.events.impl.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateEventInfo;
import org.hibernate.search.genericjpa.db.events.impl.UpdateSource;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.impl.JPATransactionWrapper;
import org.hibernate.search.genericjpa.jpa.util.impl.MultiQueryAccess;
import org.hibernate.search.genericjpa.jpa.util.impl.MultiQueryAccess.ObjectIdentifierWrapper;
import org.hibernate.search.genericjpa.util.NamingThreadFactory;

/**
 * a {@link UpdateSource} implementation that uses JPA to retrieve the updates from the database. For this to work the
 * entities have to be setup with JPA annotations
 *
 * @author Martin Braun
 */
public class JPAUpdateSource implements UpdateSource {

	private final String delimitedIdentifierToken;

	private static final Logger LOGGER = Logger.getLogger( JPAUpdateSource.class.getName() );

	private final List<EventModelInfo> eventModelInfos;
	private final EntityManagerFactory emf;
	private final long timeOut;
	private final TimeUnit timeUnit;
	private final int batchSizeForUpdates;
	private final int batchSizeForDatabaseQueries;

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
			int batchSizeForUpdates, String delimitedIdentifierToken) {
		this(
				eventModelInfos,
				emf,
				transactionManager,
				timeOut,
				timeUnit,
				batchSizeForUpdates,
				1,
				delimitedIdentifierToken,
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
			int batchSizeForDatabaseQueries,
			String delimitedIdentifierToken) {
		this(
				eventModelInfos,
				emf,
				transactionManager,
				timeOut,
				timeUnit,
				batchSizeForUpdates,
				batchSizeForDatabaseQueries,
				delimitedIdentifierToken,
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
			String delimitedIdentifierToken,
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
		this.delimitedIdentifierToken = delimitedIdentifierToken;
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
				count = ((Number) em.createNativeQuery(
						"SELECT count(*) FROM " + updateSource.delimitedIdentifierToken + evi.getUpdateTableName() + updateSource.delimitedIdentifierToken
				)
						.getSingleResult()).longValue();
			}
			countMap.put( evi.getUpdateTableName(), count );

			{
				StringBuilder queryString = new StringBuilder().append( "SELECT " )
						.append( updateSource.delimitedIdentifierToken )
						.append( evi.getUpdateIdColumn() )
						.append( updateSource.delimitedIdentifierToken )
						.append( ", " )
						.append( updateSource.delimitedIdentifierToken )
						.append( evi.getEventTypeColumn() ).append( updateSource.delimitedIdentifierToken );
				for ( EventModelInfo.IdInfo idInfo : evi.getIdInfos() ) {
					for ( String column : idInfo.getColumnsInUpdateTable() ) {
						queryString.append( ", " )
								.append( updateSource.delimitedIdentifierToken )
								.append( column )
								.append(
										updateSource.delimitedIdentifierToken
								);
					}
				}
				queryString.append( " FROM " ).append( updateSource.delimitedIdentifierToken )
						.append( evi.getUpdateTableName() ).append( updateSource.delimitedIdentifierToken )
						.append(
								" ORDER BY "
						)
						.append( updateSource.delimitedIdentifierToken )
						.append( evi.getUpdateIdColumn() )
						.append( updateSource.delimitedIdentifierToken );

				Query query = em.createNativeQuery(
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
									Object[] valuesFromQuery = (Object[]) query.get();

									Long updateId = ((Number) valuesFromQuery[0]).longValue();
									Integer eventType = ((Number) valuesFromQuery[1]).intValue();

									EventModelInfo evi = this.updateTableToEventModelInfo.get( query.identifier() );

									toRemove.add(
											new Object[] {
													evi.getUpdateTableName(),
													evi.getUpdateIdColumn(),
													updateId,
											}
									);

									//we skip the id and eventtype
									int currentIndex = 2;
									for ( EventModelInfo.IdInfo info : evi.getIdInfos() ) {
										ColumnType[] columnTypes = info.getColumnTypes();
										String[] columnNames = info.getColumnsInUpdateTable();
										Object val[] = new Object[columnTypes.length];
										for ( int i = 0; i < columnTypes.length; ++i ) {
											val[i] = valuesFromQuery[currentIndex++];
										}
										Object entityId = info.getIdConverter().convert(
												val,
												columnNames,
												columnTypes
										);
										updateInfos.add(
												new UpdateEventInfo(
														info.getEntityClass(),
														entityId,
														eventType,
														info.getHints()
												)
										);
									}
									// TODO: maybe move this to a method as
									// it is getting reused
									if ( ++processed % this.batchSizeForUpdates == 0 ) {
										for ( UpdateConsumer consumer : this.updateConsumers ) {
											consumer.updateEvent( updateInfos );
											LOGGER.fine( "handled update-event: " + updateInfos );
										}
										for ( Object[] rem : toRemove ) {
											// the table is in rem[0], the
											// id column for the update is in
											// rem[1] and the id is in rem[2]
											query.addToNextValuePosition( rem[0].toString(), -1L );
											em.createNativeQuery( "DELETE FROM " + this.delimitedIdentifierToken + rem[0] + this.delimitedIdentifierToken + " WHERE " + this.delimitedIdentifierToken + rem[1] + this.delimitedIdentifierToken + " = " + rem[2] )
													.executeUpdate();
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
										// the table is in rem[0], the
										// id column for the update is in
										// rem[1] and the id is in rem[2]
										query.addToNextValuePosition( rem[0].toString(), -1L );
										em.createNativeQuery( "DELETE FROM " + this.delimitedIdentifierToken + rem[0] + this.delimitedIdentifierToken + " WHERE " + this.delimitedIdentifierToken + rem[1] + this.delimitedIdentifierToken + " = " + rem[2] )
												.executeUpdate();
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
