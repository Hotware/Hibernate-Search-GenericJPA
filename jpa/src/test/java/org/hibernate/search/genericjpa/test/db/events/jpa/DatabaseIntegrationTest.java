/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.db.events.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.genericjpa.db.events.AnnotationEventModelParser;
import org.hibernate.search.genericjpa.db.events.EventModelInfo;
import org.hibernate.search.genericjpa.db.events.EventModelParser;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdateSource;
import org.hibernate.search.genericjpa.db.events.triggers.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.triggers.TriggerSQLStringSource;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.jpa.util.JPATransactionWrapper;
import org.hibernate.search.genericjpa.test.jpa.entities.Place;
import org.hibernate.search.genericjpa.test.jpa.entities.Sorcerer;
import org.hibernate.search.genericjpa.util.Sleep;

import org.junit.After;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Martin Braun
 */
public abstract class DatabaseIntegrationTest {

	protected int valinorId = 0;
	protected int helmsDeepId = 0;
	protected Place valinor;
	protected EntityManagerFactory emf;
	protected EventModelParser parser = new AnnotationEventModelParser();
	protected String exceptionString;
	protected List<String> dropStrings;


	private List<String> updateTableNames;

	public void setup(String persistence, TriggerSQLStringSource triggerSource) throws SQLException {
		this.emf = Persistence.createEntityManagerFactory( persistence );
		EntityManager em = emf.createEntityManager();
		try {

			this.updateTableNames = new ArrayList<>();
			List<EventModelInfo> infos = parser.parse(
					new HashSet<>(
							Arrays.asList(
									Place.class,
									Sorcerer.class
							)
					)
			);
			em.getTransaction().begin();
			for ( EventModelInfo info : infos ) {
				this.updateTableNames.add( info.getUpdateTableName() );
				for ( String str : triggerSource.getUpdateTableDropCode( info ) ) {
					System.out.println( str );
					em.createNativeQuery( str ).executeUpdate();
				}
				for ( String str : triggerSource.getUpdateTableCreationCode( info ) ) {
					System.out.println( str );
					em.createNativeQuery( str ).executeUpdate();
				}
			}
			em.getTransaction().commit();

			this.deleteAllData( em );
			this.setupData( em );
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
	}

	protected void setupData(EntityManager em) {
		EntityTransaction tx = em.getTransaction();
		tx.begin();
		Sorcerer gandalf = new Sorcerer();
		gandalf.setName( "Gandalf" );
		em.persist( gandalf );

		Sorcerer saruman = new Sorcerer();
		saruman.setName( "Saruman" );
		em.persist( saruman );

		Sorcerer radagast = new Sorcerer();
		radagast.setName( "Radagast" );
		em.persist( radagast );

		Sorcerer alatar = new Sorcerer();
		alatar.setName( "Alatar" );
		em.persist( alatar );

		Sorcerer pallando = new Sorcerer();
		pallando.setName( "Pallando" );
		em.persist( pallando );

		// populate this database with some stuff
		Place helmsDeep = new Place();
		helmsDeep.setName( "Helm's Deep" );
		Set<Sorcerer> sorcerersAtHelmsDeep = new HashSet<>();
		sorcerersAtHelmsDeep.add( gandalf );
		gandalf.setPlace( helmsDeep );
		helmsDeep.setSorcerers( sorcerersAtHelmsDeep );
		em.persist( helmsDeep );

		Place valinor = new Place();
		valinor.setName( "Valinor" );
		Set<Sorcerer> sorcerersAtValinor = new HashSet<>();
		sorcerersAtValinor.add( saruman );
		saruman.setPlace( valinor );
		valinor.setSorcerers( sorcerersAtValinor );
		em.persist( valinor );

		this.valinorId = valinor.getId();
		this.helmsDeepId = helmsDeep.getId();

		this.valinor = valinor;

		em.flush();
		tx.commit();
	}

	protected void deleteAllData(EntityManager em) {
		EntityTransaction tx = em.getTransaction();
		tx.begin();

		{
			@SuppressWarnings("unchecked")
			List<Place> toDelete = new ArrayList<>( em.createQuery( "SELECT a FROM Place a" ).getResultList() );
			for ( Place place : toDelete ) {
				em.remove( place );
			}
			em.flush();
		}

		{
			@SuppressWarnings("unchecked")
			List<Sorcerer> toDelete = new ArrayList<>( em.createQuery( "SELECT a FROM Sorcerer a" ).getResultList() );
			for ( Sorcerer place : toDelete ) {
				em.remove( place );
			}
			em.flush();
		}

		tx.commit();

		tx.begin();
		{
			if ( this.updateTableNames != null ) {
				for ( String updateTableName : this.updateTableNames ) {
					em.createNativeQuery( "DELETE FROM " + updateTableName ).executeUpdate();
					em.flush();
				}
			}
		}
		tx.commit();
	}

	public void setupTriggers(TriggerSQLStringSource triggerSource) throws SQLException {
		EntityManager em = this.emf.createEntityManager();
		try {
			JPATransactionWrapper tx = JPATransactionWrapper.get( em, null );
			tx.begin();

			dropStrings = new ArrayList<>();
			// this is just for the unit tests to work properly.
			// normally we shouldn'tdelete the unique_id table or we could run
			// into trouble
			{
				if ( triggerSource instanceof MySQLTriggerSQLStringSource ) {
					em.createNativeQuery( "DROP TABLE IF EXISTS " + MySQLTriggerSQLStringSource.DEFAULT_UNIQUE_ID_TABLE_NAME )
							.executeUpdate();
				}
			}

			List<EventModelInfo> infos = parser.parse(
					new HashSet<>(
							Arrays.asList(
									Place.class,
									Sorcerer.class
							)
					)
			);

			this.updateTableNames = new ArrayList<>();

			try {
				for ( String str : triggerSource.getSetupCode() ) {
					System.out.println( str );
					em.createNativeQuery( str ).executeUpdate();
					if ( tx != null ) {
						System.out.println( "commiting setup code!" );
						tx.commitIgnoreExceptions();
						tx.begin();
					}
				}
				for ( EventModelInfo info : infos ) {
					for ( String unSetupCode : triggerSource.getSpecificUnSetupCode( info ) ) {
						System.out.println( unSetupCode );
						em.createNativeQuery( unSetupCode ).executeUpdate();
						if ( tx != null ) {
							System.out.println( "commiting setup code!" );
							tx.commitIgnoreExceptions();
							tx.begin();
						}
					}
					for ( String setupCode : triggerSource.getSpecificSetupCode( info ) ) {
						System.out.println( setupCode );
						em.createNativeQuery( setupCode ).executeUpdate();
						if ( tx != null ) {
							System.out.println( "commiting setup code!" );
							tx.commitIgnoreExceptions();
							tx.begin();
						}
					}
					for ( int eventType : EventType.values() ) {
						String[] triggerDropStrings = triggerSource.getTriggerDropCode( info, eventType );
						for ( String triggerCreationString : triggerDropStrings ) {
							System.out.println( triggerCreationString );
							em.createNativeQuery( triggerCreationString ).executeUpdate();
							if ( tx != null ) {
								System.out.println( "commiting setup code!" );
								tx.commitIgnoreExceptions();
								tx.begin();
							}
						}
					}
					for ( int eventType : EventType.values() ) {
						String[] triggerCreationStrings = triggerSource.getTriggerCreationCode( info, eventType );
						for ( String triggerCreationString : triggerCreationStrings ) {
							System.out.println( triggerCreationString );
							em.createNativeQuery( triggerCreationString ).executeUpdate();
							if ( tx != null ) {
								System.out.println( "commiting setup code!" );
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
					System.out.println( "rolling back trigger setup!" );
				}
				throw new SearchException( e );
			}
			tx.commitIgnoreExceptions();
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
	}

	public void tearDownTriggers() throws SQLException {
		EntityManager em = this.emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();

			// as we are testing on a mapping relation we cannot check whether
			// the UPDATE triggers are set up correctly. but because of the
			// nature how the triggers are created everything should be fine

			tx.begin();

			java.sql.Connection connection = em.unwrap( java.sql.Connection.class );
			connection.setAutoCommit( false );

			if ( dropStrings == null ) {
				return;
			}
			for ( String dropString : dropStrings ) {
				Statement statement = connection.createStatement();
				System.out.println( "DROP: " + connection.nativeSQL( dropString ) );
				statement.addBatch( connection.nativeSQL( dropString ) );
				statement.executeBatch();
			}

			tx.commit();
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
	}

	public void testUpdateIntegration() throws InterruptedException {
		EntityManager em = this.emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			int countBefore = em.createNativeQuery( "SELECT * FROM PlaceSorcererUpdatesHsearch" )
					.getResultList()
					.size();
			em.flush();
			tx.commit();

			tx.begin();
			Place valinorDb = em.find( Place.class, this.valinorId );
			Sorcerer randomNewGuy = new Sorcerer();
			randomNewGuy.setId( -42 );
			randomNewGuy.setName( "randomNewGuy" );
			randomNewGuy.setPlace( valinorDb );
			em.persist( randomNewGuy );
			valinorDb.getSorcerers().add( randomNewGuy );
			tx.commit();

			tx.begin();
			assertEquals(
					countBefore + 1,
					em.createNativeQuery( "SELECT * FROM PlaceSorcererUpdatesHsearch" ).getResultList().size()
			);
			tx.commit();

			tx.begin();
			assertEquals(
					1,
					em.createNativeQuery( "SELECT * FROM PlaceSorcererUpdatesHsearch" ).getResultList().size()
			);
			tx.commit();

			tx.begin();
			valinorDb.getSorcerers().remove( randomNewGuy );
			em.remove( randomNewGuy );
			tx.commit();

			tx.begin();
			assertEquals(
					countBefore + 2,
					em.createNativeQuery( "SELECT * FROM PlaceSorcererUpdatesHsearch" ).getResultList().size()
			);
			tx.commit();

			tx.begin();
			assertEquals(
					1,
					em.createNativeQuery( "SELECT * FROM PlaceSorcererUpdatesHsearch WHERE eventCase = " + EventType.DELETE )
							.getResultList()
							.size()
			);
			tx.commit();

			JPAUpdateSource updateSource = new JPAUpdateSource(
					parser.parse( new HashSet<>( Arrays.asList( Place.class, Sorcerer.class ) ) ),
					emf,
					null,
					1,
					TimeUnit.SECONDS,
					1,
					1
			);
			updateSource.setUpdateConsumers(
					Arrays.asList(
							new UpdateConsumer() {

								@Override
								public void updateEvent(List<UpdateEventInfo> arg0) {

								}

							}
					)
			);

			updateSource.start();
			Sleep.sleep(
					100_000, () -> {
						tx.begin();
						try {
							return em.createNativeQuery( "SELECT * FROM PlaceSorcererUpdatesHsearch" )
									.getResultList()
									.size() == 0;
						}
						finally {
							tx.commit();
						}
					},
					100, ""
			);

			if ( exceptionString != null ) {
				fail( exceptionString );
			}
		}
		finally {
			em.close();
		}
	}

	@After
	public void shutdown() {
		try {
			this.tearDownTriggers();
		}
		catch (SQLException e) {
			e.printStackTrace();
		}
		if ( this.emf != null ) {
			this.emf.close();
			this.emf = null;
		}
		// cleanup the MySQL driver?!
		Enumeration<Driver> drivers = DriverManager.getDrivers();
		Driver d = null;
		while ( drivers.hasMoreElements() ) {
			try {
				d = drivers.nextElement();
				DriverManager.deregisterDriver( d );
			}
			catch (SQLException e) {
				throw new RuntimeException( e );
			}
		}
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		Thread[] threadArray = threadSet.toArray( new Thread[threadSet.size()] );
		for ( Thread t : threadArray ) {
			if ( t.getName().contains( "Abandoned connection cleanup thread" ) ) {
				synchronized (t) {
					t.stop(); // don't complain, it works
				}
			}
		}
	}

}
