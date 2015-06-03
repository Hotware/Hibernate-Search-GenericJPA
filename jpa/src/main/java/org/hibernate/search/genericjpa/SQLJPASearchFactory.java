/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import javax.persistence.EntityManager;

import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdateSource;

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
			Connection connection = this.getConnectionForSetup( em );

			TriggerSQLStringSource triggerSource = this.getTriggerSQLStringSource();
			try {
				for ( String str : triggerSource.getSetupCode() ) {
					Statement statement = connection.createStatement();
					LOGGER.info( str );
					statement.addBatch( connection.nativeSQL( str ) );
					statement.executeBatch();
					connection.commit();
				}
				for ( EventModelInfo info : eventModelInfos ) {
					for ( String unSetupCode : triggerSource.getSpecificUnSetupCode( info ) ) {
						Statement statement = connection.createStatement();
						LOGGER.info( unSetupCode );
						statement.addBatch( connection.nativeSQL( unSetupCode ) );
						statement.executeBatch();
						connection.commit();
					}
					for ( String setupCode : triggerSource.getSpecificSetupCode( info ) ) {
						Statement statement = connection.createStatement();
						LOGGER.info( setupCode );
						statement.addBatch( connection.nativeSQL( setupCode ) );
						statement.executeBatch();
						connection.commit();
					}
					for ( int eventType : EventType.values() ) {
						String[] triggerDropStrings = triggerSource.getTriggerDropCode( info, eventType );
						for ( String triggerCreationString : triggerDropStrings ) {
							Statement statement = connection.createStatement();
							LOGGER.info( triggerCreationString );
							statement.addBatch( connection.nativeSQL( triggerCreationString ) );
							statement.executeBatch();
							connection.commit();
						}
					}
					for ( int eventType : EventType.values() ) {
						String[] triggerCreationStrings = triggerSource.getTriggerCreationCode( info, eventType );
						for ( String triggerCreationString : triggerCreationStrings ) {
							Statement statement = connection.createStatement();
							LOGGER.info( triggerCreationString );
							statement.addBatch( connection.nativeSQL( triggerCreationString ) );
							statement.executeBatch();
							connection.commit();
						}
					}

				}
			}
			catch (SQLException e) {
				// TODO: better Exception:
				throw new RuntimeException( e );
			}
		}
		finally {
			if ( em != null && !this.isUseJTATransaction() ) {
				em.close();
			}
		}
	}

}
