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
import java.util.Properties;

import org.apache.lucene.search.MatchAllDocsQuery;

import org.hibernate.search.genericjpa.Constants;
import org.hibernate.search.genericjpa.JPASearchFactoryController;
import org.hibernate.search.genericjpa.Setup;
import org.hibernate.search.genericjpa.db.events.triggers.MySQLTriggerSQLStringSource;
import org.hibernate.search.genericjpa.test.jpa.entities.MultipleColumnsIdEntity;
import org.hibernate.search.genericjpa.util.Sleep;
import org.hibernate.search.jpa.FullTextEntityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by Martin on 25.06.2015.
 */
public class MultipleColumnsIdEntityIntegrationTest {

	private static final int COUNT = 10;

	private EntityManagerFactory emf;
	private EntityManager em;
	private JPASearchFactoryController searchController;

	@Test
	public void testFindAll() throws InterruptedException {
		Sleep.sleep(
				100_000, () -> COUNT == this.searchController.getFullTextEntityManager( em ).createFullTextQuery(
						new MatchAllDocsQuery(),
						MultipleColumnsIdEntity.class
				).getResultSize()
				, 100, ""
		);
	}

	@Before
	public void setup() {
		this.emf = Persistence.createEntityManagerFactory( "EclipseLink_MySQL" );
		this.em = this.emf.createEntityManager();
		Properties properties = new Properties();
		properties.setProperty( "org.hibernate.search.genericjpa.searchfactory.name", "test" );
		properties.setProperty(
				"hibernate.search.trigger.source",
				MySQLTriggerSQLStringSource.class.getName()
		);
		properties.setProperty(
				Constants.TRIGGER_CREATION_STRATEGY_KEY,
				Constants.TRIGGER_CREATION_STRATEGY_DROP_CREATE
		);
		properties.setProperty( "hibernate.search.searchfactory.type", "sql" );
		this.searchController = Setup.createSearchFactory( this.emf, properties );

		FullTextEntityManager fem = this.searchController.getFullTextEntityManager( this.em );
		this.em.getTransaction().begin();
		for ( int i = 0; i < COUNT; ++i ) {
			MultipleColumnsIdEntity ent = new MultipleColumnsIdEntity();
			ent.setFirstId( "first" + i );
			ent.setSecondId( "second" + i );
			ent.setInfo( "info" + i );
			this.em.persist( ent );
		}
		this.em.getTransaction().commit();
		this.em.clear();
	}

	@After
	public void shutdown() {
		if ( this.searchController != null ) {
			this.searchController.close();
		}
		if ( this.em != null ) {
			this.em.close();
		}
		if ( this.emf != null ) {
			this.emf.close();
		}
	}

}
