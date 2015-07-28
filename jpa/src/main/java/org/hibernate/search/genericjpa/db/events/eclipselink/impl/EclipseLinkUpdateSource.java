/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.eclipselink.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.persistence.descriptors.DescriptorEvent;
import org.eclipse.persistence.descriptors.DescriptorEventAdapter;
import org.eclipse.persistence.queries.ObjectLevelReadQuery;
import org.eclipse.persistence.queries.ReadObjectQuery;
import org.eclipse.persistence.sessions.Session;
import org.eclipse.persistence.sessions.SessionEvent;
import org.eclipse.persistence.sessions.SessionEventAdapter;
import org.eclipse.persistence.sessions.UnitOfWork;

import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.search.genericjpa.db.EventType;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.index.impl.IndexUpdater;
import org.hibernate.search.genericjpa.entity.EntityProvider;
import org.hibernate.search.genericjpa.events.impl.SynchronizedUpdateSource;
import org.hibernate.search.genericjpa.exception.AssertionFailure;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.factory.Transaction;
import org.hibernate.search.genericjpa.factory.impl.SubClassSupportInstanceInitializer;
import org.hibernate.search.genericjpa.metadata.impl.RehashedTypeMetadata;
import org.hibernate.search.spi.InstanceInitializer;

/**
 * Created by Martin on 27.07.2015.
 */
public class EclipseLinkUpdateSource implements SynchronizedUpdateSource {

	//TODO: use an abstraction of IndexUpdater here. we don't want the logic to be duplicated

	private static final int HSQUERY_BATCH = 50;

	private static Logger LOGGER = Logger.getLogger( EclipseLinkUpdateSource.class.getName() );

	private static final InstanceInitializer INSTANCE_INITIALIZER = SubClassSupportInstanceInitializer.INSTANCE;

	private final IndexUpdater indexUpdater;
	private final Set<Class<?>> indexRelevantEntities;
	private final Map<Class<?>, RehashedTypeMetadata> rehashedTypeMetadataPerIndexRoot;
	private final Map<Class<?>, List<Class<?>>> containedInIndexOf;

	private List<UpdateConsumer> updateConsumers = new ArrayList<>();

	final DescriptorEventAspect descriptorEventAspect;
	final SessionEventAspect sessionEventAspect;

	private final ConcurrentHashMap<Session, Transaction> transactions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Transaction, List<UpdateConsumer.UpdateEventInfo>> updateEventInfos = new ConcurrentHashMap<>();

	public EclipseLinkUpdateSource(
			IndexUpdater indexUpdater,
			Set<Class<?>> indexRelevantEntities,
			Map<Class<?>, RehashedTypeMetadata> rehashedTypeMetadataPerIndexRoot,
			Map<Class<?>, List<Class<?>>> containedInIndexOf) {
		this.indexUpdater = indexUpdater;
		this.indexRelevantEntities = indexRelevantEntities;
		this.descriptorEventAspect = new DescriptorEventAspect();
		this.sessionEventAspect = new SessionEventAspect();
		this.rehashedTypeMetadataPerIndexRoot = rehashedTypeMetadataPerIndexRoot;
		this.containedInIndexOf = containedInIndexOf;
	}

	@Override
	public void setUpdateConsumers(List<UpdateConsumer> updateConsumers) {
		this.updateConsumers = updateConsumers;
	}

	private void notify(List<UpdateConsumer.UpdateEventInfo> updateEventInfos) {
		if ( updateEventInfos.size() > 0 ) {
			for ( UpdateConsumer updateConsumer : EclipseLinkUpdateSource.this.updateConsumers ) {
				try {
					updateConsumer.updateEvent( updateEventInfos );
				}
				catch (Exception e) {
					LOGGER.log( Level.WARNING, "Exception while notifying updateConsumers", e );
				}
			}
		}
	}

	private Object getId(Class<?> entityClass, Object entity) {
		List<Class<?>> inIndexOf = EclipseLinkUpdateSource.this.containedInIndexOf.get( entityClass );
		if ( inIndexOf.size() > 0 ) {
			//hack, but works
			RehashedTypeMetadata metadata = EclipseLinkUpdateSource.this.rehashedTypeMetadataPerIndexRoot.get(
					inIndexOf.get( 0 )
			);
			XProperty idProperty = metadata.getIdPropertyAccessorForType().get( entityClass );
			Object id = idProperty.invoke( entity );
			return id;
		}
		return null;
	}

