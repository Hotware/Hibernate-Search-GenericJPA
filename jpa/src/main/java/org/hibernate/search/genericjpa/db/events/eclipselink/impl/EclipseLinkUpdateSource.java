/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.eclipselink.impl;

import javax.persistence.EntityManagerFactory;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.eclipse.persistence.descriptors.DescriptorEvent;
import org.eclipse.persistence.descriptors.DescriptorEventAdapter;
import org.eclipse.persistence.expressions.Expression;
import org.eclipse.persistence.internal.sessions.UnitOfWorkImpl;
import org.eclipse.persistence.queries.ObjectLevelReadQuery;
import org.eclipse.persistence.queries.ReadObjectQuery;
import org.eclipse.persistence.sessions.Session;
import org.eclipse.persistence.sessions.SessionEvent;
import org.eclipse.persistence.sessions.SessionEventAdapter;
import org.eclipse.persistence.sessions.UnitOfWork;

import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.search.backend.spi.SingularTermDeletionQuery;
import org.hibernate.search.backend.spi.Work;
import org.hibernate.search.backend.spi.WorkType;
import org.hibernate.search.bridge.FieldBridge;
import org.hibernate.search.bridge.StringBridge;
import org.hibernate.search.engine.ProjectionConstants;
import org.hibernate.search.engine.metadata.impl.DocumentFieldMetadata;
import org.hibernate.search.genericjpa.JPASearchFactoryController;
import org.hibernate.search.genericjpa.events.impl.SynchronizedUpdateSource;
import org.hibernate.search.genericjpa.factory.Transaction;
import org.hibernate.search.genericjpa.factory.impl.SubClassSupportInstanceInitializer;
import org.hibernate.search.genericjpa.metadata.impl.RehashedTypeMetadata;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.query.engine.spi.EntityInfo;
import org.hibernate.search.query.engine.spi.HSQuery;
import org.hibernate.search.spi.InstanceInitializer;

/**
 * Created by Martin on 27.07.2015.
 */
public class EclipseLinkUpdateSource implements SynchronizedUpdateSource {

	//TODO: use an abstraction of IndexUpdater here. we don't want the logic to be duplicated

	private static final int HSQUERY_BATCH = 50;

	private static Logger LOGGER = Logger.getLogger( EclipseLinkUpdateSource.class.getName() );

	private static final InstanceInitializer INSTANCE_INITIALIZER = SubClassSupportInstanceInitializer.INSTANCE;

	private final JPASearchFactoryController searchFactoryController;
	private final Set<Class<?>> indexRelevantEntities;
	private final Map<Class<?>, RehashedTypeMetadata> rehashedTypeMetadataPerIndexRoot;
	private final Map<Class<?>, List<Class<?>>> containedInIndexOf;

	final DescriptorEventAspect descriptorEventAspect;
	final SessionEventAspect sessionEventAspect;

	private final ConcurrentHashMap<Session, FullTextEntityManager> fullTextEntityManagers = new ConcurrentHashMap<>();

	public EclipseLinkUpdateSource(
			JPASearchFactoryController searchFactoryController,
			Set<Class<?>> indexRelevantEntities,
			Map<Class<?>, RehashedTypeMetadata> rehashedTypeMetadataPerIndexRoot,
			Map<Class<?>, List<Class<?>>> containedInIndexOf) {
		this.searchFactoryController = searchFactoryController;
		this.indexRelevantEntities = indexRelevantEntities;
		this.descriptorEventAspect = new DescriptorEventAspect();
		this.sessionEventAspect = new SessionEventAspect();
		this.rehashedTypeMetadataPerIndexRoot = rehashedTypeMetadataPerIndexRoot;
		this.containedInIndexOf = containedInIndexOf;
	}

	private class DescriptorEventAspect extends DescriptorEventAdapter {

		@Override
		public void postInsert(DescriptorEvent event) {
			Object entity = event.getObject();
			Class<?> entityClass = INSTANCE_INITIALIZER.getClass( entity );
			if ( EclipseLinkUpdateSource.this.indexRelevantEntities.contains( entityClass ) ) {
				LOGGER.fine( "Insert Event for " + entity );
				Session session = event.getSession();
				if ( session.isUnitOfWork() ) {
					session = ((UnitOfWork) session).getParent();
				}
				FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
				fem.index( entity );
			}
		}

