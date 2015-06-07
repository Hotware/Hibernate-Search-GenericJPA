/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;

import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdateSource;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

/**
 * an implementation of {@link JPASearchFactory} that uses an {@link UpdateSource} that gets it's information out of
 * tables that hold all the information about changes in the index tree. These tables are filled via triggers
 * 
 * @author Martin Braun
 */
public abstract class SQLJPASearchFactory extends JPASearchFactory {

	private final Logger LOGGER = Logger.getLogger( SQLJPASearchFactory.class.getName() );

	/**
	 * @return the RDBMS specific trigger source
	 */
	protected abstract TriggerSQLStringSource getTriggerSQLStringSource();

	@Override
	protected UpdateSource getUpdateSource() {
		EventModelParser eventModelParser = new EventModelParser();
		List<EventModelInfo> eventModelInfos = eventModelParser.parse( new ArrayList<>( this.getUpdateClasses() ) );
		this.setupTriggers( eventModelInfos );

		return new JPAUpdateSource( eventModelInfos, this.getEmf(), this.isUseJTATransaction(), this.getDelay(), this.getDelayUnit(),
				this.getBatchSizeForUpdates(), this.getExecutorServiceForUpdater() );
	}

	private void setupTriggers(List<EventModelInfo> eventModelInfos) {
		EntityManager em = null;
		try {
			em = this.getEmf().createEntityManager();
			// tx is null if we cannot get a UserTransaction. This could be because we are
			// a container managed bean
			JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.isUseJTATransaction(), true );
			if ( tx != null ) {
				tx.setIgnoreExceptionsForUserTransaction( true );
				tx.begin();
			}

			TriggerSQLStringSource triggerSource = this.getTriggerSQLStringSource();
			try {
				for ( String str : triggerSource.getSetupCode() ) {
					LOGGER.info( str );
					em.createNativeQuery( str ).executeUpdate();
					if ( tx != null ) {
						LOGGER.info( "commiting setup code!");
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
				throw new RuntimeException( e );
			}
			if ( tx != null ) {
				tx.commit();
				LOGGER.info( "commited trigger setup!" );
			}
			em.setFlushMode( FlushModeType.COMMIT );
			LOGGER.info( "finished setting up triggers!" );
		}
		catch (SecurityException e) {
			throw new RuntimeException( e );
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
	}
}
