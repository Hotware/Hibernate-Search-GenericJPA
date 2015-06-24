/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;

import org.hibernate.search.backend.impl.batch.DefaultBatchBackend;
import org.hibernate.search.backend.spi.BatchBackend;
import org.hibernate.search.genericjpa.Constants;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.batchindexing.impl.IdProducerTask;
import org.hibernate.search.genericjpa.batchindexing.impl.ObjectHandlerTask;
import org.hibernate.search.genericjpa.db.events.EventType;
import org.hibernate.search.genericjpa.db.events.triggers.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.entity.EntityManagerEntityProvider;
import org.hibernate.search.genericjpa.entity.EntityProvider;
import org.hibernate.search.genericjpa.exception.SearchException;
import org.hibernate.search.genericjpa.factory.StandaloneSearchFactory;
import org.hibernate.search.genericjpa.impl.JPASearchFactoryAdapter;
import org.hibernate.search.genericjpa.test.db.events.jpa.MetaModelParser;
import org.hibernate.search.genericjpa.test.jpa.entities.NonJPAEntity;
import org.hibernate.search.genericjpa.test.jpa.entities.Place;
import org.hibernate.search.genericjpa.test.jpa.entities.Sorcerer;
import org.hibernate.search.genericjpa.test.jpa.entities.TestDto;
import org.hibernate.search.genericjpa.util.Sleep;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.FullTextQuery;
import org.hibernate.search.query.DatabaseRetrievalMethod;
import org.hibernate.search.query.ObjectLookupMethod;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IntegrationTest {

	private int valinorId = 0;
	private int helmsDeepId = 0;

	private EntityManagerFactory emf;
	private EntityManager em;
	private JPASearchFactoryAdapter searchFactory;

	@Test
	public void metaModelParser() throws IOException {
		EntityProvider entityProvider = null;
		StandaloneSearchFactory searchFactory = null;
		try {
			MetaModelParser parser = new MetaModelParser();
			parser.parse( this.emf.getMetamodel() );
			{
				assertEquals( 4, parser.getIndexRelevantEntites().size() );
			}
		}
		finally {
			if ( entityProvider != null ) {
				entityProvider.close();
			}
			if ( searchFactory != null ) {
				searchFactory.close();
			}
		}
	}

	// TODO: different test class?
	@Test
	public void testIdProducerTask() {
		this.testIdProducerTask( 2, 1 );
		this.testIdProducerTask( 1, 1 );
		this.testIdProducerTask( 1, 2 );
	}

	private void testIdProducerTask(int batchSizeToLoadIds, int batchSizeToLoadObjects) {
		IdProducerTask idProducer = new IdProducerTask(
				Place.class, "id", this.emf, null, batchSizeToLoadIds, batchSizeToLoadObjects, new UpdateConsumer() {

			private boolean hadOne = false;

			@Override
			public void updateEvent(List<UpdateInfo> batch) {
				if ( !hadOne ) {
					assertEquals(
							"Helm's Deep", IntegrationTest.this.em.find( Place.class, batch.get( 0 ).getId() )
									.getName()
					);
					hadOne = true;
				}
				else {
					assertEquals(
							"Valinor", IntegrationTest.this.em.find( Place.class, batch.get( 0 ).getId() )
									.getName()
					);
				}
			}

		}, null, null
		);
		idProducer.run();
	}

	@Test
	public void testQueryDto() {
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );

		List<TestDto> testDtos = fem.createFullTextQuery(
				new TermQuery(
						new Term(
								"id",
								String.valueOf( this.valinorId )
						)
				), Place.class
		)
				.queryDto( TestDto.class );
		assertEquals( "Valinor", testDtos.get( 0 ).getField() );
	}

	@Test
	public void testNullEntityManager() {
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( null );
		try {
			//basic queries are allowed
			{
				FullTextQuery ftQuery = fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class );
				assertEquals( 2, ftQuery.getResultSize() );
				assertEquals( 2, ftQuery.queryDto( TestDto.class ).size() );
			}

			//purging as well
			fem.beginSearchTransaction();
			fem.purgeAll( Place.class );
			fem.commitSearchTransaction();
			{
				FullTextQuery ftQuery = fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class );
				assertEquals( 0, ftQuery.getResultSize() );
				assertEquals( 0, ftQuery.queryDto( TestDto.class ).size() );
			}

			//should work as well
			fem.createIndexer().startAndWait();
			{
				FullTextQuery ftQuery = fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class );
				assertEquals( 2, ftQuery.getResultSize() );
				assertEquals( 2, ftQuery.queryDto( TestDto.class ).size() );
			}
			//TODO: do we really need to test this for purgeByTerm (?)
		}
		catch (InterruptedException e) {
			throw new SearchException( e );
		}
		finally {
			fem.close();
		}
	}

	@Test
	public void testQueryDtoWithProfile() {
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );

		List<TestDto> testDtos = fem.createFullTextQuery(
				new TermQuery(
						new Term(
								"id",
								String.valueOf( this.valinorId )
						)
				), Place.class
		)
				.queryDto( TestDto.class, "ID_PROFILE" );
		assertEquals( this.valinorId, testDtos.get( 0 ).getField() );
	}

	@Test
	public void testObjectHandlerTask() {
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );
		fem.beginSearchTransaction();
		fem.purgeAll( Place.class );
		fem.commitSearchTransaction();

		Map<Class<?>, String> idProperties = new HashMap<>();
		idProperties.put( Place.class, "id" );
		BatchBackend batchBackend = new DefaultBatchBackend( this.searchFactory.getSearchIntegrator(), null );
		ObjectHandlerTask handler = new ObjectHandlerTask(
				batchBackend, Place.class, this.searchFactory.getSearchIntegrator().getIndexBinding( Place.class ),
				() -> new EntityManagerEntityProvider( this.em, idProperties ), (x, y) -> {

		}, this.emf.getPersistenceUnitUtil()
		);

		List<UpdateInfo> batch = new ArrayList<>();
		batch.add( new UpdateInfo( Place.class, this.valinorId, EventType.INSERT ) );
		batch.add( new UpdateInfo( Place.class, this.helmsDeepId, EventType.INSERT ) );

		handler.batch( batch );
		handler.run();

		batchBackend.flush( new HashSet<>( Arrays.asList( Place.class ) ) );

		assertEquals( 2, fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class ).getResultList().size() );
	}

	@Test
	public void testJPAQueryInterfaces() throws InterruptedException {
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( em );
		fem.beginSearchTransaction();

		Sleep.sleep(
				5000,
				() -> {
					return 2 == fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class )
							.initializeObjectsWith( ObjectLookupMethod.SKIP, DatabaseRetrievalMethod.QUERY )
							.getResultList()
							.size();
				}, 100, "coudln't find all entities in index!"
		);

		//assert for 0 is needed here, because we want to test if the query does filter out stuff
		//that is not found in the database anymore properly
		assertEquals(
				0, fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class ).initializeObjectsWith(
						ObjectLookupMethod.SKIP,
						DatabaseRetrievalMethod.QUERY
				)
						.entityProvider(
								new EntityProvider() {

									@Override
									public void close() throws IOException {
										// no-op
									}

									@Override
									public List getBatch(Class<?> entityClass, List<Object> id) {
										// this should happen!
										// an empty list is actually quite interesting for the backend.
										// does it handle not finding anything for a given entityClass right?
										return Collections.emptyList();
									}

									@Override
									public Object get(Class<?> entityClass, Object id) {
										throw new AssertionError( "should have used getBatch instead!" );
									}

								}
						).getResultList().size()
		);

		assertEquals(
				0, fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class ).initializeObjectsWith(
						ObjectLookupMethod.SKIP,
						DatabaseRetrievalMethod.FIND_BY_ID
				)
						.entityProvider(
								new EntityProvider() {

									@Override
									public void close() throws IOException {
										// no-op
									}

									@Override
									public List getBatch(Class<?> entityClass, List<Object> id) {
										throw new AssertionError( "should have used get instead!" );
									}

									@Override
									public Object get(Class<?> entityClass, Object id) {
										return null;
									}

								}
						).getResultList().size()
		);

		fem.commitSearchTransaction();
	}

	@Test
	public void testMassIndexerWithNonJPAEntityPresent() throws InterruptedException {
		//this shouldn't throw an Exception while having a NonJPAEntity present?
		this.searchFactory.createMassIndexer().startAndWait();

		//this should be sufficient even though we are not checking for index sizes
	}

	@Test
	public void testDeleteByQuery() {
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );

		//TODO: test if all the Sorcerers still available?

		fem.beginSearchTransaction();
		fem.purgeByTerm( Place.class, "id", this.valinorId );
		fem.commitSearchTransaction();

		//TODO: test this for the other query types

		assertEquals(
				0, fem.createFullTextQuery( new TermQuery( new Term( "name", "Valinor" ) ), Place.class )
						.getResultList()
						.size()
		);
	}

	@Test
	public void testMultipleEntityQuery() {
		FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( this.em );

		BooleanQuery query = new BooleanQuery();

		query.add(
				fem.getSearchFactory()
						.buildQueryBuilder()
						.forEntity( Place.class )
						.get()
						.keyword()
						.onField( "name" )
						.matching( "Valinor" ).createQuery(), BooleanClause.Occur.SHOULD
		);
		query.add(
				fem.getSearchFactory().buildQueryBuilder().forEntity( Sorcerer.class ).get().keyword().onField(
						"name"
				).matching( "Saruman" ).createQuery(), BooleanClause.Occur.SHOULD
		);

		FullTextQuery ftQuery = fem.createFullTextQuery( query, Place.class, Sorcerer.class );
		assertEquals( 2, ftQuery.getResultSize() );

		this.testFoundSorcererAndPlace( ftQuery );

		//test this for FIND_BY_ID as well
		ftQuery.initializeObjectsWith( ObjectLookupMethod.SKIP, DatabaseRetrievalMethod.FIND_BY_ID );
		this.testFoundSorcererAndPlace( ftQuery );
	}

	private void testFoundSorcererAndPlace(FullTextQuery ftQuery) {
		boolean[] found = new boolean[2];
		ftQuery.getResultList().stream().forEach(
				(ent) -> {
					if ( ent instanceof Place ) {
						found[0] = true;
					}
					else if ( ent instanceof Sorcerer ) {
						found[1] = true;
					}
					else {
						throw new AssertionError();
					}
				}
		);
		for ( boolean fd : found ) {
			assertTrue( "did not find Sorcerer AND Place in Query", fd );
		}
	}

	@Before
	public void setup() {
		this.emf = Persistence.createEntityManagerFactory( "EclipseLink_MySQL" );
		Properties properties = new Properties();
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.name", "test" );
		properties.setProperty(
				"org.hibernate.search.genericjpa.searchfactory.triggerSource",
				MySQLTriggerSQLStringSource.class.getName()
		);
		properties.setProperty( Constants.ADDITIONAL_INDEXED_TYPES_KEY, NonJPAEntity.class.getName() );
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.type", "manual-updates" );
		this.searchFactory = (JPASearchFactoryAdapter) Setup.createSearchFactory( this.emf, properties );
		EntityManager em = emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			em.createQuery( "DELETE FROM Place" ).executeUpdate();
			em.flush();

			em.createQuery( "DELETE FROM Sorcerer" ).executeUpdate();
			em.flush();

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

			em.flush();

			FullTextEntityManager fem = this.searchFactory.getFullTextEntityManager( em );
			fem.beginSearchTransaction();
			//this will index the Sorcerers as well
			fem.index( valinor );
			fem.index( helmsDeep );
			fem.commitSearchTransaction();

			tx.commit();
		}
		finally {
			if ( em != null ) {
				em.close();
			}
		}
		this.em = this.emf.createEntityManager();
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
