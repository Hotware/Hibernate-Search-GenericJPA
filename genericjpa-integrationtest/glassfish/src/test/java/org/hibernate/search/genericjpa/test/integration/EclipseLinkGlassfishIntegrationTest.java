/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.integration;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.hibernate.search.genericjpa.test.entities.Game;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.jpa.Search;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Martin Braun
 */
@RunWith(Arquillian.class)
public class EclipseLinkGlassfishIntegrationTest {

	@Deployment
	public static Archive<?> createDeployment() {
		return IntegrationTestUtil.createEclipseLinkMySQLDeployment();
	}
	
	private static final String[] GAME_TITLES = { "Super Mario Brothers", "Mario Kart", "F-Zero" };

	@PersistenceContext
	public EntityManager em;

	@Inject
	public UserTransaction utx;

	@Before
	public void setup() throws Exception {
		this.clearData();
		this.insertData();
		this.startTransaction();
	}

	@After
	public void commitTransaction() throws Exception {
		utx.commit();
	}

	private void clearData() throws Exception {
		utx.begin();
		em.joinTransaction();
		System.out.println( "Dumping old records..." );
		em.createQuery( "delete from Game" ).executeUpdate();
		utx.commit();

		FullTextEntityManager fem = Search.getFullTextEntityManager( this.em );
		fem.beginSearchTransaction();
		fem.purgeAll( Game.class );
		fem.commitSearchTransaction();
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

	@SuppressWarnings("unchecked")
	@Test
	public void shouldFindAllGamesInIndex() throws Exception {
		Thread.sleep( 1000 );

		List<Game> games = new ArrayList<>();
		FullTextEntityManager fem = Search.getFullTextEntityManager( this.em );
		for ( String title : GAME_TITLES ) {
			FullTextQuery query = fem.createFullTextQuery( new TermQuery( new Term( "title", title ) ), Game.class );
			games.addAll( query.getResultList() );
		}

		System.out.println( "Found " + games.size() + " games (using Hibernate-Search):" );
		assertContainsAllGames( games );
	}

	@Test
	public void testManualIndexing() throws Exception {
		Thread.sleep( 1000 );

		FullTextEntityManager fem = Search.getFullTextEntityManager( this.em );
		fem.beginSearchTransaction();
		Game newGame = new Game( "Legend of Zelda" );
		fem.index( newGame );
		fem.commitSearchTransaction();
		Thread.sleep( 500 );
		FullTextQuery fullTextQuery = fem.createFullTextQuery( new TermQuery( new Term( "title", "Legend of Zelda" ) ), Game.class );
		// we can find it in the index even though it is not persisted in the database
		assertEquals( 1, fullTextQuery.getResultSize() );
		// but no result should be returned here:
		assertEquals( 0, fullTextQuery.getResultList().size() );
	}

	@Test
	public void testRollback() throws Exception {
		Thread.sleep( 1000 );

		FullTextEntityManager fem = Search.getFullTextEntityManager( this.em );
		fem.beginSearchTransaction();
		Game newGame = new Game( "Pong" );
		fem.index( newGame );
		Thread.sleep( 500 );
		fem.rollbackSearchTransaction();
		FullTextQuery fullTextQuery = fem.createFullTextQuery( new TermQuery( new Term( "title", "Pong" ) ), Game.class );
		// we can find it in the index even though it is not persisted in the database
		assertEquals( 0, fullTextQuery.getResultSize() );
		// no result should be returned here either
		assertEquals( 0, fullTextQuery.getResultList().size() );
	}

	@Test
	public void testUnwrap() {
		FullTextEntityManager fem = Search.getFullTextEntityManager( this.em );
		assertEquals( fem, fem.unwrap( FullTextEntityManager.class ) );

		FullTextQuery query = fem.createFullTextQuery( new MatchAllDocsQuery(), Game.class );
		assertEquals( query, query.unwrap( FullTextQuery.class ) );
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

}
