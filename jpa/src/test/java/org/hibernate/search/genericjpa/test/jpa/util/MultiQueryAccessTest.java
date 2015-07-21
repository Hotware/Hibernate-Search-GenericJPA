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
import org.hibernate.search.genericjpa.db.events.triggers.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.jpa.util.MultiQueryAccess;
import org.hibernate.search.genericjpa.test.db.events.jpa.DatabaseIntegrationTest;
import org.hibernate.search.genericjpa.test.db.events.jpa.JPAUpdateSourceTest;

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
		this.setup( "EclipseLink_MySQL", new MySQLTriggerSQLStringSource() );

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
				em.createNativeQuery(
						String.format(
								"INSERT INTO PlaceSorcererUpdatesHsearch(updateid, eventCase, placefk, sorcererfk) VALUES (1, %s, 123123, 123)",
								String.valueOf( EventType.INSERT )
						)
				).executeUpdate();
			}
			em.flush();

			{
				em.createNativeQuery(
						String.format(
								"INSERT INTO PlaceSorcererUpdatesHsearch(updateid, eventCase, placefk, sorcererfk) VALUES (5, %s, 123123, 123)",
								String.valueOf( EventType.INSERT )
						)
				).executeUpdate();
			}
			em.flush();

			{
				em.createNativeQuery(
						String.format(
								"INSERT INTO PlaceSorcererUpdatesHsearch(updateid, eventCase, placefk, sorcererfk) VALUES (4, %s, 123123, 123)",
								String.valueOf( EventType.UPDATE )
						)
				).executeUpdate();
			}
			em.flush();

			{
				em.createNativeQuery(
						String.format(
								"INSERT INTO PlaceUpdatesHsearch(updateid, eventCase, placefk) VALUES (3, %s, 233)",
								String.valueOf( EventType.UPDATE )
						)
				).executeUpdate();
			}
			em.flush();

			{
				em.createNativeQuery(
						String.format(
								"INSERT INTO PlaceUpdatesHsearch(updateid, eventCase, placefk) VALUES (2, %s, 233)",
								String.valueOf( EventType.DELETE )
						)
				).executeUpdate();
			}
			em.flush();

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
							EventType.UPDATE,
							EventType.UPDATE,
							EventType.INSERT
					)
			);

			tx.begin();
			{
				MultiQueryAccess access = this.query( em );
				int cnt = 0;
				while ( access.next() ) {
					Object[] obj = (Object[]) access.get();
					if ( access.identifier().equals( "PlaceUpdatesHsearch" ) ) {
						assertEquals( eventOrder.remove( 0 ), obj[1] );
					}
					else if ( access.identifier().equals( "PlaceSorcererUpdatesHsearch" ) ) {
						assertEquals( eventOrder.remove( 0 ), obj[1] );
					}
					++cnt;
				}
				assertEquals( 5, cnt );
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
