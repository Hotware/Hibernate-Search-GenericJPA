/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.impl;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdateSource;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

/**
 * @author Martin Braun
 */
public class SQLJPAUpdateSourceProvider implements UpdateSourceProvider {

	private static final Logger LOGGER = Logger.getLogger( SQLJPAUpdateSourceProvider.class.getName() );

	private final TriggerSQLStringSource triggerSource;
	private final List<Class<?>> updateClasses;
	private final EntityManagerFactory emf;
	private final boolean useJTATransaction;

	public SQLJPAUpdateSourceProvider(
			EntityManagerFactory emf,
			boolean useJTATransaction,
			TriggerSQLStringSource triggerSource,
			List<Class<?>> updateClasses) {
		this.triggerSource = triggerSource;
		this.updateClasses = updateClasses;
		this.emf = emf;
		this.useJTATransaction = useJTATransaction;
	}

	@Override
	public UpdateSource getUpdateSource(long delay, TimeUnit timeUnit, int batchSizeForUpdates) {
		EventModelParser eventModelParser = new EventModelParser();
		List<EventModelInfo> eventModelInfos = eventModelParser.parse( new ArrayList<>( this.updateClasses ) );
		this.setupTriggers( eventModelInfos );
		return new JPAUpdateSource(
				eventModelInfos,
				this.emf,
				this.useJTATransaction,
				delay,
				timeUnit,
				batchSizeForUpdates
		);
	}

	private void setupTriggers(List<EventModelInfo> eventModelInfos) {
		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.useJTATransaction );
			if ( tx != null ) {
				tx.setIgnoreExceptionsForJTATransaction( true );
				tx.begin();
			}

			TriggerSQLStringSource triggerSource = this.triggerSource;
			try {
				for ( String str : triggerSource.getSetupCode() ) {
					LOGGER.info( str );
					em.createNativeQuery( str ).executeUpdate();
					if ( tx != null ) {
						LOGGER.info( "commiting setup code!" );
						tx.commit();
						tx.begin();
					}
				}
				for ( EventModelInfo info : eventModelInfos ) {
					for ( String unSetupCode : triggerSource.getSpecificUnSetupCode( info ) ) {
						LOGGER.info( unSetupCode );
						em.createNativeQuery( unSetupCode ).executeUpdate();
					}
					for ( String setupCode : triggerSource.getSpecificSetupCode( info ) ) {
						LOGGER.info( setupCode );
						em.createNativeQuery( setupCode ).executeUpdate();
					}
					for ( int eventType : EventType.values() ) {
						String[] triggerDropStrings = triggerSource.getTriggerDropCode( info, eventType );
						for ( String triggerCreationString : triggerDropStrings ) {
							LOGGER.info( triggerCreationString );
							em.createNativeQuery( triggerCreationString ).executeUpdate();
						}
					}
					for ( int eventType : EventType.values() ) {
						String[] triggerCreationStrings = triggerSource.getTriggerCreationCode( info, eventType );
						for ( String triggerCreationString : triggerCreationStrings ) {
							LOGGER.info( triggerCreationString );
							em.createNativeQuery( triggerCreationString ).executeUpdate();
						}
					}

				}
			}
			catch (Exception e) {
				if ( tx != null ) {
					tx.rollback();
					LOGGER.warning( "rolling back trigger setup!" );
				}
				throw new SearchException( e );
			}
			if ( tx != null ) {
				tx.commit();
				LOGGER.info( "commited trigger setup!" );
			}
			em.setFlushMode( FlushModeType.COMMIT );
			LOGGER.info( "finished setting up triggers!" );
		}
		catch (SecurityException e) {
			throw new SearchException( e );
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
	}

}
