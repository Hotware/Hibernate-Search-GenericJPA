/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.db.events.jpa.impl;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.TransactionManager;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.hibernate.search.genericjpa.db.EventType;
import org.hibernate.search.genericjpa.db.events.impl.AsyncUpdateSource;
import org.hibernate.search.genericjpa.db.events.impl.AnnotationEventModelParser;
import org.hibernate.search.genericjpa.db.events.impl.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.impl.EventModelParser;
import org.hibernate.search.genericjpa.db.events.triggers.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.impl.AsyncUpdateSourceProvider;
import org.hibernate.search.genericjpa.jpa.util.impl.JPATransactionWrapper;

import static org.hibernate.search.genericjpa.Constants.BATCH_SIZE_FOR_UPDATE_QUERIES_DEFAULT_VALUE;
import static org.hibernate.search.genericjpa.Constants.BATCH_SIZE_FOR_UPDATE_QUERIES_KEY;
import static org.hibernate.search.genericjpa.Constants.TRIGGER_CREATION_STRATEGY_CREATE;
import static org.hibernate.search.genericjpa.Constants.TRIGGER_CREATION_STRATEGY_DONT_CREATE;
import static org.hibernate.search.genericjpa.Constants.TRIGGER_CREATION_STRATEGY_DROP_CREATE;

/**
 * @author Martin Braun
 */
public class SQLJPAAsyncUpdateSourceProvider implements AsyncUpdateSourceProvider {

	private static final Logger LOGGER = Logger.getLogger( SQLJPAAsyncUpdateSourceProvider.class.getName() );


	private final TriggerSQLStringSource triggerSource;
	private final List<Class<?>> updateClasses;
	private final String triggerCreateStrategy;

	public SQLJPAAsyncUpdateSourceProvider(
			TriggerSQLStringSource triggerSource,
			List<Class<?>> updateClasses,
			String triggerCreateStrategy) {
		this.triggerSource = triggerSource;
		this.updateClasses = updateClasses;
		this.triggerCreateStrategy = triggerCreateStrategy;
	}

	@Override
	public AsyncUpdateSource getUpdateSource(
			long delay,
			TimeUnit timeUnit,
			int batchSizeForUpdates,
			Properties properties,
			EntityManagerFactory emf, TransactionManager transactionManager) {
		EventModelParser eventModelParser = new AnnotationEventModelParser();
		List<EventModelInfo> eventModelInfos = eventModelParser.parse( new ArrayList<>( this.updateClasses ) );
		this.setupTriggers( emf, transactionManager, eventModelInfos, properties );
		JPAUpdateSource updateSource = new JPAUpdateSource(
				eventModelInfos,
				emf,
				transactionManager,
				delay,
				timeUnit,
				batchSizeForUpdates,
				Integer.parseInt(
						properties.getProperty(
								BATCH_SIZE_FOR_UPDATE_QUERIES_KEY,
								BATCH_SIZE_FOR_UPDATE_QUERIES_DEFAULT_VALUE
						)
				), this.triggerSource.getDelimitedIdentifierToken()
		);
		return updateSource;
	}

	private void setupTriggers(
			EntityManagerFactory emf,
			TransactionManager transactionManager,
			List<EventModelInfo> eventModelInfos,
			Properties properties) {
		if ( TRIGGER_CREATION_STRATEGY_DONT_CREATE.equals( this.triggerCreateStrategy ) || (!TRIGGER_CREATION_STRATEGY_CREATE
				.equals( this.triggerCreateStrategy ) && !TRIGGER_CREATION_STRATEGY_DROP_CREATE.equals( this.triggerCreateStrategy )) ) {
			return;
		}

		//FIXME: obtain a real connection here
		Connection connection = null;
		try {
			try {
				if ( TRIGGER_CREATION_STRATEGY_DROP_CREATE.equals( this.triggerCreateStrategy ) ) {
					//DROP EVERYTHING IN THE EXACTLY INVERSED ORDER WE CREATE IT
					for ( EventModelInfo info : eventModelInfos ) {

						for ( int eventType : EventType.values() ) {
							String[] triggerDropStrings = this.triggerSource.getTriggerDropCode( info, eventType );
							for ( String triggerDropString : triggerDropStrings ) {
								LOGGER.info( triggerDropString );
								this.doQueryOrLogException(
										emf,
										transactionManager,
										connection,
										triggerDropString,
										true
								);
							}
						}

						for ( String unSetupCode : this.triggerSource.getSpecificUnSetupCode( info ) ) {
							LOGGER.info( unSetupCode );
							this.doQueryOrLogException( emf, transactionManager, connection, unSetupCode, true );
						}

						for ( String str : triggerSource.getUpdateTableDropCode( info ) ) {
							LOGGER.info( str );
							this.doQueryOrLogException( emf, transactionManager, connection, str, true );
						}

					}

					for ( String str : triggerSource.getUnSetupCode() ) {
						LOGGER.info( str );
						this.doQueryOrLogException( emf, transactionManager, connection, str, true );
					}
				}

				//CREATE EVERYTHING
				try {
					for ( String str : triggerSource.getSetupCode() ) {
						LOGGER.info( str );
						this.doQueryOrLogException( emf, transactionManager, connection, str, false );
					}

					for ( EventModelInfo info : eventModelInfos ) {
						for ( String str : triggerSource.getUpdateTableCreationCode( info ) ) {
							LOGGER.info( str );
							this.doQueryOrLogException( emf, transactionManager, connection, str, false );
						}

						for ( String setupCode : this.triggerSource.getSpecificSetupCode( info ) ) {
							LOGGER.info( setupCode );
							this.doQueryOrLogException( emf, transactionManager, connection, setupCode, false );
						}

						for ( int eventType : EventType.values() ) {
							String[] triggerCreationStrings = this.triggerSource.getTriggerCreationCode(
									info,
									eventType
							);
							for ( String triggerCreationString : triggerCreationStrings ) {
								LOGGER.info( triggerCreationString );
								this.doQueryOrLogException(
										emf,
										transactionManager,
										connection,
										triggerCreationString,
										false
								);
							}
						}
					}
				}
				catch (Exception e) {
					throw new SearchException( e );
				}
				LOGGER.info( "finished setting up triggers!" );
			}
			finally {
				if ( connection != null ) {
					connection.close();
				}
			}
		}
		catch (Exception e) {
			throw new SearchException( e );
		}
	}

	private void doQueryOrLogException(
			EntityManagerFactory emf,
			TransactionManager transactionManager,
			Connection connection,
			String query,
			boolean canFail) {
		try {
			if ( connection != null ) {
				Statement statement = connection.createStatement();
				statement.execute( query );
			}
			else {
				//we use a new EntityManager here everytime, because
				//if we get an error during trigger creation
				//(which is allowed, since we don't have logic
				//to check with IF EXISTS on every database)
				//the EntityManager can be in a RollbackOnly state
				//which we dont want
				EntityManager em = emf.createEntityManager();
				try {
					JPATransactionWrapper tx = JPATransactionWrapper.get( em, transactionManager );
					tx.setIgnoreExceptionsForJTATransaction( true );
					tx.begin();

					em.createNativeQuery( query ).executeUpdate();

					tx.commitIgnoreExceptions();
				}
				finally {
					em.close();
				}
			}
		}
		catch (Exception e) {
			if ( canFail ) {
				LOGGER.warning(
						"Exception occured during setup of triggers (most of the time, this is okay): " +
								e.getMessage()
				);
			}
		}
	}

}
