/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.persistence.EntityManagerFactory;

import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.genericjpa.annotations.Updates;
import org.hibernate.search.standalone.annotations.InIndex;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.exception.SearchException;

public final class Setup {

	private static final Logger LOGGER = Logger.getLogger( Setup.class.getName() );

	private Setup() {
		// can't touch this!
	}

	public static JPASearchFactory createUnmanagedSearchFactory(EntityManagerFactory emf, Properties properties, UpdateConsumer updateConsumer) {
		return createUnmanagedSearchFactory( emf, false, properties, updateConsumer );
	}

	public static JPASearchFactory createUnmanagedSearchFactory(EntityManagerFactory emf, boolean useUserTransactions, Properties properties,
			UpdateConsumer updateConsumer) {
		return createSearchFactory( emf, useUserTransactions, properties, updateConsumer, null );
	}

	public static JPASearchFactory createSearchFactory(EntityManagerFactory emf, boolean useUserTransactions, Properties properties,
			UpdateConsumer updateConsumer, ScheduledExecutorService exec) {
		if ( useUserTransactions ) {
			if ( exec == null ) {
				throw new IllegalArgumentException( "provided ScheduledExecutorService may not be null if using userTransactions" );
			}
			try {
				if ( !Class.forName( "javax.enterprise.concurrent.ManagedScheduledExecutorService" ).isAssignableFrom( exec.getClass() ) ) {
					throw new IllegalArgumentException( "an instance of" + " javax.enterprise.concurrent.ManagedScheduledExecutorService"
							+ "has to be used for scheduling when using JTA transactions!" );
				}
			}
			catch (ClassNotFoundException e) {
				throw new SearchException( "coudln't load class javax.enterprise.concurrent.ManagedScheduledExecutorService "
						+ "even though JTA transaction is to be used!" );
			}
		}
		if ( SearchFactoryRegistry.getSearchFactory() != null ) {
			throw new SearchException( "there is already a searchfactory running. close it first!" );
		}
		try {
			//hack... but OpenJPA wants this so it can enhance the classes.
			emf.createEntityManager().close();
			List<Class<?>> updateClasses = emf.getMetamodel().getEntities().stream().map( (entityType) -> {
				return entityType.getBindableJavaType();
			} ).filter( (entityClass) -> {
				return entityClass.isAnnotationPresent( Updates.class );
			} ).collect( Collectors.toList() );
			List<Class<?>> indexRootTypes = emf.getMetamodel().getEntities().stream().map( (entityType) -> {
				return entityType.getBindableJavaType();
			} ).filter( (entityClass) -> {
				return entityClass.isAnnotationPresent( InIndex.class ) && entityClass.isAnnotationPresent( Indexed.class );
			} ).collect( Collectors.toList() );

			String type = properties.getProperty( "org.hibernate.search.genericjpa.searchfactory.type", "sql" );
			Integer batchSizeForUpdates = Integer.parseInt( properties.getProperty( "org.hibernate.search.genericjpa.searchfactory.batchsizeForUpdates", "5" ) );
			Integer updateDelay = Integer.parseInt( properties.getProperty( "org.hibernate.search.genericjpa.searchfactory.updateDelay", "500" ) );
			UnmanagedSearchFactoryImpl ret = null;
			if ( "sql".equals( type ) ) {
				String triggerSource = properties.getProperty( "org.hibernate.search.genericjpa.searchfactory.triggerSource" );
				Class<?> triggerSourceClass;
				if ( triggerSource == null || ( triggerSourceClass = Class.forName( triggerSource ) ) == null ) {
					throw new SearchException( "org.hibernate.search.genericjpa.searchfactory.triggerSource must be a class type" );
				}
				if ( useUserTransactions ) {
					LOGGER.info( "using userTransactions" );
				}
				ret = new UnmanagedSearchFactoryImpl( emf, useUserTransactions, indexRootTypes, properties, updateConsumer, exec,
						new SQLJPAUpdateSourceProvider( emf, useUserTransactions, (TriggerSQLStringSource) triggerSourceClass.newInstance(), updateClasses ) );
				ret.setBatchSizeForUpdates( batchSizeForUpdates );
				ret.setUpdateDelay( updateDelay );
				ret.init();
			}
			else {
				throw new SearchException( "unrecognized type : " + type );
			}
			SearchFactoryRegistry.setup( ret );
			return ret;
		}
		catch (Exception e) {
			if ( !( e instanceof SearchException ) ) {
				throw new SearchException( e );
			}
			else {
				throw (SearchException) e;
			}
		}
	}
}
