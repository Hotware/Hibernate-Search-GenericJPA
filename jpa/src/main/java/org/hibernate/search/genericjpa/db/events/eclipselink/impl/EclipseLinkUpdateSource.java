/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.eclipselink.impl;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.eclipse.persistence.descriptors.DescriptorEvent;
import org.eclipse.persistence.descriptors.DescriptorEventAdapter;
import org.eclipse.persistence.sessions.Session;
import org.eclipse.persistence.sessions.SessionEvent;
import org.eclipse.persistence.sessions.SessionEventAdapter;
import org.eclipse.persistence.sessions.UnitOfWork;

import org.hibernate.search.genericjpa.JPASearchFactoryController;
import org.hibernate.search.genericjpa.events.impl.SynchronizedUpdateSource;
import org.hibernate.search.genericjpa.factory.Transaction;
import org.hibernate.search.genericjpa.factory.impl.SubClassSupportInstanceInitializer;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.spi.InstanceInitializer;

/**
 * Created by Martin on 27.07.2015.
 */
public class EclipseLinkUpdateSource implements SynchronizedUpdateSource {

	private static Logger LOGGER = Logger.getLogger( EclipseLinkUpdateSource.class.getName() );

	private static final InstanceInitializer INSTANCE_INITIALIZER = SubClassSupportInstanceInitializer.INSTANCE;

	private final JPASearchFactoryController searchFactoryController;
	private final Set<Class<?>> indexRelevantEntities;

	final DescriptorEventAspect descriptorEventAspect;
	final SessionEventAspect sessionEventAspect;

	private final ConcurrentHashMap<Session, FullTextEntityManager> fullTextEntityManagers = new ConcurrentHashMap<>();

	public EclipseLinkUpdateSource(
			JPASearchFactoryController searchFactoryController,
			Set<Class<?>> indexRelevantEntities) {
		this.searchFactoryController = searchFactoryController;
		this.indexRelevantEntities = indexRelevantEntities;
		this.descriptorEventAspect = new DescriptorEventAspect();
		this.sessionEventAspect = new SessionEventAspect();
	}

	private class DescriptorEventAspect extends DescriptorEventAdapter {

		public void postInsert(DescriptorEvent event) {
			Object entity = event.getObject();
			Class<?> entityClass = INSTANCE_INITIALIZER.getClass( entity );
			if ( EclipseLinkUpdateSource.this.indexRelevantEntities.contains( entityClass ) ) {
				LOGGER.fine( "Insert Event for " + entity );
				Session session = event.getSession();
				if(session.isUnitOfWork()) {
					session = ((UnitOfWork) session).getParent();
				}
				FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
				fem.index( entity );
			}
		}

		public void postUpdate(DescriptorEvent event) {
			Object entity = event.getObject();
			Class<?> entityClass = INSTANCE_INITIALIZER.getClass( entity );
			if ( EclipseLinkUpdateSource.this.indexRelevantEntities.contains( entityClass ) ) {
				LOGGER.fine( "Update Event for " + entity );
				Session session = event.getSession();
				if(session.isUnitOfWork()) {
					session = ((UnitOfWork) session).getParent();
				}
				FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
				fem.index( entity );
			}
		}

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
				if(session.isUnitOfWork()) {
					session = ((UnitOfWork) session).getParent();
				}
				FullTextEntityManager fem = EclipseLinkUpdateSource.this.fullTextEntityManagers.get( session );
				Object id = session.getId( entity );
				fem.purge( entityClass, (Serializable) id );
			}
		}

	}

	private class SessionEventAspect extends SessionEventAdapter {

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
