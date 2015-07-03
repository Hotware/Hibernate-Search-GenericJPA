/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa.util;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.jpa.util.MultiQueryAccess;
import org.hibernate.search.genericjpa.test.db.events.jpa.DatabaseIntegrationTest;
import org.hibernate.search.genericjpa.test.db.events.jpa.JPAUpdateSourceTest;
import org.hibernate.search.genericjpa.test.jpa.entities.PlaceSorcererUpdates;
import org.hibernate.search.genericjpa.test.jpa.entities.PlaceUpdates;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/**
 * @author Martin
 */
public class MultiQueryAccessTest extends DatabaseIntegrationTest {

	@Before
	public void setup() throws NoSuchFieldException, SQLException {
		this.setup( "EclipseLink" );

		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();

			EntityTransaction tx = em.getTransaction();

			// hacky, this doesn't belong in the setup method, but whatever
			tx.begin();
			{
				MultiQueryAccess access = this.query( em );
				assertFalse( access.next() );
				try {
					access.get();
					fail( "expected IllegalStateException" );
				}
				catch (IllegalStateException e) {
					// nothing to see here.
				}
			}
			tx.commit();
			System.out.println( "passed false MultiQueryAccessTest" );

			List<Object> del = new ArrayList<>();

			tx.begin();
			{
				PlaceSorcererUpdates up = new PlaceSorcererUpdates();
				up.setEventType( EventType.INSERT );
				up.setId( 1L );
				up.setPlaceId( 123123 );
				up.setSorcererId( 123 );
				em.persist( up );
			}
			em.flush();

			{
				PlaceSorcererUpdates up = new PlaceSorcererUpdates();
				up.setEventType( EventType.INSERT );
				up.setId( 2L );
				up.setPlaceId( 123123 );
				up.setSorcererId( 123 );
				em.persist( up );
				del.add( up );
			}
			em.flush();

			{
				PlaceSorcererUpdates up = new PlaceSorcererUpdates();
				up.setEventType( EventType.UPDATE );
				up.setId( 3L );
				up.setPlaceId( 123123 );
				up.setSorcererId( 123 );
				em.persist( up );
			}
			em.flush();

			{
				PlaceUpdates up = new PlaceUpdates();
				up.setEventType( EventType.INSERT );
				up.setId( 1 );
				up.setPlaceId( 233 );
				em.persist( up );
				del.add( up );
			}
			em.flush();

			{
				PlaceUpdates up = new PlaceUpdates();
				up.setEventType( EventType.DELETE );
				up.setId( 2 );
				up.setPlaceId( 233 );
				em.persist( up );
			}
			em.flush();

			tx.commit();

			tx.begin();

			// we have to delete stuff here because of the autoincrement thingy
			// in the Updates classes if this is changed, this Test is still
			// correct because we set the ids right
			for ( Object obj : del ) {
				em.remove( obj );
			}

			tx.commit();
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
		System.out.println( "setup complete!" );
	}

	@Test
	public void test() throws NoSuchFieldException, SQLException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException {
		EntityManager em = null;
		try {
			em = this.emf.createEntityManager();
			EntityTransaction tx = em.getTransaction();

			List<Integer> eventOrder = new ArrayList<>(
					Arrays.asList(
							EventType.INSERT,
							EventType.DELETE,
							EventType.UPDATE
					)
			);

			tx.begin();
			{
				MultiQueryAccess access = this.query( em );
				int cnt = 0;
				while ( access.next() ) {
					Object obj = access.get();
					if ( obj instanceof PlaceUpdates ) {
						assertEquals( eventOrder.remove( 0 ), ((PlaceUpdates) obj).getEventType() );
					}
					else if ( obj instanceof PlaceSorcererUpdates ) {
						assertEquals( eventOrder.remove( 0 ), ((PlaceSorcererUpdates) obj).getEventType() );
					}
					++cnt;
				}
				assertEquals( 3, cnt );
			}
			tx.commit();

		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
	}

	private MultiQueryAccess query(EntityManager em) throws NoSuchFieldException {
		return JPAUpdateSourceTest.query( this.emf, em );
	}

}
