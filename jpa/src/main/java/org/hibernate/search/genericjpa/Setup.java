/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.persistence.EntityManagerFactory;

import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.genericjpa.annotations.InIndex;
import org.hibernate.search.genericjpa.annotations.Updates;
import org.hibernate.search.genericjpa.db.events.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.impl.JPASearchFactoryAdapter;
import org.hibernate.search.genericjpa.impl.SQLJPAUpdateSourceProvider;
import org.hibernate.search.genericjpa.impl.SearchFactoryRegistry;

public final class Setup {

	private static final Logger LOGGER = Logger.getLogger( Setup.class.getName() );

	private Setup() {
		// can't touch this!
	}

	public static JPASearchFactory createUnmanagedSearchFactory(EntityManagerFactory emf) {
		return createSearchFactory( emf, emf.getProperties(), null, null );
	}

	public static JPASearchFactory createUnmanagedSearchFactory(EntityManagerFactory emf, @SuppressWarnings("rawtypes") Map properties) {
		return createSearchFactory( emf, properties, null, null );
	}

	public static JPASearchFactory createUnmanagedSearchFactory(EntityManagerFactory emf, UpdateConsumer updateConsumer) {
		return createSearchFactory( emf, emf.getProperties(), updateConsumer, null );
	}

	public static JPASearchFactory createUnmanagedSearchFactory(EntityManagerFactory emf, @SuppressWarnings("rawtypes") Map properties,
			UpdateConsumer updateConsumer) {
		return createSearchFactory( emf, properties, updateConsumer, null );
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static JPASearchFactory createSearchFactory(EntityManagerFactory emf, Map properties, UpdateConsumer updateConsumer, ScheduledExecutorService exec) {
		boolean useUserTransactions = Boolean.parseBoolean( (String) properties.getOrDefault(
				"org.hibernate.search.genericjpa.searchfactory.useUserTransactions", "false" ) );
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

		try {
			// hack... but OpenJPA wants this so it can enhance the classes.
			emf.createEntityManager().close();

			// get all the updates classes marked by an @Updates annotation
			List<Class<?>> updateClasses = emf.getMetamodel().getEntities().stream().map( (entityType) -> {
				return entityType.getBindableJavaType();
			} ).filter( (entityClass) -> {
				return entityClass.isAnnotationPresent( Updates.class );
			} ).collect( Collectors.toList() );

			// get all the root types maked by an @InIndex and @Indexed (@Indexed isn't sufficient here!)
			List<Class<?>> indexRootTypes = emf.getMetamodel().getEntities().stream().map( (entityType) -> {
				return entityType.getBindableJavaType();
			} ).filter( (entityClass) -> {
				return entityClass.isAnnotationPresent( InIndex.class ) && entityClass.isAnnotationPresent( Indexed.class );
			} ).collect( Collectors.toList() );

			LOGGER.info( "using hibernate-search properties: " + properties );
			// get the basic properties
			String name = SearchFactoryRegistry.getNameProperty( properties );
			String type = (String) properties.getOrDefault( "org.hibernate.search.genericjpa.searchfactory.type", "sql" );
			Integer batchSizeForUpdates = Integer.parseInt( (String) properties.getOrDefault(
					"org.hibernate.search.genericjpa.searchfactory.batchSizeForUpdates", "5" ) );
			Integer updateDelay = Integer.parseInt( (String) properties.getOrDefault( "org.hibernate.search.genericjpa.searchfactory.updateDelay", "500" ) );

			if ( SearchFactoryRegistry.getSearchFactory( name ) != null ) {
				throw new SearchException( "there is already a searchfactory running for name: " + name + ". close it first!" );
			}

			JPASearchFactoryAdapter ret = null;
			if ( "sql".equals( type ) ) {
				String triggerSource = (String) properties.get( "org.hibernate.search.genericjpa.searchfactory.triggerSource" );
				Class<?> triggerSourceClass;
				if ( triggerSource == null || ( triggerSourceClass = Class.forName( triggerSource ) ) == null ) {
					throw new SearchException( "class specified in org.hibernate.search.genericjpa.searchfactory.triggerSource could not be found." );
				}
				if ( useUserTransactions ) {
					LOGGER.info( "using userTransactions" );
				}
				ret = new JPASearchFactoryAdapter( name, emf, useUserTransactions, indexRootTypes, properties, updateConsumer, exec,
						new SQLJPAUpdateSourceProvider( emf, useUserTransactions, (TriggerSQLStringSource) triggerSourceClass.newInstance(), updateClasses ) );
				ret.setBatchSizeForUpdates( batchSizeForUpdates );
				ret.setUpdateDelay( updateDelay );
				ret.init();
			}
			else {
				throw new SearchException( "unrecognized type : " + type );
			}
			SearchFactoryRegistry.setup( name, ret );
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