		@Override
		public void postUpdate(DescriptorEvent event) {
			Object entity = event.getObject();
			Class<?> entityClass = INSTANCE_INITIALIZER.getClass( entity );
			if ( EclipseLinkUpdateSource.this.indexRelevantEntities.contains( entityClass ) ) {
				LOGGER.fine( "Update Event for " + entity );
				Session session = event.getSession();
				if ( session.isUnitOfWork() ) {
					session = ((UnitOfWork) session).getParent();
				}
				FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
				fem.index( entity );
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
				Session session = event.getSession();
				if ( session.isUnitOfWork() ) {
					session = ((UnitOfWork) session).getParent();
				}
				FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
				List<Class<?>> inIndexOf = EclipseLinkUpdateSource.this.containedInIndexOf.get( entityClass );
				for ( Class<?> indexClass : inIndexOf ) {
					RehashedTypeMetadata metadata = EclipseLinkUpdateSource.this.rehashedTypeMetadataPerIndexRoot.get(
							indexClass
					);

					XProperty idProperty = metadata.getIdPropertyAccessorForType().get( entityClass );
					Object id = idProperty.invoke( entity );

					List<String> fields = metadata.getIdFieldNamesForType().get( entityClass );
					for ( String field : fields ) {
						DocumentFieldMetadata metaDataForIdField = metadata.getDocumentFieldMetadataForIdFieldName()
								.get(
										field
								);

						SingularTermDeletionQuery.Type idType = metadata.getSingularTermDeletionQueryTypeForIdFieldName()
								.get( entityClass );
						Object idValueForDeletion;
						if ( idType == SingularTermDeletionQuery.Type.STRING ) {
							FieldBridge fb = metaDataForIdField.getFieldBridge();
							if ( !(fb instanceof StringBridge) ) {
								throw new IllegalArgumentException( "no TwoWayStringBridge found for field: " + field );
							}
							idValueForDeletion = ((StringBridge) fb).objectToString( id );
						}
						else {
							idValueForDeletion = id;
						}

						if ( indexClass.equals( entityClass ) ) {
							fem.purge( entityClass, (Serializable) id );
						}
						else {
							FullTextQuery fullTextQuery = fem.createFullTextQuery(
									fem.getSearchFactory()
											.buildQueryBuilder()
											.forEntity( indexClass )
											.get()
											.keyword()
											.onField( field )
											.matching( idValueForDeletion )
											.createQuery(), indexClass
							);

							fullTextQuery.setMaxResults( HSQUERY_BATCH );
							fullTextQuery.setProjection( ProjectionConstants.ID );

							int count = fullTextQuery.getResultSize();
							int processed = 0;
							// this was just contained somewhere
							// so we have to update the containing entity
							while ( processed < count ) {
								fullTextQuery.setFirstResult( processed );
								for ( Object[] projection : (List<Object[]>) fullTextQuery.getResultList() ) {
									Serializable originalId = (Serializable) projection[0];
									ReadObjectQuery nativeQuery = new ReadObjectQuery();
									nativeQuery.setReferenceClass( indexClass );
									nativeQuery.setSelectionId( originalId );
									nativeQuery.setCacheUsage( ObjectLevelReadQuery.DoNotCheckCache );
									Object original = session.executeQuery( nativeQuery );
									if ( original != null ) {
										fem.index( original );
									}
									else {
										// original is not available in the
										// database, but it will be deleted by its
										// own delete event
										// TODO: log this?
									}
								}
								processed += HSQUERY_BATCH;
							}
						}
					}
				}
			}
		}

	}

	private class SessionEventAspect extends SessionEventAdapter {

		@Override
		public void postBeginTransaction(SessionEvent event) {
			Session session = event.getSession();
			FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
			if ( fem != null && fem.isSearchTransactionInProgress() ) {
				//we are fine
			}
			else {
				fem = EclipseLinkUpdateSource.this.searchFactoryController.getFullTextEntityManager( null );
				fem.beginSearchTransaction();
				EclipseLinkUpdateSource.this.fullTextEntityManagers.put( session, fem );
			}
		}

		@Override
		public void postCommitTransaction(SessionEvent event) {
			Session session = event.getSession();
			FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
			if ( fem != null && fem.isSearchTransactionInProgress() ) {
				fem.commitSearchTransaction();
				EclipseLinkUpdateSource.this.fullTextEntityManagers.remove( session );
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
			FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
			if ( fem != null && fem.isSearchTransactionInProgress() ) {
				fem.rollbackSearchTransaction();
				EclipseLinkUpdateSource.this.fullTextEntityManagers.remove( session );
			}
			else {
				LOGGER.warning(
						"received rollback event from EclipseLink and transaction should have been in progress, but wasn't"
				);
			}
		}

	}

	@Override
	public void close() {

	}

}
