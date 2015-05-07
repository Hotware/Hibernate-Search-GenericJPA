/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.integration;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;

import org.hibernate.search.genericjpa.test.entities.Game;
import org.hibernate.search.genericjpa.test.searchFactory.EJBSearchFactory;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(Arquillian.class)
public class GlassfishIntegrationTest {

	@Deployment
	public static Archive<?> createDeployment() {
		return ShrinkWrap.create( WebArchive.class, "test.war" ).setWebXML( "web.xml" ).addPackage( Game.class.getPackage() )
				.addPackage( EJBSearchFactory.class.getPackage() ).addAsResource( "META-INF/persistence.xml", "META-INF/persistence.xml" )
				.addAsWebInfResource( EmptyAsset.INSTANCE, "beans.xml" );
	}

	private static final String[] GAME_TITLES = { "Super Mario Brothers", "Mario Kart", "F-Zero" };

	@PersistenceContext
	EntityManager em;

	@Inject
	UserTransaction utx;

	@Before
	public void preparePersistenceTest() throws Exception {
		clearData();
		insertData();
		startTransaction();
	}

	private void clearData() throws Exception {
		utx.begin();
		em.joinTransaction();
		System.out.println( "Dumping old records..." );
		em.createQuery( "delete from Game" ).executeUpdate();
		utx.commit();
	}

	private void insertData() throws Exception {
		utx.begin();
		em.joinTransaction();
		System.out.println( "Inserting records..." );
		for ( String title : GAME_TITLES ) {
			Game game = new Game( title );
			em.persist( game );
		}
		utx.commit();
		// clear the persistence context (first-level cache)
		em.clear();
	}

	@Test
	public void shouldFindAllGamesUsingJpqlQuery() throws Exception {
		// given
		String fetchingAllGamesInJpql = "select g from Game g order by g.id";

		// when
		System.out.println( "Selecting (using JPQL)..." );
		List<Game> games = em.createQuery( fetchingAllGamesInJpql, Game.class ).getResultList();

		// then
		System.out.println( "Found " + games.size() + " games (using JPQL):" );
		assertContainsAllGames( games );
	}

	private static void assertContainsAllGames(Collection<Game> retrievedGames) {
		Assert.assertEquals( GAME_TITLES.length, retrievedGames.size() );
		final Set<String> retrievedGameTitles = new HashSet<String>();
		for ( Game game : retrievedGames ) {
			System.out.println( "* " + game );
			retrievedGameTitles.add( game.getTitle() );
		}
		Assert.assertTrue( retrievedGameTitles.containsAll( Arrays.asList( GAME_TITLES ) ) );
	}

	private void startTransaction() throws Exception {
		utx.begin();
		em.joinTransaction();
	}

	@After
	public void commitTransaction() throws Exception {
		utx.commit();
	}

}
