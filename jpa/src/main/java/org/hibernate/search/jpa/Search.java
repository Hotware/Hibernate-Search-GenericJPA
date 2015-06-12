/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import org.hibernate.search.genericjpa.impl.ImplementationFactory;
import org.hibernate.search.genericjpa.impl.SearchFactoryRegistry;

/**
 * Helper class that should be used when building a FullTextEntityManager
 *
 * @author Emmanuel Bernard
 * @author Hardy Ferentschik
 * @author Martin Braun
 */
public final class Search {

	private Search() {
	}

	public static void pauseUpdateing(EntityManager em, boolean pause) {
		Search.pauseUpdating( em.getEntityManagerFactory(), pause );
	}

	public static void pauseUpdating(EntityManagerFactory emf, boolean pause) {
		String name = SearchFactoryRegistry.getNameProperty( emf.getProperties() );
		if ( SearchFactoryRegistry.getSearchFactory( name ) != null ) {
			SearchFactoryRegistry.getSearchFactory( name ).pauseUpdateSource( pause );
		}
	}

	/**
	 * Build a full text capable EntityManager The underlying EM implementation has to be Hibernate EntityManager
	 */
	public static FullTextEntityManager getFullTextEntityManager(EntityManager em) {
		if ( em instanceof FullTextEntityManager ) {
			return (FullTextEntityManager) em;
		}
		else {
			String name = SearchFactoryRegistry.getNameProperty( em.getProperties() );
			return ImplementationFactory.createFullTextEntityManager( em, SearchFactoryRegistry.getSearchFactory( name ) );
		}
	}
}
