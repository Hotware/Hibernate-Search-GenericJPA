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
import javax.transaction.TransactionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hibernate.search.genericjpa.db.events.AnnotationEventModelParser;
import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.UpdateSource;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdateSource;
import org.hibernate.search.genericjpa.db.events.triggers.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;

import static org.hibernate.search.genericjpa.Constants.BATCH_SIZE_FOR_UPDATE_QUERIES_DEFAULT_VALUE;
import static org.hibernate.search.genericjpa.Constants.BATCH_SIZE_FOR_UPDATE_QUERIES_KEY;
import static org.hibernate.search.genericjpa.Constants.TRIGGER_CREATION_STRATEGY_CREATE;
import static org.hibernate.search.genericjpa.Constants.TRIGGER_CREATION_STRATEGY_DONT_CREATE;
import static org.hibernate.search.genericjpa.Constants.TRIGGER_CREATION_STRATEGY_DROP_CREATE;

/**
 * @author Martin Braun
 */
public class SQLJPAUpdateSourceProvider implements UpdateSourceProvider {

	private static final Logger LOGGER = Logger.getLogger( SQLJPAUpdateSourceProvider.class.getName() );

	private final TriggerSQLStringSource triggerSource;
	private final List<Class<?>> updateClasses;
	private final EntityManagerFactory emf;
	private final TransactionManager transactionManager;
	private final String triggerCreateStrategy;

	public SQLJPAUpdateSourceProvider(
			EntityManagerFactory emf,
			TransactionManager transactionManager,
			TriggerSQLStringSource triggerSource,
			List<Class<?>> updateClasses,
			String triggerCreateStrategy) {
		this.triggerSource = triggerSource;
		this.updateClasses = updateClasses;
		this.emf = emf;
		this.transactionManager = transactionManager;
		this.triggerCreateStrategy = triggerCreateStrategy;
	}

	@Override
	public UpdateSource getUpdateSource(long delay, TimeUnit timeUnit, int batchSizeForUpdates, Properties properties) {
		EventModelParser eventModelParser = new AnnotationEventModelParser();
		List<EventModelInfo> eventModelInfos = eventModelParser.parse( new ArrayList<>( this.updateClasses ) );
		this.setupTriggers( eventModelInfos );
		return new JPAUpdateSource(
				eventModelInfos,
				this.emf,
				this.transactionManager,
				delay,
				timeUnit,
				batchSizeForUpdates,
				Integer.parseInt(
						properties.getProperty(
								BATCH_SIZE_FOR_UPDATE_QUERIES_KEY,
								BATCH_SIZE_FOR_UPDATE_QUERIES_DEFAULT_VALUE
						)
				)
		);
	}

	private void setupTriggers(List<EventModelInfo> eventModelInfos) {
		if ( TRIGGER_CREATION_STRATEGY_DONT_CREATE.equals( this.triggerCreateStrategy ) || (!TRIGGER_CREATION_STRATEGY_CREATE
				.equals( this.triggerCreateStrategy ) && !TRIGGER_CREATION_STRATEGY_DROP_CREATE.equals( this.triggerCreateStrategy )) ) {
			return;
		}
		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			JPATransactionWrapper tx = JPATransactionWrapper.get( em, this.transactionManager );
			if ( tx != null ) {
				tx.setIgnoreExceptionsForJTATransaction( true );
				tx.begin();
			}

			for ( EventModelInfo info : eventModelInfos ) {
				if ( tx != null ) {
					tx.commitIgnoreExceptions();
					tx.begin();
				}
				if ( TRIGGER_CREATION_STRATEGY_DROP_CREATE.equals( this.triggerCreateStrategy ) ) {
					for ( String str : triggerSource.getUpdateTableDropCode( info ) ) {
						LOGGER.info( str );
						em.createNativeQuery( str ).executeUpdate();
					}
				}
				if ( tx != null ) {
					tx.commitIgnoreExceptions();
					tx.begin();
				}

				for ( String str : triggerSource.getUpdateTableCreationCode( info ) ) {
					LOGGER.info( str );
					this.doQueryOrLogException( em, str );
				}
				if ( tx != null ) {
					tx.commitIgnoreExceptions();
					tx.begin();
				}
			}

			try {
				for ( String str : triggerSource.getSetupCode() ) {
					LOGGER.info( str );
					em.createNativeQuery( str ).executeUpdate();
					if ( tx != null ) {
						tx.commitIgnoreExceptions();
						tx.begin();
					}
				}
				for ( EventModelInfo info : eventModelInfos ) {
					if ( TRIGGER_CREATION_STRATEGY_DROP_CREATE.equals( this.triggerCreateStrategy ) ) {
						for ( String unSetupCode : this.triggerSource.getSpecificUnSetupCode( info ) ) {
							LOGGER.info( unSetupCode );
							em.createNativeQuery( unSetupCode ).executeUpdate();
							if ( tx != null ) {
								tx.commitIgnoreExceptions();
								tx.begin();
							}
						}
					}

					for ( String setupCode : this.triggerSource.getSpecificSetupCode( info ) ) {
						LOGGER.info( setupCode );
						this.doQueryOrLogException( em, setupCode );
						if ( tx != null ) {
							tx.commitIgnoreExceptions();
							tx.begin();
						}
					}

					if ( TRIGGER_CREATION_STRATEGY_DROP_CREATE.equals( this.triggerCreateStrategy ) ) {
						for ( int eventType : EventType.values() ) {
							String[] triggerDropStrings = this.triggerSource.getTriggerDropCode( info, eventType );
							for ( String triggerCreationString : triggerDropStrings ) {
								LOGGER.info( triggerCreationString );
								em.createNativeQuery( triggerCreationString ).executeUpdate();
								if ( tx != null ) {
									tx.commitIgnoreExceptions();
									tx.begin();
								}
							}
						}
					}

					for ( int eventType : EventType.values() ) {
						String[] triggerCreationStrings = this.triggerSource.getTriggerCreationCode( info, eventType );
						for ( String triggerCreationString : triggerCreationStrings ) {
							LOGGER.info( triggerCreationString );
							this.doQueryOrLogException( em, triggerCreationString );
							if ( tx != null ) {
								tx.commitIgnoreExceptions();
								tx.begin();
							}
						}
					}
				}
			}
			catch (Exception e) {
				if ( tx != null ) {
					tx.rollback();
					LOGGER.log( Level.WARNING, "rolling back trigger setup!", e );
				}
				throw new SearchException( e );
			}
			if ( tx != null ) {
				tx.commitIgnoreExceptions();
				LOGGER.info( "commited trigger setup!" );
			}
			//TODO: what is this doing here? :D
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

	private void doQueryOrLogException(EntityManager em, String query) {
		try {
			em.createNativeQuery( query ).executeUpdate();
		}
		//FIXME: better Exception, will this ever throw an Exception? even if triggers are not present
		//this doesn't seem to throw anything (for MySQL at least, I think it's best to keep it for now)
		catch (Exception e) {
			LOGGER.log(
					Level.WARNING,
					"Exception while trying to create trigger (if triggers are not dropped before creating this might be okay)",
					e
			);
		}
	}

}
