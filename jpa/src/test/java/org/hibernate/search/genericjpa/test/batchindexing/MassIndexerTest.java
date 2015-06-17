/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.batchindexing;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.hibernate.search.genericjpa.Constants;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.batchindexing.MassIndexer;
import org.hibernate.search.genericjpa.batchindexing.MassIndexerProgressMonitor;
import org.hibernate.search.genericjpa.batchindexing.impl.MassIndexerImpl;
import org.hibernate.search.genericjpa.db.events.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.impl.JPASearchFactoryAdapter;
import org.hibernate.search.genericjpa.test.jpa.entities.Place;
import org.hibernate.search.genericjpa.test.jpa.entities.Sorcerer;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Martin Braun
 */
public class MassIndexerTest {

	private EntityManagerFactory emf;
	private EntityManager em;
	private JPASearchFactoryAdapter searchFactory;
	private MassIndexer massIndexer;

	private static final int COUNT = 2153;

	@Test
	public void test() throws InterruptedException {
		System.out.println( "starting MassIndexer test!" );

		this.massIndexer.progressMonitor( new MassIndexerProgressMonitor() {

			@Override
			public void objectsLoaded(Class<?> entityType, int count) {
				System.out.println( "objects loaded: " + count );
			}

			@Override
			public void documentsBuilt(Class<?> entityType, int count) {
				System.out.println( "documents built: " + count );
			}

			@Override
			public void idsLoaded(Class<?> entityType, int count) {
				System.out.println( "loaded ids: " + count );
			}

			@Override
			public void documentsAdded(int count) {
				System.out.println( "documents added: " + count );
			}

		} );

		this.massIndexer.threadsToLoadObjects( 15 );
		this.massIndexer.batchSizeToLoadObjects( 100 );
		this.massIndexer.batchSizeToLoadIds( 500 );
		long pre = System.currentTimeMillis();
		try {
			this.massIndexer.startAndWait();
		}
		catch (InterruptedException e) {
			throw new SearchException( e );
		}
		long after = System.currentTimeMillis();
		System.out.println( "indexed " + COUNT + " root entities (3 sub each) in " + ( after - pre ) + "ms." );

		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );

		assertEquals( COUNT, fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class ).getResultSize() );
	}

	@Test
	public void testCancel() {
		Future<?> future = this.massIndexer.start();
		future.cancel( true );
	}

	@Test
	public void testFromSearchFactory() {
		try {
			this.searchFactory.createMassIndexer().threadsToLoadObjects( 15 ).batchSizeToLoadObjects( 100 ).startAndWait();
		}
		catch (InterruptedException e) {
			throw new SearchException( e );
		}
	}

	@Before
	public void setup() {
		this.emf = Persistence.createEntityManagerFactory( "EclipseLink_MySQL" );
		Properties properties = new Properties();
		properties.setProperty( Constants.SEARCH_FACTORY_NAME_KEY, "test" );
		properties.setProperty( Constants.TRIGGER_SOURCE_KEY, MySQLTriggerSQLStringSource.class.getName() );
		properties.setProperty( Constants.SEARCH_FACTORY_TYPE_KEY, "sql" );
		properties.setProperty( "hibernate.search.default.directory_provider", "filesystem" );
		properties.setProperty( "hibernate.search.default.indexBase", "target/indexes" );
		this.searchFactory = (JPASearchFactoryAdapter) Setup.createUnmanagedSearchFactory( emf, properties, null );
		this.searchFactory.pauseUpdating( true );
		EntityManager em = emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			em.createQuery( "DELETE FROM Place" ).executeUpdate();
			em.flush();

			em.createQuery( "DELETE FROM Sorcerer" ).executeUpdate();
			em.flush();

			int sorcCount = 0;
			for ( int i = 0; i < COUNT; ++i ) {
				Place place = new Place();
				place.setName( "Place" + i );

				Set<Sorcerer> sorcs = new HashSet<>();
				for ( int j = 0; j < 3; ++j ) {
					Sorcerer sorc = new Sorcerer();
					sorc.setName( "Sorcerer" + sorcCount++ );
					sorcs.add( sorc );
					sorc.setPlace( place );
					em.persist( sorc );
				}
				place.setSorcerers( sorcs );
				em.merge( place );
			}

			tx.commit();
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
		this.em = this.emf.createEntityManager();
		this.massIndexer = new MassIndexerImpl( this.emf, this.searchFactory.getSearchIntegrator(), Arrays.asList( Place.class ), false );
	}

	@After
	public void shutdown() {
		// has to be shut down first (update processing!)
		try {
			this.searchFactory.close();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		if ( this.em != null ) {
			try {
				this.em.close();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		if ( this.emf != null ) {
			try {
				this.emf.close();
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

}
