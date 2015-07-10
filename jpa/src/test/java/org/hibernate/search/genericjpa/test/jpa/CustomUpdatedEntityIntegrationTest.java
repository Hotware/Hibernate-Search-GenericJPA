/*
 * Hibernate Search, full-text search for your domain model
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.search.genericjpa.test.jpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.List;
import java.util.Properties;

import org.apache.lucene.search.MatchAllDocsQuery;

import org.hibernate.search.genericjpa.Constants;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.db.events.triggers.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.entity.EntityManagerEntityProviderAdapter;
import org.hibernate.search.genericjpa.impl.JPASearchFactoryAdapter;
import org.hibernate.search.genericjpa.test.jpa.entities.CustomUpdatedEntity;
import org.hibernate.search.genericjpa.test.jpa.entities.CustomUpdatedEntityEntityProvider;
import org.hibernate.search.genericjpa.test.jpa.entities.NonJPAEntity;
import org.hibernate.search.genericjpa.util.Sleep;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Created by Martin on 10.07.2015.
 */
public class CustomUpdatedEntityIntegrationTest {

	private static final int ENTITY_COUNT = 100;
	private static final String ENT_NAME_SHOULD_NOT_FIND = "shouldnot";

	private EntityManagerFactory emf;
	private JPASearchFactoryAdapter searchFactory;
	private EntityManager em;

	@Test
	public void test() throws InterruptedException {
		Sleep.sleep(
				100_000,
				() -> ENTITY_COUNT == this.searchFactory.getFullTextEntityManager( this.em )
						.createFullTextQuery( new MatchAllDocsQuery(), CustomUpdatedEntity.class )
						.getResultSize()
		);

		//the original name should not be found
		assertEquals(
				0,
				this.searchFactory.getFullTextEntityManager( this.em )
						.createFullTextQuery(
								this.searchFactory.getSearchFactory().buildQueryBuilder().forEntity(
										CustomUpdatedEntity.class
								).get().keyword().onField( "text" ).matching( ENT_NAME_SHOULD_NOT_FIND ).createQuery()
						).getResultSize()
		);

		//but the entities should still be the same
		for ( CustomUpdatedEntity ent : (List<CustomUpdatedEntity>) this.searchFactory.getFullTextEntityManager( this.em )
				.createFullTextQuery(
						this.searchFactory.getSearchFactory().buildQueryBuilder().forEntity(
								CustomUpdatedEntity.class
						).get().keyword().onField( "text" ).matching( ENT_NAME_SHOULD_NOT_FIND ).createQuery()
				).getResultList() ) {
			assertEquals( ENT_NAME_SHOULD_NOT_FIND, ent.getText() );
		}

		this.assertEveryThingThere();

		this.searchFactory.getFullTextEntityManager( this.em )
				.createIndexer( CustomUpdatedEntity.class )
				.entityProvider(
						EntityManagerEntityProviderAdapter.adapt(
								CustomUpdatedEntityEntityProvider.class,
								this.em,
								null
						)
				).startAndWait();
		this.assertEveryThingThere();

		this.searchFactory.getFullTextEntityManager( this.em )
				.createIndexer( CustomUpdatedEntity.class )
				.entityProvider(
						EntityManagerEntityProviderAdapter.adapt(
								CustomUpdatedEntityEntityProvider.class,
								this.emf,
								null, 8
						)
				).startAndWait();
		this.assertEveryThingThere();
	}

	private void assertEveryThingThere() {
		assertEquals(
				ENTITY_COUNT, this.searchFactory.getFullTextEntityManager( this.em )
						.createFullTextQuery(
								this.searchFactory.getSearchFactory()
										.buildQueryBuilder()
										.forEntity(
												CustomUpdatedEntity.class
										)
										.get()
										.keyword()
										.onField( "text" )
										.matching( CustomUpdatedEntityEntityProvider.CUSTOM_TEXT )
										.createQuery()
						).getResultSize()
		);
	}

	@Before
	public void setup() {
		this.emf = Persistence.createEntityManagerFactory( "EclipseLink_MySQL" );
		Properties properties = new Properties();
		properties.setProperty( Constants.SEARCH_FACTORY_NAME_KEY, "test" );
		properties.setProperty( Constants.ADDITIONAL_INDEXED_TYPES_KEY, NonJPAEntity.class.getName() );
		//we do manual updates, so this will be ignored, but let's keep it here
		//if we change our mind later
		properties.setProperty(
				Constants.TRIGGER_SOURCE_KEY,
				MySQLTriggerSQLStringSource.class.getName()
		);
		properties.setProperty(
				Constants.TRIGGER_CREATION_STRATEGY_KEY,
				Constants.TRIGGER_CREATION_STRATEGY_DROP_CREATE
		);
		properties.setProperty( Constants.BATCH_SIZE_FOR_UPDATES_KEY, "2" );
		properties.setProperty( Constants.SEARCH_FACTORY_TYPE_KEY, "sql" );
		this.searchFactory = (JPASearchFactoryAdapter) Setup.createSearchFactory( this.emf, properties );
		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		try {
			for ( int i = 1; i < ENTITY_COUNT + 1; ++i ) {
				CustomUpdatedEntity ent = new CustomUpdatedEntity();
				ent.setId( (long) i );
				ent.setText( ENT_NAME_SHOULD_NOT_FIND );
				em.persist( ent );
			}
			em.getTransaction().commit();
		}
		catch (Exception e) {
			em.getTransaction().rollback();
		}
		finally {
			em.close();
		}

		this.em = emf.createEntityManager();
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
