/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.db.events.jpa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.jpa.JPAUpdateSource;
import org.hibernate.search.genericjpa.test.jpa.entities.Place;
import org.hibernate.search.genericjpa.test.jpa.entities.PlaceSorcererUpdates;
import org.hibernate.search.genericjpa.test.jpa.entities.PlaceUpdates;
import org.hibernate.search.genericjpa.test.jpa.entities.Sorcerer;
import org.junit.Test;

/**
 * @author Martin
 */
public class MySQLTriggerIntegrationTest extends DatabaseIntegrationTest {

	@Test
	public void testMySQLIntegration() throws SQLException, InterruptedException {
		this.setup( "EclipseLink_MySQL" );
		this.setupTriggers();

		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			EntityTransaction tx = em.getTransaction();
			tx.begin();
			java.sql.Connection connection = em.unwrap( java.sql.Connection.class );
			connection.setAutoCommit( false );

			tx.commit();

			tx.begin();
			int countBefore = em.createQuery( "SELECT a FROM PlaceSorcererUpdates a" ).getResultList().size();
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
			assertEquals( countBefore + 1, em.createQuery( "SELECT a FROM PlaceSorcererUpdates a" ).getResultList().size() );
			tx.commit();

			tx.begin();
			assertEquals( 1, em.createQuery( "SELECT a FROM PlaceSorcererUpdates a WHERE a.eventType = " + EventType.INSERT ).getResultList().size() );
			tx.commit();

			tx.begin();
			valinorDb.getSorcerers().remove( randomNewGuy );
			em.remove( randomNewGuy );
			tx.commit();

			tx.begin();
			assertEquals( countBefore + 2, em.createQuery( "SELECT a FROM PlaceSorcererUpdates a" ).getResultList().size() );
			tx.commit();

			tx.begin();
			assertEquals( 1, em.createQuery( "SELECT a FROM PlaceSorcererUpdates a WHERE a.eventType = " + EventType.DELETE ).getResultList().size() );
			tx.commit();

			JPAUpdateSource updateSource = new JPAUpdateSource(
					parser.parse( new HashSet<>( Arrays.asList( PlaceSorcererUpdates.class, PlaceUpdates.class ) ) ), emf, false, 1, TimeUnit.SECONDS, 1, 1 );
			updateSource.setUpdateConsumers( Arrays.asList( new UpdateConsumer() {

				@Override
				public void updateEvent(List<UpdateInfo> arg0) {

				}

			} ) );

			updateSource.start();
			Thread.sleep( 1000 );
			tx.begin();
			assertEquals( 0, em.createQuery( "SELECT a FROM PlaceSorcererUpdates a" ).getResultList().size() );
			tx.commit();

			if ( exceptionString != null ) {
				fail( exceptionString );
			}
		}
		finally {
			if ( em != null ) {
				em.close();
			}
			this.tearDownTriggers();
		}
	}
}