	private class DescriptorEventAspect extends DescriptorEventAdapter {

		@Override
		public void postInsert(DescriptorEvent event) {
			Object entity = event.getObject();
			Class<?> entityClass = INSTANCE_INITIALIZER.getClass( entity );
			Object id = EclipseLinkUpdateSource.this.getId( entityClass, entity );
			if ( id == null ) {
				throw new SearchException( "id was null for " + entity );
			}
			if ( EclipseLinkUpdateSource.this.indexRelevantEntities.contains( entityClass ) ) {
				LOGGER.fine( "Insert Event for " + entity );
				Session session = event.getSession();
				if ( session.isUnitOfWork() ) {
					session = ((UnitOfWork) session).getParent();
				}
				Transaction tx = EclipseLinkUpdateSource.this.transactions.get( session );
				if ( tx == null ) {
					tx = new Transaction();
					try {
						EclipseLinkUpdateSource.this.indexUpdater.index( entity, tx );
						tx.commit();
						EclipseLinkUpdateSource.this.notify(
								Collections.singletonList(
										new UpdateConsumer.UpdateEventInfo(
												entityClass, id, EventType.INSERT
										)
								)
						);
					}
					catch (Exception e) {
						tx.rollback();
					}
				}
				else {
					EclipseLinkUpdateSource.this.indexUpdater.index( entity, tx );
					EclipseLinkUpdateSource.this.updateEventInfos.get( tx ).add(
							new UpdateConsumer.UpdateEventInfo(
									entityClass, id, EventType.INSERT
							)
					);
				}
			}
		}

		@Override
		public void postUpdate(DescriptorEvent event) {
			Object entity = event.getObject();
			Class<?> entityClass = INSTANCE_INITIALIZER.getClass( entity );
			Object id = EclipseLinkUpdateSource.this.getId( entityClass, entity );
			if ( id == null ) {
				throw new SearchException( "id was null for " + entity );
			}
			if ( EclipseLinkUpdateSource.this.indexRelevantEntities.contains( entityClass ) ) {
				LOGGER.fine( "Update Event for " + entity );
				Session session = event.getSession();
				if ( session.isUnitOfWork() ) {
					session = ((UnitOfWork) session).getParent();
				}
				Transaction tx = EclipseLinkUpdateSource.this.transactions.get( session );
				if ( tx == null ) {
					tx = new Transaction();
					try {
						EclipseLinkUpdateSource.this.indexUpdater.update( entity, tx );
						tx.commit();
						EclipseLinkUpdateSource.this.notify(
								Collections.singletonList(
										new UpdateConsumer.UpdateEventInfo(
												entityClass, id, EventType.UPDATE
										)
								)
						);
					}
					catch (Exception e) {
						tx.rollback();
					}
				}
				else {
					EclipseLinkUpdateSource.this.indexUpdater.update( entity, tx );
					EclipseLinkUpdateSource.this.updateEventInfos.get( tx ).add(
							new UpdateConsumer.UpdateEventInfo(
									entityClass, id, EventType.UPDATE
							)
					);
				}
			}
		}

