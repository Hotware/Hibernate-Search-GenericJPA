/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.batchindexing.IdProducerTask;
import org.hibernate.search.genericjpa.batchindexing.ObjectHandlerTask;
import org.hibernate.search.genericjpa.db.events.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.db.events.UpdateConsumer.UpdateInfo;
import org.hibernate.search.genericjpa.test.db.events.jpa.MetaModelParser;
import org.hibernate.search.genericjpa.test.jpa.entities.Place;
import org.hibernate.search.genericjpa.test.jpa.entities.Sorcerer;
import org.hibernate.search.genericjpa.util.Sleep;
import org.hibernate.search.jpa.FullTextEntityManager;
import org.hibernate.search.jpa.Search;
import org.hibernate.search.query.DatabaseRetrievalMethod;
import org.hibernate.search.query.ObjectLookupMethod;
import org.hibernate.search.standalone.entity.EntityProvider;
import org.hibernate.search.standalone.factory.StandaloneSearchFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IntegrationTest {

	private int valinorId = 0;
	private Place valinor;
	private EntityManagerFactory emf;
	private EntityManager em;
	private StandaloneSearchFactory searchFactory;

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
		ObjectHandlerTask objHandlerTask = new ObjectHandlerTask() {

			private boolean hadOne = false;
			private List<UpdateInfo> batch;

			@Override
			public void run() {
				if ( !hadOne ) {
					assertEquals( "Helm's Deep", IntegrationTest.this.em.find( Place.class, this.batch.get( 0 ).getId() ).getName() );
					hadOne = true;
				}
				else {
					assertEquals( "Valinor", IntegrationTest.this.em.find( Place.class, this.batch.get( 0 ).getId() ).getName() );
				}
			}

			@Override
			public ObjectHandlerTask batch(List<UpdateInfo> batch) {
				this.batch = batch;
				return this;
			}

		};
		IdProducerTask idProducer = new IdProducerTask( Place.class, "id", this.emf, false, 1, 1, null, (clazz) -> {
			return objHandlerTask;
		} );
		idProducer.run();

	}

	// TODO: different test class?
	@Test
	public void testObjectHandlerWithIdProducerTask() {
		// TODO:
	}

	@Test
	public void testJPAInterfaces() throws InterruptedException {
		FullTextEntityManager fem = Search.getFullTextEntityManager( em );
		fem.beginSearchTransaction();

		Sleep.sleep(
				5000,
				() -> {
					return 2 == fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class )
							.initializeObjectsWith( ObjectLookupMethod.SKIP, DatabaseRetrievalMethod.QUERY ).getResultList().size();
				}, 100, "coudln't find all entities in index!" );

		fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class ).initializeObjectsWith( ObjectLookupMethod.SKIP, DatabaseRetrievalMethod.QUERY )
				.entityProvider( new EntityProvider() {

					@Override
					public void close() throws IOException {
						// no-op
					}

					@Override
					public List getBatch(Class<?> entityClass, List<Object> id) {
						// this should happen!
						return Collections.emptyList();
					}

					@Override
					public Object get(Class<?> entityClass, Object id) {
						throw new AssertionError( "should have used getBatch instead!" );
					}

				} ).getResultList();

		fem.createFullTextQuery( new MatchAllDocsQuery(), Place.class ).initializeObjectsWith( ObjectLookupMethod.SKIP, DatabaseRetrievalMethod.FIND_BY_ID )
				.entityProvider( new EntityProvider() {

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
						try {
							return entityClass.newInstance();
						}
						catch (InstantiationException | IllegalAccessException e) {
							throw new RuntimeException( e );
						}
					}

				} ).getResultList();

		fem.commitSearchTransaction();
	}

	@Before
	public void setup() {
		this.emf = Persistence.createEntityManagerFactory( "EclipseLink_MySQL" );
		Properties properties = new Properties();
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.triggerSource", MySQLTriggerSQLStringSource.class.getName() );
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.type", "sql");
		this.searchFactory = Setup.createUnmanagedSearchFactory( emf, properties, null );
		EntityManager em = emf.createEntityManager();
		try {
			EntityTransaction tx = em.getTransaction();
			tx.begin();

			@SuppressWarnings("unchecked")
			List<Place> toDelete = new ArrayList<>( em.createQuery( "SELECT a FROM Place a" ).getResultList() );
			for ( Place place : toDelete ) {
				em.remove( place );
			}
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

			valinorId = valinor.getId();

			this.valinor = valinor;

			em.flush();
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
