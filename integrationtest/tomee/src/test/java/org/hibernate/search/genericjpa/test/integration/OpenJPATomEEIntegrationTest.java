/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.integration;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.UserTransaction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.Archive;

import org.hibernate.search.genericjpa.JPASearchFactoryController;
import org.hibernate.search.genericjpa.test.entities.Game;
import org.hibernate.search.genericjpa.util.Sleep;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * @author Martin Braun
 */
@RunWith(Arquillian.class)
public class OpenJPATomEEIntegrationTest {

	private static final String[] GAME_TITLES = {"Super Mario Brothers", "Mario Kart", "F-Zero"};
	@PersistenceContext
	public EntityManager em;
	@Inject
	public UserTransaction utx;
	boolean firstStart = true;
	@Inject
	private JPASearchFactoryController searchFactory;

	@Deployment
	public static Archive<?> createDeployment() {
		return IntegrationTestUtil.createOpenJPAMySQLDeployment();
	}

	private static boolean assertContainsAllGames(Collection<Game> retrievedGames) {
		final Set<String> retrievedGameTitles = new HashSet<String>();
		for ( Game game : retrievedGames ) {
			System.out.println( "* " + game );
			retrievedGameTitles.add( game.getTitle() );
		}
		return GAME_TITLES.length == retrievedGames.size() && retrievedGameTitles.containsAll(
				Arrays.asList(
						GAME_TITLES
				)
		);
	}

	@Before
	public void setup() throws Exception {
		if ( this.firstStart ) {
			Thread.sleep( 3000 );
			this.firstStart = false;
		}
		this.clearData();
		this.insertData();
		this.startTransaction();
	}

	@After
	public void commitTransaction() throws Exception {
		em.clear();
		utx.commit();
	}

	private void clearData() throws Exception {
		utx.begin();
		em.joinTransaction();
		System.out.println( "Dumping old records..." );
		em.createQuery( "delete from Game" ).executeUpdate();
		utx.commit();

		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );
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
		Sleep.sleep(
				5000, () -> {
					List<Game> games = new ArrayList<>();
					FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );
					for ( String title : GAME_TITLES ) {
						FullTextQuery query = fem.createFullTextQuery(
								new TermQuery( new Term( "title", title ) ),
								Game.class
						);
						games.addAll( query.getResultList() );
					}

					System.out.println( "Found " + games.size() + " games (using Hibernate-Search):" );
					return assertContainsAllGames( games );
				}, 100, "coudln't find all games!"
		);
	}

	@Test
	public void testManualIndexing() throws Exception {
		this.shouldFindAllGamesInIndex();
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );
		fem.beginSearchTransaction();
		Game newGame = new Game( "Legend of Zelda" );
		fem.index( newGame );
		fem.commitSearchTransaction();
		Sleep.sleep(
				5000, () -> {
					FullTextQuery fullTextQuery = fem.createFullTextQuery(
							new TermQuery(
									new Term(
											"title",
											"Legend of Zelda"
									)
							), Game.class
					);
					// we can find it in the index even though it is not persisted in the database
					boolean val1 = 1 == fullTextQuery.getResultSize();
					// but no result should be returned here:
					boolean val2 = 0 == fullTextQuery.getResultList().size();
					return val1 && val2;
				}, 100, ""
		);
	}

	@Test
	public void testRollback() throws Exception {
		this.shouldFindAllGamesInIndex();

		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );
		fem.beginSearchTransaction();
		Game newGame = new Game( "Pong" );
		fem.index( newGame );
		fem.rollbackSearchTransaction();
		Sleep.sleep(
				5000, () -> {
					FullTextQuery fullTextQuery = fem.createFullTextQuery(
							new TermQuery( new Term( "title", "Pong" ) ),
							Game.class
					);
					// we can find it in the index even though it is not persisted in the database
					boolean val1 = 0 == fullTextQuery.getResultSize();
					// no result should be returned here either
					boolean val2 = 0 == fullTextQuery.getResultList().size();
					return val1 && val2;
				}, 100, ""
		);
	}

	@Test
	public void testUnwrap() {
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );
		assertEquals( fem, fem.unwrap( FullTextEntityManager.class ) );

		FullTextQuery query = fem.createFullTextQuery( new MatchAllDocsQuery(), Game.class );
		assertEquals( query, query.unwrap( FullTextQuery.class ) );
	}

	private void startTransaction() throws Exception {
		utx.begin();
		em.joinTransaction();
	}

}