		@Override
		public void postDelete(DescriptorEvent event) {
			//we have to do stuff similar to IndexUpdater here.
			//we have to check in which index this object was found and
			//and then work our way up.
			//maybe we should allow for a purge method that takes
			//objects in FullTextEntityManager?
			//would make sense.
			Object entity = event.getObject();
			Class<?> entityClass = INSTANCE_INITIALIZER.getClass( entity );
			if ( EclipseLinkUpdateSource.this.indexRelevantEntities.contains( entityClass ) ) {
				LOGGER.fine( "Delete Event for " + entity );
				Session tmp = event.getSession();
				if ( tmp.isUnitOfWork() ) {
					tmp = ((UnitOfWork) tmp).getParent();
				}
				final Session session = tmp;
				Transaction tx = EclipseLinkUpdateSource.this.transactions.get( session );
				boolean createdOwnTx = false;
				UpdateConsumer.UpdateEventInfo updateEventInfo = null;
				if ( tx == null ) {
					tx = new Transaction();
					createdOwnTx = true;
				}
				try {
					List<Class<?>> inIndexOf = EclipseLinkUpdateSource.this.containedInIndexOf.get( entityClass );
					if ( inIndexOf.size() > 0 ) {
						//hack, but works
						RehashedTypeMetadata metadata = EclipseLinkUpdateSource.this.rehashedTypeMetadataPerIndexRoot
								.get(
										inIndexOf.get( 0 )
								);
						XProperty idProperty = metadata.getIdPropertyAccessorForType().get( entityClass );
						Object id = idProperty.invoke( entity );
						updateEventInfo = new UpdateConsumer.UpdateEventInfo(
								entityClass, id, EventType.DELETE
						);
						EclipseLinkUpdateSource.this.indexUpdater.delete(
								entityClass, inIndexOf, id, new EntityProvider() {

									@Override
									public Object get(Class<?> entityClass, Object id, Map<String, String> hints) {
										ReadObjectQuery nativeQuery = new ReadObjectQuery();
										nativeQuery.setReferenceClass( entityClass );
										nativeQuery.setSelectionId( id );
										nativeQuery.setCacheUsage( ObjectLevelReadQuery.DoNotCheckCache );
										Object original = session.executeQuery( nativeQuery );
										return original;
									}

									@Override
									public List getBatch(
											Class<?> entityClass,
											List<Object> id,
											Map<String, String> hints) {
										throw new AssertionFailure( "normally not used in IndexUpdater" );
									}

									@Override
									public void close() throws IOException {
										//no-op
									}

								}, tx
						);
					}
					if ( createdOwnTx ) {
						tx.commit();
						if ( updateEventInfo != null ) {
							EclipseLinkUpdateSource.this.notify( Collections.singletonList( updateEventInfo ) );
						}
					}
					else {
						if ( updateEventInfo != null ) {
							EclipseLinkUpdateSource.this.updateEventInfos.get( tx ).add(
									updateEventInfo
							);
						}
					}
				}
				catch (Exception e) {
					if ( createdOwnTx ) {
						tx.rollback();
					}
					throw e;
				}
			}
		}

	}

	private class SessionEventAspect extends SessionEventAdapter {

		@Override
		public void postBeginTransaction(SessionEvent event) {
			Session session = event.getSession();
			Transaction tx = EclipseLinkUpdateSource.this.transactions.get( session );
			if ( tx != null && tx.isTransactionInProgress() ) {
				//we are fine
			}
			else {
				tx = new Transaction();
				EclipseLinkUpdateSource.this.transactions.put( session, tx );
				EclipseLinkUpdateSource.this.updateEventInfos.put( tx, new ArrayList<>() );
			}
		}

		@Override
		public void postCommitTransaction(SessionEvent event) {
			Session session = event.getSession();
			Transaction tx = EclipseLinkUpdateSource.this.transactions.get( session );
			if ( tx != null && tx.isTransactionInProgress() ) {
				tx.commit();
				List<UpdateConsumer.UpdateEventInfo> updateEventInfos = EclipseLinkUpdateSource.this.updateEventInfos
						.remove(
								tx
						);
				EclipseLinkUpdateSource.this.notify( updateEventInfos );
				EclipseLinkUpdateSource.this.transactions.remove( session );
			}
			else {
				LOGGER.warning(
						"received commit event from EclipseLink and transaction should have been in progress, but wasn't"
				);
			}
		}

		@Override
		public void postRollbackTransaction(SessionEvent event) {
			Session session = event.getSession();
			Transaction tx = EclipseLinkUpdateSource.this.transactions.get( session );
			if ( tx != null && tx.isTransactionInProgress() ) {
				tx.rollback();
				EclipseLinkUpdateSource.this.updateEventInfos.remove( tx );
				EclipseLinkUpdateSource.this.transactions.remove( session );
			}
			else {
				LOGGER.warning(
						"received rollback event from EclipseLink and transaction should have been in progress, but wasn't"
				);
			}
		}

		@Override
		public void postLogout(SessionEvent event) {
			Session session = event.getSession();
			Transaction tx = EclipseLinkUpdateSource.this.transactions.get( session );
			if ( tx != null && tx.isTransactionInProgress() ) {
				LOGGER.warning(
						"rolling back transaction because session logged out..."
				);
				tx.rollback();
				EclipseLinkUpdateSource.this.updateEventInfos.remove( tx );
				EclipseLinkUpdateSource.this.transactions.remove( session );
			}
		}

	}

	@Override
	public void close() {
		//just to make sure
		this.transactions.clear();

		this.indexUpdater.close();
	}

}
